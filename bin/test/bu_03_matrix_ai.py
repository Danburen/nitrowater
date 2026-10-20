"""课时5 · AI 执行轨道 —— 列表树可视化参数化矩阵（TASK 式，AI 自己判定）。

与 ``bu_01`` / ``bu_02`` 同构：文件顶部只有一个 ``TASK``（主任务体），交给
Browser Use 的 LLM 执行；脚本不做功能断言，只做三道非功能门：
  1. 完整性：每一行都要有 CASE 结论；
  2. 一致性：逐行结论与最终 VERDICT 不得矛盾；
  3. 观察纪律：每个用例都必须真实读取页面（防「凭输入推算结果」的伪造证据）。

Prompt 由 ``ai_specs`` 拼接：PRE（预先提示词）+ TASK（本文件）+ DATA（矩阵）
+ COVERAGE（覆盖声明）+ FINAL（输出格式约束）。

运行：
    bin\\test\\.venv\\Scripts\\python.exe bin\\test\\bu_03_matrix_ai.py [--headed] [--only C1,C2]
    ... --dry-run      # 只看拼接后的完整 prompt
"""

from __future__ import annotations

import sys

from _matrix_ai import main

TASK = """## 每个用例的执行方法
1. 在「树形文本输入」多行文本框中：先全选（Ctrl+A）再输入本行数据，确保覆盖上一个用例的内容。
2. 点击「渲染树列表」按钮。
   - 例外：空输入用例中该按钮通常为 disabled（禁用）——这是正确行为，记录按钮状态即可，
     不要强行点击、不要用 JS 绕过。
3. 立即读取页面（证据纪律，必须）：用 search_page 搜索「个节点」，或 evaluate
   document.body.innerText；只有本次读取到的数字/文本才能作为证据。
4. 读取页面上的统计文本（形如「N 个节点」）和列表面板内容。

## 证据纪律（违反即视为无效）
- 禁止根据输入文本推算节点数；每个用例都必须有一次真实的页面读取动作。
- 每行 evidence 必须引用你刚刚读到的页面文本（可加引号）。
- 没有实际读取就写 PASS 的用例，视为伪造证据。

## 判定
- 页面是否可用、统计与输入是否自洽、是否出现崩溃/白屏/无响应。
- 空输入、非法输入：允许出现空结果或提示，只要不崩溃且页面仍可操作，即算通过。
- 其它行：节点数必须与参考期望一致（或能解释差异）。

## 判定原则
- 你是判定者（oracle）。不要修改代码、不要读源码、不要用 fetch/console 直接调接口；只通过 UI 操作。
- 每个用例都要给出 PASS 或 FAIL，并引用你在页面上实际看到的文本作为证据。"""


if __name__ == "__main__":
    sys.exit(main("list-tree", TASK))
