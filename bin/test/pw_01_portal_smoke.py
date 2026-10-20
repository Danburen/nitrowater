"""课时1-2 · 功能测试基础 —— Playwright 门户冒烟（功能测试层）。

功能测试（Functional Test）验证"单个功能/页面行为符合预期"，通常在一层内完成；
本脚本覆盖 NitroWater 门户的四个功能点：

  1. 门户首页渲染：document title、品牌链接、主视觉标题、顶栏导航、登录按钮
  2. 顶栏导航路由切换：工具箱 / 演练场 / 关于（SPA 内部跳转）
  3. 工具箱页 7 个工具卡片齐全
  4. 匿名公共 API：GET /api/public/problems 返回题库列表（BFF 代理 → server）

运行（服务需已启动，见 README）：
    bin\\.venv\\Scripts\\python.exe bin\\test\\pw_01_portal_smoke.py [--headed]
"""

from __future__ import annotations

import argparse
import sys

from playwright.sync_api import sync_playwright

from _common import ARTIFACTS, PORTAL, Checker, any_visible, wait_any_visible

TOOL_TITLES = [
    "Java DTO 转 TypeScript",
    "压缩 / 解压",
    "树形结构可视化",
    "列表树可视化",
    "在线代码运行",
    "MP4 转 GIF",
    "Markdown 转 Word",
]


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--headed", action="store_true", help="run with a visible browser window")
    args = ap.parse_args()

    c = Checker("pw_01 门户功能冒烟 (Playwright 功能测试)")
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=not args.headed)
        # Wide viewport: the top nav is `hidden md:flex` (hidden below the md breakpoint).
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()
        try:
            # ---- 1. home renders ------------------------------------------------------
            page.goto(PORTAL + "/", wait_until="networkidle")
            c.check("NitroWater" in page.title(), f"home title = {page.title()!r}")
            c.check(
                any_visible(page.get_by_role("link", name="NitroWater")),
                "header brand link 'NitroWater' visible",
            )
            hero = page.locator("h1").first.inner_text()
            c.check("一个站点，装下你的" in hero, f"hero h1 rendered: {hero.splitlines()[0]!r}")
            c.check(
                any_visible(page.get_by_text("匿名工具箱 · 在线演练场 · 插件中心")),
                "hero badge text visible",
            )
            for nav in ("工具箱", "演练场", "插件中心", "关于"):
                c.check(
                    any_visible(page.get_by_role("button", name=nav, exact=True)),
                    f"top-nav button '{nav}' visible",
                )
            c.check(
                any_visible(page.get_by_role("button", name="登录", exact=True)),
                "logged-out state shows '登录' button",
            )

            # ---- 2. public API (anonymous) --------------------------------------------
            resp = context.request.get(PORTAL + "/api/public/problems")
            c.check(resp.ok, f"GET /api/public/problems -> HTTP {resp.status}")
            problems = resp.json() if resp.ok else []
            c.check(isinstance(problems, list) and len(problems) >= 1, f"problem list non-empty ({len(problems)} items)")
            if problems:
                first = problems[0]
                c.check(
                    all(k in first for k in ("id", "slug", "title", "difficulty")),
                    f"problem DTO keys present (e.g. {first.get('slug')!r})",
                )

            # ---- 3. top-nav routing: 工具箱 -------------------------------------------
            page.get_by_role("button", name="工具箱", exact=True).first.click()
            page.wait_for_url("**/tools")
            c.check(
                wait_any_visible(page.get_by_role("heading", name="在线工具箱")),
                "tools page heading '在线工具箱'",
            )
            # Scan visible page text (body.inner_text only includes rendered, visible text).
            tools_text = page.locator("body").inner_text()
            for title in TOOL_TITLES:
                c.check(title in tools_text, f"tool card '{title}' present")

            # ---- 4. top-nav routing: 演练场 / 关于 ------------------------------------
            page.goto(PORTAL + "/", wait_until="networkidle")
            page.get_by_role("button", name="演练场", exact=True).first.click()
            page.wait_for_url("**/playground")
            c.check(page.url.endswith("/playground"), f"playground route = {page.url}")

            page.goto(PORTAL + "/", wait_until="networkidle")
            page.get_by_role("button", name="关于", exact=True).first.click()
            page.wait_for_url("**/about")
            wait_any_visible(page.locator("h1"))
            about_h1 = page.locator("h1").first.inner_text()
            c.check("NitroWater" in about_h1, f"about page h1 starts with NitroWater: {about_h1[:40]!r}")
        except Exception as exc:  # noqa: BLE001 - surface unexpected driver errors as a failed run
            c.check(False, f"unexpected error: {type(exc).__name__}: {exc}")
            page.screenshot(path=str(ARTIFACTS / "pw_01_error.png"), full_page=True)
        finally:
            context.close()
            browser.close()
    return c.finish()


if __name__ == "__main__":
    sys.exit(main())
