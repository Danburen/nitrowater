# Day 7 反思录 · huangcong

> 日期：2026-10-11 – 10-12（提交 `f2a8b6f` / `8903cf5` / `dfe26ea` / `6d87d76` / `6d5f0d3` / `f067270` / `3e11dad`）｜ 项目：NitroWater
> 关键词：资源服务自托管（SeaweedFS/S3/COS 三后端）、account 全站头像 P0、deploy 对齐 WaterFun、Piston 判题闭环、题目域（双语/tag/题解/题单）、管理端 UI、Playground 前端、运行器后端化、`.ai/testkit` 工具箱、best_score 修复

## 一、今天的反思区

### 1. 今天做了什么
- **资源服务自托管（`f2a8b6f` 导入 / `8903cf5` 完善）**：新增独立模块 `nitrowater-resource`（:8093）——`resource` 表 + `FileStorage` 抽象（**S3=SeaweedFS / COS / LOCAL** 三后端，可插拔）+ presigned 上传 / 读取 + RS 校验 SSO JWT；BFF 条件路由 `/api/resource/**`、`/api/public/resource/**` → :8093。读侧加 **presigned URL 缓存**（Redis `nitrowater:cache:resource:read:{uuid}`，TTL=签名有效期−余量，删除即失效，**Redis 挂则降级**）+ `DELETE /api/resource/{uuid}`（owner 幂等）+ 匿名 `/api/public/resource/{uuid}/meta` + 读响应 `Cache-Control` + LOCAL 流式读补 `Content-Length`；MinIO 依赖移除 → **AWS SDK v2(S3)**；新增 `S3_PUBLIC_ENDPOINT`（presigned 浏览器可达）。Docker 化 `Dockerfile` + compose，实测 Flyway V1 应用、8093 起、公共 404 JSON / 受保护 401。
- **account P0 全站头像（`dfe26ea`）**：`user.avatar_resource_uuid CHAR(36)`（策略 A 改基线 + 重置库），**仅存 uuid 不存 URL**；OIDC 输出 `picture` = `${app.avatar.picture-base-url}/api/public/resource/{uuid}`（稳定路径永不过期）；新增 `PUT /api/auth/account/avatar`；实测 id_token / userinfo 均含 `picture`。
- **deploy 对齐 WaterFun**：`deploy/docker/{.env.example, docker-compose.{infra,app,piston}.yml}` + 根**单文件多目标** `Dockerfile`（account/resource/server/bff，产物同名 `app.jar`）+ 根 `.dockerignore` + external 网络 `nitrowater-net`；移除旧 `deploy/compose.yaml` 与模块级 Dockerfile；infra+app 实测通过。
- **Piston 沙箱导入 + 判题闭环**：Docker 起 Piston（`--privileged -p 2000:2000`）；官方镜像**默认无语言**，直连 GitHub 被阻断 → **经 ghproxy 代理重写索引**装入 java/gcc/node/python/typescript/lua；修 `PistonConfig` **强制 HTTP/1.1**；新增 `bin/start-piston.bat`（含 `PISTON_*` 限额）+ `bin/install-piston-langs.ps1`；种题 → 提交 → Redis 入队 → `JudgeWorker` → verdict=AC、积分 +10。
- **v2 业务 API + 判题（`6d87d76` 前）**：biz `V1__biz_baseline.sql`（8 表）+ 10 枚举（`TINYINT` + `@Converter`）/ 8 实体 / 7 仓储；题库公开只读 `/api/public/problems[/{id}]` + 管理员 upsert；提交 `POST /api/submissions`（202 入队）/ 轮询 / 列表；`JudgeQueue`(Redis list) + `JudgeWorker`(单线程) + `OutputComparator`(EXACT/TOKEN/LINES) + 积分幂等。
- **架构定稿·传统分层（`6d87d76`）**：放弃全量 DDD，定稿 `config/infra/service/web`；子包 `infra/{entity,enums,exception,ratelimit,repository,third}` + `service/{judge,problem,sandbox,shared}`；Piston→`infra/third`、Judge/OutputComparator→`service/judge`。
- **Jackson 2→3 统一（`6d5f0d3`）**：`JsonUtil` 重写（java.time 内置、`WRITE_DATES_AS_TIMESTAMPS` 迁 `DateTimeFeature` 默认 false）；lib/core/account 换依赖；包名 `tools.jackson.*`。
- **题目 slug 自动生成**：lib 加可插拔 `Romanizer`；server 加 `PinyinRomanizer`（pinyin4j 离线）+ `SlugGenerator`（冲突 base62 重试）；admin 手填优先、空则由 title 生成；`SlugGeneratorTest` 5/5（`两数之和 → liang-shu-zhi-he`）。
- **题目域·双语 + tag + 题解 + 题单 + 管理端（`f067270`）**：直列 `_en`（title_en/description_en）；`category` → **题单** `problem_set` + `problem_set_item`（一题多题单，成员从 problem 侧 replace-all）；`tag`(TOPIC/COMPANY) + `problem_tag`；题解 `problem_editorial`(1:N，全可见 LeetCode 式)；slug 手填 > 英文标题 > 拼音；controller 按角色拆（public `/api/public/**` vs `AdminXxxController @PreAuthorize`）；管理端前端 `features/admin/*`（AdminLayout + 问题/标签/题单/题解）；改 V1 baseline + 重置库重放。
- **Playground 前端 + 运行器后端化 v1**：`features/playground/*`（列表/详情/提交/轮询）+ 路由 `/tools/playground[/:id]`；server `sandbox` 包直连 Piston、`POST /api/sandbox/run`（**仅登录**）、per-IP 滑动窗口 + 全局并发闸、BFF 覆写 XFF + 读超时、前端登录门控。
- **best_score 恒 0 修复**：`JudgeService.awardIfSolved` 改为**显式接收 `judge()` 传入的 `score`**（不再读 `finish()` 前载入的旧内存 `submission.getScore()`）；DB 实测 `best_score=10`。
- **`.ai/testkit` 工具箱**：8 脚本（`_common` / `check-services` / `start-test` / `stop-test` / `reset-db` / `seed-test-users` / `seed-demo-problem` / `playwright-smoke`）+ `README.md` + `PLAYWRIGHT-MCP.md`；判题 e2e OIDC 全链路 `PASS=4`（修 `Submit-And-Poll` 漏带 `WebSession` → 401）；`smoke-oidc` 复跑通过（refresh_token issued）。
- **wtools-B2 收尾**：`VideoToGifTool.doConvert` 的 `deleteFile('input/palette/output')` 从成功路径移入 `finally`（`.catch` 容错）。
- **提交固化**：`f2a8b6f` / `8903cf5` / `dfe26ea` / `6d87d76` / `6d5f0d3` / `f067270` / `3e11dad`；工作区干净。

### 2. 收获与认识
- **资源服务"三后端可插拔 + presigned"是把存储做成基础设施的范式**：业务不碰字节流，只拿签名 URL 直传直读；`LOCAL` 兜底让"本地无云"也能跑。**接口在"存储后端会变"的那条缝上抽象**，与运行器 `runtimeManager` 同构。
- **presigned 签名是有成本的**：同一对象重复读应缓存签名（Redis，TTL 留余量、删除即失效），且 **Redis 不可用要降级而非报错**——基础设施不能是单点。
- **"只存 uuid 不存 URL"是稳定引用**：头像 `picture` 由 uuid + base 派生，换存储 / 换域名不动数据；与"跨服务引用用 uuid"约定一致。
- **评估 → 拍板 → 落地 → 提交 → 打 tag 的节奏继续兑现**：资源先行、Playground 方案拍板（直连 Piston）、管理端 controller 拆角色，都是"先定调、再动手"。
- **工具化收尾**：`.ai/testkit` 把"重建库 / 造角色用户 / 健康检查 / 启停 / Playwright 快测"变成一条命令——**把反复的手工操作沉淀成脚本，是给未来的自己省时间**。

### 3. 踩坑与教训（今天最值钱的一栏）
- **Piston 官方镜像默认无语言 + 直连 GitHub 被阻断**：必须经代理（ghproxy）重写索引装入语言；`PISTON_*` 限额不配 → 请求 400。
- **JDK HttpClient 对明文 HTTP 发 h2c 升级 → Express body-parser 400**：`PistonConfig` 强制 HTTP/1.1 才通。
- **best_score 恒 0 的根因**：`awardIfSolved` 读的是 `finish()` 前载入的**旧内存 score**。教训：**展示字段不一定等于结算字段，评分链路要盯"值从哪来"**。
- **testkit 起 jar 的三连坑**：`java -jar` 必须把 WorkingDirectory 设为仓库根（account 的 `jwt.private-key=file:./deploy/keys/...` 是相对路径）；`Start-Process` 两个重定向文件不能相同；Redis Windows 服务**假活** → 直接 `redis-server.exe` 起。
- **smoke-oidc 是交互式的**：`Read-Host` 收 piped stdin 可用；验证码从 Redis `nitrowater:biz:verify:captcha:{uuid}` 读；账号库重置后默认 `smokeuser1` 不存在 → 用 `testuser1/Passw0rd123`。
- **管理端浏览器调试法**：BFF 注册 id 是 `nitrowater`（非 `nitrowater-bff`）→ 授权入口 `http://localhost:8080/oauth2/authorization/nitrowater`；SSO 登录用**表单 POST /login**，验证码从 Redis 读。

### 4. 下一步计划
- **题解 / 题单 / 标签管理端细节打磨**（体验、校验、边界）。
- **`docs` 产物并入 `/docs/**`（P1）**：VitePress `base=/docs/`，SPA 回退排除。
- **清理 DB 惰性行**：`nitrowater-web` 客户端行 + 旧 public client 残留；运行器**匿名限流压测**。
- **Phase 2.8 网关联 JWKS**（等上游评审窗口）。
- **编码规整（评估）**：按"文件类分策"处理 `.ai`（见对话 4），不搞一刀切。

## 二、对话档案

### 对话 1 · 工具箱收尾要什么？
**我**：继续收尾，收尾后需要你在 `/.ai` 下建一个文件夹，快速重建库、快速添加各类角色的测试 user、快捷检测各服务是否启动的快捷脚本，方便启动测试、关闭测试、Playwright 快捷测试。
**AI**：拆成 8 件套（`_common` / `check-services` / `start-test` / `stop-test` / `reset-db` / `seed-test-users` / `seed-demo-problem` / `playwright-smoke`）+ README；统一约定（UTF-8+BOM、退出码=必需 DOWN 数、幂等）；先勘察落 `2026-10-12-testkit-recon.md` 再编码。
**结论**：8 脚本落地，解析 0 错，`check-services` 退出码 0。

### 对话 2 · 下一步做什么？
**我**：这部分做完之后，需要你看一下下一步做什么？
**AI**：给三个候选——① 起 Docker+Piston 跑判题 e2e；② 小收尾（Video→GIF `deleteFile`、复跑 smoke-oidc、best_score 复核）；③ 先刷新 `current.md`。并提示"大量功能改动仍未提交"。
**结论**：用户选全部；三项均完成（判题 `PASS=4`、smoke-oidc 复跑过、best_score=10）。

### 对话 3 · 提交与记录
**我**：我已提交，需要你记录一下日志。
**AI**：按 log-context 规范——路由判定（`nitrowater` 未命中 `job\log` → 普通日志 `.ai\context`）；`current.md` 定点编辑 + `events.log`（GBK）追加 6 行事件；写后严格解码复验（UTF-8 无 BOM / GBK 未变、行尾未变）。
**结论**：日志落盘并复验通过。

### 对话 4 · `.ai` 编码要不要统一？+ 写 Day 7
**我**：有没有必要把 `/.ai` 下的所有文件都统一 UTF-8 no BOM？同时把截至目前的写一下 day7 的反思录，可以参考前几个（并贴出 Day 6 完工报告作范例）。
**AI**：给"按文件类分策"的结论——`.md` 已全 UTF-8 无 BOM（保持）；`.ps1` 必须 UTF-8 **with BOM**（勿改）；`events.log` 为 GBK（唯一非 UTF-8 文本，可评估迁移但需同步改 AGENTS/技能）；`logs/*.log` 为运行时产物。**不建议一刀切**（会破坏脚本与历史日志）。
**结论**：awaiting 用户对 `events.log` 是否迁移的拍板；本反思录产出。

## 三、AI 提示词与协作过程记录（重点）

### 提示词演进（用户侧）
1. **"继续收尾，收尾后在 `/.ai` 下建文件夹……快捷脚本"（带交付清单的指令）**——把"造数 / 启停 / 快测"这类**反复的手工操作**明确要求沉淀为工具；AI 先勘察设计（recon 附件）再编码，避免边写边改。
2. **"这部分做完之后，需要你看一下下一步做什么？"（要选项，不要瞎干）**——用户给完一段任务后要"下一步建议"；AI 列候选 + 风险提示（未提交），由用户勾选。
3. **"我已提交，需要你记录一下日志"（提交与记录同级）**——把"写日志"当同日交付物；AI 按 log-context 规范（路由 / 编码探测 / 定点编辑 / 写后复验）执行。
4. **"有没有必要把 `/.ai` 下所有文件统一 UTF-8 no bom……先评估"（先评估再动手）**——对"规整性改造"先要结论 + 推荐 + 风险；AI 给分类判断、**不动手**等拍板。**"先评估"是本项目的协作基线**。
5. **"把截至目前的写一下 day7 的反思录，可以参考前几个，同时直接输出：<Day 6 完工报告>"（拿既有产物当格式契约）**——用上一份报告对齐结构，减少反复沟通。

### 协作过程要点（AI 侧）
- **先诊断后动手**：401 = `Submit-And-Poll` 漏带 `WebSession`；best_score = 旧内存 score——都先定位根因再改，改动最小。
- **评估先行、不越界**：编码统一问题只给结论与推荐，等用户拍板；不擅自改 `events.log` 或脚本编码。
- **证据留痕**：判题 `PASS=4`、`best_score=10`(DB)、`smoke-oidc exit=0`、`npm run lint`=0/`build`=0、编码写后复验（严格解码 + 行尾/BOM 未变 + 关键词命中）——结论都有数字/断言。
- **遵守约定**：无 subagent（主进程直做）、`.ps1`=UTF-8+BOM、`.ai/*.md`=UTF-8 无 BOM、`events.log`=GBK、Redis 命名空间 `nitrowater:*`、跨服务引用用 uuid。
- **收尾与提交纪律**：当日工作当日固化（提交后工作区 clean）；待办（`deleteFile`、smoke-oidc、docs 并入）逐条销账而非隐去。
