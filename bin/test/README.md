# bin/test — 功能测试 / 端到端测试脚本（课时1-5）

> 本目录承载「功能测试基础」的课时任务：**课时1-2 功能测试与 E2E 概念 + Playwright 简介**，
> **课时3-4 Browser Use 框架（AI 控制浏览器）**，**课时5 AI 生成/执行测试（参数化矩阵 · 四维覆盖）**。
> 各课时脚本 + 本讲义，配套专用 Python venv。

| 脚本 | 课时 | 层次 | 说明 |
|---|---|---|---|
| `pw_01_portal_smoke.py` | 1-2 | 功能测试 | 门户渲染、顶栏路由、工具箱卡片、匿名公共 API |
| `pw_02_sso_login_e2e.py` | 1-2 | 端到端测试 | 真实 OIDC 登录/登出全链路（表单 → 授权码 → 会话 → 登出）；`--archive` 逐步截图 + JSONL |
| `bu_01_toolbox_ai.py` | 3-4 | AI 驱动的 E2E | Browser Use：AI 自主导航→输入目录文本→渲染→断言节点数 |
| `bu_02_sso_login_ai.py` | 3-4 | AI 驱动的 E2E | Browser Use：AI 填 SSO 登录表单（验证码注入）→ 断言登录态 |
| `bu_03_matrix_ai.py` | 5 | AI 执行 + 自判（主线） | 列表树 9 行矩阵；TASK 式：AI 逐行执行并判 PASS/FAIL，脚本只做非功能门 |
| `bu_04_java_to_ts_ai.py` | 5 | AI 执行 + 自判（主线） | Java DTO→TS 8 行矩阵（泛型/注释/未知类型/规则/空输入） |
| `bu_05_tree_ai.py` | 5 | AI 执行 + 自判（主线） | JSON→树 9 行矩阵（数组根/非法 JSON/深层/大输入/字段映射） |
| `ai_specs.py` / `_matrix_ai.py` | 5 | 规范/共享运行器 | 数据矩阵 + PRE/FINAL + 覆盖声明；矩阵轨道共用的门禁与留证逻辑 |
| `ai_02_generate_data.py` | 5 | AI 生成测试数据 | LLM 产出等价类/边界值/异常数据（JSON），脚本只校验 schema 与分布 |
| `dd_01_data_driven.py` | 5 | 数据驱动（确定性） | 读 JSON/CSV → 逐行执行 → 机器判定；结果写 JSONL 归档 |
| `ai_01_generate_pytest.py` | 5 | AI 生成测试代码（pytest） | LLM 为 `_matrix_ai.py` 生成 pytest 用例；门禁：≥4 函数 + 全绿 |
| `ai_03_generate_junit.py` | 5 | AI 生成测试代码（JUnit） | LLM 为 `MaskUtil` 生成 JUnit；门禁：≥4 方法/≥12 执行 + 覆盖率 |
| `pw_03_exceptions.py` | 5+ | 异常场景测试 | route 拦截模拟超时/500/401 + 空输入；逐用例截图 + JSONL |
| `archive_unit_coverage.py` | 5+ | 覆盖率归档 | 解析 JaCoCo XML，按包门禁（≥80%）写入 JSONL |
| `_common.py` | 底座 | 共享 + **唯一 LLM 入口** | 端点/账号/断言器/验证码旁路；`chat()`（HTTP）、`browser_llm()`（browser-use）都在这里 |
| `casebook.py` | 5+ | 用例存档 | 汇总 results/*.jsonl → `cases/`（CSV/Excel/文本） |
| `report_archive.py` | 5+ | 结果报告（确定性） | run 级汇总 → `reports/report-*.xlsx` + `.txt` |
| `report_ai.py` | 5+ | 测试报告（AI 撰写） | 读 JSONL 汇总 + 失败摘录 → LLM 生成中文 markdown 报告 |
| `_results.py` | 5 | 底座 | 统一 JSONL 归档（run/case/log/run_end）；`runs` / `show` 查询 |
| `../demo/ai_data_demo.py` | 5+ | 演示 | 最小闭环：一次 LLM 调用生成数据 + 结构校验打印 |

---

## 0. 环境准备（一次性）

```powershell
# 1) 起服务栈（MySQL/Redis/SeaweedFS + account/bff/server/resource，不起前端）
#    本项目浏览器统一入口是 BFF :8080 托管的 SPA（nitrowater-web/dist），无需 vite dev
.\.ai\testkit\start-test.ps1 -NoWeb

# 2) 造测试账号（testadmin=ADMIN / testuser1=USER，密码 Passw0rd123）
.\bin\seed-test-users.ps1

# 3) Python venv（已建好则跳过；工具程序一律放 D 盘，不放 C 盘）
uv venv bin\test\.venv --python 3.12
uv pip install --python bin\test\.venv\Scripts\python.exe -r bin\test\requirements.txt

# 4) Chromium 浏览器装到 D 盘（PLAYWRIGHT_BROWSERS_PATH 已由 _common.py 设为 D:\Program\ms-playwright；
#    该变量也已写入用户级环境变量）
$env:PLAYWRIGHT_BROWSERS_PATH = 'D:\Program\ms-playwright'
bin\test\.venv\Scripts\python.exe -m playwright install chromium
```

Browser Use 脚本还需要 LLM Key（本机已配置 `DEEPSEEK_API_KEY`）。

### 0.1 LLM 入口（唯一）

所有 AI 轨道统一从 `bin/test/_common.py` 取模型，**不再各自内联 HTTP 客户端**：

| 函数 | 使用者 | 说明 |
|---|---|---|
| `chat(prompt, *, model, system, temperature, timeout)` | `ai_01/02/03`、`report_ai` | 同步 HTTP `/chat/completions` |
| `browser_llm(*, model, temperature, timeout)` | `bu_*` / `_matrix_ai` | browser-use `ChatDeepSeek` 适配器（**显式**传 model） |
| `describe_llm(model, transport)` | 归档 `results/*.jsonl` 的 `model` 字段 | 记录"真正跑的模型"，而非写死字符串 |

- 默认模型 `deepseek-chat`、默认 host `https://api.deepseek.com`；可用 `NITROWATER_TEST_LLM_MODEL` / `NITROWATER_TEST_LLM_HOST` 覆盖。
- API key 只在这一处读取（`DEEPSEEK_API_KEY`），缺失立刻退出；`requests` / `browser_use` 均为懒加载，不影响 `pw_*`。

## 1. 运行

```powershell
# 课时1-2：脚本式 Playwright（确定性测试）
bin\test\.venv\Scripts\python.exe bin\test\pw_01_portal_smoke.py
bin\test\.venv\Scripts\python.exe bin\test\pw_02_sso_login_e2e.py

# 课时3-4：Browser Use（AI 自主操控浏览器；加 --headed 可围观 AI 点网页）
bin\test\.venv\Scripts\python.exe bin\test\bu_01_toolbox_ai.py
bin\test\.venv\Scripts\python.exe bin\test\bu_02_sso_login_ai.py

# 课时5：TASK 式矩阵（主线，AI 执行并自判）
bin\test\.venv\Scripts\python.exe bin\test\bu_03_matrix_ai.py --max-steps 90     # 列表树
bin\test\.venv\Scripts\python.exe bin\test\bu_04_java_to_ts_ai.py --max-steps 80  # Java→TS
bin\test\.venv\Scripts\python.exe bin\test\bu_05_tree_ai.py --max-steps 90        # JSON→树

# 课时5：数据驱动（确定性，机器判定；数据由 AI 生成）
bin\test\.venv\Scripts\python.exe bin\test\ai_02_generate_data.py --spec list-tree   # AI 产数据
bin\test\.venv\Scripts\python.exe bin\test\dd_01_data_driven.py --spec list-tree    # 执行 + 判定
bin\test\.venv\Scripts\python.exe bin\test\dd_01_data_driven.py --spec list-tree --data bin\test\data\exceptions.json  # 异常场景数据

# 课时5+：AI 生成测试代码 / 异常场景 / 场景法
bin\test\.venv\Scripts\python.exe bin\test\ai_01_generate_pytest.py     # LLM→pytest 用例
bin\test\.venv\Scripts\python.exe bin\test\ai_03_generate_junit.py      # LLM→JUnit(MaskUtil)
bin\test\.venv\Scripts\python.exe bin\test\pw_03_exceptions.py          # 超时/500/401/空输入
bin\test\.venv\Scripts\python.exe bin\test\pw_02_sso_login_e2e.py --archive  # 登录旅程 S1-S4（逐步截图+JSONL）

# 课时5+：存档 / 报告 / 覆盖率归档
bin\test\.venv\Scripts\python.exe bin\test\casebook.py                  # cases/ CSV/Excel/TXT
bin\test\.venv\Scripts\python.exe bin\test\report_archive.py            # reports/ Excel+TXT
bin\test\.venv\Scripts\python.exe bin\test\report_ai.py                 # reports/ AI 中文报告
bin\test\.venv\Scripts\python.exe bin\test\report_ai.py --dry-run       # 只看喂给 LLM 的摘要，不烧 token
.\gradlew.bat :nitrowater-lib:test finalizedBy jacocoTestReport
bin\test\.venv\Scripts\python.exe bin\test\archive_unit_coverage.py     # JaCoCo→JSONL（≥80% 门禁）
bin\test\.venv\Scripts\python.exe demo\ai_data_demo.py                  # AI 数据生成最小演示

# 只看拼接后的完整 prompt（PRE + TASK + DATA + COVERAGE + FINAL），不烧 token
bin\test\.venv\Scripts\python.exe bin\test\bu_03_matrix_ai.py --dry-run
```

- 每个脚本逐条打印 `[PASS]/[FAIL]`，最后输出 `== ...: PASS/FAIL ==`，退出码 0/1。
- 失败截图 / AI 最终页面截图 → `bin/test/artifacts/`（不入库）。
- 浏览器二进制在 `D:\Program\ms-playwright`；venv 在 `bin\test\.venv`（均不入 C 盘、不入库）。

---

## 2. 课时1-2：功能测试与端到端测试概念 · Playwright 简介

### 2.1 概念：功能测试 vs 端到端测试

测试金字塔（从下到上：单元 → 集成 → E2E）：越往上**保真度越高、成本越高、稳定性越低**。

| | 功能测试 Functional Test | 端到端测试 E2E |
|---|---|---|
| 验证对象 | **一个功能点**在一层/一个界面上的行为 | **一条完整用户旅程**跨越多个服务/层 |
| 边界 | 本页面 + 直接依赖的接口 | 浏览器 → BFF → SSO/业务库 → 回浏览器 |
| 典型断言 | "首页渲染出 7 个工具卡片"、"公共题库接口返回 DTO" | "点登录→SSO 登录→拿到 BFF 会话→退出后会话失效" |
| 速度/稳定性 | 快、稳定 | 慢、依赖环境（服务、验证码、LLM 等） |
| 失败信息 | 哪个功能坏了 | 旅程断在哪一跳 |

**本项目映射**（NitroWater 架构）：

```
pw_01（功能层）                        pw_02（E2E 层）
─────────────────                      ─────────────────
SPA 首页/路由/卡片       ←前端一层      门户点登录
/api/public/problems     ←BFF+server    → BFF /oauth2/authorization/nitrowater
                                          → account:8090 托管登录页（表单 POST /login）
                                          → 授权码回跳 BFF（服务端换 token、建会话）
                                          → 回门户：/bff/me authenticated:true + ROLE_ADMIN
                                          → 退出（RP-initiated logout：吊销 RT + 销毁会话）
```

工程经验（两个脚本都体现的通用原则）：

1. **断言后置条件，不断言中间过程**——`pw_02` 登录后直接等「退出」按钮出现，而不是猜每一跳 URL。
2. **机器不可读的输入走白盒旁路**——图形验证码由脚本从 Redis 读真实码值
   （`nitrowater:biz:verify:captcha:{CAPTCHA_KEY}`），UI 流程与后端校验都仍是真实的。
3. **失败留证据**——出错自动截全页图到 `artifacts/`。
4. **E2E 的账号用固定种子**（`bin/seed-test-users.ps1`），避免注册限流等随机性。

### 2.2 Playwright 简介

- **是什么**：微软开源的浏览器自动化库，**一个 API 驱动 Chromium/Firefox/WebKit**；
  本课用 **Python 版 `playwright`**（`playwright.sync_api`），另有 TS/Java/.NET 版与 `@playwright/test` 测试框架。
- **核心机制**：
  - **自动等待**：每个 locator 操作前自动等待元素可交互（可见、稳定、启用、接收事件）；
    `wait_for_url` / `wait_for(timeout=...)` 显式控制导航与超时。
  - **Locator 即一等公民**：`page.get_by_role("button", name="登录")`、`page.fill("#username", …)`
    ——按可访问性角色/文本定位，抗 DOM 结构变动（比裸 XPath/CSS 稳）。
  - **BrowserContext = 隔离会话**：cookie/localStorage 互不污染，可并行开多"浏览器画像"；
    `context.request` 是与浏览器**共享 cookie 的 HTTP 客户端**（`pw_02` 用它探 `/bff/me`）。
  - **无头/有头同一套代码**：CI 无头，本地 `--headed` 有头，仅一个参数差异。
- **本项目链路**：BFF(:8080) 托管 SPA + `/api/**` 代理 + `/bff/me` 会话探针；SSO 在 :8090；
  `pw_02` 完整跨越两者，正是"E2E"的含义。
- **局限（引出课时3-4）**：脚本是**确定性**的——选择器/步骤必须人事先写好；
  页面改版、动态内容、未知路径时脚本会脆；而且**写脚本本身就是成本**。AI 能不能自己看页面、自己点？

---

## 3. 课时3-4：Browser Use 框架（与阶段2衔接）

### 3.1 Browser Use 是什么

**Browser Use**（`browser-use`，Python）= 给 LLM 用的浏览器智能体框架：把"网页"变成 LLM 可读的
**可访问性树（ARIA）快照 + 元素编号**，让 LLM 以**多轮动作**的方式操控真实浏览器：

```
┌────────────┐  ARIA/DOM 快照（元素编号+角色+文本） ┌────────────┐  JSON 动作（click/fill/goto…）
│  Chromium  │ ───────────────────────────────→ │ LLM        │ ───────────────────────────────→ │  Chromium  │
│ (Playwright│                                  │ (DeepSeek  │                                  │  执行动作   │
│  CDP 驱动) │ ←─────────────────────────────── │  chat)     │ ←─────────────────────────────── │  结果/新页面│
└────────────┘      新快照 / 任务完成(final)     └────────────┘      下一轮观察                   └────────────┘
        循环，直到 LLM 给出 final_result（PASS/FAIL）或达到 max_steps
```

- **与阶段2衔接**：阶段2的 Playwright 脚本把"人写步骤"固化下来；Browser Use 把"步骤"交给模型
  ——**人只写自然语言任务描述**（`bu_01` 里没有任何 CSS selector）。
- **本课实现**：`BrowserSession`（Chromium，走 Playwright + CDP）+ browser-use 内置
  `ChatDeepSeek`（`browser_use.llm`，读环境变量 `DEEPSEEK_API_KEY`）
  + `use_vision=False`（纯文本模型 → 只用 ARIA 快照，不开截图）。
- **确定性 vs 自主性**：

| | Playwright 脚本（pw_*） | Browser Use（bu_*） |
|---|---|---|
| 步骤来源 | 人写代码 | LLM 看页面自己决定 |
| 选择器 | 精确、脆弱 | 不需要，靠语义 |
| 速度/成本 | 快、免费 | 慢、按 token 计费 |
| 稳定性 | 环境不变则 100% | 受模型能力影响，需兜底断言 |
| 适合 | 回归测试、CI | 探索性测试、页面不熟/频繁改版、演示 |

### 3.2 两个脚本教的四件事

1. **点击 / 输入 / 断言都是 AI 的动作**（`bu_01`）：
   AI 导航门户 → 点「开始使用工具箱」→ 进「列表树可视化」→ 向 textarea **输入多行目录树** →
   点「渲染树列表」→ 断言「个节点」统计 → 按约定输出 `PASS: 共 5 个节点`。
2. **结果性断言必须脚本复核**（两个 bu 脚本共同模式）：
   `history.final_result` 只是 AI 的"自述"，脚本再用 Playwright 直接读 DOM / `fetch('/bff/me')`
   独立复核——**AI 的话不能全信，这是 AI 测试工程的铁律**。
3. **人机协同（HITL）处理验证码**（`bu_02`）：
   纯文本 LLM 读不了图形验证码 → 脚本先驱动浏览器进入 OIDC 授权入口（预置 `continue/state`），
   再从 Redis 取真实验证码码值**注入任务提示**，AI 负责其余全部交互。这就是业界处理
   验证码/2FA/邮件链接的通用模式：**旁路系统解一次，AI 干活**。
4. **环境治理**：`enable_default_extensions=False`（不往 C 盘下扩展）、
   浏览器二进制统一 `D:\Program\ms-playwright`、`generate_gif=False`（不产录像）。

### 3.3 与 Playwright MCP 的关系

`.ai/testkit/PLAYWRIGHT-MCP.md` 是**人在对话里手把手指挥浏览器**（MCP 交互式）；
本目录的 Browser Use 是**把任务描述一次性交给 agent 自主完成、并可进 CI 断言退出码**——
前者是"协同操作台"，后者是"可回归的测试资产"。

---

## 4. 课时5：AI 生成 / 执行测试（TASK 式 + 参数化矩阵）

### 4.1 两条轨道

| | `bu_03/04/05`（AI 执行 + 自判） | `dd_01`（数据驱动，机器判定） |
|---|---|---|
| 数据从哪来 | 人写死在 `ai_specs.py` 的矩阵 | `ai_02` 让 LLM 生成 → `data/<tool>.json` |
| 谁执行 | LLM 在真实浏览器里逐行操作 | Playwright 脚本按数据逐行执行 |
| 谁判定 | LLM 自判（每行 `CASE ... : PASS/FAIL`） | 字符串/节点数硬匹配（`assert` 语义） |
| 可重复性 | 低：每次都是新的一轮 LLM 决策 | 高：同一份数据可无限回归、可进 CI |
| 证据 | 每行 evidence 引用页面文本；脚本抓末页 URL/正文/截图 | 实际页面文本 vs expected；失败逐用例截图 |
| 适合 | 探索、演示、页面不熟 | 回归门禁、稳定资产、结果可比 |

### 4.2 Prompt 拼接架构（PRE + TASK + DATA + COVERAGE + FINAL）

```text
build_matrix_prompt(TASK, PORTAL, cases, pre, paths)
  = PRE(场景/路径/页面结构) + TASK(主任务体) + DATA(矩阵) + COVERAGE(覆盖声明) + FINAL(输出契约)
```

| 片段 | 所在位置 | 职责 |
|---|---|---|
| `PRE` 预先提示词 | `ai_specs.py`：`MATRIX_PRE` / `JAVA_TO_TS_PRE` / `TREE_PRE` | 站点、访问路径、页面结构与定位依据 |
| `TASK` 主任务体 | **各轨道文件顶部常量**（`bu_03/04/05`、`ai_02`） | 怎么做、证据纪律、判定口径与例外 |
| `DATA` 数据矩阵 | `ai_specs.py`：`LIST_TREE_CASES` / `JAVA_TO_TS_CASES` / `TREE_CASES` | 矩阵轨道的唯一真源；数据轨道的示例种子 |
| `COVERAGE` 覆盖声明 | `ai_specs.py`：`coverage_block()` | 由矩阵**动态生成**：用例数、类型分布、等价类、边界值、异常数据、UI 路径 |
| `FINAL` 结束约束 | `ai_specs.py`：`MATRIX_FINAL` / `DATA_GEN_FINAL` | 报告格式契约（`CASE` 行 / `PATH` / `VERDICT`；数据轨道只输出 JSON 数组） |

改主任务 → 只改 `TASK`；改数据 → 只改矩阵；改输出契约 → 只改 `FINAL`。

### 4.3 四维要求 → 实现方式

| 要求 | 实现 | 证据 |
|---|---|---|
| **参数化** | 矩阵逐行执行（9/8/9 组）；数据轨道按 `data/<tool>.json` 逐行执行（AI 生成 10-11 组） | `DATA` 段 + `CASE`/`D*` 行 |
| **等价类** | kind 标注：`valid` / `equivalence-*` | `COVERAGE` 的「等价类」行 |
| **边界值** | 空输入、单节点、1 层/5 层、30/31 节点 | `COVERAGE` 的「边界值」行 |
| **异常数据** | 非法 JSON、非 Java 代码、空输入、未知类型 | `COVERAGE` 的「异常数据」行 |
| **路径覆盖（UI）** | 每个 spec 显式声明 5 条 UI 路径（空/正常/非法/规则/映射/重渲染） | `COVERAGE` 的「路径覆盖」行 + 报告 `PATH:` 行 |
| **语句覆盖** | **本课用「需求覆盖」落地**（下表追溯）；代码级百分比由 JaCoCo/istanbul 产出，E2E 无法产出 | 追溯表；后端 `nitrowater-lib` 已有 100% 单测 |

> 说明：`statement/branch coverage` 是白盒指标。若课程要求百分比，请引用 `StrongPasswordValidatorTest`
> （JaCoCo instruction/branch/line = 100%），或补前端 vitest 覆盖 `listTree.ts` / `buildTree.ts`。
> 浏览器 E2E / AI 测试无论怎么写都产不出该数字——不要在报告里混用两个概念。

### 4.4 需求 → 用例追溯表

**列表树（`bu_03` / `dd_01`，9 组矩阵 + AI 生成 11 组）**

| 需求 | 用例 | 类型 | 期望 |
|---|---|---|---|
| 正常目录树渲染并统计 | C1 | valid | 5 个节点 |
| 空输入安全（不崩） | C2 | boundary-empty | 按钮 disabled，页面可用 |
| 单节点 | C3 | boundary-single | 1 个节点 |
| 深层嵌套 | C4 | boundary-deep | 5 个节点、层级正确 |
| 空白容错（空行/行尾空格） | C5 | equivalence-whitespace | 5 个节点 |
| 中文路径 | C6 | equivalence-unicode | 4 个节点、不乱码 |
| 畸形输入容错 | C7 | invalid | 1 个节点、不崩溃 |
| 大输入 | C8 | boundary-large | 31 个节点 |
| 重复渲染必须刷新 | C9 | stability-re-render | 4 个节点、不残留 31 |

**Java DTO 转 TS（`bu_04`，8 组）**

| 需求 | 用例 | 类型 | 期望 |
|---|---|---|---|
| 基本字段映射 + 默认值 | J1 | valid | `interface RoleResp`、`defaultRoleResp` |
| 单字段 | J2 | boundary-single | `interface One` |
| 泛型嵌套 | J3 | boundary-generics | `Array<Record<string, number[]>>` |
| 注释映射 | J4 | equivalence-comment | `// 用户名称` |
| 未知类型容错 | J5 | equivalence-unknown-type | `FIXME` + 警告 |
| 校验规则生成 | J6 | path-rules | `rules` + `required/min/max` |
| 非 Java 输入容错 | J7 | invalid | 不崩溃（可能是空 `UnknownType`） |
| 空输入 | J8 | boundary-empty | 提示「请输入 Java 代码」，输出不清空 |

**JSON → 树（`bu_05`，9 组）**

| 需求 | 用例 | 类型 | 期望 |
|---|---|---|---|
| 正常渲染 | T1 | valid | 3 个节点 |
| 空输入 | T2 | boundary-empty | 按钮 disabled |
| 非法 JSON | T3 | invalid | 「JSON 解析错误」提示 |
| 单节点 | T4 | boundary-single | 1 个节点 |
| 数组根（虚拟 root） | T5 | equivalence-array-root | 3 个节点 |
| 中文标签 | T6 | equivalence-unicode | 2 个节点 |
| 五层嵌套 | T7 | boundary-deep | 5 个节点 |
| 大输入 | T8 | boundary-large | 31 个节点 |
| 字段映射（label/kids） | T9 | path-field-mapping | 3 个节点 |

### 4.5 脚本自己的门禁（不做功能断言）

`_matrix_ai.py` 只校验「报告可信」，功能对错由 LLM 判：

1. **完整性**：每个选中用例都要有 `CASE` 结论；
2. **一致性**：无 `FAIL` 行，且末行 `VERDICT: PASS`；
3. **观察纪律（防伪造）**：页面读取动作数 ≥ 用例数（`search_page`/`evaluate`/…）；
4. 末页 `URL + 正文长度 + 截图` 存档到 `artifacts/`；browser-use 内置 judge 只作 `[ADVISORY]`。

### 4.6 设计取舍

- **放弃 CRLF 等价类**：`<textarea>` 会把 CRLF 规范化为 LF，测不到真语义 → 改为空白容错（C5）。
- **空输入/非法输入允许「保留上一次结果」**：`java-to-ts` 空输入不清空输出、`tree` 解析失败不清统计，
  均为实现现状；矩阵里写成「已知行为」，避免 AI 误判 FAIL。
- **`ai_01`（AI 生成测试代码，pytest）已重新纳入**：阶段 6 按评估要求补回——LLM 为
  `_matrix_ai.py` 生成 pytest 用例，门禁为「≥4 生成函数 + 全绿」；确定性回归职责仍由
  `ai_02` + `dd_01` 承担，两者互补不冲突。

### 4.7 课时5-8 阶段计划（方案 B）

> 方案 B：**LLM 负责设计数据 / 执行 / 判定 / 写分析；脚本只记录，不裁判**。
> 唯一保留的证据纪律：每个用例的结论必须引用真实页面读取。

| 阶段 | 目标 | 交付物 | 状态 |
|---|---|---|---|
| 1 | 结果归档底座（JSONL） | `_results.py` + `results/<run_id>.jsonl` + `artifacts/<run_id>/` | **已完成** |
| 2 | AI 生成测试数据 + 数据驱动框架（JSON/CSV 循环） | `ai_02_generate_data.py` + `dd_01_data_driven.py` | **已完成** |
| 3 | 异常场景（网络超时 / 空输入 / 权限错误） | `pw_03_exceptions.py`（route 拦截）+ `data/exceptions.json`（dd_01） | **已完成** |
| 4 | 自动截图 + 日志（逐用例） | `pw_03` / `pw_02 --archive` 逐用例截图落 `artifacts/<run_id>/`，日志落 JSONL `record=log` | **已完成** |
| 5 | 用例/结果存档（CSV / Excel / 文本） | `casebook.py`（用例）+ `report_archive.py`（结果） | **已完成** |
| 6 | 单测 ≥80%（`nitrowater-lib`）+ 场景法旅程 + 测试报告 | 手写 JUnit（validator 100%/codec 84.7%）+ `pw_02 --archive`（S1-S4）+ `report_ai.py` | **已完成** |

### 4.8 阶段 1：结果归档（已落地）

每次运行生成一个 append-only JSONL：`bin/test/results/<run_id>.jsonl`，记录 4 类：

| record | 关键字段 |
|---|---|
| `run_start` | `run_id / tool / mode / source / model / started_at` |
| `case` | `case_id / kind / input / expected / verdict / evidence / screenshot / duration_ms / meta` |
| `log` | `level / message / at` |
| `run_end` | `status / finished_at / duration_ms / checks[] / meta` |

- **谁写**：`_matrix_ai.py`（`bu_03/04/05`，逐用例写 `case`）、`dd_01_data_driven.py`（数据轨道，逐用例写 `case`）、`ai_02`（数据生成，run 级）。
- **截图**：`bin/test/artifacts/<run_id>/final.png`（逐用例截图在阶段 4 接入）。
- **查看**：

```powershell
bin\test\.venv\Scripts\python.exe bin\test\_results.py runs        # 列出所有 run
bin\test\.venv\Scripts\python.exe bin\test\_results.py show latest # 看最近一次
```

`results/` 与 `artifacts/` 均已 gitignore——它们是运行产物，不是源码。

### 4.9 阶段 2：AI 生成数据 + 数据驱动框架（已落地）

**两步走**：`ai_02`（LLM 产数据） → `dd_01`（机器执行并判定）。

```powershell
# 1) AI 生成数据（等价类/边界值/异常数据），落盘 bin/test/data/<tool>.json
bin\test\.venv\Scripts\python.exe bin\test\ai_02_generate_data.py --spec list-tree
bin\test\.venv\Scripts\python.exe bin\test\ai_02_generate_data.py --spec java-to-ts
bin\test\.venv\Scripts\python.exe bin\test\ai_02_generate_data.py --spec tree

# 2) 数据驱动执行（确定性，无 LLM）：读 JSON 或 CSV，逐行跑，机器判定
bin\test\.venv\Scripts\python.exe bin\test\dd_01_data_driven.py --spec list-tree
bin\test\.venv\Scripts\python.exe bin\test\dd_01_data_driven.py --spec tree --data bin\test\data\tree.csv
```

**数据 schema**（JSON 数组，CSV 同列）：

| 字段 | 必填 | 说明 |
|---|---|---|
| `id` | ✓ | 唯一（D1、D2…） |
| `kind` | ✓ | `valid` / `equivalence-*` / `boundary-*` / `invalid*` |
| `input` | ✓ | 原样输入（保留换行） |
| `expected` | ✓ | **页面原文片段**（不是描述；含「应该/提示/。」等会被判为 prose 并拒绝） |
| `expect_all` |  | 额外必须同时出现的片段数组 |
| `nodes` |  | 仅节点统计类工具；必须与真实解析结果一致 |
| `page_state` |  | `"disabled"` = 期望主按钮禁用（空输入） |

**校验分层**（AI 数据必过）：

1. `ai_02` 结构校验：schema、kind 白名单、id/input 去重、`expected` 字面量检查、分布（≥3 等价类 / ≥3 边界 / ≥2 异常）。
2. `dd_01` 执行校验：逐行跑 UI，`nodes` 与 `expected` 必须**在页面上真实出现**，否则 FAIL 并截图。
3. 人工复核：AI 也会写错（本次 `list-tree` 有 1 条把「顶层无缩进行」误判为错误提示，已按真实行为修正并标注）。

**实测结果**：`list-tree` 11/11、`java-to-ts` 10/10、`tree` 10/10（均 PASS，结果已入 `results/`）。

> 这一层解决的是"AI 数据不可信"：LLM 只负责**想数据**，`dd_01` 用页面原文做**硬判定**；
> 而"AI 自己判"仍由 `bu_03/04/05` 承担——两条轨道共用同一份数据，结论可互相对照。

---

## 5. 排障速查

| 症状 | 原因/处理 |
|---|---|
| 连接被拒（8080/8090） | `.\.ai\testkit\start-test.ps1 -NoWeb` 没起；`check-services.ps1` 体检 |
| 验证码相关 FAIL | Redis 没起（6379）；或验证码超 2 分钟 TTL；重新跑脚本即可（每次自动取新码） |
| 登录被锁 | 连续错 5 次锁 15 分钟（admin 120 分钟）；等锁过期或 `reset-db` |
| 找不到浏览器 | `PLAYWRIGHT_BROWSERS_PATH` 必须是 `D:\Program\ms-playwright`（`_common.py` 已设；用户级环境变量也已写入） |
| bu 脚本 LLM 报错 | `DEEPSEEK_API_KEY` 未设置/欠费；网络需可达 `api.deepseek.com` |
| bu 脚本慢 | 正常：每步一次 LLM 调用，`bu_01/02` 约 1-3 分钟 |
| 服务端口/账号细节 | 见 `.ai/testkit/README.md`、`.ai/testkit/PLAYWRIGHT-MCP.md` |
