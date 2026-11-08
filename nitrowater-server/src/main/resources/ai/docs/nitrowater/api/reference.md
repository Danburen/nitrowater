# HTTP API 参考

## 1. 命名空间与认证

| 前缀 | 认证 | 说明 |
|---|---|---|
| `/api/**` | 需登录 | 唯一数据命名空间；BFF 注入 Bearer token |
| `/api/public/**` | 匿名 | 有意公开的只读接口（如公开资源、公开插件列表） |
| `/api/admin/**` | 管理员 | 管理台接口 |
| `/internal/**` | 内部 token | 服务间调用，BFF 与网关默认拒绝 |
| `/actuator/**` | 拒绝 | 不对外暴露 |
| `/plugin/**` | 匿名 | 插件文档站（docs nginx，当前只有 `velochatx`） |
| `/bff/**` | 按接口 | BFF 自身接口（如 `/bff/me`） |

浏览器侧只调用同源 `/api/**`；写请求需要携带 `X-XSRF-TOKEN`。

## 2. 通用约定

- 请求/响应体为 JSON（`application/json`）；SSE 为 `text/event-stream`。
- 列表接口使用游标分页：`?cursor=&limit=`，响应返回 `nextCursor`（无更多时为 `null`）。
- 时间统一用 ISO-8601（UTC）。
- 错误：4xx/5xx 返回 JSON，`message` 为可读原因；沙箱错误由 `SandboxExceptionHandler` 统一映射。服务端故障不返回堆栈或下游地址。
- 所有标识：会话用 `uuid`，用户用 `uid`（数字），资源用 `uuid`。

## 3. 身份与会话

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/me` | 当前登录用户信息（未登录 401） |
| GET | `/api/public/ping` | 匿名探活 |
| GET | `/bff/me` | BFF 侧会话信息 |

account 提供标准 OIDC 端点：`/oauth2/authorize`、`/oauth2/token`、`/oauth2/jwks`、`/oauth2/revoke`、`/oauth2/introspect`、`/userinfo`、`/connect/logout`，以及登录/注册页 `/login`、`/register`。

## 4. 插件中心

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| GET | `/api/public/plugins` | 匿名 | 公开插件列表 |
| GET | `/api/public/plugins/{key}` | 匿名 | 插件详情 |
| GET | `/api/plugins` | 登录 | 管理视角列表 |
| POST | `/api/plugins` | 管理员 | 新增插件 |
| PUT | `/api/plugins/{id}` | 管理员 | 更新插件 |
| DELETE | `/api/plugins/{id}` | 管理员 | 删除插件 |
| GET | `/api/admin/plugin-resources/{key}` | 管理员 | 插件产物（JAR）列表 |
| POST | `/api/admin/plugin-resources/{key}` | 管理员 | 上传/替换 JAR |
| DELETE | `/api/admin/plugin-resources/{key}/{uuid}` | 管理员 | 删除 JAR |

## 5. 资源服务

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/resource/upload/policy` | 登录 | 申请上传策略（返回预签名 URL 与资源 uuid，行状态 PENDING） |
| POST | `/api/resource/upload/callback` | 登录 | 上传完成回调（PENDING → ACTIVE） |
| PUT | `/api/resource/upload/{uuid}` | 登录 | 直传（local 后端或代理上传） |
| GET | `/api/resource/{uuid}` | 登录 | 读取（预签名或重定向） |
| GET | `/api/resource/{uuid}/download` | 登录 | 下载 |
| GET | `/api/resource/{uuid}/meta` | 登录 | 元数据 |
| GET | `/api/resources` | 登录 | 我的资源列表 |
| DELETE | `/api/resource/{uuid}` | 登录 | 删除 |
| GET | `/api/public/resource/{uuid}` | 匿名 | 公开资源读取（如头像） |
| GET | `/api/public/resources` | 匿名 | 公开资源列表 |
| GET | `/api/admin/resources/{uuid}` | 管理员 | 管理台读取 |
| GET | `/api/admin/resources/{uuid}/download` | 管理员 | 管理台下载 |
| DELETE | `/api/admin/resources/{uuid}` | 管理员 | 管理台删除 |
| GET | `/api/admin/resources/dictionaries` | 管理员 | 管理台字典 |
| POST | `/internal/resource/publish` | 内部 token | server 发布插件产物到 resource |

## 6. 沙箱

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/sandbox/run` | 登录 | 编译并运行代码；返回 stdout/stderr/状态 |

## 7. AI / NitroBot

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| GET | `/api/ai/status` | 登录 | 索引与模型状态 |
| GET | `/api/ai/skills` | 登录 | 可用技能 |
| POST | `/api/ai/agent/chat` | 登录 | Agent 一次性回答（唯一问答路线） |
| POST | `/api/ai/agent/chat/stream` | 登录 | Agent SSE |
| GET | `/api/admin/ai/skills` | 管理员 | 技能清单（管理台） |
| POST | `/api/admin/ai/skills/import` | 管理员 | 导入 `SKILL.md`（同名覆盖） |
| GET | `/api/admin/ai/skills/{name}/export` | 管理员 | 导出为 `SKILL.md`（内置也可导出） |
| GET | `/api/admin/ai/tools` | 管理员 | 工具清单（管理台） |
| POST | `/api/admin/ai/reindex` | 管理员 | 重建文档索引 |

### 7.1 会话接口（服务端落库）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/ai/conversations` | 新建会话，返回 `{ uuid }` |
| GET | `/api/ai/conversations?cursor=&limit=20` | 会话列表（按 `last_message_at` 倒序） |
| GET | `/api/ai/conversations/{uuid}/messages?before=&limit=50` | 上翻消息 |
| PATCH | `/api/ai/conversations/{uuid}` | 改标题 / 归档 |
| DELETE | `/api/ai/conversations/{uuid}` | 软删 |

聊天请求新增可选字段 `conversationUuid`；`history` 字段保留一版以兼容旧客户端。

### 7.2 请求体

```json
{
  "question": "如何配置跨服频道？",
  "conversationUuid": "6f1c...",
  "history": [{ "role": "user", "content": "..." }],
  "skill": "config-walkthrough",
  "allowWeb": false
}
```

- `question` 必填，上限 `app.ai.max-question-chars`（默认 2000）。
- `skill` 为空时由模型按需加载技能。
- `allowWeb` 只影响 `webSearch` 工具是否注册（还需服务端配置 provider key）。

### 7.3 SSE 事件

```text
event: conversation data: {"uuid":"..."}
event: start   data: {"mode":"agent"}
event: step    data: {"phase":"thinking"} | {"phase":"tool_start",...} | {"phase":"tool_end",...}
event: delta   data: {"text":"..."}
event: sources data: {"sources":[...],"grounded":true}
event: tools   data: {"tools":[...]}
event: skills  data: {"skills":["..."]}
event: done    data: {}
```

`conversation` 事件在首个 token 之前发出，新建会话的客户端应记住这个 uuid。

`conversation` / `start` / 第一个 `step{"phase":"thinking"}` 在处理器返回时即被写出，早于上下文组装与额度预占；因此客户端可以在请求发出后立刻渲染过程时间线，不必等首个 `delta`。

`step` 与 `delta` 交替到达：工具运行期间不发 `delta`。`sources` / `tools` / `skills` 在结束时一次性发出，因此它们在流结束时一定是完整的。客户端应容忍未知事件名。

## 8. 管理台

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/ai/*` | 见上 |
| `/bff/accounts/**` | BFF 代理到 account | 账号激活、删除等管理操作 |

## 9. 错误码速查

| 状态 | 场景 |
|---|---|
| 400 | 参数校验失败、未知技能、未知沙箱语言 |
| 401 | 未登录 / 会话过期 / token 刷新失败 |
| 403 | 权限不足、CSRF 校验失败、audience 不匹配 |
| 404 | 会话/资源/插件不存在，或不属于当前用户 |
| 429 | 限流、队列满、排队超时 |
| 503 | AI 关闭、Piston 不可用 |
