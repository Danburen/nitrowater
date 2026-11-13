# NitroWater 部署

> 单机 Linux + Docker。**部署根** `DEPLOY_ROOT`（默认 `/opt/nitrowater`），命令都在这里执行。
> 变量模板只有 `deploy/docker/.env.example`（每项都有注释）；改完不要留 `YOUR_*_HERE`。

| 场景 | 怎么做 | 文档 |
|---|---|---|
| **首次部署**（一次性，不可回滚） | 人工执行，碰 DNS / TLS / 密钥 / KEK | [`docs/bootstrap.md`](docs/bootstrap.md) |
| **日常运维 + 版本迭代** | CI 同步 `deploy/` + `bash deploy/bin/deploy.sh --tag <tag>` | [`docs/operations.md`](docs/operations.md) |

首次部署不进 CI：失败会留下半初始化的机器。CI 只负责已运行机器上的"文件同步 + 镜像替换"。

**回滚**（手动，够用就好）：`deploy.sh --tag <旧 sha>`，并把 `deploy/` 同步回同一个 commit
（CI 用 `deploy.yml` 的 `ref`）；要退数据就用 `deploy/backups/` 里发版前那份 dump。暂不做自动回滚。

## 拓扑

```
公网 nginx（TLS）
  ├── <WEB_DOMAIN>  → 127.0.0.1:8080  bff     SPA + /api/** + /plugin/**
  └── <AUTH_DOMAIN> → 127.0.0.1:8090  account 登录 / OIDC / 管理端
容器内不发布：server 8081 · resource 8093 · docs 80 · mysql · redis · piston · qdrant · seaweedfs
```

| 单元 | compose | 内容 |
|---|---|---|
| infra | `deploy/docker/docker-compose.infra.yml` | mysql / redis（`edge` profile 带 nginx + 自动证书） |
| account | `deploy/docker/docker-compose.account.yml` | SSO / OIDC，可独立发布 |
| app | `deploy/docker/docker-compose.app.yml` | seaweedfs / qdrant / piston / docs / resource / server / bff |

三份共享 `nitrowater-net`。**启动顺序：infra → account → app**（bff 启动要拉 account 的 OIDC discovery）。

## 命令

```bash
# 首次（详见 docs/bootstrap.md）：本地仓库根打包，再在服务器上解开
tar -czf /tmp/nitrowater-deploy.tgz -C . deploy
scp /tmp/nitrowater-deploy.tgz <host>:/tmp/
# 服务器上（$USER = 你的 SSH 账号，CI 也用同一个写 deploy/）
sudo mkdir -p /opt/nitrowater && sudo tar -xzf /tmp/nitrowater-deploy.tgz -C /opt/nitrowater
sudo chown -R "$USER": /opt/nitrowater/deploy && rm -f /tmp/nitrowater-deploy.tgz
# 之后：cp .env.example → .env、gen-keys → check-env → infra + 建库 → account → app → 收尾 → 验收

# 每次上线（CI 会先同步 deploy/；手动改过文件就自己按上面三行再传一次，用同一个账号解包就不用 sudo）
bash deploy/bin/deploy.sh --tag <tag>       # 门禁 → 备份 → pull → up -d --no-build → 探活
bash deploy/bin/deploy.sh --only account    # 只动身份服务
bash deploy/bin/deploy.sh --help
```

## 脚本

| 用途 | 脚本 |
|---|---|
| **上线（唯一入口，CI 与手动同一条）** | `deploy.sh` |
| 生成 JWT 密钥 | `gen-keys.sh` |
| 环境自检 / 部署门禁 | `check-env.sh` |
| 备份 MySQL（`deploy.sh` 发版前自动执行） | `backup_mysql.sh` |
| 运维 UI 隧道 | `ops-tunnel.sh` |

全部是 `.sh`：Windows 用 Git Bash 或 WSL。开发期脚本在 `bin/dev/`，不属于上线路径。

## 三条硬规则

1. `deploy/` 下的 compose / 脚本 / 文档是**发布产物**：CI 从被部署的那个 commit 同步上来，
   改动随 commit 走。不要在服务器上手改——下次同步会被覆盖。
2. 宿主机只保留状态：`.env`、`keys/`、`content/`、`backups/`、数据卷。它们不在同步范围内
   （被 gitignore），也不会被覆盖。
3. 换机器 = 手工搬 `.env` / `keys/` / `content/` 加数据卷；`NITROWATER_KEK` 必须离线另存（丢了密文不可解）。

## 安全红线

- `CORS_ALLOWED_ORIGIN_PATTERNS` 禁止 `*`。
- `PUBLISH_BIND` / `OPS_BIND` 保持 `127.0.0.1`。
- 审核上线前 `RESOURCE_USER_UPLOAD=false`。
- `piston` 只连 `runner-net`，不要接回 `nitrowater-net`。
- `/actuator/**`、`/internal/**` 不对公网开放（容器不发布 + 反代 404）。

## 先验三条

```bash
curl -fsS http://127.0.0.1:8080/api/public/ping                                  # pong
curl -s -o /dev/null -w '%{http_code}\n' https://<WEB_DOMAIN>/actuator/health      # 404/401，不能 200
curl -sI https://<WEB_DOMAIN>/api/ai/status | head -1                             # 401
```
