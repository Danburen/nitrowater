<script setup lang="ts">
/**
 * Online code runner — structure & look adapted from wtools (MIT) `src/components/CodeRunner.vue`.
 *
 * Runs Python entirely in the browser via Pyodide (WebAssembly): anonymous, nothing uploaded.
 * The runtime is cached in IndexedDB (cache-first fetch interceptor) and Python packages are
 * auto-loaded on demand. Server-side languages (Java / C / C++) will run on the backend; they
 * require login, surfaced by the inline hint beside the language picker.
 *
 * UI mirrors the wtools runner: two panels with headers (icon + title on the left, controls on the
 * right) and a dark console for output. Element Plus components are used on-demand; colors follow
 * the app theme through the `--el-*` -> project-token bridge in style.css.
 */
import {
  ChatLineSquare,
  CopyDocument,
  Delete,
  Monitor,
  Refresh,
  Select,
  VideoPlay,
} from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useAuth } from '../../../auth/useAuth'
import { toast } from '../../../shared/ui/toast'
import ToolShell from '../ToolShell.vue'
import CodeEditor from './CodeEditor.vue'
import { DEFAULT_LANGUAGE_ID, RUNNER_LANGUAGES } from './langCatalog'
import { ansiToHtml } from './lib/ansi'
import { flushMemoryCache, useContentCache, type UseContentCacheReturn } from './lib/contentCache'
import { ensureRuntime, isPyodideReady, runPythonCode } from './lib/pyodide/pyodideManager'

const { login } = useAuth()

/** Only languages that run online today — the picker never lists unavailable ones. */
const availableLanguages = RUNNER_LANGUAGES.filter((item) => item.available)
const languageId = ref(DEFAULT_LANGUAGE_ID)

// Per-language content cache (switching languages never overwrites another's draft).
const caches = new Map<string, UseContentCacheReturn>()
for (const item of RUNNER_LANGUAGES) {
  if (item.engine === 'local') {
    caches.set(item.id, useContentCache(`runner-code-${item.id}`, item.defaultCode))
  }
}
const activeCache = computed<UseContentCacheReturn>(() => caches.get(languageId.value) ?? caches.get('python')!)
const code = computed<string>({
  get: () => activeCache.value.content.value,
  set: (value) => {
    activeCache.value.content.value = value
  },
})

const stdin = ref('')
const output = ref('')
const status = ref('就绪')
const running = ref(false)

const outputHtml = computed(() => ansiToHtml(output.value))

// ──────────────────────────────────────────
//  Editor actions
// ──────────────────────────────────────────

function resetCode() {
  activeCache.value.clear()
}

function clearCode() {
  activeCache.value.content.value = ''
}

function saveCode() {
  activeCache.value.save()
  toast.success('已临时保存到浏览器中')
}

async function copyCode() {
  try {
    await navigator.clipboard.writeText(code.value)
    toast.success('代码已复制')
  } catch {
    toast.error('复制失败')
  }
}

// ──────────────────────────────────────────
//  Run
// ──────────────────────────────────────────

async function run() {
  if (running.value) return
  running.value = true
  output.value = ''
  status.value = '准备中...'
  try {
    if (!isPyodideReady()) await ensureRuntime((message) => (status.value = message))
    const result = await runPythonCode(code.value, {
      stdin: stdin.value,
      onMessage: (message) => (status.value = message),
    })
    output.value = result
  } catch (error) {
    output.value = `[运行错误] ${error instanceof Error ? error.message : String(error)}`
  } finally {
    running.value = false
    status.value = '就绪'
  }
}

function clearOutput() {
  output.value = ''
}

onMounted(() => window.addEventListener('beforeunload', flushMemoryCache))
onBeforeUnmount(() => window.removeEventListener('beforeunload', flushMemoryCache))
</script>

<template>
  <ToolShell
    fill
    title="在线代码运行器"
    desc="纯浏览器运行 Python（Pyodide / WASM），代码不上传。更多语言将在服务端运行，需登录。"
  >
    <div class="grid min-h-0 flex-1 gap-4 lg:grid-cols-2">
      <!-- Editor panel -->
      <section class="panel flex min-h-0 flex-col">
        <div class="mb-3 flex items-center gap-2">
          <span class="flex shrink-0 items-center gap-1.5 text-sm font-medium text-ink">
            <el-icon><Monitor /></el-icon>
            代码编辑器
          </span>
          <el-select
            v-model="languageId"
            class="!w-[92px] shrink-0"
            size="small"
            :disabled="running"
          >
            <el-option
              v-for="item in availableLanguages"
              :key="item.id"
              :label="item.label"
              :value="item.id"
            />
          </el-select>
          <span class="min-w-0 truncate text-xs text-faint">
            更多语言需
            <button
              type="button"
              class="text-accent hover:text-accent-strong"
              @click="login()"
            >登录</button>
            后在服务端运行
          </span>
          <el-tooltip
            content="重置为示例代码"
            placement="top"
          >
            <el-button
              class="ml-auto shrink-0"
              size="small"
              :icon="Refresh"
              circle
              :disabled="running"
              @click="resetCode"
            />
          </el-tooltip>
          <el-tooltip
            content="清空编辑区"
            placement="top"
          >
            <el-button
              class="shrink-0"
              size="small"
              :icon="Delete"
              circle
              :disabled="running"
              @click="clearCode"
            />
          </el-tooltip>
          <el-tooltip
            content="保存到浏览器"
            placement="top"
          >
            <el-button
              class="shrink-0"
              size="small"
              :icon="Select"
              circle
              @click="saveCode"
            />
          </el-tooltip>
          <el-tooltip
            content="复制代码"
            placement="top"
          >
            <el-button
              class="shrink-0"
              size="small"
              :icon="CopyDocument"
              circle
              @click="copyCode"
            />
          </el-tooltip>
        </div>

        <div class="min-h-0 flex-1 overflow-hidden rounded-md border border-line">
          <CodeEditor
            v-model="code"
            language="python"
          />
        </div>

        <details class="mt-3">
          <summary class="cursor-pointer select-none text-xs text-faint">
            标准输入（可选，供 input() 读取）
          </summary>
          <textarea
            v-model="stdin"
            class="mt-2 w-full resize-y rounded-md border border-line bg-soft p-2 font-mono text-xs text-ink outline-none focus:border-accent"
            rows="3"
            placeholder="运行时按需读取；此处内容会被一次读入。"
          />
        </details>
      </section>

      <!-- Output panel -->
      <section class="panel flex min-h-0 flex-col">
        <div class="mb-3 flex items-center justify-between gap-2">
          <span class="flex items-center gap-1.5 text-sm font-medium text-ink">
            <el-icon><ChatLineSquare /></el-icon>
            运行输出
          </span>
          <div class="flex items-center gap-2">
            <span
              v-if="running"
              class="text-xs text-faint"
            >{{ status }}</span>
            <el-button
              type="primary"
              size="small"
              :disabled="running"
              @click="run"
            >
              <el-icon class="mr-1">
                <VideoPlay />
              </el-icon>
              {{ running ? '运行中...' : '运行' }}
            </el-button>
            <el-button
              v-if="output"
              size="small"
              text
              @click="clearOutput"
            >
              清空
            </el-button>
          </div>
        </div>

        <!-- eslint-disable vue/no-v-html -- ansiToHtml() escapes text; only emits a fixed tag set -->
        <div class="flex min-h-0 flex-1 flex-col overflow-auto rounded-md bg-[#1e293b] p-3.5">
          <div
            v-if="!output"
            class="flex flex-1 items-center justify-center text-center text-[13px] text-[#64748b]"
          >
            {{ running ? status : '点击「运行」执行代码' }}
          </div>
          <pre
            v-else
            class="m-0 whitespace-pre-wrap break-all font-mono text-[13px] leading-[1.6] text-[#e2e8f0]"
            v-html="outputHtml"
          />
        </div>
        <!-- eslint-enable vue/no-v-html -->
      </section>
    </div>
  </ToolShell>
</template>
