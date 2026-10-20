"""AI 测试轨道的规范与 prompt 组装器。

所有轨道共用的 prompt 拼接顺序（依次拼接）：

    <PRE（本模块）> + <TASK（轨道文件里的模块级常量）> + <DATA（本模块）>
    + <COVERAGE（本模块）> + <FINAL 输出约束（本模块）>

职责划分：

* *规范模块*（本文件）负责可复用的外壳：
    - ``MATRIX_PRE`` / ``JAVA_TO_TS_PRE`` / ``TREE_PRE`` —— 预先提示词
      （被测功能、访问路径、页面结构、可读文本、解析规则），
    - ``MATRIX_FINAL`` / ``DATA_GEN_FINAL`` —— 最终输出约束（输出格式契约），
    - 数据矩阵 —— AI 执行轨和 AI 数据生成轨共享的唯一真源。
* 每个*轨道脚本*（``bu_03/04/05``、``ai_02``）自己持有一个模块级 ``TASK`` 常量
  （在文件顶部）：主任务体（模型要达成什么、怎么做、证据纪律）。要改"让模型干什么"，改那个常量。

轨道说明：

* ``bu_03/04/05`` —— LLM 驱动浏览器逐行执行矩阵并自行判定，每行附一条页面证据引用。
  脚本只检查报告完整性/一致性 + 页面观察纪律，并归档事实。

* ``ai_02`` —— LLM 只写*数据*（等价类、边界值、异常输入），输出扁平 JSON 数组；
  脚本校验 schema 并存盘，供 ``dd_01_data_driven.py`` 在没有 LLM 的情况下确定性地执行。
"""

from __future__ import annotations

# ---------------------------------------------------------------------------
# Data matrix: list-tree visualizer (anonymous tool, no captcha/lockout risk).
# 9 rows cover: valid, empty, single, deep, blank-line/trailing-space, unicode,
# malformed, large, re-render/stability.
# ---------------------------------------------------------------------------

SAMPLE_TREE = """project/
├── src/
│   ├── main.py
│   └── utils.py
└── README.md"""

# Second tree with the SAME node count but a DIFFERENT structure. Re-rendering C1 then
# C1B (same count, different content) would pass even if the renderer kept a stale list;
# rendering SAMPLE_TREE then ALT_TREE (4 nodes) forces the count to actually change, which
# is what C9 checks. Equivalence class: same-shape/different-content re-render.
ALT_TREE = """alpha/
├── x.py
├── y.py
└── z.md"""

DEEP_TREE = """a/
└── b/
    └── c/
        └── d/
            └── e.txt"""

# Blank line + trailing spaces mixed into the sample tree. Equivalence class: whitespace
# tolerance — exercises the parser's blank-line skip and line-end trim paths, which
# SAMPLE_TREE alone never hits. (CRLF was dropped: the browser textarea normalizes CRLF
# to LF on paste, so a CRLF row could never prove what it claimed.)
WHITESPACE_TREE = (
    "project/\n"
    "\n"
    "├── src/\n"
    "│   ├── main.py   \n"
    "│   └── utils.py\n"
    "   \n"
    "└── README.md"
)

UNICODE_TREE = """项目/
├── 源码/
│   └── 主程序.py
└── 说明.md"""

MALFORMED_TREE = "├── └── │ not-a-tree ???"

LARGE_TREE = "root/\n" + "\n".join(
    (f"├── file{i:02d}.txt" if i < 30 else f"└── file{i:02d}.txt") for i in range(1, 31)
)

LIST_TREE_CASES = [
    {
        "id": "C1",
        "kind": "valid",
        "input": SAMPLE_TREE,
        "nodes": 5,
        "expect": "正常渲染，统计为 5 个节点",
    },
    {
        "id": "C2",
        "kind": "boundary-empty",
        "input": "",
        "nodes": None,
        "expect": "空输入：渲染按钮应为 disabled（禁用），页面保持可用、不崩溃",
    },
    {
        "id": "C3",
        "kind": "boundary-single",
        "input": "a.txt",
        "nodes": 1,
        "expect": "单节点：统计为 1 个节点",
    },
    {
        "id": "C4",
        "kind": "boundary-deep",
        "input": DEEP_TREE,
        "nodes": 5,
        "expect": "深层嵌套（4 空格缩进）：5 个节点且层级正确",
    },
    {
        "id": "C5",
        "kind": "equivalence-whitespace",
        "input": WHITESPACE_TREE,
        "nodes": 5,
        "expect": "空行与行尾空格被忽略：仍为 5 个节点",
    },
    {
        "id": "C6",
        "kind": "equivalence-unicode",
        "input": UNICODE_TREE,
        "nodes": 4,
        "expect": "中文路径：4 个节点且不乱码",
    },
    {
        "id": "C7",
        "kind": "invalid",
        "input": MALFORMED_TREE,
        "nodes": None,
        "expect": "非法字符输入：容错处理（空结果或提示），页面不崩溃",
    },
    {
        "id": "C8",
        "kind": "boundary-large",
        "input": LARGE_TREE,
        "nodes": 31,
        "expect": "较大输入：31 个节点，渲染完成且页面可继续操作",
    },
    {
        "id": "C9",
        "kind": "stability-re-render",
        "input": ALT_TREE,
        "nodes": 4,
        "expect": "连续渲染后再次渲染新树：统计刷新为 4 个节点（不残留上一次的 31）",
    },
]

# ---------------------------------------------------------------------------
# Java DTO -> TypeScript converter matrix (client-side, no backend login needed).
# Covers: simple/single/generics/comment/unknown-type/rules/invalid/empty.
# ---------------------------------------------------------------------------

JAVA_SIMPLE = """public class RoleResp {
    private Integer id;
    private String name;
}"""

JAVA_SINGLE = """public class One {
    private String only;
}"""

JAVA_GENERICS = """public class PagedResp {
    private List<Map<String, List<Integer>>> rows;
}"""

JAVA_COMMENT = """public class UserResp {
    @ApiModelProperty("用户名称")
    private String name;
}"""

JAVA_UNKNOWN = """public class ExtResp {
    private MyCustomDTO ext;
}"""

JAVA_RULES = """public class RuleResp {
    @NotNull
    @Size(min = 2, max = 8)
    private String name;
}"""

JAVA_TO_TS_CASES = [
    {
        "id": "J1",
        "kind": "valid",
        "input": JAVA_SIMPLE,
        "expect": "生成 export interface RoleResp，含 id: number; 与 name: string;，并生成默认值对象 defaultRoleResp",
    },
    {
        "id": "J2",
        "kind": "boundary-single",
        "input": JAVA_SINGLE,
        "expect": "只有一个字段的类：接口 One 仅含 only: string;",
    },
    {
        "id": "J3",
        "kind": "boundary-generics",
        "input": JAVA_GENERICS,
        "expect": "泛型嵌套映射：rows: Array<Record<string, number[]>>;",
    },
    {
        "id": "J4",
        "kind": "equivalence-comment",
        "input": JAVA_COMMENT,
        "expect": "@ApiModelProperty 注释映射：name: string; // 用户名称",
    },
    {
        "id": "J5",
        "kind": "equivalence-unknown-type",
        "input": JAVA_UNKNOWN,
        "expect": "未知自定义类型：ext: MyCustomDTO; 带 FIXME 注释，并出现警告提示",
    },
    {
        "id": "J6",
        "kind": "path-rules",
        "input": JAVA_RULES,
        "expect": "校验规则分支：输出含 Element Plus rules（required: true、min: 2, max: 8）",
    },
    {
        "id": "J7",
        "kind": "invalid",
        "input": "hello world",
        "expect": "非 Java 代码：页面不崩溃、仍可操作；容错输出可能是无字段的 UnknownType 接口，不据此判失败",
    },
    {
        "id": "J8",
        "kind": "boundary-empty",
        "input": "",
        "expect": "空输入：点击转换出现「请输入 Java 代码」提示（toast 可能已消失）；输出区不会被清空（保留上一次结果，已知行为），页面不崩溃即通过",
    },
]

# ---------------------------------------------------------------------------
# Tree visualizer matrix (JSON -> ECharts tree; node count shown as 「共 N 个节点」).
# Covers: valid/empty/invalid/single/array-root/unicode/deep/large/field-mapping.
# ---------------------------------------------------------------------------

TREE_SIMPLE = '{"name":"root","children":[{"name":"a"},{"name":"b"}]}'
TREE_SINGLE = '{"name":"solo"}'
TREE_ARRAY_ROOT = '[{"name":"a"},{"name":"b"}]'
TREE_UNICODE = '{"name":"根","children":[{"name":"子"}]}'
TREE_DEEP = (
    '{"name":"L1","children":[{"name":"L2","children":[{"name":"L3",'
    '"children":[{"name":"L4","children":[{"name":"L5"}]}]}]}]}'
)
TREE_CUSTOM_FIELDS = '{"label":"r","kids":[{"label":"a"},{"label":"b"}]}'
TREE_LARGE = (
    '{"name":"root","children":['
    + ",".join(f'{{"name":"n{i:02d}"}}' for i in range(1, 31))
    + "]}"
)
TREE_INVALID = "{ not json"

TREE_CASES = [
    {
        "id": "T1",
        "kind": "valid",
        "input": TREE_SIMPLE,
        "nodes": 3,
        "expect": "正常 JSON：统计为 3 个节点（root + 2 children）",
    },
    {
        "id": "T2",
        "kind": "boundary-empty",
        "input": "",
        "nodes": None,
        "expect": "空输入：渲染按钮为 disabled（正确行为），页面保持可用",
    },
    {
        "id": "T3",
        "kind": "invalid",
        "input": TREE_INVALID,
        "nodes": None,
        "expect": "非法 JSON：出现「JSON 解析错误」提示，页面不崩溃；允许保留上一次的统计数字（已知行为）",
    },
    {
        "id": "T4",
        "kind": "boundary-single",
        "input": TREE_SINGLE,
        "nodes": 1,
        "expect": "单节点：统计为 1 个节点",
    },
    {
        "id": "T5",
        "kind": "equivalence-array-root",
        "input": TREE_ARRAY_ROOT,
        "nodes": 3,
        "expect": "数组根：自动加虚拟 root，统计为 3 个节点",
    },
    {
        "id": "T6",
        "kind": "equivalence-unicode",
        "input": TREE_UNICODE,
        "nodes": 2,
        "expect": "中文标签：2 个节点且不乱码",
    },
    {
        "id": "T7",
        "kind": "boundary-deep",
        "input": TREE_DEEP,
        "nodes": 5,
        "expect": "五层嵌套：统计为 5 个节点",
    },
    {
        "id": "T8",
        "kind": "boundary-large",
        "input": TREE_LARGE,
        "nodes": 31,
        "expect": "大输入：31 个节点且页面可继续操作",
    },
    {
        "id": "T9",
        "kind": "path-field-mapping",
        "input": TREE_CUSTOM_FIELDS,
        "nodes": 3,
        "expect": "字段映射：先在「图配置」把标签字段改为 label、子级字段改为 kids，再渲染；统计为 3 个节点",
    },
]

# ---------------------------------------------------------------------------
# Pre-prompts (预先提示词): fixed scene-setter prepended to every run of a track.
# ---------------------------------------------------------------------------

MATRIX_PRE = """你正在控制浏览器，对「列表树可视化」工具做一次参数化功能验证（站点 {portal}）。

## 访问路径（必须真实走一遍）
1. 打开 {portal}/ ，点击「开始使用工具箱」进入 /tools。
2. 点击「列表树可视化」卡片，进入 /tools/list-tree。
3. 之后所有用例都在这个页面完成，不要再离开。

## 页面可读文本（expected 只能引用这里的原文，不要臆造）
- 统计：「共 N 个节点」
- 空输入 / 纯空白：渲染按钮 disabled（数据里用 page_state: "disabled"）
- 一行都解析不出来：「无法解析树形结构，请检查文本格式」

## 解析规则（决定 expected 怎么写）
- **第一行是根节点**（根也算 1 个节点）。
- 之后的每一行必须带**分支符号**（├── / └──）或**缩进**（4 空格 / `│   `），才会成为节点；
  没有缩进的顶层行会被**丢弃**、不计入统计。
- 任何非空行都会生成节点（包括 `!!!`、`###`、`12345`），不会报错。
- 只有所有行都被忽略时，才会出现「无法解析树形结构，请检查文本格式」。
- 因此 expected 写「共 N 个节点」，N = 根 + 带缩进/分支的后续行数。"""

JAVA_TO_TS_PRE = """你正在控制浏览器，对「Java DTO 转 TypeScript」工具做一次参数化功能验证（站点 {portal}）。

## 访问路径（必须真实走一遍）
1. 打开 {portal}/ ，点击「开始使用工具箱」进入 /tools。
2. 点击「Java DTO 转 TypeScript」卡片，进入 /tools/java-to-ts。
3. 之后所有用例都在这个页面完成，不要再离开。

## 页面结构（定位依据）
- 左侧「Java 输入」是页面第 1 个 textarea（可编辑）。
- 右侧「TypeScript 输出」是页面第 2 个 textarea（readonly，只能读不能写）。
- 「转换为 TypeScript」按钮触发转换；转换成功/失败会弹出 toast。

## 页面可读文本（expected 只能引用输出里的原文片段）
- 接口声明：`export interface 类名 {`；字段：`id: number;`；默认值：`export const default类名`
- 未知类型：`// FIXME: 未知类型/自定义DTO`；规则：`export const rules: FormRules`
- 空输入（按钮不会禁用，点击后提示）：「请输入 Java 代码」"""

TREE_PRE = """你正在控制浏览器，对「树形结构可视化」工具做一次参数化功能验证（站点 {portal}）。

## 访问路径（必须真实走一遍）
1. 打开 {portal}/ ，点击「开始使用工具箱」进入 /tools。
2. 点击「树形结构可视化」卡片，进入 /tools/tree。
3. 之后所有用例都在这个页面完成，不要再离开。

## 页面结构（定位依据）
- 左侧「JSON 输入」是页面唯一的 textarea。
- 「渲染树形图」按钮触发解析与渲染；成功后在按钮右侧显示「共 N 个节点」。
- 非法 JSON 会在输入框下方显示红色错误文本「JSON 解析错误: ...」。
- 工具栏的齿轮按钮（提示「图配置」）可展开「标签字段 / 子级字段 / 布局方向 / 节点形状」。

## 页面可读文本（expected 只能引用这里的原文，不要臆造）
- 统计：「共 N 个节点」
- 空输入：渲染按钮 disabled（数据里用 page_state: "disabled"）
- 非法 JSON：「JSON 解析错误」"""


# ---------------------------------------------------------------------------
# Final-output constraints (结束输出提示词约束): the report contract appended last.
# ---------------------------------------------------------------------------

#: Output contract for AI-generated *data* (phase 2). Kept flat on purpose: the fields
#: must be machine-checkable by ``dd_01_data_driven.py`` without any LLM in the loop.
DATA_GEN_FINAL = """## 输出格式（只输出一个 JSON 数组，不要解释）
[
  {"id": "D1", "kind": "equivalence-valid", "input": "……", "expected": "共 5 个节点",
   "expect_all": ["id: number"], "nodes": 5, "note": "有效等价类：正常目录树"}
]

字段规则：
- `id`：唯一（D1、D2…）。
- `kind`：只能取 `valid`、`equivalence-*`、`boundary-*`、`invalid`。
- `input`：字符串，原样输入（保留换行）。
- `expected`：**页面上能直接读到的文本片段**（「共 N 个节点」/ 错误提示 / 输出片段），
  不要写「应该通过」这类判断。
- `expect_all`：可选，额外必须同时出现的片段数组。
- `nodes`：可选，仅当工具显示「共 N 个节点」时填整数。
  **必须与 input 中真实可解析的节点总数完全一致**：逐行数一遍，根节点也算 1 个，
  空行不算。数字写错会让整条数据被判 FAIL。
- `page_state`：可选，填 `"disabled"` 表示该用例期望主按钮处于禁用状态。
- `note`：可选，说明该条属于哪个等价类/边界。

分布要求：至少 3 条 `equivalence-*`、3 条 `boundary-*`、2 条 `invalid`；
总数 8-12 条；不得与参考示例的 `input` 重复。"""

DATA_GEN_TASK = """## 你的任务
为被测工具生成一组**机器可判定的功能测试数据**，覆盖：
1. 有效等价类（典型正常输入）；
2. 无效/异常等价类（非法输入、空输入）；
3. 边界值（最小、最大、深层嵌套、超长/大批量）。

要求：每条都能被脚本直接执行（有 input，且有页面可读到的 expected）；
不要写测试代码，只产出数据。"""


def build_data_prompt(
    task: str,
    portal: str,
    seeds: list[dict] | None = None,
    pre: str | None = None,
) -> str:
    """Assemble the data-generation prompt: PRE + TASK + SEEDS + FINAL."""
    seeds = seeds or []
    seed_block = ""
    if seeds:
        seed_block = "## 参考示例（不要原样重复这些 input）\n" + "\n".join(
            _case_block(c) for c in seeds
        )
    parts = [(pre or MATRIX_PRE).replace("{portal}", portal), task.strip()]
    if seed_block:
        parts.append(seed_block)
    parts.append(DATA_GEN_FINAL)
    return "\n\n".join(parts)

MATRIX_FINAL = """## 输出格式（必须严格遵守，便于脚本解析）
CASE C1 (valid): PASS — evidence: "共 5 个节点"
CASE C2 (boundary-empty): PASS — evidence: "渲染按钮 disabled，页面可用"
...
PATH: 门户 → 工具箱 → 工具页 → 输入 → 触发 → 读取结果（已逐行执行 {n} 次）
VERDICT: PASS ({n}/{n} cases passed)

最后一行的 VERDICT 必须与逐行结论一致；任一行 FAIL 则 VERDICT 为 FAIL。"""

# ---------------------------------------------------------------------------
# Assembly: PRE + TASK + DATA + FINAL.
# ---------------------------------------------------------------------------


def _case_block(case: dict) -> str:
    nodes = case.get("nodes")
    if nodes is None:
        node_hint = ""
    else:
        node_hint = f"；节点数参考：{nodes} 个节点"
    return (
        f"CASE {case['id']}（{case['kind']}）\n"
        f"输入（原样输入，保留换行）：\n```\n{case['input']}\n```\n"
        f"参考期望：{case['expect']}{node_hint}\n"
    )


DEFAULT_PATHS = [
    "首页 → 工具箱 → 工具页（仅门户入口，不直接跳转到子路由）",
    "空输入分支（按钮 disabled，不触发渲染/转换）",
    "有效输入分支（触发处理 → 读取结果）",
    "非法输入分支（容错，不崩溃）",
]


def coverage_block(cases: list[dict], paths: list[str] | None = None) -> str:
    """A concise, task-level coverage declaration (requirements-oriented)."""
    kinds: dict[str, int] = {}
    for c in cases:
        k = c.get("kind", "misc")
        kinds[k] = kinds.get(k, 0) + 1
    eq = sorted(set(k for k in kinds if "equivalence" in k))
    bd = sorted(set(k for k in kinds if "boundary" in k))
    ex = sorted(set(k for k in kinds if k in ("invalid", "boundary-empty") or "error" in k))
    path_coverage = paths or DEFAULT_PATHS
    return (
        "## 覆盖声明（需求覆盖式，不涉及代码覆盖率）\n"
        f"- 用例总数：{len(cases)} 组\n"
        f"- 类型分布：{', '.join(f'{k}×{v}' for k, v in sorted(kinds.items()))}\n"
        f"- 等价类（Equivalence Classes）：{eq or '—'}\n"
        f"- 边界值（Boundary Values）：{bd or '—'}\n"
        f"- 异常数据（Invalid/Exception）：{ex or '—'}\n"
        f"- 路径覆盖（Path Coverage，UI 层面）：\n"
        + "\n".join(f"  - {p}" for p in path_coverage)
    )


def build_matrix_prompt(
    task: str,
    portal: str,
    cases: list[dict] | None = None,
    pre: str | None = None,
    paths: list[str] | None = None,
) -> str:
    """Assemble the execution/judgement prompt: PRE + TASK + DATA + COVERAGE + FINAL."""
    cases = LIST_TREE_CASES if cases is None else cases
    blocks = "\n".join(_case_block(c) for c in cases)
    data = f"## 参数化数据表（共 {len(cases)} 行，逐行执行，不得跳过）\n{blocks}"
    return "\n\n".join(
        [
            (pre or MATRIX_PRE).replace("{portal}", portal),
            task.strip(),
            data,
            coverage_block(cases, paths),
            MATRIX_FINAL.format(n=len(cases)),
        ]
    )


SPECS = {
    "list-tree": {
        "title": "列表树可视化（参数化矩阵）",
        "pre": MATRIX_PRE,
        "cases": LIST_TREE_CASES,
        "paths": [
            "首页 → 工具箱 → 列表树（仅门户入口，不直接跳转到子路由）",
            "空输入分支（按钮 disabled，不触发渲染）",
            "有效输入分支（点击渲染 → 读取统计）",
            "非法输入分支（容错，不崩溃）",
            "状态稳定分支（重新渲染，统计必须刷新）",
        ],
    },
    "java-to-ts": {
        "title": "Java DTO 转 TypeScript（参数化矩阵）",
        "pre": JAVA_TO_TS_PRE,
        "cases": JAVA_TO_TS_CASES,
        "paths": [
            "首页 → 工具箱 → Java 转 TS（仅门户入口）",
            "空输入分支（不转换，提示后输出保持为空）",
            "正常转换分支（接口 + 默认值对象）",
            "校验规则分支（Element Plus rules 段）",
            "未知类型分支（FIXME 注释 + 警告提示）",
        ],
    },
    "tree": {
        "title": "树形结构可视化（参数化矩阵）",
        "pre": TREE_PRE,
        "cases": TREE_CASES,
        "paths": [
            "首页 → 工具箱 → 树形结构（仅门户入口）",
            "空输入分支（按钮 disabled，不渲染）",
            "正常解析渲染分支（统计「共 N 个节点」）",
            "非法 JSON 分支（错误提示，不崩溃）",
            "字段映射分支（图配置改标签/子级字段后渲染）",
        ],
    },
}
