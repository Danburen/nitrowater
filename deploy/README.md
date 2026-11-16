# NitroWater 部署

> 单机 Linux + Docker，部署根 `DEPLOY_ROOT`（默认 `/opt/nitrowater`），命令都在这里执行。
> **首次部署**按 [`docs/bootstrap.md`](docs/bootstrap.md) 走一遍；本页是日常发版与运维。

## 拓扑

```
公网 nginx（TLS）
  ├── <WEB_DOMAIN>  → 127.0.0.1:8080  bff     SPA + /api/** + /plugin/**
  └── <AUTH_DOMAIN> → 127.0.0.1:8090  account 登录 / OIDC / 管理端
容器内不发布：server 8081 · resource 8093 · docs 80 · mysql · redis · piston · qdrant
```

| 单元 | compose | 内容 | 谁部署 |
|---|---|---|---|
| infra | `deploy/docker/docker-compose.infra.yml` | mysql / redis（`edge` profile 带 nginx + 自动证书） | **人工** |
| account | `deploy/docker/docker-compose.account.yml` | SSO / OIDC，可独立发布 | `deploy.yml` |
| app | `deploy/docker/docker-compose.app.yml` | qdrant / piston / docs / resource / server / bff | `deploy.yml` |

三份共享 `nitrowater-net`，启动顺序 `infra → account → app`。

## 发版

```bash
gh workflow run release.yml -f tag=v1.2.3   # 构建 + 上线（tag 只是别名，部署按 sha）
gh workflow run deploy.yml                  # 只上线：当前 commit 已构建的镜像，不重建
gh workflow run deploy.yml -f ref=<sha>     # 回滚：镜像与 deploy/ 一起回到那个 commit
```

`deploy.yml` 依次做：同步 `deploy/` → `check-env` 门禁 → 拉齐 4 个镜像 → MySQL 备份 →
`account` / `app` `up -d --no-build` → 探活。任一镜像拉不下来就停，容器保持原样。

应急 / 手工操作：

```bash
# 只重发身份服务或业务面
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.account.yml up -d --no-build
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.app.yml     up -d --no-build

# 换镜像 tag：写进进程环境（优先于 --env-file）
ACCOUNT_IMAGE_TAG=<sha> APP_IMAGE_TAG=<sha> docker compose --env-file deploy/docker/.env \
  -f deploy/docker/docker-compose.app.yml up -d --no-build

# 发版前手工备份 / 探活
bash deploy/bin/backup_mysql.sh
curl -fsS http://127.0.0.1:8080/api/public/ping
```

`.env` 和 `.env.prod.secrets` 两个都带上（`--env-file` 可重复）。

infra 不随业务发版，改动后自己拉：

```bash
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.infra.yml up -d
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.infra.yml --profile edge up -d
```

## 回滚

| 场景 | 动作 |
|---|---|
| 应用异常，数据没问题 | `deploy.yml` 填 `ref=<上一个 sha>`，`tag` 留空 |
| 只回身份服务 | `ACCOUNT_IMAGE_TAG=<sha> … up -d --no-build` |
| 迁移把数据改坏 | 停 app/account → 恢复发版前的 dump → 回上一个 sha |
| 文档站 | `DOCS_IMAGE_TAG=<上一个短 sha> … up -d docs`（见下） |
| 关公网入口 | `docker compose -f deploy/docker/docker-compose.infra.yml --profile edge stop nginx-proxy acme-companion` |

回滚别用 `release`（会重建镜像并重指向同名 tag）。

## 备份与恢复

```bash
bash deploy/bin/backup_mysql.sh      # 三库全量 → deploy/backups/nitrowater_all_<时间>.sql.gz
bash deploy/bin/backup_resource.sh   # 资源快照 → deploy/backups/resource/<时间>/
```

`deploy.yml` 每次发版前自动跑一次 MySQL 备份。建议再进 crontab：

```bash
0  3 * * * /opt/nitrowater/deploy/bin/backup_mysql.sh    >/dev/null 2>&1
30 3 * * * /opt/nitrowater/deploy/bin/backup_resource.sh >/dev/null 2>&1
```

恢复（先停应用）：

```bash
docker stop nitrowater-bff nitrowater-server nitrowater-resource nitrowater-account
gzip -dc deploy/backups/nitrowater_all_<时间>.sql.gz | docker exec -i nitrowater-mysql mysql -uroot -p
rsync -a --delete deploy/backups/resource/<时间>/ "$RESOURCE_DATA_DIR"/
```

`RESOURCE_DATA_DIR` 是唯一数据面：只备份数据库会得到「行还在、下载全 404」。
Qdrant 索引可重建，需要冷备时：

```bash
docker run --rm -v nitrowater-qdrant:/qdrant/storage -v "$PWD":/backup alpine \
  tar czf /backup/qdrant-$(date +%F).tgz -C /qdrant/storage .
```

## 文档站（独立仓库镜像）

插件文档站由 `nitrowater-plugin-docs` 构建成镜像，`.env` 用 `DOCS_IMAGE_REPO` / `DOCS_IMAGE_TAG` 指过去，
部署时作为 app 单元的一个服务一起 `pull` + `up -d`。

```bash
# 只更新文档站（业务服务不动）
DOCS_IMAGE_TAG=<短 sha> docker compose --env-file deploy/docker/.env \
  -f deploy/docker/docker-compose.app.yml up -d --no-build docs
curl -fsS http://127.0.0.1:8085/healthz      # ok
```

改完把 tag 写回 `.env`，否则下次部署会退回旧版本。URL 只有 `/plugin/<id>/`，`/plugin/` 本身 404
（入口在 SPA 的插件中心）。镜像必须用 `DOCS_BASE_PREFIX=/plugin` 构建。

## Piston 加语言

```bash
bash deploy/bin/install-piston-runtimes.sh rust            # 索引里最新版
bash deploy/bin/install-piston-runtimes.sh rust:1.72.0     # 指定版本
bash deploy/bin/install-piston-runtimes.sh --list          # 索引里有哪些包/版本
bash deploy/bin/install-piston-runtimes.sh --upgrade       # 有新版才装
```

已装的会 `skip`，装完自动重启 piston 并校验 `/api/v2/runtimes`；包在 `/piston` 卷里，重建容器不丢。
别用 `POST /api/v2/packages` 装包（会打挂 piston API）。

包名 ≠ 语言名：`gcc` → `c`/`c++`/`d`/`fortran`，`node` → `javascript`，`typescript` / `java` / `python` / `lua` 各自一个。
换源：`PISTON_MIRROR=https://ghproxy.net/ bash deploy/bin/install-piston-runtimes.sh`。

## 运维入口

运维 UI 不对公网开放：`bash deploy/bin/ops-tunnel.sh --host <host>` 走 SSH 隧道，不要改 `OPS_BIND`。

## 脚本

| 用途 | 脚本 |
|---|---|
| 环境自检 / 部署门禁（`deploy.yml` 每次必跑） | `check-env.sh` |
| 生成 JWT 密钥 | `gen-keys.sh` |
| 按模板同步 `.env`（补新键、删废弃键、刷新注释） | `sync-env.sh` |
| 备份 MySQL（`deploy.yml` 发版前自动执行） | `backup_mysql.sh` |
| 备份资源字节 | `backup_resource.sh` |
| 给 Piston 装 / 升级语言包 | `install-piston-runtimes.sh` |
| 运维 UI 隧道 | `ops-tunnel.sh` |

全部是 `.sh`：Windows 用 Git Bash 或 WSL。

## 三条规则

1. `deploy/` 是发布产物，由 `deploy.yml` 从被部署的 commit 同步上来；不要手改，下次同步会覆盖。
2. 宿主机只保留状态：`.env`、`.env.prod.secrets`、`keys/`、`content/`、`backups/`、`RESOURCE_DATA_DIR` 与数据卷。
3. 换机器 = 搬上面这些状态加数据卷。

## 安全红线

- `CORS_ALLOWED_ORIGIN_PATTERNS` 禁止 `*`。
- `PUBLISH_BIND` / `OPS_BIND` 保持 `127.0.0.1`。
- 上传只允许 `ROLE_ADMIN` / `ROLE_SUPER_ADMIN`；`RESOURCE_UPLOAD_ENABLED=false` 可整面关闭。
- `piston` 只连 `runner-net`，不要接回 `nitrowater-net`。
- `/actuator/**`、`/internal/**` 不对公网开放（容器不发布 + bff denyAll）。

## 先验三条

```bash
curl -fsS http://127.0.0.1:8080/api/public/ping                                  # pong
curl -s -o /dev/null -w '%{http_code}\n' https://<WEB_DOMAIN>/actuator/health      # 404/401，不能 200
curl -sI https://<WEB_DOMAIN>/api/ai/status | head -1                             # 401
```
