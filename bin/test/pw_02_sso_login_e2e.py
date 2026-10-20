"""课时1-2 · 端到端测试 —— Playwright 走完整 OIDC 登录/登出链路（E2E 层）。

端到端测试（E2E）模拟真实用户跨越多个服务/层的完整旅程。本脚本覆盖：

  门户(:8080) 点击登录
    → BFF /oauth2/authorization/nitrowater（confidential client）
    → account(:8090) /login 托管登录页（表单 username/password/captcha/deviceFp）
    → 表单 POST /login（SsoAuthenticationProvider + 验证码）
    → OIDC 授权码回跳 BFF /login/oauth2/code/*（服务端换 AT/RT、建会话）
    → 回到门户，顶栏呈已登录态，GET /bff/me = {authenticated:true, roles:[ROLE_ADMIN]}
  然后点击"退出"（RP-initiated logout：BFF 吊销 RT + 销毁会话 → AS end_session → 回门户），
  断言 /bff/me 恢复 authenticated:false、顶栏回到"登录"按钮。

验证码是 E2E 的经典"机器不可读"输入：这里用白盒方式从 Redis 读取真实码值
（UI 流程本身仍然走真实的表单提交，验证码后端校验不变）。

`--archive` 会额外把 S1-S4 四步旅程的截图和用例结论写入 results/*.jsonl
（即原 pw_04 场景法的留证能力，已并入本脚本，避免两套重复脚本）。

运行：
    bin\\.venv\\Scripts\\python.exe bin\\test\\pw_02_sso_login_e2e.py [--headed] [--archive]
"""

from __future__ import annotations

import argparse
import sys

from playwright.sync_api import sync_playwright

from _common import (
    ADMIN,
    ARTIFACTS,
    PORTAL,
    SSO,
    Checker,
    any_visible,
    captcha_code,
    click_first_visible,
    wait_any_visible,
)
from _results import new_run


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--headed", action="store_true", help="以有头模式运行（可视化围观浏览器）")
    ap.add_argument("--archive", action="store_true", help="逐步截图并写入 results/*.jsonl")
    args = ap.parse_args()

    c = Checker("pw_02 OIDC 登录/登出 E2E (Playwright)")
    run = new_run(tool="pw-02", mode="login-e2e", source="human") if args.archive else None
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=not args.headed)
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()

        def snap(case_id: str, kind: str, expected: str, ok: bool, evidence: str) -> None:
            """--archive 时：截当前页 + 记一条 case 到 JSONL；未开开关则什么都不做。"""
            if run is None:
                return
            path = run.screenshot_path(case_id)
            page.screenshot(path=str(path), full_page=True)
            run.case(
                case_id,
                kind=kind,
                expected=expected,
                verdict="PASS" if ok else "FAIL",
                evidence=evidence,
                screenshot=path,  # Run.case 自己会转成仓库相对路径
            )

        try:
            # ---- 1. 门户 -> SSO 登录页 ------------------------------------------------
            page.goto(PORTAL + "/", wait_until="networkidle")
            c.check(
                wait_any_visible(page.get_by_role("button", name="登录", exact=True)),
                "logged-out header shows '登录' before the journey",
            )
            c.check(
                click_first_visible(page.get_by_role("button", name="登录", exact=True)),
                "clicked the '登录' entry",
            )
            page.wait_for_url(lambda url: SSO in url and "/login" in url, timeout=15_000)
            c.check(True, f"login click redirected to SSO: {page.url}")
            snap("S1", "journey-click-login", "跳转到 SSO 登录页", True, page.url)

            # 等 GET /api/auth/captcha 完成 —— 它会下发 CAPTCHA_KEY cookie。
            page.wait_for_load_state("networkidle")
            cookies = context.cookies(SSO)
            captcha_uuid = next((ck["value"] for ck in cookies if ck["name"] == "CAPTCHA_KEY"), None)
            c.check(bool(captcha_uuid), "CAPTCHA_KEY cookie issued by /api/auth/captcha")
            code = captcha_code(captcha_uuid)
            c.check(bool(code), f"captcha code read from Redis ({len(code)} chars)")

            # ---- 2. 填真实登录表单 ----------------------------------------------------
            page.fill("#username", ADMIN["username"])
            page.fill("#password", ADMIN["password"])
            page.fill("#captcha", code)
            # deviceFp 隐藏字段由托管登录页自己的 JS 填写。
            page.click("button[type=submit]")

            # ---- 3. OIDC 往返后回到门户，且已登录 --------------------------------------
            logged_in = wait_any_visible(page.get_by_role("button", name="退出", exact=True), timeout_s=20)
            c.check(logged_in, "after login the header shows '退出' (logged-in state)")
            c.check(page.url.startswith(PORTAL), f"landed on portal: {page.url}")

            me = context.request.get(PORTAL + "/bff/me").json()
            c.check(me.get("authenticated") is True, f"/bff/me authenticated = {me.get('authenticated')}")
            roles = me.get("roles") or []
            c.check("ROLE_ADMIN" in roles, f"/bff/me roles = {roles}")
            c.check(
                any_visible(page.get_by_text(ADMIN["username"], exact=False)),
                "header displays the logged-in username",
            )
            snap("S2", "journey-login", "登录成功，顶栏出现'退出'",
                 logged_in and me.get("authenticated") is True, page.url)
            snap("S3", "journey-session", "/bff/me authenticated=true 且含 ROLE_ADMIN",
                 me.get("authenticated") is True and "ROLE_ADMIN" in roles, str(me))

            # ---- 4. 退出（RP-initiated logout）----------------------------------------
            c.check(
                click_first_visible(page.get_by_role("button", name="退出", exact=True)),
                "clicked the '退出' entry",
            )
            for _ in range(40):  # 轮询等 BFF 会话消失（最多 20s）
                if context.request.get(PORTAL + "/bff/me").json().get("authenticated") is False:
                    break
                page.wait_for_timeout(500)
            me_after = context.request.get(PORTAL + "/bff/me").json()
            c.check(
                me_after.get("authenticated") is False,
                f"after logout /bff/me authenticated = {me_after.get('authenticated')}",
            )
            if not wait_any_visible(page.get_by_role("button", name="登录", exact=True), timeout_s=10):
                page.goto(PORTAL + "/", wait_until="networkidle")
                wait_any_visible(page.get_by_role("button", name="登录", exact=True), timeout_s=10)
            back_to_login = any_visible(page.get_by_role("button", name="登录", exact=True))
            c.check(back_to_login, "header restored to logged-out '登录' button")
            snap("S4", "journey-logout", "会话失效且回到'登录'按钮",
                 me_after.get("authenticated") is False and back_to_login, str(me_after))
        except Exception as exc:  # noqa: BLE001
            c.check(False, f"unexpected error: {type(exc).__name__}: {exc}")
            page.screenshot(path=str(ARTIFACTS / "pw_02_error.png"), full_page=True)
            if run is not None:
                run.log(f"unexpected error: {type(exc).__name__}: {exc}", level="ERROR")
        finally:
            context.close()
            browser.close()

    if run is not None:
        run.finish("PASS" if not c.failures else "FAIL")
    return c.finish()


if __name__ == "__main__":
    sys.exit(main())
