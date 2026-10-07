# NitroWater

[![GitHub](https://img.shields.io/badge/GitHub-danburen%2Fnitrowater-181717?logo=github)](https://github.com/danburen/nitrowater)
[![Gitee](https://img.shields.io/badge/Gitee-blackwallet%2Fnitrowarer-c71d23?logo=gitee)](https://gitee.com/blackwallet/nitrowarer)
![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![Spring Security](https://img.shields.io/badge/Spring%20Security-7.1-6DB33F)
![License](https://img.shields.io/badge/License-see%20LICENSE-lightgrey)

> 🌐 **English** | [简体中文](docs/readme/zh.md)

A **multi-project backend platform** with a **composite showcase site** (navigation · toolbox · coding playground · docs) and a **standalone SSO / OIDC identity center** used to migrate and unify authentication across the WaterFun ecosystem.

---

## What is NitroWater?

NitroWater is a new Gradle multi-module project that consolidates four separate needs into one platform:

1. **Toolbox integration** — merge the standalone `wtools` front-end (7 tools) into a single unified entry point.
2. **Backend-powered coding playground** — replace browser-WASM judging with a real compile / sandbox-run / judge service.
3. **Unified SSO** — extract WaterFun's login/registration into an independent authentication center (`nitrowater-account`), enabling single sign-on across projects.
4. **Composite showcase site** — integrate the VeloChatX docs site (`velochatx-docs`, VitePress) alongside the toolbox and playground, with SSO applied site-wide.

The SSO / OIDC identity center (`nitrowater-account`) is the current focus and the most mature subsystem.

## Goals

| # | Goal | Acceptance |
|---|---|---|
| G1 | Solid project baseline | `gradlew build` green across all modules |
| G2 | Merge `wtools` | All 7 tools usable inside `nitrowater-web` |
| G3 | Playground backend | submit → compile → sandbox run → judge (AC/WA/TLE/RE/CE) |
| G4 | Site-wide SSO | log in once, access other integrated projects without re-login |
| G5 | Extract auth from WaterFun | login/register/captcha/token issued by the SSO service |
| G6 | Composite showcase | integrate `velochatx-docs` output under `/docs/**` |

## Modules

| Module | Role | Status |
|---|---|---|
| `nitrowater-core` | Shared library (entities, repositories, auth services, security utils). No standalone deployment. | ✅ auth core migrated |
| `nitrowater-account` | **SSO / OIDC identity center** — login/register API + OIDC Provider + hosted login page | ✅ Phase 1 done · 🚧 Phase 2 OIDC skeleton |
| `nitrowater-server` | Business API — toolbox backend, problem/submission management, static hosting, Resource Server | ⏳ skeleton only |
| `nitrowater-web` | Front-end (Vue 3 + TypeScript + Vite) — navigation portal, toolbox, playground, docs channel, SSO login | ⏳ scaffold (not wired to Gradle) |
| `nitrowater-judge` | Coding playground judge service (compile → sandbox → judge) | ⏳ planned (module not created) |

## Status

### ✅ Completed

- **Project baseline (M0)** — Gradle multi-module (`core` / `account` / `server`), Java 25 + Spring Boot 4.1.1, `gradlew build` green; Gradle wrapper timeout and dangling `waterfun-core` dependencies fixed.
- **Auth core migration** — WaterFun's `common-lib` + `service-core` auth slice + `exception` tree (~203 files) migrated into `nitrowater-core` (`cn.nitrowater.core.lib`), with business coupling removed (`TODO(SSO)` markers). `core` is a **pure library** (`bootJar` disabled).
- **Phase 1 — independent SSO service** (`nitrowater-account`) — the `/api/auth/**` contract is unchanged from WaterFun (zero front-end / gateway change). End-to-end smoke passed:
  `captcha → send-code → register → /account → 401 guard → refresh → logout`.
  Phase 1 deliberately does **not** use Spring Security: it ships a self-built dual-token flow (RS256 JWT + RT HttpOnly cookie) plus a `LocalAuthContextFilter` (AT → `UserCtxHolder`) and a 401 guard.
- **SSO database** — MySQL database `nitrowater` with **6 tables** created by a single Flyway baseline `V1__sso_baseline.sql`: `user`, `user_data`, `user_data_archive`, `account_audit_log`, `encryption_data_key`, and `sso_identity` (reserved for third-party login: QQ / WeChat / GitHub).
- **CORS & cookies** — `CorsConfig` with `allowedOriginPatterns` whitelist + `allowCredentials=true` (same-domain `*.nitrowater.cn` and cross-domain `waterfun.top`); cookie `SameSite` / `Secure` made configurable via `app.cookie.*`.
- **Ops tooling** (`deploy/`) — start Redis/account, env self-check, key generation, SSO DB reset, MySQL backup, and interactive auth smoke.
- **Phase 2 (OIDC) skeleton** — Spring Security 7.1 + Spring Authorization Server 7.1 wired up:
  - Authorization Server chain (Order 1) + default chain (Order 2), JWKS reusing the core RSA key pair;
  - `SsoAuthenticationProvider` delegates authentication to the existing `LoginService` (captcha / lockout / DEK reused), with `principal.name = uid`;
  - Hosted SSO login page at `/login`;
  - In-memory `RegisteredClient` for `nitrowater-web` (public client, PKCE, auth-code + refresh-token);
  - Verified endpoints: `/.well-known/openid-configuration` → 200, `/login` → 200, `/api/auth/captcha` → 200.

### 🚧 In Progress / Unfinished

**Phase 2 — complete real OIDC single sign-on** (next milestone):

- [ ] **Token customizer** — formalize `sub = uid` and add custom claims (`jti`, `did`) to access/id tokens.
- [ ] **`oidc_client` table** — replace the in-memory `RegisteredClientRepository` with a JDBC-backed one; register clients per integrating project (redirect-URI whitelist, PKCE enforced).
- [ ] **JDBC authorization/session storage** — persist `OAuth2Authorization` / consent (currently in-memory) so tokens survive restarts.
- [ ] **`nitrowater-web` OIDC client** — integrate `oidc-client-ts` (`/auth/callback`, silent renew, logout).
- [ ] **WaterFun gateway switch** — point signature verification at JWKS; add route `/api/auth/** → SSO` at the top; align whitelist paths.

**Other modules (not started):**

- [ ] **`nitrowater-server`** — business API + Resource Server (`spring-boot-starter-oauth2-resource-server`, JWKS validation).
- [ ] **`nitrowater-web`** — composite portal: navigation + toolbox (`wtools` migration, 7 tools) + playground + docs channel (`velochatx-docs` under `/docs/**`).
- [ ] **`nitrowater-judge`** — judge service: submit → compile → sandbox run → judge; sandbox triple (timeout kill · resource limits · isolation) is a **security prerequisite** before opening submissions.

**Backlog:**

- [ ] Third-party OAuth2 login (QQ / WeChat / GitHub) via `sso_identity`.
- [ ] Smoke-script idempotency (cleanup of the test account it registers).
- [ ] Upstream decisions **D1–D9** (data split, encryption baseline, ban semantics, etc.) — blocked pending WaterFun review.

## Roadmap

| Phase | Content | Status |
|---|---|---|
| **M0** Project baseline | Init fixes, `gradlew build` green, module responsibilities | ✅ |
| **M3** SSO Phase 1 | Auth API + SSO DB + end-to-end smoke (contract unchanged) | ✅ |
| **M4** SSO Phase 2 | Real OIDC SSO: token customizer, `oidc_client` table, SPA integration, WaterFun gateway JWKS | 🚧 in progress |
| **M1** wtools merge | Migrate `wtools` into `nitrowater-web`, routing/build/nav integration | ⏳ |
| **M2** Playground MVP | `nitrowater-judge`: submit API + subprocess sandbox + Python/Java judging + rate limiting | ⏳ |
| **M5** Wrap-up | Problem import, submission history, integration SOP docs, security review | ⏳ |

## Architecture

```
nitrowater (Gradle monorepo)
├── nitrowater-core        # shared library (entities / repositories / auth services) — not deployed
├── nitrowater-account     # SSO identity center (Authorization Server + auth API + hosted login page)
├── nitrowater-server      # business API (toolbox backend, problem/submission, static hosting)
├── nitrowater-judge       # playground judge service (compile → sandbox → judge)   [planned]
└── nitrowater-web         # composite front-end: portal + toolbox + playground + docs + SSO callback
```

Runtime (production): a gateway is the trust boundary; `/api/auth/**` routes to the SSO service, `/api/**` to business services, `/api/judge/**` to the judge service. Locally, `nitrowater-server` exposes JWT verification directly (no gateway).

## Tech Stack

- **Backend**: Java 25, Spring Boot 4.1.1, Spring Security 7 + Spring Authorization Server 7 (OIDC), JPA + MySQL 8, Redis, Flyway.
- **Frontend**: Vue 3 + TypeScript + Vite, `oidc-client-ts`.
- **Docs**: VitePress (`velochatx-docs`).

## Getting Started

Prerequisites: **JDK 25**, **MySQL 8**, **Redis** (Gradle wrapper is bundled).

```powershell
# 1) JWT keys -> deploy/keys/{private,public}.key  (copy WaterFun's, or generate)
.\deploy\bin\gen-keys.ps1

# 2) Start Redis (MySQL is assumed running)
.\deploy\bin\start-redis.bat

# 3) Self-check (ports / keys / KEK)
.\deploy\bin\check-env.ps1

# 4) Run the SSO service (http://localhost:8090)
.\deploy\bin\start-account.bat
# or: gradlew :nitrowater-account:bootRun

# 5) End-to-end smoke (interactive)
.\deploy\bin\smoke-auth.ps1

# 6) OIDC discovery check
#    curl http://localhost:8090/.well-known/openid-configuration
```

Key configuration: `nitrowater-account/src/main/resources/application.yml` (datasource, Redis, JWT key paths, device salt, CORS whitelist, cookie SameSite/Secure).
The `WATERFUN_KEK` environment variable (32-byte Base64 AES KEK) is required for field encryption; `bootRun` injects a dev default.

## Repository & Docs

| Platform | URL |
|---|---|
| GitHub | https://github.com/danburen/nitrowater |
| Gitee | https://gitee.com/blackwallet/nitrowarer |

- Requirements / design: [`docs/PRD.md`](docs/PRD.md)
- Chinese README: [`docs/readme/zh.md`](docs/readme/zh.md)

## License

See [`LICENSE`](LICENSE).
