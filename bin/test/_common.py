"""bin/test 的共享底座：路径/端点/账号、断言收集器、验证码旁路、LLM 网关、Browser Use 助手。

所有脚本都用本目录专用 venv 运行：

    bin\\.venv\\Scripts\\python.exe bin\\test\\pw_01_portal_smoke.py

目录内脚本（详见 README）：
    pw_01_portal_smoke.py   课时1-2  Playwright 功能测试（门户冒烟 + 公共 API）
    pw_02_sso_login_e2e.py  课时1-2  Playwright 端到端测试（OIDC 登录/登出全链路，--archive 留证）
    pw_03_exceptions.py     课时5+   Playwright 异常场景（route 拦截超时 / 500 / 401 / 空输入）
    bu_01_toolbox_ai.py     课时3-4  Browser Use（AI 自主点击 / 输入 / 断言）
    bu_02_sso_login_ai.py   课时3-4  Browser Use（AI 表单登录 + 登录态断言）
    bu_03/04/05_*_ai.py     课时5    AI 执行并自判的矩阵轨（列表树 / Java→TS / JSON→树）
    ai_01/02/03_generate_*  课时5    AI 生成测试代码（pytest / JUnit）与测试数据
    dd_01_data_driven.py    课时5    数据驱动（机器判定，可进 CI）

前置条件（见 README）：
    - 服务栈：  .\\.ai\\testkit\\start-test.ps1 -NoWeb
    - 测试账号：.\\bin\\seed-test-users.ps1
    - AI 轨道需要环境变量 DEEPSEEK_API_KEY

约定（重要）：
    - 本文件是**唯一**接触 DeepSeek 的地方（读 key、拼端点、取 choices[0]），其它脚本一律走这里。
    - browser_use / redis / requests 全部在函数内懒加载，pw_* 不会为此付启动代价。
"""

from __future__ import annotations

import os
import time
from pathlib import Path

# 浏览器二进制放 D 盘（项目工具约定）—— 必须在 playwright / browser_use 启动前设置。
os.environ.setdefault("PLAYWRIGHT_BROWSERS_PATH", r"D:\Program\ms-playwright")

TEST_DIR = Path(__file__).resolve().parent
REPO_ROOT = TEST_DIR.parents[1]  # bin/test -> bin -> 仓库根
ARTIFACTS = TEST_DIR / "artifacts"
ARTIFACTS.mkdir(exist_ok=True)

# 端点：BFF(:8080) 是浏览器唯一入口 —— 托管 SPA + 代理 /api/** + 会话探针 /bff/me。
PORTAL = "http://localhost:8080"
SSO = "http://localhost:8090"  # account：托管的 OIDC 登录页

# 种子测试账号（bin/seed-test-users.ps1）：testadmin 持有 ROLE_ADMIN。
ADMIN = {"username": "testadmin", "password": "Passw0rd123"}

CAPTCHA_REDIS_PREFIX = "nitrowater:biz:verify:captcha:"


def captcha_code(captcha_uuid: str, timeout_s: float = 5.0) -> str:
    """读取 SSO 页面存在 Redis 里、与 cookie CAPTCHA_KEY 对应的图形验证码。

    GET /api/auth/captcha 每次生成新码（2 分钟 TTL）并下发 CAPTCHA_KEY cookie；浏览器看不到
    码值，所以测试直接从 Redis 读（UI 测试的标准白盒旁路——表单本身仍然走真实流程）。
    """
    try:
        import redis
    except ModuleNotFoundError as exc:
        raise RuntimeError("redis package is required for captcha_code()") from exc

    client = redis.Redis(host="127.0.0.1", port=6379, decode_responses=True, socket_timeout=5)
    key = CAPTCHA_REDIS_PREFIX + captcha_uuid
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        code = client.get(key)
        if code:
            return code
        time.sleep(0.2)
    raise AssertionError(f"captcha code missing in Redis within {timeout_s}s (key={key})")


def pick(callable_) -> object:
    """兼容 browser-use history 对象上的两种取值方式：属性式和调用式。"""
    return callable_() if callable(callable_) else callable_


class Checker:
    """断言收集器：单条失败不中断，逐条打印 PASS/FAIL，最后汇总。"""

    def __init__(self, title: str) -> None:
        self.title = title
        self.failures: list[str] = []

    def check(self, cond: object, msg: str) -> None:
        ok = bool(cond)
        print(f"  [{'PASS' if ok else 'FAIL'}] {msg}", flush=True)
        if not ok:
            self.failures.append(msg)

    def finish(self) -> int:
        if self.failures:
            print(f"\n== {self.title}: FAIL ({len(self.failures)} failed) ==", flush=True)
            return 1
        print(f"\n== {self.title}: PASS ==", flush=True)
        return 0


def any_visible(locator) -> bool:
    """只要有任意一个匹配元素可见就返回 True。

    Playwright 的 `.first.is_visible()` 只看 DOM 顺序第 0 个 —— 页面存在隐藏副本
    （响应式克隆、屏外区块）时会误判；这里全量扫描。
    """
    return any(locator.nth(i).is_visible() for i in range(locator.count()))


def wait_any_visible(locator, timeout_s: float = 20.0, poll_s: float = 0.3) -> bool:
    """轮询直到任意匹配元素可见（有上限的等待，不是瞬时 is_visible）。"""
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        if any_visible(locator):
            return True
        time.sleep(poll_s)
    return any_visible(locator)


def click_first_visible(locator, timeout_s: float = 10.0, poll_s: float = 0.3) -> bool:
    """点击第一个**可见**的匹配元素（最多等 timeout_s），超时返回 False。"""
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        for i in range(locator.count()):
            candidate = locator.nth(i)
            if candidate.is_visible():
                candidate.click(timeout=5_000)
                return True
        time.sleep(poll_s)
    return False


# ---------------------------------------------------------------------------
# LLM 网关（DeepSeek）—— 全目录唯一入口。
#   生成轨（ai_*）走 chat()；浏览器轨（bu_*）走 browser_llm()。
#   两者共用同一份 host / model / key，换模型或换 provider 只改这一段。
#   环境变量可覆盖：NITROWATER_TEST_LLM_HOST / NITROWATER_TEST_LLM_MODEL。
# ---------------------------------------------------------------------------

LLM_HOST = os.environ.get("NITROWATER_TEST_LLM_HOST", "https://api.deepseek.com")
DEFAULT_MODEL = os.environ.get("NITROWATER_TEST_LLM_MODEL", "deepseek-chat")
LLM_KEY_ENV = "DEEPSEEK_API_KEY"
DEFAULT_SYSTEM = "You are a senior test engineer. Output only what the prompt requests, no explanations."


def llm_model(model: str | None = None) -> str:
    """返回实际要用的模型名：显式参数优先，其次环境变量 / 默认值。"""
    return model or DEFAULT_MODEL


def llm_key() -> str:
    """读取 API key；缺失立刻退出，避免流程跑到一半才报错。"""
    key = os.environ.get(LLM_KEY_ENV)
    if not key:
        raise SystemExit(f"{LLM_KEY_ENV} 未设置（AI 轨道必需）。")
    return key


def describe_llm(model: str | None = None, transport: str = "http") -> str:
    """写进 results/*.jsonl 的规范模型标识，例如 'deepseek-chat (browser-use)'。"""
    return f"{llm_model(model)} ({transport})"


def chat(
    prompt: str,
    *,
    model: str | None = None,
    system: str = DEFAULT_SYSTEM,
    temperature: float = 0.0,
    timeout: int = 240,
) -> str:
    """单轮补全（同步 HTTP），返回模型原文；内容是否可信由调用方校验。"""
    import requests

    resp = requests.post(
        LLM_HOST + "/chat/completions",
        headers={"Authorization": f"Bearer {llm_key()}", "Content-Type": "application/json"},
        json={
            "model": llm_model(model),
            "temperature": temperature,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": prompt},
            ],
        },
        timeout=timeout,
    )
    resp.raise_for_status()
    return resp.json()["choices"][0]["message"]["content"]


def browser_llm(*, model: str | None = None, temperature: float = 0.0, timeout: float = 120):
    """browser-use 适配器：**显式**传 model，绝不依赖 ChatDeepSeek 的 dataclass 默认值。"""
    from browser_use.llm import ChatDeepSeek

    return ChatDeepSeek(
        model=llm_model(model),
        base_url=LLM_HOST + "/v1",  # openai 客户端要的是 base，不是完整 endpoint
        api_key=llm_key(),
        temperature=temperature,
        timeout=timeout,
    )


# ---------------------------------------------------------------------------
# Browser Use（AI 驱动）助手 —— bu_* 脚本共用的浏览器默认配置。
# 懒加载，保证 pw_* 不付 browser_use 的导入代价。
# ---------------------------------------------------------------------------


async def start_browser(headed: bool = False):
    """启动一个带全目录统一配置的 BrowserSession（无头、不往 C 盘塞扩展）。

    返回已启动的 session，由调用方负责在 finally 里 `await session.stop()`。
    没有并进 run_agent_task，是因为 bu_02 这类脚本要在 AI 接手前先自己操作浏览器
    （导航、读 cookie）。
    """
    from browser_use import BrowserSession
    from browser_use.browser.profile import BrowserProfile

    profile = BrowserProfile(
        headless=not headed,
        enable_default_extensions=False,  # keep C-drive clean: no CRX downloads
        keep_alive=True,  # keep browser open for post-run DOM re-verification
    )
    session = BrowserSession(browser_profile=profile)
    await session.start()
    return session


async def run_agent_task(task: str, session, *, llm=None, max_steps: int = 30):
    """用统一默认配置创建 Browser Use agent，并在给定 session 上跑一次任务。"""
    from browser_use import Agent

    agent = Agent(
        task=task,
        llm=llm or browser_llm(),
        browser_session=session,
        use_vision=False,  # 纯文本模型：只靠 ARIA 快照驱动
        generate_gif=False,
        max_failures=5,
        directly_open_url=False,  # 让 agent 自己导航，避免解析任务文本里的 URL
    )
    return await agent.run(max_steps=max_steps)
