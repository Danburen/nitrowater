# deploy/content — 运行时内容（词表 / 文档语料）

本目录是**挂载用的内容产物**，不含代码，默认被 `.gitignore` 排除（仅保留 `README.md` 与各子目录的 `.gitkeep`）。
内容由运营/内容仓单独维护，容器只读挂载，因此改内容不需要重新构建镜像。

```
deploy/content/
├── moderation/
│   ├── words.txt      # 生产违禁词（追加到镜像内基线词表之后）
│   └── allow.txt      # 白名单（命中 allow 即不算违规）
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
