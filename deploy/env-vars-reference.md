# nitrowater 环境变量参考

> Phase 1：`nitrowater-account` 使用**扁平 `application.yml`**，不引入 shared config 导入。
> 本文件说明本地/生产运行需要关注的环境变量与配置项。

## 1. 本地开发必需

| # | 项 | 位置 | 默认/示例 |
|---|----|------|-----------|
| 1 | JWT 密钥 | `deploy/keys/{private,public}.key` | 复制 waterfun 的，或 `gen-keys.ps1` 生成 |
| 2 | `WATERFUN_KEK` | 环境变量 / `build.gradle` bootRun 默认 | 32 字节 Base64（见 `deploy/config/common-dev-secrets.yml`） |
| 3 | `device.salt` | `application.yml`（`${DEVICE_SALT:...}`） | dev 默认值；**生产必须用 `DEVICE_SALT` 覆盖，禁止复用 dev 盐值**（见第 2 节） |
| 4 | MySQL | `application.yml` datasource（`${DB_PASSWORD:123456}`） | SSO 库 `localhost:3306/nitrowater_account`（自动建库）；业务库 `nitrowater_biz`（server）；资源库 `nitrowater_resource`（resource） |
| 5 | Redis | `application.yml` data.redis（`${REDIS_PASSWORD:}`） | `localhost:6379` |
| 6 | `JWT_ISSUER` | 环境变量（SSO/OIDC 唯一签发者，Phase1 AT + AS 共用） | dev 默认 `http://localhost:8090`；生产 `https://auth.nitrowater.cn`。必须为 URL，且与前端 `VITE_OIDC_AUTHORITY` 一致 |

## 2. 生产必换清单（上线前逐项核对）

> 下列项在 `application.yml` / `docker-compose.*.yml` 中均带 **dev 弱默认**（`123456` / `change-me` /
> `nitrowater-bff-secret` 等），生产必须显式覆盖。依据：**OS 环境变量优先级高于 `application.yml`**，
> 因此只需在运行环境设置这些变量即可生效（无需改配置）。

| # | 变量 | 影响 | dev 默认（**禁止用于生产**） |
|---|------|------|------------------------------|
| 1 | `DB_PASSWORD` | account / server / resource 三库 datasource 口令 | `123456` |
| 2 | `DEVICE_SALT` | account 设备指纹盐（`device.salt`） | `8c4uWcFWgRFFJHoE/...`（**dev 盐值禁止上生产**；一旦复用即等于公开盐值） |
| 3 | `REDIS_PASSWORD` | 各服务 Redis 口令 | 空（无密码） |
| 4 | `BFF_CLIENT_SECRET` | BFF 的 OIDC 客户端密钥 | `nitrowater-bff-secret` |
| 5 | `S3_ACCESS_KEY` / `S3_SECRET_KEY` | S3/SeaweedFS 对象存储凭据 | `nitrowater` / `change-me` |
| 6 | `JWT_ISSUER` | SSO/OIDC 签发者（须为 URL） | `http://localhost:8090` |
| 7 | JWT 私钥 | `deploy/keys/*.key`（已 gitignore，不入库） | `gen-keys.ps1` 生成；生产改由 `JWT_PRIVATE_KEY` 指向挂载路径 |
| 8 | `WATERFUN_KEK` | account 密钥加密 KEK（32 字节 Base64） | 见 `deploy/config/common-dev-secrets.yml` |

## 3. 配置入口

| 用途 | 文件 |
|------|------|
| account 全部配置（扁平） | `nitrowater-account/src/main/resources/application.yml` |
| 前端 OIDC 配置（Vite env） | `nitrowater-web/.env.local`（模板 `.env.example`） |
| 开发密钥登记（模板） | `deploy/config/common-dev-secrets.yml` |
| JWT 密钥对 | `deploy/keys/` |
| 启动脚本（本地 dev） | `bin/`（`start-*.bat`）；冒烟 `bin/test/` |
| 运维/部署脚本 | `deploy/bin/`（gen-keys / check-env / backup_mysql / reset-sso-db / deploy） |

## 4. 端口

| 服务 | 端口 |
|------|------|
| nitrowater-account (SSO) | 8090 |
| nitrowater-web (Vite dev) | 5173 |
| MySQL | 3306 |
| Redis | 6379 |

## 5. 生产（后续）

- `JWT_ISSUER` 设为 `https://auth.nitrowater.cn`（须与 AS 实际对外地址、前端 `VITE_OIDC_AUTHORITY` 三方一致）；
- JWT 私钥改由环境变量 `JWT_PRIVATE_KEY` 指向挂载路径（`file:/etc/nitrowater/keys/private.key`）；
- `WATERFUN_KEK` 由密钥管理系统注入；
- JPA `ddl-auto` 由 `update` 改为 `validate` + Flyway 基线；
- docker 部署：`deploy/docker/`（`docker-compose.{infra,app,piston}.yml` + 模板 `.env.example`；根单文件多目标 `Dockerfile`），已对齐 waterfun 约定（infra=中间件、external `nitrowater-net`、服务=app）。
