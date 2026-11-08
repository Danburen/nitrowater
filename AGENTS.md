# AGENTS.md — NitroWater 项目约定（Agent 预指令）

> 本文件是**稳定的工程约定**（"规矩"），供所有 AI 会话/工具自动加载。
> 易变的**状态/进度**写在 `.ai/context/current.md`（"现状"）；两者分工不同，勿混。

## 项目速览

- Gradle 多模块：`nitrowater-lib` / `nitrowater-core` / `nitrowater-account`(SSO,OIDC,:8090) / `nitrowater-bff`(:8080) / `nitrowater-server`(业务,:8081) / `nitrowater-resource`(资源,:8093) + `nitrowater-web`(Vue3)。
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

## 查询 / JPA 约定（强制）

列表查询一律 **JPA**（`Repository` + `Specification` + `Page`），**禁止**手写 JDBC 拼 SQL 做列表；分页在数据库侧完成（`Pageable`），**禁止**查全量再内存分页。

1. **三段式**：`Entity` → `XxxRepository extends JpaRepository<Xxx, ID>, JpaSpecificationExecutor<Xxx>` → `XxxSpec`（`public final` + 私有构造 + 纯静态工厂）。
2. **`XxxSpec.of(...)` 是唯一入口**：service 只调 `XxxSpec.of(...)` 拿 `Specification`；service 里**不得**出现 `CriteriaBuilder` / `Predicate` / `cb.and(...)`。同一实体多视图（前台 / 后台 / 子资源）用**同类多个命名工厂**（`of` / `adminOf` / `pluginArtifactsOf`），不要按视图复制类。
3. **公共原语**：`core.infrastructure.persistence.specification.SpecificationBuilder`（`eq` / `notEqual` / `likeIgnoreCase` / `likeIgnoreCaseAny` / `in` / `greaterThan*` / `lessThan*` / `where` / `when` / `build`）。空值自动跳过、关键字大小写不敏感且转义 `%` `_` `\`、支持点号路径（`id.uid`）；**只有 spec 内部**调它。属性名 / 排序键必须是代码里的**字面量**，**不得**来自请求参数（这是唯一可能把用户输入带进 SQL 结构的口子，堵在这里就不需要额外转义层）。
4. **参数形状**：`of(...)` 保持 ≤ 4~5 个；再多就传查询 record（如 `AdminResourceQuery`），不要堆一串同类型 `String`（顺序错一次就是线上 bug）。
5. **spec 的纯度**：只依赖实体 / 枚举 / `SpecificationBuilder`；**不得**注入 repository / service、读配置、发事件。跨表条件写在 `where(...)` 的子查询里。
6. **业务规则不进 spec**：默认值（`status == null → ACTIVE`）、参数归一化（trim / 大小写 / 去 `ROLE_` 前缀）、权限判定留在 service；spec 只做"条件 → where"的翻译。
7. **分页正确性**：可见性 / 软删除 / 状态这类**行级过滤必须进 WHERE**；**禁止**拿 `Page` 回 service 再 `filter`（会短页、`totalElements` 失真）。
8. **排序归调用方**：`Sort` 由 service 组进 `PageRequest`，不写进 spec；排序必须带唯一键兜底（`id` / `uid`），否则翻页会重复或漏行。
9. **性能红线**：`likeIgnoreCase` 生成的 `%x%` 用不到索引，只用于后台低频搜索；`Page` 的 count 查询复用同一 spec，故 spec 内**不要**加 `fetch join`（需要抓取用 `@EntityGraph`），也不要在 WHERE 里对列做函数/算术（如 `SUM(a,b) >= x`）；报表 / 聚合走专门 read model，别把 spec 写成万能 ORM。
10. **实体归属**：跨服务共享的实体与仓储放 `nitrowater-core`（`core.domain.*` + `core.infrastructure.persistence.*`），服务用 `@EntityScan` + `@EnableJpaRepositories` 显式声明扫描范围；**只属于单个服务**的实体留在该模块，spec 与实体同包（`server.infra.specification` / `resource.application.specification`）。**不要**为了"统一"把各库实体全堆进 core——core 会变成所有 schema 的耦合点，`compileOnly` 数据栈的边界也失去意义。
11. **可测性**：spec 不碰容器，用 `@DataJpaTest` 可直接断言生成的 where；改动 spec 时要能回答"哪个索引能消化这个条件"。

## 其它工程约定

- **无 subagent**：由主进程直接执行，不派子代理/后台任务。
- **BFF 安全面（强制）**：`/api/**` 是**唯一数据命名空间**（需登录；`/api/public/**` 有意匿名）。其余路径是公开 SPA（静态资源 + history 深链接）+ OIDC 管线，故 `anyRequest().permitAll()`。`/actuator/**`、`/internal/**` 为**保留敏感前缀，默认 `denyAll`**（兜底，防新增依赖静默暴露）。**新增数据端点必须挂在 `/api/**` 下**；敏感内部接口不得落在该前缀之外。
- **BFF 令牌刷新（强制）**：取 access token 一律走 `BffTokenService`（按会话串行 + 2 分钟内共享刷新结果），**不要**直接调 `OAuth2AuthorizedClientManager`；拿不到令牌时**失效会话**，由控制器用 `ReauthResponse` 回 **401 `session_expired`**（`X-Auth-Reason` + 过期 cookie），**禁止**让它落到 500。`MultiAccountAuthorizedClientRepository` 必须「先改副本再 `setAttribute`」（会话 `flush-mode=immediate`，先写后改会丢写）。
- **GitHub 拉取优先经代理**：`https://ghproxy.net/`（首选）或 `https://gh-proxy.com/` 前缀（直连 raw/releases 常被重置/拒绝）；例 `https://ghproxy.net/https://github.com/<org>/<repo>/...`。Docker/包管理器同理。
- **AI 工具返回（强制）**：每个 `@Tool` 一律返回 `ToolResponse`（`service.ai.model.tool`，线形状 `{success, code, message, retryable?, data}`），码表在 `ToolCode`（`tool.*`，只追加不改）。`success`/`retryable` 由码派生、`data` 装动态载荷（失败也可带，如 `available`/`remaining`）；**禁止**再返回裸 `error` 字符串或把失败塞进成功体。`success=true` 只表示"调用被正常处理"——`tool.compile_error` 仍是成功调用（`stderr` 在 `data` 里）。工具必须用 `ToolResponse.record(trace, ...)` 落轨迹，使卡片状态与 payload 同源；前端只读 `ok` 布尔，不再比较码字符串。
- **AI agent 流（强制）**：一条 SSE 同时承载过程与答案，但**分事件**：`step {phase, tool, callId, argument, status, resultCount, durationMs}`（`thinking` / `tool_start` / `tool_end`）与 `delta {text}`；`sources/tools/skills` 在结束时一次性发出。过程事件必须由 **`ToolCallback` 装饰器**（`StepTrackingToolCallback`）在**每次调用**边界发出（Spring AI 的批处理不提供调用级事件，不要用全局 observation 钩子跨轮次传递）；答案文本里**不写过程旁白**（`PromptBuilder` 第 7 条），前端把 `step` 渲染成独立时间线（`AgentLiveSteps`），气泡只从 `delta` 开始、光标只在有内容时显示。
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
  - **设置页版式（强制）**：名称在左、控件在右，一行一个条目；按分类分组，**每组一个 card**（组标题 + 组内细线分隔的行，页头/页尾不留空白块）；**页面本身不再套外层 card**，避免 card 套 card。删除 / 清空等危险操作**不放在常规操作区**，统一放页面底部的 danger zone（GitHub 风格：红框 + 一行小标题 `Danger zone`，**每个动作一行 `label → 控件`**，确认弹窗携带细节；**不写描述、不做分组**）。可复用件：`features/admin/AdminSettingsGroup.vue`（组 card）、`AdminSettingsRow.vue`（左名右控件，用于组内）、`AdminDangerZone.vue`（危险区：深红标题条 + 浅红底）、`AdminDangerRow.vue`（危险区一行：左名右控件）。
  - **危险操作的落点（强制）**：**组 card 内不放重置 / 删除 / 重建类操作**；danger zone 只承载**与行无关的独立**破坏性动作（恢复默认设置、解除全站熔断、重建索引等），与各组 card 平级独立。**作用于单条对象的重置 / 删除放该行行内**（`link` + `type="danger"` + `ElMessageBox` 确认），**批量放列表头**（`plain` + `type="danger"`，由多选驱动）。
  - **查询表单（强制）**：列表页筛选统一用 `features/admin/components/AdminFilterCard.vue`（独立 card + 原生 `el-form :inline`）：卡片壳、行列间距、窄屏规则都写在该组件的 `<style scoped>` 里，页面只声明 `el-form-item`。label 在左、控件在右、「重置 / 查询」靠右；控件宽度写在页面上（`w-[220px]!` 这类 utility，**必须带 `!`**——EP 的 CSS 未分层，普通 utility 会输给它的默认宽度）；≤991px 自动一项一行。**不要**再新增全局 `.admin-filter-form` 之类，也不要 `el-row/el-col` 栅格。
  - **样式归属（强制）**：新增 / 重写的样式**默认先写组件 `<style scoped>`**，确认 ≥2 个不同文件复用后才上移 `style.css`；**不要为了通用而通用**。抽公共类的唯一理由是"已经有 ≥2 个不同文件写着同一组样式"，不是"看起来可以抽"。按顺序判定：
    - 单页 / 单组件的一次性布局 → 直接写模板上的 **Tailwind utility**，不建类。
    - 必须用 CSS 才能表达的（伪元素、滚动条、keyframes、状态变体 `.is-on`、`:deep()` 覆盖 Element Plus 内部、slot 内容上的第三方控件宽度）→ 写在该组件 `<style scoped>` 里，**一律原生 CSS 声明，不写 `@apply`**（SFC 的 scoped 块是独立 CSS 根，`@apply` 还得配 `@reference`，多一层依赖没换来好处；能表达成 utility 的部分本来就应该留在模板上）。同一元素上**不要**堆互相冲突的 utility（`border-line` + `border-accent/40` 谁赢取决于产物顺序），用 `.is-on` 这类状态类表达。
    - 至少 2 个不同文件、语义同一的真公共原语 → 才放 `style.css` 的 `@layer components`，且**优先原生 CSS 声明**；`@apply` 只留给成组复用的组合（`.btn`、`.input` 族）。
  - **全局样式（强制）**：`style.css` 只承载极少量跨页面原语（按钮、panel、tag、`admin-*` 骨架、表单控件基类）。判断一律以**引用数**为准：扫全量 `.vue` 的 `class` / `:class`，**单文件引用的类必须下线到场内**（模板 utility 或 `scoped`），不得留在全局。**禁止**为单个页面新增全局类；**禁止**把状态变体（`.foo.is-on`）或 `:deep()` 第三方覆盖写进全局。
- issuer 单一来源 `jwt.issuer`（**必须是 URL**）；RS 校验 `aud` 与本服务的 audiences 对齐。
- 密钥/凭据不入库不入仓；COS 等云凭据用环境变量，本地缺省即禁用该后端。

- **后端 JSON 契约（强制）**：所有业务服务的 JSON 响应统一 `ApiResponse<T>`（`cn.nitrowater.lib.api`，线形状 `{success, code, message, data}`）；分页一律 `ApiResponse<Page<T>>`（Spring Data `Page`）。**禁止**再引入自定义分页 DTO（`PageResp` / `PageResult` 已删除）。
  - 包裹由 `cn.nitrowater.core.web.ApiResponseAdvice` 统一完成（只拦 Jackson 转换器）。服务用 `@Import(ApiResponseAdvice.class)` 启用：server / resource / account / bff 已启用；BFF 的 `/api/**` 透传端点返回 `ResponseEntity<Resource>`（不走 Jackson 转换器），因此上游已包裹的响应不会被二次包裹。
  - **组件扫描边界**：
`nitrowater-core` 中 `cn.nitrowater.core.web` 的 advice/handler 一律用 @Import 显式启用；若某服务用 `scanBasePackages = "cn.nitrowater"` 全量扫描，必须把 `cn.nitrowater.core.web` 排除（`@ComponentScan(excludeFilters=...)`），否则会与自己的 `GlobalExceptionHandler` 同名 bean 冲突，启动直接抛 `ConflictingBeanDefinitionException`（account 已按此配置，并有 `ComponentScanBoundaryTest` 守护）。
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
- **lib 保持纯净**：`nitrowater-lib` 只放无框架依赖的工具与纯数据（`ApiResponse` 等）；Spring/Servlet/Security 相关（响应 advice、全局异常、JSON 写出、401/403 处理器）放 `nitrowater-core` 的 `cn.nitrowater.core.web`。
  - 该模块的数据栈（JPA / JDBC / Flyway / MySQL）是 **`compileOnly`**：core 只给实体挂注解、给仓储继承 `JpaRepository`，运行期由各持久化服务（account / server / resource）自己声明 starter。因此 BFF 依赖 core 也不会带进 `DataSource`，启动不受影响（`BffDependencyBoundaryTest` 守护；不要把数据栈改回 `implementation`）。