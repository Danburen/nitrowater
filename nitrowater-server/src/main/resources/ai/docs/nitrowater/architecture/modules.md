# 模块划分与依赖规则

## 1. 模块职责

### 1.1 `nitrowater-lib`

与框架无关的通用能力：统一响应码、值类型、校验注解等。**不依赖任何其它模块**，也不依赖 Spring Web 之外的重型组件。

### 1.2 `nitrowater-core`

跨服务共享的领域与基础设施代码：实体基类、用户/安全领域对象、JWT 相关工具、通用仓储与异常。

- 依赖：`lib`
- 约束：不放业务规则（插件、资源、AI 都不属于 core）

### 1.3 `nitrowater-account`（SSO / OIDC，:8090）

```
cn.nitrowater.account
├── admin    # 管理台 API（用户、角色、客户端、会话）
├── config   # AuthorizationServerConfig / SecurityConfig / SSO 处理器
├── security # 登录用户主体、验证码、失败处理
└── web      # 登录/注册页与认证 API
```

- 只读写 `nitrowater_account` 库。
- 对外提供 OIDC 端点（`/oauth2/authorize`、`/oauth2/token`、`/oauth2/jwks`、`/oauth2/revoke`、`/userinfo`、`/connect/logout`）。
- 前端：`admin-ui` 与 `auth-ui` 是 pnpm workspace 下的两个 SPA。

### 1.4 `nitrowater-bff`（:8080）

```
cn.nitrowater.bff
├── config   # OAuth2ClientConfig / SecurityConfig / WebConfig
├── security # 登出处理器、会话处理
└── web      # ApiProxyController / AiStreamProxyController / PluginDocsProxyController / LegacyDocsRedirectController / SessionController
```

- 不直连业务库；只持有会话与代理。
- SPA 静态资源与 history 深链接回退也在这里。
- `/api/**` 是唯一需要登录的数据面；其余路径是公开 SPA 与 OIDC 管线。

### 1.5 `nitrowater-server`（业务，:8081）

```
cn.nitrowater.server
├── ai      # NitroBot：文档索引、检索、Agent、技能、工具、对话
├── config  # SecurityConfig / WebConfig / Jackson 等
├── infra   # 实体、仓储、枚举、限流闸门、第三方客户端（Piston）
├── service # 业务服务（插件中心、沙箱等）
└── web     # 控制器与 DTO
```

- 只读写 `nitrowater_biz` 库。
- 同时是 OAuth2 Resource Server，校验 account 签发的 JWT。
- 通过 `/internal/resource/publish` 调用 resource 的内部发布接口（携带内部 token）。

### 1.6 `nitrowater-resource`（:8093）

```
cn.nitrowater.resource
├── application    # 资源应用服务（上传策略、回调、读写、清理）
├── config         # 安全、存储、预签名配置
├── domain         # 资源实体与状态
├── infrastructure # 存储后端（S3 / COS / local）、Redis 缓存
└── web            # 上传、读取、管理、内部发布控制器
```

- 只读写 `nitrowater_resource` 库。
- 存储后端可插拔，预签名 URL 支持前端直传与读取。
- 同时托管插件 JAR（管理员发布，普通用户只读）。

### 1.7 `nitrowater-web`（前端）

```
src/
├── features/   # 按业务域拆包：plugins / tools / ai / admin / auth
├── components/ # 通用组件
├── composables/# 通用组合式函数
├── router/     # 路由（/chat 使用 meta.layout='chat'，隐藏站点 header）
└── api/        # 请求封装（同源 /api/**，自动带 CSRF）
```

## 2. 依赖方向

```text
web  ->  bff  ->  server  ->  core  ->  lib
                 resource  ->  core  ->  lib
       account  ->  core  ->  lib
```

- 禁止反向依赖（core 不得引用 server/account/bff）。
- 服务之间不共享数据库，只共享逻辑键：`uid`（数字）与资源 `uuid`（字符串）。
- server 与 resource 之间不直接调用 Java 代码，只走 HTTP。

## 3. 新增代码该放哪

| 需求 | 落点 |
|---|---|
| 新的业务接口（插件、工具、AI） | `nitrowater-server` 的 `web` + `service` |
| 需要登录的数据接口 | 必须挂在 `/api/**` 下（`/api/public/**` 才匿名） |
| 文件上传/读取/管理 | `nitrowater-resource` |
| 登录、注册、Token、角色 | `nitrowater-account` |
| 跨服务会话与代理、SPA 托管 | `nitrowater-bff` |
| 通用工具、响应码 | `nitrowater-lib` / `nitrowater-core` |
| 前端页面 | `nitrowater-web/src/features/<域>` |

## 4. 硬性约束

- 后端响应式冻结：统一 Servlet / WebMVC，不引入 WebFlux。
- 每个服务自己的 Flyway 迁移只作用于自己的库；已应用的迁移冻结，新变更走新版本（dev 阶段允许改基线 + 重置库）。
- 注释用英文，公开方法写 Javadoc。
- Redis 键统一 `nitrowater:*` 命名空间。
- 前端 Tailwind-first；Element Plus 仅在复杂组件按需引入。
