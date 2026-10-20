"""测试轨道的统一结果归档（六阶段计划的阶段 1）。

一次运行 = 一个只追加的 JSONL 文件：

    bin/test/results/<run_id>.jsonl

记录类型（schema 稳定；阶段 5/6 的 Excel / 报告都基于这些记录生成）：

* ``run_start`` —— run_id、tool、mode、source、model、started_at
* ``case``      —— case_id、kind、input、expected、verdict、evidence、screenshot、
                   started_at、duration_ms、meta
* ``log``       —— level、message、at
* ``run_end``   —— status、finished_at、duration_ms、checks[]、meta

截图落在 ``bin/test/artifacts/<run_id>/``（已 gitignore）。

设计原则（方案 B）：运行器只记录事实，永不判定功能对错。
``verdict`` 来自 LLM（或 pytest），不来自本模块。

命令行：

    python bin/test/_results.py runs          # 列出所有运行
    python bin/test/_results.py show latest   # 看最近一次
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

from _common import ARTIFACTS, TEST_DIR

RESULTS_DIR = TEST_DIR / "results"
SCHEMA_VERSION = 1


def _now() -> str:
    """Local ISO-8601 timestamp with millisecond precision."""
    return datetime.now().astimezone().isoformat(timespec="milliseconds")


def _slug(text: str) -> str:
    return re.sub(r"[^A-Za-z0-9_.-]+", "-", str(text)).strip("-.") or "run"


@dataclass
class Run:
    """A single test run; every method appends one JSONL record."""

    run_id: str
    tool: str
    mode: str
    source: str
    model: str | None = None
    started_at: str = field(default_factory=_now)
    checks: list[dict] = field(default_factory=list)
    _perf: float = field(default_factory=time.perf_counter)
    _path: Path = field(init=False)

    def __post_init__(self) -> None:
        RESULTS_DIR.mkdir(parents=True, exist_ok=True)
        self._path = RESULTS_DIR / f"{self.run_id}.jsonl"
        self._write(
            {
                "record": "run_start",
                "schema": SCHEMA_VERSION,
                "run_id": self.run_id,
                "tool": self.tool,
                "mode": self.mode,
                "source": self.source,
                "model": self.model,
                "started_at": self.started_at,
            }
        )

    # -- paths -----------------------------------------------------------------
    @property
    def path(self) -> Path:
        return self._path

    def screenshot_path(self, name: str) -> Path:
        """Return (and create) a per-run screenshot path, e.g. ``C1.png``."""
        folder = ARTIFACTS / self.run_id
        folder.mkdir(parents=True, exist_ok=True)
        return folder / f"{_slug(name)}.png"

    def rel(self, path: Path) -> str:
        """Repo-relative POSIX path for stable archive references."""
        try:
            return path.resolve().relative_to(TEST_DIR.parent.parent).as_posix()
        except ValueError:
            return path.as_posix()

    # -- records ---------------------------------------------------------------
    def _write(self, record: dict) -> None:
        line = json.dumps(record, ensure_ascii=False, separators=(",", ":"))
        with self._path.open("a", encoding="utf-8", newline="\n") as fh:
            fh.write(line + "\n")

    def case(
        self,
        case_id: str,
        *,
        kind: str | None = None,
        input: object = None,
        expected: object = None,
        verdict: str | None = None,
        evidence: str | None = None,
        screenshot: str | Path | None = None,
        duration_ms: int | None = None,
        meta: dict | None = None,
    ) -> None:
        self._write(
            {
                "record": "case",
                "run_id": self.run_id,
                "case_id": str(case_id),
                "kind": kind,
                "input": input,
                "expected": expected,
                "verdict": (verdict or "").upper() or None,
                "evidence": evidence,
                "screenshot": self.rel(Path(screenshot)) if screenshot else None,
                "started_at": _now(),
                "duration_ms": duration_ms,
                "meta": meta or {},
            }
        )

    def log(self, message: str, level: str = "INFO", **meta) -> None:
        self._write(
            {
                "record": "log",
                "run_id": self.run_id,
                "level": level.upper(),
                "message": message,
                "at": _now(),
                "meta": meta,
            }
        )

    def check(self, name: str, ok: bool, detail: str | None = None) -> bool:
        """Record a non-functional gate result (never a functional assertion)."""
        self.checks.append({"name": name, "ok": bool(ok), "detail": detail})
        return bool(ok)

    def finish(self, status: str, meta: dict | None = None) -> None:
        self._write(
            {
                "record": "run_end",
                "run_id": self.run_id,
                "status": status.upper(),
                "finished_at": _now(),
                "duration_ms": int((time.perf_counter() - self._perf) * 1000),
                "checks": self.checks,
                "meta": meta or {},
            }
        )


def new_run(
    tool: str,
    mode: str,
    source: str,
    model: str | None = None,
    run_id: str | None = None,
) -> Run:
    """Create a run and immediately write its ``run_start`` record."""
    if run_id is None:
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        run_id = f"{stamp}-{_slug(tool)}-{_slug(mode)}"
        base, n = run_id, 2
        while (RESULTS_DIR / f"{run_id}.jsonl").exists():  # same-second collision
            run_id = f"{base}-{n}"
            n += 1
    return Run(run_id=run_id, tool=tool, mode=mode, source=source, model=model)


# ---------------------------------------------------------------------------
# Reading helpers (used by phases 5/6 and by the CLI below).
# ---------------------------------------------------------------------------


def load_run(path: str | Path) -> list[dict]:
    p = Path(path)
    if p.suffix != ".jsonl":
        p = RESULTS_DIR / f"{p}.jsonl"
    with p.open(encoding="utf-8") as fh:
        return [json.loads(line) for line in fh if line.strip()]


def list_runs() -> list[Path]:
    if not RESULTS_DIR.exists():
        return []
    return sorted(RESULTS_DIR.glob("*.jsonl"))


def resolve_run(run_id: str) -> Path:
    if run_id == "latest":
        runs = list_runs()
        if not runs:
            raise SystemExit("No runs archived yet under bin/test/results/.")
        return runs[-1]
    return RESULTS_DIR / f"{run_id}.jsonl"


def summarize(path: str | Path) -> dict:
    """Fold a run's records into a summary dict (skeleton for later phases)."""
    records = load_run(path)
    start = next((r for r in records if r["record"] == "run_start"), {})
    end = next((r for r in reversed(records) if r["record"] == "run_end"), {})
    cases = [r for r in records if r["record"] == "case"]
    verdicts = [c.get("verdict") for c in cases]
    return {
        "run_id": start.get("run_id"),
        "tool": start.get("tool"),
        "mode": start.get("mode"),
        "status": end.get("status", "RUNNING"),
        "started_at": start.get("started_at"),
        "finished_at": end.get("finished_at"),
        "duration_ms": end.get("duration_ms"),
        "cases": len(cases),
        "passed": sum(1 for v in verdicts if v == "PASS"),
        "failed": sum(1 for v in verdicts if v == "FAIL"),
        "unjudged": sum(1 for v in verdicts if not v),
        "checks": end.get("checks", []),
        "logs": sum(1 for r in records if r["record"] == "log"),
    }


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def _cmd_runs() -> int:
    runs = list_runs()
    if not runs:
        print("(no runs)")
        return 0
    print(f"{'run_id':<44} {'status':<8} {'cases':>5} {'pass':>4} {'fail':>4}")
    for p in runs:
        s = summarize(p)
        print(f"{s['run_id']:<44} {s['status']:<8} {s['cases']:>5} {s['passed']:>4} {s['failed']:>4}")
    return 0


def _cmd_show(run_id: str) -> int:
    path = resolve_run(run_id)
    if not path.exists():
        raise SystemExit(f"No such run: {path.name}")
    s = summarize(path)
    print(f"run_id={s['run_id']} tool={s['tool']} mode={s['mode']} status={s['status']}")
    print(f"cases={s['cases']} pass={s['passed']} fail={s['failed']} unjudged={s['unjudged']} logs={s['logs']}")
    for chk in s["checks"]:
        print(f"  [{'PASS' if chk['ok'] else 'FAIL'}] {chk['name']}" + (f" — {chk['detail']}" if chk.get("detail") else ""))
    for r in load_run(path):
        if r["record"] == "case":
            ev = (r.get("evidence") or "").replace("\n", " ")[:70]
            print(f"  {r['case_id']:<4} {str(r.get('verdict') or '-'):<5} {str(r.get('kind') or ''):<24} {ev}")
    return 0


def main() -> int:
    try:  # keep Chinese evidence text printable on a GBK console
        sys.stdout.reconfigure(errors="replace")
    except Exception:
        pass
    ap = argparse.ArgumentParser(description="Inspect archived test runs (JSONL).")
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("runs", help="list archived runs")
    show = sub.add_parser("show", help="show one run (run_id or 'latest')")
    show.add_argument("run_id", nargs="?", default="latest")
    args = ap.parse_args()
    return _cmd_runs() if args.cmd == "runs" else _cmd_show(args.run_id)


if __name__ == "__main__":
    sys.exit(main())
