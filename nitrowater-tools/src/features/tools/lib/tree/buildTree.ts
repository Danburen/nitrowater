/**
 * JSON → ECharts tree conversion (ported from wtools `TreeVisualizer.vue`).
 * Pure functions: no framework / charting dependency, safe to unit test.
 */

export interface TreeNode {
  name: string
  value?: string | number
  children?: TreeNode[]
  itemStyle?: Record<string, unknown>
  label?: Record<string, unknown>
  [key: string]: unknown
}

function pickLabel(node: Record<string, unknown>, labelKey: string): string {
  const explicit = node[labelKey]
  if (explicit !== undefined) return String(explicit)
  return String(node.name ?? node.id ?? 'node')
}

/** Convert arbitrary nested JSON into an ECharts-compatible tree node. */
export function buildEChartsTree(data: unknown, labelKey: string, childKey: string): TreeNode | null {
  if (data === null || data === undefined) return null

  // Primitive → wrap it
  if (typeof data !== 'object') return { name: String(data) }

  // Array → virtual root
  if (Array.isArray(data)) {
    const children = data
      .map((item) => buildEChartsTree(item, labelKey, childKey))
      .filter((node): node is TreeNode => node !== null)
    if (children.length === 0) return { name: '(empty array)' }
    return { name: 'root', children }
  }

  const raw = data as Record<string, unknown>
  const node: TreeNode = { name: pickLabel(raw, labelKey) }

  if (raw.value !== undefined) node.value = raw.value as string | number
  if (raw.itemStyle !== undefined && typeof raw.itemStyle === 'object') {
    node.itemStyle = raw.itemStyle as Record<string, unknown>
  }
  if (raw.label !== undefined && typeof raw.label === 'object') {
    node.label = raw.label as Record<string, unknown>
  }

  // Copy remaining primitive fields so the tooltip can show them
  for (const key of Object.keys(raw)) {
    if (
      key !== labelKey &&
      key !== childKey &&
      key !== 'value' &&
      key !== 'itemStyle' &&
      key !== 'label' &&
      typeof raw[key] !== 'object'
    ) {
      node[key] = raw[key]
    }
  }

  const rawChildren = raw[childKey]
  if (Array.isArray(rawChildren) && rawChildren.length > 0) {
    const mapped = rawChildren
      .map((child) => buildEChartsTree(child, labelKey, childKey))
      .filter((nodeOrNull): nodeOrNull is TreeNode => nodeOrNull !== null)
    if (mapped.length > 0) node.children = mapped
  }

  return node
}

export function countNodes(node: TreeNode | null): number {
  if (!node) return 0
  let count = 1
  for (const child of node.children ?? []) count += countNodes(child)
  return count
}

export interface ParseResult {
  tree: TreeNode | null
  /** User-facing message; `null` when the input is empty (not an error). */
  error: string | null
}

/** Parse raw textarea content into a renderable tree without throwing. */
export function parseTreeJson(text: string, labelKey: string, childrenKey: string): ParseResult {
  const trimmed = text.trim()
  if (!trimmed) return { tree: null, error: null }

  let data: unknown
  try {
    data = JSON.parse(trimmed)
  } catch (error) {
    return { tree: null, error: `JSON 解析错误: ${(error as Error).message}` }
  }

  const tree = buildEChartsTree(data, labelKey, childrenKey)
  if (!tree) return { tree: null, error: '无法从 JSON 构建树形结构' }
  return { tree, error: null }
}
