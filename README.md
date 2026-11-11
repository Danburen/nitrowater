# NitroWater

[![GitHub](https://img.shields.io/badge/GitHub-danburen%2Fnitrowater-181717?logo=github)](https://github.com/danburen/nitrowater)
[![Gitee](https://img.shields.io/badge/Gitee-blackwallet%2Fnitrowarer-c71d23?logo=gitee)](https://gitee.com/blackwallet/nitrowarer)
![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![Spring Security](https://img.shields.io/badge/Spring%20Security-7.1-6DB33F)
![License](https://img.shields.io/badge/License-see%20LICENSE-lightgrey)

> 🌐 English | [简体中文](docs/readme/zh.md)

A **multi-module platform** for Minecraft server owners and the plugin / server community: a **one-stop server-owner toolbox** (MOTD, server.properties, server-icon, start scripts) plus portal, plugin center and docs, with a **standalone SSO / OIDC identity center**.

## Features

- **Unified SSO / OIDC** — a standalone identity center (`nitrowater-account`) offering login / register / captcha and an OpenID Connect provider (authorization-code, refresh token, RP-initiated logout), plus an admin console.
- **BFF (Token Handler)** — the browser holds **no token**; a confidential OIDC client keeps access/refresh tokens server-side, auto-refreshes them, and proxies business, resource and plugin-doc traffic.
- **Business API / Resource Server** — validates the SSO-issued JWT against JWKS; backs the plugin center and the server-oriented toolbox.
- **Code runner** — logged-in users run snippets through a backend compile → sandbox → run pipeline driven by self-hosted **Piston**, with per-user / global concurrency, a bounded queue and per-minute / per-day quotas; anonymous visitors keep the browser-WASM runner.
- **Resource service** — a standalone `nitrowater-resource` providing pluggable storage (SeaweedFS/S3 · COS · local), presigned access, visibility/ownership rules and an admin file manager; it also hosts plugin JARs.
- **AI agent assistant (NitroBot)** — a Spring AI agent over the VeloChatX / NitroWater docs: markdown sources are chunked, embedded and stored in a **Qdrant** vector index, and the model decides when to call tools (`searchDocs`, `loadSkill`, optional `webSearch`, `runCode`, `saveMemory`). Answers stream through the BFF with per-tool step cards, clickable citations, user-selectable markdown **skills**, and an admin catalog at `/admin/ai` (index status, skills, tools, usage).
- **Toolbox** — 11 utilities split into two sections: **Minecraft server tools** first (MOTD, server.properties, server-icon, start scripts), then **general tools** (code runner, plugin ZIP, plugin tree, list-tree, Java→TS, MP4→GIF, Markdown→Word).
- **Composite showcase site** — portal, toolbox, plugin center and docs under one same-origin front-end.

## Modules

| Module | Role |
|---|---|
| `nitrowater-lib` | Generic utilities and value types. |
| `nitrowater-core` | Shared library: entities, repositories, auth/security services, plus the Spring MVC/Security JSON plumbing (`cn.nitrowater.core.web`: `ApiResponse` advice, global exception handler, 401/403 writers). Its data stack is `compileOnly` so the BFF can reuse it without a DataSource. |
| `nitrowater-account` | SSO / OIDC identity center (auth API + OIDC provider + hosted login + admin console). |
| `nitrowater-bff` | BFF / token handler (confidential OIDC client + session + `/api/**` proxy). |
| `nitrowater-server` | Business API + OAuth2 resource server (plugins, server-oriented toolbox, sandbox runner). |
| `nitrowater-resource` | Resource service (file upload/read/admin + plugin JAR hosting). |
| `nitrowater-web` | Front-end (Vue 3 + TypeScript + Vite + Tailwind). |

## Tech Stack

- **Backend** — Java 25, Spring Boot 4.1.1, Spring Security 7 + Spring Authorization Server 7 (OIDC), JPA + MySQL 8, Redis, Flyway, Jackson 3.
- **AI** — Spring AI 2.0 (OpenAI-compatible chat + embeddings) with a Qdrant vector store.
- **Sandbox** — self-hosted [Piston](https://github.com/engineer-man/piston) (Docker) for compile/run.
- **Storage** — self-hosted SeaweedFS (S3-compatible) with optional Tencent COS / local backend.
- **Frontend** — Vue 3 + TypeScript + Vite, **Tailwind CSS v4** (`@tailwindcss/vite`) for styling, vue-router; Element Plus kept available for complex widgets; ESLint (flat config).
- **Docs** — VeloChatX ships a VitePress site (`velochatx-docs`); NitroWater docs are a markdown-only corpus for the assistant.

## Getting Started

Prerequisites: **JDK 25**, **Node.js 24 + pnpm 11** (for the `nitrowater-account` admin/auth SPAs),
**Docker** (for infrastructure & Piston), **MySQL 8**, **Redis**.

```powershell
.\deploy\bin\gen-keys.ps1   # generate JWT keys (first run)
.\bin\start-all.ps1         # build (bootJar) + infra + backend services (account/bff/server/resource)
```

- Frontend is started by you: `cd nitrowater-web; npm run dev` → http://localhost:5173
  (or `.\bin\start-all.ps1 -BuildWeb` to serve the built SPA on :8080)
- Account admin/auth SPAs are a pnpm workspace under `nitrowater-account/`; the Gradle build
  invokes `pnpm install` + filtered builds automatically (Docker builds them in node stages).
- Portal / unified entry: http://localhost:8080 (BFF-hosted SPA)
- SSO identity center: http://localhost:8090

See [`bin/README.md`](bin/README.md) for start/stop flags and the `bin/dev/` data scripts (seed / reset / sync).

## Repository & Docs

| Platform | URL |
|---|---|
| GitHub | https://github.com/danburen/nitrowater |
| Gitee | https://gitee.com/blackwallet/nitrowarer |

- Requirements / design: [`docs/PRD.md`](docs/PRD.md)
- Deployment (Docker, single host): [`deploy/README.md`](deploy/README.md)
- Architecture notes & project understanding: `.ai/PROJECT.md`

## License

See [`LICENSE`](LICENSE).
