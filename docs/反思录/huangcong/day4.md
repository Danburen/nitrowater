# Day 4 反思录 · huangcong

> 日期：2026-10-08（提交 `0a1cd28`，tag `day4`）｜ 项目：NitroWater
> 关键词：BFF 无敏感认证、浏览器零 token、confidential client、Redis 会话、CSRF、`/api/**` 代理

## 一、今天的反思区

### 1. 今天做了什么
- **路线拍板：从 iframe 静默续期转向 BFF**：用户明确指出"iframe 无感刷新依赖 cookie，不是推荐方案"。AI 评估现状后确认：iframe 依赖同站 Cookie（第三方 Cookie 收紧即断）、AT 落 `localStorage`（XSS 可窃）、公共客户端拿不到 RT（SAS 的 `isPublicClientForAuthorizationCodeGrant` 是闸门，字节码实证）、无真登出/即时吊销。目标定为 **BFF Token Handler**。
- **分叉决策**：A1 **同源托管 SPA**（BFF 托管构建产物，无 CORS）/ 新建独立模块 `nitrowater-bff`（对应原 `waterfun-gateway` 边界）/ 前端**直接替换**（删 oidc-client-ts，不留双轨）。
- **`nitrowater-bff` 模块**（Boot 4.1.1，Servlet/WebMVC 栈，遵守"响应式迁移冻结"）：`oauth2Login`（confidential client）+ CSRF（`CookieCsrfTokenRepository` + 自定义 `CsrfCookieFilter` 强制落 `XSRF-TOKEN` cookie）+ Spring Session 落 Redis（命名空间 `nitrowater:bff:session`）+ `/api/**` 需会话且返回 **401**（非 302，SPA 可感知）；静态资源 Cache-Control 不被全局 no-store 覆盖（`DelegatingRequestMatcherHeaderWriter` 只对 `/bff/**`、`/api/**` 加 no-store）。
- **`ApiProxyController`**：`/api/**` → RS——`OAuth2AuthorizedClientManager.authorize()` 取/自动刷新 AT → `RestClient` 代理；排除 11 个 hop-by-hop/敏感头（`authorization`/`cookie`/`content-length` 等），回包滤掉 `access-control-*`。
- **account 侧**：seeder 新增 confidential client `nitrowater-bff`（auth-code + refresh_token + redirect `/login/oauth2/code/nitrowater`）。
- **前端切换**：`bff.ts`（`/bff/me`、login 整页跳 `/oauth2/authorization/nitrowater`、logout `POST /logout` 带 CSRF、`apiFetch` 自动挂 `X-XSRF-TOKEN`）；`useAuth` 改为会话状态；删除 `OidcCallbackView`/`SilentCallbackView`。
- **lib 抽取辅助**：用户主导迁移无状态工具到 `nitrowater-lib`；AI 修 `build.gradle`（缺 Boot BOM → `Could not find lombok:.` 版本解析为空）、补 `validation`/`annotations`/`hutool` 依赖；处理 `lib ↔ core` 反向引用边界。

### 2. 收获与认识
- **confidential client 才能"合法"拿到 RT**：RT 的闸门不在"grant 是否注册"，而在客户端是否 public；BFF 把 RT 收服务端，用标准 `refresh_token` grant 静默续期，**一次性消灭 iframe、跨站 Cookie、XSS 窃 token 三个问题**。
- **浏览器零 token 的连锁收益**：无 CORS（同源托管）、可即时吊销/踢下线、真登出（后续 day5 的 `sid` 才有意义）、SPA 逻辑大减（`useAuth` 只剩一个 `/bff/me`）。
- **Boot 4 starter 改名坑提前规避**：OAuth2 系列全部带 `security-` 前缀（`web→webmvc`、`...-oauth2-client`），靠 websearch 核实而非凭记忆。

### 3. 踩坑与教训（今天最值钱的一栏）
- **BFF 没起 = 5173 一切代理请求 502**：`/oauth2`、`/login`、`/api`、`/bff` 全走 Vite proxy → 8080，8080 不在连接即拒。启动顺序不可逆：`MySQL/Redis → account(8090) → bff(8080) → server(8081) → web(5173)`；BFF 启动会拉 AS 发现文档，AS 未起 BFF 直接失败。
- **lib 模块必须自带依赖管理**：sibling 库抄 core 的 Boot 插件 + `io.spring.dependency-management`，不能只 `id 'java'` + 无版本号依赖。
- **依赖方向红线**：`core → lib`，lib 里不许反向 `import cn.nitrowater.core.*`（`AuthException`/`OptionConverter` 引用 `AuthCode`/`OptionVO` 构成环）；通用类型应下沉 lib。
- **SPA fetch 登出带不上 Cookie**（对 `/logout` 的 fetch 跨不了会话语义）→ 后续改整页表单 POST（见 day5）。
- **登出 400 `invalid_token(sid)` 的根因链**（今天暴露、day5 修复）：`SsoUserPrincipal` 不实现 `equals/hashCode` → JDBC 反序列化成"新实例" → `SessionRegistryImpl` 查不到会话 → id_token 无 `sid`。
- 延续项：`.ps1` UTF-8 with BOM；`@EntityScan`/`@EnableJpaRepositories` 字符串字面量改包名要同步。

### 4. 下一步计划
- **RP-initiated 真登出**：account 引入 Spring Session 使 id_token 带 `sid`，BFF 恢复 `id_token_hint` 登出（day5 完成）。
- 前端换装 Tailwind v4 + wtools 工具迁移（day5 主线）。
- 收尾：复跑 `smoke-oidc.ps1`（应报 RT issued）、清理 DB 惰性 `nitrowater-web` 客户端行。

## 二、对话档案

### 对话 1 · iframe 要不要换掉？
**我**：我计划完善 oidc+oauth2+BFF 架构做单点登录，先评估现有模式再实现；iframe 无感刷新依赖 cookie，不是推荐方案。
**AI**：确认三条弱点（iframe/同站 Cookie、localStorage/AT、public client/无 RT），锁定 **BFF** 为终态；列出两个分叉请你拍板——A1 同源托管 vs A2 独立部署 + CORS、B1 新模块 vs B2 并入 server；另确认是否直接替换前端。
**结论**：用户答 A1（同源）、直接替换、新建模块。

### 对话 2 · 评估文档先落盘学习
**我**：把这个评估放到日志里吧，我学习一下再给你决策。
**AI**：产出 12.8KB《BFF 评估》（13 节：三条路线对比、RT 拿不到根因的字节码证据、§7 BFF 为何能合法拿 RT、分叉 §9 待拍板、P1–P3 计划），写入 `.ai/context/2026-10-08-bff-evaluation.md`，**拍板前不动代码**。
**结论**：评估-学习-拍板-实施四步协作节奏确立。

### 对话 3 · lib 编译失败怎么办？
**我**：迁移无状态通用 lib 报 `Could not find lombok:.` / `jackson-databind:.`。
**AI**：根因 = lib 只有 `id 'java'`、无版本号依赖、无 BOM；照抄 core 的 Boot 插件 + dependency-management 即修；补 validation/annotations/hutool；再清理 lib→core 反向引用。
**结论**：依赖补齐、边界收敛，`gradlew build` 绿。

## 三、AI 提示词与协作过程记录（重点）

### 提示词演进（用户侧）
1. **"先评估现有模式，然后实现 + 预先亮明技术立场"**（"iframe 不是推荐方案"）——一句话同时锁定范围与方向，AI 不会回头沿用默认方案，且评估目标明确。
2. **"评估放到日志里，我学习后再决策"**——把 AI 产出物从"聊天记录"升级为"可审阅资产"（`.ai/context/*.md`），决策权在人；这是本项目**最重要的协作节奏**：架构级变更 = 评估落盘 → 用户学习 → 用户拍板 → 实施。
3. **"我来手动创建模块骨架"** → 用户保留对工程边界的控制权；AI 后续用 TODO continuation 机制自行补齐实现（记录为一次边界案例）。

### 协作过程要点（AI 侧）
- **分叉问题先问、不闷头做**：A1/A2、B1/B2 影响部署形态与模块归属，实施前一次性问清（question 工具），避免返工。
- **验证必须走真实浏览器**：此前一次"静默续期已实测通过"被证明是**假阳性**（HttpWebRequest 不执行浏览器 iframe 策略）；后用真实浏览器 + 路由注入 `X-Frame-Options: DENY` 做 A/B 实证，才定论 XFO 是真实阻塞点。**教训：浏览器安全策略相关结论，必须真实浏览器取证。**
- **版本不确定性用工具核实**：Boot 4 starter 改名用 websearch 二次确认，不凭记忆。