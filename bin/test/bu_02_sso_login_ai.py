"""课时3-4 · Browser Use —— AI 控制浏览器完成 SSO 表单登录（登录 E2E + AI）。

与 bu_01 的区别：这次 AI 面对的是真实的登录表单（跨 OIDC 服务），并且要做
"登录成功"这种**结果性断言**。演示两个关键实践：

  1. 授权上下文预置：脚本先驱动浏览器进入 /oauth2/authorization/nitrowater，
     让 OIDC 授权码流程的 state/continue 上下文就位（否则表单 POST 成功后
     只会回 account 首页，拿不到 BFF 会话）。这一步相当于"用户点开登录入口"。
  2. 人机协同（HITL）注入验证码：图形验证码对 AI（纯文本模型）不可读，
     脚本从 Redis 预取真实码值写进任务提示——业界处理验证码的通用模式
     （人类/旁路系统解一次，AI 负责其余交互）。

AI 负责：填用户名 / 填密码 / 填验证码 / 点击登录 / 登录后断言。
脚本负责：预置授权上下文 + 验证码取码 + 独立复核 /bff/me（不信任 AI 自述）。

运行（需 DEEPSEEK_API_KEY，见 README）：
    bin\\.venv\\Scripts\\python.exe bin\\test\\bu_02_sso_login_ai.py [--headed]
"""

from __future__ import annotations

import argparse
import asyncio
import json
import sys

from _common import ADMIN, PORTAL, SSO, Checker, captcha_code, pick, run_agent_task, start_browser

TASK_TEMPLATE = """当前浏览器正处于 NitroWater 统一身份中心（SSO）的登录页。请以测试账号完成登录：

- 用户名 / 邮箱 / 手机号：{username}
- 密码：{password}
- 图形验证码：{code}     ← 该码值已由测试系统预先取好，直接填入"图形验证码"输入框
  **绝对不要点击验证码图片**（点击会刷新验证码，导致刚给你的码值失效）。

步骤：
1. 在用户名输入框填写 {username}。
2. 在密码输入框填写 {password}。
3. 在图形验证码输入框填写 {code}。
4. 点击「登录」按钮提交表单。
5. 等待页面跳转回门户（{portal}/）：断言顶栏出现「退出」按钮和用户名，
   且顶栏不再显示「登录」按钮。
6. 结束要求：
   - 成功 → 最终回复第一行以「PASS:」开头，写明你看到的登录态证据；
   - 失败 → 最终回复第一行以「FAIL:」开头，说明卡在哪一步。

只操作网页表单，不做其它系统级操作。"""


async def run(headed: bool) -> int:
    c = Checker("bu_02 AI SSO 登录旅程 (Browser Use + DeepSeek)")
    session = await start_browser(headed)
    try:
        # -- 1. Drive through the OIDC entry so authorize-state/continue is in place. ----
        await session.navigate_to(PORTAL + "/oauth2/authorization/nitrowater")
        page = await session.must_get_current_page()
        login_url = ""
        for _ in range(40):  # wait for the 302 chain to settle on the SSO login page
            login_url = await page.get_url()
            if SSO in login_url and "/login" in login_url:
                break
            await asyncio.sleep(0.5)
        c.check(SSO in login_url and "/login" in login_url, f"reached SSO login page: {login_url}")

        # -- 2. Captcha: wait for GET /api/auth/captcha, then read the real code from Redis.
        captcha_uuid = None
        for _ in range(20):  # the SPA fires /api/auth/captcha shortly after landing
            cookies = await session.cookies()
            captcha_uuid = next((ck["value"] for ck in cookies if ck.get("name") == "CAPTCHA_KEY"), None)
            if captcha_uuid:
                break
            await asyncio.sleep(0.5)
        c.check(bool(captcha_uuid), "CAPTCHA_KEY cookie present in AI browser session")
        code = captcha_code(captcha_uuid)
        c.check(bool(code), f"captcha code injected into task ({len(code)} chars)")

        # -- 3. Hand the form over to the AI. ---------------------------------------------
        task = TASK_TEMPLATE.format(
            username=ADMIN["username"],
            password=ADMIN["password"],
            code=code,
            portal=PORTAL,
        )
        history = await run_agent_task(task, session)

        final = pick(history.final_result)
        c.check(bool(final) and str(final).startswith("PASS"), f"agent verdict: {str(final)[:160]!r}")

        # -- 4. Independent verification: never trust the model's own claim. ---------------
        page = await session.must_get_current_page()
        final_url = await page.get_url()
        c.check(final_url.startswith(PORTAL), f"browser is back on portal: {final_url}")
        me_raw = await page.evaluate(
            "() => fetch('/bff/me', {credentials: 'include'}).then(r => r.json())"
        )
        me = json.loads(me_raw) if isinstance(me_raw, str) else me_raw
        c.check(me.get("authenticated") is True, f"BFF /bff/me authenticated = {me.get('authenticated')}")
        roles = me.get("roles") or []
        c.check("ROLE_ADMIN" in roles, f"BFF /bff/me roles = {roles}")
        body = await page.evaluate("() => document.body.innerText")
        c.check("退出" in body, "portal header shows '退出' in real DOM")
    except Exception as exc:  # noqa: BLE001
        c.check(False, f"unexpected error: {type(exc).__name__}: {exc}")
    finally:
        await session.stop()
    return c.finish()


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--headed", action="store_true", help="watch the AI drive a visible browser")
    args = ap.parse_args()
    return asyncio.run(run(headed=args.headed))


if __name__ == "__main__":
    sys.exit(main())
