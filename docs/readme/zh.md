# NitroWater

[![GitHub](https://img.shields.io/badge/GitHub-danburen%2Fnitrowater-181717?logo=github)](https://github.com/danburen/nitrowater)
[![Gitee](https://img.shields.io/badge/Gitee-blackwallet%2Fnitrowarer-c71d23?logo=gitee)](https://gitee.com/blackwallet/nitrowarer)
![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![Spring Security](https://img.shields.io/badge/Spring%20Security-7.1-6DB33F)
![License](https://img.shields.io/badge/License-see%20LICENSE-lightgrey)

> 🌐 [English](../../README.md) | **简体中文**

**多项目共用的服务端平台** + **功能复合型展示网站**（导航 · 工具箱 · 编程演练场 · 文档），以及**独立完整的 SSO / OIDC 认证中心**（用于迁移并统一 WaterFun 生态的登录认证）。

---

## NitroWater 是什么？

NitroWater 是一个新建的 Gradle 多模块工程，把四个独立诉求收束到同一个平台：

1. **工具集整合** —— 把独立的 `wtools` 前端（7 个工具）并入统一入口；
2. **编程演练场后端化** —— 用真实的「编译 → 沙箱运行 → 判题」服务替代浏览器 WASM 判题；
3. **全站单点登录** —— 把 WaterFun 的登录/注册抽离为独立认证中心（`nitrowater-account`），实现跨项目 SSO；
4. **复合展示站** —— 将 VeloChatX 文档站（`velochatx-docs`，VitePress）与工具箱、演练场并列整合，全站接入 SSO。

其中 **SSO / OIDC 认证中心**（`nitrowater-account`）是当前重点，也是最成熟的子系统。

## 目标

| # | 目标 | 验收标准 |
|---|---|---|
| G1 | 工程基线 | `gradlew build` 全模块全绿 |
| G2 | 合并 `wtools` | 7 个工具在 `nitrowater-web` 内全部可用 |
| G3 | 演练场后端化 | 提交 → 编译 → 沙箱运行 → 判题（AC/WA/TLE/RE/CE） |
| G4 | 全站 SSO | 一次登录，其它接入项目免登 |
| G5 | 认证迁出 WaterFun | 登录/注册/验证码/token 由 SSO 服务签发 |
| G6 | 复合展示站 | `velochatx-docs` 产物挂载到 `/docs/**` |

## 模块

| 模块 | 职责 | 状态 |
|---|---|---|
| `nitrowater-core` | 共享库（实体、仓储、认证服务、安全工具），不独立部署 | ✅ 认证核心已迁移 |
| `nitrowater-account` | **SSO / OIDC 认证中心** —— 登录注册 API + OIDC Provider + 托管登录页 | ✅ Phase 1 完成 · ✅ Phase 2.5（JDBC + 令牌定制） · ✅ 已播种 confidential BFF 客户端 |
| `nitrowater-bff` | **BFF（Backend for Frontend / Token Handler）** —— confidential OIDC 客户端；浏览器只持 HttpOnly 会话 Cookie，令牌留在服务端，`/api/**` 代理到资源服务 | ✅ Phase 3（BFF）完成 |
| `nitrowater-server` | 业务 API —— 工具箱后端、题目/提交管理、静态托管、Resource Server | ⏳ 仅骨架 |
| `nitrowater-web` | 前端（Vue 3 + TypeScript + Vite）—— 导航门户、工具箱、演练场、文档频道、SSO 登录 | 🚧 SSO 门户壳（Element Plus）完成 · 现为同源 BFF 客户端 · 工具箱/演练场/文档待续 |
| `nitrowater-judge` | 演练场判题服务（编译 → 沙箱 → 判题） | ⏳ 规划中（模块未创建） |

## 当前状态

### ✅ 已完成

- **工程基线（M0）** —— Gradle 多模块（`core` / `account` / `server`），Java 25 + Spring Boot 4.1.1，`gradlew build` 全绿；修复 Gradle wrapper 下载超时与悬空的 `waterfun-core` 依赖。
- **认证核心迁移** —— WaterFun 的 `common-lib` + `service-core` 认证切片 + `exception` 整树（约 203 个文件）迁入 `nitrowater-core`（`cn.nitrowater.core`），并砍除业务耦合（以 `TODO(SSO)` 标记）。`core` 为**纯库**（`bootJar` 已禁用）。
- **Phase 1 —— 独立 SSO 服务**（`nitrowater-account`）—— `/api/auth/**` 契约与 WaterFun 完全一致（前端/网关零改动）。端到端冒烟通过：
  `captcha → send-code → register → /account → 无 token 401 → refresh → logout`。
  Phase 1 **刻意不引入 Spring Security**：自研双 token（RS256 JWT + RT HttpOnly Cookie）+ `LocalAuthContextFilter`（AT → `UserCtxHolder`）+ 401 守卫。
- **SSO 数据库** —— MySQL 库 `nitrowater`，由单一 Flyway 基线 `V1__sso_baseline.sql` 建成 **6 张表**：`user`、`user_data`、`user_data_archive`、`account_audit_log`、`encryption_data_key`，以及为第三方登录（QQ / 微信 / GitHub）预留的 `sso_identity`。
- **CORS 与 Cookie** —— `CorsConfig` 使用 `allowedOriginPatterns` 白名单 + `allowCredentials=true`（涵盖同主域 `*.nitrowater.cn` 与跨主域 `waterfun.top`）；Cookie 的 `SameSite` / `Secure` 通过 `app.cookie.*` 可配。
- **运维脚本**（`deploy/`）—— 启动 Redis/account、环境自检、密钥生成、SSO 清库重建、MySQL 备份、交互式认证冒烟。
- **Phase 2（OIDC）骨架** —— Spring Security 7.1 + Spring Authorization Server 7.1 已接线：
  - 授权服务器链（Order 1）+ 默认链（Order 2），JWKS 复用 core 的 RSA 密钥对；
  - `SsoAuthenticationProvider` 把认证委托给现有 `LoginService`（验证码 / 失败锁定 / DEK 全复用），主体为 `SsoUserPrincipal`（`getUsername()=uid`）；
  - 托管 SSO 登录页 `/login`；
  - 为 `nitrowater-web` 注册的客户端（公共客户端、PKCE、授权码 + 刷新令牌），Phase 2.5 起改 JDBC 持久化；
  - 已验证端点：`/.well-known/openid-configuration` → 200、`/login` → 200、`/api/auth/captcha` → 200。
- **Phase 2.4 —— 令牌定制与登录主体** —— `SsoUserPrincipal` 承载完整身份（uid / 登录名 / 昵称 / userType / 状态 / 设备）；`OAuth2TokenCustomizer` 输出 `sub=uid`、`uid`、`preferred_username`、`name`、`roles`、`did`（并经 `/userinfo` 暴露）；角色自 Phase 2.5 起由 `user_role` 表派生，无绑定时回落 `ROLE_USER`。
- **Phase 2.5 —— AS JDBC 持久化（方案 A）** —— OIDC 客户端/授权/同意改用 Spring AS 标准表持久化（`oauth2_registered_client` / `oauth2_authorization` / `oauth2_authorization_consent`，迁移 `V1_1__oauth2_oidc_role.sql`）；`nitrowater-web` 客户端启动时幂等播种。
- **Phase 2.6 —— `nitrowater-web` OIDC 客户端 + SSO 门户壳** —— 接入 Vue 3 + `vue-router` + `oidc-client-ts`（授权码 + PKCE、silent renew、登出）；门户首页改用 **Element Plus** 重排，实时展示令牌声明（`uid` / `roles` / `did` / `iss`）。引入 ESLint（flat config：TS + Vue，`npm run lint`）。
- **SSO 托管登录/注册页** —— 由 AS 在 auth 域直接托管（仿 WaterFun `AuthBox`，无框架）：`GET /login`（原生表单 POST → Spring Security → 续跑 OIDC）与 `GET /register`（调 `/api/auth/**`）。身份 UI 归属身份中心，`nitrowater-web` 保持业务客户端。
- **issuer 统一** —— 单一 `jwt.issuer`（环境变量 `JWT_ISSUER`，开发默认 `http://localhost:8090`）由 Phase1 自研 AT 与 AS 共用，且等于 OIDC 发现文档的 `issuer`；须与前端 `VITE_OIDC_AUTHORITY` 一致。
- **AT/RT 标记废弃** —— Phase1 自研令牌服务（`AccessTokenService`、`AuthCoreService`）标 `@Deprecated`（由 OIDC 取代），仅保留给 `/api/auth/**` 兼容层。
- **Phase 3 —— BFF（Token Handler）** —— 新增 `nitrowater-bff` 模块（Servlet/WebMVC）：confidential OIDC 客户端（`spring-boot-starter-security-oauth2-client`）、Redis 会话（`spring-session-data-redis`）、CSRF、`/bff/me` 会话自省，以及 `/api/**` 代理——附加**服务端持有的** Bearer access token（过期用 `refresh_token` 自动续期）。浏览器**零 token**。由于客户端是 confidential，授权服务器会签发 refresh token（它刻意不给 public client 签发），从而取代 iframe `prompt=none` 路径。旧 public 客户端机制（`X-Frame-Options`/`frame-ancestors`、`/auth/silent-callback`）已移除；`nitrowater-web` 弃用 `oidc-client-ts`，改为同源调用 BFF（`/bff/me`、`/oauth2/authorization/nitrowater`、`/logout`）。

### 🚧 进行中 / 未完成

**Phase 2 —— 完成真正的 OIDC 单点登录**（下一里程碑）：

- [x] **OIDC 客户端** —— 已被取代：SPA 不再持有 token；由 **`nitrowater-bff`** confidential 客户端（Phase 3）在服务端完成登录/续期/登出。
- [x] **端到端取证** —— 授权码流已验证（`deploy/bin/smoke-oidc.ps1`）：`sub/uid/preferred_username/roles/did` + confidential BFF 客户端已下发 `refresh_token`。
- [ ] **WaterFun 网关切换** —— 验签公钥改 JWKS；置顶新增 `/api/auth/** → SSO` 路由；校对白名单路径。

**其它模块（尚未开始）：**

- [ ] **`nitrowater-server`** —— 业务 API + Resource Server（`spring-boot-starter-oauth2-resource-server`，JWKS 验签）。
- [ ] **`nitrowater-web`** —— 复合门户：导航 + 工具箱（`wtools` 迁移，7 个工具）+ 演练场 + 文档频道（`velochatx-docs` 挂 `/docs/**`）。
- [ ] **`nitrowater-judge`** —— 判题服务：提交 → 编译 → 沙箱执行 → 判题；沙箱三件套（超时强杀 · 资源限额 · 隔离）是开放提交入口前的**安全前置**。

**待办积压：**

- [ ] 第三方 OAuth2 登录（QQ / 微信 / GitHub），走 `sso_identity`。
- [ ] 冒烟脚本幂等性（清理其注册的测试账号）。
- [ ] 上游决策点 **D1–D9**（库拆分、加密基准、封禁语义等）—— 待 WaterFun 评审，目前阻塞。

## 路线图

| 阶段 | 内容 | 状态 |
|---|---|---|
| **M0** 工程基线 | 初始化修复、`gradlew build` 全绿、模块职责梳理 | ✅ |
| **M3** SSO Phase 1 | 认证 API + SSO 库 + 端到端冒烟（契约不变） | ✅ |
| **M4** SSO Phase 2 | 真 OIDC SSO：令牌定制、`oidc_client` 表、前端接入、WaterFun 网关联 JWKS | 🚧 进行中 |
| **M1** wtools 合并 | 迁入 `nitrowater-web`，路由/构建/导航整合 | ⏳ |
| **M2** 演练场 MVP | `nitrowater-judge`：提交 API + 子进程沙箱 + Python/Java 判题 + 限流 | ⏳ |
| **M5** 收尾 | 题库导入、提交历史、接入 SOP 文档、安全复盘 | ⏳ |

## 架构

```
nitrowater（Gradle monorepo）
├── nitrowater-core        # 共享库（实体/仓储/认证服务）—— 不部署
├── nitrowater-account     # SSO 认证中心（授权服务器 + 认证 API + 托管登录页）
├── nitrowater-bff         # BFF / Token Handler（confidential OIDC 客户端 + Redis 会话 + /api 代理）
├── nitrowater-server      # 业务 API（工具箱后端、题目/提交、静态托管）+ Resource Server
├── nitrowater-judge       # 演练场判题服务（编译 → 沙箱 → 判题）   [规划中]
└── nitrowater-web         # 复合前端：门户 + 工具箱 + 演练场 + 文档（同源 BFF 客户端）
```

运行时（生产）：网关为信任边界；`/api/auth/**` 路由到 SSO 服务，`/api/**` 到业务服务，`/api/judge/**` 到判题服务。本地开发无网关时，`nitrowater-server` 直接暴露 JWT 验签（与网关一致）。

## 技术栈

- **后端**：Java 25、Spring Boot 4.1.1、Spring Security 7 + Spring Authorization Server 7（OIDC）、JPA + MySQL 8、Redis、Flyway
- **前端**：Vue 3 + TypeScript + Vite、`vue-router`；ESLint（flat config）
- **文档**：VitePress（`velochatx-docs`）

## 快速开始

前置：**JDK 25**、**MySQL 8**、**Redis**（Gradle wrapper 已内置）。

```powershell
# 1) JWT 密钥 -> deploy/keys/{private,public}.key（复制 WaterFun 的，或用脚本生成）
.\deploy\bin\gen-keys.ps1

# 2) 启动 Redis（假定 MySQL 已在运行）
.\deploy\bin\start-redis.bat

# 3) 环境自检（端口 / 密钥 / KEK）
.\deploy\bin\check-env.ps1

# 4) 启动 SSO 服务（http://localhost:8090）
.\deploy\bin\start-account.bat
# 或：gradlew :nitrowater-account:bootRun

# 5) 启动 BFF（http://localhost:8080）与资源服务（http://localhost:8081）
.\deploy\bin\start-bff.bat        # 需先启动 SSO（启动时要拉 OIDC 发现文档）
# 或：gradlew :nitrowater-bff:bootRun
# 或：gradlew :nitrowater-server:bootRun

# 6) 端到端冒烟（交互式）
.\deploy\bin\smoke-auth.ps1       # /api/auth/** 流程
.\deploy\bin\smoke-oidc.ps1       # OIDC 授权码 + refresh_token（confidential BFF 客户端）

# 7) OIDC 发现端点自检
#    curl http://localhost:8090/.well-known/openid-configuration

# 8) 前端（SSO 门户）-> http://localhost:5173
cd nitrowater-web
npm install
npm run dev          # SPA 将 /api、/bff、/oauth2、/login、/logout 代理到 BFF（:8080）
npm run lint         # ESLint（flat config，TypeScript + Vue）
npm run build        # vue-tsc + vite build
```

主配置：`nitrowater-account/src/main/resources/application.yml`（数据源、Redis、JWT 密钥路径、设备盐值、CORS 白名单、Cookie SameSite/Secure）。
字段加密需要环境变量 `WATERFUN_KEK`（32 字节 Base64 的 AES KEK）；`bootRun` 会注入开发默认值。

## 仓库与文档

| 平台 | 地址 |
|---|---|
| GitHub | https://github.com/danburen/nitrowater |
| Gitee | https://gitee.com/blackwallet/nitrowarer |

- 需求/设计：[`docs/PRD.md`](../PRD.md)
- 英文 README：[`../../README.md`](../../README.md)

## 许可证

见 [`LICENSE`](../../LICENSE)。
