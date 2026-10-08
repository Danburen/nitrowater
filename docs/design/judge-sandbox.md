# 判题沙箱设计（Java · ACM + LeetCode 双模式）

> 适用：`nitrowater-judge`（独立模块/独立部署）。
> 关联需求：FR-2（演练场后端化）、NFR「安全-判题」、风险 R1（任意代码执行）/R4（Win/Linux 差异）/R7（并发打满）。
> 状态：设计稿（v0.1）。**安全前置**：沙箱三件套未就绪前，不得对外开放提交入口。

## 0. 一句话

一个**独立判题服务**：接收提交 → 编译 → 在**受限沙箱**里逐用例执行 → 与标准答案比对 → 返回结构化判定；同时支持 **ACM 模式**（整程序 stdin/stdout）与 **LeetCode 模式**（方法级，自动包装 I/O）。

---

## 1. 角色与边界

| 组件 | 职责 |
|---|---|
| `nitrowater-judge`（API + Worker） | 接收提交、排队、编译、沙箱执行、判题、落库结果 |
| **沙箱**（Sandbox） | 真正跑用户代码的受限环境（MVP=受限子进程；目标态=Docker/namespace） |
| `nitrowater-server`（Resource Server） | 题库/提交记录的对外读写；校验 SSO 的 JWT |
| `nitrowater-account`（AS） | 发令牌；judge 不直接对接登录，只信 JWT（或网关注入身份） |

**信任边界**：判题服务是**最高风险**组件。它**不能**跑在与业务/DB 同一进程/同一特权域；**默认禁网**；用户代码永远在沙箱内、以低权限、限时限额执行。

---

## 2. 双判题模式

同一道题只属于一种模式（`problem.io_mode`）。

### 2.1 ACM 模式（整程序）

- 用户提交一个**完整 Java 程序**，入口固定为 `public class Main { public static void main(String[] args) }`。
- 我们的职责：把 `stdin` 喂给它，收集 `stdout`，与 `expected` 比对。
- 目录：`Main.java`（用户代码原样写入）。
- 适用：算法题、脚本式、多行 I/O。

### 2.2 LeetCode 模式（方法级，封装 I/O）

- 用户只提交解题类：
  ```java
  class Solution {
      public int[] twoSum(int[] nums, int target) { ... }
  }
  ```
- 其余（读入参数、调用方法、输出结果）由**运行时自动生成的 Harness** 完成。
- 目录：`Solution.java`（用户代码）+ `Main.java`（Harness，我们生成）。

**Harness 生成（反射式，推荐）**

题目元数据声明签名：`method_name`、`params:[{name,type}]`、`return_type`。Harness 模板：

```java
// 生成到 Main.java —— 不要手改
import java.lang.reflect.*;
import java.util.*;
import <jackson 或内置解析>;

public class Main {
  public static void main(String[] args) throws Exception {
    String in = new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    // 1) 解析入参（按 meta 的 params 顺序/名字）
    Object[] callArgs = Harness.parse(in, META);   // e.g. {"nums":[2,7,11,15],"target":9}
    // 2) 反射调用 Solution
    Class<?> sol = Class.forName("Solution");
    Method m = sol.getMethod("twoSum", int[].class, int.class);
    Object result = m.invoke(sol.getDeclaredConstructor().newInstance(), callArgs);
    // 3) 序列化结果到 stdout
    System.out.print(Harness.write(result));
  }
}
```

- **入参格式**：单个测试用例一个 **JSON 对象**，键为参数名（如 `{"nums":[2,7,11,15],"target":9}`）。
- **出参格式**：返回值的 **JSON 表示**（`[[0,1]]`、`3`、`"abc"` 等）。
- 期望输出同样用 JSON 存，比对时做**标准化**（见 §7）。
- **类型支持（一期）**：`int/long/double/boolean/char/String`、其数组、`List<...>`。
  `TreeNode` / `ListNode` 等复杂类型**二期**（需自定义编解码），见决策 N11。

> Python 模式镜像同一套：ACM 直接跑；LeetCode 用生成脚本包装调用（`def` 签名 + JSON I/O）。
> 实现选型建议：**静态模板 + 占位符填充**（比纯反射更可控、报错更准），反射作兜底。

---

## 3. 执行流水线

```
POST /api/judge/submissions            （需登录；RS 验 JWT）
   │  校验题目/模式/语言/代码大小 + 限流 + 单用户配额
   ├─ 落 submission（status=QUEUED）
   ├─ 入队 Redis judge:queue
   └─ 返回 {submissionId}
Worker（独立进程/容器循环）:
   pop job → status=RUNNING
   ├─ 建独立临时目录（每题/每提交一个，任务结束即删）
   ├─ 写源码（ACM: Main.java；LC: Solution.java + 生成 Main.java）
   ├─ 编译（javac，超时上限；失败 → CE）
   └─ for 每个测试用例：
        ├─ 生成输入文件（ACM: 原文；LC: JSON）
        ├─ 沙箱执行（stdin=输入；限时/限额/禁网/输出截断）
        ├─ 采集 stdout/stderr/exit/wallTime/内存
        └─ 比对 → 用例判定（首个非 AC 通常可提前中止，可配「全跑」）
   └─ 聚合 verdict → 落 submission + Redis judge:sub:{id}（TTL）
```

---

## 4. 沙箱实现（三件套：超时强杀 · 资源限额 · 隔离）

### 4.1 MVP（本地开发 / Windows）——受限子进程

- `ProcessBuilder` 起 `java`，**独立工作目录**（临时目录，不含系统敏感路径）。
- JVM 参数限额：`-Xmx256m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1`（降内存/启动开销，牺牲峰值性能换稳定）。
- **超时强杀**：`waitFor(timeout)` 超时 → 杀**整个进程树**（`ProcessHandle.descendants().forEach(::destroyForcibly)`；Windows 亦可 `taskkill /T /F /PID`）。
- **输出截断**：读取 stdout/stderr 时按下限（如 1MB）截断，超限 → `OLE`。
- 限制项：内存、时间、输出、进程数（尽量 `-XX:ActiveProcessorCount=1`）。

> ⚠️ Windows 下**难以可靠禁网/隔离文件系统**；MVP 仅作本地开发，**不可**直接对公网开放。
> 另：**Java 已移除 SecurityManager**（JDK 24/JEP 486），不能再靠它做进程内限制 → **必须**进程/OS 级隔离。

### 4.2 目标态（部署 / Linux）——容器/命名空间

每次执行一个**一次性容器**（或 nsjail/bubblewrap/firejail）：

```
docker run --rm \
  --network none \                 # 禁网
  --read-only --tmpfs /tmp:size=64m \  # 只读根 + 可写 tmp
  --memory 512m --memory-swap 512m \
  --cpus 1 --pids-limit 64 \
  --user 1000:1000 \               # non-root
  --cap-drop ALL --security-opt no-new-privileges \
  --security-opt seccomp=judge.json \
  -v <jobdir>:/work -w /work \
  <jdk-image> java -Xmx256m Main
```
- 宿主机侧统一 `timeout` + kill；收集 stdout/stderr/exit/用量。
- 与业务/DB 网络完全隔离；判题 Worker 与 API 可分离部署（N2）。

### 4.3 Java 特有注意
- **启动开销**：JVM 冷启 ~100–300ms → 单用例时间上限要给足（默认 5s/用例，FR-2.3）。
- **JIT 预热**：可先跑一次「热身」（不计时/不计分）再正式计时，减少抖动导致的 TLE 误判。
- **编译也要沙箱化**：`javac` 同样是执行入口（可被滥用），编译同样限时/限目录/可选的独立容器。

---

## 5. 并发 / 限流 / 配额（防 R7）

- **全局闸门**：`judge:running` 集合/信号量，上限 = Worker 并发数。
- **单用户配额**：`judge:user:{uid}:running`（如 ≤2）+ 提交频率限制（如 ≤N 次/分钟）。
- **队列**：Redis `judge:queue`（List/Stream），削峰；超出排队上限直接拒绝（429）。
- **超时兜底**：Worker 崩溃恢复——`judge:running` 里僵尸任务定期回收，submission 置 `SE`。

---

## 6. API 与数据模型（草案）

**API**
| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/api/judge/submissions` | 登录 | `{problemId, language, ioMode, code}` → `{submissionId, status}` |
| GET | `/api/judge/submissions/{id}` | 登录 | `{status, verdict, passed, total, cases:[{index, verdict, timeMs, memoryKb}]}` |
| GET | `/api/problems`、`/api/problems/{id}` | 公开 | 题目列表/详情（**不含隐藏用例**） |
| POST | `/api/problems`、`/api/problems/{id}/testcases` | 管理员 | 题目/用例 CRUD |

**数据表**
- `problem`：`id, title, difficulty, category, tags, description, templates(JSON), io_mode, judge_config(JSON: 时限/比较策略/方法签名)`
- `problem_test_case`：`problem_id, idx, stdin, expected_output, is_sample, score?`
- `submission`：`id, uid, problem_id, language, io_mode, code, status, verdict, passed, total, time_ms, created_at`

**共享 Redis（前缀隔离）**：`nitrowater:judge:queue` / `:running` / `:sub:{id}` / `:user:{uid}:running`。

---

## 7. 判定语义

| Verdict | 触发 |
|---|---|
| **AC** | 全部用例通过 |
| **WA** | 输出与期望不符 |
| **TLE** | 单用例超时（强杀） |
| **MLE** | 超内存（JVM OOM / 容器 OOM-kill） |
| **RE** | 非 0 退出 / 异常（栈信息不回传原文，仅记日志） |
| **CE** | 编译失败（回传编译器摘要，截断/转义） |
| **OLE** | 输出超限 |
| **SE** | 系统/Worker 错误（重试或判失败） |

**比较策略**（题目级可配）：`exact`（去尾部空白后严格）/ `token`（按空白分词）/ `lines` / `special judge`（浮点 `eps`、多解）。LeetCode 模式先对 JSON 做**标准化**（对象键序无关、数组有序）后再比。

---

## 8. 里程碑

| 阶段 | 内容 | 验收 |
|---|---|---|
| J0 | 判题骨架：API + Redis 队列 + 结果轮询 | 提交返回 id，能查状态 |
| J1 | **ACM + Java MVP 沙箱**（受限子进程 + 三件套） | Python/Java 各 3 题，AC/WA/TLE/RE/CE 正确 |
| J2 | **LeetCode 模式**（Harness 生成 + JSON 比较，基础类型） | 3 道方法级题通过 |
| J3 | 限流/配额/队列 + 提交历史 | 未登录 401、超限 429 |
| J4 | **Docker 沙箱（目标态）** + 安全复盘 | R4 双环境同用例通过；无逃逸/无外联 |
| J5 | 复杂类型（TreeNode/ListNode）、多语言扩展 | 视需要 |

---

## 9. 风险与对策（对应 PRD §10）

- **R1 任意代码执行**：进程/容器隔离 + 禁网 + 限额 + 三件套**前置**；judge 与业务分离部署；不向用户回传原始 stderr/路径。
- **R4 Win/Linux 差异**：沙箱抽象层双实现（MVP 子进程 / Docker），同一套 e2e 双环境跑。
- **R7 并发打满**：全局闸门 + 单用户配额 + 队列削峰。
- **判题服务可用性**：Worker 崩溃自愈（僵尸回收）；提交接口与 Worker 解耦。

---

## 10. 待拍板

- **N11**：一期 LeetCode 类型范围（基础类型 vs 含 TreeNode/ListNode）。
- 比较策略默认值（exact vs token）。
- Python 是否与 Java 同步上线（FR-2.2 要求双语言）。
