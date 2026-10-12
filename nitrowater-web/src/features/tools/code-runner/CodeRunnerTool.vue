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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAuth } from '../../../auth/useAuth'
import { apiFetch } from '../../../auth/bff'
import { toast } from '../../../shared/ui/toast'
import ToolShell from '../ToolShell.vue'
import CodeEditor from './CodeEditor.vue'
import {
  DEFAULT_LANGUAGE_ID,
  RUNNER_LANGUAGES,
  isLocalLanguage,
  isRunnerLanguage,
  type RunnerLanguageId,
} from './langCatalog'
import { ansiToHtml } from './lib/ansi'
import { flushMemoryCache, useContentCache, type UseContentCacheReturn } from './lib/contentCache'
import { ensureLanguage, isLanguageReady, runLocalCode } from './lib/runtimes/runtimeManager'

const { login, session } = useAuth()

/** Picker entries: anonymous callers only get browser-local (WASM) languages. */
const availableLanguages = computed(() =>
  RUNNER_LANGUAGES.filter(
    (item) => item.available && (session.value?.authenticated || item.engine === 'local'),
  ),
)
const languageId = ref(DEFAULT_LANGUAGE_ID)

// Fall back to the default when the current language is no longer offered (e.g. after logout).
watch(languageId, (value) => {
  if (!availableLanguages.value.some((item) => item.id === value)) {
    languageId.value = DEFAULT_LANGUAGE_ID
  }
})

// Per-language content cache (switching languages never overwrites another's draft).
const caches = new Map<string, UseContentCacheReturn>()
for (const item of RUNNER_LANGUAGES) {
  caches.set(item.id, useContentCache(`runner-code-${item.id}`, item.defaultCode))
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

/** Language id for editor syntax highlighting (all selectable languages have a mode). */
const editorLanguage = computed<RunnerLanguageId | undefined>(() =>
  isRunnerLanguage(languageId.value) ? languageId.value : undefined,
)

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

/** Response shape of the backend sandbox run endpoint. */
interface ServerRunResponse {
  stdout: string | null
  stderr: string | null
  status: string | null
  exitCode: number | null
  wallTimeMs: number | null
  memoryBytes: number | null
  message: string | null
}

/** Human-readable hint for a non-OK sandbox status. */
function serverStatusHint(status: string | null): string {
  switch (status) {
    case 'CE':
      return '编译失败'
    case 'TO':
      return '运行超时'
    case 'OL':
    case 'EL':
      return '输出超限'
    case 'RE':
    case 'SG':
      return '运行错误'
    case 'SE':
      return '执行服务异常'
    default:
      return ''
  }
}

/** Runs code on the backend sandbox and formats its output for the console. */
async function runOnServer(language: string, source: string, input: string): Promise<string> {
  if (!session.value?.authenticated) {
    return '[需登录] 服务端沙箱运行需要登录。'
  }
  const response = await apiFetch('/api/sandbox/run', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ language, code: source, stdin: input }),
  })
  if (response.status === 429) {
    return '[请求过于频繁] 请稍后再试。'
  }
  if (!response.ok) {
    const detail = await response.text()
    return `[服务器错误 ${response.status}] ${detail}`
  }
  const data = (await response.json()) as ServerRunResponse
  const text = `${data.stdout ?? ''}${data.stderr ?? ''}`
  const hint = serverStatusHint(data.status)
  if (hint) {
    return `${text}${text && !text.endsWith('\n') ? '\n' : ''}[${hint}]`
  }
  return text || '[无输出]'
}

async function run() {
  if (running.value) return
  const language = languageId.value
  running.value = true
  output.value = ''
  status.value = '准备中...'
  try {
    if (session.value?.authenticated) {
      // Signed in: run everything on the backend sandbox (uniform, no WASM download).
      status.value = '运行中...'
      output.value = await runOnServer(language, code.value, stdin.value)
    } else if (isLocalLanguage(language)) {
      if (!isLanguageReady(language)) {
        await ensureLanguage(language, (message) => (status.value = message))
      }
      status.value = '运行中...'
      output.value = await runLocalCode(language, code.value, { stdin: stdin.value })
    } else {
      output.value = '[需登录] 该语言在服务端沙箱运行，请先登录。'
    }
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
    desc="匿名时 Python / JavaScript / TypeScript / Lua 于浏览器本地运行（代码不上传）；登录后全部语言改为在服务端沙箱运行。Java / C / C++ 需登录。"
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
            class="!w-[118px] shrink-0"
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
          <span
            v-if="!session?.authenticated"
            class="min-w-0 truncate text-xs text-faint"
          >
            Java / C / C++ 需
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
            :language="editorLanguage"
          />
        </div>

        <details class="mt-3">
          <summary class="cursor-pointer select-none text-xs text-faint">
            标准输入（可选，供 input() / readline() 读取）
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
