"""阶段 2 —— AI 生成测试数据（等价类 · 边界值 · 异常数据）。

模型只写*数据*，不写代码、也不下判定：一个扁平 JSON 数组，元素形如
``{id, kind, input, expected, expect_all, nodes, page_state, note}``。本脚本只校验
schema/分布并存到 ``bin/test/data/<tool>.json``，供 ``dd_01_data_driven.py``
确定性执行（循环里没有 LLM）。

为什么是数据而不是代码：数据能扛住 UI 改版、人几秒就能审完，而且可被确定性运行器
（dd_01）和 AI 裁判（bu_03/04/05）同时复用。

用法：
    bin\\test\\.venv\\Scripts\\python.exe bin\\test\\ai_02_generate_data.py --spec list-tree
    ... --dry-run        # 只打印 prompt，不调用模型
    ... --count 10       # 提示要生成多少行（默认 10）
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time

from _common import PORTAL, REPO_ROOT, TEST_DIR, DEFAULT_MODEL, chat  # noqa: F401  (side effect: browser path env)
from _results import new_run
from ai_specs import DATA_GEN_TASK, SPECS, build_data_prompt

DATA_DIR = TEST_DIR / "data"

# 本轨专属的 system prompt：只许输出 JSON（模型返回内容仍由下面 extract_json / 分布校验兜底）。
SYSTEM_DATA = "You are a senior test engineer. Output only the requested JSON, no explanations."


def extract_json(text: str):
    """Parse the first JSON value in the reply, tolerating fences, prose and trailing junk."""
    cleaned = text.strip()
    fenced = re.search(r"```(?:json)?\s*(.*?)```", cleaned, flags=re.S)
    if fenced:
        cleaned = fenced.group(1).strip()
    try:
        return json.loads(cleaned)
    except json.JSONDecodeError:
        pass
    # ``raw_decode`` returns the first complete value and ignores anything after it, which
    # covers "Extra data" (two arrays, a trailing explanation, JSONL-ish output).
    for idx, ch in enumerate(cleaned):
        if ch not in "[{":
            continue
        try:
            value, _ = json.JSONDecoder().raw_decode(cleaned[idx:])
            return value
        except json.JSONDecodeError:
            continue
    raise ValueError("model reply contains no parsable JSON")


#: `kind` taxonomy: valid | equivalence-* | boundary-* | invalid
KIND_RE = re.compile(r"^(valid|equivalence[-_][a-z0-9-]+|boundary[-_][a-z0-9-]+|invalid([-_][a-z0-9-]+)?)$")

#: Prose markers that betray a description instead of a literal page fragment.
PROSE_MARKERS = ("应该", "应显示", "应出现", "应为", "需要", "确保", "验证", "说明", "表示", "提示", "。")

TASK = DATA_GEN_TASK + """

## 参考工具
参考示例只用于说明字段写法；生成的数据必须是你自己的新输入，不得复制示例。"""


def validate(cases, tool: str = "") -> tuple[list[dict], list[str], dict]:
    """Schema + distribution validation. Returns (clean_cases, errors, stats)."""
    errors: list[str] = []
    if not isinstance(cases, list) or not cases:
        return [], ["payload is not a non-empty JSON array"], {}

    seen_ids: set[str] = set()
    seen_inputs: set[str] = set()
    clean: list[dict] = []
    counts = {"equivalence": 0, "boundary": 0, "invalid": 0, "valid": 0}

    for i, raw in enumerate(cases, 1):
        where = f"case#{i}"
        row_errors_start = len(errors)
        if not isinstance(raw, dict):
            errors.append(f"{where}: not a JSON object")
            continue
        cid = str(raw.get("id") or "").strip()
        kind = str(raw.get("kind") or "").strip().lower()
        inp = raw.get("input")
        exp = raw.get("expected")

        if not cid:
            errors.append(f"{where}: missing id")
        elif cid in seen_ids:
            errors.append(f"{where}: duplicate id {cid!r}")
        if not KIND_RE.match(kind):
            errors.append(f"{where}: invalid kind {kind!r}")
        if not isinstance(inp, str):
            errors.append(f"{where}: input must be a string")
        elif inp in seen_inputs:
            errors.append(f"{where}: duplicate input")
        if not isinstance(exp, str) or not exp.strip():
            errors.append(f"{where}: expected must be a non-empty string")
        elif len(exp) > 200:
            errors.append(f"{where}: expected is longer than 200 chars")
        elif any(marker in exp for marker in PROSE_MARKERS):
            # `expected` is matched verbatim against page text; prose can never match.
            errors.append(f"{where}: expected looks like prose, not literal page text: {exp!r}")
        nodes = raw.get("nodes")
        if nodes is not None and (not isinstance(nodes, int) or isinstance(nodes, bool)):
            errors.append(f"{where}: nodes must be an integer or omitted")
        state = raw.get("page_state")
        if state not in (None, "disabled"):
            errors.append(f"{where}: page_state must be 'disabled' or omitted")
        extra = raw.get("expect_all")
        if extra is not None and (
            not isinstance(extra, list) or not all(isinstance(x, str) and x for x in extra)
        ):
            errors.append(f"{where}: expect_all must be an array of non-empty strings")

        if len(errors) > row_errors_start:
            continue  # do not keep a malformed row

        seen_ids.add(cid)
        seen_inputs.add(inp)
        if kind.startswith("equivalence"):
            counts["equivalence"] += 1
        elif kind.startswith("boundary"):
            counts["boundary"] += 1
        elif kind.startswith("invalid"):
            counts["invalid"] += 1
        else:
            counts["valid"] += 1
        clean.append(
            {
                "id": cid,
                "kind": kind,
                "input": inp,
                "expected": exp.strip(),
                "expect_all": extra or [],
                "nodes": nodes,
                "page_state": state,
                "note": str(raw.get("note") or "").strip(),
            }
        )

    return clean, errors, counts


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--spec", default="list-tree", choices=sorted(SPECS))
    ap.add_argument("--model", default=DEFAULT_MODEL)
    ap.add_argument("--count", type=int, default=10)
    ap.add_argument("--timeout", type=int, default=240)
    ap.add_argument("--dry-run", action="store_true", help="print the prompt and exit")
    args = ap.parse_args()

    spec = SPECS[args.spec]
    seeds = spec["cases"][:2]
    prompt = build_data_prompt(
        TASK + f"\n目标条数：约 {args.count} 条。", PORTAL, seeds, pre=spec.get("pre")
    )
    if args.dry_run:
        print(prompt)
        return 0

    print(f"[AI] generating data for spec={args.spec} model={args.model} ...", flush=True)
    run = new_run(tool=args.spec, mode="generate-data", source="ai_02_generate_data.py", model=args.model)
    run.log("prompt assembled", prompt_chars=len(prompt), target=args.count)

    text = chat(prompt, model=args.model, system=SYSTEM_DATA, timeout=args.timeout)
    try:
        payload = extract_json(text)
    except ValueError as exc:
        DATA_DIR.mkdir(parents=True, exist_ok=True)
        raw_out = DATA_DIR / f"{args.spec}.response.md"
        raw_out.write_text(text, encoding="utf-8", newline="\n")
        run.check("reply parsed as JSON", False, str(exc))
        print(f"  [FAIL] reply parsed as JSON — {exc}")
        print(f"[RAW] saved unparsable reply to {raw_out}")
        run.finish("FAIL", meta={"reason": "unparsable JSON", "raw": run.rel(raw_out)})
        print(f"[ARCHIVE] {run.path}")
        return 1
    cases, errors, counts = validate(payload, args.spec)

    run.check("reply parsed as JSON", True, f"{len(payload) if isinstance(payload, list) else 1} raw rows")
    run.check("schema valid", not errors, "; ".join(errors[:6]) if errors else None)
    run.check("equivalence classes >= 3", counts.get("equivalence", 0) >= 3,
              f"equivalence={counts.get('equivalence', 0)}")
    run.check("boundary values >= 3", counts.get("boundary", 0) >= 3,
              f"boundary={counts.get('boundary', 0)}")
    run.check("invalid data >= 2", counts.get("invalid", 0) >= 2,
              f"invalid={counts.get('invalid', 0)}")
    for chk in run.checks:
        print(f"  [{'PASS' if chk['ok'] else 'FAIL'}] {chk['name']}" + (f" — {chk['detail']}" if chk["detail"] else ""))

    if errors:
        print("\n[ERRORS]")
        for e in errors[:20]:
            print(f"  - {e}")
        run.finish("FAIL", meta={"errors": errors[:20], "cases": len(cases)})
        print(f"[ARCHIVE] {run.path}")
        return 1

    DATA_DIR.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    out = DATA_DIR / f"{args.spec}.json"
    payload_out = {
        "tool": args.spec,
        "generated_at": stamp,
        "model": args.model,
        "portal": PORTAL,
        "counts": counts,
        "cases": cases,
    }
    out.write_text(json.dumps(payload_out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"\n[DATA] wrote {out.relative_to(REPO_ROOT)} ({len(cases)} cases, {counts})")

    ok = all(chk["ok"] for chk in run.checks)
    run.finish("GENERATED" if ok else "FAIL", meta={**counts, "data_file": run.rel(out), "cases": len(cases)})
    print(f"[ARCHIVE] {run.path}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
