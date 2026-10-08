# Day 3 反思录 · huangcong

> 日期：2026-10-08 ｜ 项目：NitroWater
> 关键词：Resource Server、SSO/业务分库、G4 一次登录跨项目、OIDC 静默续期、转 API 测试

## 一、今天的反思区

### 1. 今天做了什么
- **issuer 统一（决策 A）**：改为单一 `jwt.issuer`（env `JWT_ISSUER`，dev 默认 `http://localhost:8090`），Phase1 自研 AT 与 AS(OIDC) 共用，且等于发现文档 `issuer`；移除 `sso.issuer`，避免两份配置漂移。
- **AT/RT 废弃 + 死代码清理**：`AccessTokenService`/`AuthCoreService` 标 `@Deprecated`（仅 `/api/auth/**` 兼容层使用）；删除一批无引用类/方法。
- **Phase 2.6 前端接入**：`nitrowater-web` 接 `oidc-client-ts`（授权码 + PKCE、`/auth/callback`、silent renew、logout）+ `vue-router` + Element Plus 门户壳；接入 ESLint（flat config）。
- **SSO 托管登录/注册页**：在 account 的 auth 域做无框架品牌页（仿 WaterFun `AuthBox`）；登录是原生表单 `POST /login` → Spring Security → 续跑 OIDC，注册调 `/api/auth/**`。
- **全局异常处理**：account 加 `@RestControllerAdvice`，把校验/认证/业务异常统一成 core `ErrorResponse`——否则校验失败会退化成 Spring 默认 400 页，前端拿不到 message；并补齐 `user.password.pattern` 等 message key。
- **静态资源热改**：`static-locations` 指向源码目录 + `cache.period=0`，改 `static/auth/*` 刷新即生效。
- **roles 序列化修复**：`SsoUserPrincipal.getRoles/getAuthorities` 归一回 `ArrayList`，修复 AS JDBC 类型化对 `ImmutableCollections` 的拒绝，并加回归测试。
- **判题沙箱设计 + MC 频道规划**：产出 `docs/design/judge-sandbox.md`（ACM/LeetCode 双模式、沙箱三件套、AC/WA/TLE/MLE/RE/CE/OLE），PRD 补 FR-2.10/2.11 与 §2.5。
- **Resource Server 落地（今天主线）**：`nitrowater-server` 裁为 webmvc + security + oauth2-resource-server；`/api/**` 需 JWT、`/api/public/**` 放行、stateless、CORS 放行 5173；`MeController` 提供 `/api/public/ping` 与 `/api/me`；`issuer-uri`/`jwk-set-uri` 取 `${JWT_ISSUER:http://localhost:8090}`（懒加载 JWKS，AS 未起也能启动）；端口 8081。
- **分库隔离**：SSO 库 `nitrowater` → `nitrowater_account`（`RENAME TABLE` 同实例搬 12 表含 `flyway_schema_history`，旧库 DROP）；业务库独立为 `nitrowater_biz`（`createDatabaseIfNotExist`、`ddl-auto=none`、flyway 暂关）；跨库仅 `uid` 逻辑关联、不建 FK。
- **RS 联调 + 转 API 测试**：先做 `/rs-test` 联调页跑通（`/ping` 200、`/me` 无 token 401 / 带 token 200），随后确定以 **API 测试**为主路径，移除联调页并清掉连带死配置。

### 2. 收获与认识
- **G4「一次登录跨项目免登」在单仓内可证**：SPA 拿 AS 的 access_token，RS 用同一 issuer + JWKS 验签 → 带 token 200、无 token 401，信任链闭环。
- **服务方与身份方解耦边界清晰**：RS 只认 JWT（`iss` + JWKS），不碰 SSO 库；`/api/public/**` 留探活口。
- **懒加载 JWKS 的价值**：显式给 `jwk-set-uri`，避免启动时强制 discovery → AS 未起也能起 RS，只在首个受保护请求时才拉公钥。
- **联调页是过程工具，不是产品**：验证完就应下线，别把调试壳和死配置留在门户里。

### 3. 踩坑与教训（今天最值钱的一栏）
- **登录按钮"点了没反应" = SSO 服务没起**：SPA 的 `signinRedirect()` 第一步就 fetch `:8090/.well-known/openid-configuration`，拿不到即 reject；而该 Promise **没有 `.catch`** → 表现成静默无响应。教训：**所有会发网络请求的登录/续期入口必须处理 rejection 并给用户反馈**，否则"无反应"会掩盖真实原因。
- **`/.well-known/openid-configuration` 被疯狂刷 = 自动静默续期失控**，根因链：① Spring Authorization Server **不给公共客户端发 refresh_token**（官方明说，实测 `oauth2_authorization.refresh_token_value` 全为 NULL）；② 于是静默续期只能走 iframe，而客户端只注册了 `/auth/callback`，**没注册 `/auth/silent-callback`** → authorize 被拒；③ `oidc-client-ts` 对 iframe 超时**默认每 5 秒无限重试**（`maxSilentRenewTimeoutRetries` 缺省 = 无限）→ 反复拉 discovery。教训：**公共 SPA 客户端不要默认开 `automaticSilentRenew`**；要用则必须注册 silent 回调、限制重试次数、并让 silent 回调页用不自动续期的 UserManager。
- **DB 改名的正确姿势**：MySQL 无 `RENAME DATABASE`，用 `RENAME TABLE` 同实例跨库搬表（连 `flyway_schema_history` 一起），并同步脚本/配置，否则 Flyway 历史对不上。
- **种子客户端不幂等更新**：`registeredClientSeeder` 只在不存在时插入，**改 `RegisteredClient` 的 Java 定义不会更新已落库的行** → 改 redirect_uris/scopes 必须直接改库或删行重建。
- **小坑延续**：`.ps1` 用 UTF-8 with BOM；`@EntityScan`/`@EnableJpaRepositories` 是字符串字面量，改包名要同步。

### 4. 下一步计划
- **API 测试**：用 API-Fox 直连 RS——`/api/public/ping`（200）、`/api/me`（无 token 401 / 带 token 200）；token 取 SPA 登录后的 `localStorage`（公共客户端无密码/refresh 模式）。
- **修 OIDC 静默续期**：按"公共客户端"策略收敛（默认关自动续期，或注册 silent 回调 + 限制重试）。
- **Phase 2.8**：WaterFun 网关联 JWKS + `/api/auth/** → SSO` 置顶路由（跨仓）。
- 收尾：提交 `交作业自查.py` 修复；`element-plus` 按需导入瘦身。

## 二、对话档案

### 对话 1 · SSO 与业务要不要分开建库？
**我**：业务接口和 SSO 用同一个库行不行？
**AI**：建议物理分库——SSO 库（`nitrowater_account`）与业务库（`nitrowater_biz`）隔离，两者仅用 `uid` 做逻辑关联、不建跨库 FK。身份域与业务域各自演进/迁移，权限与备份也解耦。库名用 `RENAME TABLE` 同实例搬表完成，注意把 `flyway_schema_history` 一起搬并同步脚本。
**结论**：采纳，SSO/业务物理分库。

### 对话 2 · Resource Server 只配 `issuer-uri` 够吗？
**我**：Resource Server 配 `issuer-uri` 就够了吧？
**AI**：只给 `issuer-uri` 会在启动时强制做 OIDC discovery，AS 没起就启动失败。**两个都给**：用 `jwk-set-uri` 懒加载公钥，同时保留 `issuer-uri` 做 `iss` 校验——AS 未起也能起 RS，首个受保护请求才拉 JWKS。
**结论**：`issuer-uri` + `jwk-set-uri` 都配，均取自 `${JWT_ISSUER:http://localhost:8090}`。

### 对话 3 · 登录按钮没反应、discovery 被刷爆，是同一问题吗？
**我**：网页登录按钮点了没反应，而且 `/.well-known/openid-configuration` 一段时间后疯狂出现，怎么定位？
**AI**：两个问题——① 无反应是 **8090 没在跑**：`signinRedirect()` 的 discovery 请求失败且 Promise 没 `.catch`，所以静默无响应；② 刷 discovery 是**自动静默续期**在失控：公共客户端拿不到 refresh_token（SAS 既定行为），只能走 iframe，而 silent 回调地址没注册 → 被拒 → `oidc-client-ts` 每 5s 无限重试。修法：先起 account 验证登录；再把静默续期按"公共客户端"重新设计。
**结论**：先启动 account 验证登录；静默续期策略待重做；本次测试改用 API 方式。

### 对话 4 · UI 联调页留还是删？
**我**：UI 测试页没问题，但我后面打算用 API 测试。
**AI**：那就把 `/rs-test` 页移除——它只是联调过程工具，留着会让门户多一个调试入口和死配置（`VITE_RS_BASE`）。移除路由/页面/env 声明即可，后端 RS 的 `/api/**` 保持不变，API 测试不依赖该页。
**结论**：移除 UI 测试页 + 清 `VITE_RS_BASE`，lint/typecheck 全绿。
