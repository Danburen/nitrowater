# deploy/content — 运行时内容（词表 / 文档语料）

本目录是**挂载用的内容产物**，不含代码，默认被 `.gitignore` 排除（仅保留 `README.md` 与各子目录的 `.gitkeep`）。
内容由运营/内容仓单独维护，容器只读挂载，因此改内容不需要重新构建镜像。

```
deploy/content/
├── moderation/
│   ├── words.txt      # 生产违禁词（追加到镜像内基线词表之后）
│   └── allow.txt      # 误报逃生口（覆盖住命中的片段才生效，见下）
└── docs/
    └── nitrowater/    # 外置语料（仅当 AI_DOCS_LOCATION 指向它时生效）
```

## 挂载方式

`docker-compose.app.yml` **常驻**把两个子目录只读挂进 `server`：

| 宿主机 | 容器内 | 消费方 |
|---|---|---|
| `${CONTENT_ROOT}/moderation` | `/etc/nitrowater/moderation` | 内容安全词表 |
| `${CONTENT_ROOT}/docs` | `/etc/nitrowater/docs` | RAG 语料（`AI_DOCS_LOCATION`） |

`CONTENT_ROOT` 未设置时回落到 `../content`（相对 `deploy/docker/`，即本目录）。
目录为空不影响启动 —— 是否启用由 `.env` 里的 `AI_MODERATION_DICT` / `AI_DOCS_LOCATION` 决定。

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

⚠️ 本目录被 gitignore 且**不在部署包里**（所以不会被覆盖），但也不在 `backup_resource.sh` 的备份范围里。
词表属于配置，改完务必在私有仓或 `deploy/backups/` 留一份，否则换机能找回镜像基线却找不回生产增量。

## 文档语料：默认不接管

`AI_DOCS_LOCATION` 留空 ⇒ 仍用镜像内 `classpath:ai/docs/nitrowater`。指向本目录时（纯路径即可，
`file:` 前缀也接受）：

```
AI_DOCS_LOCATION=/etc/nitrowater/docs/nitrowater     # 容器内
AI_DOCS_LOCATION=deploy/content/docs/nitrowater      # 本地 bootRun（工作目录 = 仓库根）
```

⚠️ 指向的目录**必须至少有 1 个 `.md`**。该 corpus 读到 0 个文件时索引不会保留它，重建后这段文档会从
检索结果里消失（`check-env` 会拦这一条）。修改后生效：`POST /api/admin/ai/reindex`（全量重建，建议低峰）。

引用链接由 `app.ai.collections[].public-base-url` + 相对路径拼出，**语料与站点必须同版本**发布，否则
`[n]` 会指到另一版页面。

## 提交约定

- 本目录内容**不提交**。需要留痕的只有 `README.md` / `.gitkeep`。
- 词表变更走变更单或私有仓 PR（谁、何时、加了什么、依据），生产事故时要能举证。
- 多环境各自独立目录，不要共享（staging 试词会污染生产统计与误伤）。
