# nitrowater 环境变量参考

> Phase 1：`nitrowater-account` 使用**扁平 `application.yml`**，不引入 shared config 导入。
> 本文件说明本地/生产运行需要关注的环境变量与配置项。

## 1. 本地开发必需

| # | 项 | 位置 | 默认/示例 |
|---|----|------|-----------|
| 1 | JWT 密钥 | `deploy/keys/{private,public}.key` | 复制 waterfun 的，或 `gen-keys.ps1` 生成 |
| 2 | `WATERFUN_KEK` | 环境变量 / `build.gradle` bootRun 默认 | 32 字节 Base64（见 `deploy/config/common-dev-secrets.yml`） |
| 3 | `device.salt` | `application.yml` | 已复用 waterfun 开发值 |
| 4 | MySQL | `application.yml` datasource | `localhost:3306/nitrowater`（自动建库） |
| 5 | Redis | `application.yml` data.redis | `localhost:6379` |
| 6 | `JWT_ISSUER` | 环境变量（SSO/OIDC 唯一签发者，Phase1 AT + AS 共用） | dev 默认 `http://localhost:8090`；生产 `https://auth.nitrowater.cn`。必须为 URL，且与前端 `VITE_OIDC_AUTHORITY` 一致 |

## 2. 配置入口

| 用途 | 文件 |
|------|------|
| account 全部配置（扁平） | `nitrowater-account/src/main/resources/application.yml` |
| 前端 OIDC 配置（Vite env） | `nitrowater-web/.env.local`（模板 `.env.example`） |
| 开发密钥登记（模板） | `deploy/config/common-dev-secrets.yml` |
| JWT 密钥对 | `deploy/keys/` |
| 启动脚本 | `deploy/bin/` |

## 3. 端口

| 服务 | 端口 |
|------|------|
| nitrowater-account (SSO) | 8090 |
| nitrowater-web (Vite dev) | 5173 |
| MySQL | 3306 |
| Redis | 6379 |

## 4. 生产（后续）

- `JWT_ISSUER` 设为 `https://auth.nitrowater.cn`（须与 AS 实际对外地址、前端 `VITE_OIDC_AUTHORITY` 三方一致）；
- JWT 私钥改由环境变量 `JWT_PRIVATE_KEY` 指向挂载路径（`file:/etc/nitrowater/keys/private.key`）；
- `WATERFUN_KEK` 由密钥管理系统注入；
- JPA `ddl-auto` 由 `update` 改为 `validate` + Flyway 基线；
- docker 部署（`deploy/docker/`）暂不引入，后续对齐 waterfun。
