# NitroWater 部署

> 单机 Linux + Docker。**部署根** `DEPLOY_ROOT`（默认 `/opt/nitrowater`），命令都在这里执行。
> 变量模板只有 `deploy/docker/.env.example`（每项都有注释）；改完不要留 `YOUR_*_HERE`。

| 场景 | 怎么做 | 文档 |
|---|---|---|
| **首次部署**（一次性，不可回滚） | 人工执行，碰 DNS / TLS / 密钥 / KEK | [`docs/bootstrap.md`](docs/bootstrap.md) |
| **日常运维 + 版本迭代** | CI 同步 `deploy/` + `bash deploy/bin/deploy.sh --tag <tag>` | [`docs/operations.md`](docs/operations.md) |

首次部署不进 CI：失败会留下半初始化的机器。CI 只负责已运行机器上的"文件同步 + 镜像替换"。
第一次跑的常见坑（传文件、权限、`manifest unknown`、`invalid redirect_uri`、413、语言包）
按顺序整理在 [`docs/bootstrap.md`](docs/bootstrap.md) §11。

**回滚**（手动，够用就好）：`deploy.sh --tag <旧 sha>`，并把 `deploy/` 同步回同一个 commit
（CI 用 `deploy.yml` 的 `ref`）；要退数据就用 `deploy/backups/` 里发版前那份 dump。暂不做自动回滚。

## 拓扑

```
公网 nginx（TLS）
  ├── <WEB_DOMAIN>  → 127.0.0.1:8080  bff     SPA + /api/** + /plugin/**
  └── <AUTH_DOMAIN> → 127.0.0.1:8090  account 登录 / OIDC / 管理端
容器内不发布：server 8081 · resource 8093 · docs 80 · mysql · redis · piston · qdrant
```

| 单元 | compose | 内容 |
|---|---|---|
| infra | `deploy/docker/docker-compose.infra.yml` | mysql / redis（`edge` profile 带 nginx + 自动证书） |
| account | `deploy/docker/docker-compose.account.yml` | SSO / OIDC，可独立发布 |
| app | `deploy/docker/docker-compose.app.yml` | qdrant / piston / docs / resource / server / bff |

三份共享 `nitrowater-net`。**启动顺序：infra → account → app**（bff 启动要拉 account 的 OIDC discovery）。

`docs` 的内容不来自本仓库：独立仓库 `nitrowater-plugin-docs` 的 CI 把各插件站构建成镜像（必须带
`--build-arg DOCS_BASE_PREFIX=/plugin`），`.env` 用 `DOCS_IMAGE_REPO` / `DOCS_IMAGE_TAG` 指过去
（发版/回滚见 `docs/operations.md` §9）。本地 dev 用 `deploy/docker/docker-compose.docs-local.yml`
挂载 `../nitrowater-plugin-docs/dist`（先 `DOCS_BASE_PREFIX=/plugin npm run build`）。

## 命令

```bash
# 首次（详见 docs/bootstrap.md）：本地仓库根打包，再在服务器上解开。
# 打包用 git archive（只含 tracked 文件），不要用 tar：tar 不认 .gitignore，
# 会把本机的 deploy/keys/*.key、deploy/docker/.env*、deploy/content/* 一起打进去，
# 解包时直接覆盖服务器上的生产密钥与配置。
git archive --format=tar.gz HEAD deploy > /tmp/nitrowater-deploy.tgz
scp /tmp/nitrowater-deploy.tgz <host>:/tmp/
# 服务器上（$USER = 你的 SSH 账号，CI 也用同一个写 deploy/）
sudo mkdir -p /opt/nitrowater && sudo tar -xzf /tmp/nitrowater-deploy.tgz -C /opt/nitrowater
sudo chown -R "$USER": /opt/nitrowater/deploy && rm -f /tmp/nitrowater-deploy.tgz
# 之后：cp .env.example → .env、gen-keys → check-env → infra + 建库 → account → app → 收尾 → 验收
# （.env 与 keys/ 只在服务器上生成，永远不随包走）

# 每次上线（CI 会先同步 deploy/；手动改过文件就按上面打包那一组再传一次，用同一个账号解包就不用 sudo）
bash deploy/bin/deploy.sh --tag <tag>       # 门禁 → 备份 → pull → up -d --no-build → 探活
bash deploy/bin/deploy.sh --only account    # 只动身份服务
bash deploy/bin/deploy.sh --help
```

Windows PowerShell 用这版（`-o` 让 git 自己写文件；`>` 在 PowerShell 里是 `Out-File`，会把 gzip 当文本写坏）：

```powershell
git archive --format=tar.gz -o "$env:TEMP\nitrowater-deploy.tgz" HEAD deploy
scp "$env:TEMP\nitrowater-deploy.tgz" <host>:/tmp/
Remove-Item "$env:TEMP\nitrowater-deploy.tgz"
```

## 脚本

| 用途 | 脚本 |
|---|---|
| **上线（唯一入口，CI 与手动同一条）** | `deploy.sh` |
| 生成 JWT 密钥 | `gen-keys.sh` |
| 环境自检 / 部署门禁 | `check-env.sh` |
| 按模板同步 `.env`（补新键、删废弃键、刷新注释） | `sync-env.sh` |
| 备份 MySQL（`deploy.sh` 发版前自动执行） | `backup_mysql.sh` |
| 备份资源字节（`RESOURCE_DATA_DIR` 快照） | `backup_resource.sh` |
| 给 Piston 装语言包（首次 / 加语言） | `install-piston-runtimes.sh` |
| 运维 UI 隧道 | `ops-tunnel.sh` |

全部是 `.sh`：Windows 用 Git Bash 或 WSL。开发期脚本在 `bin/dev/`，不属于上线路径。

## 三条硬规则

1. `deploy/` 下的 compose / 脚本 / 文档是**发布产物**：CI 从被部署的那个 commit 同步上来，
   改动随 commit 走。不要在服务器上手改——下次同步会被覆盖。
2. 宿主机只保留状态：`.env`、`keys/`、`content/`、`backups/`、资源目录（`RESOURCE_DATA_DIR`）与数据卷。它们被 gitignore，
   所以用 `git archive` 打包或 CI 同步都不会带上来、也不会被覆盖 —— 但 `tar` 不认 gitignore，
   手动打包必须用 `git archive`（见「命令」）。
3. 换机器 = 手工搬 `.env` / `keys/` / `content/` / 资源目录加数据卷；`NITROWATER_KEK` 必须离线另存（丢了密文不可解）。

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
