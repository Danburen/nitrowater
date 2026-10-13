# OIDC + BFF 认证流程详解

> 高度关注的核心问题：SPA / BFF / Account 三者如何协作、Session 存储到 Redis、状态刷新、授权码转 Token、Token 存储等
> 基于前两轮问答整理完善到本文档中使用。

本文档整合了前述问答内容，用于清晰说明 NitroWater 项目的 OIDC + BFF 认证流程。

## 1. 核心设计原则

整个系统只在浏览器中保留一个 HttpOnly 的会话 Cookie（BFFSESSION），id_token、ccess_token（AT）、
efresh_token（RT）**全部只存储在 BFF 端的 HttpSession 中**，并由 Spring Session + Redis 进行持久化。浏览器永远不会接触到任何 Token。

**职责划分：**

- **Account（nitrowater-account，端口 8090）**：Spring Authorization Server（AS/OP），负责认证（表单登录）、签发 Token、提供 JWKS、RP-Initiated Logout 端点。
- **BFF（nitrowater-bff，端口 8080）**：OAuth2/OIDC Confidential Client（Token Handler），托管 SPA 静态资源，代理 /api/** 并自动附加 Bearer AT，管理服务端 Token 与会话。
- **Server/Resource（nitrowater-server/resource，端口 8081/8093）**：OAuth2 Resource Server，仅校验 AT（JWT）并做授权。
- **SPA（nitrowater-web）**：运行在浏览器的前端应用，由 BFF 同源托管。

---

## 5. 补充要点：5 个关键问题

### 5.1 SPA 如何触发登录

SPA 检测到 BFF 对 /api/** 返回 401（未认证），或者用户点击登录按钮时，主动跳转：

`	ext
window.location.href = '/oauth2/authorization/nitrowater'
`

BFF 收到该请求后，Spring Security OAuth2 Client 构造授权请求并返回 302 重定向到 Account 的 /oauth2/authorize。

### 5.2 浏览器与 Account 的表单登录

Account 的 defaultSecurityFilterChain 配置了 .formLogin(form -> form.loginPage("/login"))。LoginPageController 的 @GetMapping("/login") 返回 orward:/auth/login.html，将 GET /login 转发到静态资源 static/auth/login.html。

用户在登录页提交 POST /login（**直接发往 Account:8090**），该请求由 Spring Security 的 UsernamePasswordAuthenticationFilter 拦截，并交由自定义的 SsoAuthenticationProvider 进行验证码、密码、锁定等校验。校验成功后，在 Account 端建立 SecurityContext 会话，并由 Spring Authorization Server 生成 Authorization Code，随后返回 302 重定向到 BFF 的回调地址：

`	ext
http://localhost:8080/login/oauth2/code/nitrowater?code=...&state=...
`

### 5.3 SAS（Spring Authorization Server）如何持久化授权状态

**SAS = Spring Authorization Server**。授权码、授权状态以及后续签发的 Token 状态由 SAS 的 JdbcOAuth2AuthorizationService（框架提供的 JDBC 实现类）在内部完成持久化，相关数据写入 MySQL 的 oauth2_authorization 表。

项目在 AuthorizationServerConfig 中显式配置了该 Bean，并通过自定义 JsonMapper（放宽对 cn.nitrowater.account.* 包的子类型校验）支持 SsoUserPrincipal 的序列化/反序列化。具体的 SQL（INSERT/UPDATE/SELECT）逻辑是**封装在 Spring Authorization Server 的 JAR 内部**，代码中并未直接编写这些语句。

同时，oauth2_registered_client 表存储客户端（nitrowater-bff）注册信息，oauth2_authorization_consent 存储授权同意信息（项目配置为 equireAuthorizationConsent=false，实际使用较少）。

### 5.4 Token 存储位置

- **Account 端（AS）**：签发的 Token 及其元数据（值、过期时间、scope、metadata 等）保存在 MySQL oauth2_authorization 表中（SAS JDBC 持久化）。
- **BFF 端（Client）**：当前会话实际使用的 OAuth2AuthorizedClient（封装了 ccessToken、efreshToken、idToken）作为 HttpSession 属性存储。由于配置了 spring.session.store-type 为 Redis（通过 spring-boot-starter-session-data-redis），**整个 HttpSession 都被序列化到 Redis**，Key 前缀为 
itrowater:bff:session:<session-id>。
- **浏览器**：仅持有 HttpOnly 的 BFFSESSION Cookie（指向上述 Redis 中的 HttpSession），**从不持有 AT/RT/id_token**。

### 5.5 code→token 交换与自动刷新

1. **code→token（登录回调时）**：浏览器访问 BFF 回调 /login/oauth2/code/nitrowater。Spring Security OAuth2 Client 在**BFF 后端**向 Account 的 /oauth2/token 发起 POST 请求，使用 client_secret_basic 鉴权（Authorization: Basic base64(nitrowater-bff:<secret>)）。Account 验证 code 后签发 AT/RT/id_token，并将状态写入 oauth2_authorization 表。BFF 收到响应后构造 OAuth2AuthorizedClient 并写入 HttpSession（Redis），随后向浏览器设置 BFFSESSION Cookie 并重定向到 SPA 根路径。
2. **自动刷新 AT**：SPA 调用 /api/** 时，BFF 的 ApiProxyController 通过 OAuth2AuthorizedClientManager.authorize() 获取当前会话的 OAuth2AuthorizedClient。若检测到 AT 已过期且存在有效 RT，OAuth2AuthorizedClientManager 会**自动**在 BFF 后端向 Account 的 /oauth2/token 发起 grant_type=refresh_token 请求（同样使用 client_secret_basic 鉴权），获取新的 AT（以及因 euseRefreshTokens=false 而可能旋转的新 RT）。刷新结果会**同步更新** MySQL 中的 oauth2_authorization 记录以及 Redis 中的 BFF HttpSession。最终 BFF 自动将新的 Bearer AT 附加到转发给下游（Server/Resource）的请求中，前端对此完全无感知。
3. **登出（RP-Initiated Logout）**：BFF 的 OidcSessionLogoutHandler 在登出时优先用 RT 刷新一次以获取**最新的 id_token**，并向 Account 的 evocation_endpoint（RFC 7009）撤销 RT。随后 OidcRpInitiatedLogoutSuccessHandler 构造跳转 URL（带 id_token_hint 和 post_logout_redirect_uri）重定向到 Account 的 nd_session_endpoint，以同步销毁 Account 端的 SSO 会话。最后清理 BFF 的 HttpSession（Redis）并删除浏览器的 BFFSESSION、XSRF-TOKEN Cookie。

### 5.6 已登录状态校验

浏览器后续请求自动携带 BFFSESSION Cookie。BFF 收到请求后：

1. Spring Session 从 Redis 读取并反序列化对应的 HttpSession（
itrowater:bff:session:<session-id>），恢复其中的 SecurityContext 和 OAuth2AuthorizedClient。
2. Spring Security 将 SecurityContext 放入 SecurityContextHolder，BFF 的安全过滤链据此判断请求已认证（/api/** 放行）。
3. ApiProxyController 从 HttpSession 中获取（或自动刷新）OAuth2AuthorizedClient，提取 AT 并附加 Authorization: Bearer <access_token> 转发给下游 Resource Server。
4. Resource Server（Server 模块）使用 spring.security.oauth2.resourceserver.jwt 配置的 issuer-uri、jwk-set-uri 以及自定义 AudienceValidator 校验 AT 的签名、iss、xp/nbf、ud，并通过 JwtAuthenticationConverter 将 oles/scope 映射为 GrantedAuthority 完成授权。

## 6. 一句话总结

**浏览器只持有 HttpOnly 的 BFFSESSION。BFF 是机密型 OIDC 客户端：登录时在 BFF 后端完成 code→token 交换并将 Tokens 存入 Redis 中的 HttpSession；代理 API 时自动用 RT 刷新 AT 并附加 Bearer 头转发给 Resource Server。Account（Spring Authorization Server）负责表单认证和 Token 签发，授权状态持久化到 MySQL 的 oauth2_authorization 表。登出时通过 RP-Initiated Logout（带 id_token_hint）同步销毁 Account 端 SSO 会话。**
