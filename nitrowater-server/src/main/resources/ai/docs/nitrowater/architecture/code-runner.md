# 代码运行与 Piston 沙箱

平台提供两条代码运行通道：匿名用户的浏览器 WASM 运行器，以及登录用户的后端 Piston 沙箱。NitroBot 的 `runCode` 工具与 HTTP 入口共享同一套配额与并发闸门。

## 1. 两条通道

| 通道 | 使用者 | 执行位置 | 入口 |
|---|---|---|---|
| 浏览器 WASM | 匿名 | 浏览器内 | 工具箱「代码运行」页面 |
| 后端沙箱 | 登录用户 | Piston 容器 | `POST /api/sandbox/run`、Agent `runCode` |

## 2. 执行链路

```text
POST /api/sandbox/run
  -> SandboxRateLimitInterceptor（用户每分钟/每天配额）
  -> SandboxService（语言白名单、参数校验、输出截断）
  -> SandboxExecutionGuard（全局并发 + 每用户引用计数 + 有界队列）
  -> PistonClient
  -> Piston POST /api/v2/execute
```

Agent 的 `runCode` 不经过 HTTP 拦截器，但经过同一个 `SandboxExecutionGuard`，因此不会绕过配额与队列。

## 3. 状态机

`runCode` 对模型返回结构化状态，便于模型决定是否重试或向用户解释：

| 状态 | 含义 | 模型应对 |
|---|---|---|
| `ok` | 执行成功 | 直接引用 stdout/stderr |
| `queued` | 队列较长，需用户确认是否继续等待 | 先询问用户，确认后再调用 |
| `queue_timeout` | 等待超过上限 | 告知用户稍后重试 |
| `queue_full` | 全局队列已满 | 告知用户当前繁忙 |
| `run_timeout` | 运行超时 | 建议简化代码或检查死循环 |
| `compile_timeout` | 编译超时 | 建议精简代码 |
| `compile_error` | 编译失败 | 读取 stderr，修复后重试（最多 3 次） |
| `runtime_error` | 运行期异常 | 读取 stderr，修复后重试（最多 3 次） |
| `retry_exhausted` | 本轮重试次数用尽 | 解释问题并说明暂时无法运行 |
| `server_unavailable` | Piston 不可达或异常 | 统一提示「代码运行服务器暂时不可用」，不暴露细节 |

重试策略：编译/运行错误最多修复重试 3 次；第 4 次调用由工具自身返回 `retry_exhausted`。

## 4. 限额

配置前缀：`app.sandbox.*`

| 配置 | 默认值 | 含义 |
|---|---|---|
| `max-code-bytes` | `65536` | 单次代码上限 |
| `global-concurrency` | `2` | 全局并发执行数（对齐 Piston 容量） |
| `per-user-per-minute` | `10` | 每用户每分钟 |
| `per-user-per-day` | `200` | 每用户每天 |
| `max-queue` | `20` | 全局等待队列长度 |
| `queue-wait-ms` | `15000` | HTTP 入口最长排队等待 |
| `agent-queue-wait-ms` | `15000` | Agent 入口最长排队等待 |
| `agent-queue-ask-waiting` | `3` | 等待队列超过该值，要求模型先询问用户 |
| `agent-queue-ask-ms` | `5000` | 预计等待超过该值，要求模型先询问用户 |
| `agent-queue-estimate-run-ms` | `3000` | 估算单次运行耗时 |
| `run-timeout-ms` | `10000` | 单次运行超时 |
| `compile-timeout-ms` | `20000` | 编译超时 |
| `max-output-bytes` | `65536` | stdout/stderr 上限（超出截断） |
| `memory-limit-bytes` | `268435456` | 内存上限（256MB） |

`memory-limit-bytes` **不得超过** Piston 容器的 `PISTON_RUN_MEMORY_LIMIT`。

## 5. Piston 部署

两种启动方式，不能混用：

| 方式 | 用途 | 端口映射 |
|---|---|---|
| `deploy/docker/docker-compose.app.yml`（`piston` 服务，常驻） | 与应用容器同网络 | 不映射宿主端口，容器内访问 `http://piston:2000` |
| `bin/start-infra.ps1` | 本机直接跑 server | 映射 `-p 2000:2000`，默认 `PISTON_BASE_URL=http://localhost:2000` |

`piston` 只加入 `nitrowater-runner-net`（app compose 内创建），server 同时加入 `nitrowater-net` 与
`nitrowater-runner-net`，因此沙箱与 mysql / redis / seaweedfs 不在同一网段。

语言包需要预装（`bin/dev/install-piston-langs.ps1`，或首次部署后手工
`docker exec nitrowater-piston cli/index.js ppman install <lang>`）；未安装的语言会返回错误。
当前已验证语言：Java、Python、JavaScript、TypeScript、C、C++、Lua。

## 6. 安全与错误脱敏

- 语言白名单在 `SandboxService` 维护，未知语言返回 400。
- 只返回 stdout/stderr 与退出状态；不返回 Piston 地址、容器信息或堆栈。
- 输出超过 `max-output-bytes` 截断，避免撑爆模型上下文。
- 沙箱不是强隔离环境，仅用于运行用户自己的示例代码；不要在其中处理敏感数据。

## 7. 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| 所有语言都返回 400 且响应体是 HTML `Bad Request` | JDK HttpClient 默认 h2c Upgrade，Piston 不支持 | `PistonConfig` 固定 HTTP/1.1 |
| `server_unavailable` | Piston 未启动 / 端口不对 | 按上表选择正确的启动方式 |
| 语言运行失败但其它语言正常 | 语言包未安装 | 运行 `install-piston-langs.ps1` |
| 内存相关错误 | 内存上限超过 Piston 配置 | 下调 `memory-limit-bytes` |

## 8. 关键源码

- `nitrowater-server/.../service/sandbox/SandboxService.java`
- `nitrowater-server/.../infra/ratelimit/SandboxGate.java`
- `nitrowater-server/.../infra/ratelimit/SandboxExecutionGuard.java`
- `nitrowater-server/.../infra/third/PistonClient.java`
- `nitrowater-server/.../ai/tool/RunCodeTool.java`
- `deploy/docker/docker-compose.app.yml`（`piston` 服务）
