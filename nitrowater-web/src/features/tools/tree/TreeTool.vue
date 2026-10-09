<script setup lang="ts">
/**
 * Tree visualizer — renders arbitrary JSON as an interactive ECharts tree.
 * Ported from wtools `TreeVisualizer.vue`; UI rewritten with Tailwind, ECharts loaded
 * on demand (L2 interaction-level `import()`) and themed via the live design tokens.
 */
import { CopyDocument, Delete, Download, Refresh, Setting, Upload } from '@element-plus/icons-vue'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { EChartsOption, EChartsType } from 'echarts'
import { toast } from '../../../shared/ui/toast'
import { useTheme } from '../../../shared/theme/useTheme'
import ToolShell from '../ToolShell.vue'
import { countNodes, parseTreeJson } from '../lib/tree/buildTree'
import type { TreeNode } from '../lib/tree/buildTree'
import { chartPalette } from '../lib/tree/chartPalette'
import type { ChartPalette } from '../lib/tree/chartPalette'

type Orientation = 'TB' | 'LR' | 'RL' | 'BT'
type EChartsModule = typeof import('echarts')

const { theme } = useTheme()

const labelField = ref('name')
const childrenField = ref('children')
const orientation = ref<Orientation>('LR')
const nodeShape = ref<'circle' | 'rect'>('circle')
const configOpen = ref(false)

const jsonInput = ref('')
const errorMsg = ref('')
const nodeCount = ref(0)
const fileInput = ref<HTMLInputElement>()

function pickFile(): void {
  fileInput.value?.click()
}

const chartRendered = ref(false)
const loadingChart = ref(false)
const chartEl = ref<HTMLDivElement>()
let chart: EChartsType | null = null
let resizeObserver: ResizeObserver | null = null
let echartsModule: EChartsModule | null = null
let echartsPromise: Promise<EChartsModule> | null = null

async function ensureEcharts(): Promise<EChartsModule> {
  if (echartsModule) return echartsModule
  loadingChart.value = true
  echartsPromise ??= import('echarts')
  try {
    echartsModule = await echartsPromise
    return echartsModule
  } catch (error) {
    echartsPromise = null
    throw error
  } finally {
    loadingChart.value = false
  }
}

function sampleJson(): string {
  return JSON.stringify({
    name: '实用前端工具',
    itemStyle: { color: '#5b73e0', borderColor: '#3d56c2', borderWidth: 2 },
    children: [
      {
        name: '核心模块',
        itemStyle: { color: '#48b884' },
        children: [
          {
            name: 'ZIP 压缩',
            value: '打包·进度·加密',
            itemStyle: { color: '#6bc0e0' },
            children: [
              { name: '多文件压缩', value: '拖拽打包', itemStyle: { color: '#a8d8ea' } },
              { name: '进度条', value: '实时进度', itemStyle: { color: '#a8d8ea' } },
              { name: '密码保护', value: '加密压缩', itemStyle: { color: '#a8d8ea' } },
            ],
          },
          {
            name: 'ZIP 解压',
            value: 'zip·rar·7z·tar',
            itemStyle: { color: '#f5a623' },
            children: [
              { name: 'ZIP 格式', value: '标准解压', itemStyle: { color: '#f9d58b' } },
              { name: 'RAR 格式', value: 'rar支持', itemStyle: { color: '#f9d58b' } },
              { name: '7z 格式', value: '7z支持', itemStyle: { color: '#f9d58b' } },
            ],
          },
          {
            name: 'Java → TS',
            value: '类型·泛型·警告',
            itemStyle: { color: '#a855f7' },
            children: [
              { name: '类型映射', value: 'String→string', itemStyle: { color: '#c084fc' } },
              { name: '泛型支持', value: 'List<T>→T[]', itemStyle: { color: '#c084fc' } },
              { name: '警告标记', value: 'FIXME 未知类型', itemStyle: { color: '#c084fc' } },
            ],
          },
        ],
      },
      {
        name: '基础架构',
        itemStyle: { color: '#f56c6c' },
        children: [
          { name: 'Vue 3 + TypeScript', value: '框架栈', itemStyle: { color: '#f89898' } },
          { name: 'Element Plus UI', value: '组件库', itemStyle: { color: '#f89898' } },
          { name: 'Vite 构建', value: '构建工具', itemStyle: { color: '#f89898' } },
        ],
      },
      {
        name: '新工具',
        itemStyle: { color: '#3ba272' },
        children: [
          { name: '树形结构可视化', value: '当前工具', itemStyle: { color: '#7ecf9e' } },
          { name: '更多工具...', value: '待添加', itemStyle: { color: '#7ecf9e' } },
        ],
      },
    ],
  }, null, 2)
}

// ─── Rendering ────────────────────────────────────────────────────
const ESCAPES: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }

function escapeHtml(value: string): string {
  return value.replace(/[&<>"]/g, (char) => ESCAPES[char] ?? char)
}

function buildTooltip(params: unknown, palette: ChartPalette): string {
  const data = (params as { data?: TreeNode }).data
  if (!data) return ''
  const title = `<div style="font-weight:700;font-size:13px;color:${palette.ink};margin-bottom:4px;">${escapeHtml(data.name)}</div>`
  let fields = ''
  for (const key of Object.keys(data)) {
    if (key === 'name' || key === 'children') continue
    const value = data[key]
    if (value === undefined || value === null || typeof value === 'object') continue
    fields += `<div style="display:flex;gap:8px;font-size:12px;line-height:1.8;"><span style="color:${palette.dim};min-width:50px;">${escapeHtml(key)}</span><span style="color:${palette.ink};">${escapeHtml(String(value))}</span></div>`
  }
  if (!fields) return title
  return `${title}<div style="margin-top:4px;padding-top:6px;border-top:1px solid ${palette.line};">${fields}</div>`
}

function buildOption(tree: TreeNode): EChartsOption {
  const palette = chartPalette()
  const isLR = orientation.value === 'LR' || orientation.value === 'RL'
  const initialDepth = nodeCount.value > 50 ? 2 : nodeCount.value > 20 ? 3 : 4

  return {
    backgroundColor: 'transparent',
    color: ['#5b73e0', '#48b884', '#f5a623', '#f56c6c', '#6bc0e0', '#3ba272', '#e8855c', '#a855f7'],
    tooltip: {
      trigger: 'item',
      triggerOn: 'mousemove',
      backgroundColor: palette.card,
      borderColor: palette.line,
      borderWidth: 1,
      borderRadius: 8,
      padding: [10, 14],
      textStyle: { fontSize: 12, color: palette.ink },
      formatter: (params: unknown) => buildTooltip(params, palette),
    },
    series: [
      {
        type: 'tree',
        data: [tree],
        top: 24,
        bottom: 24,
        left: isLR ? 48 : 24,
        right: isLR ? 80 : 24,
        layout: 'orthogonal',
        orient: orientation.value,
        roam: true,
        expandAndCollapse: true,
        initialTreeDepth: initialDepth,
        animationDuration: 600,
        animationEasing: 'cubicOut',
        animationEasingUpdate: 'cubicOut',
        lineStyle: { color: palette.line, width: 1.5, curveness: 0.55 },
        label: {
          position: isLR ? 'right' : 'top',
          offset: isLR ? [10, 0] : [0, 8],
          fontSize: 12,
          color: palette.ink,
          fontWeight: 500,
          formatter: (params: unknown) => String((params as { data?: TreeNode }).data?.name ?? ''),
        },
        leaves: {
          label: { position: isLR ? 'right' : 'bottom', offset: isLR ? [10, 0] : [0, 8] },
        },
        symbolSize: (_value: number, params: unknown) => {
          const depth = Number((params as { depth?: number }).depth ?? 0)
          return Math.max(7, 15 - depth * 1.8)
        },
        symbol: nodeShape.value === 'rect' ? 'roundRect' : 'circle',
        itemStyle: {
          borderColor: palette.card,
          borderWidth: 2,
          shadowBlur: 4,
          shadowColor: 'rgba(0,0,0,0.12)',
          shadowOffsetY: 2,
        },
        emphasis: {
          focus: 'descendant',
          lineStyle: { color: palette.accent, width: 2 },
        },
      },
    ],
  }
}

async function renderChart(): Promise<void> {
  const { tree, error } = parseTreeJson(jsonInput.value, labelField.value, childrenField.value)
  if (error) {
    errorMsg.value = error
    return
  }
  if (!tree) {
    errorMsg.value = '请输入 JSON 数据'
    return
  }

  errorMsg.value = ''
  nodeCount.value = countNodes(tree)

  let echarts: EChartsModule
  try {
    echarts = await ensureEcharts()
  } catch (error) {
    errorMsg.value = `图表库加载失败: ${(error as Error).message}`
    toast.error('图表库加载失败')
    return
  }

  await nextTick()
  if (!chartEl.value) return
  chart ??= echarts.init(chartEl.value)
  chart.setOption(buildOption(tree), true)
  chartRendered.value = true
}

function onConfigChange(): void {
  if (chartRendered.value && jsonInput.value.trim()) void renderChart()
}

// ─── Toolbar actions ──────────────────────────────────────────────
function loadSample(): void {
  jsonInput.value = sampleJson()
  void renderChart()
}

function clearAll(): void {
  jsonInput.value = ''
  errorMsg.value = ''
  nodeCount.value = 0
  chartRendered.value = false
  chart?.clear()
}

function onPickFile(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    const text = String(reader.result ?? '')
    try {
      JSON.parse(text)
      jsonInput.value = text
      void renderChart()
      toast.success('JSON 文件已加载')
    } catch {
      toast.error('文件内容不是有效的 JSON')
    }
  }
  reader.readAsText(file)
}

async function copyJson(): Promise<void> {
  try {
    await navigator.clipboard.writeText(jsonInput.value)
    toast.success('JSON 已复制')
  } catch {
    toast.error('复制失败')
  }
}

function exportImage(): void {
  if (!chart) return
  const url = chart.getDataURL({
    type: 'png',
    pixelRatio: 2,
    backgroundColor: chartPalette().card,
    excludeComponents: ['tooltip'],
  })
  const link = document.createElement('a')
  link.href = url
  link.download = 'tree-visualizer.png'
  document.body.appendChild(link)
  link.click()
  link.remove()
  toast.success('图片已导出')
}

// ─── Lifecycle ────────────────────────────────────────────────────
watch(theme, () => {
  if (chartRendered.value) void renderChart()
})

onMounted(() => {
  const stored = sessionStorage.getItem('list-tree-export')
  if (stored) {
    sessionStorage.removeItem('list-tree-export')
    try {
      jsonInput.value = JSON.stringify(JSON.parse(stored), null, 2)
    } catch {
      jsonInput.value = sampleJson()
    }
  } else {
    jsonInput.value = sampleJson()
  }

  void renderChart()

  if (chartEl.value) {
    resizeObserver = new ResizeObserver(() => chart?.resize())
    resizeObserver.observe(chartEl.value)
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<template>
  <ToolShell
    title="树形结构可视化"
    desc="将 JSON 数据渲染为 ECharts 可交互树形图；支持字段映射、4 种布局方向与节点形状，可导出 PNG。"
  >
    <div class="grid gap-4 lg:h-full lg:grid-cols-[380px_1fr]">
      <!-- Input panel -->
      <section class="panel flex flex-col gap-3">
        <div class="flex items-center justify-between gap-2">
          <h3 class="text-sm font-semibold text-ink">
            JSON 输入
          </h3>
          <div class="flex flex-wrap items-center gap-1.5">
            <input
              ref="fileInput"
              type="file"
              accept=".json,application/json"
              class="hidden"
              @change="onPickFile"
            >
            <el-tooltip
              content="上传 .json 文件"
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
              content="复制 JSON"
              placement="top"
            >
              <el-button
                size="small"
                :icon="CopyDocument"
                circle
                :disabled="!jsonInput"
                @click="copyJson"
              />
            </el-tooltip>
            <el-tooltip
              content="图配置"
              placement="top"
            >
              <el-button
                size="small"
                :icon="Setting"
                circle
                :type="configOpen ? 'primary' : ''"
                @click="configOpen = !configOpen"
              />
            </el-tooltip>
          </div>
        </div>

        <!-- Config block -->
        <div
          v-if="configOpen"
          class="grid gap-2 rounded-[10px] border border-line bg-soft p-3"
        >
          <div class="grid grid-cols-2 gap-2">
            <label class="flex flex-col gap-1 text-[12px] text-dim">
              标签字段
              <input
                v-model="labelField"
                class="input"
                placeholder="name"
                @change="onConfigChange"
              >
            </label>
            <label class="flex flex-col gap-1 text-[12px] text-dim">
              子级字段
              <input
                v-model="childrenField"
                class="input"
                placeholder="children"
                @change="onConfigChange"
              >
            </label>
          </div>
          <div class="grid grid-cols-2 gap-2">
            <label class="flex flex-col gap-1 text-[12px] text-dim">
              布局方向
              <select
                v-model="orientation"
                class="select"
                @change="onConfigChange"
              >
                <option value="LR">
                  ➡ 从左到右
                </option>
                <option value="TB">
                  ⬇ 从上到下
                </option>
                <option value="RL">
                  ⬅ 从右到左
                </option>
                <option value="BT">
                  ⬆ 从下到上
                </option>
              </select>
            </label>
            <label class="flex flex-col gap-1 text-[12px] text-dim">
              节点形状
              <select
                v-model="nodeShape"
                class="select"
                @change="onConfigChange"
              >
                <option value="circle">
                  ● 圆形
                </option>
                <option value="rect">
                  ▬ 矩形
                </option>
              </select>
            </label>
          </div>
        </div>

        <textarea
          v-model="jsonInput"
          class="textarea min-h-[300px] flex-1"
          placeholder="在此粘贴 JSON 数据..."
        />

        <p
          v-if="errorMsg"
          class="text-[13px] text-danger"
        >
          {{ errorMsg }}
        </p>

        <div class="flex items-center gap-3">
          <el-button
            type="primary"
            round
            :disabled="!jsonInput.trim() || loadingChart"
            @click="renderChart"
          >
            {{ loadingChart ? '加载图表库…' : '渲染树形图' }}
          </el-button>
          <span
            v-if="nodeCount > 0"
            class="ml-auto text-[13px] text-dim"
          >共 {{ nodeCount }} 个节点</span>
        </div>
      </section>

      <!-- Chart panel -->
      <section class="relative flex min-h-0 flex-col overflow-hidden rounded-[14px] border border-line bg-card">
        <div
          v-if="chartRendered"
          class="flex shrink-0 items-center justify-end border-b border-line px-3 py-2"
        >
          <el-button
            size="small"
            :icon="Download"
            @click="exportImage"
          >
            导出图片
          </el-button>
        </div>

        <div
          ref="chartEl"
          class="min-h-[420px] w-full flex-1 bg-card"
        />

        <div
          v-if="loadingChart"
          class="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-card/90"
        >
          <span class="size-7 animate-spin rounded-full border-[3px] border-line border-t-accent" />
          <span class="text-sm font-medium text-ink">正在加载图表库…</span>
        </div>
        <div
          v-else-if="!chartRendered"
          class="pointer-events-none absolute inset-0 flex items-center justify-center p-6 text-center"
        >
          <span class="text-[13px] text-faint">输入 JSON 或上传文件后点击「渲染树形图」</span>
        </div>
      </section>
    </div>
  </ToolShell>
</template>
