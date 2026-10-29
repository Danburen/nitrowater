# NitroWater 文档

NitroWater 是一个面向 Minecraft 服主与插件开发者的**多模块平台**：一站式服务器工具箱（MOTD、server.properties、server-icon、启动脚本、代码运行、插件打包等）、插件中心、文档站，以及独立的 **SSO / OIDC 身份中心**。平台自带 AI 助手 NitroBot，用本文档作为检索语料。

## 1. 模块与端口

| 模块 | 职责 | 默认端口 |
|---|---|---|
| `nitrowater-lib` | 通用工具、值类型、响应码 | - |
| `nitrowater-core` | 共享实体、仓储、鉴权服务、安全工具 | - |
| `nitrowater-account` | SSO / OIDC 身份中心（AS/OP）+ 管理台 + 登录页 | `8090` |
| `nitrowater-bff` | BFF / Token Handler、SPA 静态托管、`/api/**` 代理 | `8080` |
| `nitrowater-server` | 业务 API、OAuth2 Resource Server、插件中心、沙箱、AI | `8081` |
| `nitrowater-resource` | 统一资源服务（文件上传/读取/管理、插件 JAR 托管） | `8093` |
| `nitrowater-web` | Vue 3 + TypeScript + Vite + Tailwind 前端 | `5173`（dev） |

## 2. 核心原则

- 浏览器**只持有 HttpOnly 的 `BFFSESSION`**；access/refresh/id token 只存在于 BFF 的服务端会话（Spring Session → Redis）。
- `/api/**` 是唯一的数据命名空间（需登录），`/api/public/**` 有意匿名；`/actuator/**` 与 `/internal/**` 一律拒绝。
- 三个服务各自管理自己的数据库：`nitrowater_account` / `nitrowater_biz` / `nitrowater_resource`，跨库只用逻辑键（`uid`、`uuid`），不建物理外键。
- 后端固定 **Servlet / WebMVC**，不使用 WebFlux。
- 前端 **Tailwind-first**，Element Plus 仅按需用于复杂组件。

## 3. 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 25、Spring Boot 4.1、Spring Security 7、Spring Authorization Server、Spring AI 2.0、JPA + MySQL 8、Flyway、Redis |
| 向量库 | Qdrant（文档检索与长期记忆） |
| 沙箱 | 自托管 Piston（Docker） |
| 存储 | SeaweedFS（S3 兼容）、腾讯 COS、本地文件系统 |
| 前端 | Vue 3、TypeScript、Vite、Tailwind CSS v4、vue-router |
| 文档 | NitroWater 文档是纯 markdown 语料（不建站）；VeloChatX 文档站为 VitePress 产物 |

## 4. 文档地图

| 主题 | 文档 |
|---|---|
| 部署拓扑与调用链 | [系统架构](architecture/overview.md) |
| 登录、会话、Token 刷新、登出 | [OIDC + BFF 认证链路](architecture/oidc-bff.md) |
| 模块边界与依赖规则 | [模块划分](architecture/modules.md) |
| NitroBot（RAG + Agent + 工具） | [AI 助手](architecture/ai-agent.md) |
| 代码运行与 Piston | [沙箱与代码运行](architecture/code-runner.md) |
| 数据库约定与表结构 | [数据模型](data/schema.md) |
| HTTP 接口与 SSE 事件 | [API 参考](api/reference.md) |
| 本地开发与测试 | [本地环境](development/setup.md) |
| 脚本、环境变量、部署 | [部署与运维](operations/deploy.md) |
| 常见故障与陷阱 | [问题排查](troubleshooting.md) |

## 5. 常见入口

| 用途 | 地址 |
|---|---|
| 门户 / SPA | `http://localhost:8080` |
| SSO 身份中心 | `http://localhost:8090` |
| 业务 API | `http://localhost:8081` |
| 资源服务 | `http://localhost:8093` |
| NitroBot | `http://localhost:8080/chat` |
| AI 管理台 | `http://localhost:8080/admin/ai` |

## 6. 术语

| 术语 | 含义 |
|---|---|
| `uid` | account 的数字用户主键（`BIGINT UNSIGNED`），也是 JWT 的 `sub` 与 `uid` claim |
| 资源 `uuid` | resource 服务对外资源键（`CHAR(36)`），跨服务引用使用它 |
| BFF | Backend-for-Frontend，同时承担 OIDC 机密客户端与 Token Handler |
| corpus | 一个文档语料集合（如 `nitrowater`、`velochatx`），检索时按 corpus 过滤 |
| skill | NitroBot 的预置工作流（markdown），模型按需 `loadSkill` |
| Piston | 自托管的编译/运行沙箱服务 |
