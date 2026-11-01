# deploy/content — 运行时内容（词表 / 文档语料 / skills）

本目录是**挂载用的内容产物**，不含代码，默认被 `.gitignore` 排除（仅保留 `README.md` 与 `.gitkeep`）。
内容由运营/内容仓单独维护，容器只读挂载，因此改内容不需要重新构建镜像。

```
deploy/content/
├── moderation/
│   ├── words.txt      # 生产违禁词（追加到镜像内基线词表之后）
│   └── allow.txt      # 白名单（命中 allow 即不算违规）
├── docs/
│   └── nitrowater/    # 外置语料（仅当 AI_DOCS_LOCATION 指向它时生效）
└── skills/
    └── <name>/SKILL.md
```

## 启用方式

内容挂载是**可选叠加层**，默认部署（只 `-f docker-compose.app.yml`）行为完全不变：词表与文档仍从镜像
`classpath` 读取。启用时叠加：

```powershell
.\deploy\bin\deploy.ps1 -Content          # Windows（-SkipCheck 可跳过预检）
```
```bash
bash deploy/bin/deploy.sh --content       # Linux/macOS（--skip-check 可跳过预检）
```

单入口脚本在 `moderation/words.txt` 存在、或 `docs/` 下有任意 `.md` 时**自动开启**，也可用 `-Content`
显式强制。等价的手工方式：

```bash
docker compose -f docker-compose.app.yml -f docker-compose.content.yml up -d
```

挂载点（容器内，只读）：

| 宿主机 | 容器内 | 消费方 |
|---|---|---|
| `${CONTENT_ROOT}/moderation` | `/etc/nitrowater/moderation` | 内容安全词表 |
| `${CONTENT_ROOT}/docs` | `/etc/nitrowater/docs` | RAG 语料（`AI_DOCS_LOCATION`） |
| `${CONTENT_ROOT}/skills` | `/etc/nitrowater/skills` | 外部技能（`AI_SKILLS_DIR`） |

`CONTENT_ROOT` 未设置时回落到本目录（相对 compose 文件为 `../content`）。

## 词表：双层叠加，不要单层

叠加层里写的是：

```
AI_MODERATION_DICT=classpath:ai/moderation/words.txt,file:/etc/nitrowater/moderation/words.txt
```

- **第一层 classpath**：随镜像发布的基线词表，保证"挂载丢了也不会裸奔"。
- **第二层 file**：本目录的生产词表，不进公开仓库。
- 挂载缺失时加载器只跳过该层并打 WARN，基线仍生效。**不要**改成只用 `file:` 单层——路径写错会让闸门
  静默失效（`words=0`，日志可见、用户无感）。

修改后生效：`POST /api/admin/ai/moderation/reload`（无需重启）。用 `deploy/bin/check-env.ps1` 可在上线前
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
