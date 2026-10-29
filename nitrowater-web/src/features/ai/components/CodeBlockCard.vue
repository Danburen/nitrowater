<script setup lang="ts">
/**
 * One fenced code block with syntax highlighting and an inline sandbox terminal.
 *
 * <p>Behaviour mirrors docs/reference/SAMPLE_NITROBOT_CODERUN.html: a dark, borderless panel, the
 * run button expands a terminal inside the block and streams the sandbox result there. Two safety
 * rules are enforced: a block may only run once streaming finished (never execute half-written
 * code), and only languages the backend sandbox actually supports get a run button.</p>
 *
 * <p>Highlighting is skipped while the block is still streaming: re-tokenizing a growing snippet
 * every frame is wasted work and produces flickering half-tokens.</p>
 */
import { computed, ref } from 'vue'
import Prism from 'prismjs'
import 'prismjs/components/prism-python'
import 'prismjs/components/prism-javascript'
import 'prismjs/components/prism-typescript'
import 'prismjs/components/prism-java'
import 'prismjs/components/prism-c'
import 'prismjs/components/prism-cpp'
import 'prismjs/components/prism-lua'
import { apiFetch } from '../../../auth/bff'

interface SandboxTarget {
  readonly id: string
  readonly label: string
  readonly fileName: string
  readonly prism: string
}

interface RunOutcome {
  readonly stdout: string | null
  readonly stderr: string | null
  readonly status: string
  readonly exitCode: number | null
  readonly wallTimeMs: number | null
  readonly memoryBytes: number | null
  readonly message: string | null
}

const TARGETS: Readonly<Record<string, SandboxTarget>> = {
  python: { id: 'python', label: 'Python', fileName: 'main.py', prism: 'python' },
  py: { id: 'python', label: 'Python', fileName: 'main.py', prism: 'python' },
  javascript: { id: 'javascript', label: 'JavaScript', fileName: 'main.js', prism: 'javascript' },
  js: { id: 'javascript', label: 'JavaScript', fileName: 'main.js', prism: 'javascript' },
  node: { id: 'javascript', label: 'JavaScript', fileName: 'main.js', prism: 'javascript' },
  typescript: { id: 'typescript', label: 'TypeScript', fileName: 'main.ts', prism: 'typescript' },
  ts: { id: 'typescript', label: 'TypeScript', fileName: 'main.ts', prism: 'typescript' },
  java: { id: 'java', label: 'Java', fileName: 'Main.java', prism: 'java' },
  c: { id: 'c', label: 'C', fileName: 'main.c', prism: 'c' },
  cpp: { id: 'cpp', label: 'C++', fileName: 'main.cpp', prism: 'cpp' },
  'c++': { id: 'cpp', label: 'C++', fileName: 'main.cpp', prism: 'cpp' },
  lua: { id: 'lua', label: 'Lua', fileName: 'main.lua', prism: 'lua' },
}

const STATUS_LABELS: Readonly<Record<string, string>> = {
  OK: '运行成功',
  CE: '编译失败',
  TO: '运行超时',
  OL: '输出超限',
  RE: '运行错误',
  SG: '运行错误',
  SE: '执行服务异常',
}

const props = withDefaults(defineProps<{
  language: string
  code: string
  /** True while the answer is still streaming; the block must not be runnable yet. */
  streaming?: boolean
}>(), { streaming: false })

const running = ref(false)
const ran = ref(false)
const outcome = ref<RunOutcome | null>(null)
const failure = ref('')
const copied = ref(false)

const target = computed<SandboxTarget | null>(() => TARGETS[props.language.trim().toLowerCase()] ?? null)
const runnable = computed(() => target.value !== null && !props.streaming && !running.value)
const languageLabel = computed(() => target.value?.label ?? (props.language || 'text'))
const command = computed(() => (target.value ? `$ ${target.value.fileName}` : ''))
const statusText = computed(() => {
  if (running.value) return '运行中…'
  if (!ran.value) return ''
  if (!outcome.value) return failure.value ? '运行失败' : ''
  const label = STATUS_LABELS[outcome.value.status] ?? '运行失败'
  const elapsed = outcome.value.wallTimeMs
  return elapsed === null ? label : `${label} (${(elapsed / 1000).toFixed(2)}s)`
})
const succeeded = computed(() => outcome.value?.status === 'OK')
const terminalOpen = computed(() => running.value || ran.value)

/** Prism output, or {@code null} when streaming or the language has no grammar. */
const highlighted = computed(() => {
  if (props.streaming) return null
  const language = TARGETS[props.language.trim().toLowerCase()]?.prism
  if (!language) return null
  const grammar = Prism.languages[language]
  if (!grammar) return null
  try {
    return Prism.highlight(props.code, grammar, language)
  } catch {
    // A grammar failure must never break the transcript: fall back to plain text.
    return null
  }
})

async function run(): Promise<void> {
  const sandbox = target.value
  if (!sandbox || running.value || props.streaming) return
  running.value = true
  ran.value = true
  outcome.value = null
  failure.value = ''
  try {
    const response = await apiFetch('/api/sandbox/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ language: sandbox.id, code: props.code }),
    })
    if (response.status === 401) {
      failure.value = '登录后才能运行代码'
      return
    }
    if (!response.ok) {
      failure.value = await readFailure(response)
      return
    }
    outcome.value = (await response.json()) as RunOutcome
  } catch (cause) {
    failure.value = cause instanceof Error ? `网络错误：${cause.message}` : '网络错误'
  } finally {
    running.value = false
  }
}

async function copy(): Promise<void> {
  try {
    await navigator.clipboard.writeText(props.code)
    copied.value = true
    window.setTimeout(() => {
      copied.value = false
    }, 1200)
  } catch {
    // Clipboard permission denied: leave the label unchanged.
  }
}

/** Reads the sandbox error body, which carries a stable code plus a Chinese message. */
async function readFailure(response: Response): Promise<string> {
  const text = await response.text().catch(() => '')
  try {
    const parsed = JSON.parse(text) as { message?: string; error?: string }
    if (parsed.message) return parsed.message
    if (parsed.error) return parsed.error
  } catch {
    // Not JSON: fall through to the raw body.
  }
  return text || `运行失败（HTTP ${response.status}）`
}

/** ANSI SGR sequences emitted by runners; stripped so terminal colours cannot corrupt the layout. */
const ANSI_SGR = new RegExp(`${String.fromCharCode(27)}\\[[0-9;]*m`, 'g')

/** Strips ANSI escapes from sandbox output. */
function clean(value: string | null): string {
  return (value ?? '').replace(ANSI_SGR, '')
}
</script>

<template>
  <div class="code-card">
    <div class="code-head">
      <span class="code-lang">{{ languageLabel }}</span>
      <span class="code-actions">
        <button
          type="button"
          class="code-btn"
          @click="copy"
        >
          {{ copied ? '已复制' : '复制' }}
        </button>
        <button
          v-if="target"
          type="button"
          class="code-btn code-run"
          :disabled="!runnable"
          :title="streaming ? '回答完成后才能运行' : `在沙盒中运行 ${languageLabel}`"
          @click="run"
        >
          {{ running ? '运行中…' : '运行' }}
        </button>
      </span>
    </div>

    <!-- Prism output is generated from escaped source, so it is safe to inject. -->
    <!-- eslint-disable-next-line vue/no-v-html -->
    <pre class="code-body"><code
      v-if="highlighted"
      class="code-hl"
      v-html="highlighted"
    /><code v-else>{{ code }}</code></pre>

    <div
      v-if="terminalOpen"
      class="code-terminal"
    >
      <div class="term-head">
        <span>终端输出（沙盒）</span>
        <span :class="succeeded ? 'term-ok' : 'term-fail'">{{ statusText }}</span>
      </div>
      <div class="term-body">
        <p class="term-cmd">
          {{ command }}
        </p>
        <p
          v-if="failure"
          class="term-fail"
        >
          {{ failure }}
        </p>
        <pre
          v-if="outcome && clean(outcome.stdout)"
          class="term-stream"
        >{{ clean(outcome.stdout) }}</pre>
        <pre
          v-if="outcome && clean(outcome.stderr)"
          class="term-stream term-fail"
        >{{ clean(outcome.stderr) }}</pre>
        <p
          v-if="outcome && !clean(outcome.stdout) && !clean(outcome.stderr)"
          class="term-muted"
        >
          {{ outcome.message || '（无输出）' }}
        </p>
        <p
          v-if="outcome"
          class="term-muted"
        >
          Process finished with exit code {{ outcome.exitCode ?? '—' }}
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.code-card {
  margin: 0.7em 0;
  overflow: hidden;
  border-radius: 12px;
  background: #1e1e24;
}

.code-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 10px;
  background: #2a2a32;
  font-family: var(--font-mono);
  font-size: 11px;
  color: #a0a0ab;
}

.code-actions {
  display: flex;
  gap: 6px;
}

.code-btn {
  border-radius: 6px;
  padding: 2px 9px;
  font-size: 11px;
  color: #a0a0ab;
  background: #3a3a44;
  transition: background 0.12s, color 0.12s;
}

.code-btn:hover:not(:disabled) {
  background: #4a4a54;
  color: #e8e8ec;
}

.code-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.code-run {
  background: color-mix(in srgb, var(--color-accent) 78%, #000);
  color: #fff;
}

.code-run:hover:not(:disabled) {
  background: var(--color-accent);
}

.code-body {
  margin: 0;
  overflow-x: auto;
  padding: 12px 14px;
  font-family: var(--font-mono);
  font-size: 12.5px;
  line-height: 1.6;
  color: #e8e8ec;
}

.code-body code {
  font-family: inherit;
}

/* One Dark-ish palette tuned for the dark panel. */
.code-body :deep(.token.comment),
.code-body :deep(.token.prolog),
.code-body :deep(.token.doctype),
.code-body :deep(.token.cdata) {
  color: #6b7280;
}

.code-body :deep(.token.punctuation) {
  color: #a6accd;
}

.code-body :deep(.token.keyword),
.code-body :deep(.token.tag),
.code-body :deep(.token.deleted) {
  color: #c792ea;
}

.code-body :deep(.token.string),
.code-body :deep(.token.attr-value),
.code-body :deep(.token.inserted) {
  color: #c3e88d;
}

.code-body :deep(.token.number),
.code-body :deep(.token.boolean),
.code-body :deep(.token.constant) {
  color: #f78c6c;
}

.code-body :deep(.token.function),
.code-body :deep(.token.class-name) {
  color: #82aaff;
}

.code-body :deep(.token.operator),
.code-body :deep(.token.entity) {
  color: #89ddff;
}

.code-body :deep(.token.builtin),
.code-body :deep(.token.attr-name) {
  color: #ffcb6b;
}

.code-terminal {
  padding: 9px 14px 11px;
  background: #111;
  font-family: var(--font-mono);
  font-size: 11.5px;
  color: #a0a0ab;
}

.term-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
  color: #6b7280;
}

.term-body {
  max-height: 260px;
  overflow: auto;
}

.term-cmd {
  margin: 0 0 4px;
  color: #7bd88f;
}

.term-stream {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  color: #e8e8ec;
}

.term-muted {
  margin: 4px 0 0;
  color: #6b7280;
}

.term-ok {
  color: #7bd88f;
}

.term-fail {
  color: #ff7b72;
}
</style>
