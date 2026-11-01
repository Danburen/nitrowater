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
import { computed, nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import Prism from 'prismjs'
import 'prismjs/components/prism-python'
import 'prismjs/components/prism-javascript'
import 'prismjs/components/prism-typescript'
import 'prismjs/components/prism-java'
import 'prismjs/components/prism-c'
import 'prismjs/components/prism-cpp'
import 'prismjs/components/prism-lua'
import { apiFetch } from '../../../auth/bff'
import type { SandboxRunOutcome } from '../../../api/sandbox'
import { readJson } from '../../../api/envelope'
import { useAuth } from '../../../auth/useAuth'
import { writeRunnerSeed } from '../../../shared/codeRunnerSeed'
import { toast } from '../../../shared/ui/toast'

interface SandboxTarget {
  readonly id: string
  readonly label: string
  readonly fileName: string
  readonly prism: string
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
const outcome = ref<SandboxRunOutcome | null>(null)
const failure = ref('')
const copied = ref(false)
/** The inline terminal, scrolled into view when a run starts or finishes. */
const terminalEl = ref<HTMLElement | null>(null)

const router = useRouter()
const { session, login } = useAuth()

/** Languages the runner executes on the server; they are only selectable when signed in. */
const SERVER_LANGUAGES: ReadonlySet<string> = new Set(['java', 'c', 'cpp'])

const target = computed<SandboxTarget | null>(() => TARGETS[props.language.trim().toLowerCase()] ?? null)
const languageLabel = computed(() => target.value?.label ?? (props.language || 'text'))

/**
 * Hides the run button after the sandbox rejected the source at compile time.
 *
 * <p>This is the ground-truth correction for the entry-point heuristic: a block that cannot
 * compile will never run, so offering the button again would only repeat the same error.</p>
 */
const compileFailed = ref(false)

/** Inputs that block waiting for data the card cannot supply (no stdin box yet). */
const STDIN_PATTERNS: readonly RegExp[] = [
  /\binput\s*\(/,
  /\bScanner\b/,
  /System\s*\.\s*in/,
  /\bcin\s*>>/,
  /\bscanf\s*\(/,
  /\breadline\b/,
  /\breadlines\s*\(/,
  /sys\.stdin/,
]

/**
 * Removes comments and string literals (keeping newlines) so an entry-point match cannot be
 * fooled by {@code main} appearing in prose.
 */
function stripNoise(source: string, language: string): string {
  let out = ''
  let index = 0
  while (index < source.length) {
    const char = source[index]
    const next = source[index + 1]
    if (char === '/' && next === '/') {
      while (index < source.length && source[index] !== '\n') {
        out += ' '
        index += 1
      }
      continue
    }
    if (char === '/' && next === '*') {
      out += '  '
      index += 2
      while (index < source.length && !(source[index] === '*' && source[index + 1] === '/')) {
        out += source[index] === '\n' ? '\n' : ' '
        index += 1
      }
      if (index < source.length) {
        out += '  '
        index += 2
      }
      continue
    }
    const quoted = char === '"' || char === "'" || (char === '`' && (language === 'javascript' || language === 'typescript'))
    if (quoted) {
      out += char
      index += 1
      while (index < source.length) {
        if (source[index] === '\\') {
          out += '  '
          index += 2
          continue
        }
        const done = source[index] === char
        out += source[index] === '\n' ? '\n' : done ? char : ' '
        index += 1
        if (done) break
      }
      continue
    }
    out += char
    index += 1
  }
  return out
}

/** {@code void main(String[] args)} / {@code String... args} / {@code String args[]}, with {@code static} in the same statement. */
function hasJavaMain(clean: string): boolean {
  const signature = /\bvoid\s+main\s*\(\s*(?:final\s+)?(?:java\.lang\.)?String\s*(?:(?:\.\.\.|\[\s*\])\s*\w+|\w+\s*\[\s*\])\s*\)/.exec(clean)
  if (!signature) return false
  const before = clean.slice(Math.max(0, signature.index - 200), signature.index)
  // Look only at the current declaration: the previous ';' / '}' bounds unrelated static fields.
  const statementStart = Math.max(before.lastIndexOf(';'), before.lastIndexOf('}'))
  return /\bstatic\b/.test(before.slice(statementStart + 1))
}

/** Conservative entry-point gate: only decides whether to *offer* the button, never promises a run. */
const entryPointOk = computed(() => {
  const sandbox = target.value
  if (!sandbox) return false
  const clean = stripNoise(props.code, sandbox.id)
  if (sandbox.id === 'java') return hasJavaMain(clean)
  if (sandbox.id === 'c' || sandbox.id === 'cpp') return /\bint\s+main\s*\(/.test(clean)
  // Script languages have no entry-point concept: any syntactically valid snippet runs.
  return true
})

const needsStdin = computed(() => {
  const sandbox = target.value
  if (!sandbox) return false
  const clean = stripNoise(props.code, sandbox.id)
  return STDIN_PATTERNS.some((pattern) => pattern.test(clean))
})

/** Whether the run button is offered at all. */
const showRun = computed(() => target.value !== null
  && !props.streaming
  && !needsStdin.value
  && entryPointOk.value
  && !compileFailed.value)

const runnable = computed(() => showRun.value && !running.value)

/** Why the run button is missing (shown as a muted note next to the language). */
const blockedReason = computed(() => {
  if (!target.value || props.streaming) return ''
  if (compileFailed.value) return '编译失败，已隐藏运行'
  if (needsStdin.value) return '需要标准输入，暂不支持直接运行'
  if (!entryPointOk.value) return '缺少程序入口（main）'
  return ''
})

/** A finished block can always be handed to the full runner. */
const canOpenInRunner = computed(() => target.value !== null && !props.streaming)
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

/** Brings the inline terminal into view: opened → nearest, output arrived → align to its end. */
async function revealTerminal(block: 'nearest' | 'start' | 'center' | 'end'): Promise<void> {
  await nextTick()
  terminalEl.value?.scrollIntoView({ behavior: 'smooth', block })
}

async function run(): Promise<void> {
  const sandbox = target.value
  if (!sandbox || running.value || props.streaming) return
  running.value = true
  ran.value = true
  outcome.value = null
  failure.value = ''
  // The terminal is rendered on the next tick; scroll once it exists so the user sees progress.
  void revealTerminal('nearest')
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
    outcome.value = await readJson<SandboxRunOutcome>(response)
    if (outcome.value.status === 'CE') {
      // The sandbox proved the source cannot compile: stop offering the button for this block.
      compileFailed.value = true
    }
  } catch (cause) {
    failure.value = cause instanceof Error ? `网络错误：${cause.message}` : '网络错误'
  } finally {
    running.value = false
    // Reveal the result (stdout/stderr/exit code) once it is painted.
    void revealTerminal('end')
  }
}

/**
 * Hands the snippet to the code-runner tool (edit + stdin + language switching), then navigates.
 *
 * <p>The payload travels through a one-shot sessionStorage seed: a URL would pollute history and
 * truncate long code. If storage is blocked the clipboard becomes the fallback channel.</p>
 */
async function openInRunner(): Promise<void> {
  const sandbox = target.value
  if (!sandbox) return
  const stored = writeRunnerSeed({ language: sandbox.id, code: props.code })
  if (!stored) {
    try {
      await navigator.clipboard.writeText(props.code)
      toast.info('已复制代码，进入运行器后粘贴即可')
    } catch {
      toast.error('无法传递代码，请先手动复制')
      return
    }
  }
  if (SERVER_LANGUAGES.has(sandbox.id) && session.value?.authenticated !== true) {
    // Server-side languages are only listed for signed-in users; sign in, then the seed is consumed.
    login('/tools/code-runner')
    return
  }
  void router.push('/tools/code-runner')
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
      <span
        v-if="blockedReason"
        class="code-note"
      >{{ blockedReason }}</span>
      <span class="code-actions">
        <button
          type="button"
          class="code-btn"
          @click="copy"
        >
          {{ copied ? '已复制' : '复制' }}
        </button>
        <button
          v-if="canOpenInRunner"
          type="button"
          class="code-btn"
          title="打开代码运行器：可编辑、可输入 stdin"
          @click="openInRunner"
        >
          <svg
            class="size-3"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path d="M7 17 17 7M8 7h9v9" />
          </svg>
          打开运行器
        </button>
        <button
          v-if="showRun"
          type="button"
          class="code-btn code-run"
          :disabled="!runnable"
          title="在沙盒中运行（不保证依赖完整）"
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
      ref="terminalEl"
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

.code-note {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-align: right;
  text-overflow: ellipsis;
  padding: 0 8px;
  font-size: 10.5px;
  color: #6b7280;
  white-space: nowrap;
}

.code-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
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