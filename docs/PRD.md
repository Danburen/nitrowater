# NitroWater 项目需求设计文档（PRD）

> - **文档版本**：v1.1
> - **产出日期**：2026-10-07
> - **修订日期**：2026-10-07（v1.1：同步实施进展——SSO 认证核心库迁入 core、构建全绿；执行顺序拍板「SSO 先行」；新增 N4–N6 决策点）
> - **文档性质**：实训第二周 —— 项目设计需求文档
> - **状态**：待评审
> - **关联文档**：
>   - `D:\Project\waterfun\docs\auth-sso-migration-plan.md`（WaterFun 认证抽离 & SSO 迁移方案，本文 SSO 章节的上游依据）
>   - `D:\Project\waterfun\docs\reference\auth-login-flow.md`（现有登录链路参考）
>   - `D:\Project\webs\wtools\README.md`（待合并工具集说明）

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

本项目（nitrowater）为新建工程，定位为**多项目共用的服务端平台**，承载三项诉求：

1. **工具集整合**：现有纯前端工具站 wtools（7 个工具）需要并入本项目，统一工程与入口；
2. **代码演练场独立化**：wtools 内的「编程演练场 / Code Runner」目前依赖浏览器内 WASM 运行时（Pyodide / CheerpJ），能力受限（无法真实判题、无法受控执行用户代码），计划**拆分为独立的后端驱动编译-执行-测试服务**；
3. **全站单点登录**：WaterFun 项目的登录注册模块计划抽离为独立 SSO 服务，nitrowater 作为其落地工程之一，实现 WaterFun 生态多项目的全站单点登录（SSO）。

### 1.2 目标与验收口径

| #   | 目标               | 验收标准                                                       |
| --- | ---------------- | ---------------------------------------------------------- |
| G1  | 工程初始化完成         | `gradlew build` 三模块全绿，可作为后续开发基线                            |
| G2  | wtools 平滑并入      | 工具全部功能在 nitrowater 内可用；构建/路由/资源统一管理                       |
| G3  | 演练场后端化          | 用户提交 Python/Java 代码 → 后端编译 → 沙箱执行 → 自动比对测试用例 → 返回判题结果 |
| G4  | 全站单点登录          | 在任一接入项目登录后，访问其它接入项目免登；接入新项目仅需注册 client + 本地资料表        |
| G5  | 认证能力从 WaterFun 迁出 | 登录/注册/凭证/验证码/token 签发全部由 SSO 服务承担（对接既有迁移方案 Phase 节奏）    |

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
| `nitrowater-account` | `spring-boot-starter-security-oauth2-authorization-server`、`oauth2-client`、`webmvc` | 空骨架（下一步：平移 `/api/auth/**` 控制器，`scanBasePackages=cn.nitrowater`） | **SSO 认证中心**（OIDC Provider，含登录注册 UI 与 token 签发） |
| `nitrowater-core`    | `data-jpa`、`data-redis`、`jdbc`、MySQL 驱动、`spring-security`(core/oauth2-jose/web)、`spring-web`、`jakarta.servlet-api`、jackson2、`hutool-captcha`、`jbcrypt`、`jjwt`、validation、cache、flyway、Lombok（**纯库，bootJar 已禁用**） | **认证核心库已迁入**：waterfun `common-lib` 全量 51 文件 + 认证切片 100 文件 + exception 整树 60 文件 → `cn.nitrowater.core.lib`；业务耦合已砍（`TODO(SSO)` 标记）；`compileJava` 0 错 | **共享核心库**：实体、仓储、认证/判题共用服务（不独立部署）             |
| `nitrowater-server`  | `webmvc`                                                                 | 空骨架    | **业务 API**：wtools 后端接口、题目/提交管理、静态资源托管          |

- **已完成项**（本期实操：工程初始化 + SSO 核心库迁移）：
  1. 修复 Gradle wrapper 下载超时（预置 Gradle 9.7.1 发行版至 `GRADLE_USER_HOME` 缓存）；
  2. 删除两处悬空依赖 `implementation ':waterfun-core'`（骨架拷贝自 WaterFun 时遗留，模块不存在）；
  3. 根工程 `group` 由 `org.waterfun.lib.waterfun` 统一为 `cn.nitrowater`；
  4. `nitrowater-core` 补测试专用数据源配置（内存 H2 + `testRuntimeOnly h2`），使 `contextLoads` 不依赖外部 MySQL；
  5. **SSO 认证核心库收束迁移**（全拷收束方案）：waterfun `common-lib` 全量 + `service-core` 认证切片 + exception 整树（210+ Java 文件）迁入 `cn.nitrowater.core.lib`，包名 `org.waterfun.* → cn.nitrowater.core.lib`；业务实体、云 SDK 通道、业务 Controller 一律不拷；
  6. **core 纯库化**：删除 Spring Boot 启动类与上下文测试、禁用 `bootJar`（恢复 plain jar）；`Login/Register/Account` 三服务砍除 stats/audit/online/role 业务耦合，恢复点以 `TODO(SSO)` 标记；
  7. **验证结果：`:nitrowater-core:compileJava` 101 错误 → 0；`gradlew build` BUILD SUCCESSFUL（3 模块，20 task，全绿）**。

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
└── nitrowater-web    ★新增  # 前端（由 wtools 演进）：工具集 + 演练场 + 登录/SSO 回调页
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
| 认证   | OIDC（PKCE）、RS256 JWT、JWKS `/.well-known/jwks.json`                |
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
| GET    | `/.well-known/jwks.json`                          | 公钥集             | 公开  |
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

### 8.1 SSO 库（`nitrowater_account`）

| 表                     | 来源         | 说明                                     |
| --------------------- | ---------- | -------------------------------------- |
| `sso_account`         | 新建（拆自 user） | uid PK / username 唯一 / password_hash / account_status |
| `user_data`           | 原样搬迁       | 手机/邮箱哈希、加密字段（FK 改指 sso_account）          |
| `user_data_archive`   | 原样搬迁       | 归档                                     |
| `encryption_data_key` | 原样搬迁       | 字段加密密钥                                 |
| `account_audit_log`   | 原样搬迁       | 账号审计                                   |
| `oidc_client`         | 新建         | 接入项目注册                                 |
| `oidc_session`        | 新建         | SSO 会话（若非纯 Cookie 实现）                  |

> DDL 细节与校验标准见上游方案 §4.2、§8。

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
| M3 SSO Phase 1 | account 落认证 API + SSO 库建表 + 存量凭证搬迁 + WaterFun 网关路由切换（接口形态不变，前端零改动）                  | 全链路登录/注册/刷新/登出冒烟通过         | W3  |
| M4 SSO Phase 2 | OIDC Provider + JWKS + nitrowater-web / WaterFun 双端接入 + 互认免登                  | 跨项目免登 e2e 通过                | W4  |
| M5 收尾         | 题库导入、提交历史、文档（接入 SOP）、安全复盘                                                              | 验收单全绿（§11）                 | W4  |

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

---

*评审意见请直接批注本文档；拍板后更新状态为「已评审」并进入 M1 实施。*
