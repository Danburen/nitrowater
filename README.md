# NitroWater

[![GitHub](https://img.shields.io/badge/GitHub-danburen%2Fnitrowater-181717?logo=github)](https://github.com/danburen/nitrowater)
[![Gitee](https://img.shields.io/badge/Gitee-blackwallet%2Fnitrowarer-c71d23?logo=gitee)](https://gitee.com/blackwallet/nitrowarer)
![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![Spring Security](https://img.shields.io/badge/Spring%20Security-7.1-6DB33F)
![License](https://img.shields.io/badge/License-see%20LICENSE-lightgrey)

> 🌐 English | [简体中文](docs/readme/zh.md)

A **multi-project backend platform** with a **composite showcase site** (navigation · toolbox · coding playground · docs) and a **standalone SSO / OIDC identity center**.

## Features

- **Unified SSO / OIDC** — a standalone identity center (`nitrowater-account`) offering login / register / captcha and an OpenID Connect provider (authorization-code, refresh token, RP-initiated logout).
- **BFF (Token Handler)** — the browser holds **no token**; a confidential OIDC client keeps access/refresh tokens server-side, auto-refreshes them, and proxies business APIs.
- **Business API / Resource Server** — validates the SSO-issued JWT against JWKS; backs the toolbox, problems and submissions.
- **Toolbox** — a single entry point for the 7 migrated `wtools` utilities. *(planned)*
- **Coding playground** — a backend compile → sandbox → judge service (AC/WA/TLE/RE/CE). *(planned)*
- **Composite showcase site** — portal, toolbox, playground and docs under one front-end. *(in progress)*

## Modules

| Module | Role |
|---|---|
| `nitrowater-core` | Shared library: entities, repositories, auth services, security utils. |
| `nitrowater-lib` | Generic utilities and value types. |
| `nitrowater-account` | SSO / OIDC identity center (auth API + OIDC provider + hosted login). |
| `nitrowater-bff` | BFF / token handler (confidential OIDC client + session + `/api/**` proxy). |
| `nitrowater-server` | Business API + OAuth2 resource server. |
| `nitrowater-web` | Front-end (Vue 3 + TypeScript + Vite). |
| `nitrowater-judge` | Coding playground judge service. *(planned)* |

## Tech Stack

- **Backend** — Java 25, Spring Boot 4.1.1, Spring Security 7 + Spring Authorization Server 7 (OIDC), JPA + MySQL 8, Redis, Flyway.
- **Frontend** — Vue 3 + TypeScript + Vite, **Tailwind CSS v4** (`@tailwindcss/vite`) for styling, vue-router; Element Plus kept available for complex widgets; ESLint (flat config).
- **Docs** — VitePress (`velochatx-docs`).

## Getting Started

Prerequisites: **JDK 25**, **MySQL 8**, **Redis**.

```powershell
.\deploy\bin\gen-keys.ps1        # generate JWT keys
.\deploy\bin\start-account.bat   # SSO  -> http://localhost:8090
.\deploy\bin\start-bff.bat       # BFF  -> http://localhost:8080
```

## Repository & Docs

| Platform | URL |
|---|---|
| GitHub | https://github.com/danburen/nitrowater |
| Gitee | https://gitee.com/blackwallet/nitrowarer |

- Requirements / design: [`docs/PRD.md`](docs/PRD.md)

## License

See [`LICENSE`](LICENSE).
