"""TASK 式 AI 矩阵轨的共用运行器（``bu_03`` / ``bu_04`` / ``bu_05``）。

轨道文件只持有自己的模块级 ``TASK`` 常量（主任务体）；本模块负责机制部分：
prompt 组装、浏览器会话、非功能门禁、证据留档与结果归档。

职责划分（方案 B）：

* **模型**是裁判。它在真实 UI 上逐行执行矩阵，并按行给出 PASS/FAIL，每行附一条
  从页面读到的证据引用。
* **脚本**永不断言功能结果。它只拒绝"不完整、自相矛盾、或没真看页面就写出来"的报告
  （防伪造），并把每一条事实归档到 ``bin/test/results/<run_id>.jsonl``。

Prompt 组装在 :mod:`ai_specs`（PRE + TASK + DATA + COVERAGE + FINAL）。
"""

from __future__ import annotations

import argparse
import asyncio
import base64
import re
import sys
from pathlib import Path
from typing import Iterable

from _common import PORTAL, Checker, describe_llm, pick, run_agent_task, start_browser
from _results import new_run
from ai_specs import SPECS, build_matrix_prompt

# \b anchor: verdict must be exactly PASS/FAIL — without it "CASE C1: PASSED" prefix-matched as PASS.
CASE_RE = re.compile(r"CASE\s+([A-Za-z0-9_-]+)\s*[^:]*:\s*(PASS|FAIL)\b", re.I)

#: Actions that prove the agent actually read page state instead of inferring it.
OBSERVATION_ACTIONS = {"search_page", "evaluate", "extract_content", "read_file", "scroll"}


def parse_verdicts(text: str) -> dict[str, str]:
    """Extract 'CASE C1 (...): PASS|FAIL' rows; the last mention of an id wins."""
    out: dict[str, str] = {}
    for cid, verdict in CASE_RE.findall(text or ""):
        out[cid.upper()] = verdict.upper()
    return out


def parse_case_details(text: str) -> dict[str, dict]:
    """Per-case verdict + evidence quote, parsed from the model's report."""
    out: dict[str, dict] = {}
    for line in (text or "").splitlines():
        m = CASE_RE.search(line)
        if not m:
            continue
        cid, verdict = m.group(1).upper(), m.group(2).upper()
        tail = line[m.end():]
        evidence = tail.split("evidence:", 1)[1] if "evidence:" in tail else tail
        out[cid] = {"verdict": verdict, "evidence": evidence.strip(" :—-")[:800]}
    return out


def count_observations(history) -> int:
    """Count page-reading actions the agent performed (process gate, not an assertion)."""
    try:
        actions = history.model_actions() or []
    except Exception:
        return 0
    n = 0
    for action in actions:
        if isinstance(action, dict):
            name = next(iter(action), "")
            if str(name) in OBSERVATION_ACTIONS:
                n += 1
    return n


def select_cases(spec: dict, only: Iterable[str]) -> list[dict]:
    wanted = {x.strip().upper() for x in only if x and x.strip()}
    return [c for c in spec["cases"] if not wanted or c["id"].upper() in wanted]


async def run_matrix(
    *,
    spec_name: str,
    task: str,
    headed: bool = False,
    only: Iterable[str] = (),
    max_steps: int = 70,
    dry_run: bool = False,
) -> int:
    spec = SPECS[spec_name]
    cases = select_cases(spec, only)
    if not cases:
        print(f"No cases selected (--only={sorted(only)})")
        return 1

    task_text = build_matrix_prompt(
        task, PORTAL, cases, pre=spec.get("pre"), paths=spec.get("paths")
    )
    if dry_run:
        print(task_text)
        return 0

    run = new_run(
        tool=spec_name,
        mode="matrix-ai",
        source=Path(sys.argv[0]).name,
        model=describe_llm(transport="browser-use"),
    )
    run.log("prompt assembled", cases=len(cases), prompt_chars=len(task_text))

    title = f"{spec_name} · {spec['title']} (Browser Use + DeepSeek)"
    c = Checker(title)
    status = "ERROR"
    meta: dict = {"max_steps": max_steps, "cases_requested": len(cases)}
    session = await start_browser(headed)
    try:
        history = await run_agent_task(task_text, session, max_steps=max_steps)
        final = str(pick(history.final_result) or "")
        print("\n---- agent report ----")
        print(final)
        print("----------------------\n")
        run.log("agent report captured", chars=len(final))

        details = parse_case_details(final)
        verdicts = parse_verdicts(final)

        # Gate 1: completeness — every selected row was judged. No functional assertions.
        missing = [x["id"] for x in cases if x["id"].upper() not in verdicts]
        ok_missing = run.check(
            "every selected case was judged",
            not missing,
            f"{len(verdicts)}/{len(cases)}" + (f", missing={missing}" if missing else ""),
        )
        c.check(ok_missing, f"agent judged every case ({len(verdicts)}/{len(cases)}"
                            + (f", missing={missing}" if missing else ")"))

        # Gate 2: consistency — no FAIL row and the final VERDICT must be PASS.
        failed = [cid for cid, v in verdicts.items() if v == "FAIL"]
        ok_all_pass = run.check(
            "no case reported FAIL", not failed, f"failed={failed}" if failed else None
        )
        c.check(ok_all_pass, "agent verdict: all cases PASS" if not failed else f"agent reported FAIL: {failed}")

        ok_verdict = run.check(
            "final VERDICT line is PASS",
            re.search(r"VERDICT:\s*PASS", final, re.I) is not None,
        )
        c.check(ok_verdict, "agent final VERDICT line is PASS")

        # Gate 3: anti-fabrication — the model must have read the page, not inferred results.
        observations = count_observations(history)
        ok_obs = run.check(
            "page read after each case",
            observations >= len(cases),
            f"{observations} reads / {len(cases)} cases",
        )
        c.check(ok_obs, f"agent read the page after each case ({observations} reads / {len(cases)} cases)")

        # Advisory only (second LLM opinion); not part of the pass/fail gate.
        try:
            print(f"[ADVISORY] browser-use judge is_successful={history.is_successful()}")
        except Exception:
            pass

        # Archive: one record per case (verdict + evidence from the model's report).
        for case in cases:
            d = details.get(case["id"].upper(), {})
            run.case(
                case["id"],
                kind=case.get("kind"),
                input=case.get("input"),
                expected=case.get("expect"),
                verdict=d.get("verdict"),
                evidence=d.get("evidence"),
                meta={"observed": bool(d)},
            )

        # Evidence capture only: final page state is recorded, not asserted.
        page = await session.must_get_current_page()
        url = await page.get_url()
        body = await page.evaluate("() => document.body.innerText")
        shot = run.screenshot_path("final")
        # browser-use's Page.screenshot returns base64 (no `path` argument like Playwright).
        shot.write_bytes(base64.b64decode(await page.screenshot(format="png")))
        print(f"[EVIDENCE] url={url}")
        print(f"[EVIDENCE] visible_chars={len(body or '')} screenshot={run.rel(shot)}")
        meta.update(
            final_url=url,
            visible_chars=len(body or ""),
            final_screenshot=run.rel(shot),
            reads=observations,
        )
        status = "PASS" if all(chk["ok"] for chk in run.checks) else "FAIL"
    except Exception as exc:  # noqa: BLE001 - surface driver errors as a failed run
        run.check("no unexpected driver error", False, f"{type(exc).__name__}: {exc}")
        c.check(False, f"unexpected error: {type(exc).__name__}: {exc}")
        meta["error"] = f"{type(exc).__name__}: {exc}"
        status = "ERROR"
    finally:
        await session.stop()

    rc = c.finish()
    run.finish(status, meta=meta)
    print(f"[ARCHIVE] {run.path}")
    return rc


def main(spec_name: str, task: str) -> int:
    """CLI entry point shared by the matrix tracks."""
    ap = argparse.ArgumentParser(description=f"AI matrix track: {spec_name}")
    ap.add_argument("--spec", default=spec_name, choices=sorted(SPECS),
                    help="override the spec (must match the TASK body)")
    ap.add_argument("--headed", action="store_true", help="watch the AI drive a visible browser")
    ap.add_argument("--only", default="", help="comma-separated case ids, e.g. C1,C2,C7")
    ap.add_argument("--max-steps", type=int, default=70)
    ap.add_argument("--dry-run", action="store_true", help="print the assembled prompt and exit")
    args = ap.parse_args()
    only = {x.strip().upper() for x in args.only.split(",") if x.strip()}
    return asyncio.run(
        run_matrix(
            spec_name=args.spec,
            task=task,
            headed=args.headed,
            only=only,
            max_steps=args.max_steps,
            dry_run=args.dry_run,
        )
    )


if __name__ == "__main__":
    sys.exit(0)
