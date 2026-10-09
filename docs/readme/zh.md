# NitroWater

[![GitHub](https://img.shields.io/badge/GitHub-danburen%2Fnitrowater-181717?logo=github)](https://github.com/danburen/nitrowater)
[![Gitee](https://img.shields.io/badge/Gitee-blackwallet%2Fnitrowarer-c71d23?logo=gitee)](https://gitee.com/blackwallet/nitrowarer)
![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![Spring Security](https://img.shields.io/badge/Spring%20Security-7.1-6DB33F)
![License](https://img.shields.io/badge/License-see%20LICENSE-lightgrey)

> 🌐 [English](../../README.md) | 简体中文

一个**多模块后端平台**，包含一座**复合展示站**（导航 · 工具箱 · 编程演练场 · 文档）与一个独立的 **SSO / OIDC 身份中心**。

## 基本功能

- **统一 SSO / OIDC** —— 独立身份中心（`nitrowater-account`），提供登录 / 注册 / 验证码，以及 OpenID Connect 提供方（授权码、refresh token、RP-initiated 登出）。
- **BFF（Token Handler）** —— 浏览器**零 token**；confidential OIDC 客户端在服务端持有并自动续期 access/refresh token，并代理业务 API。
- **业务 API / 资源服务器** —— 基于 JWKS 校验 SSO 签发的 JWT，承载工具箱、题目与提交。
- **工具箱** —— 7 个 `wtools` 小工具的统一入口。*（规划中）*
- **编程演练场** —— 后端「编译 → 沙箱 → 评测」服务（AC/WA/TLE/RE/CE）。*（规划中）*
- **复合展示站** —— 门户、工具箱、演练场与文档合一的前端。*（进行中）*

## 模块

| 模块 | 职责 |
|---|---|
| `nitrowater-core` | 共享库：实体、仓库、认证服务、安全工具。 |
| `nitrowater-lib` | 通用工具与值类型。 |
| `nitrowater-account` | SSO / OIDC 身份中心（认证 API + OIDC 提供方 + 托管登录页）。 |
| `nitrowater-bff` | BFF / Token Handler（confidential OIDC 客户端 + 会话 + `/api/**` 代理）。 |
| `nitrowater-server` | 业务 API + OAuth2 资源服务器。 |
| `nitrowater-web` | 前端（Vue 3 + TypeScript + Vite）。 |
| `nitrowater-judge` | 编程演练场评测服务。*（规划中）* |

## 技术栈

- **后端** —— Java 25、Spring Boot 4.1.1、Spring Security 7 + Spring Authorization Server 7（OIDC）、JPA + MySQL 8、Redis、Flyway。
- **前端** —— Vue 3 + TypeScript + Vite、**Tailwind CSS v4**（`@tailwindcss/vite`）负责样式、vue-router；Element Plus 保留用于复杂组件；ESLint（flat config）。
- **文档** —— VitePress（`velochatx-docs`）。

## 快速开始

前置：**JDK 25**、**MySQL 8**、**Redis**。

```powershell
.\deploy\bin\gen-keys.ps1        # 生成 JWT 密钥
.\deploy\bin\start-account.bat   # SSO  -> http://localhost:8090
.\deploy\bin\start-bff.bat       # BFF  -> http://localhost:8080
```

## 仓库与文档

| 平台 | 地址 |
|---|---|
| GitHub | https://github.com/danburen/nitrowater |
| Gitee | https://gitee.com/blackwallet/nitrowarer |

- 需求 / 设计：[`docs/PRD.md`](../PRD.md)
- 英文 README：[`../../README.md`](../../README.md)

## 许可证

见 [`LICENSE`](../../LICENSE)。
