# NitroWater

[![GitHub](https://img.shields.io/badge/GitHub-danburen%2Fnitrowater-181717?logo=github)](https://github.com/danburen/nitrowater)
[![Gitee](https://img.shields.io/badge/Gitee-blackwallet%2Fnitrowarer-c71d23?logo=gitee)](https://gitee.com/blackwallet/nitrowarer)
![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![Spring Security](https://img.shields.io/badge/Spring%20Security-7.1-6DB33F)
![License](https://img.shields.io/badge/License-see%20LICENSE-lightgrey)

> 🌐 English | [简体中文](docs/readme/zh.md)

A **multi-module backend platform** with a **composite showcase site** (portal · toolbox · coding playground · plugin center · docs) and a **standalone SSO / OIDC identity center**.

## Features

- **Unified SSO / OIDC** — a standalone identity center (`nitrowater-account`) offering login / register / captcha and an OpenID Connect provider (authorization-code, refresh token, RP-initiated logout), plus an admin console.
- **BFF (Token Handler)** — the browser holds **no token**; a confidential OIDC client keeps access/refresh tokens server-side, auto-refreshes them, and proxies business, resource and plugin-doc traffic.
- **Business API / Resource Server** — validates the SSO-issued JWT against JWKS; backs the problem bank (bilingual problems, tags, problem sets, editorials), submissions and the plugin center.
- **Coding playground** — a backend compile → sandbox → judge service (AC/WA/TLE/RE/CE) driven by self-hosted **Piston**; LeetCode-style function-mode harnesses for Java / Python / C / C++.
- **Resource service** — a standalone `nitrowater-resource` providing pluggable storage (SeaweedFS/S3 · COS · local), presigned access, visibility/ownership rules and an admin file manager; it also hosts plugin JARs.
- **Toolbox** — a single entry point for the 7 migrated `wtools` utilities (Java→TS, ZIP, tree viewer, list-tree, code runner, MP4→GIF, Markdown→Word).
- **Composite showcase site** — portal, toolbox, playground, plugin center and docs under one same-origin front-end.

## Modules

| Module | Role |
|---|---|
| `nitrowater-lib` | Generic utilities and value types. |
| `nitrowater-core` | Shared library: entities, repositories, auth services, security utils. |
| `nitrowater-account` | SSO / OIDC identity center (auth API + OIDC provider + hosted login + admin console). |
| `nitrowater-bff` | BFF / token handler (confidential OIDC client + session + `/api/**` proxy). |
| `nitrowater-server` | Business API + OAuth2 resource server (problems, submissions, judge, plugins, sandbox). |
| `nitrowater-resource` | Resource service (file upload/read/admin + plugin JAR hosting). |
| `nitrowater-web` | Front-end (Vue 3 + TypeScript + Vite + Tailwind). |

## Tech Stack

- **Backend** — Java 25, Spring Boot 4.1.1, Spring Security 7 + Spring Authorization Server 7 (OIDC), JPA + MySQL 8, Redis, Flyway, Jackson 3.
- **Sandbox** — self-hosted [Piston](https://github.com/engineer-man/piston) (Docker) for compile/run/judge.
- **Storage** — self-hosted SeaweedFS (S3-compatible) with optional Tencent COS / local backend.
- **Frontend** — Vue 3 + TypeScript + Vite, **Tailwind CSS v4** (`@tailwindcss/vite`) for styling, vue-router; Element Plus kept available for complex widgets; ESLint (flat config).
- **Docs** — VitePress (`velochatx-docs`).

## Getting Started

Prerequisites: **JDK 25**, **Docker** (for infrastructure & Piston), **MySQL 8**, **Redis**.

```powershell
.\deploy\bin\gen-keys.ps1   # generate JWT keys (first run)
.\bin\start-infra.ps1       # MySQL + Redis + SeaweedFS + Piston
.\bin\start-apps.ps1        # account -> bff / server / resource (add -WithWeb for Vite dev :5173)
```

- Portal / unified entry: http://localhost:8080 (BFF-hosted SPA)
- SSO identity center: http://localhost:8090
- Front-end dev server (optional): http://localhost:5173

See [`bin/README.md`](bin/README.md) for the full script set (start/stop/seed/sync).

## Repository & Docs

| Platform | URL |
|---|---|
| GitHub | https://github.com/danburen/nitrowater |
| Gitee | https://gitee.com/blackwallet/nitrowarer |

- Requirements / design: [`docs/PRD.md`](docs/PRD.md)
- Architecture notes & project understanding: `.ai/PROJECT.md`

## License

See [`LICENSE`](LICENSE).
