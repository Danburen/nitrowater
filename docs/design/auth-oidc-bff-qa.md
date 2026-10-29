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

| 接口                     | 职责                         | 时机             | 本项目实现                                 |
| ---------------------- | -------------------------- | -------------- | ------------------------------------- |
| `LogoutHandler`        | 做事（清理：吊销 RT、销毁会话、删 Cookie） | 会话还活着时先跑       | `OidcSessionLogoutHandler`            |
| `LogoutSuccessHandler` | 决定响应（成功后往哪跳）               | 所有 handler 跑完后 | `OidcRpInitiatedLogoutSuccessHandler` |

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

| 票                 | 签给谁      | 给谁用    | 形态/`aud`                    | 生命周期        | 本项用法                         |
| ----------------- | -------- | ------ | --------------------------- | ----------- | ---------------------------- |
| **id_token**      | 客户端(BFF) | 只给 BFF | JWT，`aud`=client_id         | 随会话         | 登录时证明"谁登录了"；**不发 RS**        |
| **access_token**  | 资源服务器    | BFF→RS | JWT，`aud`=nitrowater-server | **15m**     | 每次调 `/api` 的 `Bearer`；RS 验它  |
| **refresh_token** | 客户端(BFF) | 只给 BFF | 不透明串                        | **30d**（轮换） | 换新 AT（+新 id_token）；**不发 RS** |

**与"双 token"区别**：

- OAuth2 语境的「双 token」= `AT + RT`（一个短命访问、一个长命续期）。本项目**确实是双 token**，且 `reuseRefreshTokens=false`（刷新即轮换）。
- `id_token` 是 **OIDC 在 OAuth2 之上加的第三张票**：OAuth2 只回答"能访问什么"，OIDC 补上"你是谁"。故更准确说是「**OIDC 三票**」：id_token(我是谁) + AT(我能访问啥) + RT(怎么续)。
- 真正的差别不在"几张票"，而在**票放哪**：经典 SPA 双 token（oidc-client-ts）由**浏览器持 AT/RT**（易被 XSS）；本项目三票**全在 BFF 服务端**，浏览器零 token。

> ⚠️ 注意 account 里有**两套** token：① OIDC AS 正式签发（`/oauth2/**`，BFF 用这套）；② Phase1 遗留的"自研 AT/RT"（`/api/auth/**`，`AuthController` 标 `@SuppressWarnings("deprecation")`；`application.yml` 的 `token.access.expiration` / `refresh.rotate` / Cookie `REFRESH_TOKEN` 属它，服务已退役的旧 public client）。别拿第 2 套 TTL 去理解 BFF。

---

## Q4. id_token 是不是类似 session_id？浏览器/服务端存？BFF 会让浏览器存吗？OAuth2/OIDC 各体现在哪？account 与 bff 的职责？

### Q4a. `id_token` ≠ `session_id`

|      | session_id（本项目 = `BFFSESSION`） | id_token                       |
| ---- | ------------------------------ | ------------------------------ |
| 本质   | 不透明随机串，只是"指针"                  | 自包含签名 JWT（一份认证收据）              |
| 存哪   | **浏览器 Cookie**（HttpOnly）       | **服务端**（BFF 会话，落 Redis）        |
| 指向   | 指向服务端会话状态                      | 不指向任何东西——内容就在 token 里          |
| 谁校验  | 无人校验，服务端拿它查会话                  | **BFF 登录时做密码学校验**              |
| 身份信息 | 无                              | 有（`sub`/`name`/`roles`/`did`…） |
| 类比   | 寄存处号牌                          | 护照/回执                          |

唯一"像"之处：`id_token` 含 **`sid`** claim（= account 侧 SSO 会话 id 的哈希），登出时告诉 account 销毁哪张会话。但 `sid` 是 JWT 内部字段，不是浏览器 Cookie；account 的 SSO 会话是**另一张独立会话**（`SsoUserPrincipal` + `SessionRegistry`）。

### Q4b. BFF 会让浏览器存 id_token 吗？——**不会**。具体体现：

- 客户端无任何 token 字段：`nitrowater-web/src/auth/bff.ts` 的 `BffSession` 只有 `uid/username/name/roles/did/issuer/expiresAt`；`fetchSession()` 返回身份 JSON，无 token。
- 浏览器只被下发两个 Cookie：`BFFSESSION`（httpOnly，`bff/application.yml`）+ 可读的 `XSRF-TOKEN`；登出删的就是这两个（`SecurityConfig.java:94`）。
- id_token 活在服务端会话：`SessionController.me(@AuthenticationPrincipal OidcUser user)` 里 `user.getIdToken()`（`SessionController.java:51`）即从会话读 id_token；登出 handler 亦然。
- AT/RT 亦在服务端：`HttpSessionOAuth2AuthorizedClientRepository`（`OAuth2ClientConfig.java:27`）。

### Q4c. OAuth2 与 OIDC 在流程中的体现

| 环节                                                                         | 协议                              | 端点/产物                                  |
| -------------------------------------------------------------------------- | ------------------------------- | -------------------------------------- |
| 授权请求 `/oauth2/authorize?response_type=code&nonce=...&scope=openid,profile` | OAuth2（授权码）+ OIDC（openid/nonce） | authorize                              |
| 登录页 / 密码验证码校验                                                              | SSO 自身                          | `/login` + `SsoAuthenticationProvider` |
| 回跳带 code                                                                   | OAuth2                          | redirect_uri                           |
| `/oauth2/token` 换票（client_secret_basic）                                    | OAuth2                          | token                                  |
| 换回 AT+RT                                                                   | OAuth2（双 token）                 | —                                      |
| 换回 id_token                                                                | **OIDC**                        | —                                      |
| 启动拉 discovery                                                              | **OIDC**                        | `/.well-known/openid-configuration`    |
| RS 用 JWKS 验 AT                                                             | OAuth2                          | `/oauth2/jwks`                         |
| 登出 302 `end_session_endpoint?id_token_hint=`                               | **OIDC**                        | end_session                            |
| RT 吊销 `/oauth2/revoke`                                                     | OAuth2（RFC 7009）                | revoke                                 |
| `roles/uid/did/picture` claims                                             | OIDC/SSO 扩展                     | `jwtTokenCustomizer`                   |

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

1. HttpSessionOAuth2AuthorizedClientRepository 存到 Redis 了吗？
   是的。
   代码里：
   @Bean
   public OAuth2AuthorizedClientRepository authorizedClientRepository() {
   return new HttpSessionOAuth2AuthorizedClientRepository();
   }
   这个 Repository 的行为是：把 OAuth2AuthorizedClient（里面包含 accessToken、refreshToken、idToken）作为一个属性，直接存进 HttpSession。
   而 BFF 的配置是：
   spring:
   session:
   data:
   redis:
   namespace: nitrowater:bff:session
   flush-mode: on_save
   Spring Boot + Spring Session Data Redis 会自动把 HttpSession 整个对象（包括它所有的 attributes）序列化后存到 Redis。所以：
- OAuth2AuthorizedClient 并非单独存成一个 Redis Key，而是作为 HttpSession 的一个属性，跟整个 BFF 会话一起存在 Redis 里。
- Redis Key 命名空间是 nitrowater:bff:session:<session-id>（由 Spring Session 控制）。
- 浏览器只收到 Set-Cookie: BFFSESSION=<session-id>; HttpOnly; Path=/; SameSite=Lax。浏览器永远看不到 AT/RT/id_token，只能看到这个 Session ID。
  简单结论：Token 存在 Redis（随 BFF 的 HttpSession），浏览器只持有 BFFSESSION 这个 HttpOnly Cookie。
2. 未登录时，浏览器的用户凭证是怎么经过 BFF -> Account，再返回 HttpOnly Session 的？
   OAuth2 Authorization Code 流程 + Spring Security Form Login（Account 端是传统表单登录）。关键点：登录表单是直接提交到 Account（:8090）的，不是提交到 BFF（:8080）。
   时序如下：
   步骤 1. 浏览器访问 BFF，触发未登录
- SPA 调用 GET http://localhost:8080/api/user/...（需要认证）
- BFF 的 SecurityFilterChain 对 /api/** 要求 .authenticated()（SecurityConfig:73），当前无认证 -> 抛出未认证异常
- 对 /api/** 配置了 defaultAuthenticationEntryPointFor(..., HttpStatusEntryPoint(UNAUTHORIZED))（SecurityConfig:86-88），所以 BFF 返回 HTTP 401（不是 302 跳转登录页）。这是故意设计的：让 SPA 自己决定下一步跳转逻辑。
  SPA 收到 401 后，一般会跳转到 BFF 的 OAuth2 登录入口。
  步骤 2. 跳转到 BFF 的 OAuth2 Authorization Endpoint
  浏览器访问：
  GET http://localhost:8080/oauth2/authorization/nitrowater
  这是 Spring Security OAuth2 Client 自动提供的端点。此时 BFF 还没登录，所以它会构造 OAuth2 Authorization Code 请求，直接返回 302 重定向到 Account 的授权端点。
  步骤 3. 重定向到 Account（Authorization Server）
  302 Location 大致是：
  http://localhost:8090/oauth2/authorize
  ?response_type=code
  &client_id=nitrowater-bff
  &redirect_uri=http://localhost:8080/login/oauth2/code/nitrowater
  &scope=openid%20profile
  &state=...&nonce=...
  浏览器跟随跳转到 http://localhost:8090/oauth2/authorize。
  步骤 4. Account 要求登录（返回登录页）
  Account 的 authorizationServerSecurityFilterChain（@Order(1)，AuthorizationServerConfig:84-102）匹配 /oauth2/authorize 等 AS 端点。对于浏览器发来的 HTML 请求（Accept: text/html）且未认证，它配置了：
  .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
  new LoginUrlAuthenticationEntryPoint("/login"),
  new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
  所以 Account 返回 302 到 /login，再由 LoginPageController 转发到 forward:/auth/login.html，浏览器最终看到 Account 的登录页面。
  步骤 5. 用户提交凭证（直接发到 Account，不经过 BFF）
  浏览器在 Account 的登录页提交表单：
  POST http://localhost:8090/login
  Content-Type: application/x-www-form-urlencoded

username=xxx&password=xxx&captcha=xxx&deviceFp=xxx
Cookie: CAPTCHA_KEY=...
注意：这个 POST 是直接发往 http://localhost:8090/login（Account 端口），不是发到 BFF。 BFF 此时根本没参与这一步，用户凭证也没有经过 BFF。
步骤 6. Account 校验凭证（SsoAuthenticationProvider）
Spring Security 的 formLogin 在 Account 端拦截 /login POST，调用 SsoAuthenticationProvider.authenticate()（SsoAuthenticationProvider:35-90）：

1. 从请求参数读取 captcha、deviceFp，从 Cookie 读取 CAPTCHA_KEY
2. 构造 PwdLoginReq，调用 loginService.login(req, captchaKey)
3. LoginServiceImpl 校验验证码、密码（BCrypt）、失败次数/锁定（Redis）
4. 校验成功后，计算 did，查角色 userRoleService.findRoleCodes(uid)
5. 构建 SsoUserPrincipal（sub = uid，添加 PASSWORD_AUTHORITY）
6. 返回 UsernamePasswordAuthenticationToken.authenticated(...)，写入 Account 端的 HttpSession（SecurityContext）
   到这里，用户已经在 Account（AS）登录成功。Account 自己的会话（JSESSIONID 或默认 Servlet Session）已经建立。
   步骤 7. Account 发放 Authorization Code，重定向回 BFF
   Account 的 Spring Authorization Server 继续处理原本的 /oauth2/authorize 请求（带着 client_id=nitrowater-bff、redirect_uri=http://localhost:8080/login/oauth2/code/nitrowater 等参数）。认证通过后，SAS：
7. 生成一个 Authorization Code
8. 把这个授权码与客户端、重定向 URI、Scopes、Principal、state/nonce 等绑定，持久化到 JDBC（oauth2_authorization）
9. 返回 302 重定向到 BFF 的回调地址：
   Location: http://localhost:8080/login/oauth2/code/nitrowater?code=AUTH_CODE&state=STATE
   浏览器跟随这个 302，访问 BFF 的回调 URL。
   步骤 8. BFF 用 Authorization Code 换 Token（服务端到服务端）
   浏览器访问 GET http://localhost:8080/login/oauth2/code/nitrowater?code=...&state=...。这个请求带着浏览器的 Cookie（此时还没有 BFFSESSION）访问 BFF。
   BFF 的 Spring Security OAuth2 Client（oauth2Login）拦截这个回调路径，处理 Authorization Code 流程。关键：Token 交换是在 BFF 后端服务器之间完成的，浏览器完全不参与。
   Spring Security OAuth2 Client 会：
10. 校验 state（防 CSRF）
11. 向 Account 的 Token Endpoint POST http://localhost:8090/oauth2/token 发起服务端到服务端的请求，使用 Client Secret Basic Auth 鉴权：
    POST http://localhost:8090/oauth2/token
    Authorization: Basic base64(nitrowater-bff:<client-secret>)
    Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=AUTH_CODE
&redirect_uri=http://localhost:8080/login/oauth2/code/nitrowater
Account（Spring Authorization Server）验证 code、client_id/client_secret、redirect_uri 是否匹配，验证通过后签发：

- access_token（JWT）
- refresh_token（Opaque/格式由 SAS 生成）
- id_token（OIDC ID Token，JWT，aud = nitrowater-bff）
  Account 返回 JSON 响应给 BFF。
  步骤 9. BFF 把 Token 存到 HttpSession（Redis），发放 BFFSESSION 给浏览器
  BFF 收到 Token 响应后，Spring Security OAuth2 Client 会：
1. 构造 OAuth2AuthorizedClient 对象，封装 accessToken、refreshToken、idToken、ClientRegistration、Principal（OidcUser）等
2. 调用 OAuth2AuthorizedClientRepository（即 HttpSessionOAuth2AuthorizedClientRepository）把它存入当前请求对应的 HttpSession 的属性里
3. 同时把认证结果（OAuth2AuthenticationToken / OidcUser）放入 SecurityContext，也存进同一个 HttpSession
4. 由于 BFF 启用了 Spring Session Redis（spring.session.data.redis），Spring Session 在请求处理完成时将这个 HttpSession 整体序列化到 Redis（Key：nitrowater:bff:session:<新生成的session-id>）
5. Spring Session 响应时设置 Set-Cookie: BFFSESSION=<session-id>; HttpOnly; Path=/; SameSite=Lax; Secure=false（dev 环境）
   浏览器收到这个响应，只保存了 BFFSESSION Cookie。至此登录流程结束。
   结论（未登录->登录）：
- 用户凭证（用户名/密码/验证码）只提交给 Account（8090），从未到达 BFF。
- BFF 和 Account 之间的 code -> token 交换是纯服务端调用（BFF 使用 client_secret_basic 鉴权），浏览器看不到这个交换过程和任何 Token。
- 最终返回给浏览器的是唯一一个 HttpOnly 的 BFFSESSION Cookie，里面对应的会话数据（包含 Tokens）保存在 Redis。
3. 已登录用户如何 Pass 校验？
   浏览器之后的所有请求，都会自动带上 Cookie: BFFSESSION=<session-id>（浏览器自动附带同源 Cookie）。
   BFF 收到请求时：
4. Spring Session Data Redis 根据 BFFSESSION 的值，从 Redis 中读取 nitrowater:bff:session:<session-id>，反序列化还原出整个 HttpSession 对象（包含之前存进去的 SecurityContext 和 OAuth2AuthorizedClient 属性）
5. Spring Security Context Persistence Filter 从 HttpSession 中恢复 SecurityContext，把它设置到当前线程的 SecurityContextHolder
6. BFF 的安全过滤链判断：当前请求已经有认证（SecurityContext 非空，且是 OAuth2AuthenticationToken/OidcUser），所以 .requestMatchers("/api/**").authenticated() 直接放行，不会再跳转登录
7. ApiProxyController 在处理 /api/** 时（见下面）需要 Token，它会从 OAuth2AuthorizedClientRepository（实际是从 HttpSession 里）读取 OAuth2AuthorizedClient
   所以“已登录校验”完全依赖 BFFSESSION Cookie -> Redis 中的 HttpSession -> SecurityContext，不需要重新输入用户名密码，也不需要 Token 暴露给浏览器。
8. id_token、AT、RT 如何存储？如何交换？
   存储位置
   三个 Token 都封装在 OAuth2AuthorizedClient 这个 Spring Security 对象里，字段大致是：
   OAuth2AuthorizedClient {
   ClientRegistration registration;  // "nitrowater"
   Authentication principal;         // OAuth2AuthenticationToken/OidcUser
   OAuth2AccessToken accessToken;    // AT（含 tokenValue、expiresAt）
   OAuth2RefreshToken refreshToken;  // RT
   OAuth2AuthorizedClientId id;      // ...
   // 对 OIDC，还有 OidcIdToken（id_token）
   }
   Spring Security OAuth2 OIDC 会把 id_token 存到 OidcUser 或通过 OidcIdToken 相关结构持有。最终这个整个 OAuth2AuthorizedClient 对象被 HttpSessionOAuth2AuthorizedClientRepository 以属性名（默认命名方式由 Spring Security 内部定义，按 registrationId + principal 键）存入 HttpSession，再随 HttpSession 一起序列化到 Redis（nitrowater:bff:session:*）。
   所以存储路径：浏览器（只有 BFFSESSION） ←→ BFF（HttpSession in Redis）←→ Redis（序列化的 Session，内含 OAuth2AuthorizedClient + AT/RT/id_token）
   三个 Token 都只存在服务端（BFF 的 Redis Session），绝不会写进浏览器的 Cookie/LocalStorage。
   交换时机
   Token    何时交换（获取）    如何交换
   Authorization Code    步骤 7->8（BFF 回调时）    BFF 后端 POST Account /oauth2/token，grant_type=authorization_code + code + redirect_uri + Basic Auth（client_id:client_secret）（服务端到服务端）
   Access Token（AT）    登录时（随 code 交换一起拿到） + 自动续期时    首次随 code 返回。后续当 ApiProxyController 调用 authorizedClientManager.authorize(...) 时，Spring Security 检测到 AT 即将/已过期，会自动使用会话里的 refresh_token 向 Account /oauth2/token 发起 POST，grant_type=refresh_token + refresh_token + Basic Auth（OAuth2AuthorizedClientProviderBuilder.refreshToken() 已注册，见 OAuth2ClientConfig:34-41）
   Refresh Token（RT）    登录时随 code 交换拿到    同上，随初次 Token 响应返回。之后每次刷新 AT 时可能被旋转（替换为新值）
   ID Token（id_token）    登录时随 code 交换拿到    同 code 交换流程一起返回（OIDC Authorization Code 流）
9. BFF 如何把 AT 传给下游（Server/Resource）
   ApiProxyController（/api/**）在请求时：
   OAuth2AuthorizedClient client = authorizedClientManager.authorize(
   OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
   .principal(authentication)
   .build());
   ...
   if (client != null && client.getAccessToken() != null) {
   spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue());
   }
   这里的 authorizedClientManager.authorize(...) 非常关键：
- 它先从 OAuth2AuthorizedClientRepository（即 HttpSession）加载 OAuth2AuthorizedClient
- 如果 AT 已过期/即将过期，且 Session 里有有效 RT，它会自动调用 Account 的 /oauth2/token（grant_type=refresh_token）完成刷新，更新 HttpSession（和 Redis）中的 Token，然后返回新的 OAuth2AuthorizedClient
- 所以代理请求总是能拿到一个有效（或刚刷新的）AT，透明地附加 Authorization: Bearer <access_token> 转发给 nitrowater-server(:8081) 或 nitrowater-resource(:8093)
  下游 Resource Server 校验这个 JWT（签名、iss、exp、aud）即可，不需要知道 BFF 的 Session 细节。
6. 核心总结
7. Session 存储：HttpSessionOAuth2AuthorizedClientRepository + Spring Session Redis => OAuth2AuthorizedClient（含 AT/RT/id_token）跟 HttpSession 一起存在 Redis（nitrowater:bff:session:*）。浏览器只持有 HttpOnly BFFSESSION。
8. 登录凭证流向：用户名/密码/验证码只发往 Account(:8090)/login。BFF 从未接收这些凭证。
9. Token 交换：Authorization Code → Token 的交换是 BFF 后端 ↔ Account 后端（服务端到服务端，使用 client_secret_basic），浏览器不参与、不看到 Tokens。
10. 认证状态保持：浏览器带 BFFSESSION Cookie -> BFF 从 Redis 恢复 HttpSession 和 SecurityContext + OAuth2AuthorizedClient -> 判断为已登录。
11. 自动续期：OAuth2AuthorizedClientManager.authorize() 在需要时自动用 RT 刷新 AT，并原子地更新 Redis 中的 HttpSession，前端完全无感知。
12. 三方 Token 职责：AT 用于调用下游 Resource Server（Bearer）；RT 用于 BFF 自动刷新 AT；id_token 仅用于 RP-Initiated Logout（向 Account 的 end_session_endpoint 提供 id_token_hint，同步销毁 SSO 会话）。



深入回答：SPA/BFF/OAuth2 流程细节
你问得很关键，这里按你列的 4 个问题逐一拆解，结合源码（BFF 的 OAuth2 Login + Spring Session Redis + Account 的 FormLogin + SAS JDBC 持久化）详细说明。

1. SPA 是什么？“nitrowater-web”？怎么跳转到 BFF 的 /oauth2/authorization/nitrowater？重定向怎么体现？
   1.1 SPA 是浏览器里的前端应用
- SPA（Single Page Application） 指运行在浏览器中的前端单页应用。在本项目里对应的是 nitrowater-web（Vue 3 + Vite）。
- 它托管在哪？ BFF（nitrowater-bff，端口 8080）负责托管 SPA 的构建产物。见 WebConfig（BFF）：
  // /assets/** -> file:./nitrowater-web/dist/assets/ （带哈希，长期缓存）
  // /** -> file:./nitrowater-web/dist/ + History fallback（返回 index.html）
  // Boot 默认静态映射已关闭（spring.web.resources.add-mappings: false）
  所以浏览器访问 http://localhost:8080/、http://localhost:8080/login（前端路由）等，实际都是 BFF 返回 nitrowater-web/dist/index.html（SPA 的入口 HTML）。
- “SPA 是浏览器” 这个说法：准确的说法是“前端 SPA 运行在浏览器中”。整个交互的发起方是浏览器里的 JS 应用（SPA），但它所有的 HTTP 请求都打到同源的 BFF（http://localhost:8080）。
  1.2 SPA 怎么跳转到 BFF 的 OAuth2 登录入口？
  关键在于 BFF 对 /api/** 返回 401 而不是 302（这是故意设计的，给 SPA 控制权）。
  SecurityConfig（BFF:86-88）：
  .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
    request -> request.getRequestURI().startsWith("/api/")))
  所以当 SPA 调用一个需要登录的 API：
  fetch('/api/user/profile')  // 未登录时
  BFF 返回 HTTP/1.1 401 Unauthorized（Body 通常是 Spring Security 的默认响应，但这里是直接 401 状态码）。SPA 没有被强制重定向到登录页，它自己根据 401 做跳转。
  SPA（nitrowater-web）在检测到 401 后，一般会执行类似：
  // 检测到 401
  window.location.href = '/oauth2/authorization/nitrowater'
  也就是说：跳转动作是 SPA（浏览器 JS）发起的，它主动导航到 BFF 的 OAuth2 启动端点。
  当然也可以是用户点击“登录”按钮，按钮直接跳转到 /oauth2/authorization/nitrowater。
  1.3 /oauth2/authorization/nitrowater 是什么？怎么触发重定向到 Account？
  /oauth2/authorization/{registrationId} 是 Spring Security OAuth2 Client 自动注册的端点（BFF 开启了 oauth2Login 并配置了 registration: nitrowater）。
  当浏览器访问 GET http://localhost:8080/oauth2/authorization/nitrowater（未认证状态下访问这个端点）：
1. Spring Security OAuth2 Client 的 OAuth2AuthorizationRequestRedirectFilter 拦截这个请求
2. 它根据 registrationId=nitrowater 从 ClientRegistrationRepository 读取配置（clientId、redirect-uri、scopes、provider 的 issuer-uri/端点信息）
3. 构造 OAuth2 Authorization Request（包含 response_type=code、client_id=nitrowater-bff、redirect_uri={baseUrl}/login/oauth2/code/nitrowater、scope=openid profile、state、nonce 等）
4. 立即返回 HTTP 302 重定向，Location 指向 Account 的 /oauth2/authorize（由 OIDC Discovery 或默认路径拼出的授权端点）
   这个 302 是服务器（BFF）返回给浏览器的响应。浏览器收到 302 后，自动（遵循 HTTP 重定向）发起 GET 请求到 Account 的授权地址。整条链路都是标准的 HTTP 重定向，浏览器地址栏会先变成 BFF 的那个路径（很短暂）再跳转到 Account（:8090）。
   所以“重定向怎么体现的”：体现在 BFF 返回 302 Location 到 Account 授权端点 → 浏览器自动跳转到 Account。
5. formLogin（Account 端）是默认的还是这里配置的？“捕获 /login 然后进 static/login.html” 是什么意思？
   2.1 formLogin 是配置的
   Account 的 defaultSecurityFilterChain（AuthorizationServerConfig:115-145，@Order(2)）里显式配置了：
   .formLogin(form -> form.loginPage("/login").permitAll())
   .logout(l -> l.logoutSuccessUrl("/"))
   所以 不是 Spring Security 的完全默认 formLogin，而是显式配置了登录页路径 /login。如果不配置，默认登录页是 Spring Security 自带的 /login（内置页面），但这里显式指定了 loginPage("/login") 并且项目自己提供了页面。
   2.2 如何“捕获 /login 然后进 static/login.html”
   Account 配置了两个 SecurityFilterChain：
- @Order(1)：authorizationServerSecurityFilterChain 只匹配 SAS 的端点（/oauth2/**, /.well-known/**, ...），不匹配 /login，所以 /login 请求不会走这个链。
- @Order(2)：defaultSecurityFilterChain 匹配所有其他请求（默认匹配规则），而且在 authorizeHttpRequests 里放行了 "/login", "/register", "/auth/**", ...（AuthorizationServerConfig:122）。
  所以当 Account 收到 GET http://localhost:8090/login（HTML 请求）时：
1. 请求先经过 FilterChain，最终落到 @Order(2) 的 defaultSecurityFilterChain
2. 这个链上配置了 .formLogin(form -> form.loginPage("/login").permitAll())，Spring Security 知道“登录页是 /login”
3. 但 Spring Security 本身不会自动返回 HTML 页面，它只是把“未认证访问需要登录的资源且是 HTML 请求时跳到 /login”这个规则建立起来。真正处理 GET /login 返回页面的是控制器。
   项目里有 LoginPageController（Account）：
   @Controller
   public class LoginPageController {
    @GetMapping("/login")
    public String login() {
   
        return "forward:/auth/login.html";
   
    }
    @GetMapping("/register")
    public String register() {
   
        return "forward:/auth/register.html";
   
    }
   }
   @Controller + 返回 "forward:/auth/login.html" 表示服务器内部转发到 /auth/login.html。而 /auth/login.html 是静态资源，位于 nitrowater-account/src/main/resources/static/auth/login.html。
   Account 的静态资源配置（application.yml:61-66）：
   spring.web.resources.static-locations: "file:./nitrowater-account/src/main/resources/static/,classpath:/static/"
   所以整个流程是：浏览器 GET /login → LoginPageController 接收 → 内部 forward 到 /auth/login.html → 静态资源处理器返回 login.html 内容。
   “捕获 /login”指的是 LoginPageController 的 @GetMapping("/login") 捕获了 GET 请求，“进 static/login.html”指的是 forward 到静态目录下的 HTML。
   2.3 POST /login 是怎么处理的？
   表单里是 <form method="post" action="/login">（Account 的 login.html 第 45 行左右）。用户提交 POST 到 http://localhost:8090/login。
   这个 POST 请求同样落到 @Order(2) 的 defaultSecurityFilterChain。Spring Security 的 formLogin() 自动注册了 UsernamePasswordAuthenticationFilter，它默认拦截 POST /login（路径和 loginPage 一致）。过滤器提取表单参数 username、password（以及你页面里加的 captcha、deviceFp），封装成 UsernamePasswordAuthenticationToken（未认证），然后交给 AuthenticationManager。
   项目在 Account 配置了自定义的 SsoAuthenticationProvider（标注了 @Component），被 Spring Security 自动注册进 AuthenticationManager。于是 SsoAuthenticationProvider.authenticate() 被调用（步骤 6 的那段），完成验证码/密码校验、构造 SsoUserPrincipal 并返回认证成功的 Authentication。
   认证成功后，Spring Security 在 Account 端建立 HttpSession（SecurityContext），并继续处理原本被中断的 /oauth2/authorize 请求（因为登录是为了完成最开始的授权码请求）。这就是为什么登录成功后会自动重定向回 BFF 的回调地址（步骤 7）。
4. SAS 是什么？步骤 7 是怎么持久化的，代码里找不到，是封装到库里面的？
   3.1 SAS 是 Spring Authorization Server
   SAS = Spring Authorization Server（项目依赖：Spring Security + Spring Authorization Server 7.x）。这是 Spring 官方实现的 OAuth 2.1 / OIDC Authorization Server。
   所以文档里提到的 “SAS” 都是指这个框架组件，不是项目自定义的类名（缩写）。
   相关 Bean：
- OAuth2AuthorizationServerConfigurer（AuthorizationServerConfig:98）用来配置 SAS 的端点与安全
- AuthorizationServerSettings（281-284）配置 issuer
- RegisteredClientRepository、OAuth2AuthorizationService、OAuth2AuthorizationConsentService 是 SAS 的核心持久化接口
  3.2 步骤 7 的“持久化”指什么？授权码、授权状态存到哪？
  步骤 7 是：Account（SAS）认证成功后，继续处理原始的 /oauth2/authorize 请求，生成 Authorization Code 并重定向回 BFF。
  这个过程的持久化指的是 SAS 将“授权授权（authorization）状态”保存到数据库，以便后续 BFF 用 code 换 token 时能验证 code 的有效性、scope、principal 等。
  SAS 内部会创建一个 OAuth2Authorization 对象，表示“一次授权（Authorization Code）流程的状态”，包含：
- authorization_code_value（授权码本身，通常是加密/以安全方式存储）
- principal_name（对应 SsoUserPrincipal.getUsername() = uid）
- registered_client_id（nitrowater-bff）
- authorized_scopes（openid, profile）
- attributes（存储 Principal、Authentication 等元数据，尤其是 SsoUserPrincipal 对象本身）
- state、过期时间等
  SAS 把这个对象写入数据库的 oauth2_authorization 表。
  3.3 代码里为什么找不到写入逻辑？
  因为这些持久化操作是 SAS 框架内部实现的。你配置的 Bean 是：
  @Bean
  public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
  
        RegisteredClientRepository registeredClientRepository) {
  
    JdbcOAuth2AuthorizationService service =
  
            new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
  
    // 自定义 JsonMapper（为了支持 SsoUserPrincipal）
    ...
    return service;
  }
  JdbcOAuth2AuthorizationService 是 SAS 提供的类（Spring Authorization Server 的 JDBC 实现）。当 SAS 需要保存/读取 OAuth2Authorization 时，它会调用这个 service 的内部方法（save()、findById()、findByAuthorizationCode() 等），这些方法内部执行 SQL 向 oauth2_authorization 表写入/读取数据。
  所以“封装到库里面的”：是 Spring Authorization Server 的 JAR 包（spring-security-oauth2-authorization-server）里实现的 JDBC 持久化逻辑。
  数据库表结构在 Flyway 里：
- oauth2_registered_client（V1__sso_baseline.sql）—— 客户端注册信息
- oauth2_authorization（V1__sso_baseline.sql）—— 授权码、Token 状态等都存在这里（AT/RT/id_token 的值也以 BLOB 形式存储在这张表的相关字段里）
- oauth2_authorization_consent（V1__sso_baseline.sql）—— 授权同意（本项目设为 requireAuthorizationConsent=false 所以基本不用）
  你在 AuthorizationServerConfig:159-175 对 JdbcOAuth2AuthorizationService 设置了自定义的 JsonMapperOAuth2AuthorizationRowMapper 和 ParametersMapper，目的是让它能正确序列化/反序列化 SsoUserPrincipal（因为 SAS 默认的 Jackson 配置不允许反序列化 cn.nitrowater.account.* 包下的自定义类型，代码里通过 validatorBuilder.allowIfSubType("cn.nitrowater.account.") 放宽了限制，见 authorizationJsonMapper():177-184）。
  所以持久化是“框架做的 + 你配置了 JDBC 实现 + 自定义了序列化策略”。
4. 三个 Token（AT/RT/id_token）如何存储与交换（最关键）
   前面讲过 BFF 层面是存到 HttpSession（Redis）。Account（SAS）层面呢？
   4.1 Account 签发时：Token 写到哪里？
   Account 在 /oauth2/token 端点签发 AT/RT/id_token 时，SAS 的 OAuth2TokenGenerator（内部组件）会生成这些 Token，并把它们一并写入 oauth2_authorization 表对应的那一行记录里。
   以 oauth2_authorization 表结构为例（V1 基线 SQL）：
   字段    存储内容
   access_token_value    Access Token 的值（通常是 JWT 字符串，存成 BLOB）
   access_token_issued_at / access_token_expires_at / access_token_metadata / access_token_type / access_token_scopes    AT 元数据
   refresh_token_value    Refresh Token 的值（Opaque 字符串）
   refresh_token_*    RT 元数据
   oidc_id_token_value    ID Token（JWT） 的值
   oidc_id_token_*    ID Token 元数据
   authorization_code_value    授权码值（用完后一般失效）
   ...    其他字段（principal_name、registered_client_id、attributes 等）
   所以 Account（AS）端签发的 Token 本身也持久化在 MySQL 的 oauth2_authorization 表里（这是 SAS 的 JDBC 存储设计：授权状态 + Token 一起持久化，便于校验、撤销、刷新等）。
   4.2 BFF 拿到 Token 后存到哪？
   BFF 在收到 Token 响应（code→token 交换结果）后：
5. Spring Security OAuth2 Client 解析 JSON（包含 access_token, refresh_token, id_token, expires_in, token_type）
6. 构建 OAuth2AuthorizedClient（内部有 OAuth2AccessToken, OAuth2RefreshToken, OidcIdToken）
7. 通过 HttpSessionOAuth2AuthorizedClientRepository.putAuthorizedClient(client, authentication, request, response) 把它存入当前 HttpSession 的属性
8. Spring Session 在请求结束时将 HttpSession 序列化到 Redis（nitrowater:bff:session:<session-id>）。此时 Redis 里保存的是整个 HttpSession 对象，里面包含这个 OAuth2AuthorizedClient（以及 AT/RT/id_token 的值）
   结论：双重持久化
- Account 端（AS）：Token 的“签发记录”和状态保存在 MySQL oauth2_authorization 表（SAS 框架自动处理）
- BFF 端（Client）：当前会话实际使用的 Token 副本保存在 Redis 的 HttpSession（浏览器只持有 BFFSESSION）
  4.3 Token 交换的全链路（明确版）
  阶段    谁发起    grant_type    端点    鉴权方式    结果存储
1. 获取授权码    浏览器（跟随 302）访问 Account /oauth2/authorize    （授权请求）response_type=code    Account :8090/oauth2/authorize    基于 Account 的表单登录会话（SecurityContext in AS HttpSession）    授权码 + 状态写入 MySQL oauth2_authorization（SAS 内部）
2. code → AT/RT/id_token    BFF 后端（Spring OAuth2 Client 回调处理器）    authorization_code    Account :8090/oauth2/token    Client Secret Basic Auth（Authorization: Basic base64(nitrowater-bff:<secret>)）    Account 签发 Tokens → 写入 MySQL oauth2_authorization；BFF 收到响应 → 构建 OAuth2AuthorizedClient → 存入 BFF HttpSession（Redis），返回 BFFSESSION Cookie 给浏览器
3. AT 刷新    BFF 后端（OAuth2AuthorizedClientManager.authorize() 自动触发）    refresh_token    Account :8090/oauth2/token    Client Secret Basic Auth    Account 验证 RT → 签发新 AT（可能旋转新 RT）→ 更新 MySQL oauth2_authorization；BFF 更新其 HttpSession（Redis） 中的 OAuth2AuthorizedClient
4. 撤销 RT（登出）    BFF 后端（OidcSessionLogoutHandler）    （RFC 7009）    Account revocation_endpoint（默认 /oauth2/revoke）    Client Secret Basic Auth    Account 将对应 RT 标记为失效（更新 oauth2_authorization 中的 RT 字段/状态），BFF 清理 HttpSession（Redis）并让浏览器清除 BFFSESSION
   简短总结（回答你的核心点）
5. SPA ≠ 浏览器本身：SPA 是 nitrowater-web（Vue/Vite）打包的前端应用，运行在浏览器里，由 BFF（:8080）托管静态资源。SPA 检测到 401 后主动跳转 window.location.href = '/oauth2/authorization/nitrowater'，触发 BFF 返回 302 到 Account 的 /oauth2/authorize，浏览器自动跟随重定向。
6. formLogin 是显式配置的（Account 的 defaultSecurityFilterChain 调用 .formLogin(form -> form.loginPage("/login"))）。LoginPageController 的 @GetMapping("/login") 返回 forward:/auth/login.html，将 GET /login 转发到静态资源 static/auth/login.html。POST /login 则由 Spring Security 的 UsernamePasswordAuthenticationFilter 拦截，由 SsoAuthenticationProvider 处理。
7. SAS = Spring Authorization Server。步骤 7 中“授权码 + 授权状态的持久化”是 SAS 的 JdbcOAuth2AuthorizationService（框架类）在内部完成的，SQL 写入 oauth2_authorization 表。你在代码里配置了这个 Bean 并自定义了 Jackson 序列化器（支持 SsoUserPrincipal），但具体 INSERT/UPDATE 是封装在 SAS 的 JAR 内部实现，不是你写的显式方法。
8. Token 存储：Account 签发时写入 MySQL oauth2_authorization（全量状态）；BFF 用 code 换到 Token 后写入 Redis 的 HttpSession（nitrowater:bff:session:*），浏览器只拿到 HttpOnly BFFSESSION Cookie。Token 交换全部是BFF 后端 ↔ Account 后端（服务端到服务端，Basic Auth），浏览器从不接触 AT/RT/id_token。自动续期是 OAuth2AuthorizedClientManager 在代理 API 时检测过期并自动用 RT 向 Account /oauth2/token（grant_type=refresh_token）刷新，刷新结果会同时更新 MySQL（Account 端状态）和 Redis（BFF 的 HttpSession）。
