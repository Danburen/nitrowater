/**
 * Tree-text → node parsing + flattening for the list-tree visualizer
 * (ported from wtools `ListTreeVisualizer.vue`). Pure functions, no framework dependency.
 */

export interface ListTreeNode {
  /** Stable identity for the D3 data join (names can repeat at the same depth). */
  id: number
  name: string
  children?: ListTreeNode[]
  collapsed: boolean
  depth: number
  isFile: boolean
  isComment: boolean
  /** Inline comment after ` # ` (empty when none). */
  comment: string
}

export interface FlatNode {
  node: ListTreeNode
  depth: number
  hasChildren: boolean
  isFile: boolean
  isComment: boolean
  inlineComment: string
}

/** Parse one indented tree line (`│   ├─ name`) into depth + content. */
function parseLine(line: string): { depth: number; content: string } | null {
  const trimmed = line.trimEnd()
  if (!trimmed) return null

  let depth = 0
  let pos = 0
  while (pos + 4 <= trimmed.length) {
    const chunk = trimmed.substring(pos, pos + 4)
    if (chunk === '│   ' || chunk === '    ') {
      depth++
      pos += 4
    } else {
      break
    }
  }

  const rest = trimmed.substring(pos)
  // Strip the branch marker (`├─`, `├──`, `└──`, …) — one or more dashes.
  const branchMatch = rest.match(/^[├└]─+\s*/)
  if (branchMatch) {
    depth++
    const content = rest.substring(branchMatch[0].length).trim()
    return content ? { depth, content } : null
  }

  const content = rest.trim()
  return content ? { depth, content } : null
}

/**
 * Whether a name has a file extension (e.g. file.ts → true).
 * Uses a regex to avoid false positives like `1.1 数据` or `2.3 模块`.
 */
function hasFileExtension(name: string): boolean {
  const dot = name.lastIndexOf('.')
  if (dot <= 0 || dot >= name.length - 1) return false
  return /^[a-zA-Z0-9]+$/.test(name.substring(dot + 1))
}

/** Split `data.db # database` into `{ name: 'data.db', comment: 'database' }`. */
function splitInlineComment(raw: string): { name: string; comment: string } {
  const index = raw.indexOf(' # ')
  if (index === -1) return { name: raw, comment: '' }
  return {
    name: raw.substring(0, index).trim(),
    comment: raw.substring(index + 3).trim(),
  }
}

function inferNodeType(node: ListTreeNode): void {
  if (node.isComment) return // comments are never files
  node.isFile = hasFileExtension(node.name)
  if (node.children && node.children.length > 0) {
    // Anything with children in the text is a folder
    node.isFile = false
    node.children.forEach((child) => inferNodeType(child))
  }
}

/** Parse indented tree text into a root node. Returns `null` for empty input. */
export function parseTreeText(text: string): ListTreeNode | null {
  const parsedItems = text
    .split('\n')
    .map((line) => parseLine(line))
    .filter((item): item is { depth: number; content: string } => item !== null)
  if (parsedItems.length === 0) return null

  let nextId = 0
  const root: ListTreeNode = {
    id: nextId++,
    name: parsedItems[0].content,
    collapsed: false,
    depth: 0,
    isFile: false,
    isComment: false,
    comment: '',
    children: [],
  }
  const stack: { node: ListTreeNode; depth: number }[] = [{ node: root, depth: 0 }]

  for (let i = 1; i < parsedItems.length; i++) {
    const { depth, content } = parsedItems[i]
    if (depth < 0) continue

    const isComment = content.startsWith('#')
    const { name, comment } = isComment ? { name: content, comment: '' } : splitInlineComment(content)
    const node: ListTreeNode = {
      id: nextId++,
      name,
      collapsed: false,
      depth,
      isFile: false,
      isComment,
      comment,
      children: [],
    }

    while (stack.length > 0 && stack[stack.length - 1].depth >= depth) stack.pop()
    if (stack.length > 0) {
      const parent = stack[stack.length - 1].node
      if (!parent.children) parent.children = []
      parent.children.push(node)
    }
    stack.push({ node, depth })
  }

  if (root.children?.length === 0) root.children = undefined
  inferNodeType(root)
  return root
}

export function countTotalNodes(node: ListTreeNode): number {
  let count = 1
  for (const child of node.children ?? []) count += countTotalNodes(child)
  return count
}

export function setAllCollapsed(node: ListTreeNode, collapsed: boolean): void {
  node.collapsed = collapsed
  for (const child of node.children ?? []) setAllCollapsed(child, collapsed)
}

/** Depth-first walk honouring collapse state — the rows the list should show. */
export function flattenTree(root: ListTreeNode): FlatNode[] {
  const result: FlatNode[] = []
  const walk = (node: ListTreeNode): void => {
    result.push({
      node,
      depth: node.depth,
      hasChildren: !!node.children?.length,
      isFile: node.isFile,
      isComment: node.isComment,
      inlineComment: node.comment,
    })
    if (node.children && !node.collapsed) node.children.forEach(walk)
  }
  walk(root)
  return result
}

export interface JsonTreeNode {
  name: string
  children?: JsonTreeNode[]
}

/** Export shape consumed by the tree visualizer tool. */
export function treeToJson(node: ListTreeNode): JsonTreeNode {
  const result: JsonTreeNode = { name: node.name }
  if (node.children && node.children.length > 0) {
    result.children = node.children.map((child) => treeToJson(child))
  }
  return result
}
