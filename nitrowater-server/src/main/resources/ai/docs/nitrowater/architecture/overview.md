# 系统架构

## 1. 部署拓扑

```text
Browser SPA (nitrowater-web)
        |
        | same-origin HttpOnly cookie (BFFSESSION) + XSRF-TOKEN
        v
nitrowater-bff :8080
  - OIDC confidential client / token handler
  - SPA static hosting (index.html fallback for history routes)
  - /api/** proxy with Bearer token
  - /plugin/** proxy to the docs container (nginx)
        |
        +-------------------+-------------------+
        |                   |                   |
        v                   v                   v
nitrowater-server :8081  nitrowater-resource :8093  nitrowater-account :8090
  business API             files / plugin JARs        SSO / OIDC provider
  OAuth2 resource server   OAuth2 resource server     authorization server
        |                   |                   |
        +---------+---------+-------------------+
                  |
        MySQL / Redis / Qdrant / SeaweedFS / Piston
```

各服务都是独立进程、独立数据库、独立启动脚本；服务之间只通过 HTTP 与逻辑键（`uid`、`uuid`）协作。

## 2. 请求路径

### 2.1 页面访问

1. 浏览器请求 `http://localhost:8080/chat`。
2. BFF 返回 SPA 静态资源；非静态路径回退到 `index.html`，由 vue-router 处理深链接。
3. SPA 调用 `/api/me` 判断登录态；未登录时显示登录入口，不发 token。

### 2.2 业务 API

```text
SPA -> BFF /api/plugins
        -> OAuth2AuthorizedClientManager 取当前会话 token
        -> 有效：注入 Authorization: Bearer <AT>
        -> 过期：服务端 refresh_token，刷新后继续
        -> 转发到 server :8081（按前缀路由到 server 或 resource）
```

- 路由规则：`/api/ai/**`、`/api/plugins/**`、`/api/sandbox/**` 等业务前缀走 server；`/api/resource/**`、`/api/public/resource/**` 走 resource；`/api/public/plugins` 走 server。
- 响应流式回传：`ApiProxyController` 使用 `InputStreamResource` 双向转发，避免把大文件（插件 jar）读进 BFF 堆。

### 2.3 文件上传（预签名）

```text
1. SPA POST /api/resource/upload/policy   -> 预签名 PUT URL + 资源 uuid（行状态 PENDING）
2. SPA PUT 预签名 URL（直传对象存储）
3. SPA POST /api/resource/upload/callback -> 行状态转 ACTIVE
4. 客户端放弃上传 -> ResourceCleanupService 超时删除对象并置 ORPHAN
```

对象存储后端可切换：`s3`（SeaweedFS）/ `cos` / `local`，由 `app.resource.default-backend` 决定。

### 2.4 AI 对话（SSE）

```text
SPA -> BFF POST /api/ai/agent/chat/stream
     -> AiStreamProxyController（保留 SSE、逐块透传）
     -> server AiAgentController -> AiAgentService -> ChatClient(tools)
     -> 事件：start / delta* / sources / tools / skills / done
```

### 2.5 代码运行

```text
SPA -> /api/sandbox/run (server)
     -> SandboxRateLimitInterceptor（用户配额）
     -> SandboxService -> SandboxExecutionGuard（全局并发 + 有界队列）
     -> PistonClient -> Piston /api/v2/execute
```

NitroBot 的 `runCode` 工具与 HTTP 入口共享同一套闸门，避免 Agent 绕过配额。

## 3. 数据与中间件

| 组件 | 用途 | 默认地址 |
|---|---|---|
| MySQL 8 | account / biz / resource 三库，Flyway 管理 schema | `localhost:3306` |
| Redis | BFF 会话（`nitrowater:bff:session`）、验证码、限流计数 | `localhost:6379` |
| Qdrant | 文档向量索引（按 corpus 区分）与长期记忆 | `localhost:6334`（gRPC） |
| SeaweedFS | S3 兼容对象存储 | `localhost:8333` |
| Piston | 编译/运行沙箱 | `localhost:2000` |

Qdrant 的 schema 初始化关闭（`initialize-schema: false`），索引服务首次写入时创建集合，因此 Qdrant 未启动不会阻止 server 启动。

## 4. 安全边界

| 边界 | 规则 |
|---|---|
| 浏览器 ↔ BFF | 只有 HttpOnly 会话 Cookie + 可读 CSRF Cookie；无 Bearer token |
| BFF ↔ 下游 | `Authorization: Bearer <access_token>`；server/resource 校验签名、`iss`、`exp/nbf`、`aud` |
| 数据命名空间 | `/api/**` 需登录；`/api/public/**` 匿名；`/actuator/**`、`/internal/**` 默认拒绝 |
| 内部接口 | resource 的 `/internal/**` 需要内部 token，仅供 server 发布插件产物 |
| 沙箱 | 语言白名单 + 配额 + 全局并发 + 输出/内存/超时上限；错误信息脱敏 |

## 5. 文档与静态资源

- 文档语料：`nitrowater-server/src/main/resources/ai/docs/{nitrowater,velochatx}`，由索引器读 markdown 写入 Qdrant。
- **NitroWater 文档只有 markdown**：不构建站点、不部署静态页，引用只带标题/章节/相对路径（`url` 为空）。
- VeloChatX 是唯一发布静态站的语料：外部仓库独立演进，产物发布到 `deploy/plugin-docs/velochatx/docs`，由 docs nginx（:8085）托管、BFF 以 `/plugin/**` 同源代理，引用 URL 前缀也是 `/plugin/velochatx/docs/`。
- 站点不进 jar：`static/docs` 已废弃并忽略，文档更新是纯文件发布（rsync/scp），不需要重新构建 server。
- 改语料后重启（或重建 jar）触发索引，或调用管理员重建接口。

## 6. 关键源码

- `nitrowater-bff/src/main/java/cn/nitrowater/bff/web/ApiProxyController.java`
- `nitrowater-bff/src/main/java/cn/nitrowater/bff/web/AiStreamProxyController.java`
- `nitrowater-bff/src/main/java/cn/nitrowater/bff/web/PluginDocsProxyController.java`
- `nitrowater-server/src/main/java/cn/nitrowater/server/config/SecurityConfig.java`
- `nitrowater-server/src/main/java/cn/nitrowater/server/config/WebConfig.java`
- `nitrowater-resource/src/main/java/cn/nitrowater/resource/web/ResourceUploadController.java`
