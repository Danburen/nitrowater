# OIDC / OAuth2 / BFF 认证协作 —— 问答记录

> 关联模块：`nitrowater-account`（AS/OP）、`nitrowater-bff`（Client/RP）、`nitrowater-server`/`nitrowater-resource`（RS）、`nitrowater-web`（SPA）。
> 状态：答疑记录（供审阅）。代码引用以仓库当前基线为准。

## 0. 一句话总览

浏览器只持有一个 HttpOnly 会话 Cookie（`BFFSESSION`）；`id_token` / `access_token`(AT) / `refresh_token`(RT) 全部由 BFF 服务端持有（→ Redis）。
**account 发证（认证 + 签发），bff 持证/代办（Token Handler + 代理），RS 只验票（授权）。**

---

## Q1. BFF 的 Session 是不是已经存到 Redis 了？具体是哪条语句？

**A：是，且为纯自动配置——源码里没有任何 `@EnableRedisHttpSession` / `RedisSessionRepository` 的显式声明。** 触发点是「依赖 + 属性」两处：

1. 依赖 —— `nitrowater-bff/build.gradle:31`
   ```groovy
   implementation 'org.springframework.boot:spring-boot-starter-session-data-redis'
   ```
   （`:30` 的 `spring-boot-starter-data-redis` 提供 `RedisConnectionFactory`。）

2. 配置 —— `nitrowater-bff/src/main/resources/application.yml:23-38`
   ```yaml
   spring:
     data:
       redis:
         host: localhost
         port: 6379
     session:
       data:
         redis:                              # Boot 4 由 spring.session.redis.* 改名
           namespace: nitrowater:bff:session # ← 关键一条：会话键前缀
           flush-mode: on_save
   ```

行为：只要 classpath 有该 starter + `RedisConnectionFactory`，Boot 的 `SessionAutoConfiguration` 即装配 `RedisSessionRepository` + `SpringSessionRepositoryFilter`，把 `HttpSession` 读写改道 Redis（无需写 `spring.session.store-type`）。

**AT/RT 为何也在 Redis**：`OAuth2ClientConfig.java:27` 返回 `HttpSessionOAuth2AuthorizedClientRepository`——授权客户端（含 AT/RT）存进 `HttpSession`，而 `HttpSession` 已由 Redis 支撑，故一并持久化。属「会话存储（session store）」，非另建的 token 缓存。

---

## Q2. 登出：`LogoutHandler` 与 `LogoutSuccessHandler` 怎么触发和处理？

**A：两者都在内置 `LogoutFilter` 里被触发，但时机不同：**

| 接口 | 职责 | 时机 | 本项目实现 |
|---|---|---|---|
| `LogoutHandler` | 做事（清理：吊销 RT、销毁会话、删 Cookie） | 会话还活着时先跑 | `OidcSessionLogoutHandler` |
| `LogoutSuccessHandler` | 决定响应（成功后往哪跳） | 所有 handler 跑完后 | `OidcRpInitiatedLogoutSuccessHandler` |

`LogoutFilter` 逻辑：匹配登出 URL → 调 `CompositeLogoutHandler.logout(...)`（跑所有 handler）→ 调唯一的 `logoutSuccessHandler.onLogoutSuccess(...)`。

- **匹配**：CSRF 开启时，默认只匹配 `POST /logout`（关 CSRF 才允许 GET）。这正是 SPA 必须**顶层表单 POST** 而不能用 `<a>` 链接的原因（`auth/bff.ts` 的 `logout()`）。
- **顺序**：`.addLogoutHandler(oidcSessionLogoutHandler)` 加的自定义 handler 在**默认 `SecurityContextLogoutHandler`（作废会话）之前**执行——所以能读到 RT 去吊销。
- **默认 handler 配置**：`.invalidateHttpSession(true) / .clearAuthentication(true) / .deleteCookies("BFFSESSION","XSRF-TOKEN")`（`SecurityConfig.java:92-94`）配置的即默认 `SecurityContextLogoutHandler`。

本项目登出链：

```
POST /logout (顶层表单, 带 _csrf)
  └─ LogoutFilter
       ├─ CompositeLogoutHandler
       │    1) OidcSessionLogoutHandler      ← 自定义，会话存活中
       │         · 用 RT 换一次 token（拿到"当前" id_token，刷新会重签）
       │         · 把轮换后的 RT 在 /oauth2/revoke 吊销（RFC 7009）
       │    2) SecurityContextLogoutHandler  ← 作废会话 + 清 SecurityContext
       │    3) CookieClearingLogoutHandler   ← 删 BFFSESSION / XSRF-TOKEN
       └─ OidcRpInitiatedLogoutSuccessHandler
            · 302 浏览器 → account end_session_endpoint?id_token_hint=...&post_logout_redirect_uri=...
            · account 销毁 SSO 会话 → 回跳
```

（RT/endpoint 取不到时降级为本地登出，跳 `fallbackUri`——`OidcRpInitiatedLogoutSuccessHandler.java:61-65`。）

---

## Q3. account 把 id_token / AT / RT 交给 BFF 后，BFF 会不会"校验→判合法→放行"？三票作用是什么？与"双 token"区别？

**A：会校验，但只在登录那一刻、且只校验 `id_token`；此后每次请求 BFF 只查会话，不再逐次验票。** 分三层：

- **登录时**：Spring 的 `OidcAuthorizationCodeAuthenticationProvider` 用 iss 的 JWKS **验 `id_token`**（签名 / `iss` / `aud`=client_id / `exp`·`nbf` / `nonce`），失败即登录失败。通过后才建 BFF 会话、存 AT/RT。→ **「判合法→放行」发生在此，信的是 id_token。**
- **之后每次 `/api/**`**：`ApiProxyController.proxy` 只判断「有没有有效 BFF 会话」，通过则取 AT（过期用 RT 静默刷新）附为 `Bearer`；**BFF 从不解析/校验 AT**。
- **下游**：`nitrowater-server` 才逐次验 AT（`NimbusJwtDecoder` + `AudienceValidator`，`SecurityConfig.java:88-96`）。

分层口诀：**登录时 BFF 验 id_token（认证）→ 登录后 BFF 验会话 → 下游 RS 验 AT（授权）。**

三票对照：

| 票 | 签给谁 | 给谁用 | 形态/`aud` | 生命周期 | 本项用法 |
|---|---|---|---|---|---|
| **id_token** | 客户端(BFF) | 只给 BFF | JWT，`aud`=client_id | 随会话 | 登录时证明"谁登录了"；**不发 RS** |
| **access_token** | 资源服务器 | BFF→RS | JWT，`aud`=nitrowater-server | **15m** | 每次调 `/api` 的 `Bearer`；RS 验它 |
| **refresh_token** | 客户端(BFF) | 只给 BFF | 不透明串 | **30d**（轮换） | 换新 AT（+新 id_token）；**不发 RS** |

**与"双 token"区别**：

- OAuth2 语境的「双 token」= `AT + RT`（一个短命访问、一个长命续期）。本项目**确实是双 token**，且 `reuseRefreshTokens=false`（刷新即轮换）。
- `id_token` 是 **OIDC 在 OAuth2 之上加的第三张票**：OAuth2 只回答"能访问什么"，OIDC 补上"你是谁"。故更准确说是「**OIDC 三票**」：id_token(我是谁) + AT(我能访问啥) + RT(怎么续)。
- 真正的差别不在"几张票"，而在**票放哪**：经典 SPA 双 token（oidc-client-ts）由**浏览器持 AT/RT**（易被 XSS）；本项目三票**全在 BFF 服务端**，浏览器零 token。

> ⚠️ 注意 account 里有**两套** token：① OIDC AS 正式签发（`/oauth2/**`，BFF 用这套）；② Phase1 遗留的"自研 AT/RT"（`/api/auth/**`，`AuthController` 标 `@SuppressWarnings("deprecation")`；`application.yml` 的 `token.access.expiration` / `refresh.rotate` / Cookie `REFRESH_TOKEN` 属它，服务已退役的旧 public client）。别拿第 2 套 TTL 去理解 BFF。

---

## Q4. id_token 是不是类似 session_id？浏览器/服务端存？BFF 会让浏览器存吗？OAuth2/OIDC 各体现在哪？account 与 bff 的职责？

### Q4a. `id_token` ≠ `session_id`

| | session_id（本项目 = `BFFSESSION`） | id_token |
|---|---|---|
| 本质 | 不透明随机串，只是"指针" | 自包含签名 JWT（一份认证收据） |
| 存哪 | **浏览器 Cookie**（HttpOnly） | **服务端**（BFF 会话，落 Redis） |
| 指向 | 指向服务端会话状态 | 不指向任何东西——内容就在 token 里 |
| 谁校验 | 无人校验，服务端拿它查会话 | **BFF 登录时做密码学校验** |
| 身份信息 | 无 | 有（`sub`/`name`/`roles`/`did`…） |
| 类比 | 寄存处号牌 | 护照/回执 |

唯一"像"之处：`id_token` 含 **`sid`** claim（= account 侧 SSO 会话 id 的哈希），登出时告诉 account 销毁哪张会话。但 `sid` 是 JWT 内部字段，不是浏览器 Cookie；account 的 SSO 会话是**另一张独立会话**（`SsoUserPrincipal` + `SessionRegistry`）。

### Q4b. BFF 会让浏览器存 id_token 吗？——**不会**。具体体现：

- 客户端无任何 token 字段：`nitrowater-web/src/auth/bff.ts` 的 `BffSession` 只有 `uid/username/name/roles/did/issuer/expiresAt`；`fetchSession()` 返回身份 JSON，无 token。
- 浏览器只被下发两个 Cookie：`BFFSESSION`（httpOnly，`bff/application.yml`）+ 可读的 `XSRF-TOKEN`；登出删的就是这两个（`SecurityConfig.java:94`）。
- id_token 活在服务端会话：`SessionController.me(@AuthenticationPrincipal OidcUser user)` 里 `user.getIdToken()`（`SessionController.java:51`）即从会话读 id_token；登出 handler 亦然。
- AT/RT 亦在服务端：`HttpSessionOAuth2AuthorizedClientRepository`（`OAuth2ClientConfig.java:27`）。

### Q4c. OAuth2 与 OIDC 在流程中的体现

| 环节 | 协议 | 端点/产物 |
|---|---|---|
| 授权请求 `/oauth2/authorize?response_type=code&nonce=...&scope=openid,profile` | OAuth2（授权码）+ OIDC（openid/nonce） | authorize |
| 登录页 / 密码验证码校验 | SSO 自身 | `/login` + `SsoAuthenticationProvider` |
| 回跳带 code | OAuth2 | redirect_uri |
| `/oauth2/token` 换票（client_secret_basic） | OAuth2 | token |
| 换回 AT+RT | OAuth2（双 token） | — |
| 换回 id_token | **OIDC** | — |
| 启动拉 discovery | **OIDC** | `/.well-known/openid-configuration` |
| RS 用 JWKS 验 AT | OAuth2 | `/oauth2/jwks` |
| 登出 302 `end_session_endpoint?id_token_hint=` | **OIDC** | end_session |
| RT 吊销 `/oauth2/revoke` | OAuth2（RFC 7009） | revoke |
| `roles/uid/did/picture` claims | OIDC/SSO 扩展 | `jwtTokenCustomizer` |

判据：凡涉及 `openid`/id_token/userinfo/nonce/end_session/discovery → **OIDC**；凡涉及授权码、token/revoke、客户端认证、AT/RT、Bearer 访问 RS、scopes → **OAuth2**。OIDC = OAuth2 + 身份证层。

### Q4d. account 与 bff 的职责

协议角色：Client = **bff**；Authorization Server / OpenID Provider = **account**；Relying Party = **bff**；Resource Server = server/resource；Resource Owner = 终端用户。

- **account（AS + OP，发证机关）**
  - OAuth2 侧：授权/token/revoke 端点；客户端注册表 `oauth2_registered_client`（决定 `nitrowater-bff` 为 confidential、可发 RT、redirect_uri 白名单）；grant/scopes/客户端认证；JDBC 授权与同意存储。
  - OIDC 侧：签发 id_token；discovery + JWKS；nonce 处理；end_session；claims 定制（`jwtTokenCustomizer`：`sub=uid`、`roles`、`did`、`picture`）。
  - 外加 SSO：托管登录页、验证码/密码认证、SSO 会话（`SsoUserPrincipal`）。

- **bff（Client/RP + Token Handler + 代理）**
  - OAuth2 客户端：发起授权、处理回调、换票、持 AT/RT、RT 静默刷新、登出 revoke RT。
  - OIDC(RP)：校验 id_token、消费 discovery/JWKS、RP-initiated logout、读取身份。
  - BFF 特有：自维护浏览器会话（`BFFSESSION`+Redis）、CSRF、`/bff/me`、把 `/api/**` 连 `Bearer AT` 代理到 RS。浏览器面向的只有它。

- **server / resource（RS，验票方）**：JWKS 验 AT 签名 + `iss`/`exp`/`nbf` + `aud`；`roles`→`ROLE_*`；无状态。

---

## 5. 谁存什么 / 谁验什么（总图）

```
        浏览器                 BFF(:8080)                      account(:8090)         RS(:8081/8093)
   ┌───────────────┐    ┌──────────────────────────┐    ┌───────────────────┐   ┌──────────────┐
   │ BFFSESSION    │───▶│ 会话(Redis)              │    │ SSO 会话          │   │ 无状态       │
   │  (session id) │    │  ├─ OidcUser(含 id_token)│    │ (SsoUserPrincipal │   │ 验 AT:       │
   │ XSRF-TOKEN    │    │  └─ AT / RT              │    │  + sid)           │   │  JWKS 签名   │
   │ (无任何 token)│    │ 验 id_token(登录时,OIDC) │    │ 发 id_token(iss)  │   │  iss/aud/exp │
   └───────────────┘    │ 持 AT/RT(OAuth2)         │    │ 发 AT/RT(OAuth2)  │   │  roles→ROLE_ │
                        │ 刷新/吊销 → account      │───▶│ /oauth2/token     │   └──────────────┘
                        │ ── Bearer AT 代理 /api ──┼────┼───────────────────┼──────────▶
                        └──────────────────────────┘    └───────────────────┘
```
