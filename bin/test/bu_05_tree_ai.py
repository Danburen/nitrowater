"""课时5 · AI 执行轨道 —— 树形结构可视化（JSON → 树）参数化矩阵（TASK 式）。

结构同 ``bu_03``：TASK 顶置，脚本只做完整性/一致性/观察纪律三门 + 逐页留证。
被测功能：JSON 解析 → ECharts 树渲染，页面显示「共 N 个节点」。

运行：
    bin\\test\\.venv\\Scripts\\python.exe bin\\test\\bu_05_tree_ai.py [--headed]
    ... --only T1,T3,T9
    ... --dry-run
"""

from __future__ import annotations

import sys

from _matrix_ai import main

TASK = """## 每个用例的执行方法
1. 在「JSON 输入」textarea（页面唯一 textarea）先全选（Ctrl+A）再输入本行 JSON。
2. 点击「渲染树形图」按钮。
   - 例外：空输入时该按钮为 disabled（正确行为），记录按钮状态即可，不要强点、不要用 JS 绕过。
3. 立即读取页面（证据纪律，必须）：用 search_page 搜索「个节点」，或 evaluate
   document.body.innerText；只有本次读取到的文本才能作为证据。
4. 例外 T9（字段映射）：先点击工具栏齿轮（提示「图配置」）展开配置，把「标签字段」改为 label、
   「子级字段」改为 kids，再点击「渲染树形图」。

## 证据纪律（违反即视为无效）
- 禁止根据 JSON 结构推算节点数；每个用例都必须有一次真实的页面读取动作。
- 每行 evidence 必须引用你刚刚读到的页面文本（可加引号）。
- 没有实际读取就写 PASS 的用例，视为伪造证据。

## 判定
- 正常用例：页面出现「共 N 个节点」，且 N 与参考期望一致（虚拟 root 计入节点）。
- 空输入：渲染按钮 disabled、页面可用 → 通过。
- 非法 JSON：输入框下方出现「JSON 解析错误」提示 → 通过；允许页面仍显示上一次的统计数字
  （已知行为，不判失败），但必须在 evidence 中说明。
- 深层/大输入：渲染完成、统计正确、页面可继续操作。
- 字段映射：按 label/kids 解析成功且统计正确。

## 判定原则
- 你是判定者（oracle）。不要读源码、不要修改代码、不要直接调接口；只通过 UI 操作与读取页面。
- 每个用例都要给出 PASS 或 FAIL，并引用你在页面上实际看到的文本作为证据。"""


if __name__ == "__main__":
    sys.exit(main("tree", TASK))
