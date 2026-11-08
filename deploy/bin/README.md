# deploy/bin — 运维脚本

> 与 `bin/`（本地启动）分离：这里放**运维**脚本。均从**仓库根目录**执行。
> 每个脚本同时提供 **`.ps1`（Windows）** 与 **`.sh`（Linux/macOS）**；`.sh` 首次使用前 `chmod +x deploy/bin/*.sh`。
> **首次上线请按 `deploy/go-live-checklist.md` 逐步执行**（密钥生成 → .env → 预检 → 启动 → 首个管理员 → 冒烟）。
>
> 部署本身不提供封装脚本：直接 `docker compose ... up -d`（见下）。

| 用途 | Windows | Linux/macOS |
|------|---------|-------------|
| 生成 JWT 密钥 → `deploy/keys/` | `gen-keys.ps1` | `gen-keys.sh` |
| **上线环境自检**（加载 `.env`；必需变量/占位值/内容资产/密钥） | `check-env.ps1` | `check-env.sh` |
| 备份 MySQL（gzip + 保留 30 天） | `backup_mysql.ps1` | `backup_mysql.sh` |
| 清空 SSO 表（Flyway 重建） | `reset-sso-db.ps1` | `reset-sso-db.sh` |
| **运维 UI 隧道**（Qdrant / SeaweedFS，仅内网可达） | `ops-tunnel.ps1` | `ops-tunnel.sh` |

## 上线环境自检（check-env）

```powershell
.\deploy\bin\check-env.ps1                 # Docker 上线模式（读 deploy/docker/.env）
.\deploy\bin\check-env.ps1 -Local          # 本地 dev：额外查 3306/6379/8333/6333
.\deploy\bin\check-env.ps1 -EnvFile <path> # 指定别的 .env
```
```bash
bash deploy/bin/check-env.sh
bash deploy/bin/check-env.sh --local
ENV_FILE=deploy/docker/.env bash deploy/bin/check-env.sh
```

**退出码**：`0` = 无 FAIL；`1` = 有 FAIL。上线前必须 0 FAIL。

检查项：

| 分组 | 内容 |
|------|------|
| `.env` | 是否加载；进程内已有变量优先（与 compose 语义一致） |
| 必需 / 占位值 | `NITROWATER_KEK`、`DEVICE_SALT`（拦 dev 默认值）、`JWT_ISSUER`（必须 URL）、`DB_PASSWORD`、`BFF_CLIENT_SECRET`、S3 凭据 |
| 条件变量 | `COMMUNICATION_MODE=real` → SMTP + 阿里云短信；`RESOURCE_BACKEND=cos` → COS |
| AI | chat / embedding key（含 `DEEPSEEK_API_KEY` / `SILICONFLOW_API_KEY` 回落）；维度与集合匹配 |
| **内容资产** | 词表必须含 `classpath:` 基线层且去注释后非空；`AI_DOCS_LOCATION=file:` 目录必须含 `.md` |
| 密钥 | `deploy/keys/{private,public}.key` |
| 端口 | 仅 `-Local`：MySQL / Redis / SeaweedFS / Qdrant |

内容资产的检查会把容器内路径（`/etc/nitrowater/...`）映射回宿主机目录，校验的正是**将要被挂载的那份文件**；
挂载契约见 `deploy/content/README.md`。

## 暴露面与运维入口

不启用 `edge` profile 时**没有公网入口**：`account/resource/server/bff` 绑 `PUBLISH_BIND`（默认 `127.0.0.1`），
`qdrant/seaweedfs` 绑 `OPS_BIND`（默认 `127.0.0.1`）。生产用 infra compose 的 `edge` profile
（nginx-proxy + acme-companion）：只有 80/443 对外，证书自动签发续期。关闭公网入口：

```bash
docker compose -f deploy/docker/docker-compose.infra.yml --profile edge stop nginx-proxy acme-companion
```

管理面（Qdrant Dashboard、SeaweedFS filer/master）**不走公网边缘**，用隧道：

```powershell
.\deploy\bin\ops-tunnel.ps1 -HostName ops.example.com
```
```bash
bash deploy/bin/ops-tunnel.sh --host ops.example.com
```

→ `http://127.0.0.1:6333/dashboard`（Qdrant）、`:8888`（filer）、`:9333`（master），本地端口与远端一致。
完整暴露表、证书前提与回滚见 `deploy/env-vars-reference.md` §10。

## 日常运维：三类内容，三个入口

都不需要重跑 jar / 重建镜像，细节见 `deploy/env-vars-reference.md` §11。

| 内容 | 放哪 | 更新动作 |
|------|------|----------|
| AI 词表 / 语料 | `deploy/content/**` | 覆盖文件 → `POST /api/admin/ai/moderation/reload` / `reindex` |
| 插件站点文档 | `deploy/plugin-docs/<plugin>/docs/` | 覆盖产物（必要时 `docker restart nitrowater-docs`） |
| 插件 Jar | resource 服务 + SeaweedFS（不在 `deploy/`） | `pwsh -File bin\dev\sync-res.ps1 -Plugin <key> -SourceDir <dir>` |

管理入口：SPA `/admin/resources`；插件 jar 也支持 `POST /api/admin/plugin-resources/{key}` 直传。
用户上传默认关闭（`RESOURCE_USER_UPLOAD=false` → 接口返回 `503 uploads_disabled`），插件发布与下载不受影响。

## 部署（手工 docker compose）

三份 compose 单元（都从仓库根执行，共享外部网络 `nitrowater-net`）：

| 单元 | 文件 | 内容 |
|------|------|------|
| infra | `docker-compose.infra.yml` | mysql / redis；`edge` profile = nginx-proxy + acme-companion |
| account | `docker-compose.account.yml` | SSO / OIDC，可独立发布（`up -d --no-deps account`） |
| app | `docker-compose.app.yml` | seaweedfs / qdrant / piston / docs / resource / server / bff |

```bash
cp deploy/docker/.env.prod.example deploy/docker/.env   # 填写真实值
bash deploy/bin/check-env.sh                            # 0 FAIL 再继续

docker network create nitrowater-net                    # 只需一次

# 1) 中间件（要公网入口加 --profile edge）
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.infra.yml up -d

# 2) 身份服务（只更新它时加 --no-deps，不碰 mysql/redis/nginx）
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.account.yml up -d

# 3) 业务面（首次或重建加 --build）
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.app.yml up -d
```

**内容挂载**：app compose 常驻把 `deploy/content/{moderation,docs}` 只读挂到
`/etc/nitrowater/{moderation,docs}`；目录为空时不影响启动（是否启用由 `AI_MODERATION_DICT` /
`AI_DOCS_LOCATION` 决定）。变量与规则见 `deploy/env-vars-reference.md` §9。

详见 `deploy/docker/` 与 `deploy/env-vars-reference.md`。
