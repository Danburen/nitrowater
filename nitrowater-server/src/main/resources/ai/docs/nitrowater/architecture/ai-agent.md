# NitroBot：AI 文档助手与 Agent

NitroBot 是平台内置的 AI 助手，页面在 `/chat`。它有两种调用形态：普通 RAG（只基于检索到的文档回答）与 Agent（可调用工具）。产品上只暴露 Agent 形态，检索、技能、工具由服务端自动决定，不再给用户开关。

## 1. 能力与端点

| 端点 | 形态 | 说明 |
|---|---|---|
| `POST /api/ai/chat` | RAG | 一次调用，返回答案 + 引用 |
| `POST /api/ai/chat/stream` | RAG | SSE：`delta*` → `sources` → `done` |
| `POST /api/ai/agent/chat` | Agent | 完整一轮（含工具调用） |
| `POST /api/ai/agent/chat/stream` | Agent | SSE：`start` → `delta*` → `sources` → `tools` → `skills` → `done` |
| `GET /api/ai/status` | 状态 | 索引/模型可用性 |
| `GET /api/ai/skills` | 技能 | 可用技能清单（管理台与调试用） |
| `POST /api/admin/ai/reindex` | 管理 | 重建文档索引 |

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
| `app.ai.web-search.auto-fallback-count` | `3` | 自动联网取几条 |

聊天、embedding、联网搜索**各自独立**：聊天与 embedding 都走智谱 GLM 的 OpenAI 兼容接口（`https://open.bigmodel.cn/api/paas/v4`，共用 `GLM_API_KEY`，embedding 只做向量化），联网搜索走 DeepSeek 搜索 API（`DEEPSEEK_API_KEY`）。密钥都从环境变量读取；缺失时服务仍可启动，但对应能力不可用（`/api/ai/status` 的 `webSearchEnabled` 反映真实状态）。注意 Spring AI 2.x 的请求 URL = base-url + 固定路径（`/chat/completions`、`/embeddings`），base-url 必须带版本段。

## 3. 文档语料与索引

- 语料位置：`nitrowater-server/src/main/resources/ai/docs/{nitrowater,velochatx}`，支持 `classpath:` 与文件系统路径。
- 每个 collection 有独立的 `corpus` 与 `public-base-url`，检索与引用都按 corpus 区分。
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
| `webSearch(query)` | 联网搜索（DeepSeek 搜索 API），本轮最多 2 次 | 近期事件/实时数据/可能变更，或用户明确要求 |

工具轨迹由 `AgentTrace` 汇总：`name`、`argument`、`resultCount`、`status`、耗时。SSE 在结束前发送 `tools` 与 `skills`，前端据此渲染「过程」折叠区。

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
        │     → 结果标 source=web，payload 带 fallback=web
        │     → 每轮最多一次（AgentTrace#claimWebFallback）
        └─ 否则 → 照常返回「没有匹配的文档片段」
```

- 触发条件是**检索结果为空**，不是模型判断“够不够”；
- 每轮最多一次，避免模型换关键词反复联网；
- `webSearch` 工具保留：只用于近期事件、实时数据、价格、可能变更的信息，或用户明确说“搜索/查一下”；**本轮最多 2 次**（首查 + 改写一次），超限返回 `limit_reached`；
- 稳定事实、数学、编程基础、创作、用户已提供的资料**不联网**；
- `POST /api/ai/chat(.stream)`（纯 RAG 路径）没有联网能力，只有 Agent 端点有。

### 5.4 过程声明与上下文

- 调用工具前必须先输出一句「动作 + 对象 + 关键参数」的声明（如“正在查阅文档：chat-format 占位符”）；禁止“正在思考/请稍等”，禁止编造条数或结果状态，真实结果由工具轨迹渲染。
- 工具结果只取结论：`searchDocs` 单条截到 1200 字，联网结果 600 字；需要更多再检索，不凭记忆补全。
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

## 9. 相关源码

- `ai/DocChunkBuilder.java`、`DocCollection.java`、`DocsIndexService.java`、`DocRetriever.java`
- `ai/agent/AiAgentService.java`、`AgentTrace.java`、`PreparedAgent.java`
- `ai/tool/DocTools.java`、`SkillTools.java`、`RunCodeTool.java`、`WebSearchTool.java`
- `ai/skill/SkillRegistry.java`
- `web/AiAgentController.java`、`web/AiController.java`、`web/AdminAiController.java`
- `nitrowater-bff/.../web/AiStreamProxyController.java`
