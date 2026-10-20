"""课时3-4 · Browser Use —— 让 AI 控制浏览器完成"点击 / 输入 / 断言"（工具箱旅程）。

Browser Use 的核心循环（与 Playwright 脚本式的本质区别）：

    观察：浏览器页面 → ARIA/DOM 可访问性快照（元素编号）
    决策：LLM（这里 DeepSeek deepseek-chat）看到快照后输出下一个动作 JSON
    执行：框架把动作翻译成真实 Playwright/CDP 操作（click / fill / navigate / …）
    断言：循环直到 LLM 判定任务完成（final_result），脚本再做一次独立的 DOM 复核

本脚本让 AI 自主完成：门户 → 工具箱 → 列表树可视化工具 → 输入目录文本 →
点击"渲染树列表" → 断言出现节点统计。脚本不写任何 CSS selector——这就是
"AI 控制浏览器"与"录制/手写脚本"的差别。

运行（需 DEEPSEEK_API_KEY，见 README）：
    bin\\.venv\\Scripts\\python.exe bin\\test\\bu_01_toolbox_ai.py [--headed]
"""

from __future__ import annotations

import argparse
import asyncio
import sys

from _common import PORTAL, Checker, pick, run_agent_task, start_browser

TASK = f"""你正在控制一个浏览器，请按顺序完成一次网页功能测试旅程（站点 {PORTAL}）：

1. 打开门户首页 {PORTAL}/ ，确认主标题（h1）含「一个站点，装下你的」。
2. 点击主视觉区的「开始使用工具箱」按钮，进入 /tools 页面，应出现标题「在线工具箱」。
3. 点击「列表树可视化」卡片，进入 /tools/list-tree。
4. 在「树形文本输入」的多行文本框中输入（保持换行）：
project/
├── src/
│   ├── main.py
│   └── utils.py
└── README.md
5. 点击「渲染树列表」按钮。
6. 断言：页面出现「个节点」字样的统计文本（样例树共 5 个节点），且列表面板渲染出内容。
7. 结束要求：
   - 全部成功 → 最终回复第一行以「PASS:」开头，并写出你断言到的节点数；
   - 任一步无法完成 → 最终回复第一行以「FAIL:」开头并说明卡在哪一步；
   - 最终回复前请停留在 /tools/list-tree 页面，不要离开。

只操作网页（点击/输入/读取页面），不要做其它系统级操作。"""


async def run(headed: bool) -> int:
    c = Checker("bu_01 AI 工具箱旅程 (Browser Use + DeepSeek)")
    session = await start_browser(headed)
    try:
        history = await run_agent_task(TASK, session, max_steps=40)

        final = pick(history.final_result)
        c.check(final is not None, "agent produced a final result")
        c.check(bool(final) and str(final).startswith("PASS"), f"agent verdict: {str(final)[:160]!r}")

        urls = pick(history.urls) or []
        c.check(any("/tools/list-tree" in (u or "") for u in urls), "agent actually visited /tools/list-tree")

        # Independent DOM re-check — never trust the model's own word alone.
        try:
            page = await session.must_get_current_page()
            body = await page.evaluate("() => document.body.innerText")
            c.check("个节点" in body, "DOM re-check: node-count stat present ('个节点')")
            final_url = await page.get_url()
            c.check(
                final_url.rstrip("/").endswith("/tools/list-tree"),
                f"final URL stays on the tool page: {final_url}",
            )
        except Exception as exc:  # noqa: BLE001
            c.check(False, f"post-run DOM re-check failed: {type(exc).__name__}: {exc}")
    except Exception as exc:  # noqa: BLE001
        c.check(False, f"unexpected error: {type(exc).__name__}: {exc}")
    finally:
        await session.stop()
    return c.finish()


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--headed", action="store_true", help="watch the AI drive a visible browser")
    args = ap.parse_args()
    return asyncio.run(run(headed=args.headed))


if __name__ == "__main__":
    sys.exit(main())
