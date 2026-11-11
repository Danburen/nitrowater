# NitroWater

[![GitHub](https://img.shields.io/badge/GitHub-danburen%2Fnitrowater-181717?logo=github)](https://github.com/danburen/nitrowater)
[![Gitee](https://img.shields.io/badge/Gitee-blackwallet%2Fnitrowater-c71d23?logo=gitee)](https://gitee.com/blackwallet/nitrowater)
![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![License](https://img.shields.io/badge/License-MIT-blue)

[English](../../README.md) | 简体中文

面向 Minecraft 腐竹的多模块平台：开服工具箱、插件中心与 AI 助手，背后是一套独立的 SSO / OIDC 身份中心。浏览器不保存任何 token——BFF 在服务端持有凭据并代理所有 API。

## 模块

| 模块 | 职责 |
|---|---|
| `nitrowater-web` | Vue 3 + Vite 前端：门户、工具箱、插件中心、NitroBot 对话 |
| `nitrowater-bff` | confidential OIDC 客户端、会话、`/api/**` 代理，并托管 SPA |
| `nitrowater-account` | SSO / OIDC 身份中心，自带登录页与管理端 |
| `nitrowater-server` | 业务 API：插件中心、AI Agent、沙箱代码运行 |
| `nitrowater-resource` | 文件服务与插件 JAR 托管 |
| `nitrowater-core`、`nitrowater-lib` | 共享持久化/安全基础件、无框架依赖的工具类 |

工具箱包含 MOTD、`server.properties`、`server-icon`、启动脚本，以及代码运行、ZIP、树形视图、Java→TypeScript、MP4→GIF、Markdown→Word。NitroBot 基于检索到的文档语料（Qdrant）作答，可联网核实、执行代码并保留长期记忆。

## 本地运行

依赖：JDK 25、Node 24 + pnpm 11、Docker（Piston / SeaweedFS / Qdrant / 文档站）、MySQL 8、Redis。

```powershell
.\deploy\bin\gen-keys.ps1      # 首次：生成 JWT 签名密钥
.\bin\start-all.ps1            # 构建 jar、拉起基础设施、启动四个服务
cd nitrowater-web; npm run dev # http://localhost:5173，/api 代理到 BFF
```

门户 `:8080`，身份中心 `:8090`（直达 `/tools`、`/plugins`、`/chat`）。`start-all.ps1 -SkipBuild` 复用现有 jar 重启，`-Hidden` 后台运行，`-BuildWeb` 由 BFF 直接托管构建版，`stop-all.ps1` 停止全部服务；参数与数据脚本见 [`bin/README.md`](../../bin/README.md)。

开发数据只放在脚本里、不进迁移：`bin/dev/dev-seed.ps1` 生成测试用户与示例资源（`testadmin` / `testuser1` / `testuser`，密码 `Passw0rd123`），`bin/dev/reset-db.ps1` 清库后由 Flyway 从 `V1` 重放。

## 调试

`start-all.ps1` 启动的服务各占一个常驻窗口，Spring 日志直接可见；加 `-Hidden` 后同样内容写入 `bin\.run\logs\<module>.out.log`。只调一个模块时用 Gradle 或 IDE 直接跑：

```powershell
.\gradlew.bat :nitrowater-server:bootRun
```

需要在断点处停下时，用 JDWP 启动 jar，再把 IDE attach 到 5005：

```powershell
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar nitrowater-server\build\libs\app.jar
```

提交前跑一遍：

```powershell
.\gradlew.bat test                     # 全模块
.\gradlew.bat :nitrowater-server:test  # 单模块
cd nitrowater-web; npm run lint; npm run build
```

前端改动走 Vite 热更新，前提是 `npm run dev` 在跑——SPA 只通过开发代理访问 BFF。数据库报错多半是 checksum 过期：`bin/dev/reset-db.ps1 -Db biz -Force -Restart` 会清库并重放迁移。

## 部署

单台 Linux + Docker。三个 compose 文件共享同一网络，顺序固定（`infra` → `account` → `app`）；可选的 `edge` profile 会在 BFF 与身份中心前面加上 nginx + ACME。

```bash
bash deploy/bin/gen-keys.sh
cp deploy/docker/.env.prod.example deploy/docker/.env    # 域名、密钥、存储、配额
bash deploy/bin/check-env.sh deploy/docker/.env
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.infra.yml up -d
# 再依次 up docker-compose.account.yml、docker-compose.app.yml
```

每个变量在 `.env.prod.example` 里都有注释；完整步骤、TLS edge profile、备份与运维说明见 [`deploy/README.md`](../../deploy/README.md)。两条流水线均为手动触发：`ci.yml` 负责构建与各项检查，`deploy.yml` 构建四个镜像、推送到 GHCR 并 SSH 部署。

## 文档

| | |
|---|---|
| 需求与设计 | [`docs/PRD.md`](../PRD.md) |
| 架构说明 | [`docs/architecture.md`](../architecture.md) |
| 部署 | [`deploy/README.md`](../../deploy/README.md) |
| 开发脚本 | [`bin/README.md`](../../bin/README.md) |

## 许可证

[MIT](../../LICENSE)
