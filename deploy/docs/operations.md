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

> **从 S3/SeaweedFS 版本升级**：`app.yml` 已删掉 `seaweedfs` 服务，旧容器不会自动消失。
> 首次用新 compose 发版后手工清一次：
> `docker rm -f nitrowater-seaweedfs; docker volume rm nitrowater-app_nitrowater-seaweedfs`（卷名以
> `docker volume ls` 为准）。资源字节从这次起落在 `RESOURCE_DATA_DIR`；`resource.backend` 只有
> `1=COS / 2=LOCAL` 两个编码（`0` 属于已删除的 S3 后端，永不复用），基线 `V1__resource_baseline.sql`
> 的默认值已是 `2`。

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

> `compose pull` 会拉整份文件，`app.yml` 里第三方镜像（piston / qdrant）未固定 tag；
> 某个源抽风会中止整个发布。建议预拉或固定版本。

## 2. 内容更新（多数不需要重建镜像）

| 内容 | 放哪 | 动作 |
|---|---|---|
| AI 词表 / 语料 | `deploy/content/**` | 覆盖文件 → `POST /api/admin/ai/moderation/reload` / `reindex` |
| 插件站点文档 | 不在宿主机：在 `nitrowater-plugin-docs` 的镜像里 | 改 `.env` 的 `DOCS_IMAGE_TAG` → `up -d docs`（见 §9）；不要往服务器目录里写，容器不挂载 |
| 插件 Jar | `RESOURCE_DATA_DIR`（本地磁盘） | `POST /internal/resource/publish` 或 `bin/dev/sync-res.ps1` |

上传面只对 `ROLE_ADMIN` / `ROLE_SUPER_ADMIN` 开放（`/api/resource/upload/**`）；`RESOURCE_UPLOAD_ENABLED=false`
可整面关掉（503）。插件发布走独立通道，不受影响。

### 资源出入口（只有 443 一个入口）

| 方向 | 路径 | 鉴权 |
|---|---|---|
| 上传（唯一入口） | `POST /api/resource/upload/policy` → `PUT /api/resource/upload/{uuid}` | Bearer + `ROLE_ADMIN` / `ROLE_SUPER_ADMIN`；PUT 必须带 `Content-Length`（流式，不缓冲） |
| 公开读（匿名出口） | `GET /api/public/resource/{uuid}` `…/download` `…/meta`、`/api/public/resources` | 无需登录；**只出 `visibility=PUBLIC` 的行** |
| 认证读（登录出口） | `GET /api/resource/{uuid}` `…/download` `…/meta`、`/api/resources` | Bearer；`PUBLIC` + **本人** `PRIVATE` |
| 管理端下载（控制台） | `GET /api/admin/resources/{uuid}/download` | Bearer + `ROLE_ADMIN`（本人行）或 `ROLE_SUPER_ADMIN`（全部） |

以上端点都按**客户端 IP** 限流（资源服务开启 `forward-headers-strategy`，按 BFF 透传的
`X-Forwarded-For` 取真实来源）：匿名读 300 次/60s、匿名列表 60 次/60s、通用上传 20 次/300s
（代理 PUT 10 次/300s）、插件 jar 10 次/300s、内部发布 30 次/60s；超限回
`429 TOO_MANY_REQUEST`（与 account 的限流码一致）。匿名读不到的行一律回 **404**（不确认 uuid 存在）。

`RESOURCE_PRESIGN_ENABLED=false`（默认）时不会 302 到对象存储：字节全部由 resource 服务经 BFF 转发，
所以公网只需要 `https://<WEB_DOMAIN>`，也不需要给 bucket 配 CORS。反过来，如果打开 presign，
两个出口都会改成跳转，`/api/public/**` 的匿名性就由对象存储的签名 URL 承担。

## 3. 备份与恢复

```bash
bash deploy/bin/backup_mysql.sh      # 三个库全量 dump → deploy/backups/nitrowater_all_<时间>.sql.gz
bash deploy/bin/backup_resource.sh   # 资源目录 → deploy/backups/resource/<时间>/（硬链接快照）

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

**资源字节必须单独备份**：`RESOURCE_DATA_DIR` 是唯一的数据面（`resource` 表只有元数据）。
丢了它，库里的行还在但所有下载 404。`backup_resource.sh` 用 `rsync --link-dest` 做快照：
每次写一份完整的 `deploy/backups/resource/<时间>/`，未改动的文件与上一份共享硬链接，所以每日增量
只占变化量；默认保留 30 天。恢复先停 `nitrowater-resource`，再
`rsync -a --delete deploy/backups/resource/<时间>/ "$RESOURCE_DATA_DIR"/` 后启动。
同样建议进 crontab：`30 3 * * * /opt/nitrowater/deploy/bin/backup_resource.sh >/dev/null 2>&1`。

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
- **日志/磁盘**：resource/server/bff 已有 `max-size`；mysql/redis/qdrant/piston 没有，
  长期运行会涨。定期看 `docker system df` 与 `df -h "$RESOURCE_DATA_DIR"`，磁盘告警阈值建议 80%。

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
| `/plugin/*` 404 或 nginx 默认页 | 文档站镜像没换成功 | 看 `docker inspect nitrowater-docs --format '{{.Config.Image}}'`，对照 `.env` 的 `DOCS_IMAGE_REPO` / `DOCS_IMAGE_TAG`（见 bootstrap §7） |
| 点"运行"报错 / 语言不可用 | Piston 镜像不含语言包 | 跑 §8 第一条安装命令（见 §8） |
| `manifest unknown` | `GITHUB_REPOSITORY` 仍是 your-org | 改 `.env` 或由 CI 注入 |
| bff 不健康 | OIDC discovery / 连库 / KEK | `docker logs nitrowater-bff` |

## 7. 上线前后各查一次

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://<WEB_DOMAIN>/actuator/health                     # 404/401
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://<WEB_DOMAIN>/internal/resource/publish  # 404/401/403
curl -s -o /dev/null -w '%{http_code}\n' https://<WEB_DOMAIN>/api/ai/status                      # 401
ss -lntp | grep -E ':(3306|6379|6333|8081|8093|9080)\b'   # 只应出现 127.0.0.1
```

## 8. Piston 语言运行时（加语言）

镜像**没有语言包**，也没有 `cli/ppman`。加语言和下节首次装包用的是同一个脚本，指定包名即可：

```bash
bash deploy/bin/install-piston-runtimes.sh rust            # 索引里最新版
bash deploy/bin/install-piston-runtimes.sh rust:1.72.0     # 指定版本
bash deploy/bin/install-piston-runtimes.sh --list          # 索引里有哪些包/版本
```

脚本做四件事：下官方 tarball → 解到 `/piston/packages/<包>/<版本>/` → 补 `.ppman-installed` 标记 →
重启 piston 并校验 `/api/v2/runtimes`。包在 `/piston` 卷里，重建 piston 不会丢。

**已安装的处理**：卷里已经有某个版本时默认不动它（打印 `skip rust (installed: 1.68.2, use --upgrade to move on)`）；
装了一半的目录（没有标记）会 `warn … reinstalling` 就地重装；`--upgrade` 才会去装索引里的最新版（旧版本目录保留）。

> **别用 `POST /api/v2/packages`**：它每次都会去 GitHub 拉包索引，跨境拉不动时会把 piston API 进程打死
> （`curl: (52) Empty reply`，之后要 `docker restart`）。脚本完全绕开它。
> 首次装齐见 `bootstrap.md` §7。

### 版本与源

| 需求 | 做法 |
|---|---|
| 要最新版（且没装过） | 只写包名（`rust`）；脚本用 `sort -V` 比较，不会把 `3.9.4` 当成比 `3.12.0` 新 |
| 已经装过、想升到最新 | `--upgrade`（`bash deploy/bin/install-piston-runtimes.sh --upgrade rust`）；旧版本目录保留，确认后再删 |
| 要可复现 | 写完整版号（`python:3.12.0`），`--list` 里挑 |
| 换代理 | `PISTON_MIRROR=https://ghproxy.net/ bash deploy/bin/install-piston-runtimes.sh …` |
| 换下载缓存目录 | `PISTON_DL_DIR=/data/piston-dl …`（默认 `/var/tmp/piston-dl`，断点续传用） |
| 换容器名 | `PISTON_CONTAINER=nitrowater-piston …`（默认就是它） |

### 验收

```bash
# 清单（脚本末尾已经打过一次）
docker exec nitrowater-piston node -e "require('http').get('http://127.0.0.1:2000/api/v2/runtimes',r=>{let d='';r.on('data',c=>d+=c);r.on('end',()=>console.log(d))})"

# 真跑一段比看列表靠谱（借一次性 curl 容器连 piston）
docker run --rm --network container:nitrowater-piston curlimages/curl:latest \
  -fsS -X POST http://127.0.0.1:2000/api/v2/execute \
  -H 'Content-Type: application/json' \
  -d '{"language":"rust","version":"1.72.0","files":[{"name":"main.rs","content":"fn main(){println!(\"ok\");}"}]}'
```

### 包名 ≠ 语言名（一个包提供多个语言）

| 包 | 提供的语言 |
|---|---|
| `gcc` | `c` / `c++` / `d` / `fortran` |
| `node` | `javascript`（别名 `js` / `node-js` …） |
| `typescript` | `typescript`（包里自带 `bin/node`，不用另装 `node`） |
| `java` / `python` / `lua` | 各自一个 |

脚本安装完会按这张表校验：`gcc` 装完应有 `c`，`node` 装完应有 `javascript`，缺了会报 `MISSING`。

### 装完 ≠ 用户能选：接到应用里

Piston 里装好只是第一步：后端有一张**白名单**、前端有一份**语言目录**，缺一处就会点不到，
或者点了报 `unsupported_language`。

| 改动点 | 说明 |
|---|---|
| `nitrowater-server/.../service/sandbox/LanguageMapper.java` | `TARGETS` 加一条 `前端id → (piston 语言, 版本, 文件名)`。版本写 `"*"`（取已装里最新，和现有条目一致）或固定完整版号；**编译型文件名不带扩展名**（Piston 自己补，带了会变成 `main.rs.rs`），脚本型带真实扩展名 |
| `nitrowater-web/src/features/tools/code-runner/langCatalog.ts` | `RUNNER_LANGUAGES` 加一条（`id/label/badge/engine:'server'/available:true/cacheKey/defaultCode`），并把 id 加进 `ServerLanguageId` 联合类型 |
| `nitrowater-web/src/features/tools/code-runner/samples.json` | 给新 id 一份起手示例（`SAMPLES` 是 `Record<RunnerLanguageId, string>`，漏了会编译报错） |
| 可选 `service/shared/LanguagePrelude.java`、`CompileErrors.java` | 想给默认 import/头文件就加一个 `case`；不加则原样执行、编译错误按运行时错误展示 |

示例（接 Rust，`rust` 包已装）：

```java
// LanguageMapper.TARGETS
Map.entry("rust", new Target("rust", "*", "main")),
```

```ts
// langCatalog.ts
export type ServerLanguageId = 'java' | 'c' | 'cpp' | 'rust'
```

改完重新构建并发布镜像 —— `deploy/bin/deploy.sh` 只拉镜像、不做构建，所以必须先在 CI 出 image。

## 9. 文档站发版与回滚（镜像）

插件文档站（每个插件一个 VitePress 站点，URL `/plugin/<id>/`）由**独立仓库**
`nitrowater-plugin-docs` 构建成一个镜像，本仓库只负责拉取和路由：

| 谁 | 管什么 |
|---|---|
| `nitrowater-plugin-docs` 的 CI | 构建静态树 + 镜像（必须 `--build-arg DOCS_BASE_PREFIX=/plugin`）；每次 main push 推 `:latest` 和 `:<短 sha>` |
| 本仓库 `docker-compose.app.yml` | 引用镜像：`DOCS_IMAGE_REPO`（owner/repo）+ `DOCS_IMAGE_TAG` |
| 本仓库 `docs-nginx.conf` | 路由 / 缓存 / `/healthz`；内容发布改不动它（故意的） |

### 发版

```bash
# A. 跟主项目一起（docs 属于 app 单元，deploy.sh 会一并 pull + up -d）
bash deploy/bin/deploy.sh

# B. 只更新文档站（业务服务不动；tag 写在进程环境里，优先级高于 .env）
DOCS_IMAGE_TAG=1a2b3c4 docker compose --env-file deploy/docker/.env \
  -f deploy/docker/docker-compose.app.yml up -d --no-build docs
curl -fsS http://127.0.0.1:8085/healthz      # ok
```

验证没问题后把 tag 写回 `.env`（`DOCS_IMAGE_TAG=1a2b3c4`）。不写回去的话，下次 `deploy.sh` 会按 `.env`
里的旧值把文档站退回上一个版本 —— 这是唯一一个"改了看起来生效、下次发布又变回去"的坑。

### 回滚

```bash
DOCS_IMAGE_TAG=<上一个短 sha> docker compose --env-file deploy/docker/.env \
  -f deploy/docker/docker-compose.app.yml up -d --no-build docs
```

一个 tag 覆盖整站（所有插件站点一起进退），不会出现"一半新一半旧"。

### 注意

| 要点 | 说明 |
|---|---|
| `--tag` 不管文档站 | `deploy.sh --tag v1.2.3` 只设 account / app 的 tag；文档站由 `DOCS_IMAGE_TAG` 独立控制 |
| `latest` 会飘 | 那个仓库每次 main push 都覆盖 `:latest`；想可复现/可回滚就写短 sha（history 里挑） |
| 私有包 | 服务器先 `docker login ghcr.io`（`read:packages`）；或把 package 设 public（可匿名 pull） |
| 按 digest 固定（可选） | 把 compose 那行写成 `image: …@sha256:…`；代价是 `DOCS_IMAGE_TAG` 对它不生效，升级要改文件 |
| 别往服务器目录里写 | 生产容器**不挂载任何文档目录**（`deploy/plugin-docs/` 已删）：写文件不会生效，只能换 tag |
| 换 nginx 配置 | 改完 `docker restart nitrowater-docs`（或 `docker exec nitrowater-docs nginx -s reload`）；`up -d` 对 `:ro` 挂载的文件变更不保证重建 |
| 镜像里那份 `docker/nginx.conf` | 部署时被本仓库的 `docs-nginx.conf` 挂载覆盖，只服务「本地 `docker run` 单独看镜像」；改路由请改本仓库这份 |
| 文档站 404 / 资源全挂 | 先看 `docker inspect nitrowater-docs --format '{{.Config.Image}}'` 是不是 `.env` 里那个 tag；资源 404 则是镜像没用 `DOCS_BASE_PREFIX=/plugin` 构建 |

本地预览不用镜像：在 `nitrowater-plugin-docs` 里 `DOCS_BASE_PREFIX=/plugin npm run build`，然后
`bin\start-infra.ps1`（dev 容器只读挂载 `../nitrowater-plugin-docs/dist`，路径可换 `DOCS_SITE_DIR`）→
`http://localhost:8085/plugin/<id>/`。`/plugin/` 本身是 404：**没有门户页**，入口在 SPA 的插件中心。

## 10. 已废弃的通道

- **SSH 推目录 / `scp` 上传文档**：`deploy/plugin-docs/` 已删除，生产容器不挂载任何文档目录，写文件
  不会生效。文档内容统一走 §9 的镜像（`receive-plugin-docs.sh` 也一并删了）。
- **`POST /api/v2/packages` 装 Piston 语言包**：会去拉 GitHub 索引，跨境拉不动会把 piston API 打死；
  改用 `deploy/bin/install-piston-runtimes.sh`（§8）。
