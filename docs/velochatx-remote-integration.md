# VeloChatX ↔ nitrowater 远程监控对接方案（N 侧落地设计）

> 目标：把 `VeloChatX/docs/remote-monitoring-plan.md` 的协议落到 nitrowater 现有工程里。
> 结论：后端业务全部放 **nitrowater-server**（业务库/Redis/SSE 都在这里），**BFF 只加薄代理**，
> **前端加 key 管理 + 实例注册表 + 监控页**。不改 SSO / account / resource。
> 身份模型：**用户级 api-key + V 自生成 UUID**，实例 = `(key_id, instanceId)` 二元组，每 key 最多 3 台。
> 本文只做设计与落点说明，不含实现代码。

---

## 0. 关键结论（先看这个）

| 约束（来自 nitrowater 现状 / 本方案） | 对方案的影响 |
|---|---|
| BFF 强约定：`/api/**` 是唯一数据命名空间，`/api/public/**` 有意匿名 | **不能**新建 `/agent/**` 顶级命名空间；实例端点改为 `/api/public/instance/**` |
| `ProxyHeaders.FORWARDED` 白名单**不含 `authorization`**，响应侧显式丢弃 `authorization`/`set-cookie` | 通用 `ApiProxyController` 会把 V 的 api-key 剥掉；**必须新增专用 BFF 代理**逐字转发 `Authorization` |
| BFF 全局 Cookie CSRF，`permitAll` **不豁免** CSRF | V 的心跳/状态/结果是 **POST**，必须给 `/api/public/instance/**` 加 **CSRF 豁免**，否则 403 |
| 所有业务 JSON 必须走 `ApiResponse<T>`（`{success,code,message,data}`） | V 读 `.data`；错误看 `.code`；**SSE 帧不包裹**（协议层例外） |
| Servlet/WebMVC 冻结，SSE 用 `SseEmitter` | 沿用 `AiAgentController` 的 `SseEmitter` 模式，不引入响应式栈 |
| BFF 的 SSE 必须专用控制器逐块 flush | 命令 SSE 和浏览器 SSE 都要专用代理，不能走通用缓冲代理 |
| `/internal/**` 被 BFF `denyAll` | 外部 V 实例**不能**走 `/internal/**`；api-key 认证放在 `/api/public/instance/**` 业务服务里 |
| 时间列 `TIMESTAMP(3)`、枚举 `TINYINT UNSIGNED` + `@Converter`、表名单数 | 迁移脚本按此写；Flyway 下一条为 `V2__...`（server 当前只有 V1 基线） |
| **一把 key 被同一用户多台 V 共用（≤3）** | **key 只定用户，不能焊死实例 id**；实例身份 = `(key_id, instanceId)`，需新增注册表 + 显示名生成 + 配额校验 |
| **克隆数据目录会产生重复 instanceId** | SSE 后到者 409、**先到者保留**；收到 409 的 V 自动生成新 UUID 重连（见 §2.4） |
| **SSE 常驻，不随 viewers 关闭** | AI/UI 随时可命令任意实例；viewers 只控制 5s status 开关 |

---

## 1. 最终端点契约

### 1.1 实例侧（V → N，api-key 认证）

Base URL：`https://n.example.com/api/public/instance`

| 方法 | 路径 | 说明 | 返回 |
|---|---|---|---|
| POST | `/api/public/instance/heartbeat` | 30s 常驻；首绑注册 + 门控 | `ApiResponse<HeartbeatResp>` |
| POST | `/api/public/instance/status` | 5s，仅 viewers>0；payload ≤2KB | `ApiResponse<StatusAck>` |
| GET | `/api/public/instance/commands/stream` | **SSE 常驻**；`Last-Event-ID` 重放 | `text/event-stream`（不包裹） |
| POST | `/api/public/instance/commands/{id}/result` | 命令结果回传 | `ApiResponse<ResultAck>` |

请求头统一 `Authorization: Bearer nw_live_<43字符>`。SSE 连接建立时认证一次，后续事件不再逐条鉴权。

`HeartbeatReq.data`（请求体，关键字段）：

```json
{ "v": 1, "instanceId": "<V 生成的 UUID>", "bootId": "...", "seq": 1234,
  "startTime": 1763900000000, "pluginVersion": "2.1.0", "proxyVersion": "3.4.0" }
```

`HeartbeatResp.data`：

```json
{
  "v": 1,
  "instanceId": "...",
  "displayName": "instance-1Im3eA",   // 首绑生成、可在 NW 改；V 缓存展示
  "serverTime": 1763900030000,
  "viewers": 0,
  "reportIntervalMs": 30000,
  "instanceState": "IDLE"
}
```

- `viewers` = 当前正在看该 `(key_id, instanceId)` 的浏览器 SSE 会话数（内存计数，不落库）。
- `reportIntervalMs`：`viewers == 0 → 30000`，`> 0 → 5000`。**只控制 5s status，不影响 SSE**。
- `instanceId` 由 V 生成并经注册表登记；N 不信任"请求体里的 id 属于别人"——归属由 key 决定（key → owner_uid），id 只是该 owner 下的机器标识。

### 1.2 浏览器侧（B → N，OIDC 会话 + JWT）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/ui/stream?instanceId=...` | 登录用户 | SSE：status / command_result fan-out |
| POST | `/api/ui/commands` | `ROLE_ADMIN` | 下发命令，返回 `202` + `ApiResponse<{id}>` |
| GET | `/api/ui/instances` | 登录用户 | 实例列表（注册表）+ 最近一次状态（内存） |

### 1.3 管理侧（key 生命周期 + 实例注册表）

**key 管理**：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/instance-credentials` | 分页列表（脱敏，只显 kid/last4） |
| POST | `/api/admin/instance-credentials` | 签发**用户级** key：N 生成 kid + key（**不绑实例 id**），明文**只返回一次** |
| POST | `/api/admin/instance-credentials/{id}/revoke` | 吊销（宽限期内旧 key 仍可上报，发命令立即拒绝） |

**实例注册表**（每 key 最多 3 台，`InstanceLimits.MAX_INSTANCES_PER_KEY = 3` 常量）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/instance-credentials/{id}/instances` | 该 key 下的实例列表（显示名 / UUID / lastSeen / bootId / 状态） |
| POST | `/api/admin/instance-credentials/{id}/instances/{instanceId}/rename` | 改显示名（body: `{displayName}`，按 owner 唯一） |
| POST | `/api/admin/instance-credentials/{id}/instances/{instanceId}/remove` | 移除实例，**释放槽位**（软删 `removed_at`） |

沿用现有 `/api/admin/**` 兜底规则 + 控制器 `@PreAuthorize("hasAuthority('ROLE_ADMIN')")`。

**签发流程（key 只定用户，实例由 V 自带）**：运维在 NW 管理页填备注 → 点击「生成实例凭据」→
N 签发用户级 key（带 `kid`/`scopes`/`owner_uid`，**不绑定任何实例 id**），明文 key 只展示一次 →
运维把**同一把 key** 贴到最多 3 台 V（`vcx remote set <key>`）→
每台 V 首次启动自己生成 UUID，首次心跳把 `(key_id, instanceId)` 注册进来 →
N 校验配额（<3）后登记、生成显示名并随心跳回显 → NW 页面可见并可改名/移除。

**显示名生成规则**：`instance-` + 6 位 base62，取 `base62(SHA-256(instanceId))` 前 6 位
（确定性、首绑重试幂等；如与该 owner 下已有显示名冲突则加盐重算）。用户可在 NW 手动改。
显示名按 owner 唯一，避免 AI 按名字寻址时歧义。

### 1.4 错误码（写进 `.code`，HTTP 状态只服务基础设施）

`instance_bad_protocol`(400) / `instance_unauthorized`(401) /
`instance_forbidden`(403) / `instance_limit_exceeded`(403) /
`instance_conflict`(409) / `instance_rate_limited`(429) / `instance_offline`(503)。

前端 `api/codes.ts` 的 `ResponseCode` 只追加这些字符串，不复用。

- `instance_limit_exceeded`：该 key 的注册表已有 3 台未移除实例。V 收到后**停止自动纠正并告警**。
- `instance_conflict`：同一 `(key_id, instanceId)` 已有活 SSE。V 收到后自动生成新 UUID 回写 H2 重连。

### 1.5 "裸 SSE / 白名单"的准确含义

用户确认的口述是"开放一条 whitelist 的 SSE，期望带 api-key 的裸 SSE 请求，而不是传统认证路线"。
落到实现上是三件事，**不是**网络/IP 白名单，也**不是**免认证：

1. **路径白名单**：Server 侧新增 `@Order(1)` 的 `SecurityFilterChain`，
   `securityMatcher("/api/public/instance/**")`，让这个前缀走 api-key 链而不是 JWT 链。
   BFF 侧 `/api/public/**` 本来就是 permitAll，只需额外做 CSRF 豁免（见 §3）。
2. **"裸"的含义**：请求只有 `Authorization: Bearer <api-key>` + `Accept: text/event-stream`
   （重连时加 `Last-Event-ID`），**没有** OIDC 会话 Cookie、CSRF token、`X-Device-Fp`、JWT。
   V 是服务端插件，拿不到这些，也不该走 SSO 登录。
   认证发生在 server 的 `InstanceApiKeyFilter` 里，不是"不认证"。
3. **V 不能用浏览器原生 `EventSource`**：它不能设自定义 Header；用 `HttpURLConnection` 读流。
   浏览器侧那条 `/api/ui/stream` 则**仍然走传统路线**（BFF OIDC 会话 + JWT），不要混为一谈。

即：全系统有**两条** SSE，认证方式不同：

| SSE | 消费方 | 认证 |
|---|---|---|
| `GET /api/public/instance/commands/stream` | V 插件 | api-key（专用链，裸请求），**常驻** |
| `GET /api/ui/stream` | 浏览器 | OIDC 会话 + JWT（BFF `BffTokenService`） |

---

## 2. nitrowater-server 改动（主体）

模块本身已具备全部基础设施：`spring-boot-starter-webmvc` + `security` + `oauth2-resource-server`
+ `data-jpa` + `flyway` + `data-redis`，`@Import({ApiResponseAdvice, GlobalExceptionHandler})` 已开，
组件扫描默认在 `cn.nitrowater.server`，新增包会被自动发现。

### 2.1 新增包结构

```
cn.nitrowater.server
├── config
│   ├── InstanceSecurityConfig.java          # @Order(1) 专用 api-key 链
│   ├── InstanceLimits.java                  # MAX_INSTANCES_PER_KEY = 3 常量
│   └── SecurityConfig.java                  # 现有链加 @Order(2)（唯一改动）
├── filter
│   └── InstanceApiKeyFilter.java            # Bearer api-key → InstanceAuthentication
├── web.instance
│   ├── InstanceHeartbeatController.java
│   ├── InstanceStatusController.java
│   ├── InstanceCommandStreamController.java
│   └── InstanceCommandResultController.java
├── web.ui
│   ├── UiStreamController.java
│   ├── UiCommandController.java
│   └── UiInstanceController.java
├── web.admin
│   └── AdminInstanceCredentialController.java   # key 管理 + 实例列表/改名/移除
├── service.instance
│   ├── InstanceCredentialService.java       # 签发/校验/吊销 + 缓存
│   ├── InstanceRegistryService.java         # 首绑登记 / 配额(3) / 显示名 / 改名 / 移除
│   ├── InstanceNameGenerator.java           # instance- + base62(sha256(uuid))[0:6]，冲突加盐
│   ├── InstanceSessionRegistry.java         # 内存：连接/viewers/重放缓冲/最近状态
│   ├── InstanceCommandService.java          # 下发/结果/离线判定
│   ├── InstanceViewerCounter.java            # 浏览器会话计数（可并入 Registry）
│   └── InstanceRateLimiter.java              # 心跳/上报频率与 429
├── domain.instance
│   ├── InstanceCredential.java / InstanceCredentialRepository.java / InstanceCredentialSpec.java
│   ├── InstanceRegistry.java / InstanceRegistryRepository.java / InstanceRegistrySpec.java
│   ├── InstanceAuthentication.java           # 自定义 Authentication（principal = key_id）
│   ├── InstanceScope.java                    # TINYINT UNSIGNED 枚举 + Converter
│   ├── InstanceCredentialStatus.java
│   └── dto/{HeartbeatReq,HeartbeatResp,StatusReq,StatusAck,CommandFrame,CommandResultReq,...}.java
└── resources/db/migration/V2__instance_credential.sql
```

### 2.2 安全链（关键）

现有 `SecurityConfig.securityFilterChain` 保持 JWT 校验不变，只加 `@Order(2)`。
新增 `InstanceSecurityConfig`：

```java
@Bean
@Order(1)
SecurityFilterChain instanceChain(HttpSecurity http, InstanceApiKeyFilter apiKeyFilter,
        ApiResponseAuthenticationEntryPoint entryPoint,
        ApiResponseAccessDeniedHandler denied) throws Exception {
    http.securityMatcher("/api/public/instance/**")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
        .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied))
        .addFilterBefore(apiKeyFilter, AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(a -> a.anyRequest().hasRole("INSTANCE"));
    return http.build();
}
```

- 链 1 只匹配 `/api/public/instance/**`，其余 `/api/public/**` 继续落到链 2 的 `permitAll`。
- `InstanceApiKeyFilter` 读 `Authorization`，校验前缀 + 长度，SHA-256 后查缓存/库，
  成功写入 `SecurityContext`（**principal = key_id**，authority：`ROLE_INSTANCE` +
  `SCOPE_TELEMETRY_WRITE` / `SCOPE_RPC_EXECUTE`）；
  失败用 `ApiResponseWriter.error(...)` 直接回 401 `instance_unauthorized`，不进控制器。
- **注册陷阱**：`Filter` 型 Bean 会被 Spring Boot 自动注册到 Servlet 容器，出现在 security 链里会执行两次。
  用 `FilterRegistrationBean<InstanceApiKeyFilter>` 设 `setEnabled(false)`，只交给 security 链。
- 命令下发端点再加方法级 `@PreAuthorize("hasAuthority('SCOPE_RPC_EXECUTE')")`，防止只有上报权限的 key 发命令。
- 链 2 里补一条 `.requestMatchers("/api/ui/commands/**").hasAnyAuthority("ROLE_ADMIN","ROLE_SUPER_ADMIN")`
  作为兜底（和 `/api/admin/**` 同一模式）。

### 2.3 身份、注册与唯一性

**key 校验与缓存**：

- 生成：`nw_live_` + 32 字节随机（base64url，43 字符），服务端只存 `key_hash = SHA-256(key)`。
- 校验：常量时间比较；先查本地 Caffeine（或 Redis `nitrowater:cache:instance-key:<hash前16位>`，TTL 60s），
  未命中再查库并回填。100 实例心跳 3.3 rps + status 20 rps 不算高，缓存主要削峰。
- 吊销：删缓存 + 置 `status=REVOKED`；`vcx remote set` 轮换时旧 key 保留一个宽限期（仅允许上报）。
- 常量时间比较照抄 `InternalPublishController` 的 `MessageDigest.isEqual`。

**职责边界：V 端不做认证。** N 签发 api-key，V 只做被动持有者：
`vcx remote set <key>` 加密存本地 H2 → 每次出站原样带 `Authorization: Bearer` → 401 停连告警 + 脱敏展示。
V 不校验 key 格式、不解析 scope、不签发/轮换、不管用户角色；这些全在 N 的
`InstanceApiKeyFilter`（认证）+ 管理端（生命周期）。

**首绑注册（心跳里完成）**：

`InstanceRegistryService.register(keyId, ownerUid, instanceId, bootId)`：

1. `SELECT ... WHERE key_id=? AND instance_id=? AND removed_at IS NULL`：
   - 命中 → 只更新 `last_seen_at` / `boot_id`，返回现有 `displayName`（幂等，重启不新增）。
   - 未命中 → 走 2。
2. 配额校验：`COUNT(*) WHERE key_id=? AND removed_at IS NULL >= MAX_INSTANCES_PER_KEY(3)` →
   抛 `instance_limit_exceeded`（403）。
3. 生成 `displayName = InstanceNameGenerator.generate(instanceId, ownerUid)`（按 owner 唯一，冲突加盐）。
4. 插入注册表（DB `UNIQUE(key_id, instance_id)` 兜底并发）。

**唯一实例的五道锁（权威在 N）**：

1. **身份归属**：身份 = `(key_id, instanceId)` 二元组。**key 定用户**（`owner_uid` 来自凭据），
   **instanceId 定机器**（V 自生成 UUID）。N 不信任"这个 id 属于别的用户"——归属只由 key 决定。
2. **key 唯一 + 配额**：DB `uk_instance_credential_hash`（hash 全局唯一）；每把 key 最多 3 台未移除实例
   （`InstanceLimits.MAX_INSTANCES_PER_KEY = 3` 常量 + `uk_instance_registry_key_instance`）。
3. **注册校验**：instanceId 必须已在该 key 的注册表里（首见自动登记，满 3 拒绝 403）。
4. **连接唯一**：一个 `(key_id, instanceId)` 只允许一条活 SSE；后到者 409，先到者保留；后到者 V 自动换 UUID 重连（见 §2.4）。
5. **冲突可观测**：`bootId` + `lastSeen` + `displayName` 在管理页展示；两台真机器克隆时能看到 bootId 跳变。

> V 侧没有 `remote.instance-id` 配置、也不读写 `identity.json`；UUID 存本地 H2，与 api-key 同表。
> 一把 key 合法挂最多 3 台同用户的机器，加机器只需贴同一把 key，无需找 N 重新签发。

### 2.4 内存模型与连接冲突

```
InstanceSessionRegistry
  instanceStreams   : ConcurrentHashMap<instanceKey, InstanceStream>   # instanceKey = key_id + ":" + instanceId
  latestStatus      : ConcurrentHashMap<instanceKey, StatusSnapshot>   # 浏览器首屏
  lastSeen          : ConcurrentHashMap<instanceKey, Instant>          # 心跳
  replayBuffer      : ConcurrentHashMap<instanceKey, Deque<CommandFrame>>  # Last-Event-ID 重放（TTL 5min）
  viewerSessions    : ConcurrentHashMap<instanceKey, Set<SseEmitter>>  # 浏览器 SSE 集合
  viewers(instanceKey) = viewerSessions.get(instanceKey).size()
  InstanceStream = { SseEmitter emitter, AtomicBoolean alive }         # alive 供冲突判定
```

- `SseEmitter.onCompletion/onError/onTimeout` 必须把 `alive` 置 false 并清理对应 map，否则 viewers 泄漏、命令写到死连接。
- **建连原子性**：`instanceStreams.putIfAbsent(instanceKey, newStream)`：
  - 成功 → 当前连接为先到者，保留。
  - 失败 → 取旧 stream：若 `!old.alive`（旧进程断线残留、map 未及时清）→ **直接替换，不报错**；
    若 `old.alive == true`（真有两台活机器）→ **在流打开前**回 409 `instance_conflict`，不改旧连接。
    这样正常重启不会被误判为克隆，只有真克隆才触发 V 侧自动纠正。
- 命令 id 用实例内递增 long；SSE 重连带 `Last-Event-ID`，重放 id 之后的缓存命令。
- 命令时浏览器无 SSE → 直接 503 `instance_offline`，不排队不落库（符合"N 不存业务数据"）。
  注意：这里判断的是**实例的 V-SSE 是否活着**，不是浏览器 SSE；SSE 常驻后，只有实例真离线才会 503。
- 状态/结果只内存扇出给浏览器，请求结束即丢弃，不写 MySQL。

### 2.5 SSE 控制器要点（照抄 `AiAgentController`）

- `new SseEmitter(timeout)`，`emitter.send(SseEmitter.event().id(...).name("command").data(json))`。
- 每 20s 发 `: ping` 注释帧（`SseEmitter.event().comment("ping")`），防 nginx/NAT 掐断，维持常驻。
- 错误在**流打开前**用 `ApiResponseWriter` + `Content-Type: application/json` 回（见 `SseErrorEnvelopeTest`），
  否则 SSE-only 客户端收到不可解析的错误体。409/403 必须走这条路。
- BFF async 超时会把长 SSE 掐断：首期给实例命令 SSE 设 **150s timeout**，V 侧按
  `Last-Event-ID` 自动重连（V 用 `HttpURLConnection`，需自己实现重连，不是 `EventSource` 的自动重连）。
  克隆检测不受影响——克隆一启动就建连，立刻撞 409。
- 浏览器 SseEmitter timeout 建议 0（长连接 + ping），或 170s 并靠 `EventSource` 自动重连（见 §3.3）。

### 2.6 统一监控逻辑层（UI 与 AI tool 共用）

用户要求的调用链：**AI agent 的 `@Tool` → 监控逻辑** 与 **UI 监控页 → 监控逻辑** 共用同一份实现。
落点是 server 侧一个 `MonitoringService`，两个入口只是适配层：

```
UI 控制器 ────────┐
                   ├─→ MonitoringService（唯一监控逻辑）
AI @Tool ─────────┘        │
                            ├─ snapshot(instanceId)         → 内存 latestStatus，即时，可能 stale
                            └─ liveQuery(instanceId, metric) → 命令往返（见下）
```

**AI/UI 多实例寻址**：`@Tool` 参数支持传 `instanceId`（UUID）或 `displayName`；
按名字解析时若该 owner 下重名（虽已约束唯一，仍要防御）→ 返回 ToolResponse 让用户选。
UI 侧实例列表来自注册表 `GET /api/ui/instances`。

**数据回传不是走 SSE**：SSE 是单向的（N → V，只下命令）。V 执行完后用
`POST /api/public/instance/commands/{id}/result` 把数据回传，N 收到后 resolve 对应的 pending future。
要拿双向能力得上 WebSocket，那会推翻"V 不开入站端口 + 只出站"的整个设计，不做。

**SSE 常驻带来的简化**：因为命令 SSE 常驻，`liveQuery` 不再有"实例 IDLE 无 SSE → 下不了命令"的问题，
AI 和 UI 对任意一台实例的命令**功能完全等价**。之前的 `ACTIVE = viewers > 0 || pendingQueries > 0`
门控可以去掉——viewers 现在只控制 5s status。

**三个最容易混淆的点**：

1. `5s` 是 **V → N 的状态上报周期**（`POST .../status`，纯数据），**不是** V 去轮询"取请求"。
2. 取命令走的是**常驻 SSE 长连接**，命令一产生就推，毫秒级；`30s` 心跳只负责 viewers 门控与首绑注册。
3. V 返回的是**方法执行结果**，不是"方法本身"。`method` 名字来自 V 侧 `RpcRegistry` 白名单
   （`status` / `broadcast` / `onlinePlayers` / `reload`），N 只能按名字调用，不能传任意代码。

完整链路：

```
浏览器 / AI tool ──请求──▶ N.MonitoringService
                               │
                 ┌─────────────┴─────────────┐
                 │ 命中内存快照 (SWR)          │ 未命中 / 过期
                 │ → 立即返回，零等待          │ → 转成 command
                 └─────────────┬─────────────┘
                               │ SSE 长连接（常驻，实时）
                               │──── command {id,method,params} ───▶ V
                               │◀── POST .../commands/{id}/result ── V
                               │
                               └─→ 结果经 UI SSE 推浏览器 / 返回给 AI tool（ToolResponse）
```

`liveQuery` 流程：

```
1. requestId = UUID；pending.put(requestId, CompletableFuture)   ← 内存，TTL 10s
2. SSE 下发 command {id, method, params}                          ← N → V
3. V 执行 → HTTP POST .../commands/{id}/result                    ← V → N（数据上行）
4. pending.get(id).complete(result) → future.get(timeout 8s)
5. UI 侧返回 ApiResponse<...>；AI 侧返回 ToolResponse（按 AGENTS.md 码表）
```

#### 缓存该放哪（关键结论）

| 缓存对象 | 放哪 | TTL | 理由 |
|---|---|---|---|
| `requestId → CompletableFuture` | **内存 ConcurrentHashMap** | 10s | 存的是回调对象，不可序列化；当前 server 单副本（compose 里 `container_name` + 无 replicas）；将来多副本用 Redis **pub/sub 做路由**，不是"缓存请求" |
| `instanceId → 最近一次状态/结果` | **内存 `InstanceSessionRegistry.latestStatus`** | 随 ACTIVE 更新 | 本来就要给浏览器扇出，数据已在内存；落库/落 Redis 违反"N 不存业务数据"，还产生陈旧数据；重启丢失是特性不是缺陷 |
| `Last-Event-ID` 重放缓冲 | **内存 Deque** | 5min | 短命，只需本进程重放 |
| api-key hash 校验结果 | Caffeine（或 Redis 缓存） | 60s | 这是**认证**数据，才适合放 Redis |
| 注册表（id/displayName/lastSeen） | **MySQL `instance_registry`** | 持久 | 这是**归属/管理元数据**，不是监控数据；显示名要持久化，槽位要跨重启统计 |

**所以监控请求/监控数据不要放 Redis。** Redis 在本方案里的正当用途是：api-key 校验缓存、限流计数、
以及多副本时的命令路由（pub/sub）。把监控 payload 写进 Redis 只会带来序列化成本 + 陈旧数据 + 违反数据主场约束。

#### 请求去重与延迟稀释

用户描述的模型是"浏览器收集各类监控请求 → 交给 V 去取"。去重分三层，**不要指望 V 做业务去重**：

| 层 | 位置 | 做法 |
|---|---|---|
| A. 浏览器合并 | `api/remote.ts` + 页面 store | `debounce 300ms`；`in-flight` 同 key 共享同一个 Promise（singleflight），避免连点产生多条命令 |
| B. N 侧 singleflight | `MonitoringService` | `Map<dedupKey, InFlight>`（TTL 10s）；同 key 在途直接复用 `CompletableFuture`；先看 `latestStatus`/`lastResult`，`age < freshMs` 直接返回缓存，根本不下发命令 |
| C. V 侧幂等 | `RpcRegistry` | 只按 `commandId` 去重（防 `Last-Event-ID` 重放导致重复执行），不做业务级合并，保持 V 简单 |

`dedupKey = key_id + ":" + instanceId + ":" + method + canonical(params)`。`canonical` 必须：map key 排序、字符串 trim、
**剔除 `requestId` / `issuedAt` / `traceId` 等易变字段**、数值按精度取整。否则语义相同的请求会被当成不同请求。

稀释延迟的四个手段（按收益排序）：

1. **SWR（stale-while-revalidate）**：先返回内存快照，后台刷新，新值经 UI SSE 推送。用户零等待，是最大的收益点。
2. **批量合并（batching）**：`200–300ms` 窗口内收集多个请求，合成一条 `batch` 命令下发；V 一次执行多条、一次回传，摊薄 RTT 和命令开销。
3. **UI 异步化**：浏览器请求立即返回 `202 + requestId`，不等命令往返；结果走 UI SSE 推送，用户交互与延迟解耦。
4. **预取（prefetch）**：监控面板打开时（浏览器 SSE 建立 → viewers>0 → V 开 5s status），立即批量拉该页全部指标，不等用户点击。

约束：
- `batch` 必须限制条数（如 ≤20）和总耗时（≤10s），防止一条批量命令卡死 V 的命令线程。
- `pending` / `in-flight` 的 future 必须在**结果回传或超时时清理**，否则内存泄漏且后续请求拿到已完成的旧 future。
- UI SSE 推送做有界队列 + 丢弃旧帧（backpressure），慢客户端不能阻塞 V 的 result POST。

#### AI tool 的延迟陷阱（已由 SSE 常驻化解）

原来 `@Tool` 同步语义下，V 在 `viewers=0` 时无 SSE，AI 查监控最坏要等
**30s 心跳 + 10s 命令超时 = 40s**。SSE 改为常驻后，命令通道始终在线，AI 与 UI 功能等价。
仍建议分级降级（AI tool 只返回 `ToolResponse`）：

1. 先看 `snapshot()`：有缓存且 `now - ts < freshMs`（如 15s）→ 直接返回，带 `stale=false`。
2. 缓存过期/缺失 → `liveQuery()`，`future.get(8s)`。
3. 超时 → 若有旧快照，返回旧数据并标 `stale=true` + `retryable=true`；无快照则返回
   `ToolResponse.of(ToolCode.UNAVAILABLE, "...", data)`。
4. views>0 时 5s status 提供新鲜快照；viewers=0 时快照可能旧，靠 SWR + `stale` 标记暴露新鲜度。

### 2.7 Flyway 迁移 `V2__instance_credential.sql`

遵循 AGENTS.md：表名单数、`TIMESTAMP(3)`、枚举 `TINYINT UNSIGNED` + COMMENT、`uk_`/`idx_`/`fk_`、
`ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`；跨服务引用用 uid（逻辑，不建跨库 FK）。

```sql
CREATE TABLE instance_credential (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  key_id        VARCHAR(64)  NOT NULL COMMENT 'kid，展示用',
  key_hash      CHAR(64)     NOT NULL COMMENT 'SHA-256(api-key)，鉴权索引用',
  key_prefix    VARCHAR(16)  NOT NULL COMMENT 'nw_live_ + 前4位，展示/排错',
  key_last4     VARCHAR(8)   NOT NULL,
  scopes        VARCHAR(256) NOT NULL COMMENT '逗号分隔：telemetry:write,rpc:execute',
  status        TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=ACTIVE 1=ROTATED 2=REVOKED',
  owner_uid     BIGINT UNSIGNED NULL COMMENT '逻辑关联 biz_user.uid，无跨库 FK',
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  revoked_at    TIMESTAMP(3) NULL,
  last_used_at  TIMESTAMP(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_instance_credential_kid (key_id),
  UNIQUE KEY uk_instance_credential_hash (key_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VeloChatX 用户级 api-key（只存 hash，不绑实例 id）';

-- 实例注册表：一把 key 挂最多 3 台 V；身份 = (key_id, instance_id)
CREATE TABLE instance_registry (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  key_id        VARCHAR(64)  NOT NULL COMMENT '逻辑关联 instance_credential.key_id',
  instance_id   VARCHAR(128) NOT NULL COMMENT 'V 插件自生成的 UUID',
  display_name  VARCHAR(64)  NOT NULL COMMENT 'N 首绑生成，用户可改，按 owner 唯一',
  owner_uid     BIGINT UNSIGNED NULL COMMENT '逻辑关联 biz_user.uid',
  boot_id       VARCHAR(64)  NULL COMMENT '最近上报的进程 UUID，仅展示排错，不参与自动纠正',
  first_seen_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  last_seen_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  removed_at    TIMESTAMP(3) NULL COMMENT '软删；非 NULL 时不占槽位',
  PRIMARY KEY (id),
  UNIQUE KEY uk_instance_registry_key_instance (key_id, instance_id),
  KEY idx_instance_registry_owner (owner_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VeloChatX 实例注册表（归属/管理元数据）';

-- 审计只记元数据，不记 payload（避免把监控业务数据落库）
CREATE TABLE instance_command_audit (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  command_id  VARCHAR(64) NOT NULL,
  instance_id VARCHAR(128) NOT NULL,
  method      VARCHAR(64) NOT NULL,
  issued_by   BIGINT UNSIGNED NULL,
  result      TINYINT UNSIGNED NULL COMMENT '0=OK 1=ERROR 2=TIMEOUT',
  issued_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_instance_command_audit_id (command_id),
  KEY idx_instance_command_audit_instance (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='命令审计（仅元数据，不含参数/状态）';
```

实体/仓储/规格按三段式：`InstanceCredential` → `InstanceCredentialRepository extends JpaRepository<...,Long>, JpaSpecificationExecutor<...>` → `InstanceCredentialSpec.of(...)`（分页在库侧完成）；注册表同理 `InstanceRegistry*`。

---

## 3. nitrowater-bff 改动（代理 + 一处安全配置）

`SecurityConfig` 需要改**一处**：给 `/api/public/instance/**` 加 CSRF 豁免。
原因是 BFF 全局启用了 `CookieCsrfTokenRepository`，`CsrfFilter` 在鉴权之前执行，
`permitAll` 不会跳过它；V 的心跳 / 状态 / 结果都是 **POST**，没有 CSRF token 会被 403。
GET 的 SSE 是安全方法，不受影响，但同一路径前缀的 POST 必须豁免：

```java
.csrf(csrf -> csrf
        .ignoringRequestMatchers("/api/public/instance/**")   // 新增
        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        .csrfTokenRequestHandler(csrfRequestHandler))
```

其余安全规则不动：`/api/public/**` 已 permitAll，`/api/**` 已要求会话。
还需要新增**专用控制器**，因为通用代理会剥掉 `Authorization` 且缓冲 SSE。

### 3.1 `InstanceProxyController`（新增）

- `@RequestMapping("/api/public/instance/**")`：比 `ApiProxyController` 的 `/api/**` 更具体，优先命中。
- JSON 请求：逐字转发入站 `Authorization` + `content-type`/`accept`（**不要**调 `BffTokenService`，
  该路径没有会话；也不要改 `ProxyHeaders.FORWARDED` 全局白名单，会影响所有 `/api/**`）。
- 请求体用 `StreamingResource` 流式转发，不缓冲（照抄 `ApiProxyController`）。
- 响应过滤沿用 `ProxyHeaders.isRelayableResponseHeader`（会丢 `set-cookie`/`authorization`）。
- **409/403 必须原样透传**：V 靠 `.code == instance_conflict` 触发自动纠正，不能被 BFF 改写或吞掉。

### 3.2 `InstanceStreamProxyController`（新增）

- `GET /api/public/instance/commands/stream`：V 的命令 SSE。预处理下游状态/头，非 2xx 回 JSON 错误
  （409/403 在此路径返回，要让 V 读得到 `.code`）；2xx 则按 `AiStreamProxyController` 的模式逐块
  `write` + `flush`，带 `Content-Type: text/event-stream`、`Cache-Control: no-store`、`X-Accel-Buffering: no`。
- `GET /api/ui/stream`：浏览器 SSE。这个路径有会话，用 `BffTokenService.accessToken(...)` 取 Bearer JWT，
  `ReauthRequiredException` → 401 `session_expired`（同 `AiStreamProxyController`）。
- 两个映射都比通用 `/api/**` 更具体，不会冲突。

### 3.3 异步超时

BFF 当前 `spring.mvc.async.request-timeout: 180s`。两条长 SSE 会被掐断：
- 推荐：实例命令 SSE 的 `SseEmitter` 设 150s，V 侧带 `Last-Event-ID` 自己重连（几毫秒内恢复）。
  这是**常驻 SSE 的必要配套**——不处理的话，每 180s 会断一次，虽能重连但 viewers 会抖动。
- 或：把 BFF 全局超时调到 `0`（禁用），由各 SseEmitter 自己控制；AI 流自带 180s timeout，不受影响。
  这是配置变更，需单独评估，不作为首期默认。

---

## 4. nitrowater-web 改动

- `src/api/instance.ts`（或按域拆 `api/remote/`）：所有请求走现有 `apiFetch`（自动带 `X-Device-Fp`）；
  类型只放这里；新错误码追加到 `src/api/codes.ts` 的 `ResponseCode`
  （`instance_conflict` / `instance_limit_exceeded` 等）。
- `src/features/admin/AdminInstanceCredentials.vue`：Element Plus 管理页（签发/列表/吊销），
  用 `AdminFilterCard` + `AdminPage` + `AdminSettingsGroup` 等既有件；签发弹窗只显示一次明文 key。
- 实例注册表 UI：每个 key 展开显示其实例列表（`displayName` / UUID / lastSeen / bootId），
  提供「改名」「移除」按钮（对应 §1.3 端点）；移除前二次确认（会释放槽位）。
- `src/features/admin/RemoteMonitor.vue`（首期可放管理端）：实例选择、状态卡片、命令按钮、实时事件流。
  AI/UI 都用同一个 `instanceId` 或 `displayName` 选实例。
- `src/features/admin/menu.ts` + `src/router/index.ts`：加入口。
- **EventSource 的设备指纹**：`EventSource` 不能设 header，但 `SessionGuardFilter` 先读 `X-Device-Fp`
  再读 `nitrowater_device_fp` Cookie（登录时已镜像），同源 `EventSource` 自动带 Cookie，因此可用。
  不要给 `/api/ui/stream` 改用 `fetch` 流式读取来"补 header"，那会破坏现有会话约定。

---

## 5. 与 VeloChatX 方案文档需要同步的差异

`docs/remote-monitoring-plan.md` 已按本文结论更新以下点：

1. Base URL：`/v1/agent/*` → `/api/public/instance/*`（BFF 只允许 `/api/**` 数据命名空间）。
2. 所有 JSON 响应包一层 `ApiResponse<T>`，V 读 `.data`、错误读 `.code`；SSE 帧仍裸 JSON。
3. 错误表从 HTTP 状态码改为 nitrowater 的 `code` 字符串（HTTP 状态仍保留作基础设施语义）。
4. 身份模型：一把用户级 key + V 自生成 UUID，实例 = `(key_id, instanceId)`，每 key ≤3 台。
5. SSE 常驻，viewers 只控制 5s status；409 自动纠正；新增 `instance_limit_exceeded`。

✅ 已确认：2Mbps 是 **V 侧上行**（V→N，正是心跳 / 状态 / 结果回传方向）。全 ACTIVE 100 实例约 300–450 kbps，占 15–22%，`reportIntervalMs` 保持 5s 默认值即可，无需降频或额外压缩；仍由心跳响应动态下发，便于实测后调整。

---

## 6. 实施顺序（建议）

| 阶段 | nitrowater 侧 | VeloChatX 侧 |
|---|---|---|
| P0 | 本文定稿 + 端点契约冻结 | 协议文档同步（本次已做） |
| P1 | `V2` 迁移（credential + registry + audit）+ 三段式实体 + 管理端签发 API + 前端 key 页 | `vcx remote set` + H2 V003（含 instance_id/display_name）+ 脱敏 |
| P2 | 实例安全链 + filter + heartbeat/status 控制器 + 首绑注册/配额/显示名 + BFF JSON 代理 | `JdkRemoteTransport` + 心跳首绑 + 5s status 门控 + outbox |
| P3 | 命令 SSE（常驻）+ 浏览器 SSE + 命令下发/结果 + 409 冲突语义 + BFF 流式代理 | SSE 常驻 + 命令接收 + RpcRegistry + 结果回传 + 409 自动纠正 |
| P4 | 前端监控页 + 实例改名/移除 + 带宽/超时实测 + 动态间隔 | 接真实事件 + gzip + 压测 |
| P5（后） | Paper companion / 规则告警 | plugin-message 通道（V ↔ Paper） |

---

## 7. 验收自检清单

- [ ] `Authorization` 从 V 到 server 全程没被 BFF 剥掉（专用代理，不改全局白名单）。
- [ ] V 的 POST（心跳/状态/结果）不带 CSRF token 也不被 BFF 403；浏览器 `/api/**` 的 CSRF 仍然生效。
- [ ] 直接访问 `:8081/api/public/instance/heartbeat` 无 key → 401 `instance_unauthorized`，且是 `ApiResponse` 形状。
- [ ] 只有 `telemetry:write` 的 key 调命令接口 → 403 `instance_forbidden`。
- [ ] SSE 帧在浏览器/V 端逐条到达（BFF 逐块 flush，`X-Accel-Buffering: no`）。
- [ ] 一把 key 贴到第 4 台 V → 首绑时 403 `instance_limit_exceeded`，V 停止自动纠正并告警。
- [ ] 首绑生成 `instance-xxxxxx` 显示名；NW 改名后下次心跳回显新名字；V 缓存更新。
- [ ] 两台 V 克隆（同 key + 同 UUID）建 SSE → 后到者 409、先到者不受影响；后到者换新 UUID 回写 H2 重连，最终两台独立在线。
- [ ] 正常重启（旧连接已断、map 未及时清）不被误判为克隆：直接替换旧 stream，不返回 409。
- [ ] `vcx remote set` 轮换 key 后 `instance_id` / `display_name` 不变（身份不漂移）。
- [ ] 浏览器全部关闭后，下个心跳 `viewers=0`、`reportIntervalMs=30000`，V 停 5s status，但心跳与 SSE 不停；AI 仍可命令该实例。
- [ ] 移除实例后槽位释放，第 4 台可以重新首绑成功。
- [ ] 吊销 key 后缓存被清，下一次请求 401。
- [ ] MySQL 里没有任何玩家列表 / 聊天记录 / status 快照（只有 credential + registry + 命令元数据审计）。
