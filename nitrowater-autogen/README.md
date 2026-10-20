# AutoGenTestCase（Java + JUnit 版）

> **课时 5-6（设计思想）+ 可运行实现**：基于代码 AST 分析 + 大模型（DeepSeek）为 Java 代码自动生成 JUnit 5 测试用例。
>
> 参考作业原本是「为 Python 函数生成 Pytest 用例」，这里是**同思想换语言**：AST 分析换用 JavaParser，生成物换成 JUnit 5，执行验证换成 JUnit Platform——思路完全一致。

本模块是主工程 `nitrowater` 的一个子模块，**复用主项目已有的依赖与版本管理**：Spring Boot BOM（`4.1.1`）统一钉版本、Jackson 3（`tools.jackson.*`）、Java 25 toolchain，与其余模块保持一致。

---

## 1. 设计思想（课时 5-6）

一句话：**把「理解代码」交给 AST，把「写测试」交给 LLM，把「对不对」交给编译器与测试运行器。**

```
         ┌─────────────┐   事实    ┌──────────────┐   Prompt   ┌──────────────┐
 源码 ──► │ JavaAstAnalyzer │ ───────► │ PromptBuilder │ ─────────► │  DeepSeek API │
(.java)  │  (JavaParser)  │  签名/修饰符/ │              │  系统+用户  │ (/chat/…)    │
         └─────────────┘  异常/复杂度/ │              │   两条消息  └──────┬───────┘
                           javadoc/源码 └──────────────┘                    │ 代码
                                                                            ▼
   ┌───────────────────────────┐   通过    ┌──────────────────┐    ┌───────────────┐
   │ JUnitRunner               │ ◄──────── │ JavaCompilationCheck │ ◄─ │ CodeExtractor │
   │ 内存编译 + Platform 执行   │  写文件    │ 与被测类一起编译校验  │    │ 去 Markdown 围栏 │
   └───────────────────────────┘           └──────────────────┘    └───────────────┘
```

四个关键设计取舍：

1. **AST 事实优先，源码随后** —— 先把机器能 100% 确定的*事实*（方法签名、修饰符、声明抛出的异常、圈复杂度、行数、javadoc）抽出来，再附上方法原文。事实用来约束「测什么」（比如声明了 `ArithmeticException` 就一定要有异常用例），源码用来让模型理解「怎么测」（具体行为）。
2. **确定性校验兜底** —— 生成结果**不信任、必须验证**：先用 `JavaCompilationCheck` 把「被测类 + 生成的测试」一起交给 `javax.tools.JavaCompiler` 编译（等价于参考设计里的 `ast.parse`，但在 Java 里能顺带验证类型解析）。编译不过就不落盘、不运行。
3. **闭环执行** —— `--run` 会用 JUnit Platform Launcher 在内存里加载并执行生成出来的测试，直接给出 `found/succeeded/failed`，把「生成」变成「可证伪」。
4. **可观测、可调试的中间态** —— `--list-methods`（AST 识别了哪些方法）、`--show-facts`（抽取到的事实 JSON）、`--dry-run`（真正发出去的 Prompt）三个开关让你在每一步都能看见中间结果，而不是只看到最终代码。

## 2. 模块结构

| 包 | 职责 |
|---|---|
| `ast` | `JavaAstAnalyzer` 用 JavaParser 解析源码，产出 `ClassFact / MethodFact / ParamFact / FieldFact` |
| `llm` | `LlmClient` 调 DeepSeek Chat Completions（JDK `HttpClient` + Jackson 3，无第三方 SDK） |
| `gen` | `PromptBuilder` 组 Prompt、`CodeExtractor` 去围栏、`JavaSourceInfo` 取全限定名、`JavaCompilationCheck` 编译校验、`JUnitTestGenerator` 串起整条流水线 |
| `run` | `JUnitRunner` 内存编译 + JUnit Platform 执行 |
| `AutoGenTestCaseCli` | 命令行入口 |
| `examples/` | 示例被测类（`demo.Calculator`，刻意带 除零/负数/null/空 等边界） |

## 3. 用法

API Key 从环境变量读取：

```powershell
$env:DEEPSEEK_API_KEY = "sk-..."   # 仅本地/CI 注入，勿入库
```

> 若在 VS Code / IntelliJ 里直接 Run，请在运行配置里配置该环境变量；Gradle `run` 会自动继承当前 shell 的环境变量。

命令一律从**仓库根目录**执行（走主工程的 `gradlew`）：

```powershell
# 只看 AST 识别出的方法（不调模型）
.\gradlew.bat :autogen-testcase:run --args="--list-methods"

# 看抽取到的 AST 事实（JSON）
.\gradlew.bat :autogen-testcase:run --args="--show-facts"

# 只预览将要发送的 Prompt（不调模型、不需要 Key）
.\gradlew.bat :autogen-testcase:run --args="--dry-run"

# 生成测试并落盘（默认写 generated-tests/<类名>Test.java）
.\gradlew.bat :autogen-testcase:run --args="--run"

# 只针对某个方法生成
.\gradlew.bat :autogen-testcase:run --args="--method divide --run"

# 换被测文件 / 指定输出 / 换模型
.\gradlew.bat :autogen-testcase:run --args="--file path\to\Foo.java --out out\FooTest.java --model deepseek-chat"
```

| 参数 | 说明 |
|---|---|
| `-f, --file <path>` | 被测 `.java`（默认 `examples/Calculator.java`） |
| `-m, --method <name>` | 只生成该方法的测试（默认除 `main` 外全部） |
| `--list-methods` | 打印 AST 识别到的方法后退出 |
| `--show-facts` | 以 JSON 打印 AST 事实后退出 |
| `--dry-run` | 打印将发送的 Prompt 后退出（不调 API） |
| `--out <path>` | 输出文件（默认 `generated-tests/<Class>Test.java`） |
| `--model <name>` | DeepSeek 模型（默认 `deepseek-chat`） |
| `--base-url <url>` | API 基址（默认 `https://api.deepseek.com`） |
| `--run` | 生成后编译并执行测试，报告通过/失败 |

## 4. 实测

对 `examples/Calculator.java`（5 个方法）执行 `--run`：DeepSeek 生成约 176 行 JUnit 5 测试，编译校验通过，JUnit Platform 执行 **22 个用例全部通过**，覆盖正常路径、边界值（0/负数/`Integer.MAX_VALUE` 溢出）、异常路径（`ArithmeticException` / `IllegalArgumentException`）与 `null`/空集合。

## 5. 依赖

- `com.github.javaparser:javaparser-core:3.26.4` —— Java AST 解析
- `tools.jackson.core:jackson-databind`（由 Boot BOM 管理）—— JSON
- `org.junit.jupiter:junit-jupiter` / `org.junit.platform:junit-platform-launcher`（由 Boot BOM 管理）—— 生成物编译与执行
- 其余（HTTP 客户端、内存编译）均为 JDK 自带

## 6. 构建与自测

```powershell
.\gradlew.bat :autogen-testcase:build      # 编译 + 运行本模块自身单测
.\gradlew.bat :autogen-testcase:test       # 只跑单测
```
