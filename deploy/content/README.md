# deploy/content — 运行时内容（词表 / 文档语料）

本目录是**挂载用的内容产物**，不含代码，但三类数据的归属不同：

- `docs/`：**随仓库版本化的文档语料**（git 跟踪，随 CI 打包发布）。它决定 AI 回答的内容，必须和 commit 对齐。
- `moderation/`：**宿主机状态**（生产词表，gitignore，不进部署包），改完热加载即可。
- `legal/`：**宿主机状态**（用户协议 / 隐私政策等纯文本，gitignore，不进部署包），一篇一个 `.txt`，
  改完按请求立即生效；篇数、顺序由文件名决定，标题取正文第一行，加文档不用改代码。

三类都只读挂进容器，所以改内容都不需要重新构建镜像。

```
deploy/content/
├── moderation/          # 宿主机状态（不提交）
│   ├── words.txt        # 生产违禁词（追加到镜像内基线词表之后）
│   └── allow.txt        # 误报逃生口（覆盖住命中的片段才生效，见下）
├── legal/               # 宿主机状态（不提交）
│   ├── terms.txt         # 用户协议（纯文本，文件名即 key）
│   └── privacy.txt       # 隐私政策（纯文本）
└── docs/                # 随仓库发布（tracked）
    ├── nitrowater/      # 平台自身语料
    │   ├── _corpus.yml
    │   └── **/*.md
    └── velochatx/       # VeloChatX 插件语料
        ├── _corpus.yml
        └── **/*.md
```

## 挂载方式

`docker-compose.app.yml` **常驻**把三个子目录只读挂进容器：

| 宿主机 | 容器内 | 消费方 |
|---|---|---|
| `${CONTENT_ROOT}/moderation` | `/etc/nitrowater/moderation` | 内容安全词表（`server`） |
| `${CONTENT_ROOT}/docs` | `${AI_DOCS_ROOT:-/etc/nitrowater/docs}` | RAG 语料（`server`，`app.ai.docs-root`） |
| `${CONTENT_ROOT}/legal` | `/etc/nitrowater/legal` | 法务文本：`bff` 读正文（`LEGAL_DIR` / `app.legal.dir`）；`account` 不挂载，只读 `client_legal_link` 表里的链接 |

生产（`DEPLOY_ROOT=/opt/nitrowater`，tarball 解到 `deploy/`）里 `CONTENT_ROOT=/opt/nitrowater/deploy/content`，
所以语料就是 `/opt/nitrowater/deploy/content/docs`；本地相对路径 `../content` 与之等价（相对 `deploy/docker/`）。
**`.env` 只需要配 `CONTENT_ROOT`**：容器内路径固定是 `/etc/nitrowater/docs`（compose 拿它当挂载点和
`AI_DOCS_ROOT`，不随环境变，`.env` 里不用填）。bootRun 没有容器，未配置 `AI_DOCS_ROOT` 时服务依次尝试
`deploy/content/docs` → `../deploy/content/docs` → `/etc/nitrowater/docs`。

启动 / 更新：

```bash
docker compose --env-file deploy/docker/.env -f deploy/docker/docker-compose.app.yml up -d
```

## 词表：双层叠加，不要单层

在 `.env` 里显式写两层：

```
AI_MODERATION_DICT=classpath:ai/moderation/words.txt,file:/etc/nitrowater/moderation/words.txt
AI_MODERATION_ALLOW=classpath:ai/moderation/allow.txt,file:/etc/nitrowater/moderation/allow.txt
```

- **第一层 classpath**：随镜像发布的基线词表，保证"挂载丢了也不会裸奔"。
- **第二层 file**：本目录的生产词表，不进公开仓库。
- 挂载缺失时加载器只跳过该层并打 WARN，基线仍生效。**不要**改成只用 `file:` 单层——路径写错会让闸门
  静默失效（`words=0`，日志可见、用户无感）。

修改后生效：`POST /api/admin/ai/moderation/reload`（无需重启）。用 `deploy/bin/check-env.sh` 可在上线前
校验词表是否真的非空、基线层是否还在。

### 词表格式（加载器的硬规则）

| 规则 | 说明 |
|---|---|
| UTF-8 **无 BOM** | 带 BOM 时第一行会变成 `\uFEFF代开发票`，**永远匹配不到**，而且没有任何报错 |
| 一行一个词 | 行首尾空白被 `trim`；空行与 `#` 开头忽略 |
| 每条 ≥ 2 字符 | 单字条目被**静默丢弃**（`ModerationDictionary.MIN_WORD_LENGTH`），所以 `check-env` 数的行数可能比实际加载数大 |
| 换行 LF | CRLF 不会破坏加载，但保持 LF（`sed -i 's/\r$//'`） |
| 归一化已开 | 大小写、全半角、繁简、重复字符自动归一，不必为变体重复登记 |
| 多层去重 | 按声明顺序合并、重复词只留一次；某层文件不存在只打 WARN 并跳过 |

词内空格不会去掉：`代 开` 这类拆字仍可绕过，这是词典方案的公认局限（靠模型侧 prompt 兜底）。

### allow 的抑制范围（实测行为）

allow 只抑制**它自己覆盖的那一段**，且长度必须 ≥ 命中的违禁词：

| 违禁词 | allow | 文本 | 结果 |
|---|---|---|---|
| 代开 | 代开发票 | 我要代开发票 | 不命中（长 allow 盖住短 deny） |
| 代开 | 代开发票 | 代开 | **命中**（allow 只护住更长的那个片段） |
| 代开发票 | 代开 | 我要代开发票 | **命中**（allow 比 deny 短，挡不住） |
| 代开发票 | 代开发票 | 我要代开发票 | 不命中 |

所以正确姿势是：从后台命中列表的 `words` 列**抄下命中的那个词**，把它（或包含它的更高层短语）写进
`allow.txt`，而不是写一个更短的上位词。

### 上线节奏与调词闭环

1. `moderation_mode` 默认 `LOG`（影子：只记录、不拦截、不遮盖），先跑 1–2 天。
2. 看 `GET /api/admin/ai/moderation`：`dictionarySize` 应为基线 + overlay 的实际条数，命中行带 `words`。
3. 误报 → 加 `allow.txt`；真阳性 → 加 `words.txt`；两者都只动本目录。
4. `POST /api/admin/ai/moderation/reload`：返回刷新后的报告，热生效（旧引用给在途请求用完即弃）。
5. 稳定后切 `ENFORCE`（后台设置页 / `PUT /api/admin/ai/settings`）：违规提问被拒、答案被遮盖。
6. 稳定超过一个发布周期后，把 overlay 里的词迁回仓库基线（PR + 下个镜像），overlay 只留部署独有的。

`AI_MODERATION_STRIKE_LIMIT` / `STRIKE_WINDOW_MINUTES` / `COOLDOWN_MINUTES` 控制"连续命中几次后冷却多久"，
默认 5 次 / 10 分钟 → 冷却 15 分钟，只有 `ENFORCE` 下才真的拦。

⚠️ 词表目录被 gitignore 且**不在部署包里**（所以不会被覆盖），但也不在 `backup_resource.sh` 的备份范围里。
词表属于配置，改完务必在私有仓或 `deploy/backups/` 留一份，否则换机能找回镜像基线却找不回生产增量。

## 法务文本：宿主机维护，按请求读取

`legal/` 下每个 `.txt` 是**一篇纯文本**法务文档（不是 Markdown，没有 front matter、没有标记语法）。
**正文由 BFF（门户）** 在每次请求时读取，所以改完保存即生效：不用重启容器、不用重新构建、
也不会被浏览器 / 代理缓存（响应带 `no-store`）。

登录 / 注册页的链接**不拉取正文，也不由 SSO 托管**：SSO 库有一张独立的 `client_legal_link`
表（`client_id` 逻辑关联 `oauth2_registered_client`，不建物理 FK），每个应用可以登记任意多条
（用户协议、隐私政策、退款政策 …），按 `sort_order` 排序。用户被重定向到登录页时，account 按
待处理请求的 `client_id` 读这张表，把**该应用登记的全部文档**依次渲染成《…》链接，点击跳到
**该应用自己的页面**。这与 Microsoft Entra ID、Amazon Cognito、SAP Cloud Identity Services 的做法一致：
IdP 只负责指路，法务文本仍由各应用（BFF / 静态站）自行托管和更新。

- 第一方 `nitrowater-bff`：seeder 自动写入两条（`<SSO_DEFAULT_REDIRECT>/legal/terms`、`.../privacy`）；
  额外文档可在管理端「应用管理 → 法务链接」添加，重启不会被删。
- 其他应用在管理端加自己的链接即可，不需要改 account 配置、更不需要发版。
- 应用没登记 → 登录页只显示无链接的纯文本告知，**不会**借用运营方或其他应用的条款（那会造成误导）。
- 直接打开 `/login`、`/register`（没有待登录应用）→ 渲染第一方 `nitrowater-bff` 的链接。

- **文件名即 key**：`<key>.txt`，只允许小写字母、数字和连字符（`[a-z0-9-]`），其他文件一律忽略，
  不会变成接口。**没有顺序约定**：清单按目录返回顺序，页面就按那个顺序渲染。
- **标题取正文第一行**（跳过前面的空行）：页脚链接和详情页标题都用这一行，页面只显示一次——
  接口返回的正文已去掉这行标题。第一行超过 40 字或含控制字符（RTL 覆盖、ANSI 转义）时视为正文，
  该篇改用文件名作标题。所以**每篇开头写一行标题**，例如 `NitroWater 隐私政策`。
- **日期不用手写**：接口返回文件的**最后修改时间**，页面同时把它显示为「最近更新」和「生效日期」。
  想改生效日期就改文件（保存即变），正文里不要再写日期行。
- **联系渠道**：写「用户中心 / 站内公告公布的联系渠道」，不公开具体邮箱与注册地址。

| 项 | 值 |
|---|---|
| 清单 | `GET /api/public/legal` → `ApiResponse<[{key,title,updatedAt}]>`（只由 BFF 提供） |
| 单篇 | `GET /api/public/legal/<key>`（`text/plain; charset=utf-8`、`no-store`） |
| 登录页链接 | `GET /api/auth/login-context`（account，公开）→ `{clientId, clientName, legalLinks:[{title,url}]}`；URL 取自 `client_legal_link`，SSO 不读取、不缓存正文 |
| 门户源站 | `<WEB_DOMAIN>`（BFF），页面路由 `/legal/<key>`，页脚链到这里；登录页链接也指向这里（第一方应用的注册值） |
| SSO 源站 | `<AUTH_DOMAIN>`（account）**只读链接表并渲染**，不提供正文、不挂载目录；应用没登记时退化为无链接的纯文本告知 |
| 容器内目录 | `/etc/nitrowater/legal`（只有 `docker-compose.app.yml` 挂，`LEGAL_DIR`） |
| 本地 bootRun | BFF 自动探测 `deploy/content/legal` → `../deploy/content/legal` → `/etc/nitrowater/legal` |
| 版本管理 | **不提交**（`.gitignore` 已覆盖），每台机器自己放；换机时从私有备份恢复 |

> 加一篇新文档 = ① 门户里放 `refund.txt`（页脚入口自动出现）；② 管理端该应用的「法务链接」里
> 加一条《退款政策》指向 `<WEB_DOMAIN>/legal/refund`。正文归门户，登录页只读 SSO 的链接表。

> 为什么不在登录时抓取：登录页是整条链路最关键的页面，不该依赖某个应用的可用性；IdP 也不该去读
> 别人的文件。链接表在 SSO 本地，登录时只做一次查表，既解耦了认证与业务，也让每个应用能独立
> 更新、增删自己的条款。

登录页为什么不是勾选框：账号在注册时已经同意过这些文件，每次登录再勾一遍只会训练用户盲点，
也不构成新的同意。注册页的勾选是**必填**，前端拦截之外服务端还会校验 `agreed=true`。

首次部署：

```bash
sudo mkdir -p /opt/nitrowater/deploy/content/legal
sudo chown -R "$USER": /opt/nitrowater/deploy/content/legal
$EDITOR /opt/nitrowater/deploy/content/legal/terms.txt
$EDITOR /opt/nitrowater/deploy/content/legal/privacy.txt
curl -fsS https://<WEB_DOMAIN>/api/public/legal | head           # 门户清单
curl -fsS https://<WEB_DOMAIN>/api/public/legal/terms | head     # 门户正文
curl -fsS https://<AUTH_DOMAIN>/api/auth/login-context | head      # 登录页上下文（含应用条款链接）
```

**运营主体统一写 `NitroWater`**；正文里不再写「更新日期 / 生效日期」（由文件修改时间带出）；
最后一节的联系方式写「用户中心 / 站内公告公布的联系渠道」。发布前 `grep -n '{{' legal/*.txt` 自查，
没有输出才算干净。

> 目录不存在时清单为空、单篇 404，而不是回退到镜像里的旧版本——这是有意的：宁可暴露"还没上传"，
> 也不要让用户看到上一版、甚至别家运营主体的协议。

## 文档语料：目录即语料，自动发现

语料根下的**每个子目录 = 一个 corpus**，目录名即 corpus 名。新增语料只需加目录（放进 `.md` 即可），
不需要改 Java、也不需要改 `application.yml`：

- 只索引 `.md`，跳过 `.` 开头的路径与 `node_modules`；
- 可选 `_corpus.yml` 提供元数据：

  ```yaml
  description: VeloChatX 插件文档（安装、命令与权限、占位符、配置、排障）
  public-base-url: /plugin/velochatx/
  ```

  `description` 会进 system prompt，模型据此决定要不要 `searchDocs`（缺省就只能生成通用描述，检索命中率会掉）；
  `public-base-url` 决定引用链接，留空 = 只显示文档标题、不带 URL（如 `nitrowater/`）。
- 引用链接由 `public-base-url` + 相对路径拼出，**语料与站点必须同版本**发布，否则 `[n]` 会指到另一版页面。
- 启动时自动对齐：语料有新增/修改/删除，后台会重建索引；也可手动 `POST /api/admin/ai/reindex`（全量重建，建议低峰）。

### 发布

- 语料 tracked 在本仓库，`deploy.yml` 随 commit 打包 `deploy/`。只改文档时直接跑 `deploy.yml`
  （镜像默认用 `latest`，不重建），要连二进制一起更新才跑全量 `release.yml`。
- 线上语料不需要回滚，也不需要备份：下一次发布覆盖为最新即可。
- 语料目录为空或缺失时服务照常启动，只是检索会静默退化，`deploy/bin/check-env.sh` 会在上线前拦这一条。

### 核对"哪些文档建了索引"

```
GET /api/admin/ai/index                                   # 每个 corpus 的文档数 / chunk 数 / 索引时间
GET /api/admin/ai/index/documents?status=STALE&page=0&size=50
```

`ai_doc_index` 表记录"上次索引时的文件名 + 内容 sha"，与磁盘比对得出四种状态：

| 状态 | 含义 | 处理 |
|---|---|---|
| `INDEXED` | 磁盘与索引一致 | 无需操作 |
| `STALE` | 文件改过，索引还是旧的 | 重建索引 |
| `MISSING` | 磁盘上有，索引里没有 | 重建索引 |
| `ORPHAN` | 索引里有，文件已删 | 重建索引（会删掉这些向量） |

### 与 nitrowater-plugin-docs 的边界

`velochatx/` 下的 markdown 是**副本**，权威源是 `nitrowater-plugin-docs` 仓库（它同时产出站点镜像）。
改那边的文档时，同步到这里再发布；`nitrowater/` 语料只属于本仓库。

## 提交约定

- `docs/**` **提交**：新增/修改语料走正常 PR，与代码一起 review。
- `moderation/**` **不提交**：词表变更走变更单或私有仓 PR（谁、何时、加了什么、依据），生产事故时要能举证。
- `legal/**` **不提交**：法务文本按运营主体 / 域名 / 生效日期分别维护，换主体或换域名时必须重写，不能跟着代码走。
- 多环境各自独立目录，不要共享（staging 试词会污染生产统计与误伤）。
