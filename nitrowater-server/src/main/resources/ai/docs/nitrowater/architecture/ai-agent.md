# NitroBot：AI 文档助手与 Agent

NitroBot 是平台内置的 AI 助手，页面在 `/chat`。它只有一种调用形态：**Agent**（模型按需调用工具）；模型判断不需要工具时就是一次普通回答（单轮、无工具、无引用）。检索、技能、工具由服务端自动决定，不给用户开关。

## 1. 能力与端点

| 端点 | 说明 |
|---|---|
| `POST /api/ai/agent/chat` | Agent 一次性回答（含工具调用） |
| `POST /api/ai/agent/chat/stream` | SSE：`start` → `step`/`delta`* → `sources` → `tools` → `skills` → `done` |
| `GET /api/ai/status` | 索引/模型可用性 |
| `GET /api/ai/skills` | 可用技能清单（管理台与调试用） |
| `POST /api/admin/ai/reindex` | 管理：重建文档索引 |

所有接口都经 BFF 代理；SSE 由 `AiStreamProxyController` 逐块透传，BFF 的异步请求超时配置为 180s。

## 2. 模型与向量配置

| 配置 | 默认值 | 说明 |
|---|---|---|
| `spring.ai.openai.chat.base-url` | `https://api.deepseek.com` | OpenAI 兼容聊天接口（DeepSeek） |
| `spring.ai.openai.chat.model` | `deepseek-chat` | 由 `deepseek-flash` 服务，1M 上下文（`GET /models` 实测）；`deepseek-reasoner` 不支持工具调用，agent 不能用 |
| `spring.ai.openai.chat.temperature` | `0.2` | 事实型回答，低温度 |
| `spring.ai.openai.chat.max-tokens` | `4096` | 单轮回答上限（1200 会在多数技术回答中途截断） |
| `spring.ai.openai.embedding.base-url` | `https://api.siliconflow.cn/v1` | 可换成任意兼容实现 |
| `spring.ai.openai.embedding.model` | `Qwen/Qwen3-Embedding-0.6B` | 1024 维；换模型或维度要重建索引 |
| `spring.ai.vectorstore.qdrant.*` | `localhost:6334` | gRPC；`initialize-schema: false` |
| `spring.ai.tools.limits.*` | 总调用 18 / 单工具 3 / webSearch 12 / runCode 4 / calculate 6 | 超限返回错误响应而不是抛异常；单工具上限必须高于工具的「成功次数」预算 |
| `app.ai.web-search.endpoint` | `https://api.deepseek.com/v1/tools/search` | DeepSeek 搜索 API |
| `app.ai.web-search.api-key` | `${DEEPSEEK_API_KEY}` | 搜索用 key，可与聊天共用 |
| `app.ai.web-search.auto-fallback` | `true` | 本地文档无命中时自动联网一次 |
| `app.ai.web-search.max-results` | `3` | 单次联网取几条（显式搜索与自动兜底共用） |
| `app.ai.web-search.max-successes` | `8` | 单轮成功联网上限（失败/重试不计数） |
| `app.ai.web-search.max-attempts` | `2` | 单次调用的 HTTP 尝试上限（首查 + 一次重试） |
| `app.ai.web-search.retry-backoff-ms` | `300` | 可重试失败之间的间隔 |
| `app.ai.agent.max-rounds` | `18` | 单回合模型轮次上限（一轮 = 一次模型调用，可含多个工具调用）；与框架的「调用次数」上限解耦 |
| `app.ai.agent.turn-timeout-ms` | `150000` | 单轮总预算（模型回合 + 工具），必须小于 SSE 的 180s |
| `app.ai.agent.retry-attempts` | `1` | 首轮在**任何 token 发出之前**失败（503/429/连接重置）时的重试次数；已发出 delta 不再重试，超时不重试；`0` 关闭 |
| `app.ai.agent.retry-backoff-ms` | `400` | 上述重试的退避间隔 |
| `app.ai.agent.quota-floor-tokens` | `10000` | 剩余 token 低于此值不再开新工具批次，改为一轮「直接给结论」；0 关闭 |
| `app.ai.context.model-window-tokens` | `1000000` | 模型真实上下文窗口（`deepseek-chat` 由 `deepseek-flash` 服务，1M）；换模型只改这一项 |
| `app.ai.context.history-window-ratio` | `0.064` | 仅在 `history-max-tokens` 缺失时生效（0.064 × 1M = 64K）；生产固定用绝对预算 |
| `app.ai.context.history-max-tokens` | `64000` | 回放历史的绝对预算（tokens）。回放预算是成本杠杆，不随窗口放大；`0` = 旧版按 `max-history-turns` 轮数 |
| `app.ai.context.compact-trigger-ratio` | `0.8` | 未压缩积压达到回放预算的多少比例就压缩（0.8 × 64K ≈ 51.2K）；必须 < 1，`0` 关闭该触发 |
| `app.ai.context.compact-keep-turns` | `2` | 压缩后保留最近 2 轮原文（4 条消息） |
| `app.ai.context.summary-input-max-chars` | `24000` | 单次摘要调用最多吃多少字转录；积压更大时分多趟（每轮最多 4 趟） |

聊天、embedding、联网搜索**各自独立**：聊天走 DeepSeek 的 OpenAI 兼容接口（`https://api.deepseek.com`，`deepseek-chat`，`AI_CHAT_API_KEY`，未设时回落到 `DEEPSEEK_API_KEY`），embedding 走 SiliconFlow（`Qwen/Qwen3-Embedding-0.6B`，`AI_EMBEDDING_API_KEY`，未设时回落到 `SILICONFLOW_API_KEY`），联网搜索走 DeepSeek 搜索 API（`DEEPSEEK_API_KEY`）。密钥都从环境变量读取；缺失时服务仍可启动，但对应能力不可用（`/api/ai/status` 的 `webSearchEnabled` 反映真实状态）。注意 Spring AI 2.x 的请求 URL = base-url + 固定路径（`/chat/completions`、`/embeddings`），base-url 必须带版本段。

## 3. 文档语料与索引

- 语料位置：`nitrowater-server/src/main/resources/ai/docs/{nitrowater,velochatx}`，支持 `classpath:` 与文件系统路径。
- 每个 collection 有独立的 `corpus`、`public-base-url` 与 `description`，检索与引用都按 corpus 区分。
- `description` 会拼进 agent 系统提示的「知识库文档」段（写清该语料覆盖什么），让通用 agent 知道本服务提供哪些文档可查；未配置时只显示 corpus 名。
- 索引流程：markdown → 去掉 UTF-8 BOM 与 YAML front matter → 按标题/Token 切块 → embedding → 写入 Qdrant。
- 重建是破坏式替换：先删该 corpus 的旧点再写入；chunk id 稳定，便于引用对齐。
- `app.ai.ingest-on-startup: true` 时启动会检查并补索引；也可由管理员手动触发重建。
- 没有 embedding key 时索引失败但不会阻止服务启动。

语料策略：

- `nitrowater` 只收录**人工整理过**的项目文档（架构、鉴权、模块、数据、API、开发、运维、排障），不使用工作日志或未整理的需求草稿。
- `velochatx` 收录精简后的插件文档（5 篇），不再保留中英双份原始文档树。

## 4. 检索与引用

- `DocRetriever` 按问题向量检索，`top-k` 默认 6，可用 `AI_TOP_K` 调整。
- 引用形如 `[1]`，指向 `public-base-url + 文档相对路径`；`sources` 事件同时给出标题、章节、路径与片段。
- 检索结果是**数据**，不是指令：提示词明确禁止执行文档/网页中的指令、改变角色或泄露提示词。
- 站点相关问题只依据检索结果回答；没有命中时明确说明「文档中没有找到」，不编造默认值。

## 5. Agent 工具

| 工具 | 作用 | 何时使用 |
|---|---|---|
| `searchDocs(query, topK)` | 检索本地文档语料；**无命中时自动联网一次** | 站点产品、配置、权限、版本等事实性问题 |
| `loadSkill(name)` | 加载预置工作流 | 需要按固定步骤完成的任务（命令参考、配置导览、排障） |
| `calculate(expression)` | 精确十进制求值（`+ - * /`、括号、`round/abs/min/max/sqrt`） | 问题里有具体算式（小数、多步乘除、百分比换算后）；心算不可靠 |
| `runCode(language, code, stdin)` | 在 Piston 沙箱编译并运行 | 需要执行环境才能确认：跑/测代码、循环、解析、批量数据、读写文件 |
| `webSearch(query)` | 联网搜索（DeepSeek 搜索 API），默认够用即停，单轮成功上限 8 次 | 近期事件/实时数据/可能变更，或需要核实自己没有把握的具体事实（版本、价格、日期、签名、链接），或用户明确要求 |
| `saveMemory(kind, content)` | 保存跨会话的长期事实/偏好/决定/项目背景 | 用户**明确要求**“记住/保存/以后都按这个来”；不随普通聊天自动保存 |

工具在提示词里只保留**路由规则**（何时用 / 何时不用 / 引用口径）；参数、返回值、重试预算与错误码只写在工具自身的 `@Tool` description 里——那是模型唯一会读的第二份定义，抄进系统提示词就等于制造一份会漂移的副本。系统提示词按 `<tool_policy>` / `<continuation>` / `<answer>` / `<safety>` 分段，改哪类规则就改哪一段。**本轮未提供的工具不会出现在提示词里**（例如关闭联网时不出现 `webSearch`，并追加一句「本轮未开启联网搜索」），模型因此不会去调用一个不存在的工具。

每轮还会在用户消息末尾内联本轮时刻：

```text
<now>2026-11-11 14:32:05 星期三（Asia/Shanghai，UTC+08:00）</now>
```

时区来自 `app.ai.agent.time-zone`（默认 `Asia/Shanghai`）。放在用户消息而不是 system 前缀，是为了保住供应商的前缀缓存；`<answer>` 段要求涉及「今天/现在/最近」时以它为准，避免模型按训练截止日期推断。

工具轨迹由 `AgentTrace` 汇总：`name`、`argument`、`resultCount`、`status`（`ToolCode` 码，如 `tool.ok`）、`ok`、`summary`、`render`（展示级别，见 5.2.1）、耗时。SSE 在结束前发送 `tools` 与 `skills`；运行期间的 `step` 事件渲染成答案上方的实时卡片，结束（或重新加载）后由持久化的 `tools` 用同一套卡片展示。

引用、工具轨迹与技能名随回答一起落库（`sources_json` / `tools_json` / `skills_json`），所以刷新或切换会话后过程卡片仍然完整。`conversation` / `start` / 首个 `step{thinking}` 在请求处理器返回时即写出，早于上下文组装，客户端不必等首个 token 才显示过程卡片。

「继续生成」复用同一条 assistant 行：新文本追加、`tokens` 累加、引用/工具/技能合并（重复的引用编号会顺延），不会在库里留下两条半截回答。

### 5.1 技能

- 内置技能在 `resources/ai/skills/<name>/SKILL.md`（随 jar 发布）；管理台新增 / 导入的技能存 `ai_skill`，同名时数据库行覆盖内置，删除行即恢复内置。
- 当前内置：`command-reference`、`config-walkthrough`、`troubleshooting`。
- **热加载**：`SkillRegistry` 每轮都从数据库读管理行，没有缓存、没有 reload 按钮；保存 / 导入 / 删除即对下一轮对话生效，多副本天然一致。
- 管理台支持导入 `SKILL.md`（front matter + 正文）与导出（内置也可导出，改完再导入即成为覆盖行）；导入时若 front matter 没有 `description`，取正文第一行作为目录摘要。
- 系统提示词列出全部技能与适用场景，模型按需 `loadSkill`；未显式选择的技能不会内联进提示词。

### 5.2 工具注册与门禁

工具在 `AgentToolRegistry` 一处登记，四个事实互相独立，**不得合并成一个布尔**：

| 事实 | 来源 | 含义 |
|---|---|---|
| `available` | 注册时的 `probe`（目前只有 `webSearch` 用它探 API key） | 运行时依赖是否就绪，与权限无关 |
| `feature` | `ToolFeature`（DOCS/SKILLS/CODE/WEB/NONE） | 管理员功能开关；同时是控制台「来源」列的取值（`ToolFeature.bucket()` → `docs/skills/code/web/core`） |
| `disabledTools` | `ai_admin_setting.disabled_tools` | 管理员对单个工具的隐藏 |
| `optIn` | 请求里的 `allowWeb` | 用户本回合是否保留联网（默认开，只有显式关闭才为 `false`） |

- 组合规则只有 `AgentTool.advertised(...)` 一处；新增门禁改这里，不要在每个调用点重算。
- 控制台 `AiToolInfo` 分别上报 `enabled`（配置）/ `optIn`（需用户同意）/ `available`（运行时可用），所以「管理员关了」与「用户没勾选」不会被压成同一个 false。
- 新增工具：在注册表加一行 `scan(...)`；新增分类：给 `ToolFeature` 加一个枚举值并补 `bucket()`，控制台会自动多一个来源标签（前端未映射时回落显示原始值）。

### 5.2.1 展示级别（RenderPolicy）

工具调用**永远**进协议、进轨迹、进限额；`RenderPolicy` 只决定客户端怎么画，三档语义与业内一致（AI SDK 的 tool part、LangGraph 的 `tools` 通道、Anthropic 的 `response_inclusion`）：

| 级别 | 用于 | 表现 |
|---|---|---|
| `FULL` | 外部调用与写操作，默认 | 完整步骤卡 |
| `COMPACT` | 确定性本地计算（`calculate`） | 一行紧凑 chip，仍能看到算式 |
| `EXCLUDED` | 结果被回答管线内部消费、不是用户动作 | 实时与历史都不渲染，`tools` 里仍保留 |

- 策略挂在 `AgentTool.render()`，每轮由 `AiAgentService` 写进 `AgentTrace`，所以实时 `step` 与持久化 `tools` 用同一份策略，不会出现「运行时看不见、刷新后又冒出来」。
- `EXCLUDED` 只在传输层跳过 `step` 与前端渲染，**不删轨迹**：评测、管理台统计、排障照旧。
- 三档都不改模型可见的 schema、不改系统提示词、不改 `max-total-tool-calls` 与单工具限额。
- 客户端另外把**连续同名 `FULL` 调用**折成一行（`2 次检索 · 8 条结果 · 3.2s`），可展开看每次的 query；`COMPACT` 与轮次边界（`thinking`）从不参与折叠，任一调用失败则该行自动展开。折叠是纯展示，协议、轨迹、限额计数都不变。

### 5.2.2 工具调用限额

- `max-total-tool-calls: 18`：单轮所有工具调用总数（Spring AI 自己计数，含失败与重复）。**模型轮次上限是另一项配置**（`app.ai.agent.max-rounds`，默认同为 18）：一轮可以带多个工具调用，所以两者数值相同但含义不同，调工具预算时不要把轮数一起带着改。
- **单次工具结果上限 16000 字符**（`ToolResponse#json` 统一兜底）：结果会在后续每一轮重发，失控的载荷等于每一轮都付一次；超限时 `data` 变成 `{truncated, originalChars, preview}`，信封仍可解析，模型能据此换更窄的参数。
- `max-calls-per-tool-default: 3`：单个工具默认上限。
- `webSearch: 12`：必须**高于** `app.ai.web-search.max-successes`（8）。框架按调用次数算，重复 query、失败、重试都占一次；工具自身只按成功算。两者相等时第 8 次成功搜索会被框架先拒掉。
- `runCode: 4`：第 4 次必须放行，让工具自己返回 `retry_exhausted`；若这里回退成默认 3，模型永远看不到该状态（`ToolLimitBindingTest` 守住这条）。
- `calculate: 6`：多步算术常超过默认 3 次；它是纯 CPU 且渲染为 COMPACT 一行，多调用不产生 token 费用。
- 超限策略：`on-limit-exceeded: RETURN_ERROR_RESPONSE`，把错误回给模型而不是抛异常。
- **与 token 配额的关系**：工具返回的结果会进入下一轮的 prompt，且每一轮都会重新发送累积的上下文，所以联网搜索的 token 成本已经计入每日配额（每轮 provider 的 `usage` 都会累加进 `AiUsageService` 会话）。放宽搜索次数会同步抬高单轮 token 成本——8 次检索的一轮可达数万 token，默认单人额度 2,000,000 够这种回合跑几十次（见 5.2.4）。

### 5.2.4 token 计量与每日配额

配额是「每用户 × 每 UTC 日」，存在 `ai_daily_usage(uid, usage_date, input_tokens, output_tokens, reserved_tokens, request_count, estimated_requests)`；同一张表里 `uid=0` 是全站哨兵行，限额来自 `ai_admin_setting` 的单例行（`daily_token_limit` 默认 2,000,000，`global_daily_token_limit` 默认 40,000,000，`global_quota_enabled` 默认开）。

> 额度与上下文窗口是一对：回放预算从 4K 提到 **64K** 后，一个开启工具的长回合每轮都要重发这 64K，`V1_2__scale_token_defaults_to_context_window.sql` 因此把单人额度从 500K 提到 2M（全站 20M → 40M）。只改窗口不改额度，等于把一天从 ~40 个回合压到个位数；64K 下 2M 大约换 29 个纯回答回合或 14 个带一轮检索的回合（历史已经装满时）。

| 阶段 | 怎么算 |
|---|---|
| 准入（`reserve`） | 估算首轮 prompt：**CJK 1 字 = 1 token、其余 4 字符 = 1 token**（`AiTokenUsageMeter#estimateTokens`），再加 `spring.ai.openai.chat.max-tokens`（4096）作为本次预留写进 `reserved_tokens`，`request_count += 1`（**按 AI 请求/回合计，不是按模型轮次**） |
| 准入判定 | **软门槛**：只有当天 `input + output + reserved >= 限额` 才拒绝（`token_quota`）；已放行的一轮一定跑完，最多超额一轮的「估算 + max-tokens」。并发由单例行锁 + 用户行锁串行化，全站行按同一锁序先查 |
| 计量（`record`） | 每轮模型调用的 `usage.prompt_tokens / completion_tokens` 累加到本轮会话；Spring AI 默认已请求 `stream_options.include_usage`，所以**工具轮次也照实计入**（工具调用参数算输出 token） |
| 增量提交（`flush`） | **每个模型轮次结束**把「上次提交之后的增量」写进当天行（`input/output += delta`），**不动预留**。这样一个跑 18 轮的长回合在进行中就已被其他回合看见，而不是结算时才一次性出现 |
| 结算（`settle`） | 释放预留，并把**尚未提交的差额**加到当天行（`total - flushed`，不会重复计）；供应商没回 usage 时退回「首轮 prompt 估算 + 回答字符数/4」，并把 `estimated_requests += 1`（兜底不含工具结果与工具参数，属于**低估**） |
| 查询/重置 | `/api/ai/slash/quota`（本人）、`/api/admin/ai/usage*`（管理端）；`resetToday` 清当天行（连预留一起清，用于泄漏恢复），`resetGlobalToday` 即「解除全站熔断」；历史行按保留策略定期清理 |

一个回合的 input 是**逐轮累加**的：第 N 轮要重发 system + 历史 + 前 N-1 轮的全部工具结果，所以工具的成本是「结果体积 × 剩余轮数」而不是「一次调用」，这也是放宽检索次数会显著抬高单轮成本的原因。

**轮内护栏**：每次要执行工具批次之前，`AiAgentService` 用 `AiUsageTracker#remainingTokens()` 判断剩余额度。它**读当天行**（`limit − 别人的消耗 − 本回合已消耗`），而不是准入时的快照：同一天的其他回合只要 flush 过就会算进来。低于 `app.ai.agent.quota-floor-tokens`（默认 10000）就不再执行工具，改为**不带工具**跑最后一轮，并追加一条「额度即将用完，直接给结论」的用户提示。读库失败时退回快照（记账读不到不能挡住有额度的回合）；`0` 表示关闭护栏（只剩准入检查）。

**超额上界**：单回合最多多花「护栏阈值 + 最后一轮」≈ 1 个模型轮次（64K 窗口下约 7 万 token，≈ 日额度的 3%）；并发回合各自也在同一本账上收敛，因为每个回合在轮边界都会先提交再判定。

### 5.2.3 两级时限与重试边界

| 层级 | 位置 | 行为 |
|---|---|---|
| 单轮 | `AiAgentService`（`app.ai.agent.turn-timeout-ms`） | 到期即结束本轮：下一轮不启动，工具批次不再执行；流式发 `error{code=turn_timeout}`，非流式回 504 |
| 单次工具 | `StepTrackingToolCallback`（注册表里的 `AgentTool.timeoutMs`：检索 25s / DB 10s / 沙箱与联网 0 = 工具自负） | 到期返回**可重试结果** `tool.timeout`（`success=false, retryable=true`），模型可换参数再试一次；卡片以 `tool.timeout` 收尾 |
| 单轮模型调用 | `AiAgentService.driveRound`（`app.ai.agent.retry-attempts`） | **只在还没有任何 token 发给客户端时**重试（503/429/连接重置），默认 1 次、退避 400ms；已发出 delta 不重试（会重复），回合超时不重试（预算已尽）。重试用 `Flux.defer` 重新发起请求，不是重订阅旧流 |

- 超时只是"不再等"，不是取消：JDBC/HTTP 调用可能仍在跑，其结果被丢弃。因此不能承诺副作用没发生。
- **工具层不设通用重试**：`saveMemory` 是写、`runCode` 花钱、`searchDocs` 只是慢。是否重试由模型看到 `retryable` 后决定。
- 用户想接着来时走新一轮（`continueGeneration` 语义），历史里保留那条 `tool.timeout`，模型自然知道上次卡在哪。

### 5.3 联网触发策略

`searchDocs` 内部做了确定性兜底，不依赖模型自觉：

```text
searchDocs(query)
  ├─ 本地命中 → 返回 kind=doc 片段
  └─ 本地 0 条
        ├─ app.ai.web-search.auto-fallback=true 且 provider 可用
        │     → 用同一个 query 调一次 DeepSeek 搜索（默认 3 条）
        │     → 结果标 source=web，`data.fallback=web`
        │     → 每轮最多一次（AgentTrace#claimWebFallback）
        └─ 否则 → 照常返回「没有匹配的文档片段」
```

- 触发条件是**检索结果为空**，不是模型判断“够不够”；
- 每轮最多一次自动兜底，且与显式 `webSearch` **共用同一份成功预算**：已搜过的 query 不再重复搜，预算耗尽后连兜底也不再联网；
- `webSearch` 工具保留：用于近期事件、实时数据、价格、可能变更的信息，**或需要核实自己没有把握的具体事实**（版本号、签名、默认值、链接），或用户明确说“搜索/查一下”；**单轮成功上限 8 次**（默认首查就够，只有存在明确缺口才继续；8 次对应「每个子问题一到两条 query」的多段检索），超限返回 `tool.limit_reached`；
- 失败处理：provider 对 I/O、超时、5xx、429 自动重试一次，4xx 直接失败；失败不占成功预算，兜底失败会释放占用，下次文档未命中仍可再试；
- 返回体在 `data` 里带 `remaining`（剩余成功次数），`message` 里给出「够用即停」的提示；
- 稳定事实、数学、编程基础、创作、用户已提供的资料**不联网**。

### 5.4 过程展示与上下文

- 正在做什么由界面上的步骤卡片展示（`step` 事件）；答案里**不写过程文字**（不说“正在查阅文档/正在联网搜索”），也不说“正在思考/请稍等”，不编造条数或结果状态。
- 工具结果只取结论：`searchDocs` 单条截到 1200 字，联网结果正文 1200 字（界面预览 600 字）；只有缺失细节是回答所必需时才再检索，不凭记忆补全。
- 会话历史按 **token 预算**装填（`history-max-tokens`，默认 64K；缺失时按 `model-window-tokens` × `history-window-ratio` 推导）：从新到旧累计，装不下即停，再按时间顺序回放；摘要也计入同一预算。预算为 `0` 时回退到 `max-history-turns`。
- **压缩与窗口同口径**：自动压缩有两个触发（谁先到算谁）——未压缩积压 ≥ `compact-trigger-messages`（默认 32 条，防「很多条很短的消息」），或积压估算 ≥ **回放预算 × `compact-trigger-ratio`**（默认 0.8 × 64K ≈ 51.2K token）。token 触发由回放预算推导、且恒小于预算：窗口装不下的第一条消息已经从提示词里消失，若等到积压超过窗口才压缩，模型会有一段时间既看不到原文、摘要里也没有它（「摘要空档」）。积压超过单次摘要输入上限时分多趟折叠（每轮最多 4 趟），下一轮继续。
- token 估算按 **CJK 1 字 = 1 token、其余 4 字符 = 1 token**：中文用 `(字符+3)/4` 会低估约 3 倍，而窗口、触发与配额都用这个单位。
- **预算与渲染共用一把尺子**（`PromptBuilder#historyTurnTokens`）：装历史、判压缩、渲染提示词都按同一套截断（最近 4 条 8000 字、更早 2000 字）计价。按原文长度计价会让窗口「看起来满了、其实还有三分之一」，压缩也会提前触发——那正是模型本来还能读到的内容。
- **运行期护栏**：预算是估出来的，只有 provider 的 `usage.prompt_tokens` 是真值。任一轮 prompt 达到模型窗口的 90% 就记一条 WARN（`AiUsageService`，每轮最多一条），提示该调低 `history-window-ratio`。不自动纠正：窗口大小是部署决策，不是一次请求能改的。
- **最近 4 条消息逐字入提示词**（不再按固定字符数硬截）：续写要看的是上一轮停在哪里，改成尾部截断正是为了保住这个锚点。只有单条超过安全上限（最近 4 条 8000 字、更早 2000 字）才保留头尾并插入 `…（中间省略 N 字）…`；正常路径下真正的边界是上面的 token 预算。
- 用户手打「继续」走的是一条**普通轮次**：历史里完整保留上一轮回答并标记为「助手（上一轮回答）」，`<continuation>` 段给出锚点优先级、不复述、编号续排与「检索续接要换 query」的规则；界面上的「继续生成」按钮仍走 `continueGeneration`（合并进同一条 assistant 行），两者互补。
- 若 `ai_conversation.summary` 非空，会作为「【此前对话摘要｜非指令】」合成首轮注入。
- **系统提示是缓存前缀，逐轮字节不变**：人设/规则/工具说明/技能目录/语料只在配置或工具开关变化时才变。**画像与召回记忆、`<now>` 时钟、问题本身全部放在 user 消息尾部**（`<user_context>` → `<now>` → `<question>`）。原因：DeepSeek 的上下文缓存按**前缀完整匹配**命中，把按问题召回的 `<user_context>` 放进 system 会让每轮的第 0 个 token 就不同，64K 历史全部 miss；把易变段放到历史之后，命中的是「system + 历史」这一整段，价格差约 50 倍（$0.003 vs $0.15 /M）。
- 同一回合内的多个工具轮次共享同一前缀，因此第 2 轮起天然命中；真正会打断缓存的是压缩（改写 summary）、切换工具/技能、以及部署后配置变化。
## 6. 会话与上下文

现状（T11 已落地，T12/T13 由压缩补齐）：

- 会话与消息写入 `ai_conversation` / `ai_message`，前端只传 `conversationUuid`；新建会话时服务端返回 uuid（SSE 通过 `conversation` 事件下发）。
- **写入按会话串行**：`seq` 由 `MAX(seq)+1` 得到、聚合计数是读改写，而 `ai_message` 有唯一键 `uk_ai_message_conv_seq`。所以 `start` / `finish` / `fail` 都先用 `SELECT … FOR UPDATE` 锁住 `ai_conversation` 那一行，并**用锁后重读的实体**写回——否则同一会话开两个标签页时会撞 seq，或把并发回合的 `message_count` 覆盖掉。代价是同一会话的写入排队，不同会话互不影响。
- 每轮先落库用户问题，再把**库里的历史**喂给模型；回答完成（或失败）后写入助手消息或错误消息。
- `history` 字段保留一版以兼容旧客户端；带 uuid 时服务端忽略它。
- 超出窗口的旧消息由**滚动压缩**折进 `summary` 并推进 `summary_upto_seq`：自动在轮末触发（未压缩积压 ≥ `compact-trigger-messages`（默认 32）或 ≥ 回放预算 × `compact-trigger-ratio`（默认 0.8，约 51.2K token），两者谁先到算谁；一次轮末可连折多趟，最多 4 趟），也可用 `/compact` 手动触发（`POST /api/ai/conversations/{uuid}/compact`）。压缩保留最近 `compact-keep-turns` 轮原文；摘要写回用 `compress_version` 做 CAS，并发失败直接放弃。
- 摘要与记忆同等级别：`OutputRedactor` 脱敏 + `ContentGuards` 正则 + `ModerationDirection.SUMMARY` 复检，命中即丢弃摘要（退化为 `/clear` 语义，不影响当轮）。
- `GET /api/ai/conversations/{uuid}/context` 返回边界、摘要与两侧消息数，供前端解释「以上已压缩」。
- 长工具输出可被剪枝为占位符（`is_pruned=1`），原文仍保留在库中。
- 跨会话记忆进入 `ai_memory`，Qdrant 只做检索影子，MySQL 是唯一事实来源。

## 7. 限流

| 配置 | 默认 | 说明 |
|---|---|---|
| `app.ai.per-user-per-minute` | 20 | 每用户每分钟请求 |
| `app.ai.per-user-per-day` | 300 | 每用户每天请求 |
| `app.ai.max-question-chars` | 2000 | 单条问题字符硬上限 |

## 8. 管理台

`/admin/ai`（需管理员）展示：索引状态、corpus 列表、可用 skills、可用 tools，并提供重建索引按钮（调用 `/api/admin/ai/reindex`）。

## 9. 内容安全（违禁词风控）

> 定位：**合规兜底 + 可处置手柄**，不是安全边界。词表拦不住谐音、拆字、跨语言与代码藏词，所以目标只有三件事：可疑文本不进库、处置留痕可审、对重复输入做冷却而不是封号。

### 9.1 三种编码与三种判定

| 维度 | 取值 | 说明 |
|---|---|---|
| `ai_admin_setting.moderation_mode` | `0=OFF / 1=LOG / 2=ENFORCE` | 默认 `1`（影子模式）：只记录不拦截，先观察命中和误判，再收紧 |
| `direction` | `0=INPUT / 1=OUTPUT / 2=MEMORY / 3=PROFILE` | 命中发生在哪一段 |
| `action` | `0=LOGGED / 1=BLOCKED / 2=MASKED` | LOG 一律 `LOGGED`；ENFORCE 下输入与存储被拒为 `BLOCKED`，回答被替换为 `MASKED` |

### 9.2 闸门位置

| 位置 | 触发点 | LOG | ENFORCE |
|---|---|---|---|
| 用户输入 | `ConversationService.start()`，落库与调用模型之前 | 记录 | 400 `content_blocked`，不产生 `ai_message` 行 |
| 模型回答 | `AgentTurnService`（同步与流式各 1 处） | 记录 | 落库存**遮蔽后**文本；流式再发 SSE `moderation{text}` 让前端替换气泡 |
| 长期记忆 | `AiMemoryService.save()` | 记录 | 拒绝写入 |
| 画像偏好 | `AiProfileService` 写入时逐条校验；`promptBlock()` 读取时再过滤历史脏数据 | 记录 | 400 `content_blocked` |

### 9.3 风控计数（只算输入）

只有 INPUT 命中计分：Redis `nitrowater:ai:guard:strike:{uid}` 在 `strike-window-minutes` 内累计，达到 `strike-limit` 时写入 `nitrowater:ai:guard:cooldown:{uid}`（TTL = `cooldown-minutes`），冷却期内 AI 接口直接返回 429 `content_cooldown`。OUTPUT / MEMORY / PROFILE 命中只记录——那多半是模型漂移或引用文档原文，不是用户的尝试，不应据此惩罚账号。

### 9.4 词表与运维

- 引擎 `com.github.houbb:sensitive-word`（不自带词库）；词表 `resources/ai/moderation/words.txt` + `allow.txt`，可用 `AI_MODERATION_DICT` / `AI_MODERATION_ALLOW` 指向挂载文件，生产词表不入仓。
- 误判处置：把词加进 allow（或从 dict 删除）→ 管理台「重载词表」（`POST /api/admin/ai/moderation/reload`）→ 下一个请求即生效，无需重启或重新部署。
- 审计表 `ai_moderation_hit`：只存命中词，不存原文；`/admin/ai` 可按来源/处置筛选，按 `id` 游标翻页；`AI_MODERATION_RETENTION_DAYS`（默认 90 天）到期后由 `AiModerationMaintenance` 夜间 cron 清理。
- `ai_message.moderated=1` 记录该回答曾被遮蔽，前端据此在重新加载会话后仍显示「内容已过滤」徽标。

### 9.5 已知边界

同音字/火星文、跨语言、Base64 与代码片段、图片内容均不覆盖；词表命中也不等于语义违规。它保证的是：被判定的文本不会进入对话历史、记忆或画像，并且处置可追溯、可申诉。

### 9.6 输出脱敏、提示泄露与记忆加固

| 能力 | OFF | LOG | ENFORCE |
|---|---|---|---|
| 输出脱敏（密钥 / JWT / 私钥 / 连接串口令 / 邮箱 / 手机号 / 内网 IP） | 生效 | 生效 | 生效 |
| 系统提示泄露（canary `NITROBOT-INTERNAL-PROMPT-7F3A`） | 不检测 | 记录，不改写 | 整段替换为固定拒答 |
| 记忆加固（单行化、≤200 字、禁密钥/手机号/身份证、禁指令式文本、单用户 ≤200 条） | 生效 | 生效 | 生效 |

- 脱敏与记忆加固**不随 `moderation_mode` 变化**：前者是数据卫生（把密钥原样回吐给用户与内容政策无关），后者是资源与注入面约束，因此 `OFF` 下同样生效。
- 脱敏命中会写一条 `OUTPUT/MASKED` 审计（`words` 记规则名，如 `openai_key,private_ip`），并令 `ai_message.moderated=1`。
- 泄露检测只用 canary：命中即确定泄露、误报为零；模型改写式复述（不含 canary）不覆盖，需分类器，属后续迭代。
- 记忆只接受「陈述事实/偏好」：角色标记（`<|im_start|>` 等）与「忽略以上规则」类文本直接拒绝，避免把记忆变成持久化的系统提示。
- 脱敏只覆盖已知形态（带前缀 token、真实密钥形状、RFC 1918 段）；自由文本里的口令不保证识别。


## 10. 相关源码

- `service/ai/DocChunkBuilder.java`、`DocCollection.java`、`DocsIndexService.java`、`DocRetriever.java`、`PromptBuilder.java`
- `service/ai/model/agent/AiAgentService.java`、`AgentTrace.java`、`AgentRun.java`、`PreparedAgent.java`、`StepTrackingToolCallback.java`、`ToolCall.java`、`AgentTool.java`、`ToolFeature.java`
- `service/ai/model/tool/ToolResponse.java`、`ToolCode.java`
- `service/ai/tool/AgentToolRegistry.java`（唯一注册表：扫描 `@Tool`、按开关 + 停用列表 + 本轮 opt-in 过滤）、`DocTools.java`、`SkillTools.java`、`RunCodeTool.java`、`WebSearchTool.java`、`MemoryTools.java`
- `service/ai/SkillRegistry.java`
- `service/ai/AgentTurnService.java`（一轮对话的编排：落库 / 模型 / 风控 / SSE 协议）、`AiStatusService.java`、`AiToolCatalogService.java`、`AiAdminReportService.java`
- `web/AiAgentController.java`（仅传输层）、`web/AiController.java`、`web/admin/AdminAiController.java`
- `web/dto/`（请求体）、`web/vo/`（响应体，`from(...)` 把 service model 转成线形状）
- `service/ai/model/`（`AgentTurnCommand`、`AgentTurnResult`、`AiStatusSnapshot`、`SkillRow`/`SkillDetail`/`SkillDraft` 等与 HTTP 无关的 service model）
- `nitrowater-bff/.../web/AiStreamProxyController.java`
