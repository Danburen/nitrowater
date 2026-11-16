# NitroWater 部署

> 单机 Linux + Docker。**部署根** `DEPLOY_ROOT`（默认 `/opt/nitrowater`），命令都在这里执行。
> 变量模板只有 `deploy/docker/.env.example`（每项都有注释）；改完不要留 `YOUR_*_HERE`。

| 场景 | 怎么做 | 文档 |
|---|---|---|
| **首次部署**（一次性，碰 DNS / TLS / 密钥 / KEK） | 人工执行 | [`docs/bootstrap.md`](docs/bootstrap.md) |
| **日常发版** | `release.yml`（build + deploy）或 `deploy.yml`（只上线已有镜像） | [`docs/operations.md`](docs/operations.md) §1 |
| **infra 变更**（mysql / redis / edge） | 人工：改 compose 后 `up -d`，不随业务发版走 | [`docs/operations.md`](docs/operations.md) §1 |
| **备份 / 恢复 / 证书 / 隧道** | 人工 | [`docs/operations.md`](docs/operations.md) §2–§6 |

发版只换镜像，不碰宿主机状态。`deploy.yml` 的完整动作：同步 `deploy/` → `check-env` 门禁 →
**先拉齐 4 个镜像** → MySQL 备份 → `account` → `app`（`up -d --no-build`）→ 探活。
拉取失败不会留下"新 account + 旧 server"，备份失败不会继续替换容器。

**回滚**：`deploy.yml` 填 `ref=<产出镜像的那个 commit>`，`tag` **留空**（自动取该 commit 的 sha），
`deploy/` 与镜像自然同源；要退数据就用 `deploy/backups/` 里发版前那份 dump。`release` 会重建镜像，
不用来回滚。

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

三份共享 `nitrowater-net`。**启动顺序：infra → account → app**（bff 启动要拉 account 的 OIDC discovery）。

`docs` 的内容不来自本仓库：独立仓库 `nitrowater-plugin-docs` 的 CI 把各插件站构建成镜像（必须带
`--build-arg DOCS_BASE_PREFIX=/plugin`），`.env` 用 `DOCS_IMAGE_REPO` / `DOCS_IMAGE_TAG` 指过去
（发版/回滚见 `docs/operations.md` §9）。本地 dev 用 `deploy/docker/docker-compose.docs-local.yml`
挂载 `../nitrowater-plugin-docs/dist`（先 `DOCS_BASE_PREFIX=/plugin npm run build`）。

## 命令

```bash
# 首次部署：本地按 tracked 文件打包，服务器上解开（详见 docs/bootstrap.md）
# 必须用 git archive：tar 不认 gitignore，会把本机 keys/*.key、.env*、content/* 覆盖到生产
git archive --format=tar.gz HEAD deploy > /tmp/nitrowater-deploy.tgz
scp /tmp/nitrowater-deploy.tgz <host>:/tmp/
sudo mkdir -p /opt/nitrowater && sudo tar -xzf /tmp/nitrowater-deploy.tgz -C /opt/nitrowater
sudo chown -R "$USER": /opt/nitrowater/deploy && rm -f /tmp/nitrowater-deploy.tgz
# 之后：cp .env.example → .env、gen-keys → check-env → infra + 建库 → account → app → 收尾 → 验收
```

日常发版走 CI（全部手动触发）：

```bash
gh workflow run release.yml -f tag=v1.2.3   # 构建 + 上线（tag 只是人类别名，部署按 sha）
gh workflow run deploy.yml                  # 只上线当前 commit 已构建的镜像
gh workflow run deploy.yml -f ref=<sha>     # 回滚：镜像与 deploy/ 一起回到那个 commit
```

不经 CI 的应急动作（等价于 CI 的某一段，务必先确认 `.env` 在）：

```bash
# 只重发身份服务 / 只重发业务面
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.account.yml up -d --no-build
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.app.yml     up -d --no-build
# 换 tag：把 ACCOUNT_IMAGE_TAG / APP_IMAGE_TAG 写进环境（进程环境优先于 --env-file）
ACCOUNT_IMAGE_TAG=<sha> APP_IMAGE_TAG=<sha> docker compose ... up -d --no-build
# 发版前的备份（CI 会自动跑；手工发布前请自己跑一次）
bash deploy/bin/backup_mysql.sh
# 探活
curl -fsS http://127.0.0.1:8080/api/public/ping && docker inspect -f '{{.State.Health.Status}}' nitrowater-bff
```

Windows PowerShell 打包（`-o` 让 git 自己写文件；`>` 是 `Out-File`，会把 gzip 当文本写坏）：

```powershell
git archive --format=tar.gz -o "$env:TEMP\nitrowater-deploy.tgz" HEAD deploy
scp "$env:TEMP\nitrowater-deploy.tgz" <host>:/tmp/
Remove-Item "$env:TEMP\nitrowater-deploy.tgz"
```

## 脚本

| 用途 | 脚本 |
|---|---|
| 环境自检 / 部署门禁（`deploy.yml` 每次必跑） | `check-env.sh` |
| 生成 JWT 密钥 | `gen-keys.sh` |
| 按模板同步 `.env`（补新键、删废弃键、刷新注释） | `sync-env.sh` |
| 备份 MySQL（`deploy.yml` 发版前自动执行） | `backup_mysql.sh` |
| 备份资源字节（`RESOURCE_DATA_DIR` 快照） | `backup_resource.sh` |
| 给 Piston 装语言包（首次 / 加语言） | `install-piston-runtimes.sh` |
| 运维 UI 隧道 | `ops-tunnel.sh` |

全部是 `.sh`：Windows 用 Git Bash 或 WSL。开发期脚本在 `bin/dev/`，不属于上线路径。

## 三条硬规则

1. `deploy/` 下的 compose / 脚本 / 文档是**发布产物**：`deploy.yml` 从被部署的那个 commit 同步上来，
   改动随 commit 走。不要在服务器上手改——下次同步会被覆盖。
2. 宿主机只保留状态：`.env`、`.env.prod.secrets`、`keys/`、`content/`、`backups/`、资源目录
   （`RESOURCE_DATA_DIR`）与数据卷。它们被 gitignore，`git archive` 与 CI 同步都不会覆盖 ——
   但 `tar` 不认 gitignore，手动打包必须用 `git archive`。
3. 换机器 = 手工搬上面这些状态加数据卷；`NITROWATER_KEK` 必须离线另存（丢了密文不可解）。

## 安全红线

- `CORS_ALLOWED_ORIGIN_PATTERNS` 禁止 `*`。
- `PUBLISH_BIND` / `OPS_BIND` 保持 `127.0.0.1`。
- 上传只允许 `ROLE_ADMIN` / `ROLE_SUPER_ADMIN`；`RESOURCE_UPLOAD_ENABLED=false` 可整面关闭。
- `piston` 只连 `runner-net`，不要接回 `nitrowater-net`。
- 部署包只含 tracked 文件（`git archive`）；`keys/`、`.env` 只在服务器上生成。
- `/actuator/**`、`/internal/**` 不对公网开放（容器不发布 + 反代 404）。

## 先验三条

```bash
curl -fsS http://127.0.0.1:8080/api/public/ping                                  # pong
curl -s -o /dev/null -w '%{http_code}\n' https://<WEB_DOMAIN>/actuator/health      # 404/401，不能 200
curl -sI https://<WEB_DOMAIN>/api/ai/status | head -1                             # 401
```
