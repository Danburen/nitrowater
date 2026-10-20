"""阶段 2 —— 数据驱动功能测试框架（读 JSON/CSV → 逐行执行 → 判定）。

这是 AI 自判轨道的确定性对照面：

* ``bu_03/04/05`` —— LLM 驱动浏览器并判定（探索性、不可重复）。
* ``dd_01``（本文件）—— 读数据文件（由 ``ai_02`` 生成或手写），把每一行都过一遍
  同一套 UI，并对期望文本做**机器校验**。

因为期望值就是页面原文（"共 5 个节点"、"JSON 解析错误"、某段输出片段），判定不依赖模型。
这让本轨道适合进 CI，也给 AI 生成的数据配了一个硬性、可重复的 oracle。

数据格式（JSON；CSV 用相同列名）：

    {"cases": [{"id": "D1", "kind": "equivalence-valid", "input": "...",
                "expected": "共 5 个节点", "expect_all": [], "nodes": 5,
                "page_state": null, "note": "..."}]}

用法：
    bin\\test\\.venv\\Scripts\\python.exe bin\\test\\dd_01_data_driven.py --spec list-tree
    ... --data bin\\test\\data\\list-tree.csv --headed
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
import time
from pathlib import Path

from playwright.sync_api import sync_playwright

from _common import PORTAL, TEST_DIR
from _results import new_run

DATA_DIR = TEST_DIR / "data"

TOOLS = {
    "list-tree": {
        "route": "/tools/list-tree",
        "button": "渲染树列表",
        "empty_disabled": True,  # :disabled="!rawText.trim()"
        "stat": True,            # 「共 N 个节点」
    },
    "java-to-ts": {
        "route": "/tools/java-to-ts",
        "button": "转换为 TypeScript",
        "empty_disabled": False,  # clicking empty shows a toast instead
        "stat": False,
        "output_textarea": True,   # 2nd textarea holds the generated TS
    },
    "tree": {
        "route": "/tools/tree",
        "button": "渲染树形图",
        "empty_disabled": True,
        "stat": True,
    },
}


def load_cases(path: Path) -> list[dict]:
    """Read JSON (object with `cases` or a bare array) or CSV with the same columns."""
    if path.suffix.lower() == ".csv":
        with path.open(encoding="utf-8-sig", newline="") as fh:
            rows = list(csv.DictReader(fh))
    else:
        raw = json.loads(path.read_text(encoding="utf-8"))
        rows = raw.get("cases", []) if isinstance(raw, dict) else raw

    cases: list[dict] = []
    for row in rows:
        nodes = row.get("nodes")
        if isinstance(nodes, str):
            nodes = int(nodes) if nodes.strip() else None
        extra = row.get("expect_all")
        if isinstance(extra, str):
            extra = [x for x in (s.strip() for s in extra.split("|")) if x]
        state = row.get("page_state") or None
        cases.append(
            {
                "id": str(row.get("id") or "").strip(),
                "kind": str(row.get("kind") or "").strip(),
                "input": row.get("input") or "",
                "expected": str(row.get("expected") or "").strip(),
                "expect_all": extra or [],
                "nodes": nodes,
                "page_state": state,
                "note": str(row.get("note") or "").strip(),
            }
        )
    return cases


def page_text(page, tool: str) -> str:
    """Visible page text, plus textarea values for tools whose output lives in a textarea."""
    text = page.inner_text("body")
    if TOOLS[tool].get("output_textarea"):
        values = page.eval_on_selector_all("textarea", "els => els.map(e => e.value).join('\\n')")
        text += "\n---TEXTAREA---\n" + (values or "")
    return text


def execute_case(page, tool: str, case: dict) -> tuple[str, str]:
    """Run one data row through the UI; return (verdict, evidence)."""
    cfg = TOOLS[tool]
    raw_input = str(case.get("input") or "")
    button = page.get_by_role("button", name=cfg["button"]).first
    page.locator("textarea").first.fill(raw_input)

    # Empty input is a first-class boundary case: two tools disable the button on purpose.
    expect_disabled = case.get("page_state") == "disabled" or (
        cfg["empty_disabled"] and not raw_input.strip()
    )
    if expect_disabled:
        disabled = button.is_disabled()
        return ("PASS" if disabled else "FAIL"), f"button disabled={disabled} (empty input)"

    button.click()
    page.wait_for_timeout(500)  # toast / parse error / chart update
    text = page_text(page, tool)

    missing: list[str] = []
    nodes = case.get("nodes")
    if nodes is not None and f"{nodes} 个节点" not in text:
        missing.append(f"{nodes} 个节点")
    for needle in [case.get("expected"), *(case.get("expect_all") or [])]:
        if needle and needle not in text:
            missing.append(str(needle))

    if missing:
        return "FAIL", "missing: " + " | ".join(missing[:4])
    return "PASS", f"found: {case.get('expected')}"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--spec", default="list-tree", choices=sorted(TOOLS))
    ap.add_argument("--data", default="", help="JSON/CSV data file (default: bin/test/data/<spec>.json)")
    ap.add_argument("--headed", action="store_true")
    args = ap.parse_args()

    cfg = TOOLS[args.spec]
    data_path = Path(args.data) if args.data else DATA_DIR / f"{args.spec}.json"
    if not data_path.exists():
        print(f"Data file not found: {data_path}\nGenerate it first: ai_02_generate_data.py --spec {args.spec}")
        return 1
    cases = load_cases(data_path)
    if not cases:
        print(f"No cases in {data_path}")
        return 1

    run = new_run(tool=args.spec, mode="data-driven", source="dd_01_data_driven.py")
    run.log("data loaded", file=run.rel(data_path), cases=len(cases))

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=not args.headed)
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()
        try:
            # Journey: portal → toolbox → tool page (same path the AI tracks must take).
            page.goto(PORTAL + "/", wait_until="networkidle")
            page.get_by_role("button", name="工具箱", exact=True).first.click()
            page.wait_for_url("**/tools")
            page.locator(f'a[href="{cfg["route"]}"]').first.click()
            page.wait_for_url(f"**{cfg['route']}")
            page.wait_for_timeout(300)
            run.check("navigation reached tool page", True, page.url)

            for case in cases:
                started = time.perf_counter()
                try:
                    verdict, evidence = execute_case(page, args.spec, case)
                except Exception as exc:  # noqa: BLE001 - a broken row must not stop the run
                    verdict, evidence = "FAIL", f"error: {type(exc).__name__}: {exc}"
                elapsed = int((time.perf_counter() - started) * 1000)

                shot = None
                if verdict != "PASS":
                    shot = run.screenshot_path(case["id"])
                    page.screenshot(path=str(shot), full_page=True)

                run.case(
                    case["id"],
                    kind=case.get("kind"),
                    input=case.get("input"),
                    expected=case.get("expected"),
                    verdict=verdict,
                    evidence=evidence,
                    screenshot=shot,
                    duration_ms=elapsed,
                    meta={"note": case.get("note")},
                )
                print(f"  [{verdict}] {case['id']:<4} {case.get('kind',''):<24} {evidence[:90]} ({elapsed} ms)")
        except Exception as exc:  # noqa: BLE001
            run.check("no unexpected driver error", False, f"{type(exc).__name__}: {exc}")
            print(f"[ERROR] {type(exc).__name__}: {exc}")
        finally:
            context.close()
            browser.close()

    verdicts = [c for c in (r for r in _read_cases(run.path)) if c]
    passed = sum(1 for v in verdicts if v == "PASS")
    failed = len(verdicts) - passed
    status = "PASS" if failed == 0 and verdicts else "FAIL"
    run.finish(status, meta={"data_file": run.rel(data_path), "cases": len(cases), "passed": passed, "failed": failed})
    print(f"\n== dd_01 {args.spec}: {status} ({passed}/{len(cases)} cases) ==")
    print(f"[ARCHIVE] {run.path}")
    return 0 if status == "PASS" else 1


def _read_cases(path: Path) -> list[str]:
    """Case verdicts already archived for this run (single source of truth)."""
    out: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        rec = json.loads(line)
        if rec.get("record") == "case":
            out.append(rec.get("verdict") or "")
    return out


if __name__ == "__main__":
    sys.exit(main())
