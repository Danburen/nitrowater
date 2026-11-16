# OIDC + BFF 认证链路

本文描述当前仓库的实现：SPA / BFF / Account 的职责边界，Session 与 Token 的存放位置，刷新与登出流程，以及校验失败时的行为。它不替代 OIDC 规范。

## 1. 角色与原则

| 角色 | 模块 | 职责 |
|---|---|---|
| Authorization Server | `nitrowater-account` :8090 | 登录、注册、验证码、授权码、Token 签发、JWKS、RP-Initiated Logout、管理台 |
| OIDC Client / BFF | `nitrowater-bff` :8080 | code→token 交换、Token 刷新、HttpSession 管理、`/api/**` 代理、SPA 托管 |
| Resource Server | `nitrowater-server` :8081 / `nitrowater-resource` :8093 | 校验 JWT、映射权限、执行业务 |
| SPA | `nitrowater-web` | 同源前端，不接触任何 token |

核心原则：**浏览器只持有 HttpOnly 的 `BFFSESSION` Cookie**（外加可读的 `XSRF-TOKEN` 用于 CSRF）。所有 token 只存在 BFF 的 HttpSession 中，由 Spring Session 持久化到 Redis。

## 2. 登录流程（Authorization Code）

1. SPA 请求 `/api/**` 得到 `401`，跳转 `/oauth2/authorization/nitrowater`。
2. BFF 生成授权请求并 302 到 Account 的 `/oauth2/authorize`。
3. 用户在 Account 的 `/login` 完成表单登录（含验证码），Account 建立自己的 SSO 会话。
4. Account 校验 `redirect_uri` 后回调 BFF 的 `/login/oauth2/code/nitrowater`。
5. BFF 在服务端用授权码换取 token（`client_secret_basic`），构造 `OAuth2AuthorizedClient` 写入 HttpSession。
6. BFF 设置 `BFFSESSION`，重定向回 SPA。

`redirect_uri` 必须在 `app.oidc.bff-redirect-uris` 白名单内（默认包含 `5173` 与 `8080` 的本地地址以及生产域名）。

## 3. 存储位置

| 数据 | 存储 | 说明 |
|---|---|---|
| 授权码、授权状态、Token 元数据 | MySQL `nitrowater_account.oauth2_authorization` | `JdbcOAuth2AuthorizationService` 读写 |
| 客户端注册信息 | `oauth2_registered_client` | 含 `nitrowater-bff` 机密客户端 |
| BFF 会话（含 AT/RT/id_token） | Redis `nitrowater:bff:session:*` | Spring Session；BFF 重启不掉登录 |
| OIDC 会话 Cookie | 浏览器 `BFFSESSION` | HttpOnly、SameSite=Lax、30 分钟超时 |
| CSRF token | 浏览器 `XSRF-TOKEN` | 非 HttpOnly，前端读取后放入请求头 |

## 4. code→token 与自动刷新

### 4.1 访问 API

SPA 调用 `/api/**` 时，`ApiProxyController` 通过 `OAuth2AuthorizedClientManager.authorize()` 取当前会话的客户端：

- access token 有效：注入 `Authorization: Bearer <AT>` 后转发。
- access token 过期且 refresh token 有效：BFF 在服务端发起 `grant_type=refresh_token`，更新 MySQL 授权记录与 Redis 会话，再继续代理。
- 刷新失败：清理会话并返回 `401`，SPA 触发重新登录。

前端对刷新完全无感知，也不会看到 token。

### 4.2 Token 内容与校验

- `jwt.issuer` 是平台唯一签发者（必须是一个 URL），account 用它签发，server/resource 用它做 issuer 校验。
- JWT `sub` 与 `uid` claim 都是 account 的数字用户主键（`String.valueOf(uid)`）；`roles`、`preferred_username`、`name`、`did`、`picture` 按需附加。
- access token 的 `aud` 由 `app.oidc.access-token-audiences` 与 client_id 组成；各 Resource Server 用 `app.security.jwt.audiences` 校验（server 默认 `nitrowater-server`，resource 默认 `nitrowater-resource,nitrowater-server`）。
- `JwtAuthenticationConverter` 把 `roles`/`scope` 映射为 `GrantedAuthority`。

## 5. 登出（RP-Initiated Logout）

1. `OidcSessionLogoutHandler` 先用 refresh token 刷新一次，拿到最新 id_token。
2. 调用 account 的 revocation endpoint 撤销 refresh token。
3. `OidcRpInitiatedLogoutSuccessHandler` 构造带 `id_token_hint` 与 `post_logout_redirect_uri` 的地址，跳转 account 的 `end_session_endpoint`，销毁 SSO 会话。
4. BFF 清理 HttpSession（Redis）并删除 `BFFSESSION`、`XSRF-TOKEN`。

`post_logout_redirect_uri` 必须在 `app.oidc.bff-post-logout-redirect-uris` 白名单内。

## 6. CSRF 与会话失效

- Cookie 认证必须有 CSRF 防护：BFF 使用 `CookieCsrfTokenRepository.withHttpOnlyFalse()`，并通过 `CsrfCookieFilter` 强制写出 token。
- 前端写请求（POST/PUT/PATCH/DELETE）需携带 `X-XSRF-TOKEN`；BFF 对 `/api/**` 校验。
- `/api/auth/**` 等少数链路历史上免 CSRF；在 SameSite=Lax + CORS 白名单下风险有限，但不要在新接口上扩大豁免范围。
- BFF 会话超时（默认 30 分钟）后，接口返回 `401`；前端应重新走登录。
- account 侧的并发会话控制会由 `expiredSessionFilter` 统一返回 JSON `401`，避免出现纯文本 200。

## 7. 常见故障

| 现象 | 原因 | 处理 |
|---|---|---|
| 登录后反复跳登录页 | `issuer` 不一致（account、BFF、SPA 配置不同源） | 统一 `JWT_ISSUER`，检查 BFF 的 `issuer-uri` 与 account 的 `jwt.issuer` |
| `invalid_redirect_uri` | 回调地址不在白名单 | 把实际地址加入 `app.oidc.bff-redirect-uris` |
| BFF 启动失败 `Unable to resolve Configuration with the provided Issuer` | account 未启动或不可达 | 先启动 account，再启动 BFF |
| 接口 401 但刚登录过 | 会话过期 / Redis 中会话被清 | 重新登录；检查 Redis 连接与 namespace |
| 403 且日志提示 audience | token `aud` 与 RS 配置不一致 | 对齐 `access-token-audiences` 与各 RS 的 `audiences` |
| 写请求 403 CSRF | 未带 `X-XSRF-TOKEN` 或 token 过期 | 前端重新读取 cookie 并重试 |

## 8. 关键源码

- `nitrowater-account/src/main/java/cn/nitrowater/account/config/AuthorizationServerConfig.java`
- `nitrowater-bff/src/main/java/cn/nitrowater/bff/config/SecurityConfig.java`
- `nitrowater-bff/src/main/java/cn/nitrowater/bff/config/OAuth2ClientConfig.java`
- `nitrowater-bff/src/main/java/cn/nitrowater/bff/web/ApiProxyController.java`
- `nitrowater-bff/src/main/java/cn/nitrowater/bff/security/OidcSessionLogoutHandler.java`
- `nitrowater-server/src/main/java/cn/nitrowater/server/config/SecurityConfig.java`
