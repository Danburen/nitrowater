# NitroWater

[![GitHub](https://img.shields.io/badge/GitHub-danburen%2Fnitrowater-181717?logo=github)](https://github.com/danburen/nitrowater)
[![Gitee](https://img.shields.io/badge/Gitee-blackwallet%2Fnitrowarer-c71d23?logo=gitee)](https://gitee.com/blackwallet/nitrowarer)
![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![Spring Security](https://img.shields.io/badge/Spring%20Security-7.1-6DB33F)
![License](https://img.shields.io/badge/License-see%20LICENSE-lightgrey)

> 🌐 [English](../../README.md) | 简体中文

一个**多模块后端平台**，包含一座**复合展示站**（门户 · 工具箱 · 编程演练场 · 插件中心 · 文档）与一个独立的 **SSO / OIDC 身份中心**。

## 基本功能

- **统一 SSO / OIDC** —— 独立身份中心（`nitrowater-account`），提供登录 / 注册 / 验证码，以及 OpenID Connect 提供方（授权码、refresh token、RP-initiated 登出），并带管理控制台。
- **BFF（Token Handler）** —— 浏览器**零 token**；confidential OIDC 客户端在服务端持有并自动续期 access/refresh token，并代理业务、资源与插件文档流量。
- **业务 API / 资源服务器** —— 基于 JWKS 校验 SSO 签发的 JWT，承载题库（双语题目、标签、题单、题解）、提交与插件中心。
- **编程演练场** —— 后端「编译 → 沙箱 → 评测」服务（AC/WA/TLE/RE/CE），由自托管的 **Piston** 驱动；提供 LeetCode 风格的函数式 harness（Java / Python / C / C++）。
- **资源服务** —— 独立 `nitrowater-resource`，提供可插拔存储（SeaweedFS/S3 · COS · 本地）、presigned 访问、可见性/归属规则与管理端文件管理，并托管插件 JAR。
- **工具箱** —— 7 个 `wtools` 小工具的统一入口（Java→TS、ZIP、树形可视化、列表树、代码运行器、MP4→GIF、Markdown→Word）。
- **复合展示站** —— 门户、工具箱、演练场、插件中心与文档合一、同源的前端。

## 模块

| 模块 | 职责 |
|---|---|
| `nitrowater-lib` | 通用工具与值类型。 |
| `nitrowater-core` | 共享库：实体、仓库、认证服务、安全工具。 |
| `nitrowater-account` | SSO / OIDC 身份中心（认证 API + OIDC 提供方 + 托管登录页 + 管理控制台）。 |
| `nitrowater-bff` | BFF / Token Handler（confidential OIDC 客户端 + 会话 + `/api/**` 代理）。 |
| `nitrowater-server` | 业务 API + OAuth2 资源服务器（题库、提交、判题、插件、沙箱）。 |
| `nitrowater-resource` | 资源服务（文件上传/读取/管理 + 插件 JAR 托管）。 |
| `nitrowater-web` | 前端（Vue 3 + TypeScript + Vite + Tailwind）。 |

## 技术栈

- **后端** —— Java 25、Spring Boot 4.1.1、Spring Security 7 + Spring Authorization Server 7（OIDC）、JPA + MySQL 8、Redis、Flyway、Jackson 3。
- **沙箱** —— 自托管 [Piston](https://github.com/engineer-man/piston)（Docker），负责编译/运行/评测。
- **存储** —— 自托管 SeaweedFS（S3 兼容）+ 可选腾讯 COS / 本地后端。
- **前端** —— Vue 3 + TypeScript + Vite、**Tailwind CSS v4**（`@tailwindcss/vite`）负责样式、vue-router；Element Plus 保留用于复杂组件；ESLint（flat config）。
- **文档** —— VitePress（`velochatx-docs`）。

## 快速开始

前置：**JDK 25**、**Docker**（用于基础设施与 Piston）、**MySQL 8**、**Redis**。

```powershell
.\deploy\bin\gen-keys.ps1   # 首次：生成 JWT 密钥
.\bin\start-infra.ps1       # MySQL + Redis + SeaweedFS + Piston
.\bin\start-apps.ps1        # account -> bff / server / resource（加 -WithWeb 起 Vite dev :5173）
```

- 门户 / 统一入口：http://localhost:8080 （BFF 托管的 SPA）
- SSO 身份中心：http://localhost:8090
- 前端开发服务器（可选）：http://localhost:5173

完整脚本清单（启停 / 播种 / 同步）见 [`bin/README.md`](../../bin/README.md)。

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
