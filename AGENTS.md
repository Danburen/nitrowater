# AGENTS.md — NitroWater 项目约定（Agent 预指令）

> 本文件是**稳定的工程约定**（"规矩"），供所有 AI 会话/工具自动加载。
> 易变的**状态/进度**写在 `.ai/context/current.md`（"现状"）；两者分工不同，勿混。

## 项目速览

- Gradle 多模块：`nitrowater-lib` / `nitrowater-core` / `nitrowater-core-web` / `nitrowater-account`(SSO,OIDC,:8090) / `nitrowater-bff`(:8080) / `nitrowater-server`(业务,:8081) / `nitrowater-resource`(资源,:8093) + `nitrowater-web`(Vue3)。
- 命令（仓库根）：`.\gradlew.bat build`；单模块 `.\gradlew.bat :<module>:bootRun`。
- 前端（`nitrowater-web/`）：`npm run lint` / `npm run build`（dev 固定 5173）。
- account 前端（`nitrowater-account/`）：admin-ui/auth-ui 为 **pnpm workspace**（Node 24 + pnpm 11，共享包在 `packages/*`）；模块根 `pnpm install` 一次即可，Gradle 构建会自动调用 pnpm（`-PskipAdminUi`/`-PskipAuthUi` 可跳过，Docker 内由 node stage 构建）。
- 运行前置：host MySQL(3306, root/123456)、Redis(6379)、SeaweedFS(8333)；account 需 `NITROWATER_KEK`（bootRun 注入 dev 默认）。

## 数据库 / Schema 约定（强制）

1. **时间列统一 `TIMESTAMP(3)`**（毫秒精度）；`DATETIME(3)` 仅在 `TIMESTAMP` 不适用（如超出 1970–2038）时作为**不得已**选择，且须先与用户确认。**唯一例外**：生日/纯日期语义字段用 `DATE`。
   - 配合 datasource `connectionTimeZone=UTC&preserveInstants=true`，JPA 实体用 `java.time.Instant`。
   - ⚠️ MySQL `TIMESTAMP` 范围 1970–2038；若某列需要 2038 之后（罕见），先与用户确认再破例。
   - **例外（vendor schema）**：第三方库自带的 DDL **原样保留**，不强行套本项目约定——如 Spring Authorization Server 官方表（`V1__sso_baseline.sql` 中的 `oauth2_registered_client`/`oauth2_authorization`/`oauth2_authorization_consent`，含裸 `timestamp` 列）。改动会偏离上游 schema，且被 `JdbcRegisteredClientRepository`/`JdbcOAuth2AuthorizationService` 直接读写。
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
6. Flyway：**已应用的迁移冻结**（改名/改内容 → checksum 校验失败导致启动失败）；新变更走新版本（account 当前基线为 `V1__sso_baseline.sql`，下一条为 `V1_1__...`）。
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
- **前端 UI 选型（推荐，非强制）**
  - 用户可接触的界面（门户、工具箱、聊天、`nitrowater-account/auth-ui`）**优先 Tailwind + 项目 token**（`--color-*`、`shared/ui`）；Element Plus 作为备选。
  - **管理端**（`nitrowater-web/src/features/admin/**`、`nitrowater-account/admin-ui/**`）推荐统一用 **Element Plus**。
  - **豁免**：Tailwind 实现明显更复杂或不合适时（虚拟化表格、上传/进度、级联选择、富文本等），直接用重型库/EP 即可，不必勉强手写；提交说明里写一句原因更好。
  - **存量不强制迁移**：工具页与 auth-ui 里已有的 EP 用法保留；改动这些文件时，新增/重写的部分按上面的偏好选择，避免一边倒。
- issuer 单一来源 `jwt.issuer`（**必须是 URL**）；RS 校验 `aud` 与本服务的 audiences 对齐。
- 密钥/凭据不入库不入仓；COS 等云凭据用环境变量，本地缺省即禁用该后端。

- **后端 JSON 契约（强制）**：所有业务服务的 JSON 响应统一 `ApiResponse<T>`（`cn.nitrowater.lib.api`，线形状 `{success, code, message, data}`）；分页一律 `ApiResponse<Page<T>>`（Spring Data `Page`）。**禁止**再引入自定义分页 DTO（`PageResp` / `PageResult` 已删除）。
  - 包裹由 `cn.nitrowater.core.web.ApiResponseAdvice` 统一完成（只拦 Jackson 转换器）。服务用 `@Import(ApiResponseAdvice.class)` 启用：server / resource / account / bff 已启用；BFF 的 `/api/**` 透传端点返回 `ResponseEntity<Resource>`（不走 Jackson 转换器），因此上游已包裹的响应不会被二次包裹。
  - **组件扫描边界**：
itrowater-core-web 的 advice/handler 一律用 @Import 显式启用；若某服务用 `scanBasePackages = "cn.nitrowater"` 全量扫描，必须把 `cn.nitrowater.core.web` 排除（`@ComponentScan(excludeFilters=...)`），否则会与自己的 `GlobalExceptionHandler` 同名 bean 冲突，启动直接抛 `ConflictingBeanDefinitionException`（account 已按此配置，并有 `ComponentScanBoundaryTest` 守护）。
  - 分页必须是 Spring Data `Page<T>`，且服务主类**必须**标注 `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`（server / resource / account 已标注）。漏标会泄漏 `PageImpl` 平铺形状（`pageable`/`sort`/`first`/`last`），前端读 `data.page.totalElements` 会拿到 `undefined`。线形状固定为 `data.content` + `data.page{size,number,totalElements,totalPages}`，**请求侧 page 为 0 基**。
  - 异常统一走 `cn.nitrowater.core.web.GlobalExceptionHandler`（兜底 500 / 参数校验 / 体解析失败 / 404 / `ErrorResponseException` 保留原状态码）；领域处理器用 `ApiResponse.reject(code, message, data)`；**禁止**裸 `Map` 错误体。
  - MVC 之外（`HandlerInterceptor`、`Filter`、Security 过滤链）也必须同形状：`ApiResponseWriter.error(...)`；401/403 用 `ApiResponseAuthenticationEntryPoint` / `ApiResponseAccessDeniedHandler`。
  - **例外清单**（白名单式；新增例外必须在提交说明写明理由）。这些都是**协议层/传输层**，结构上无法套 envelope：SSE（`text/event-stream` 帧）、二进制下载与 `StreamingResponseBody`、HTML 视图（`@Controller` 视图名）、OIDC 协议端点（授权/令牌/JWKS，规范固定，由 Spring Authorization Server 过滤器直接写）、`/actuator/**`（Spring Boot 自有端点）。`/bff/**` 已统一包裹（不再例外）。
  - 契约测试：`.ai/testkit/smoke-admin-resource.ps1`、`smoke-bff-admin-resource.ps1` 断言 `data.content` + `data.page.totalElements`；改响应形状必须同步这两个脚本。
- **响应码（前后端唯一词汇）**：后端 `BaseResponseCode` 与领域异常的 `code()` 只承诺**字符串**；前端镜像在 `nitrowater-web/src/api/codes.ts` 的 `ResponseCode`（`as const` 常量对象 + 联合类型，**不要用 TS enum**）。码只追加、不复用；HTTP 状态码只服务基础设施（重试/跳登录），业务分支一律看 `success` / `code`。
- **前端 API 层（强制）**：所有后端调用与接口类型统一放 `nitrowater-web/src/api/`；组件**不得**自行 `fetch` / `response.json()`，也不得声明接口响应类型。
  - 扁平域：`api/<domain>.ts`（`account` / `admin` / `conversations` / `slash` / `sandbox`）。
  - 大域（> 约 250 行或 2 条责任线）：`api/<domain>/{index,types,<a>,<b>}.ts`（如 `ai/{index,types,chat,admin}`、`plugins/{index,types,catalog,admin}`）；`index.ts` 只做 barrel，`types.ts` 只放声明，运行时代码按 `user/admin`、`catalog/admin` 分文件，依赖单向（admin → 共享运行时，不反向）。
  - 通用件：`api/types.ts`（`ApiResponse<T>` / `Page<T>` / `PageMeta`）、`api/codes.ts`（`ResponseCode`）、`api/envelope.ts`（`readJson` / `readVoid` / `unwrap` / `ApiError`）、`api/http.ts`（`getJson` / `readErrorMessage` / `messageFor` / `isQuotaRejection`）。域内不要各写一份。
  - 解包宽容：有 `success` 字段才取 `data`；`/bff/**` 未包裹的响应原样返回。
- **lib 保持纯净**：`nitrowater-lib` 只放无框架依赖的工具与纯数据（`ApiResponse` 等）；Spring/Servlet/Security 相关（响应 advice、全局异常、JSON 写出、401/403 处理器）放 `nitrowater-core-web` 的 `cn.nitrowater.core.web`（只依赖 lib + spring-webmvc/security-web，**不拉 JPA**，因此 BFF 可以直接复用）。