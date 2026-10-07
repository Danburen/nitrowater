# nitrowater 环境变量参考

> Phase 1：`nitrowater-account` 使用**扁平 `application.yml`**，不引入 shared config 导入。
> 本文件说明本地/生产运行需要关注的环境变量与配置项。

## 1. 本地开发必需

| # | 项 | 位置 | 默认/示例 |
|---|----|------|-----------|
| 1 | JWT 密钥 | `deploy/keys/{private,public}.key` | 复制 waterfun 的，或 `gen-keys.ps1` 生成 |
| 2 | `WATERFUN_KEK` | 环境变量 / `build.gradle` bootRun 默认 | 32 字节 Base64（见 `deploy/config/common-dev-secrets.yml`） |
| 3 | `device.salt` | `application.yml` | 已复用 waterfun 开发值 |
| 4 | MySQL | `application.yml` datasource | `localhost:3306/nitrowater_account`（自动建库） |
| 5 | Redis | `application.yml` data.redis | `localhost:6379` |

## 2. 配置入口

| 用途 | 文件 |
|------|------|
| account 全部配置（扁平） | `nitrowater-account/src/main/resources/application.yml` |
| 开发密钥登记（模板） | `deploy/config/common-dev-secrets.yml` |
| JWT 密钥对 | `deploy/keys/` |
| 启动脚本 | `deploy/bin/` |

## 3. 端口

| 服务 | 端口 |
|------|------|
| nitrowater-account (SSO) | 8090 |
| MySQL | 3306 |
| Redis | 6379 |

## 4. 生产（后续）

- JWT 私钥改由环境变量 `JWT_PRIVATE_KEY` 指向挂载路径（`file:/etc/nitrowater/keys/private.key`）；
- `WATERFUN_KEK` 由密钥管理系统注入；
- JPA `ddl-auto` 由 `update` 改为 `validate` + Flyway 基线；
- docker 部署（`deploy/docker/`）暂不引入，后续对齐 waterfun。
