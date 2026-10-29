# NitroWater

[![GitHub](https://img.shields.io/badge/GitHub-danburen%2Fnitrowater-181717?logo=github)](https://github.com/danburen/nitrowater)
[![Gitee](https://img.shields.io/badge/Gitee-blackwallet%2Fnitrowarer-c71d23?logo=gitee)](https://gitee.com/blackwallet/nitrowarer)
![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![Spring Security](https://img.shields.io/badge/Spring%20Security-7.1-6DB33F)
![License](https://img.shields.io/badge/License-see%20LICENSE-lightgrey)

> 🌐 [English](../../README.md) | 简体中文

一个面向 **Minecraft 腐竹与插件 / 服务器社区**的**多模块平台**，包含一套**一站式开服工具箱**（MOTD、server.properties、server-icon、启动脚本）与门户、插件中心、文档，以及独立的 **SSO / OIDC 身份中心**。

## 基本功能

- **统一 SSO / OIDC** —— 独立身份中心（`nitrowater-account`），提供登录 / 注册 / 验证码，以及 OpenID Connect 提供方（授权码、refresh token、RP-initiated 登出），并带管理控制台。
- **BFF（Token Handler）** —— 浏览器**零 token**；confidential OIDC 客户端在服务端持有并自动续期 access/refresh token，并代理业务、资源与插件文档流量。
- **业务 API / 资源服务器** —— 基于 JWKS 校验 SSO 签发的 JWT，承载插件中心与开服 / 服务器工具。
- **代码运行器** —— 登录用户经后端「编译 → 沙箱 → 运行」链路（自托管 **Piston**）执行片段，带单用户/全局并发、有界队列与分钟/日配额；匿名访客仍走浏览器 WASM。
- **资源服务** —— 独立 `nitrowater-resource`，提供可插拔存储（SeaweedFS/S3 · COS · 本地）、presigned 访问、可见性/归属规则与管理端文件管理，并托管插件 JAR。
- **工具箱** —— 11 个工具分两栏：**开服工具在前**（MOTD、server.properties、server-icon、启动脚本），**通用功能在后**（代码运行器、插件 ZIP、插件树、列表树、Java→TS、MP4→GIF、Markdown→Word）。
- **复合展示站** —— 门户、开服工具箱、插件中心与文档合一、同源的前端。

## 模块

| 模块 | 职责 |
|---|---|
| `nitrowater-lib` | 通用工具与值类型。 |
| `nitrowater-core` | 共享库：实体、仓库、认证服务、安全工具。 |
| `nitrowater-account` | SSO / OIDC 身份中心（认证 API + OIDC 提供方 + 托管登录页 + 管理控制台）。 |
| `nitrowater-bff` | BFF / Token Handler（confidential OIDC 客户端 + 会话 + `/api/**` 代理）。 |
| `nitrowater-server` | 业务 API + OAuth2 资源服务器（插件中心、服务器工具、沙箱运行器）。 |
| `nitrowater-resource` | 资源服务（文件上传/读取/管理 + 插件 JAR 托管）。 |
| `nitrowater-web` | 前端（Vue 3 + TypeScript + Vite + Tailwind）。 |

## 技术栈

- **后端** —— Java 25、Spring Boot 4.1.1、Spring Security 7 + Spring Authorization Server 7（OIDC）、JPA + MySQL 8、Redis、Flyway、Jackson 3。
- **沙箱** —— 自托管 [Piston](https://github.com/engineer-man/piston)（Docker），负责编译/运行。
- **存储** —— 自托管 SeaweedFS（S3 兼容）+ 可选腾讯 COS / 本地后端。
- **前端** —— Vue 3 + TypeScript + Vite、**Tailwind CSS v4**（`@tailwindcss/vite`）负责样式、vue-router；Element Plus 保留用于复杂组件；ESLint（flat config）。
- **文档** —— VitePress（`velochatx-docs`）。

## 快速开始

前置：**JDK 25**、**Docker**（用于基础设施与 Piston）、**MySQL 8**、**Redis**。

```powershell
.\deploy\bin\gen-keys.ps1   # 首次：生成 JWT 密钥
.\bin\start-all.ps1         # 构建（bootJar）+ infra + 后端服务（account/bff/server/resource）
```

- 前端由你自己启动：`cd nitrowater-web; npm run dev` → http://localhost:5173
  （或 `.\bin\start-all.ps1 -BuildWeb` 让 BFF 在 :8080 直接托管构建版）
- 门户 / 统一入口：http://localhost:8080 （BFF 托管的 SPA）
- SSO 身份中心：http://localhost:8090

启停参数与 `bin/dev/` 数据脚本（播种 / 重置 / 同步）见 [`bin/README.md`](../../bin/README.md)。

## 仓库与文档

| 平台 | 地址 |
|---|---|
| GitHub | https://github.com/danburen/nitrowater |
| Gitee | https://gitee.com/blackwallet/nitrowarer |

- 需求 / 设计：[`docs/PRD.md`](../PRD.md)
- 架构笔记与项目理解：`.ai/PROJECT.md`
- 英文 README：[`../../README.md`](../../README.md)

## 许可证

见 [`LICENSE`](../../LICENSE)。
