# NitroWater 项目需求设计文档（PRD）

> - **文档版本**：v1.4
> - **产出日期**：2026-10-07
> - **修订日期**：
>   - 2026-10-07 v1.1：同步实施进展——SSO 认证核心库迁入 core、构建全绿；执行顺序拍板「SSO 先行」；新增 N1–N3 决策点。
>   - 2026-10-07 v1.2：**纳入 VeloChatX/velochatx-docs 整合诉求**（复合展示站：导航 + 工具箱 + 演练场 + 文档）；确认 `nitrowater-web`（Vue3+Vite，2026-10-07 新建）为前端融合落点；补充 `nitrowater-account` 落地缺口清单与 SSO 下一步分步方案（§9.1）；新增 N4–N6 决策点。
>   - 2026-10-07 v1.3：**Phase 1 落地完成**（account 编译绿、IDEA 启动通过；SSO 库 `nitrowater` 建成 6 表）；新增 **§9.2 Phase 2（OIDC）融合方案**（token 映射、waterfun 无感登录对接、依赖清单、CORS 白名单）；新增 N7–N8 决策点。
>   - 2026-10-07 v1.4：**Phase 2.4/2.5 落地**——① 登录主体升级为 `SsoUserPrincipal`（承载 uid/登录名/昵称/userType/状态/设备），`SsoAuthenticationProvider` 不再用裸 `User`、角色由 userType 派生；`OAuth2TokenCustomizer` 保证 `sub=uid` 并输出 `preferred_username/name/roles/did`。② OIDC 客户端与授权/同意改为 **JDBC 持久化（方案 A：Spring AS 标准表）**，新增 `V1_1__oauth2_oidc_role.sql`，客户端启动幂等播种；数据源 URL 按官方建议补 `preserveInstants/connectionTimeZone`。新增 §9.2.8、§9.2.9 与 N9。
> - **文档性质**：实训第二周 —— 项目设计需求文档
> - **状态**：待评审
> - **关联文档**：
>   - `D:\Project\waterfun\docs\auth-sso-migration-plan.md`（WaterFun 认证抽离 & SSO 迁移方案，本文 SSO 章节的上游依据）
>   - `D:\Project\waterfun\docs\reference\auth-login-flow.md`（现有登录链路参考）
>   - `D:\Project\webs\wtools\README.md`（待合并工具集说明）
>   - `D:\Project\webs\velochatx-docs`（VeloChatX 文档站，VitePress，待融合；暂时独立）
>   - `D:\Project\nitrowater\.ai\context\current.md`（本项目状态快照）

---

## 目录

1. [项目背景与目标](#1-项目背景与目标)
2. [现状盘点](#2-现状盘点)
3. [方案可行性评估](#3-方案可行性评估)
4. [总体架构设计](#4-总体架构设计)
5. [功能需求](#5-功能需求)
6. [非功能需求](#6-非功能需求)
7. [接口设计](#7-接口设计)
8. [数据设计](#8-数据设计)
9. [里程碑与排期](#9-里程碑与排期)
10. [风险清单与应对](#10-风险清单与应对)
11. [验收标准](#11-验收标准)
12. [待决事项（评审拍板项）](#12-待决事项评审拍板项)

---

## 1. 项目背景与目标

### 1.1 背景

本项目（nitrowater）为新建工程，定位为**多项目共用的服务端平台**，承载四项诉求：

1. **工具集整合**：现有纯前端工具站 wtools（7 个工具）需要并入本项目，统一工程与入口；
2. **代码演练场独立化**：wtools 内的「编程演练场 / Code Runner」目前依赖浏览器内 WASM 运行时（Pyodide / CheerpJ），能力受限（无法真实判题、无法受控执行用户代码），计划**拆分为独立的后端驱动编译-执行-测试服务**；
3. **全站单点登录**：WaterFun 项目的登录注册模块计划抽离为独立 SSO 服务，nitrowater 作为其落地工程之一，实现 WaterFun 生态多项目的全站单点登录（SSO）；
4. **复合展示站整合**（v1.2 新增）：将 VeloChatX 项目的文档站 `velochatx-docs`（VitePress）整合进本项目的展示门户，与「导航 + 工具箱 + 编程演练场」共同构成**功能复合型展示网站**；该站同时承载**独立且完整的 SSO 单点登录**，作为对接并迁移 WaterFun 认证的运行载体。

### 1.2 目标与验收口径

| #   | 目标               | 验收标准                                                       |
| --- | ---------------- | ---------------------------------------------------------- |
| G1  | 工程初始化完成         | `gradlew build` 三模块全绿，可作为后续开发基线                            |
| G2  | wtools 平滑并入      | 工具全部功能在 nitrowater 内可用；构建/路由/资源统一管理                       |
| G3  | 演练场后端化          | 用户提交 Python/Java 代码 → 后端编译 → 沙箱执行 → 自动比对测试用例 → 返回判题结果 |
| G4  | 全站单点登录          | 在任一接入项目登录后，访问其它接入项目免登；接入新项目仅需注册 client + 本地资料表        |
| G5  | 认证能力从 WaterFun 迁出 | 登录/注册/凭证/验证码/token 签发全部由 SSO 服务承担（对接既有迁移方案 Phase 节奏）    |
| G6  | 复合展示站整合      | VeloChatX 文档站（`velochatx-docs`）产物以子路由形式并入 `nitrowater-web`，与工具箱/演练场并列展示；站点入口为统一导航门户，SSO 登录态贯穿全部频道 |

### 1.3 非目标（本期不做）

- 不改造 WaterFun 业务功能（帖子/评论/通知/审核等一律不动）；
- 不引入第三方 IdP（自建 Spring Authorization Server）；
- 首期不做第三方社交登录（预留 OIDC 扩展位）；
- 演练场首期只支持 Python / Java 两种语言（与现状一致，架构上可扩展）；
- 不做多租户/开放平台级别的 OAuth 授权（仅服务间 OIDC 接入）。

---

## 2. 现状盘点

### 2.1 nitrowater（当前工程，本 PRD 的落点）

- **工程形态**：Gradle 多模块，`rootProject.name = nitrowater`
  - Java 25（toolchain）、Gradle 9.7.1（wrapper）、Spring Boot **4.1.1**、`io.spring.dependency-management` 1.1.7
- **模块清单**：

| 模块                  | 现有依赖                                                                     | 当前状态（2026-10-07）   | 目标职责                                        |
| ------------------- | ------------------------------------------------------------------------ | --------------------------------------- | ------------------------------------------- |
| `nitrowater-account` | `webmvc`、`spring-security-crypto`、`spring-security-oauth2-jose`、`nitrowater-core`（Phase 1 未引 security web；Phase 2 恢复 AS） | **Phase 1 已落地**：控制器/DTO/配置/安全就绪，编译绿、IDEA 启动通过；SSO 库 6 表已建（§9.2.1） | **SSO 认证中心**（Phase 1 自研认证 API；Phase 2 OIDC Provider） |
| `nitrowater-core`    | `data-jpa`、`data-redis`、`jdbc`、MySQL 驱动、`spring-security`(core/oauth2-jose/web)、`spring-web`、`jakarta.servlet-api`、jackson2、`hutool-captcha`、`jbcrypt`、`jjwt`、validation、cache、flyway、Lombok（**纯库，bootJar 已禁用**） | **认证核心库已迁入**：waterfun `common-lib` 全量 51 文件 + 认证切片 100 文件 + exception 整树 60 文件 → `cn.nitrowater.core`；业务耦合已砍（`TODO(SSO)` 标记）；`compileJava` 0 错 | **共享核心库**：实体、仓储、认证/判题共用服务（不独立部署）             |
| `nitrowater-server`  | `webmvc`                                                                 | 空骨架    | **业务 API**：wtools 后端接口、题目/提交管理、静态资源托管          |
| `nitrowater-web`  ★新增 | Vue 3 + TypeScript + Vite 8（`package.json name=nitrowater-web`，2026-10-07 14:17 新建，仅 Vue 依赖） | 空脚手架（默认 HelloWorld）    | **复合前端**：导航门户 + 工具箱（wtools）+ 演练场 + **文档频道（velochatx-docs 产物）** + SSO 登录/回调页 |

- **已完成项**（本期实操：工程初始化 + SSO 核心库迁移）：
  1. 修复 Gradle wrapper 下载超时（预置 Gradle 9.7.1 发行版至 `GRADLE_USER_HOME` 缓存）；
  2. 删除两处悬空依赖 `implementation ':waterfun-core'`（骨架拷贝自 WaterFun 时遗留，模块不存在）；
  3. 根工程 `group` 由 `org.waterfun.lib.waterfun` 统一为 `cn.nitrowater`；
  4. `nitrowater-core` 补测试专用数据源配置（内存 H2 + `testRuntimeOnly h2`），使 `contextLoads` 不依赖外部 MySQL；
  5. **SSO 认证核心库收束迁移**（全拷收束方案）：waterfun `common-lib` 全量 + `service-core` 认证切片 + exception 整树（210+ Java 文件）迁入 `cn.nitrowater.core`，包名 `org.waterfun.* → cn.nitrowater.core`；业务实体、云 SDK 通道、业务 Controller 一律不拷；
  6. **core 纯库化**：删除 Spring Boot 启动类与上下文测试、禁用 `bootJar`（恢复 plain jar）；`Login/Register/Account` 三服务砍除 stats/audit/online/role 业务耦合，恢复点以 `TODO(SSO)` 标记；
  7. **验证结果：`:nitrowater-core:compileJava` 101 错误 → 0；`gradlew build` BUILD SUCCESSFUL（3 模块，20 task，全绿）**；
  8. **v1.2 评审补充**：完成与 WaterFun 认证三件套（登录/注册/验证码）逐接口对照，确认 `nitrowater-core` 已具备认证服务层（Login/Register/Account/AccessToken/Device/Captcha/Verification/SingleUseToken），并盘点出 account 落地的 **9 项缺口**（§9.1）；纳入 velochatx-docs 整合诉求。

### 2.2 wtools（待合并工具集）

- **位置**：`D:\Project\webs\wtools`（独立 git 仓库）
- **技术栈**：Vue 3（Composition API + `<script setup>`）+ TypeScript + Vite 8 + Element Plus + CodeMirror 6 + ECharts 6 + D3；**hash 路由**（`createWebHashHistory`），纯前端、无后端、无认证
- **功能清单**：

| 工具                   | 路由                   | 说明                                                                    | 运行位置   |
| ---------------------- | ---------------------- | ----------------------------------------------------------------------- | -------- |
| 首页                   | `/`                    | 工具导航                                                                  | 纯前端    |
| Java DTO → TypeScript | `/java-to-ts`          | 解析 Java DTO/VO 生成 TS 类型                                             | 纯前端    |
| ZIP 工具                | `/zip`                 | 压缩/解压（ZIP/RAR/7z/TAR/GZ/XZ/BZ2）                                    | 纯前端    |
| 树可视化                 | `/tree-visualizer`     | JSON → ECharts 树图，PNG 导出                                            | 纯前端    |
| 缩进树可视化               | `/list-tree`           | `├ └` 文本 → D3 可折叠列表，PNG 导出                                       | 纯前端    |
| **Code Runner**       | `/code-runner`         | Python/Java 统一运行（Pyodide / CheerpJ WASM，懒加载）                   | 浏览器 WASM |
| 视频转 GIF               | `/video-to-gif`        | ffmpeg.wasm 双通道调色                                                   | 浏览器 WASM |
| **编程演练场**            | `/playground`（bare 布局） | ACM 式刷题：内置题目（general / game-dev / game-server）、共享编辑器、stdin、运行 | 浏览器 WASM |

- **演练场现状细节**（拆分对象）：
  - 题目数据：`src/data/problems/index.ts`（静态），`Problem` 结构含 `id/title/difficulty/category/tags/description/templates/testCases`；`testCases` 为 `{ input, expectedOutput }`，**当前为占位、未自动判题**；
  - 运行时：`src/utils/runner/{runnerManager.ts, languages.ts}`，语言元数据 `python/java`；
  - 视图：`src/views/PlaygroundView.vue` → `components/Playground.vue`。

### 2.3 WaterFun（认证迁移来源）

- **形态**：Gradle 多模块后端 + pnpm 前端 monorepo；网关 `waterfun-gateway(8080)` 为唯一信任边界；
- **现有认证**：
  - 双 token：AT（RSA-JWT，claims `sub=uid/jti/did`）+ RT（纯 UUID，HttpOnly Cookie，Redis 存 family，7d 轮换 / 30d family）；
  - 网关验签（`RsaJwtDecoder`，RS256 + Redis JTI 吊销校验）→ 注入 `X-User-Uid/Did/Jti` 头，下游不验签；
  - 设备指纹 `deviceFp → HMAC(device.salt + uid) → did`，一设备一 token 家族；
  - 图形验证码、短信/邮箱验证码、登录失败锁定、re-auth 一次性 token、BCrypt 凭证；
  - 用户存储：MySQL `user`（凭证列）+ `user_data`（敏感字段加密）等 40 表，Redis 存 token/设备/验证码；
- **已有 SSO 迁移方案**：`docs/auth-sso-migration-plan.md`（评审稿，603 行），核心：
  - 抽离为 `waterfun-auth`（OIDC Provider，Spring Authorization Server），**nitrowater-account 的现有依赖与该定位完全吻合**；
  - 库拆分方案 A：凭证拆出 `sso_account`，资料留业务库，17 个 FK 零改动；
  - `sub` claim = uid（16 位数字，SSO 唯一签发）；事件驱动（`user.registered` 等）做跨库注册外关联；
   - 分阶段 Phase 0（契约）→ 1（服务独立，前端零改动）→ 2（OIDC 真 SSO）→ 3（收尾开放接入）。

### 2.4 velochatx-docs（文档站整合来源）与 nitrowater-web（前端落点）

- **位置**：`D:\Project\webs\velochatx-docs`（**独立 git 仓库**，暂不并入，本期只做整合规划）；
- **技术栈**：VitePress 1.6（`docs:dev / docs:build / docs:preview`），内容为 Vue 3 驱动的静态文档站；含 zh/en 双语（`docs/guide/**`、`docs/en/guide/**`、`docs/reference/**`）、自定义主题（`docs/.vitepress/theme/`）、`index.md` 首页；`docs/.vitepress/dist/` 已有构建产物；
- **定位**：VeloChatX 项目文档，整合后作为本复合展示站的「文档频道」；
- **前端落点 `nitrowater-web`**：2026-10-07 新建的 Vue 3 + TS + Vite 8 空脚手架（见 §2.1 模块表），为工具箱/演练场/文档/SSO 的统一承载工程；
- **整合约束（待拍板 N5）**：`velochatx-docs` **保持独立仓库**，以「构建产物挂子路径」为首选融合方式（详见 FR-4）。

---

## 3. 方案可行性评估

### 3.1 子方案一：wtools 合并到 nitrowater —— ✅ 可行（低风险）

| 维度    | 评估                                                                                                       |
| ----- | -------------------------------------------------------------------------------------------------------- |
| 技术冲突  | wtools 为纯前端（Vue3+Vite），与 Java 后端零耦合；可作为 `nitrowater-web/` 前端子工程并入 monorepo                      |
| 构建整合  | 保留 npm/pnpm 脚本，根 README/CI 统一串联；后端 `nitrowater-server` 以静态资源方式托管生产构建产物，开发期 Vite dev proxy |
| 工作量   | 主要是工程搬迁 + 路由基路径调整（hash 路由对子路径部署天然友好）、入口导航与登录态组件预留                                        |

**结论**：直接可行。唯一约定——保留 wtools 独立 `package.json`，以子目录形式进仓，不与后端 Gradle 构建耦合。

### 3.2 子方案二：代码演练场独立 + 后端驱动编译/测试 —— ✅ 可行（中高风险，本项目技术核心）

| 维度    | 评估                                                                                                                         |
| ----- | -------------------------------------------------------------------------------------------------------------------------- |
| 必要性   | 浏览器 WASM 方案无法：真实判题（服务端校验 testCases）、防止作弊（前端判题可篡改）、统一配额管理；后端化是 G3 的唯一路径                                     |
| 技术路线  | 独立判题服务（新 Gradle 模块 `nitrowater-judge` 或 `nitrowater-server` 内独立包，推荐**独立模块独立部署**）：接收提交 → 编译（javac / 语法检查）→ 沙箱执行 → stdout 与 `expectedOutput` 比对 → 返回结果 |
| 沙箱方案  | **MVP**：受限子进程（工作目录隔离、超时强杀、内存/CPU 配额、禁网、输出截断）；**目标态**：Docker 容器执行（每任务新容器、只读根文件系统、non-root、网络 drop、资源限额）。实训 Windows 本地开发用 MVP，部署环境用 Docker |
| 判题语义  | ACM 式：stdin 喂 `input`，stdout 与 `expectedOutput` 精确比对（支持多答案数组，与现有 `Problem.testCases` 结构一致）                 |
| 风险    | **任意代码执行安全**是最高风险项（见 §10 R1）；WASM 前端运行时（Code Runner/视频转 GIF）**保留不动**，仅演练场后端化，控制爆炸半径                              |

**结论**：可行，但必须先落地沙箱基线（超时/资源/隔离三件套）再开放提交入口。实训周期内建议 MVP（子进程隔离）+ 明确的安全边界声明。

### 3.3 子方案三：WaterFun 登录注册迁移 + 全站 SSO —— ✅ 可行（已有成熟方案，执行成本已知）

| 维度    | 评估                                                                                                                             |
| ----- | ------------------------------------------------------------------------------------------------------------------------------ |
| 方案基础  | WaterFun 侧已有评审级迁移方案（`auth-sso-migration-plan.md`），含类迁移清单（MOVE/STAY/MODIFY）、库拆分 DDL、事件契约、分阶段步骤、10 项风险与 9 个决策点 —— **无需重新设计，本 PRD 直接引用**                   |
| 工程吻合  | `nitrowater-account` 现有依赖（`oauth2-authorization-server` + `oauth2-client`）正是方案中 `waterfun-auth` 的技术选型；`nitrowater-core`（JPA+Redis）对应 SSO 库与 token 存储；Redis/MySQL 依赖齐备 |
| SSO 模型 | OIDC：每项目一个 client，`sub`=uid，登录页托管在 SSO 域，PKCE + redirect；SSO 域 Cookie 即单点会话                                            |
| 主要成本  | ① 存量用户凭证搬迁（`user` → `sso_account`，含 BCrypt 抽样校验）；② 网关验签来源切换（文件公钥 → JWKS）+ 路由新增 `/api/auth/** → sso`；③ 前端双端接入改造；④ 多账号池与单会话 SSO 的冲突（上游决策点 D8）                         |
| 依赖外部  | 需要 WaterFun 侧配合窗口（双跑期、路由切换、最终物理删除认证代码）——**跨仓库协作项，是排期上的最大外部依赖**                                                |

**结论**：可行且风险可控。执行按上游方案 Phase 节奏，nitrowater 侧先交付「Phase 1 形态」（接口兼容的独立认证服务），Phase 2（OIDC 互认）在本项目内完成。

### 3.4 总体结论

> **方案整体可行**，三子项互相支撑（SSO 为演练场提供身份，wtools 为演练场提供前端壳），建议按 `工程基线 → SSO Phase 1 →（wtools 合并 ∥ 演练场 MVP）→ SSO Phase 2` 顺序推进 —— **执行顺序已于 2026-10-07 拍板：SSO 先行**。约束条件：
>
> 1. 演练场沙箱须在开放任何提交入口前完成（安全前置）；
> 2. SSO 涉及 WaterFun 跨仓库协作与停机窗口，排期需与 WaterFun 侧对齐；
> 3. 上游方案 D1–D9 决策点未拍板前，库表与路径改造不动（见 §12）。

---

## 4. 总体架构设计

### 4.1 目标模块拓扑

```
nitrowater（Gradle monorepo）
├── nitrowater-core        # 共享核心库（实体/仓储/公共契约/认证共用类），不独立部署
├── nitrowater-account     # SSO 认证中心（Spring Authorization Server + 登录注册 API + 托管登录页）
├── nitrowater-server      # 业务 API 网关侧服务（题目/提交/工具后端接口 + 静态资源托管）
├── nitrowater-judge  ★新增  # 代码演练场判题服务（编译 → 沙箱执行 → 判题），独立部署、独立限流
└── nitrowater-web    ★新增  # 复合前端：导航门户 + 工具箱 + 演练场 + 文档频道（velochatx-docs 产物）+ 登录/SSO 回调页
```

### 4.2 运行时拓扑

```
浏览器
 │
 ├─ nitrowater-web (静态资源，可由 nitrowater-server 托管或独立 nginx)
 │      │  Bearer AT（内存）/ SSO Cookie silent renew
 ▼      ▼
[waterfun-gateway(8080)] ── 信任边界（WaterFun 侧，验签 + X-User-* 注入）
 │
 ├─ /api/auth/**   → nitrowater-account (SSO, 独立端口)   ← 路由置顶
 ├─ /api/**        → waterfun-service / admin-service
 └─ /api/judge/**  → nitrowater-judge
       nitrowater-server → nitrowater-core → MySQL(SSO库/业务库) + Redis(SSO token/判题队列)
```

- **本地开发**（无 WaterFun 网关时）：nitrowater-server 直接暴露，JWT 过滤器实现与网关一致的验签 + 头注入逻辑（core 提供共享 Filter）；
- **生产**：网关统一入口，SSO 与判题服务均为下游受信服务。

### 4.3 关键技术选型

| 层    | 选型                                                                 |
| ---- | ------------------------------------------------------------------ |
| 后端   | Spring Boot 4.1.1 / Java 25 / Spring Security + OAuth2 Authorization Server |
| 认证   | OIDC（PKCE）、RS256 JWT、JWKS `/oauth2/jwks`                |
| 存储   | MySQL 8（SSO 库 + 业务库分库）、Redis（AT jti / RT family / 验证码 / 判题会话）  |
| 判题   | MVP：受限子进程；目标态：Docker API（每任务容器、资源限额、网络 drop）                        |
| 前端   | Vue 3 + TypeScript + Vite 8 + Element Plus + CodeMirror 6（沿用 wtools）  |
| 前端认证 | OIDC 授权码 + PKCE（`oidc-client-ts` 或自实现轻量封装），AT 内存化                   |

---

## 5. 功能需求

### FR-1 wtools 合并入 nitrowater

| 编号     | 需求                                            | 优先级 |
| ------ | --------------------------------------------- | --- |
| FR-1.1 | wtools 源码迁入 `nitrowater-web/`，保留全部 7 个工具与既有功能      | P0   |
| FR-1.2 | 路由增加鉴权路由元：`meta.requiresAuth`，登录态由 SSO 统一提供         | P1   |
| FR-1.3 | 新增登录页 / SSO 回调页（`/auth/login`、`/auth/callback`）      | P0   |
| FR-1.4 | 生产构建产物由 `nitrowater-server` 静态托管（或 nginx），dev 走 Vite proxy | P1   |
| FR-1.5 | 导航首页整合：工具区 + 演练场入口 + 用户信息区                          | P1   |
| FR-1.6 | 原 Code Runner / 视频转 GIF 的浏览器 WASM 运行时**保持不动**        | P2   |

### FR-2 代码演练场独立与后端化

| 编号     | 需求                                                                                             | 优先级 |
| ------ | ------------------------------------------------------------------------------------------------ | --- |
| FR-2.1 | 演练场从 wtools 拆出为独立前后端能力：前端只负责编辑/展示，**执行与判题全部后端完成**                                    | P0   |
| FR-2.2 | 后端支持 Python、Java 双语言：接收源码 + 题目 ID + stdin → 编译（Java）→ 沙箱执行 → 与标准答案比对 → 返回逐用例结果            | P0   |
| FR-2.3 | 沙箱三件套：**超时强杀**（默认 5s/用例，可配）、**资源限额**（内存 ≤512MB、输出 ≤1MB）、**隔离**（独立临时目录、禁网、执行用户受限）        | P0   |
| FR-2.4 | 判题结果结构化：`{ status: AC/WA/TLE/MLE/RE/CE, passed, total, cases: [{index, timeMs, stderr?}] }` | P0   |
| FR-2.5 | 题目管理：题目/测试用例入库（管理端 CRUD），替代前端静态 `problems/index.ts`（首期支持导入现有静态题目）                       | P1   |
| FR-2.6 | 提交记录：保存每次提交（用户、题目、语言、代码、结果、耗时），支持历史回看                                            | P1   |
| FR-2.7 | 队列与限流：单用户并发提交限制、全局并发上限（信号量/队列），防止单用户打满判题资源                                        | P0   |
| FR-2.8 | 登录用户方可提交（复用 SSO token）；游客可浏览题目                                                        | P1   |
| FR-2.9 | 目标态：判题迁移到 Docker 容器执行（只读 rootfs、non-root、`--network none`、CPU/内存限额）                       | P2   |

### FR-3 登录注册迁移与全站单点登录

> 执行依据：`waterfun/docs/auth-sso-migration-plan.md`。本节仅定义 **nitrowater 侧** 需求与验收。

| 编号     | 需求                                                                                       | 优先级 |
| ------ | ---------------------------------------------------------------------------------------- | --- |
| FR-3.1 | `nitrowater-account` 承载完整认证 API：图形验证码、密码/验证码登录、注册、忘记密码、登出、刷新（沿用现有 `/api/auth/**` 契约，前端/网关零改动）        | P0   |
| FR-3.2 | 凭证与敏感数据迁入 SSO 库：`sso_account`、`user_data`、`encryption_data_key`、`account_audit_log`（方案 A，17 FK 零改动）        | P0   |
| FR-3.3 | uid 唯一签发权收归 SSO（`HashedTimeNumericUidGenerator` + PK 冲突重试）；发布 `user.registered` 事件                | P0   |
| FR-3.4 | token 体系迁移：RS256 JWT 签发（私钥仅 SSO 持有）、RT family 轮换、设备指纹 did、Redis jti 吊销                            | P0   |
| FR-3.5 | OIDC Provider：`/.well-known/openid-configuration`、JWKS、`/oauth2/authorize|token|revoke`、SSO 会话 Cookie、托管登录页 | P1   |
| FR-3.6 | 接入项目注册：`oidc_client` 表 + client_id / redirect_uri 白名单 / PKCE 强制                                      | P1   |
| FR-3.7 | 全站单点登录效果：任一接入项目（WaterFun web、WaterFun admin、nitrowater-web）登录后，其它项目**免登**；SSO 登出触发 back-channel logout | P1   |
| FR-3.8 | WaterFun 网关改造配合：新增 `/api/auth/** → sso` 路由（置顶）、验签公钥改 JWKS、白名单路径校对                                 | P0   |
| FR-3.9 | 存量数据切换：行数/BCrypt 抽样/孤儿校验全通过后，停机窗口（<30min）切换，具备回滚脚本                                            | P0   |
| FR-3.10 | 接入新项目 SOP 文档化：注册 client → 建本地资料表 → 订阅 `user.registered` → 接 callback                                  | P2   |

### FR-4 velochatx-docs 文档站整合（复合展示站之「文档频道」）

> 规划依据：`D:\Project\webs\velochatx-docs`（VitePress）。**本期仅规划，暂不改动该仓库。**

| 编号     | 需求                                                                                       | 优先级 |
| ------ | ---------------------------------------------------------------------------------------- | --- |
| FR-4.1 | `velochatx-docs` 保持**独立仓库、独立构建**；产物以子路径方式并入 `nitrowater-web`（推荐方案 A：`docs:build` → `dist/` 拷贝至 `public/docs/` 或由 `nitrowater-server` 静态托管，路由挂 `/docs/**`） | P1   |
| FR-4.2 | 融合前先对齐 VitePress `base`（`/docs/`）与站内链接，保证子路径部署后资源/路由不破 | P1   |
| FR-4.3 | 备选融合方式：B「monorepo 子包（pnpm workspace，统一构建）」/ C「iframe 嵌入」——按决策点 N5 拍板 | P2   |
| FR-4.4 | 文档频道默认公开浏览；登录态仅用于用户区与演练场，不阻断文档访问 | P2   |

### FR-5 复合展示站（导航门户）

| 编号     | 需求                                                                                       | 优先级 |
| ------ | ---------------------------------------------------------------------------------------- | --- |
| FR-5.1 | 统一导航门户首页：入口卡片 = 工具箱（wtools）/ 编程演练场 / 文档（VeloChatX）/ 用户区（登录态） | P1   |
| FR-5.2 | 频道划分：`/tools/**`（工具）、`/playground`（演练场）、`/docs/**`（文档）、`/auth/**`（SSO） | P1   |
| FR-5.3 | **独立且完整的 SSO 单点登录**：登录/注册/回调/登出全链路（复用 FR-3），作为对接并迁移 WaterFun 认证的落地载体 | P0   |
| FR-5.4 | 路由与子路径部署兼容（hash 路由或 history + 基路径配置，见 R5） | P2   |

---

## 6. 非功能需求

| 类别    | 要求                                                                                |
| ----- | ----------------------------------------------------------------------------------- |
| 安全-判题 | 任意代码执行必须沙箱化；超时/内存/输出/网络四重限制；判题服务与业务服务进程/容器隔离；提交接口必须鉴权 + 限流；结果回传做长度截断与转义                |
| 安全-认证 | 私钥仅 SSO 持有（JWKS 只暴露公钥）；RT 走 HttpOnly Cookie；登录接口限流（沿用上游 `AuthRateLimitFilter`）；密码 BCrypt；验证码防刷 |
| 性能-判题 | 单用例执行 P95 < 3s（含 JVM 预热上限 5s）；全局并发判题数可配（默认 4）                              |
| 性能-门户 | wtools 首屏保持路由级懒加载（现状），工具切换 < 1s                                     |
| 可用性   | SSO 不可用时业务侧已有 AT 在有效期内可继续访问（graceful）；判题服务故障不影响工具集浏览            |
| 可观测   | 判题任务全链路日志（提交 ID 关联）；认证关键事件审计（登录/注册/改密/换绑，落 `account_audit_log`） |
| 兼容性   | 浏览器：Chrome/Edge 最近两个大版本；判题服务运行环境：Linux x86_64（生产），Windows（开发 MVP，功能等价性允许子进程实现差异） |

---

## 7. 接口设计（草案）

> 统一响应壳：`{ code, message, data }`（沿用 WaterFun `ApiResponse` 风格，DTO 用 `Req`/`Resp` 后缀约定）。

### 7.1 认证（`/api/auth/**`，归属 nitrowater-account）

| 方法     | 路径                                                | 说明              | 鉴权  |
| ------ | ------------------------------------------------- | --------------- | --- |
| GET    | `/api/auth/captcha`                               | 图形验证码           | 公开  |
| POST   | `/api/auth/login-by-password`                     | 密码登录            | 公开  |
| POST   | `/api/auth/login-by-code`                         | 验证码登录           | 公开  |
| POST   | `/api/auth/register`                              | 注册              | 公开  |
| POST   | `/api/auth/refresh`                               | RT 轮换刷新         | Cookie |
| POST   | `/api/auth/logout`                                | 登出（携 deviceFp）  | AT   |
| POST   | `/api/auth/forgot-password/**`                    | 找回密码链路          | 公开  |
| GET    | `/api/auth/account`                               | 当前账号信息          | AT   |
| GET    | `/.well-known/openid-configuration`               | OIDC 元数据         | 公开  |
| GET    | `/oauth2/jwks`                          | 公钥集             | 公开  |
| POST   | `/oauth2/token`、`/oauth2/revoke`                 | 授权码换 token / 撤销 | client |

### 7.2 演练场判题（`/api/judge/**`，归属 nitrowater-judge）

| 方法     | 路径                            | 说明                                             | 鉴权    |
| ------ | ----------------------------- | ---------------------------------------------- | ----- |
| POST   | `/api/judge/submissions`      | 提交判题 `{ problemId, language, code }` → 异步返回 submissionId | 登录    |
| GET    | `/api/judge/submissions/{id}` | 查询结果（轮询）`{ status, passed, total, cases }`          | 登录/本人 |
| GET    | `/api/problems`               | 题目分页列表（分类/难度/标签过滤）                              | 公开    |
| GET    | `/api/problems/{id}`          | 题目详情（含描述、模板、样例；**不返回隐藏测试用例**）                   | 公开    |
| POST   | `/api/problems`（管理端）         | 题目/用例 CRUD                                    | 管理员  |

### 7.3 wtools 存量纯前端工具

无后端接口，全部维持现状（前端内处理）。

---

## 8. 数据设计

### 8.1 SSO 库（`nitrowater`）

实际落地：单一基线 `V1__sso_baseline.sql`（6 表）+ `V1_1__oauth2_oidc_role.sql`（5 表：3 OIDC + 2 RBAC）。

| 表                     | 来源         | 说明                                     |
| --------------------- | ---------- | -------------------------------------- |
| `user`                | 拆自 waterfun.user | uid PK / username 唯一 / password_hash / account_status / user_type（沿用 waterfun 命名） |
| `user_data`           | 原样搬迁       | 手机/邮箱 加密+哈希、加密字段                       |
| `user_data_archive`   | 原样搬迁       | 归档                                     |
| `encryption_data_key` | 原样搬迁       | 字段加密 DEK（KEK 加密存储）                     |
| `account_audit_log`   | 原样搬迁       | 账号审计                                   |
| `sso_identity`        | 新建         | 第三方登录身份绑定（QQ/微信/GitHub…，本期仅预留）          |
| `oauth2_registered_client`     | 新建（Spring AS 标准表） | OIDC 接入客户端（Phase 2.5）                  |
| `oauth2_authorization`         | 新建（Spring AS 标准表） | OIDC 授权状态：token/授权码/会话（Phase 2.5）       |
| `oauth2_authorization_consent` | 新建（Spring AS 标准表） | OIDC 授权同意（Phase 2.5）                    |
| `role`                         | 新建（RBAC）             | 角色词汇表：code/name/builtin；内置 USER/ADMIN 播种（V1_1）|
| `user_role`                    | 新建（RBAC）             | 用户-角色映射：uid+role_id 复合 PK，FK→user/role 级联（V1_1）|

> 命名调整（决策 N9）：账号表沿用 waterfun `user`；客户端/授权/同意改用 **Spring AS 标准表**，退役原计划的 `sso_account`/`oidc_client`/`oidc_session`。
> DDL 细节与校验标准见上游方案 §4.2、§8。
> RBAC（V1_1）：采用角色制 `role`/`user_role`；`role`/`user_role` 为**角色权威源**，`user.userType` 退为账号类别/展示；细粒度权限（`permission`/`role_permission`）**暂不建**，待出现"角色→权限"消费方时再加子版本迁移。

### 8.2 业务库（`nitrowater`）

| 表                     | 说明                                        |
| --------------------- | ----------------------------------------- |
| `problem`             | 题目：id、title、difficulty、category、tags、description、templates(JSON) |
| `problem_test_case`   | 用例：problem_id、idx、stdin、expected_output、is_sample |
| `submission`           | 提交：id、uid、problem_id、language、code、status、passed/total、time_ms、created_at |
| `web_tool_config`（可选） | 工具运营配置（后续）                               |

- 与 SSO 关联仅通过 `uid`（逻辑外键，不建跨库 FK）；
- 用户资料本地表（`local_user`，uid + nickname + avatar）按上游方案「注册外关联」事件创建。

### 8.3 Redis（SSO/判题共用实例，按前缀隔离）

- 认证：`user:{uid}:device:{did}*`、`refreshtoken:{rt}`、验证码、登录失败阈值、`op:re-auth:*`（沿用上游 key 设计）；
- 判题：`judge:queue`、`judge:running`（并发闸门）、`judge:sub:{id}`（短期结果缓存）。

---

## 9. 里程碑与排期

> 假设实训周期为 4 周，本文档为第 2 周交付物；可按实际周数等比压缩。

| 阶段            | 内容                                                                                            | 产出/验收                       | 周次  |
| ------------- | --------------------------------------------------------------------------------------------- | --------------------------- | --- |
| M0 工程基线       | ✅ 初始化修复、构建全绿、模块职责梳理（本 PRD §2.1）                                                        | `gradlew build` 通过          | W2  |
| M1 wtools 合并  | 源码迁入 `nitrowater-web`，路由/构建/导航整合，登录页骨架                                                  | 7 个工具全部可用                  | W2  |
| M2 演练场 MVP    | `nitrowater-judge`：提交 API + 子进程沙箱 + Python/Java 判题 + 限流；前端拆分调用                                                        | 样例题 AC/WA/TLE/RE/CE 用例通过 | W3  |
| M3 SSO Phase 1 | account 落认证 API + SSO 库建表 + 存量凭证搬迁 + WaterFun 网关路由切换（接口形态不变，前端零改动）；**首步见 §9.1**                  | 全链路登录/注册/刷新/登出冒烟通过         | W3  |
| M4 SSO Phase 2 | OIDC Provider + JWKS + nitrowater-web / WaterFun 双端接入 + 互认免登                  | 跨项目免登 e2e 通过                | W4  |
| M5 收尾         | 题库导入、提交历史、文档（接入 SOP）、安全复盘                                                              | 验收单全绿（§11）                 | W4  |

### 9.1 下一步方案（本会话敲定）：`nitrowater-account` 落地 —— SSO Phase 1 第一步

> 依据：本会话将 WaterFun 认证三件套（`AuthController`/`AuthAccountController` + `service-core` 服务层）与本项目 `nitrowater-core` 已迁内容**逐接口比对**后得出。**目标**：`nitrowater-account` 单模块端到端跑通 `/api/auth/**`（captcha → login-by-password → refresh → account → logout），**接口契约与路径不变，前端/网关零改动**。

**已就绪**：`nitrowater-core` 203 文件编译绿；认证服务层齐全（`Login`/`Register`/`AuthCore`/`AccessToken`/`Device`/`Captcha`/`Verification`/`SingleUseToken`/`Account`/`UserDatum`）；基础设施 `CookieUtil`/`ResponseUtil`/`UserCtxHolder`/`AuthContextHelper`/`HashedTimeNumericUidGenerator`/`JwtKeyConfig`/`messages*.properties` 已迁。

**落地缺口（9 项）**：

| # | 缺口 | 说明 |
| --- | --- | --- |
| 1 | account 依赖 | `build.gradle` 未依赖 `:nitrowater-core`，且缺 jpa/redis/jdbc/validation/cache/flyway/mysql/jjwt/hutool/jbcrypt/lombok（Gradle `implementation` 不传递 → 需在 account 重声明，或把 core 改 `java-library`+`api`，见 N6） |
| 2 | 启动扫描 | `@SpringBootApplication` 仅扫 `cn.nitrowater.account` → 需 `scanBasePackages="cn.nitrowater"` + `@EntityScan`/`@EnableJpaRepositories` 指向 `cn.nitrowater.core` |
| 3 | `@RateLimit` / `RateLimitAspect` | 控制器方法级限流注解与切面未迁（依赖 Redis） |
| 4 | `CodeSender` 实现 | 短信/邮箱发码实现（`AliyunSmsService` / `Resend`/`SpringEmail`）未迁 → `CodeSenderFactory` 空绑定触发 `CHANNEL_NOT_SUPPORT`。决策 N4：先落 `ConsoleCodeSender` 打通链路 |
| 5 | 控制器 | `AuthController`、`AuthAccountController` 未迁（waterfun-service web 层） |
| 6 | web 层 DTO | `ForgotPassword{ReAuth,Reset,Verify}Req`、`ReAuthReq`/`ReAuthVerifyReq`/`ReAuthRequest`、`PasswordChangeReq`、`ChangeEmailReq`/`ChangePhoneReq`、`EmailChangeVerifyReq`/`PhoneChangeVerifyReq`、`EmailReAuthReq`/`PhoneReAuthReq`、`EmailChangeVo`/`PhoneChangeVo`/`ReAuthInfoResp`/`ReAuthKeyVo`/`ReAuthTokenVo` 未迁（core 已有 `AccountResp`/`CodeResult`/`LoginClientData`/`LoginResult`/`VerifyChannel`/`VerifyScene`/`PwdLoginReq`/`RegisterRequest`/`SendCodeReq`/`VerifyCodeDto`） |
| 7 | account 配置 | 仅 `spring.application.name`；缺 datasource(SSO 库)/redis/jwt 公私钥路径/device.salt/flyway/messages/server.port |
| 8 | SSO 库 Flyway 基线 | `sso_account`/`user_data`/`user_data_archive`/`encryption_data_key`/`account_audit_log` DDL 未建 |
| 9 | 本地身份注入 | 无网关直连开发时，需 filter 验 AT 并填充 `UserCtxHolder`（`AuthContextHelper` 已在 core） |

**分步（Step 独立可验证）**：

| Step | 内容 | 验证 |
| --- | --- | --- |
| S1 | 依赖与启动骨架：account 依赖 core + starters；`scanBasePackages`；空上下文启动 | 连 MySQL/Redis 启动成功 |
| S2 | 基础设施补齐：迁 `RateLimit`+`RateLimitAspect`；加 `ConsoleCodeSender`（临时） | 启动 bean 齐 |
| S3 | DTO + 控制器平移（路径不变 `/api/auth/**`、`/api/auth/account/**`） | `:nitrowater-account:compileJava` 0 错 |
| S4 | SSO 库基线 + 配置：`V1__sso_baseline.sql`；`application.yml`（datasource/redis/jwt/device.salt）；本地过滤链 | Flyway 迁移通过 |
| S5 | 冒烟：captcha → 造 `sso_account` 测试数据 → login-by-password(200+Cookie) → refresh → `/account` → logout | 5 端点 curl 通过；无 token 访问 `/account` = 401 |

**阻塞/待拍板**：N4（发码通道：先 Console 还是直接上云）、N6（core 依赖暴露方式）、上游 D1–D9（含 D5 加密基准、D3 ban、D6 仓库形态）——数据迁移/Schema 未定前不动库表。

> **注**：用户口述「新建了一个 waterfun-web 前端」，经核对，实际新建落点为 `D:\Project\nitrowater\nitrowater-web`（`package.json name=nitrowater-web`）。本 PRD 统一以 `nitrowater-web` 为准；若确需更名为 `waterfun-web` 请评审时确认。

### 9.2 Phase 1 落地结果 + Phase 2（OIDC）融合方案（评估稿）

#### 9.2.1 Phase 1 落地结果（2026-10-07）
- **S1–S4 完成**：`core/account/server` 编译绿；**IDEA 手动启动 account 成功（:8090）**；
- **SSO 库 `nitrowater` 建成 6 表**：`user`、`user_data`、`user_data_archive`、`account_audit_log`、`encryption_data_key`、`sso_identity`（第三方预留）；
- **采用 waterfun 命名、core 零改动**；`/api/auth/**` 契约与路径不变（前端/网关零改动）；
- **Phase 1 不引入 Spring Security**：自研双 token（RS256 JWT + RT Cookie）+ `LocalAuthContextFilter`（AT→UserCtxHolder）+ 无身份 401 守卫。

#### 9.2.2 Phase 2 融合原则：OIDC Provider 复用现有认证，不自研第二套 token
```
前端 ──► nitrowater-account(SSO)
          ├─ Spring Authorization Server (OIDC Provider, JWKS)   ← Phase 2 新门面
          │    认证委托 ▼ AuthenticationProvider
          ├─ 现有 LoginService/RegisterService（密码/短信/验证码/锁定/DEK） ← Phase 1 复用
          └─ /api/auth/** 兼容层（迁移期保留，迁完下线）
```
- account 加回 `spring-boot-starter-security` + `spring-boot-starter-oauth2-authorization-server`；
- **认证委托**：把现有 `LoginService` 包成 `AuthenticationProvider`，验证码/失败锁定/DEK 全复用；
- **签发交给 AS**：用**同一对 RSA 密钥**签发并暴露 JWKS → Phase 1 旧 token 与新 token **同公钥可验**，平滑过渡；
- `/api/auth/**` 保留为兼容层。

#### 9.2.3 Token 模型映射
| Phase 1（自研） | Phase 2（OIDC） |
| --- | --- |
| AT：RS256 JWT（`sub=uid/jti/did`） | `access_token` + `id_token`（JWKS 验签） |
| RT：UUID Cookie + Redis family | `refresh_token`（轮换）+ **SSO 会话 Cookie** |
| 401 单飞 refresh（前端 axios） | **silent renew**（oidc-client-ts）/ refresh_token |
| 设备指纹 `deviceFp→did` | 自定义 claim / authorize 参数（可选） |
| Redis `jti` 吊销 | **保留**：token revocation + back-channel logout |
| 多账号池 | 决策点 D8（登出换号 / 多会话） |

#### 9.2.4 waterfun 无感登录 + Token 轮换对接
- **无感登录**：SSO 域会话 Cookie → 应用重定向 `/oauth2/authorize` 时静默带 code 跳回（免登）；
- **各应用刷新**：SPA（nitrowater-web / waterfun-admin）用 `oidc-client-ts` automaticSilentRenew（iframe + `prompt=none`）或 refresh_token 轮换；SSR（waterfun-web-client）服务端用 refresh_token；
- **轮换/注销**：waterfun 的 RT family 轮换 → 由 AS refresh_token rotation + `OAuth2Authorization` 存储承接；注销用 `/oauth2/logout` + back-channel logout；
- **网关联公钥改 JWKS**；waterfun 最终也作为 OIDC client。

#### 9.2.5 依赖清单（新增极少）
| 模块 | 新增 |
| --- | --- |
| `nitrowater-account` | `spring-boot-starter-security` + `spring-boot-starter-oauth2-authorization-server`（恢复） |
| `nitrowater-server` | `spring-boot-starter-oauth2-resource-server` |
| `nitrowater-web` | npm `oidc-client-ts` |
| waterfun 网关 | 公钥源改 JWKS（已有 resource-server） |
| 数据 | `oidc_client` + AS 授权状态存储（复用 JDBC/Redis，**无需新库**） |

#### 9.2.6 CORS / 跨域（必做白名单）
- account 新增 `WebConfig implements WebMvcConfigurer`，读 `app.cors.allowed-origins` **白名单**；`allowCredentials=true`（**不可 `*`**）；覆盖 `/api/auth/**`、`/oauth2/**`、`/.well-known/**`；
- **Cookie SameSite**：dev 同站点（localhost 不同端口）`Strict` 可用；**生产跨主域必须 `SameSite=None; Secure`（HTTPS）**，或 **SSO 与应用同主域（推荐）**；
- 前端 `fetch(..., { credentials:'include' })`。

#### 9.2.7 下一步
1. ~~account 补 CORS 白名单~~（已完成）；
2. ~~Phase 1 冒烟~~（已完成）；
3. ~~Phase 2 骨架：恢复 AS 依赖 + `AuthorizationServerConfig` + `AuthenticationProvider`~~（已完成，见 §9.2.8）；
4. ~~**Phase 2.6**：`nitrowater-web` 接入 `oidc-client-ts`（`/auth/callback`、silent renew）~~（已完成，见 §9.2.10）；
5. **Phase 2.8**：WaterFun 网关验签改 JWKS + 路由 `/api/auth/** → SSO`（跨仓库，待窗口；动手前先定 issuer）；
6. ~~端到端：起 MySQL/Redis 后跑一次完整授权码流（decode token 校验 `sub/uid/preferred_username/roles/did`）~~（已完成：`smoke-oidc.ps1` 全绿）。

#### 9.2.8 Phase 2.4 落地结果（令牌定制 / 登录主体）

- **登录主体**：新增 `cn.nitrowater.account.security.SsoUserPrincipal`（`UserDetails`），承载 uid / 登录名 / 昵称 / userType / accountStatus / deviceFp / did；`getUsername()` 返回 uid（保证 `sub=uid`）；`getAuthorities()` 由 `user_role` 派生（Phase 2.5 起，原为 userType；无绑定回落 `ROLE_USER`）。
- **认证委托**：`SsoAuthenticationProvider` 由 `LoginResult.user()` 构造该主体，`did` 经 `DeviceService.calculaateDid(uid, dfp)` 计算（纯 HMAC，不依赖 Redis）。
- **令牌定制**：`OAuth2TokenCustomizer<JwtEncodingContext>` 输出 `sub=uid`、`uid`、`preferred_username`、`name`、`roles`、`did`；经默认 `/userinfo`（由 id_token claims 映射）自动暴露。
- **核实纠正**：AS 刷新链路会把授权记录中的 `java.security.Principal`（资源所有者）重新注入 token 上下文，故默认 `sub` 本就是 uid；customizer 的价值是**显式保证 + 扩展 claim**，并非修 bug。

#### 9.2.9 Phase 2.5 落地结果（JDBC 持久化，方案 A）

- **存储**：`JdbcRegisteredClientRepository` + `JdbcOAuth2AuthorizationService` + `JdbcOAuth2AuthorizationConsentService`，替换原内存实现，重启不丢。
- **表**：`V1_1__oauth2_oidc_role.sql` 建 `oauth2_registered_client` / `oauth2_authorization` / `oauth2_authorization_consent`（官方 7.1.1 DDL）。
- **客户端**：启动幂等播种 `nitrowater-web`（公共客户端 + PKCE，auth-code + refresh，redirect `http://localhost:5173/auth/callback`）。
- **连接串**：按官方建议补 `preserveInstants=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` 以保证令牌时间精度。
- **校验**：临时库应用 V1_1 DDL 通过（MySQL 8）；`nitrowater` 库当前为 Flyway baseline v1（版本 1），下次启动自动应用 V1_1（版本 1.1）。

#### 9.2.10 Phase 2.6 落地结果（前端 OIDC 客户端 + issuer 统一）

- **`nitrowater-web` OIDC 客户端**：Vue 3 + `vue-router` + `oidc-client-ts`，授权码 + PKCE（公共客户端）、`automaticSilentRenew`、登出；页面 `/`（门户壳 + 令牌声明展示）、`/auth/callback`、`/auth/silent-callback`。dev 端口固定 5173（`vite.config.ts` strictPort）以匹配 `redirect_uri`；可用 Vite env（`VITE_OIDC_*`，模板 `.env.example`）覆盖。
- **issuer 统一（决策 A：AS 单一签发者）**：单一属性 `jwt.issuer`（环境变量 `JWT_ISSUER`，dev 默认 `http://localhost:8090`）同时供 Phase1 自研 AT 与 AS 使用，等于发现文档 `metadata.issuer`。**必须是 URL**（OIDC Discovery 要求），不能是裸词（如 `waterfun` / `nitrowater`）；生产设 `JWT_ISSUER=https://auth.nitrowater.cn`，且必须与前端 `VITE_OIDC_AUTHORITY` 一致。
- **AT/RT 标记废弃**：`AccessTokenService`/`AccessTokenServiceImpl`、`AuthCoreService`/`AuthCoreServiceImpl` 标 `@Deprecated`，方法保留供 `/api/auth/**` 兼容层使用，待 WaterFun 切 OIDC 后随垫片退役。
- **ESLint**：`nitrowater-web` 引入 ESLint flat config（`@eslint/js` + `typescript-eslint` + `eslint-plugin-vue`），脚本 `npm run lint` / `lint:fix`。
- **验证**：`gradlew build` 全绿；`npm run lint` 0 问题；`npm run build` 通过；实机 discovery `issuer=http://localhost:8090`，CORS 放行 `http://localhost:5173`（allow-credentials=true）。
- **SSO 托管登录/注册页**：身份 UI 归属身份中心——auth 域托管品牌登录页 `/login`（原生表单 POST → Spring Security → 续跑 OIDC）与注册页 `/register`（调 `/api/auth/**`），仿 WaterFun `AuthBox` 风格（无框架静态页，`static/auth/*`）。业务前端 `nitrowater-web` 仅做客户端；其首页改用 Element Plus 重排。

---

## 10. 风险清单与应对

| #   | 风险                                                        | 等级 | 应对                                                              |
| --- | --------------------------------------------------------- | -- | --------------------------------------------------------------- |
| R1  | **判题服务任意代码执行**：沙箱逃逸/资源耗尽/恶意扫描                                    | 高  | 三件套前置（FR-2.3）；判题独立进程/容器；禁网；限流；P2 迁 Docker；安全复盘作为验收门禁            |
| R2  | SSO 跨仓库协作窗口（WaterFun 侧路由/双跑/删除认证代码的节奏不受本项目控制）                      | 高  | 按上游 Phase 逐阶段交付；接口形态不变先行（Phase 1），前端零改动降低耦合                   |
| R3  | 上游 D1–D9 决策点未拍板 → 库表/路径返工                                         | 中  | §12 列为评审前置项；未拍板前只做接口兼容层                                       |
| R4  | Windows（开发）与 Linux（部署）沙箱行为差异                                       | 中  | 沙箱抽象层双实现（子进程/Docker），判题用例双环境跑同一套 e2e                          |
| R5  | wtools 合并后 hash 路由 + SSO 回调页的路径冲突                                  | 低  | 回调路径 `/auth/**` 显式加入免登白名单；保留 hash 路由避免全站改 history 模式           |
| R6  | Spring Boot 4.1.1 + Java 25 为新版本，生态踩坑（本项目初始化已遇 wrapper 超时、悬空依赖等问题） | 低  | 锁版本、CI 每次全量构建；问题修复记入 `docs/`                                    |
| R7  | 判题并发打满服务器资源                                                | 中  | 全局并发闸门 + 单用户配额 + 队列削峰（FR-2.7）                                  |

---

## 11. 验收标准

1. **工程**：`gradlew build` 全绿；`nitrowater-web` `npm run build` 通过；
2. **工具**：wtools 7 个工具在 nitrowater 内功能与迁移前一致（回归清单逐项勾验）；
3. **演练场**：
   - Python/Java 各 3 道样例题，AC/WA/TLE/RE/CE 五类结果全部正确返回；
   - 死循环代码在 5s 内被强杀（TLE）；超内存触发 MLE（或 RE，需说明）；
   - 未登录提交返回 401；单用户并发超限返回 429；
4. **SSO**：
   - 存量账号可正常登录（BCrypt 抽样 ≥200 条通过）；
   - WaterFun web 登录后访问 admin / nitrowater-web 免登（授权范围内）；
   - 未带 token 访问受保护端点必须 401（防绕过回归用例）；
   - SSO 登出后各接入项目会话失效（back-channel）；
5. **文档**：接入 SOP、判题 API 文档、本 PRD 评审通过并更新状态。

---

## 12. 待决事项（评审拍板项）

> 来源：上游方案 §10（D1–D9）+ 本项目新增决策。**评审时逐项拍板，未拍板项对应工作不启动。**

| 编号      | 决策点                                                               | 建议                        | 影响范围   |
| ------- | ----------------------------------------------------------------- | ------------------------- | ------ |
| D1/D4/D5 | 库拆分方案 A、独立实例拓扑、user_data 加密基准                                      | 采纳上游建议（A / 独立实例 / 就近审计确认）  | 数据迁移   |
| D3      | 本地封禁是否拦截 SSO 登录                                                    | 资源端拦截，SSO 只认全局 account_status | 登录语义   |
| D6      | waterfun-auth 独立仓库 vs monorepo 新模块                                | **本项目采纳：nitrowater-account 即该模块（同 repo 变体）** | 工程形态   |
| D7      | 路径收编（logout → `/api/auth/logout`、admin 统一前缀）                      | Phase 2 一并改                | 前端双端   |
| D8      | WaterFun 多账号池 vs 单会话 SSO                                           | 降级为登出换号（改动最小）              | web-client |
| **N1**  | 演练场沙箱：子进程 MVP 是否可作为本期交付（Docker 为 P2）                                 | 是（实训环境约束下）                | FR-2   |
| **N2**  | `nitrowater-judge` 独立部署 vs 并入 server（端口/限流隔离粒度）                       | 独立模块独立部署                   | 架构     |
| **N3**  | wtools 是否保留独立仓库同步（双向维护）vs 单向归档                                     | 单向迁入 + 原仓归档                | FR-1   |
| **N4**  | SSO 发码通道：先落 `ConsoleCodeSender`（日志打印验证码，打通链路）vs 直接迁 AliyunSms/Resend 真实实现 | 先 Console 打通冒烟，云通道后补       | §9.1 S2 |
| **N5**  | `velochatx-docs` 融合方式：A 产物挂子路径（独立仓）/ B monorepo 子包（pnpm workspace）/ C iframe 嵌入 | A（松耦合、保留独立仓）             | FR-4   |
| **N6**  | core 依赖暴露：account 重复声明依赖 vs core 改 `java-library` + `api` 暴露            | **已定：Option B**（account/server 各自声明，与 waterfun 一致） | §9.1 S1 |
| **N7**  | 跨域 CORS：account 白名单来源（yml `app.cors.allowed-origins`）+ 是否允许 credentials | 白名单 + `allowCredentials=true`；不放 `*` | §9.2.6 |
| **N8**  | SSO 与应用部署：同主域（`SameSite=Lax`，推荐）vs 跨主域（`SameSite=None;Secure`，强制 HTTPS） | 同主域 | 无感登录 |
| **N9**  | OIDC 客户端/授权/同意存储：A Spring AS 标准表 vs B 自建 `oidc_client`/`oidc_session` | **已定：A**（官方 3 表 + `Jdbc*` 实现，零自研） | Phase 2.5 |

---

*评审意见请直接批注本文档；拍板后更新状态为「已评审」并进入 M1 实施（SSO 先行，首步 §9.1）。*
