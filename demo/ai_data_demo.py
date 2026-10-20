"""AI 生成测试数据演示 —— 一次 LLM 调用产出等价类/边界/异常数据并打印。

    bin\\test\\.venv\\Scripts\\python.exe demo\\ai_data_demo.py

演示"AI 想数据、机器校验"的最小闭环：模型只输出 JSON，脚本做结构校验后打印。
正式批量生成请用 bin/test/ai_02_generate_data.py（含归档与门禁）。
"""

from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

import requests

DEEPSEEK_URL = "https://api.deepseek.com/chat/completions"

PROMPT = """\
为「列表树可视化」工具生成 6 条测试数据（中文备注），覆盖等价类/边界值/异常输入。
输入格式：每行一个节点，缩进或 ├── └── 表示层级。输出按钮点击后页面显示「共 N 个节点」。

只输出 JSON 数组，每个元素：
{{"id":"D1","kind":"valid|boundary-*|invalid","input":"...","expected":"页面原文片段（如：共 3 个节点）","nodes":3}}
- expected 必须是页面上会字面出现的文本，不要写"应该显示"这类描述
- 至少 2 条 invalid、2 条 boundary
"""


def main() -> int:
    key = os.environ.get("DEEPSEEK_API_KEY")
    if not key:
        print("DEEPSEEK_API_KEY is not set.")
        return 1

    print("[AI] requesting 6 test-data rows from deepseek-chat ...", flush=True)
    resp = requests.post(
        DEEPSEEK_URL,
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
        json={
            "model": "deepseek-chat",
            "temperature": 0.0,
            "messages": [
                {"role": "system", "content": "Output only the requested JSON array."},
                {"role": "user", "content": PROMPT},
            ],
        },
        timeout=180,
    )
    resp.raise_for_status()
    text = resp.json()["choices"][0]["message"]["content"]

    # Extract the first JSON array (tolerate fences/prose).
    cleaned = text.strip()
    fenced = re.search(r"```(?:json)?\s*(.*?)```", cleaned, flags=re.S)
    if fenced:
        cleaned = fenced.group(1).strip()
    for idx, ch in enumerate(cleaned):
        if ch != "[":
            continue
        try:
            data, _ = json.JSONDecoder().raw_decode(cleaned[idx:])
            break
        except json.JSONDecodeError:
            continue
    else:
        print("[FAIL] no parsable JSON in reply:")
        print(text[:500])
        return 1

    if not isinstance(data, list) or not data:
        print("[FAIL] reply is not a non-empty array")
        return 1

    # Minimal structural validation.
    kinds = {str(row.get("kind", "")) for row in data}
    has_invalid = any(k.startswith("invalid") for k in kinds)
    has_boundary = any(k.startswith("boundary") for k in kinds)

    print(f"\n[DATA] {len(rows := data)} rows  invalid={has_invalid}  boundary={has_boundary}")
    for row in data:
        print(f"  {row.get('id'):<4} {row.get('kind'):<18} expected={row.get('expected')!r}")
        print(f"       input={json.dumps(row.get('input'), ensure_ascii=False)[:80]}")

    ok = has_invalid and has_boundary
    print(f"\n== ai_data_demo: {'PASS' if ok else 'FAIL'} ==")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
