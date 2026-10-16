# AGENTS.md — NitroWater 项目约定（Agent 预指令）

> 本文件是**稳定的工程约定**（"规矩"），供所有 AI 会话/工具自动加载。
> 易变的**状态/进度**写在 `.ai/context/current.md`（"现状"）；两者分工不同，勿混。

## 项目速览

- Gradle 多模块：`nitrowater-lib` / `nitrowater-core` / `nitrowater-account`(SSO,OIDC,:8090) / `nitrowater-bff`(:8080) / `nitrowater-server`(业务,:8081) / `nitrowater-resource`(资源,:8093) + `nitrowater-web`(Vue3)。
- 命令（仓库根）：`.\gradlew.bat build`；单模块 `.\gradlew.bat :<module>:bootRun`。
- 前端（`nitrowater-web/`）：`npm run lint` / `npm run build`（dev 固定 5173）。
- 运行前置：host MySQL(3306, root/123456)、Redis(6379)、SeaweedFS(8333)；account 需 `NITROWATER_KEK`（bootRun 注入 dev 默认）。

## 数据库 / Schema 约定（强制）

1. **时间列统一 `TIMESTAMP(3)`**（毫秒精度）；`DATETIME(3)` 仅在 `TIMESTAMP` 不适用（如超出 1970–2038）时作为**不得已**选择，且须先与用户确认。**唯一例外**：生日/纯日期语义字段用 `DATE`。
   - 配合 datasource `connectionTimeZone=UTC&preserveInstants=true`，JPA 实体用 `java.time.Instant`。
   - ⚠️ MySQL `TIMESTAMP` 范围 1970–2038；若某列需要 2038 之后（罕见），先与用户确认再破例。
   - **例外（vendor schema）**：第三方库自带的 DDL **原样保留**，不强行套本项目约定——如 Spring Authorization Server 官方表（`V1_1` 的 `oauth2_registered_client`/`oauth2_authorization`/`oauth2_authorization_consent`，含裸 `timestamp` 列）。改动会偏离上游 schema，且被 `JdbcRegisteredClientRepository`/`JdbcOAuth2AuthorizationService` 直接读写。
2. **枚举一律"整数编码 + 映射"**：DB 列 `TINYINT UNSIGNED`，JPA 用 `@Converter(autoApply = true)`。
   - 枚举自带 `short value` + `static fromValue(int)`；实体字段标 `@Column(..., columnDefinition="tinyint UNSIGNED")`。
   - **禁止** `@Enumerated(EnumType.STRING/ORDINAL)`、`@Convert(disableConversion=true)`。
   - 列 `COMMENT` 写清每个编码（如 `'0=ACTIVE,1=SUSPENDED'`）；编码**只追加、不改**。
   - 落到"确无映射方案"才另议（万不得已）。
3. 布尔列 `TINYINT(1)`（0/1）。
4. 命名：库/表 `snake_case`、**表名单数**；库内 FK `fk_*`、唯一键 `uk_*`、普通索引 `idx_*`；`ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`。
5. **跨库/跨服务引用用 `uuid`（CHAR(36)），不用自增 `id`**（`id` 仅服务内部主键用）。
   - 跨库**不建物理 FK**，只存逻辑键（如 `uid`、`*_resource_uuid`）。
   - `resource.uuid` 为对外唯一键（`UNIQUE`），可作引用目标。
6. Flyway：**已应用的迁移冻结**（改名/改内容 → checksum 校验失败导致启动失败）；新变更走新版本（`V1_2__...`）。
   - dev 阶段允许"**改基线 + 重置库**"：drop schema 后由 Flyway 重放（无生产数据，随意）。
   - 每个服务管自己的库（`nitrowater_account` / `nitrowater_biz` / `nitrowater_resource`），`ddl-auto=none`。

## 其它工程约定

- **无 subagent**：由主进程直接执行，不派子代理/后台任务。
- **BFF 安全面（强制）**：`/api/**` 是**唯一数据命名空间**（需登录；`/api/public/**` 有意匿名）。其余路径是公开 SPA（静态资源 + history 深链接）+ OIDC 管线，故 `anyRequest().permitAll()`。`/actuator/**`、`/internal/**` 为**保留敏感前缀，默认 `denyAll`**（兜底，防新增依赖静默暴露）。**新增数据端点必须挂在 `/api/**` 下**；敏感内部接口不得落在该前缀之外。
- **GitHub 拉取优先经代理**：`https://ghproxy.net/`（首选）或 `https://gh-proxy.com/` 前缀（直连 raw/releases 常被重置/拒绝）；例 `https://ghproxy.net/https://github.com/<org>/<repo>/...`。Docker/包管理器同理。
- 后端响应式冻结：一律 **Servlet/WebMVC**（勿 WebFlux）。
- 注释**英文** + 方法 **Javadoc**。
- Redis 命名空间 `nitrowater:*`（缓存如 `nitrowater:cache:*`）。
- Windows 脚本编码：`.bat` = ASCII + CRLF；`.ps1` = **UTF-8 with BOM**（脚本非日志，保持 BOM）。
- 日志编码：`.ai/context/*.md` 与 `events.log` 一律 **UTF-8 无 BOM**；写入前先探测，遇历史 GBK 先按原编码读出再以 UTF-8 无 BOM 重写（内容不变），随后一律 UTF-8。
- 前端 **Tailwind-first**，Element Plus 仅按需用于复杂组件。
- issuer 单一来源 `jwt.issuer`（**必须是 URL**）；RS 校验 `aud` 与本服务的 audiences 对齐。
- 密钥/凭据不入库不入仓；COS 等云凭据用环境变量，本地缺省即禁用该后端。
