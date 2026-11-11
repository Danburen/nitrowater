# NitroWater

[![GitHub](https://img.shields.io/badge/GitHub-danburen%2Fnitrowater-181717?logo=github)](https://github.com/danburen/nitrowater)
[![Gitee](https://img.shields.io/badge/Gitee-blackwallet%2Fnitrowater-c71d23?logo=gitee)](https://gitee.com/blackwallet/nitrowater)
![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![License](https://img.shields.io/badge/License-MIT-blue)

English | [简体中文](docs/readme/zh.md)

A multi-module platform for Minecraft server owners: a one-stop server toolbox, a plugin centre and an AI assistant, behind a standalone SSO / OIDC identity centre. The browser never holds a token — a BFF keeps the credentials and proxies every API call.

## Modules

| Module | Role |
|---|---|
| `nitrowater-web` | Vue 3 + Vite SPA: portal, toolbox, plugin centre, NitroBot chat |
| `nitrowater-bff` | Confidential OIDC client, session, `/api/**` proxy, serves the SPA |
| `nitrowater-account` | SSO / OIDC identity centre with its own login and admin pages |
| `nitrowater-server` | Business API: plugin centre, AI agent, sandboxed code runner |
| `nitrowater-resource` | File service and plugin JAR hosting |
| `nitrowater-core`, `nitrowater-lib` | Shared persistence / security plumbing, framework-free utilities |

The toolbox ships MOTD, `server.properties`, `server-icon` and start scripts, plus a code runner, ZIP, tree views, Java→TypeScript, MP4→GIF and Markdown→Word. NitroBot answers from an indexed documentation corpus (Qdrant), can search the web, run code and keep long-term memory.

## Run locally

You need JDK 25, Node 24 with pnpm 11, Docker (Piston, SeaweedFS, Qdrant, docs), MySQL 8 and Redis.

```powershell
.\deploy\bin\gen-keys.ps1      # once: JWT signing keys
.\bin\start-all.ps1            # build jars, start infra, run the four services
cd nitrowater-web; npm run dev # http://localhost:5173, proxies /api to the BFF
```

Portal is on `:8080`, the identity centre on `:8090` (direct links: `/tools`, `/plugins`, `/chat`). `start-all.ps1 -SkipBuild` restarts existing jars, `-Hidden` runs services in the background, `-BuildWeb` serves the built SPA from the BFF, and `stop-all.ps1` stops everything. Flags and data scripts are listed in [`bin/README.md`](bin/README.md).

Development data lives entirely in scripts, never in migrations: `bin/dev/dev-seed.ps1` creates test users and sample resources (`testadmin` / `testuser1` / `testuser`, password `Passw0rd123`), `bin/dev/reset-db.ps1` drops a database and lets Flyway replay it from `V1`.

## Debugging

Services started by `start-all.ps1` keep a console window with their Spring log; with `-Hidden` the same output goes to `bin\.run\logs\<module>.out.log`. To iterate on one service, run it from the Gradle wrapper or your IDE:

```powershell
.\gradlew.bat :nitrowater-server:bootRun
```

For breakpoints, launch the jar with JDWP and attach the IDE to port 5005:

```powershell
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar nitrowater-server\build\libs\app.jar
```

Checks before pushing:

```powershell
.\gradlew.bat test                     # all modules
.\gradlew.bat :nitrowater-server:test  # a single module
cd nitrowater-web; npm run lint; npm run build
```

Frontend changes hot-reload through Vite; `npm run dev` must be running because the SPA only talks to the BFF through the dev proxy. Database problems are almost always a stale checksum — `bin/dev/reset-db.ps1 -Db biz -Force -Restart` drops the schema and replays the migrations.

## Deployment

A single Linux host with Docker. Three compose files share one network and must be brought up in order (`infra` → `account` → `app`); an optional `edge` profile adds nginx + ACME in front of the BFF and the identity centre.

```bash
bash deploy/bin/gen-keys.sh
cp deploy/docker/.env.prod.example deploy/docker/.env    # domains, secrets, storage, quotas
bash deploy/bin/check-env.sh deploy/docker/.env
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.infra.yml up -d
# then docker-compose.account.yml, then docker-compose.app.yml
```

Every variable is annotated in `.env.prod.example`; the full walkthrough, TLS edge profile, backup and operations notes are in [`deploy/README.md`](deploy/README.md). Both pipelines are manual: `ci.yml` builds and runs the checks, `deploy.yml` builds the four images, pushes them to GHCR and rolls them out over SSH.

## Documentation

| | |
|---|---|
| Requirements and design | [`docs/PRD.md`](docs/PRD.md) |
| Architecture | [`docs/architecture.md`](docs/architecture.md) |
| Deployment | [`deploy/README.md`](deploy/README.md) |
| Developer scripts | [`bin/README.md`](bin/README.md) |

## License

[MIT](LICENSE)
