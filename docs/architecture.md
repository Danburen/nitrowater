# NitroWater 项目总结与架构快照

> 这是一份**自包含**的项目说明：即使不能读取仓库，也能理解 NitroWater 做了什么、由哪些部分组成、完成到什么程度。
>
> 更新：2026-10-30。第 9 节是“仓库可访问时”的后续 Agent 入口；如果只能拿到本文，读第 0-6 节即可（第 8 节是术语表）。

---

## 0. 一分钟版

NitroWater 是一个面向 **Minecraft 服务器主与插件生态**的多模块平台，把通常分散的几件事收进同一个工程：

- **统一身份**：自建 SSO / OIDC 身份中心，支持登录、注册、验证码、RBAC、管理控制台。
- **BFF Token Handler**：浏览器不保存 access/refresh token，只持有 HttpOnly 会话；BFF 负责续期和代理。
- **业务平台**：门户、服务器工具箱、插件中心、资源服务、代码运行器。
- **文档站**：VeloChatX 的 VitePress 双语文档站，经 nginx 与 BFF 同源挂载。
- **AI Agent**：唯一的问答路线——模型按需调用工具（检索、技能、联网、代码沙箱），带工具预算、轨迹、配额和 SSE 步骤卡片。
- **工程化**：Gradle 多模块、Flyway 三库隔离、Docker compose、一键启停/播种/同步脚本、单元测试与前端构建验证。

一句话：**它已经是一个可运行、可演示、端到端闭环的 Minecraft 服务器生态平台，而不是只有页面的原型。**

---

## 1. 项目定位：解决什么问题

目标用户是 Minecraft 服务器主、插件作者和插件使用者。常见痛点是：

1. **工具分散**：MOTD、server.properties、server-icon、启动脚本、插件包处理等工具散落在不同网站。
2. **插件文档难查**：文档、命令、权限、配置项、占位符分散在仓库、Wiki 和群聊里，中英文还可能不同步。
3. **身份不统一**：多个子系统各自登录，token 暴露在浏览器，权限模型不一致。
4. **代码片段不可运行**：文档里的示例只能看，不能直接跑；服务器主缺少安全的在线运行环境。

NitroWater 的解决方式是把这些能力收敛到一个同源平台：**一个账号、一个门户、一个插件中心、一套文档、一个能引用文档的 AI 助手**。

---

## 2. 项目做了什么（完成度清单）

| 领域 | 已完成内容 | 具体表现 |
|---|---|---|
| 统一身份 | SSO / OIDC 身份中心 | 授权码登录、refresh、RP-initiated logout、JWKS、userinfo；托管登录/注册/验证码；account 管理控制台 |
| 权限 | RBAC 与审计 | `SUPER_ADMIN` / `ADMIN` / `USER`；JWT 带 `uid`、角色、设备指纹；账号审计；字段加密 |
| BFF | Token Handler | 浏览器只有 HttpOnly `BFFSESSION`；AT/RT 存服务端；自动续期；统一 401 JSON；发布可失效旧会话 |
| 门户 | 复合前端 | Vue 3 + TypeScript + Vite + Tailwind；门户、工具箱、插件中心、文档入口、NitroBot、管理端 |
| 工具箱 | 11 个在线工具 | MOTD、server.properties、server-icon、启动脚本；代码运行器、插件 ZIP、插件树、ListTree、Java→TS、MP4→GIF、Markdown→Word |
| 插件中心 | 插件元数据与分发 | 插件元数据、管理端 CRUD、JAR 下载、文档入口 |
| 代码运行器 | 浏览器 + 服务端双模式 | 匿名走浏览器 WASM；登录用户走 Piston；Redis 分钟/日配额；进程内排队；输出截断 |
| 资源服务 | 可插拔文件存储 | SeaweedFS/S3、腾讯 COS、LOCAL；预签名读写；可见性/owner；管理端文件管理；孤儿回收；插件 JAR 托管 |
| 文档站 | VeloChatX VitePress | 9 篇中文 + 9 篇英文 + README；构建产物发布到 `deploy/plugin-docs`；nginx :8085；BFF `/plugin/**` 同源代理 |
| AI Agent | 工具调用问答 | 多语料索引、`searchDocs`/`loadSkill`/`webSearch`/`runCode`；工具预算、AgentTrace、技能覆盖、引用 `[n]`、SSE 步骤卡片、会话落库 |
| AI 运营 | 配额与管理 | 登录校验、每分钟限流、每日 token 计量；运行时开关；`/admin/ai/{settings,usage,catalog}` |
| 工程化 | 构建与运维 | Gradle 多模块、Flyway 三库隔离、Docker compose、一键启停/播种/同步脚本、日志与测试 |

### 2.1 已经闭环的端到端场景

- **登录**：浏览器 → BFF → account OIDC → 回调 → BFF 会话 → 受保护 API。
- **文档问答**：提问 → 检索 VeloChatX/NitroWater 文档 → 带引用回答 → SSE 流式展示。
- **Agent 任务**：提问 → 模型决定是否检索/联网/跑代码 → 工具结果回灌 → 最终回答 + 工具轨迹。
- **文档发布**：VeloChatX 源仓 → VitePress 构建 → `deploy/plugin-docs` → nginx → BFF `/plugin/**`。
- **代码运行**：前端代码块 → 运行器 → 服务端 Piston（登录）或浏览器 WASM（匿名）→ 结果回显。
- **资源管理**：上传 → 预签名 → 读/下载 → 可见性/owner 校验 → 管理端软删/回收。

---

## 3. 系统架构

### 3.1 拓扑

```text
Browser
  │  HttpOnly BFFSESSION（不保存 AT/RT）
  ▼
BFF :8080
  ├─ /                       SPA（nitrowater-web/dist）
  ├─ /oauth2/**, /login      OIDC 管线，委托 account :8090
  ├─ /api/**                 代理到 server :8081，注入 Bearer AT
  ├─ /api/resource/**        代理到 resource :8093
  ├─ /plugin/**              文档静态代理，转发 docs nginx :8085
  └─ /actuator/**, /internal/**  denyAll
```

```text
MySQL 3306        nitrowater_account / nitrowater_biz / nitrowater_resource
Redis 6379        session、验证码、限流、缓存
SeaweedFS 8333    S3 兼容对象存储
Qdrant 6334       文档向量索引
Piston 2000       代码沙箱（容器内网）
docs nginx 8085   VeloChatX 文档站
```

### 3.2 模块职责

| 模块 | 端口 | 职责 |
|---|---:|---|
| `nitrowater-lib` | — | 通用工具与值类型 |
| `nitrowater-core` | — | 共享实体/仓储/认证服务/安全工具；纯库，不独立启动 |
| `nitrowater-account` | 8090 | SSO/OIDC Provider、登录/注册/验证码、托管登录页、管理控制台、RBAC |
| `nitrowater-bff` | 8080 | BFF Token Handler、会话、自动续期、API/文档/SSE 代理、登出 |
| `nitrowater-server` | 8081 | 业务 API、插件中心、代码运行器、AI Agent、管理接口 |
| `nitrowater-resource` | 8093 | 文件/插件 JAR 存储、预签名、可见性/owner、管理端文件管理 |
| `nitrowater-web` | 5173(dev) | Vue 门户、工具箱、插件中心、文档入口、NitroBot、管理端 |
| `deploy/` + `bin/` | — | Docker compose、nginx、密钥、启停/播种/同步脚本 |

### 3.3 请求怎么流

1. 浏览器访问 BFF；静态资源由 BFF 托管 SPA。
2. 未登录时走 `/oauth2/**`，由 account 完成登录，BFF 回调后建立服务端会话。
3. 业务请求走 `/api/**`；BFF 注入 Bearer AT，转发到 server；资源请求转发到 resource。
4. 插件文档走 `/plugin/**`，由 BFF 转发到 docs nginx。
5. `/actuator/**`、`/internal/**` 默认拒绝，避免内部端点静默暴露。

---

## 4. 核心链路

### 4.1 登录与身份

```text
Browser -> BFF /oauth2/authorization
        -> account /oauth2/authorize（登录/注册/验证码）
        -> BFF 回调 /login/oauth2/code
        -> BFF 保存 AT/RT 到服务端会话
        -> Browser 只拿 BFFSESSION
```

设计要点：

- **浏览器零 token**：AT/RT 只在 BFF 服务端，降低 XSS 窃取风险。
- **自动续期**：BFF 用 refresh token 续期，业务服务只校验 JWT。
- **统一身份**：account 是唯一 issuer；server/resource 通过 JWKS 验签。
- **会话失效**：`APP_SESSION_EPOCH` 变更可让旧会话立即失效，适合发布/应急。

### 4.2 AI 文档问答（检索）

```text
question
  -> BFF /api/ai/agent/chat[/stream]
  -> server: 配额 -> 会话历史 -> Agent system prompt
  -> chat model（需要站内事实时自行调用 searchDocs / webSearch）
  -> tools: DocRetriever -> Qdrant（引用编号来自工具结果）
  -> answer + sources([n])
  -> 持久化 ai_message
```

当前实现：

- 两套语料：`velochatx`（5 篇精选）与 `nitrowater`（11 篇）。
- 文档清洗、按标题/token 切块、确定性 id，写入 Qdrant。
- 默认 `top-k=6`、相似度阈值 `0.35`；站内问题要求带引用。
- 只有 Agent 一条问答路线：模型不调工具时就是单轮回答，调工具时多轮；SSE 核心事件为 `step → delta* → sources/tools/skills → done`。
- 会话/消息落库；支持历史、停止、截断续写、内联引用、额度提示。

### 4.3 AI Agent

```text
question
  -> Agent system prompt + skills
  -> model stream
  -> tool call: searchDocs / loadSkill / webSearch / runCode
  -> tool result 回灌
  -> model -> final answer + citations
  -> AgentTrace + SSE step/traces
```

当前实现：

- 自定义 tool loop（`ChatModel` 自驱），保留原始流式输出。
- 四个工具：
  - `searchDocs`：检索本地文档；无命中时可自动联网兜底。
  - `loadSkill`：加载 markdown 技能（classpath 内置 + 外部覆盖）。
  - `webSearch`：可选联网，按轮次限次。
  - `runCode`：Piston 沙箱运行代码，带配额/队列/超时。
- 工具预算：总调用 10、单工具默认 3、`runCode` 4；超限返回错误响应而不是抛异常。
- `AgentTrace` 记录轮次、工具、引用、技能、联网、runCode 次数；前端展示工具轨迹。
- 管理端可看索引、技能、工具与用量；每日 token 配额可重置。

### 4.4 文档发布

```text
VeloChatX 源仓（9 zh + 9 en）
  -> VitePress build
  -> deploy/plugin-docs/velochatx/docs
  -> docs nginx :8085
  -> BFF /plugin/**
  -> 浏览器
```

- 文档站与检索语料是两条链路：站点面向人，classpath 语料面向 `searchDocs` 工具。
- 语料当前精选 5 篇，源站有 18 篇；这是有意的“先保证质量，再扩覆盖”。
- `bin/dev/sync-docs.ps1` 负责构建/同步；BFF 负责同源代理。

### 4.5 代码运行与资源

- 匿名用户：浏览器 WASM 运行，不占服务端资源。
- 登录用户：server 直连 Piston；Redis 做分钟/日配额；`SandboxGate` 做单用户串行、全局并发、有界队列。
- 资源服务：上传先落 PENDING，预签名直传；读/下载校验可见性与 owner；孤儿回收清理未完成上传。
- 插件 JAR 由 resource 托管；插件中心按最新文件下载。

---

## 5. 关键设计决策

1. **BFF 而不是 SPA 直连 OIDC**：浏览器不持 token，续期、登出、失效都在服务端完成。
2. **三库隔离**：account / biz / resource 各自 Flyway 管理，跨库只用 `uuid`，不建物理 FK。
3. **Servlet/WebMVC**：项目明确冻结响应式栈，降低团队与部署复杂度。
4. **文档双链路**：VitePress 面向人，classpath markdown 面向 Agent 检索；两者都从同一源仓产出。
5. **检索先 grounded 再智能**：站内问题必须先 `searchDocs` 并带引用；无证据时说明局限，而不是自由发挥。
6. **Agent 工具有预算**：检索、联网、代码执行都受限，避免无限循环和成本失控。
7. **配额分层**：请求分钟限流 + 每日 token 计量 + 沙箱独立配额，分别保护不同资源。
8. **可运维**：一键启停、播种、重置、文档同步；日志落在 `bin/.run/logs/`。

---

## 6. 完成度与验证

### 6.1 已验证

- 多模块 `gradlew build` 通过。
- 记录值（2026-10-29）：server 77 tests、bff 24 tests、0 fail。
- 前端 `typecheck` + `lint` + `build` 通过。
- Piston 7 种语言 HTTP 沙箱全链路通过（含中文 UTF-8、stdin）。
- Agent 非流式与 SSE 均实际调用过 `runCode`；工具限额、CE/TO 映射有测试覆盖。
- SSO/OIDC、BFF 代理、文档代理、资源上传/下载、插件中心均有端到端或冒烟验证记录。

### 6.2 当前边界

- AI 长期记忆/画像自动推断：表与实体已存在，写入/召回/压缩尚未完成。
- 多模态（图片/视频）未做。
- 部分管理页仍在从 Element Plus 存量迁移中。
- 生产部署硬化、Piston 多实例、Qdrant 鉴权/快照仍需完善。
- 最新一轮改动已过单元测试/前端构建，但全链路 `bootRun` 与浏览器验收仍建议复跑。

这些是“下一步”，不影响项目已经端到端闭环的事实。

---

## 7. 关键接口

| 接口 | 用途 |
|---|---|
| `/oauth2/**`, `/login` | BFF 发起的 OIDC 登录/回调 |
| `GET /bff/me` | 当前会话状态 |
| `/api/**` | 业务 API（登录后） |
| `/api/public/**` | 匿名可读接口 |
| `/api/ai/agent/chat`, `/api/ai/agent/chat/stream` | Agent 问答（JSON / SSE，唯一问答路线） |
| `/api/ai/conversations` | 会话与消息 |
| `/api/sandbox/run` | 服务端代码运行 |
| `/api/admin/**` | 管理端接口（ADMIN） |
| `/plugin/**` | 插件文档站同源代理 |

---

## 8. 术语与缩写

| 缩写 | 含义 |
|---|---|
| SSO | Single Sign-On，单点登录 |
| OIDC | OpenID Connect，建立在 OAuth2 之上的身份层协议 |
| BFF | Backend for Frontend，浏览器与服务端之间的 Token Handler |
| RBAC | 基于角色的访问控制 |
| RAG | Retrieval-Augmented Generation，检索增强生成 |
| Agent | 能调用工具、分步完成任务的模型编排 |
| SSE | Server-Sent Events，服务端流式推送 |
| JWT | JSON Web Token |
| Qdrant | 向量数据库 |
| Piston | 自托管代码沙箱 |
| Flyway | 数据库迁移工具 |
| CRUD | 增删改查 |

---

## 9. 给后续 Agent 的入口

1. 先读 `README.md`、`AGENTS.md`、`.ai/PROJECT.md`、`.ai/context/current.md`。
2. 改认证/BFF：从 `nitrowater-account` 的 `AuthorizationServerConfig` 与 `nitrowater-bff` 的 `SecurityConfig` / 代理控制器进入。
3. 改 AI/Agent：从 `nitrowater-server/src/main/java/cn/nitrowater/server/service/ai` 进入（工具在 `service/ai/tool`、提示词在 `PromptBuilder`、SSE 在 `web/AiAgentController`）；前端从 `nitrowater-web/src/features/ai` 进入。
4. 改文档/站点：源仓 `velochatx-docs`，产物 `deploy/plugin-docs`，代理 `/plugin/**`。
5. 遵守项目约定：Servlet/WebMVC、`/api/**` 数据命名空间、Flyway 迁移冻结、枚举整数编码、时间列 `TIMESTAMP(3)`、Redis `nitrowater:*`、无 subagent。

> 如果后续 Agent 无法访问仓库，以上第 1-6 节已经提供了自包含的项目摘要；第 9 节仅用于能拿到代码时的导航。
