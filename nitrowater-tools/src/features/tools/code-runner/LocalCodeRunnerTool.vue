<script setup lang="ts">
/**
 * Browser-local code runner — the login-free counterpart of the main site's cloud runner.
 *
 * Code and stdin never leave the browser: Python runs on Pyodide, JavaScript on the native
 * engine, TypeScript is transpiled in-page, and Lua on the wasmoon WebAssembly VM. There is no
 * BFF, no API call and no account state anywhere in this component or its runtimes.
 *
 * UI mirrors the original wtools-inspired runner: two panels with headers (icon + title on the
 * left, controls on the right) and a dark console for output. Element Plus is used on-demand;
 * colors follow the tools-site tokens through the `--el-*` bridge in style.css.
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
import { toast } from '../../../shared/ui/toast'
import PageShell from '../../../shared/ui/PageShell.vue'
import TextArea from '../../../shared/ui/TextArea.vue'
import CodeEditor from './CodeEditor.vue'
import { DEFAULT_LANGUAGE_ID, RUNNER_LANGUAGES, type RunnerLanguageId } from './langCatalog'
import { ansiToHtml } from './lib/ansi'
import { flushMemoryCache, useContentCache, type UseContentCacheReturn } from './lib/contentCache'
import type { RuntimeProgress, RuntimeStep } from './lib/runtimes/progress'
import { ensureLanguage, isLanguageReady, runLocalCode } from './lib/runtimes/runtimeManager'

/** All entries in this catalog are browser-local; there is no server language to gate. */
const availableLanguages = RUNNER_LANGUAGES
const languageId = ref<RunnerLanguageId>(DEFAULT_LANGUAGE_ID)

// Per-language content cache (switching languages never overwrites another's draft).
const caches = new Map<RunnerLanguageId, UseContentCacheReturn>()
for (const item of RUNNER_LANGUAGES) {
  caches.set(item.id, useContentCache(`runner-code-${item.id}`, item.defaultCode))
}
const activeCache = computed<UseContentCacheReturn>(
  () => caches.get(languageId.value) ?? caches.get(DEFAULT_LANGUAGE_ID)!,
)
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

/** Language id for editor syntax highlighting (all catalog entries have a mode). */
const editorLanguage = computed<RunnerLanguageId>(() => languageId.value)

const outputHtml = computed(() => ansiToHtml(output.value))

// ──────────────────────────────────────────
//  Warm-up progress (one line per stable step)
// ──────────────────────────────────────────

interface StepView extends RuntimeStep {
  state: 'running' | 'done' | 'error'
  detail: string
}

const progressSteps = ref<StepView[]>([])
let clearTimer: number | undefined

function cancelProgressClear(): void {
  if (clearTimer !== undefined) {
    window.clearTimeout(clearTimer)
    clearTimer = undefined
  }
}

function resetProgress(): void {
  cancelProgressClear()
  progressSteps.value = []
}

/** Translate a structured runtime event into the checklist rendered in the console. */
function handleProgress(event: RuntimeProgress): void {
  cancelProgressClear()
  switch (event.type) {
    case 'start':
      progressSteps.value = [
        ...progressSteps.value.filter((step) => step.key !== event.step.key),
        { ...event.step, state: 'running', detail: '' },
      ]
      break
    case 'detail': {
      const step = progressSteps.value.find((item) => item.key === event.key)
      if (step) step.detail = event.detail
      break
    }
    case 'done': {
      const step = progressSteps.value.find((item) => item.key === event.key)
      if (step) step.state = 'done'
      break
    }
    case 'error': {
      const step = progressSteps.value.find((item) => item.key === event.key)
      if (step) {
        step.state = 'error'
        step.detail = event.message
      }
      break
    }
  }
}

/** "cls": fade the checklist away shortly after everything finishes. */
function scheduleProgressClear(): void {
  cancelProgressClear()
  clearTimer = window.setTimeout(() => {
    progressSteps.value = []
    clearTimer = undefined
  }, 900)
}

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
//  Run (always in this browser)
// ──────────────────────────────────────────

async function run() {
  if (running.value) return
  const language = languageId.value
  running.value = true
  output.value = ''
  resetProgress()
  status.value = '准备中...'
  try {
    if (!isLanguageReady(language)) {
      await ensureLanguage(language, handleProgress)
    }
    status.value = '运行中...'
    output.value = await runLocalCode(language, code.value, {
      stdin: stdin.value,
      onProgress: handleProgress,
    })
  } catch (error) {
    output.value = `[运行错误] ${error instanceof Error ? error.message : String(error)}`
  } finally {
    running.value = false
    status.value = '就绪'
    scheduleProgressClear()
  }
}

function clearOutput() {
  output.value = ''
  resetProgress()
}

onMounted(() => window.addEventListener('beforeunload', flushMemoryCache))
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', flushMemoryCache)
  cancelProgressClear()
})
</script>

<template>
  <PageShell
    fill
    title="本地代码运行器"
    desc="Python / JavaScript / TypeScript / Lua 在浏览器本地执行，代码与输入不上传、无需登录。Java / C / C++ 等语言请使用主站登录后的云端运行器。"
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
            class="w-[124px]! shrink-0"
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
            浏览器本地运行 · 代码不上传
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
          <TextArea
            v-model="stdin"
            class="mt-2 text-xs"
            min-height="72px"
            :rows="3"
            placeholder="运行时按需读取；此处内容会被一次读入。"
            aria-label="标准输入"
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
            v-if="progressSteps.length"
            class="progress-list"
          >
            <div
              v-for="step in progressSteps"
              :key="step.key"
              class="progress-step"
              :class="`is-${step.state}`"
            >
              <span class="step-mark">
                <span
                  v-if="step.state === 'running'"
                  class="step-spinner"
                />
                <span v-else-if="step.state === 'done'">✓</span>
                <span v-else>✗</span>
              </span>
              <span class="step-label">{{ step.label }}</span>
              <span
                v-if="step.detail"
                class="step-detail"
              >{{ step.detail }}</span>
            </div>
          </div>
          <div
            v-if="!output && !progressSteps.length"
            class="flex flex-1 items-center justify-center text-center text-[13px] text-[#64748b]"
          >
            {{ running ? status : '点击「运行」执行代码' }}
          </div>
          <pre
            v-if="output"
            class="m-0 whitespace-pre-wrap break-all font-mono text-[13px] leading-[1.6] text-[#e2e8f0]"
            v-html="outputHtml"
          />
        </div>
        <!-- eslint-enable vue/no-v-html -->
      </section>
    </div>
  </PageShell>
</template>

<style scoped>
.progress-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 10px;
}

.progress-step {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.5;
}

.progress-step.is-done {
  color: #4ade80;
}

.progress-step.is-error {
  color: #f87171;
}

.step-mark {
  display: inline-flex;
  flex-shrink: 0;
  justify-content: center;
  width: 14px;
}

.step-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(148, 163, 184, 0.3);
  border-top-color: #60a5fa;
  border-radius: 50%;
  animation: step-spin 0.7s linear infinite;
}

@keyframes step-spin {
  to {
    transform: rotate(360deg);
  }
}

.step-detail {
  color: #64748b;
}
</style>
