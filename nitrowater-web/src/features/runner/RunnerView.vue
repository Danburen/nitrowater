<script setup lang="ts">
/**
 * Signed-in code runner — a standalone page at `/runner`.
 *
 * Unlike the old in-portal tool, this page has no anonymous / browser-local tier: every language
 * runs in the backend sandbox (Piston) via `/api/sandbox/run`, and an unauthenticated visitor is
 * sent through the BFF login flow with `/runner` as the return target.
 *
 * UI mirrors the wtools runner: two panels with headers (icon + title on the left, controls on the
 * right) and a dark console for output.
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
import { confirm } from '../../shared/ui/confirm'
import { useAuth } from '../../auth/useAuth'
import { consumeRunnerSeed } from '../../shared/codeRunnerSeed'
import { apiFetch } from '../../auth/bff'
import { readJson } from '../../api/envelope'
import type { SandboxRunOutcome } from '../../api/sandbox'
import { toast } from '../../shared/ui/toast'
import TextArea from '../../shared/ui/TextArea.vue'
import CodeEditor from './CodeEditor.vue'
import {
  DEFAULT_LANGUAGE_ID,
  RUNNER_LANGUAGES,
  isRunnerLanguage,
  type RunnerLanguageId,
} from './langCatalog'
import { ansiToHtml } from './lib/ansi'
import { flushMemoryCache, useContentCache, type UseContentCacheReturn } from './lib/contentCache'

const { login, session, loading } = useAuth()

const languageId = ref<RunnerLanguageId>(DEFAULT_LANGUAGE_ID)

/** Per-language content cache (switching languages never overwrites another's draft). */
const caches = new Map<string, UseContentCacheReturn>()
for (const item of RUNNER_LANGUAGES) {
  caches.set(item.id, useContentCache(`runner-code-${item.id}`, item.defaultCode))
}
const activeCache = computed<UseContentCacheReturn>(() => caches.get(languageId.value) ?? caches.get(DEFAULT_LANGUAGE_ID)!)
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

/**
 * Applies a one-shot handoff from a chat code block (language + source).
 *
 * <p>Runs after the session probe resolves so a pending login can never consume the seed before the
 * runner is actually usable.</p>
 */
async function applySeed(): Promise<void> {
  const seed = consumeRunnerSeed()
  if (!seed || !isRunnerLanguage(seed.language)) return
  const language = seed.language
  const entry = RUNNER_LANGUAGES.find((item) => item.id === language)
  const cache = caches.get(language)
  if (!entry || !cache) return

  const current = cache.content.value
  if (current.trim() && current !== entry.defaultCode && current !== seed.code) {
    if (!(await confirm({
      title: '覆盖现有代码？',
      message: `运行器里「${entry.label}」已有代码，载入会覆盖当前草稿。`,
      confirmText: '覆盖',
      danger: true,
    }))) return
  }

  languageId.value = language
  cache.content.value = seed.code
  stdin.value = ''
  toast.success('已从对话载入代码')
}

// Login guard: this page is useless without a session, so leave the SPA entirely for the SSO flow.
watch(
  [loading, () => session.value?.authenticated],
  ([busy, authenticated]) => {
    if (busy) return
    if (!authenticated) {
      login('/runner')
      return
    }
    void applySeed()
  },
  { immediate: true },
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
    return '[需登录] 登录后才能运行代码。'
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
  const data = await readJson<SandboxRunOutcome>(response)
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
    status.value = '运行中...'
    output.value = await runOnServer(language, code.value, stdin.value)
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
  <!-- Standalone page: the global AppHeader is the only chrome; content fills the rest. -->
  <div class="flex h-[calc(100svh_-_var(--app-header-h))] min-h-0 flex-col p-4 sm:p-6">
    <div
      v-if="!session?.authenticated"
      class="flex flex-1 items-center justify-center text-[13px] text-dim"
    >
      正在跳转登录…
    </div>
    <div
      v-else
      class="grid min-h-0 flex-1 gap-4 lg:grid-cols-2"
    >
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
              v-for="item in RUNNER_LANGUAGES"
              :key="item.id"
              :label="item.label"
              :value="item.id"
            />
          </el-select>
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
            :language="languageId"
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
  </div>
</template>
