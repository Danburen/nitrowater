# 日常运维与版本迭代

> 前提：机器已完成 [`bootstrap.md`](bootstrap.md)。之后**不要再手敲 compose**。
> 唯一发布入口是 `deploy/bin/deploy.sh`，手动与 CI 走同一条代码路径。

## 1. 发版

```bash
bash deploy/bin/deploy.sh --tag <tag>            # 门禁 → 备份 → pull → up -d --no-build → 探活
bash deploy/bin/deploy.sh --only account         # 只动身份服务（不碰 infra）
bash deploy/bin/deploy.sh --profile edge         # 启用自带 80/443
bash deploy/bin/deploy.sh --no-prune             # 不清理 24h 前旧镜像
bash deploy/bin/deploy.sh --skip-backup          # 跳过发版前的 MySQL 备份
```

顺序固定 `infra → account → app`；失败即停（`set -e`），退出码 0 = 探活通过。
`tag` 是镜像选择器：`v1.2.3`、`latest` 或某个提交的 sha。每次 build 同时打 `<tag>` + `<sha>`。

**发版前自动备份**：替换容器之前先跑 `backup_mysql.sh`，把三个库 dump 到 `deploy/backups/`；
失败就中止，不会出现"新代码已启动、旧数据没备份"。Flyway 在应用启动时执行，所以这份 dump 是
唯一的事前恢复点。`--only infra` 不备份（那正是启动 mysql 的动作）；确认本次无 schema 变更时才用
`--skip-backup`。

`deploy.sh` 只管镜像，**不同步文件**。CI 会先把 `deploy/` 同步上来；手动上线时如果改过
compose / 脚本，先用 README「命令」里那三行 tar + scp 传一次。

### CI（GitHub Actions，全部手动触发）

| 工作流 | 干什么 | 碰服务器 |
|---|---|---|
| `build.yml` | 跑检查（后端测试 + 两个前端 lint/build + compose/脚本守卫）→ `bake` 推 4 个应用镜像（`<tag>` + `<sha>`） | 否 |
| `deploy.yml` | preflight 确认 4 个镜像存在 → 从被部署的 commit 取 `deploy/` 打包 scp 上去 → 跑同一个 `deploy.sh` | 是 |
| `release.yml` | build → deploy，构建失败不动线上 | 是 |

```bash
gh workflow run release.yml -f tag=v1.2.3   # 日常发布
gh workflow run deploy.yml  -f tag=<sha>    # 只上线 / 回滚，不重建
```

`deploy.yml` 的 `ref` 决定 `deploy/` 来自哪个 commit：留空 = 本次运行的 commit；回滚时填成
产出该镜像的 sha（或版本 tag），保证 compose / 脚本和镜像同源。

一次性准备：仓库 Secrets `SSH_HOST` / `SSH_USER` / `SSH_KEY`（private 包再加 `GHCR_USER` / `GHCR_PAT`）；
Variables 可选 `DEPLOY_ROOT`、`COMPOSE_PROFILE`。CI 用专用账号，不要用 root：

```bash
sudo useradd -m -s /bin/bash deployer && sudo usermod -aG docker deployer
ssh-keygen -t ed25519 -f ~/.ssh/nitrowater_deploy && ssh-copy-id -i ~/.ssh/nitrowater_deploy.pub deployer@<host>
```

`deployer` 要能写 `DEPLOY_ROOT`（解包 deploy/）并带进 docker 组（`docker` 组 ≈ root，账号只给 CI 用）。

### 回滚（手动，够用就好）

```bash
# 1) 文件回到那个 commit：CI 的 deploy.yml 填 ref=<sha>；手动就自己 tar + scp 传一次
# 2) 镜像回到那个 tag
bash deploy/bin/deploy.sh --tag <上一个 sha>
bash deploy/bin/deploy.sh --tag <sha> --only account      # 只回滚身份服务
# 3) 数据退回去（仅当本次迁移不兼容）：停应用 → 用发版前的 dump 恢复（见 §3）
```

| 场景 | 动作 |
|---|---|
| 应用异常，数据没问题 | `deploy.sh --tag <上一 sha>` |
| account 异常 | `deploy.sh --tag <sha> --only account` |
| 迁移把数据改坏 | 停 app/account → 恢复发版前的 `deploy/backups/*.sql.gz` → 回上一个 tag |
| 关公网入口 | `docker compose -f deploy/docker/docker-compose.infra.yml --profile edge stop nginx-proxy acme-companion` |
| 拦截误伤 | 管理端模式切回 `LOG`，或改词表后 `moderation/reload` |
| 索引坏了 | `POST /api/admin/ai/reindex`（换维度先改 `AI_COLLECTION`） |
| 证书问题 | 保留 `nginx_certs` 卷；修 DNS/80 后重启 `acme-companion` |

**回滚别用 `release`**：它会重新构建并覆盖同名镜像。`deploy` 是唯一该用于回滚的入口。

### 部署文件与发布产物

- `deploy/`（compose + `bin/` + `docs/` + `.env.example`）由 CI 从 commit 同步，**commit 是对齐单位**；
  回滚镜像时把 `deploy/` 同步回同一个 commit。
- 只改 compose / 脚本 / 文档**不用 `build`**：直接走 `deploy` 即可（CI 同步的就是这些文件）。
- 同步是**合并**：只能覆盖仓库里有的文件；`.env`、`keys/`、`content/`、`backups/` 是宿主机状态，
  不在 tarball 里。仓库里删掉的旧文件不会自动消失（手工删）。
- 不要在服务器上手改 `deploy/` 下的发布产物，下次同步会覆盖。

> `compose pull` 会拉整份文件，`app.yml` 里第三方镜像（seaweedfs、piston）未固定 tag；
> 某个源抽风会中止整个发布。建议预拉或固定版本。

## 2. 内容更新（都不需要重建镜像）

| 内容 | 放哪 | 动作 |
|---|---|---|
| AI 词表 / 语料 | `deploy/content/**` | 覆盖文件 → `POST /api/admin/ai/moderation/reload` / `reindex` |
| 插件站点文档 | `deploy/plugin-docs/<plugin>/docs/` | 覆盖产物（必要时 `docker restart nitrowater-docs`） |
| 插件 Jar | SeaweedFS | `POST /internal/resource/publish` 或 `bin/dev/sync-res.ps1` |

用户上传默认关闭（`RESOURCE_USER_UPLOAD=false` → 503），插件发布下载不受影响。

## 3. 备份与恢复

```bash
bash deploy/bin/backup_mysql.sh      # 三个库全量 dump → deploy/backups/nitrowater_all_<时间>.sql.gz

# 恢复（先停应用，再灌回；dump 自带 CREATE DATABASE，可恢复到空实例）
docker stop nitrowater-bff nitrowater-server nitrowater-resource nitrowater-account
gzip -dc deploy/backups/nitrowater_all_<时间>.sql.gz | \
  docker exec -i nitrowater-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'
```

- 备份由容器内的 `mysqldump` 完成（宿主机不需要 mysql 客户端），口令从 `.env` 读；
  文件权限 600、目录 700，默认保留 30 天（`RETAIN_DAYS`）。
- `deploy/backups/` 不进 git、也不参与同步；换盘或对象存储请用 `BACKUP_DIR` 指过去。
- 备份与 `NITROWATER_KEK` **分开保管**：没有 KEK，dump 里的加密列解不开。
- 每次发版 `deploy.sh` 已经自动备份一次；另外建议每天定时跑，不要只靠发版触发：
  `0 3 * * * /opt/nitrowater/deploy/bin/backup_mysql.sh >/dev/null 2>&1`。
- 至少做一次恢复演练（临时 mysql 校验表数与关键行数）。

Qdrant 索引可重建（要花 embedding 费用），需要时冷备卷：

```bash
docker run --rm -v nitrowater-qdrant:/qdrant/storage -v "$PWD":/backup alpine \
  tar czf /backup/qdrant-$(date +%F).tgz -C /qdrant/storage .
```

## 4. 证书与密钥

- `edge`：nginx-proxy + acme-companion 自动申请 / 续签 / reload；进度看 `docker logs nitrowater-acme -f`。
- 自建反代：证书由那台 nginx 负责，本仓库不管。

| 密钥 | 轮换影响 | 做法 |
|---|---|---|
| `keys/private.key` | 全员重新登录 | 覆盖后重启 account |
| `NITROWATER_KEK` | 存量密文永久解不开 | 不要直接换；走重新加密迁移 |
| `DB_PASSWORD` | 应用连不上库 | 先 `ALTER USER`，再改 `.env`，再发布 |
| `APP_SESSION_EPOCH` | 旧会话全失效 | 每次发布换新值 |
| `BFF_CLIENT_SECRET` | 登录中断 | account + bff 一次发布同时更新 |

## 5. 其它

- **运维 UI 不暴露**：`bash deploy/bin/ops-tunnel.sh --host <host>` 走 SSH 隧道，不要改 `OPS_BIND`。
- **AI 额度**：先只统计不熔断，观察 3–7 天，按峰值 ×1.5 设限再打开；误判用「解除今日熔断」。
- **日志/磁盘**：resource/server/bff 已有 `max-size`；mysql/redis/seaweedfs/qdrant/piston 没有，
  长期运行会涨。定期看 `docker system df`，磁盘告警阈值建议 80%。

## 6. 故障对照

| 症状 | 原因 | 处理 |
|---|---|---|
| 登录后跳 `localhost:5173` | 地址变量没改 | 改 `.env` 重启 bff/account |
| `invalid redirect_uri` | 回调地址不一致 | 同上，注意 https 与结尾斜杠 |
| `Access denied for user` | 没建号 / 口令不符 | 见 bootstrap §5 |
| 证书不签发 | 80 不通 / DNS 未生效 | 安全组 + `dig`；看 acme 日志 |
| 管理端 403 | 未绑角色或未重新登录 | 授权 SQL + 重登 |
| 上传 413，容器日志无请求 | 反代 `client_max_body_size` 1m | 改 100m |
| AI 回答断流 / 整段刷出 | 反代 timeout / buffering | `proxy_buffering off; proxy_read_timeout 300s;` |
| `/plugin/*` 404 | `plugin-docs` 没传 | 见 bootstrap §7 |
| 点"运行"报错 | Piston 语言包没装 | `ppman install java/c/c++` |
| `manifest unknown` | `GITHUB_REPOSITORY` 仍是 your-org | 改 `.env` 或由 CI 注入 |
| bff 不健康 | OIDC discovery / 连库 / KEK | `docker logs nitrowater-bff` |

## 7. 上线前后各查一次

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://<WEB_DOMAIN>/actuator/health                     # 404/401
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://<WEB_DOMAIN>/internal/resource/publish  # 404/401/403
curl -s -o /dev/null -w '%{http_code}\n' https://<WEB_DOMAIN>/api/ai/status                      # 401
ss -lntp | grep -E ':(3306|6379|6333|8333|8081|8093|9080)\b'   # 只应出现 127.0.0.1
```
