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
| `spring.ai.openai.chat.base-url` | `https://open.bigmodel.cn/api/paas/v4` | OpenAI 兼容聊天接口（智谱） |
| `spring.ai.openai.chat.model` | `glm-5.3-flash` | 便宜、速度优先 |
| `spring.ai.openai.chat.temperature` | `0.2` | 事实型回答，低温度 |
| `spring.ai.openai.chat.max-tokens` | `1200` | 单轮回答上限 |
| `spring.ai.openai.embedding.base-url` | `https://open.bigmodel.cn/api/paas/v4` | 可换成任意兼容实现 |
| `spring.ai.openai.embedding.model` | `embedding-3` | 1024 维（支持 256/512/1024/2048） |
| `spring.ai.vectorstore.qdrant.*` | `localhost:6334` | gRPC；`initialize-schema: false` |
| `spring.ai.tools.limits.*` | 总调用 10 / 单工具 3 / runCode 4 | 超限返回错误响应而不是抛异常 |
| `app.ai.web-search.endpoint` | `https://api.deepseek.com/v1/tools/search` | DeepSeek 搜索 API |
| `app.ai.web-search.api-key` | `${DEEPSEEK_API_KEY}` | 搜索用 key，可与聊天共用 |
| `app.ai.web-search.auto-fallback` | `true` | 本地文档无命中时自动联网一次 |
| `app.ai.web-search.max-results` | `3` | 单次联网取几条（显式搜索与自动兜底共用） |
| `app.ai.web-search.max-successes` | `3` | 单轮成功联网上限（失败/重试不计数） |
| `app.ai.web-search.max-attempts` | `2` | 单次调用的 HTTP 尝试上限（首查 + 一次重试） |
| `app.ai.web-search.retry-backoff-ms` | `300` | 可重试失败之间的间隔 |

聊天、embedding、联网搜索**各自独立**：聊天与 embedding 都走智谱 GLM 的 OpenAI 兼容接口（`https://open.bigmodel.cn/api/paas/v4`，共用 `GLM_API_KEY`，embedding 只做向量化），联网搜索走 DeepSeek 搜索 API（`DEEPSEEK_API_KEY`）。密钥都从环境变量读取；缺失时服务仍可启动，但对应能力不可用（`/api/ai/status` 的 `webSearchEnabled` 反映真实状态）。注意 Spring AI 2.x 的请求 URL = base-url + 固定路径（`/chat/completions`、`/embeddings`），base-url 必须带版本段。

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
| `runCode(language, code, stdin)` | 在 Piston 沙箱编译并运行 | 用户明确要求运行/测试/验证代码 |
| `webSearch(query)` | 联网搜索（DeepSeek 搜索 API），默认 1 次够用即停，单轮成功上限 3 次 | 近期事件/实时数据/可能变更，或用户明确要求 |

工具轨迹由 `AgentTrace` 汇总：`name`、`argument`、`resultCount`、`status`（`ToolCode` 码，如 `tool.ok`）、`ok`、`summary`、耗时。SSE 在结束前发送 `tools` 与 `skills`，前端据此渲染「过程」折叠区；运行期间的 `step` 事件由步骤卡片实时渲染。

引用、工具轨迹与技能名随回答一起落库（`sources_json` / `tools_json` / `skills_json`），所以刷新或切换会话后「过程」折叠区仍完整。`conversation` / `start` / 首个 `step{thinking}` 在请求处理器返回时即写出，早于上下文组装，客户端不必等首个 token 才显示过程卡片。

「继续生成」复用同一条 assistant 行：新文本追加、`tokens` 累加、引用/工具/技能合并（重复的引用编号会顺延），不会在库里留下两条半截回答。

### 5.1 技能

- 内置技能在 `resources/ai/skills/<name>/SKILL.md`，外部技能在 `deploy/ai-skills/<name>/SKILL.md`，同名时外部覆盖内置。
- 当前内置：`command-reference`、`config-walkthrough`、`troubleshooting`。
- 系统提示词列出全部技能与适用场景，模型按需 `loadSkill`；未显式选择的技能不会内联进提示词。

### 5.2 工具调用限额

- `max-total-tool-calls: 10`：单轮所有工具调用总数。
- `max-calls-per-tool-default: 3`：单个工具默认上限。
- `runCode: 4`：第 4 次必须放行，让工具自己返回 `retry_exhausted`；若这里回退成默认 3，模型永远看不到该状态（`ToolLimitBindingTest` 守住这条）。
- 超限策略：`on-limit-exceeded: RETURN_ERROR_RESPONSE`，把错误回给模型而不是抛异常。

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
- `webSearch` 工具保留：只用于近期事件、实时数据、价格、可能变更的信息，或用户明确说“搜索/查一下”；**单轮成功上限 3 次**（默认首查就够，只有存在明确缺口才继续），超限返回 `tool.limit_reached`；
- 失败处理：provider 对 I/O、超时、5xx、429 自动重试一次，4xx 直接失败；失败不占成功预算，兜底失败会释放占用，下次文档未命中仍可再试；
- 返回体在 `data` 里带 `remaining`（剩余成功次数），`message` 里给出「够用即停」的提示；
- 稳定事实、数学、编程基础、创作、用户已提供的资料**不联网**。

### 5.4 过程展示与上下文

- 正在做什么由界面上的步骤卡片展示（`step` 事件）；答案里**不写过程文字**（不说“正在查阅文档/正在联网搜索”），也不说“正在思考/请稍等”，不编造条数或结果状态。
- 工具结果只取结论：`searchDocs` 单条截到 1200 字，联网结果正文 1200 字（界面预览 600 字）；只有缺失细节是回答所必需时才再检索，不凭记忆补全。
- 会话历史取最近 `max-history-turns` 轮（默认 4）；若 `ai_conversation.summary` 非空，会作为「【此前对话摘要】」合成首轮注入（摘要生成属 T12）。
## 6. 会话与上下文

现状（T11 已落地）：

- 会话与消息写入 `ai_conversation` / `ai_message`，前端只传 `conversationUuid`；新建会话时服务端返回 uuid（SSE 通过 `conversation` 事件下发）。
- 每轮先落库用户问题，再把**库里的历史**喂给模型；回答完成（或失败）后写入助手消息或错误消息。
- `history` 字段保留一版以兼容旧客户端；带 uuid 时服务端忽略它。
- 上下文仍按 `max-history-turns`（默认 4 轮）截取，超出部分留待 T13 的滚动摘要（`summary` / `summary_upto_seq` / `compress_version` 列已就绪）。
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
| 模型回答 | `AiAgentController`（同步与流式各 1 处） | 记录 | 落库存**遮蔽后**文本；流式再发 SSE `moderation{text}` 让前端替换气泡 |
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
- `service/ai/model/agent/AiAgentService.java`、`AgentTrace.java`、`AgentRun.java`、`PreparedAgent.java`、`StepTrackingToolCallback.java`、`ToolCall.java`
- `service/ai/model/tool/ToolResponse.java`、`ToolCode.java`
- `service/ai/tool/DocTools.java`、`SkillTools.java`、`RunCodeTool.java`、`WebSearchTool.java`、`MemoryTools.java`
- `service/ai/SkillRegistry.java`
- `web/AiAgentController.java`、`web/AiController.java`、`web/admin/AdminAiController.java`
- `nitrowater-bff/.../web/AiStreamProxyController.java`
