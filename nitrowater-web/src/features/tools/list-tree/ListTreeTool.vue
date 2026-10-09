<script setup lang="ts">
/**
 * List-tree visualizer — renders indented tree text as a collapsible D3 list.
 * Ported from wtools `ListTreeVisualizer.vue`; UI rewritten with Tailwind, D3 loaded
 * on demand (L2 interaction-level `import()`) and row styles driven by design tokens.
 */
import { CopyDocument, Delete, Download, Expand, Fold, Refresh, Share, Upload } from '@element-plus/icons-vue'
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { saveAs } from 'file-saver'
import { useRouter } from 'vue-router'
import { toast } from '../../../shared/ui/toast'
import ToolShell from '../ToolShell.vue'
import { getFileIconSVG } from '../lib/fileIcons'
import {
  countTotalNodes,
  flattenTree,
  parseTreeText,
  setAllCollapsed,
  treeToJson,
} from '../lib/tree/listTree'
import type { FlatNode, ListTreeNode } from '../lib/tree/listTree'
import sampleText from '../lib/samples/list-chart.txt?raw'

type D3Module = typeof import('d3')

const router = useRouter()

const rawText = ref('')
const errorMsg = ref('')
const nodeCount = ref(0)
const fileInput = ref<HTMLInputElement>()

function pickFile(): void {
  fileInput.value?.click()
}
const totalNodeCount = ref(0)
const rendered = ref(false)
const h2cLoading = ref(false)
const listEl = ref<HTMLDivElement>()

let treeRoot: ListTreeNode | null = null
let d3Module: D3Module | null = null
let d3Promise: Promise<D3Module> | null = null

async function ensureD3(): Promise<D3Module> {
  if (d3Module) return d3Module
  d3Promise ??= import('d3')
  try {
    d3Module = await d3Promise
    return d3Module
  } catch (error) {
    d3Promise = null
    throw error
  }
}

// ─── SVG templates (theme-aware via CSS variables) ────────────────
const TOGGLE_SVG = '<svg width="10" height="10" viewBox="0 0 10 10"><path d="M3.5 1.5 L7.5 5 L3.5 8.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>'
const FOLDER_CLOSED_SVG = '<svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M2 3 L8 3 L10 5 L14 5 L14 13 L2 13 Z" fill="var(--color-accent)" fill-opacity="0.16" stroke="var(--color-accent)" stroke-width="1.2"/><path d="M2 3 L8 3 L10 5 L14 5 L14 13 L2 13 Z" fill="none" stroke="var(--color-accent)" stroke-width="1.2" opacity="0.5"/></svg>'
const FOLDER_OPEN_SVG = '<svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M2 3 L8 3 L10 5 L14 5 L14 13 L2 13 Z" fill="var(--color-accent)" fill-opacity="0.16" stroke="var(--color-accent)" stroke-width="1.2" opacity="0.85"/><path d="M2 3 L8 3 L10 5 L14 5 L14 13 L2 13 Z" fill="none" stroke="var(--color-accent)" stroke-width="1.2"/></svg>'

// ─── D3 rendering ─────────────────────────────────────────────────
const INDENT_STEP = 24

function updateList(root: ListTreeNode): void {
  const d3 = d3Module
  const container = listEl.value
  if (!d3 || !container) return

  const flat = flattenTree(root)
  nodeCount.value = flat.length

  const rows = d3.select(container)
    .selectAll<HTMLDivElement, FlatNode>('.tree-row')
    .data(flat, (d) => d.node.id)

  rows.exit()
    .transition()
    .duration(180)
    .style('opacity', 0)
    .style('height', 0)
    .remove()

  const enter = rows.enter()
    .append('div')
    .attr('class', 'tree-row')
    .style('opacity', 0)
    .style('height', 0)

  enter.each(function () {
    const row = d3.select(this)
    row.append('span').attr('class', 'toggle-btn').html(TOGGLE_SVG)
    row.append('span').attr('class', 'node-icon')
    row.append('span').attr('class', 'row-label')
    row.append('span').attr('class', 'row-comment')
  })

  const merged = enter.merge(rows)

  merged.style('padding-left', (d) => `${6 + d.depth * INDENT_STEP}px`)
  merged.classed('comment-row', (d) => d.isComment)

  merged.select('.toggle-btn')
    .style('display', (d) => (d.isComment ? 'none' : d.hasChildren ? 'flex' : 'none'))
    .classed('expanded', (d) => !d.isComment && d.hasChildren && !d.node.collapsed)

  merged.select('.node-icon')
    .html((d) => {
      if (d.isComment) return ''
      if (d.isFile) return getFileIconSVG(d.node.name)
      return d.node.collapsed ? FOLDER_CLOSED_SVG : FOLDER_OPEN_SVG
    })
    .style('display', (d) => (d.isComment ? 'none' : 'flex'))

  merged.select('.row-label')
    .text((d) => d.node.name)
    .classed('comment-label', (d) => d.isComment)

  merged.select('.row-comment')
    .text((d) => (d.inlineComment ? '# ' + d.inlineComment : ''))
    .style('display', (d) => (d.inlineComment ? '' : 'none'))

  merged.on('click', function (event: MouseEvent, d: FlatNode) {
    if (d.isComment) return
    if ((event.target as HTMLElement).closest('.toggle-btn') && d.hasChildren) {
      d.node.collapsed = !d.node.collapsed
      updateList(root)
    }
  })

  if (rendered.value) {
    enter.transition().duration(200).style('opacity', 1).style('height', '')
  } else {
    enter.style('opacity', 1).style('height', '')
  }
  rendered.value = true
}

// ─── Controller ───────────────────────────────────────────────────
async function renderList(): Promise<void> {
  errorMsg.value = ''
  if (!rawText.value.trim()) {
    errorMsg.value = '请输入树形文本数据'
    return
  }
  try {
    const tree = parseTreeText(rawText.value)
    if (!tree) {
      errorMsg.value = '无法解析树形结构，请检查文本格式'
      return
    }
    treeRoot = tree
    totalNodeCount.value = countTotalNodes(tree)
    await ensureD3()
    await nextTick()
    updateList(tree)
  } catch (error) {
    errorMsg.value = `解析错误: ${(error as Error).message}`
  }
}

function loadSample(): void {
  rawText.value = sampleText
  errorMsg.value = ''
  void renderList()
}

function expandAll(): void {
  if (!treeRoot) return
  setAllCollapsed(treeRoot, false)
  updateList(treeRoot)
}

function collapseAll(): void {
  if (!treeRoot) return
  setAllCollapsed(treeRoot, true)
  updateList(treeRoot)
}

function clearAll(): void {
  rawText.value = ''
  errorMsg.value = ''
  nodeCount.value = 0
  totalNodeCount.value = 0
  rendered.value = false
  treeRoot = null
  if (d3Module && listEl.value) d3Module.select(listEl.value).selectAll('*').remove()
}

async function copyText(): Promise<void> {
  try {
    await navigator.clipboard.writeText(rawText.value)
    toast.success('内容已复制')
  } catch {
    toast.error('复制失败')
  }
}

function onPickFile(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    const text = String(reader.result ?? '')
    if (text.trim()) {
      rawText.value = text
      void renderList()
      toast.success('文件已加载')
    } else {
      toast.error('文件内容为空')
    }
  }
  reader.readAsText(file)
}

// ─── Export ───────────────────────────────────────────────────────
function exportJson(): void {
  if (!treeRoot) return
  const blob = new Blob([JSON.stringify(treeToJson(treeRoot), null, 2)], { type: 'application/json' })
  saveAs(blob, 'tree-data.json')
  toast.success('JSON 已导出')
}

function openInTreeTool(): void {
  if (!treeRoot) return
  sessionStorage.setItem('list-tree-export', JSON.stringify(treeToJson(treeRoot)))
  void router.push('/tools/tree')
  toast.success('数据已发送至树形结构可视化工具')
}

async function exportImage(): Promise<void> {
  const container = listEl.value
  if (!treeRoot || !container) return

  h2cLoading.value = true

  // Expand everything so the exported image shows the full tree.
  const prevCollapsed = new Map<ListTreeNode, boolean>()
  const saveCollapsed = (node: ListTreeNode): void => {
    prevCollapsed.set(node, node.collapsed)
    node.collapsed = false
    node.children?.forEach(saveCollapsed)
  }
  saveCollapsed(treeRoot)
  updateList(treeRoot)
  // Let the enter transition settle before snapshotting.
  await new Promise((resolve) => setTimeout(resolve, 220))

  const origOverflow = container.style.overflow
  const origHeight = container.style.height
  container.style.overflow = 'visible'
  container.style.height = 'auto'

  try {
    const { default: html2canvas } = await import('html2canvas')
    const card = getComputedStyle(document.documentElement).getPropertyValue('--color-card').trim()
    const canvas = await html2canvas(container, {
      backgroundColor: card || '#ffffff',
      scale: 2,
      useCORS: true,
      logging: false,
    })
    canvas.toBlob((blob) => {
      if (blob) {
        saveAs(blob, 'tree-list.png')
        toast.success('图片已导出')
      }
    })
  } catch (error) {
    toast.error('图片导出失败: ' + (error instanceof Error ? error.message : String(error)))
  } finally {
    container.style.overflow = origOverflow
    container.style.height = origHeight
    prevCollapsed.forEach((collapsed, node) => { node.collapsed = collapsed })
    updateList(treeRoot)
    h2cLoading.value = false
  }
}

// ─── Lifecycle ────────────────────────────────────────────────────
onMounted(() => {
  loadSample()
})

onBeforeUnmount(() => {
  if (d3Module && listEl.value) d3Module.select(listEl.value).selectAll('*').remove()
})
</script>

<template>
  <ToolShell
    title="列表树可视化"
    desc="将 ├─ └─ │ 缩进树文本渲染为可折叠的 D3 列表，自动识别文件图标与内联注释，可导出 JSON / PNG。"
  >
    <div class="grid gap-4 lg:h-full lg:grid-cols-[420px_1fr]">
      <!-- Input panel -->
      <section class="panel flex flex-col gap-3">
        <div class="flex items-center justify-between gap-2">
          <h3 class="text-sm font-semibold text-ink">
            树形文本输入
          </h3>
          <div class="flex flex-wrap items-center gap-1.5">
            <input
              ref="fileInput"
              type="file"
              accept=".txt,.md,text/plain"
              class="hidden"
              @change="onPickFile"
            >
            <el-tooltip
              content="上传 .txt 文件"
              placement="top"
            >
              <el-button
                size="small"
                :icon="Upload"
                circle
                @click="pickFile"
              />
            </el-tooltip>
            <el-tooltip
              content="加载示例数据"
              placement="top"
            >
              <el-button
                size="small"
                :icon="Refresh"
                circle
                @click="loadSample"
              />
            </el-tooltip>
            <el-tooltip
              content="清空输入"
              placement="top"
            >
              <el-button
                size="small"
                :icon="Delete"
                circle
                @click="clearAll"
              />
            </el-tooltip>
            <el-tooltip
              content="复制内容"
              placement="top"
            >
              <el-button
                size="small"
                :icon="CopyDocument"
                circle
                :disabled="!rawText"
                @click="copyText"
              />
            </el-tooltip>
          </div>
        </div>

        <textarea
          v-model="rawText"
          class="textarea min-h-[340px] flex-1"
          placeholder="在此粘贴树形文本，例如：&#10;project/&#10;├── src/&#10;│   ├── main.py&#10;│   └── utils.py&#10;└── README.md"
        />

        <p
          v-if="errorMsg"
          class="text-[13px] text-danger"
        >
          {{ errorMsg }}
        </p>

        <div class="flex flex-wrap items-center gap-3">
          <el-button
            type="primary"
            round
            :disabled="!rawText.trim()"
            @click="renderList"
          >
            渲染树列表
          </el-button>
          <span
            v-if="nodeCount > 0 && !errorMsg"
            class="ml-auto text-[13px] text-dim"
          >共 {{ totalNodeCount }} 个节点</span>
        </div>

        <div
          v-if="nodeCount > 0 && !errorMsg"
          class="flex flex-wrap gap-2 border-t border-line pt-3"
        >
          <el-button
            size="small"
            :icon="Download"
            @click="exportJson"
          >
            导出 JSON
          </el-button>
          <el-button
            size="small"
            :icon="Share"
            @click="openInTreeTool"
          >
            在树形结构可视化中打开
          </el-button>
        </div>
      </section>

      <!-- List panel -->
      <section class="relative flex min-h-0 flex-col overflow-hidden rounded-[14px] border border-line bg-card">
        <div
          v-if="rendered"
          class="flex shrink-0 flex-wrap items-center justify-end gap-2 border-b border-line px-3 py-2"
        >
          <div class="flex flex-wrap gap-1.5">
            <el-button
              size="small"
              :icon="Download"
              :disabled="h2cLoading"
              @click="exportImage"
            >
              导出图片
            </el-button>
            <el-button
              size="small"
              :icon="Expand"
              @click="expandAll"
            >
              展开全部
            </el-button>
            <el-button
              size="small"
              :icon="Fold"
              @click="collapseAll"
            >
              全部折叠
            </el-button>
          </div>
        </div>

        <div
          v-show="rendered"
          ref="listEl"
          class="list-container min-h-[420px] max-h-[72vh] flex-1 overflow-auto p-2 lg:min-h-0 lg:max-h-none"
        />

        <div
          v-if="!rendered"
          class="flex flex-1 items-center justify-center p-16 text-center text-[13px] text-faint"
        >
          输入树形文本后点击「渲染树列表」
        </div>

        <div
          v-if="h2cLoading"
          class="absolute inset-0 z-20 flex flex-col items-center justify-center gap-3 bg-card/90"
        >
          <span class="size-7 animate-spin rounded-full border-[3px] border-line border-t-accent" />
          <span class="text-sm font-medium text-ink">正在生成图片…</span>
        </div>
      </section>
    </div>
  </ToolShell>
</template>

<style scoped>
/*
 * D3 injects `.tree-row` elements at runtime, so they need `:deep()`.
 * Colours come from design tokens so the list follows the active theme.
 */
.list-container :deep(.tree-row) {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 2px 6px;
  border-radius: 5px;
  user-select: none;
  overflow: hidden;
  height: 28px;
}

.list-container :deep(.tree-row:hover) {
  background: var(--color-soft);
}

.list-container :deep(.toggle-btn) {
  flex-shrink: 0;
  width: 18px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--color-dim);
  border-radius: 4px;
  transition: background 0.15s, color 0.15s;
  margin-right: 2px;
}

.list-container :deep(.toggle-btn svg) {
  transition: transform 0.2s ease;
}

.list-container :deep(.toggle-btn:hover) {
  background: var(--color-soft);
  color: var(--color-accent);
}

.list-container :deep(.toggle-btn.expanded svg) {
  transform: rotate(90deg);
}

.list-container :deep(.node-icon) {
  flex-shrink: 0;
  width: 20px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 2px;
}

.list-container :deep(.row-label) {
  font-size: 13px;
  color: var(--color-ink);
  line-height: 1.5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}

.list-container :deep(.row-comment) {
  margin-left: auto;
  font-style: italic;
  font-size: 12px;
  color: var(--color-faint);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 0;
  padding-left: 8px;
}

.list-container :deep(.comment-row) {
  opacity: 0.75;
  pointer-events: none;
}

.list-container :deep(.comment-row .row-label) {
  margin-left: auto;
  text-align: right;
  font-style: italic;
  font-size: 12px;
  color: var(--color-faint);
}
</style>
