# NitroWater

[![GitHub](https://img.shields.io/badge/GitHub-danburen%2Fnitrowater-181717?logo=github)](https://github.com/danburen/nitrowater)
[![Gitee](https://img.shields.io/badge/Gitee-blackwallet%2Fnitrowater-c71d23?logo=gitee)](https://gitee.com/blackwallet/nitrowater)
![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![Vue](https://img.shields.io/badge/Vue-3-42b883)
![License](https://img.shields.io/badge/License-MIT-blue)

[English](../../README.md) | 简体中文

**面向 Minecraft 服主的一站式平台**：开服工具箱、插件中心、AI 助手，背后是一套自托管的 SSO / OIDC 身份中心。浏览器永远拿不到 token —— BFF 在服务端保管凭据并代理所有 API。

- 💪 **一个账号，一个入口** —— 门户、工具箱、插件中心、对话只需登录一次。
- 🔐 **令牌留在服务端** —— confidential OIDC 客户端 + HttpOnly 会话，`/api/**` 由 BFF 带 Bearer token 转发。
- 🧰 **11 个在线工具** —— MOTD、`server.properties`、`server-icon`、启动脚本、压缩包、目录树、Java→TypeScript、MP4→GIF、Markdown→Word、代码运行器。
- 🤖 **NitroBot** —— 基于自有文档的检索问答、联网搜索、沙箱内执行代码，并带长期记忆。
- 🧱 **朴素可靠的基础设施** —— Gradle 多模块后端、Vue 3 单页前端、Flyway 管理 schema、Docker Compose 部署。

## 🧭 NitroWater 是什么

开一台 Minecraft 服务器，日常要在十几个互不相关的站点之间来回横跳 —— MOTD、`server.properties`、图标、启动脚本、插件、文档。NitroWater 把这些日常收拢成一个同源平台，共用一个账号。

### 模块

| 模块 | 技术 | 职责 |
|---|---|---|
| `nitrowater-web` | Vue 3、TypeScript、Vite、Tailwind | 门户、工具箱、插件中心、对话、管理台 |
| `nitrowater-bff` | Spring Boot、Spring Security OAuth2 client | 登录、会话、`/api/**` 代理、托管 SPA |
| `nitrowater-account` | Spring Authorization Server、Vue 3 workspace | SSO / OIDC 身份中心 |
| `nitrowater-server` | Spring Boot、Spring AI、JPA + Flyway | 插件中心、AI Agent、代码运行器 |
| `nitrowater-resource` | Spring Boot、SeaweedFS / S3 | 文件、上传、插件 JAR 托管 |
| `nitrowater-core` / `nitrowater-lib` | JPA、无框架依赖工具类 | 共享持久化与安全底座 |

浏览器只跟 BFF 打交道 —— access / refresh token 永远不出服务端。

## ✨ 功能

### 身份与权限

- 自托管授权服务器：authorization code + PKCE、refresh token、RP-initiated logout、JWKS 与 userinfo。
- 登录、注册、验证码与多账号切换，角色 `SUPER_ADMIN` / `ADMIN` / `USER` 随 JWT 下发。

### 开服工具箱

11 个工具，绝大多数完全跑在浏览器里（WebAssembly / Worker），文件不离开本机：

- **MOTD 编辑器** —— 支持 `&` / `§` 颜色代码，双行实时预览，可导出 legacy、`server.properties` 或 JSON。
- **`server.properties` 生成器** —— 分组表单、预设、导入现有文件、保留未知键、实时校验。
- **`server-icon` 生成器**与**启动脚本**生成（含常用 JVM 参数）。
- **压缩包与目录树** —— ZIP 打包/解包、目录树、ListTree 图表渲染。
- **转换器** —— Java→TypeScript、MP4→GIF、Markdown→Word。
- **代码运行器** —— JavaScript、TypeScript、Python、Lua 在 Worker 沙箱中执行，服务端另有 Piston 沙箱负责评测。

### 插件中心

- 按目录浏览插件，带版本、元数据与文档。
- 作者文档是 `deploy/plugin-docs` 下的 VitePress 站点，经 BFF 的 `/plugin/**` 同源提供。
- JAR 与附件由资源服务存储（SeaweedFS / S3、腾讯 COS 或本地磁盘），通过预签名地址上传。

### NitroBot

- 基于 Qdrant 中索引语料的回答，带 `[n]` 引用指回原文。
- 工具是一等公民：`searchDocs`、`loadSkill`、`webSearch`、`runCode`、`calculate`，统一返回 `ToolResponse`，前端渲染成步骤时间线。
- SSE 分事件推送 `step` / `delta`，支持按用户配额、token 预算、内容审核、Agent Trace 与长期记忆。

### 管理台

- 门户侧（`/admin`）：插件、资源、AI 运行设置、内容安全、用户用量、索引状态、技能与工具。
- 账号侧（`:8090` 的 `/admin`）：用户、角色与已注册的 OIDC 客户端。

## 🚀 快速开始

### 环境要求

| 依赖 | 说明 |
|---|---|
| JDK 25 | 后端 toolchain |
| Node 24 + pnpm 11 | `nitrowater-web` 用 npm；`nitrowater-account` 是 pnpm workspace |
| Docker | Piston、SeaweedFS、Qdrant 与插件文档站 |
| MySQL 8 与 Redis | 本机服务，默认 `3306` / `6379`（`root` / `123456`） |

### 三条命令

```powershell
.\deploy\bin\gen-keys.ps1      # 首次：生成 JWT 签名密钥
.\bin\start-all.ps1            # 构建 jar、拉起基础设施、启动四个服务
cd nitrowater-web; npm run dev # http://localhost:5173，/api 代理到 BFF
```

| 位置 | 地址 |
|---|---|
| 门户（BFF 托管的 SPA） | <http://localhost:8080> |
| 前端开发服务器 | <http://localhost:5173> |
| 身份中心 | <http://localhost:8090> |
| 直达页面 | `/tools`、`/plugins`、`/chat` |

`start-all.ps1` 支持 `-SkipBuild`（复用现有 jar 重启）、`-SkipInfra`、`-Hidden`（后台运行，日志在 `bin\.run\logs\<module>.out.log`）、`-BuildWeb`（由 BFF 直接托管构建产物）与 `-NoRestart`；`stop-all.ps1` 停止全部服务。完整参数与数据脚本见 [`bin/README.md`](../../bin/README.md)。

开发数据只放在脚本里、不进迁移：

```powershell
.\bin\dev\dev-seed.ps1                         # testadmin / testuser1 / testuser，密码 Passw0rd123
.\bin\dev\reset-db.ps1 -Db biz -Force -Restart # 清库，由 Flyway 从 V1 重放
```

### 只调一个服务

其余服务照常跑着，用 Gradle 或 IDE 单独启动：

```powershell
.\gradlew.bat :nitrowater-server:bootRun
```

需要下断点时，用 JDWP 启动 jar，再把 IDE attach 到 `5005`：

```powershell
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar nitrowater-server\build\libs\app.jar
```

前端改动走 Vite 热更新；`npm run dev` 必须开着，因为 SPA 只通过开发代理访问 BFF。改过 schema 之后启动失败，基本都是 Flyway checksum 过期 —— 按上面的方式重置数据库即可。

## 🔨 如何构建

### 后端

```powershell
.\gradlew.bat build                        # 全模块：编译、测试、JaCoCo 报告
.\gradlew.bat :nitrowater-server:bootJar   # 单模块 → build/libs/app.jar
```

account 模块会顺带构建 `admin-ui` 与 `auth-ui`；只要 jar 时用 `-PskipAdminUi` / `-PskipAuthUi` 跳过（Dockerfile 本身也是独立的 node stage 构建前端）。

### 前端

```powershell
cd nitrowater-web
npm ci
npm run lint        # ESLint
npm run build       # vue-tsc 类型检查 + Vite 构建 → dist/，由 BFF 托管
```

```powershell
cd nitrowater-account
pnpm install
pnpm build          # admin-ui + auth-ui
pnpm dev:admin      # http://localhost:5174
pnpm dev:auth       # http://localhost:5175
```

### Docker 镜像

一个多阶段 `Dockerfile` 承载所有前端与后端服务，按 target 取用：

```bash
docker build --target bff -t nitrowater-bff .
docker build --target account -t nitrowater-account .
docker build --target server -t nitrowater-server .
docker build --target resource -t nitrowater-resource .

# 一次五个，四个应用 target 的 gradle 只跑一次（target 在 docker-bake.hcl，REGISTRY 默认 ghcr.io）
# kit 是 FROM scratch 的载体（compose 文件 + 运维脚本），deploy.yml 在宿主机解包
# --set '*.attest=' 去掉 SBOM/来源证明：docker 导出器装不了 attestation
TAG=dev SHA=$(git rev-parse --short HEAD) REPO=local/nitrowater \
  docker buildx bake --load --set '*.attest='
```

### 提交之前

```powershell
.\gradlew.bat test                     # 全模块
.\gradlew.bat :nitrowater-server:test  # 单模块
cd nitrowater-web; npm run lint; npm run build
cd nitrowater-account; pnpm lint; pnpm build
```

覆盖率报告在 `<module>/build/reports/jacoco/test/html/index.html`，汇总报告在 `build/reports/jacoco/root/html/index.html`。三条流水线都是手动触发：`build.yml` 先跑各项检查，再用 `docker buildx bake` 构建四个应用镜像 + `kit`（compose 文件与运维脚本）并推 GHCR（`<tag>` + `<sha>` 双标签）；`deploy.yml` 只把已构建好的 tag 通过 SSH 上线（回滚也用它），用 `docker create` + `docker cp` 解出 kit（宿主机不需要 git），再调 `deploy/bin/deploy.sh`——手动上线是同一条命令；`release.yml` 把 build → deploy 串成一次运行。

## 📦 部署

单台 Linux + Docker。三个 compose 文件共享同一网络，顺序固定（`infra` → `account` → `app`）；可选的 `edge` profile 会在 BFF 与身份中心前加一层 nginx + ACME。

```bash
bash deploy/bin/gen-keys.sh
cp deploy/docker/.env.example deploy/docker/.env   # 域名、密钥、存储、配额
bash deploy/bin/check-env.sh deploy/docker/.env
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.infra.yml up -d
# 再依次 up docker-compose.account.yml、docker-compose.app.yml
```

每个变量在 `.env.example` 里都有注释；完整步骤、变量填写顺序（必填 / 地址组 / 条件必填 / 镜像 tag）、TLS edge profile、备份与运维说明见 [`deploy/README.md`](../../deploy/README.md)。

## 📚 文档

| | |
|---|---|
| 需求与设计 | [`docs/PRD.md`](../PRD.md) |
| 架构说明 | [`docs/architecture.md`](../architecture.md) |
| 部署 | [`deploy/README.md`](../../deploy/README.md) |
| 开发脚本 | [`bin/README.md`](../../bin/README.md) |
| 工程约定 | [`AGENTS.md`](../../AGENTS.md) |

## 🤝 参与贡献

欢迎提 Issue 和 Pull Request —— 中文或英文都行，怎么顺手怎么写。

1. Fork 仓库，从 `main` 切分支（`feat/…`、`fix/…`、`docs/…`）。
2. 提交信息参考 [Conventional Commits](https://www.conventionalcommits.org/)，`type(scope): summary` 这个形状就够了（`feat(agent): add calculate tool`），不必逐字对齐规范；一个 PR 只做一件事。
3. 遵守 [`AGENTS.md`](../../AGENTS.md) 的约定：时间列 `TIMESTAMP(3)`、枚举整数编码、列表查询走 `Specification`、JSON 统一 `ApiResponse<T>`、前端 API 调用只写在 `nitrowater-web/src/api/`。
4. 跑一遍[提交之前](#提交之前)里的检查，并保持现有测试全绿。
5. 不要提交任何密钥、凭据 —— 本地由 `deploy/bin/gen-keys.*` 生成，生产由环境变量注入。

发现 bug 或有功能建议？开 Issue 时附上版本/提交、涉及模块和复现步骤。

## 📄 许可证

[MIT](../../LICENSE)
