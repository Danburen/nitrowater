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

## 手工建账号（只有用户名 + 密码）

门户注册要短信 / 邮箱验证码；运维偶尔需要一个只有用户名和密码的账号（发布机器人、内网演示、应急管理员）。
`create-account.sh` 负责这件事：终端里输入两次密码（或从 stdin 管道喂），脚本自己算 BCrypt 再写库，明文不落盘。
交互输入**不回显**（同 Linux 的 `passwd`，连星号都不显示），管道喂入不进 shell 历史，密码也不经过 `argv`。

```bash
bash deploy/bin/create-account.sh -u opsbot              # 交互输入密码，角色 USER
bash deploy/bin/create-account.sh -u opsbot -r ADMIN     # 建（或补）管理员角色
bash deploy/bin/create-account.sh -u opsbot --force      # 只重置密码：不动角色、不复活封禁账号
printf '%s' "$PASS" | bash deploy/bin/create-account.sh -u opsbot   # 无人值守
```

- 用户名 4–20 位 `[A-Za-z0-9_]`，不能是手机号形状（登录会先按手机号 hash 解析）；密码 8–20 位且含大小写
  字母和数字。和注册页同一套规则——手工账号不该是注册接口会拒绝的账号。
- 账号既没手机号也没邮箱（`user_data` 两个 hash 列留 NULL），只能用户名 + 密码登录。
- `uid` 取 `9xxxxxxxxx` 段（服务自动生成的是 `1xxxxxxxxx`），由用户名 CRC32 派生，重跑落在同一行。
- 复用 MySQL 容器执行（宿主机不用装客户端）；BCrypt 依次尝试 `htpasswd`、`python3 + bcrypt`、
  `docker run httpd:2-alpine`（首次会拉一次镜像）。
- `lib/create-account.sql` 是配套的纯 DML，**不能单独执行**：它读脚本写入的 `@username` /
  `@password_hash` / `@uid` 等会话变量（MySQL 没有 BCrypt 函数，摘要只能在库外算）。
  `bin/` 下只放可执行脚本，`bin/lib/` 放它们读的 SQL / 片段等非可执行文件。

## 脚本

| 用途 | 脚本 |
|---|---|
| 环境自检 / 部署门禁（`deploy.yml` 每次必跑） | `check-env.sh` |
| 生成 JWT 密钥 | `gen-keys.sh` |
| 按模板同步 `.env`（补新键、删废弃键、刷新注释） | `sync-env.sh` |
| 备份 MySQL（`deploy.yml` 发版前自动执行） | `backup_mysql.sh` |
| 备份资源字节 | `backup_resource.sh` |
| 给 Piston 装 / 升级语言包 | `install-piston-runtimes.sh` |
| 手工建「用户名 + 密码」账号 / 重置其密码 | `create-account.sh` |
| 运维 UI 隧道 | `ops-tunnel.sh` |

全部是 `.sh`：Windows 用 Git Bash 或 WSL。

## 三条规则

1. `deploy/` 是发布产物，由 `deploy.yml` 从被部署的 commit 同步上来；不要手改，下次同步会覆盖。
2. 宿主机只保留状态：`.env`、`.env.prod.secrets`、`keys/`、`content/`、`backups/`、`RESOURCE_DATA_DIR` 与数据卷。
3. 换机器 = 搬上面这些状态加数据卷。

## 会话与重启

登录态分两层，都在 Redis：门户（BFF）的 `nitrowater:bff:session`、SSO（account）的
`nitrowater:account:session`；令牌本体由 account 写 MySQL（`oauth2_authorization`）。

- **日常重启不掉线**：Redis 是 AOF + 命名卷（`nitrowater-redis:/data`），容器/整机重启后会话还在。
  会丢的只有 `docker compose down -v`、`docker volume prune`、磁盘写满——这三种等于全员重登。
- **全员下线**：account 管理端「会话安全 → 注销全部登录会话」。它轮换 Redis 里的
  `nitrowater:auth:session-epoch`（BFF 与 account 默认链下一次请求即 401）、删除全部 SSO 会话、
  清空 `oauth2_authorization`（刷新令牌）。操作者自己也会被登出；因为动作是全局破坏性的，
  还要求操作者在最近 10 分钟内验证过密码，否则先重新登录。无需发版、无需动 `.env`。
- **免登录窗口**：空闲 7 天（`spring.session.timeout`）、绝对 30 天（`APP_SESSION_ABSOLUTE`，从最后一次
  输密码起算）。超过 30 天后 BFF 与 account 管理端都会强制回登录页，不需要清 Redis。
- **设备指纹**：门户每个 `/api/**`、`/bff/**` 请求都带 `X-Device-Fp`；指纹同时镜像到
  `nitrowater_device_fp` Cookie，供 `window.open`/资源下载这类带不了 header 的顶层导航使用。
  与登录时（`?dfp=`）不一致即 401 `device_changed`。用户清浏览器站点数据 = 换设备，属预期。
- **Redis 内存**：`volatile-lru` + 512MB。不要改回 `allkeys-lru`：那会把登录会话当普通缓存淘汰，
  表现为用户被静默踢下线。
- Redis 的 `appendonlydir` 不在 `backup_mysql.sh` 范围内；换机时带上卷，或者接受一次全员重登。

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
