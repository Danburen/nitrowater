"""课时5 · AI 执行轨道 —— Java DTO 转 TypeScript 参数化矩阵（TASK 式）。

结构同 ``bu_03``：TASK 顶置，脚本只做完整性/一致性/观察纪律三门 + 逐页留证。
被测工具是纯前端转换器（无后端、无登录），输出在只读 textarea 中。

运行：
    bin\\test\\.venv\\Scripts\\python.exe bin\\test\\bu_04_java_to_ts_ai.py [--headed]
    ... --only J1,J6        # 只跑子集
    ... --dry-run           # 打印拼接后的 prompt（PRE + TASK + DATA + COVERAGE + FINAL）
"""

from __future__ import annotations

import sys

from _matrix_ai import main

TASK = """## 每个用例的执行方法
1. 在「Java 输入」文本框（页面第 1 个 textarea）先全选（Ctrl+A）再输入本行 Java 代码，
   确保覆盖上一个用例的内容。
2. 点击「转换为 TypeScript」按钮。
3. 立即读取输出区（页面第 2 个 textarea，readonly，不能写入）：
   用 evaluate 执行 `() => document.querySelectorAll('textarea')[1]?.value ?? ''`；
   只有本次读到的文本才能作为证据，禁止根据输入推算输出。
4. 同时记录页面提示（toast：如「转换成功」「检测到未知类型，请检查 FIXME 注释」「请输入 Java 代码」）。
   toast 数秒后会消失；若已消失，以输出区内容作为证据，并在 evidence 中说明。

## 证据纪律（违反即视为无效）
- 每个用例都必须有一次真实的输出读取动作（读第 2 个 textarea）。
- 每行 evidence 必须引用你刚刚读到的输出片段。
- 没有实际读取就写 PASS 的用例，视为伪造证据。

## 判定
- 正常用例：输出包含参考期望中的接口名/字段/默认值对象；字段类型映射正确。
- 未知自定义类型：允许输出 FIXME 注释 + 警告提示，判通过（这是设计好的容错）。
- 校验规则分支：输出必须出现 rules 段与对应约束（required / min / max 等）。
- 空输入：点击转换后出现「请输入 Java 代码」提示（或仅页面不崩溃）；输出区不会被清空
  （保留上一次结果，已知行为，不判失败）→ 通过。
- 非法输入：页面不崩溃、仍可操作 → 通过；容错输出（如空字段的 UnknownType 接口）不判失败。
- 泛型/嵌套写法与参考期望有细微差异时，若语义等价可判 PASS，并在 evidence 中说明。

## 判定原则
- 你是判定者（oracle）。不要读源码、不要修改代码、不要直接调接口；只通过 UI 操作与读取页面。
- 每个用例都要给出 PASS 或 FAIL，并引用你在页面上实际看到的输出文本作为证据。"""


if __name__ == "__main__":
    sys.exit(main("java-to-ts", TASK))
