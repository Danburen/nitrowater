# NitroWater

[![GitHub](https://img.shields.io/badge/GitHub-danburen%2Fnitrowater-181717?logo=github)](https://github.com/danburen/nitrowater)
[![Gitee](https://img.shields.io/badge/Gitee-blackwallet%2Fnitrowater-c71d23?logo=gitee)](https://gitee.com/blackwallet/nitrowater)
![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![Vue](https://img.shields.io/badge/Vue-3-42b883)
![License](https://img.shields.io/badge/License-MIT-blue)

English | [简体中文](docs/readme/zh.md)

**One platform for Minecraft server owners** — a server toolbox, a plugin centre and an AI assistant, behind a self-hosted SSO / OIDC identity centre. The browser never holds a token: the BFF keeps the credentials and proxies every API call.

- 💪 **One account, one door** — sign in once for the portal, the toolbox, the plugin centre and the chat.
- 🔐 **Tokens stay on the server** — confidential OIDC client plus an HttpOnly session; `/api/**` is proxied by the BFF with a bearer token.
- 🧰 **Eleven browser tools** — MOTD, `server.properties`, `server-icon`, start scripts, archives, tree views, Java→TypeScript, MP4→GIF, Markdown→Word and a code runner.
- 🤖 **NitroBot** — retrieval over your own indexed documentation, web search, sandboxed code execution and long-term memory.
- 🧱 **Boring infrastructure** — Gradle multi-module backend, Vue 3 SPA, Flyway-owned schemas, Docker Compose deploy.

## 🧭 What's NitroWater

Running a Minecraft server means juggling a dozen unrelated sites — MOTD, `server.properties`, icons, start scripts, plugins, documentation. NitroWater pulls that daily work into one same-origin platform with one account.

### Module map

| Module | Tech | Role |
|---|---|---|
| `nitrowater-web` | Vue 3, TypeScript, Vite, Tailwind | Portal, toolbox, plugin centre, chat, admin console |
| `nitrowater-bff` | Spring Boot, Spring Security OAuth2 client | Login, session, `/api/**` proxy, serves the SPA |
| `nitrowater-account` | Spring Authorization Server, Vue 3 workspace | SSO / OIDC identity centre |
| `nitrowater-server` | Spring Boot, Spring AI, JPA + Flyway | Plugin centre, AI agent, code runner |
| `nitrowater-resource` | Spring Boot, SeaweedFS / S3 | Files, uploads, plugin JAR hosting |
| `nitrowater-core` / `nitrowater-lib` | JPA, framework-free utilities | Shared persistence and security plumbing |

The browser only talks to the BFF — access and refresh tokens never leave the server.

## ✨ Features

### Identity and access

- Self-hosted authorization server: authorization code + PKCE, refresh tokens, RP-initiated logout, JWKS and userinfo.
- Login, registration, CAPTCHA and account switches, with `SUPER_ADMIN` / `ADMIN` / `USER` roles carried in the JWT.

### Server toolbox

Eleven tools, most of them running entirely in the browser (WebAssembly / workers), so files never leave the machine:

- **MOTD editor** — `&` / `§` colour codes, live two-line preview, export as legacy, `server.properties` or JSON.
- **`server.properties` generator** — grouped form, presets, import of an existing file, unknown keys preserved, live validation.
- **`server-icon` generator** and **start scripts** for the usual JVM flags.
- **Archives and trees** — ZIP create/extract, directory tree, ListTree chart rendering.
- **Converters** — Java→TypeScript, MP4→GIF, Markdown→Word.
- **Code runner** — JavaScript, TypeScript, Python and Lua in a worker sandbox, with the server-side Piston sandbox for judging.

### Plugin centre

- Browse the catalogue with versions, metadata and documentation.
- Author documentation is a VitePress site under `deploy/plugin-docs`, served same-origin through the BFF at `/plugin/**`.
- JARs and assets are stored by the resource service (SeaweedFS / S3, Tencent COS or local disk), uploaded through signed URLs.

### NitroBot

- Answers from an indexed corpus in Qdrant, with `[n]` citations back to the source document.
- Tools are first-class: `searchDocs`, `loadSkill`, `webSearch`, `runCode` and `calculate`, each returning a typed `ToolResponse` that the UI renders as a step timeline.
- Streaming `step` / `delta` SSE events, per-user quotas, token budgets, moderation, agent traces and long-term memory.

### Admin console

- Portal side (`/admin`): plugins, resources, AI runtime settings, content moderation, per-user usage, index status, skills and tools.
- Account side (`/admin` on `:8090`): users, roles and registered OIDC clients.

## 🚀 Getting Started

### Prerequisites

| Requirement | Notes |
|---|---|
| JDK 25 | Backend toolchain |
| Node 24 + pnpm 11 | `nitrowater-web` uses npm; `nitrowater-account` is a pnpm workspace |
| Docker | Piston, SeaweedFS, Qdrant and the plugin docs site |
| MySQL 8 and Redis | Local services on `3306` / `6379` (`root` / `123456` by default) |

### Three commands

```powershell
.\deploy\bin\gen-keys.ps1      # once: generate the JWT signing keys
.\bin\start-all.ps1            # build the jars, start infra, run the four services
cd nitrowater-web; npm run dev # http://localhost:5173, proxies /api to the BFF
```

| Where | URL |
|---|---|
| Portal (SPA served by the BFF) | <http://localhost:8080> |
| Frontend dev server | <http://localhost:5173> |
| Identity centre | <http://localhost:8090> |
| Direct links | `/tools`, `/plugins`, `/chat` |

`start-all.ps1` accepts `-SkipBuild` (restart existing jars), `-SkipInfra`, `-Hidden` (run in the background, logs to `bin\.run\logs\<module>.out.log`), `-BuildWeb` (serve the built SPA from the BFF) and `-NoRestart`; `stop-all.ps1` stops everything. All flags and data scripts are documented in [`bin/README.md`](bin/README.md).

Development data lives entirely in scripts, never in migrations:

```powershell
.\bin\dev\dev-seed.ps1                        # testadmin / testuser1 / testuser, password Passw0rd123
.\bin\dev\reset-db.ps1 -Db biz -Force -Restart # drop a schema, let Flyway replay it from V1
```

### Working on a single service

Run it from the Gradle wrapper or your IDE while the rest keeps running:

```powershell
.\gradlew.bat :nitrowater-server:bootRun
```

For breakpoints, launch the jar with JDWP and attach the IDE to port `5005`:

```powershell
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar nitrowater-server\build\libs\app.jar
```

Frontend changes hot-reload through Vite; `npm run dev` must be running because the SPA only talks to the BFF through the dev proxy. A backend that refuses to start after a schema change is almost always a stale Flyway checksum — reset the database as shown above.

## 🔨 How to Build

### Backend

```powershell
.\gradlew.bat build                        # all modules: compile, test, JaCoCo reports
.\gradlew.bat :nitrowater-server:bootJar   # one module → build/libs/app.jar
```

The account module builds `admin-ui` and `auth-ui` on its own; `-PskipAdminUi` / `-PskipAuthUi` skip them when you only need the jars (the Dockerfile builds the SPAs in a separate node stage anyway).

### Frontend

```powershell
cd nitrowater-web
npm ci
npm run lint        # ESLint
npm run build       # vue-tsc type check + Vite build → dist/, served by the BFF
```

```powershell
cd nitrowater-account
pnpm install
pnpm build          # admin-ui + auth-ui
pnpm dev:admin      # http://localhost:5174
pnpm dev:auth       # http://localhost:5175
```

### Docker images

One multi-stage `Dockerfile` carries both the SPAs and every service; pick a target:

```bash
docker build --target bff -t nitrowater-bff .
docker build --target account -t nitrowater-account .
docker build --target server -t nitrowater-server .
docker build --target resource -t nitrowater-resource .
```

### Before you push

```powershell
.\gradlew.bat test                     # all modules
.\gradlew.bat :nitrowater-server:test  # a single module
cd nitrowater-web; npm run lint; npm run build
cd nitrowater-account; pnpm lint; pnpm build
```

Coverage lands in `<module>/build/reports/jacoco/test/html/index.html` and, aggregated, in `build/reports/jacoco/root/html/index.html`. Both pipelines are manual: `ci.yml` builds and runs the checks, `deploy.yml` builds the four images, pushes them to GHCR and rolls them out over SSH.

## 📦 Deployment

A single Linux host with Docker. Three compose files share one network and must be brought up in order (`infra` → `account` → `app`); an optional `edge` profile adds nginx + ACME in front of the BFF and the identity centre.

```bash
bash deploy/bin/gen-keys.sh
cp deploy/docker/.env.prod.example deploy/docker/.env   # domains, secrets, storage, quotas
bash deploy/bin/check-env.sh deploy/docker/.env
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.infra.yml up -d
# then docker-compose.account.yml, then docker-compose.app.yml
```

Every variable is annotated in `.env.prod.example`; the full walkthrough, TLS edge profile, backup and operations notes are in [`deploy/README.md`](deploy/README.md).

## 📚 Documentation

| | |
|---|---|
| Requirements and design | [`docs/PRD.md`](docs/PRD.md) |
| Architecture | [`docs/architecture.md`](docs/architecture.md) |
| Deployment | [`deploy/README.md`](deploy/README.md) |
| Developer scripts | [`bin/README.md`](bin/README.md) |
| Engineering conventions | [`AGENTS.md`](AGENTS.md) |

## 🤝 Contribution

Issues and pull requests are welcome — in English or Chinese, whichever is easier for you.

1. Fork the repository and branch off `main` (`feat/…`, `fix/…`, `docs/…`).
2. Follow [Conventional Commits](https://www.conventionalcommits.org/) loosely — `type(scope): summary` is all we need (`feat(agent): add calculate tool`); no need to match the spec word for word. One concern per pull request.
3. Follow the conventions in [`AGENTS.md`](AGENTS.md): `TIMESTAMP(3)` columns, integer-coded enums, `Specification`-based list queries, `ApiResponse<T>` JSON contracts, API calls confined to `nitrowater-web/src/api/`.
4. Run the checks listed under [Before you push](#before-you-push) and keep the existing tests green.
5. Never commit secrets, keys or credentials — `deploy/bin/gen-keys.*` generates them locally, environment variables provide them in production.

Found a bug or have a feature request? Open an issue with the commit/version, the module involved and the steps to reproduce.

## 📄 License

[MIT](LICENSE)
