# Day 2 反思录 · huangcong

> 日期：2026-10-07（下午/晚上）｜ 项目：NitroWater
> 关键词：`/api/auth/**` 落地、SSO Phase 1 冒烟、OIDC Provider、RBAC、端到端

## 一、今天的反思区

### 1. 今天做了什么
- **account 落地 S1–S4**：account 依赖 core + starters、`scanBasePackages` / `@EntityScan`、迁 `@RateLimit`+`ConsoleCodeSender`、平移 18 个 DTO 与两个 AuthController、扁平 `application.yml`、`LocalAuthContextFilter`、迁 deploy 脚本（启动/自检/密钥/备份/冒烟）。
- **SSO 库**：Flyway 基线 `V1__sso_baseline.sql` 建成 6 表并重置库；预留 `sso_identity` 表给第三方登录（QQ/微信/GitHub）。
- **Phase 1 冒烟通过**：`captcha → send-code → register → /account → 401 → refresh → logout` 全链路绿。
- **Phase 2 OIDC**：恢复 Spring Security + Authorization Server，加 `AuthorizationServerConfig`（AS 链 Order1 + 默认链 Order2 + JWKS + 托管 `/login`）、`SsoAuthenticationProvider`（委托 `LoginService` 复用验证码/锁定/DEK）；令牌定制 `sub=uid / uid / preferred_username / name / roles / did`；OIDC 客户端/授权/同意改 **JDBC 持久化**（`V1_1` 迁移）。
- **RBAC**：引入 `role` / `user_role` 两表，`roles` 由表派生并改为 JPA 仓储实现。
- **OIDC 端到端**：`smoke-oidc.ps1` 最终全绿（`authorize → 表单登录 → code → /oauth2/token → 解 claim`）。

### 2. 收获与认识
- **版本够新、坑够多**：Spring Boot 4 / Spring Security 7 / Authorization Server 7 与旧经验差异大，遇到不确定就**反编译 / 查官方 DDL 核实**，别凭记忆。
- **迁移垫片与目标并存**：Phase1 自研双 token（AT/RT）是兼容层，OIDC 是新门面；新功能不要复用垫片。
- **端到端脚本价值高**：`smoke-oidc.ps1` 一次性把隐藏的序列化 / 映射问题打出来，比单点 curl 强。

### 3. 踩坑与教训（今天最值钱的一栏）
- **Boot 4 的 Flyway 自动配置在 `spring-boot-starter-flyway`**：只放 `flyway-core` 不触发迁移。
- **PowerShell 5.1 按 GBK 读 .ps1**：UTF-8 无 BOM 时，中文紧邻引号会吞引号 → 报"缺少终止符"。规范：`.ps1` 用 **UTF-8 with BOM**，`.bat` 用 **ASCII + CRLF**。
- **AS JDBC 默认类型化（Jackson）**：自定义 principal 需 `@JsonCreator/@JsonProperty` + 授权 JsonMapper `allowIfSubType`；**集合不能用 `List.of()` / `stream().toList()`**（会存成 `ImmutableCollections$*`，被 `PolymorphicTypeValidator` 拒）→ 统一归一化为 `ArrayList`。
- **`role` 是 MySQL 保留字** → 实体 `@Table(name = "\`role\`")`。
- 缺配置会启动失败：`clean-up.device.max-days` 必配、`WATERFUN_KEK` 必需。
- **已应用迁移要冻结**（Flyway checksum），新改动走子版本（`V1_2__…`）。

### 4. 下一步
- Phase 2.6：`nitrowater-web` 接入 `oidc-client-ts`（`/auth/callback`、silent renew、logout）。
- `nitrowater-server` 做 Resource Server（JWKS）；Phase 2.8 WaterFun 网关联 JWKS。

## 二、对话档案

### 对话 1 · Phase 1 要不要直接引入 Spring Security？
**我**：Phase 1 直接上 Spring Security 吗？
**AI**：不必。Phase 1 目标是"接口契约不变、前端与网关零改动"，用自研双 token（RS256 JWT + RT HttpOnly Cookie）+ 本地身份过滤器最省事；OAuth2/OIDC 不是 SSO 内核，放到 Phase 2 用 Spring Authorization Server 做，既快又稳。
**结论**：Phase 1 不引 Spring Security；Phase 2 用 AS 做 OIDC。

### 对话 2 · OIDC 换 token 报 500 怎么查？
**我**：`/oauth2/token` 报 500，DB 的 attributes 里能看到 `@class SsoUserPrincipal`，怎么修？
**AI**：两个根因——① 自定义 principal 存进 AS JDBC 存储时，默认 JsonMapper 的 `PolymorphicTypeValidator` 拒绝你的包，需用 `SecurityJacksonModules` + `allowIfSubType("cn.nitrowater.account.")` 构造 JsonMapper 注入 `JdbcOAuth2AuthorizationService`；② `roleCodes` 用了 `List.of()` 会存成 `ImmutableCollections$List12` 被拒，构造器归一化 `new ArrayList<>()`。
**结论**：落地自定义授权 JsonMapper + 集合归一化，回归测试通过，端到端全绿。

### 对话 3 · 两张 RBAC 表放哪、怎么用？
**我**：角色怎么建模？
**AI**：角色制最小集：`role`（角色词汇表）+ `user_role`（用户-角色映射，复合主键、级联删除）。`role`/`user_role` 为**角色权威源**，`user.userType` 退为账号类别；细粒度权限暂不建，出现消费方再加子版本迁移。实现上用 JPA 实体 + 仓储（而非硬编码 SQL）。
**结论**：采纳，`roles` claim 由 `user_role` 派生（空回落 `ROLE_USER`）。
