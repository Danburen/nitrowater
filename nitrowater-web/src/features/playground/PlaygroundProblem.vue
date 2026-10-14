<script setup lang="ts">
/**
 * Playground problem workspace — statement / code / verdict panels.
 *
 * A standalone top-level page (global header, no tools sidebar). The shell fills the viewport
 * and every panel owns its own scroll, so the layout absorbs all available height. Each of the
 * three panels can be collapsed to its title bar; the visible panels then grow to fill the space.
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { marked } from 'marked'
import { Refresh, VideoPlay } from '@element-plus/icons-vue'
import { useAuth } from '../../auth/useAuth'
import { RUNNER_LANGUAGES, isRunnerLanguage, type RunnerLanguageId } from '../tools/code-runner/langCatalog'
import CodeEditor from '../tools/code-runner/CodeEditor.vue'
import { createSubmission, getProblem, getSubmission, runCode, type ProblemDetail, type RunResult, type Submission } from './api'
import { difficultyMeta, runStatusMeta, statusLabel, verdictMeta } from './labels'
import PageShell from '../../shared/ui/PageShell.vue'

const route = useRoute()
const { session, login } = useAuth()

const problemId = computed(() => String(route.params.id))
const problem = ref<ProblemDetail | null>(null)
const loadError = ref('')

/** Judging languages: every Piston runtime the server can judge. */
const LANGUAGES = RUNNER_LANGUAGES.filter((item) => isRunnerLanguage(item.id))
const languageId = ref<RunnerLanguageId>('cpp')
const code = ref('')

const submitting = ref(false)
const submission = ref<Submission | null>(null)
const submitError = ref('')

/** Self-test run (against the first sample case) — the "run before submit" path. */
const running = ref(false)
const runResult = ref<RunResult | null>(null)
const runError = ref('')

/** Panel collapse state — collapsed panels shrink to their title bar and others absorb the space. */
const showStatement = ref(true)
const showCode = ref(true)
const showResult = ref(false)

let pollTimer: ReturnType<typeof setTimeout> | null = null

const title = computed(() => problem.value?.title || '题目')

/** Rendered markdown statement (admin-authored). */
const statementHtml = computed(() => (problem.value ? marked.parse(problem.value.description) : ''))

/** The first sample case drives the self-test run. */
const sampleCase = computed(() => problem.value?.sampleCases?.[0] ?? null)

function resetCode(): void {
  code.value = defaultCodeFor(languageId.value)
}

function defaultCodeFor(lang: RunnerLanguageId): string {
  const template = problem.value?.templates?.[lang]
  if (template) return template
  return LANGUAGES.find((item) => item.id === lang)?.defaultCode ?? ''
}

async function poll(id: number): Promise<void> {
  try {
    const current = await getSubmission(id)
    submission.value = current
    if (current.status === 'DONE' || current.status === 'SE') {
      submitting.value = false
      return
    }
    pollTimer = setTimeout(() => void poll(id), 1500)
  } catch (e) {
    submitError.value = e instanceof Error ? e.message : String(e)
    submitting.value = false
  }
}

async function submit(): Promise<void> {
  if (submitting.value || !problem.value) return
  if (!session.value?.authenticated) {
    login(`/playground/${problem.value.id}`)
    return
  }
  submitting.value = true
  submitError.value = ''
  submission.value = null
  showResult.value = true
  try {
    const created = await createSubmission(problem.value.id, languageId.value, code.value)
    submission.value = created
    pollTimer = setTimeout(() => void poll(created.id), 800)
  } catch (e) {
    submitError.value = e instanceof Error ? e.message : String(e)
    submitting.value = false
  }
}

async function runSample(): Promise<void> {
  if (running.value || !problem.value) return
  if (!session.value?.authenticated) {
    login(`/playground/${problem.value.id}`)
    return
  }
  running.value = true
  runError.value = ''
  runResult.value = null
  try {
    runResult.value = await runCode(languageId.value, code.value, sampleCase.value?.stdin ?? '')
  } catch (e) {
    runError.value = e instanceof Error ? e.message : String(e)
  } finally {
    running.value = false
  }
}

async function loadProblem(): Promise<void> {
  loadError.value = ''
  problem.value = null
  try {
    problem.value = await getProblem(problemId.value)
    code.value = defaultCodeFor(languageId.value)
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : String(e)
  }
}

// Different problem → reset the workspace (verdict cleared, code re-seeded).
watch(
  problemId,
  () => {
    submission.value = null
    submitError.value = ''
    runResult.value = null
    runError.value = ''
    void loadProblem()
  },
  { immediate: true },
)

// A different language gets its own starter snippet.
watch(languageId, (lang) => {
  code.value = defaultCodeFor(lang)
})

onBeforeUnmount(() => {
  if (pollTimer) clearTimeout(pollTimer)
})
</script>

<template>
  <PageShell
    fill
    :title="title"
    desc="ACM 模式在线判题：右侧写代码、提交评测，左侧查看题目与样例。三个面板均可收起/展开。"
  >
    <div
      v-if="loadError"
      class="py-16 text-center text-sm text-[#f43f5e]"
    >
      题目加载失败：{{ loadError }}
    </div>
    <div
      v-else-if="!problem"
      class="py-16 text-center text-sm text-faint"
    >
      加载中…
    </div>

    <div
      v-else
      class="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto lg:flex-row lg:overflow-hidden"
    >
      <!-- Statement -->
      <section
        class="panel flex flex-col"
        :class="showStatement ? 'lg:min-h-0 lg:flex-1' : ''"
      >
        <button
          type="button"
          class="flex items-center gap-2 text-left"
          :aria-expanded="showStatement"
          @click="showStatement = !showStatement"
        >
          <svg
            class="size-3.5 shrink-0 text-faint transition-transform"
            :class="showStatement ? 'rotate-90' : ''"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M9 5l7 7-7 7" />
          </svg>
          <span class="text-sm font-medium text-ink">题目</span>
          <span
            class="text-xs"
            :class="difficultyMeta(problem.difficulty).color"
          >{{ difficultyMeta(problem.difficulty).label }}</span>
          <span class="ml-auto truncate text-xs text-faint">#{{ problem.id }} · {{ problem.slug }}</span>
        </button>

        <div
          v-show="showStatement"
          class="mt-3 lg:min-h-0 lg:flex-1 lg:overflow-y-auto"
        >
          <div class="mb-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-faint">
            <span>时限 {{ problem.timeLimitMs }} ms</span>
            <span>内存 {{ Math.round(problem.memoryLimitKb / 1024) }} MB</span>
          </div>

          <!-- eslint-disable vue/no-v-html -- admin-authored markdown -->
          <div
            class="pg-statement"
            v-html="statementHtml"
          />
          <!-- eslint-enable vue/no-v-html -->

          <div
            v-if="problem.sampleCases.length"
            class="mt-4"
          >
            <div class="mb-2 text-sm font-medium text-ink">
              样例
            </div>
            <div
              v-for="sample in problem.sampleCases"
              :key="sample.idx"
              class="mb-3 grid gap-2 sm:grid-cols-2"
            >
              <div class="rounded-md border border-line bg-soft p-2">
                <div class="mb-1 text-[11px] text-faint">
                  输入
                </div>
                <pre class="m-0 whitespace-pre-wrap break-all font-mono text-xs text-ink">{{ sample.stdin }}</pre>
              </div>
              <div class="rounded-md border border-line bg-soft p-2">
                <div class="mb-1 text-[11px] text-faint">
                  输出
                </div>
                <pre class="m-0 whitespace-pre-wrap break-all font-mono text-xs text-ink">{{ sample.expectedOutput }}</pre>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Editor + verdict -->
      <section
        v-show="showCode || showResult"
        class="flex flex-col gap-3 lg:min-h-0 lg:flex-1"
      >
        <!-- Editor -->
        <div
          class="panel flex flex-col"
          :class="showCode ? 'lg:min-h-0 lg:flex-[3]' : ''"
        >
          <div class="flex flex-wrap items-center gap-2">
            <button
              type="button"
              class="flex items-center gap-2 text-left"
              :aria-expanded="showCode"
              @click="showCode = !showCode"
            >
              <svg
                class="size-3.5 shrink-0 text-faint transition-transform"
                :class="showCode ? 'rotate-90' : ''"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M9 5l7 7-7 7" />
              </svg>
              <span class="flex items-center gap-1.5 text-sm font-medium text-ink">
                <el-icon><VideoPlay /></el-icon>
                代码
              </span>
            </button>

            <template v-if="showCode">
              <el-select
                v-model="languageId"
                class="!w-[124px] shrink-0"
                size="small"
                :disabled="submitting"
              >
                <el-option
                  v-for="item in LANGUAGES"
                  :key="item.id"
                  :label="item.label"
                  :value="item.id"
                />
              </el-select>
              <el-tooltip
                content="重置为初始代码"
                placement="top"
              >
                <el-button
                  class="ml-auto shrink-0"
                  size="small"
                  :icon="Refresh"
                  circle
                  :disabled="submitting"
                  @click="resetCode"
                />
              </el-tooltip>
              <el-button
                size="small"
                class="shrink-0"
                :disabled="running || submitting"
                @click="runSample"
              >
                {{ running ? '运行中…' : '运行样例' }}
              </el-button>
              <el-button
                type="primary"
                size="small"
                class="shrink-0"
                :disabled="submitting || running"
                @click="submit"
              >
                {{ submitting ? '评测中…' : '提交评测' }}
              </el-button>
            </template>
          </div>

          <p
            v-show="showCode"
            class="mt-2 text-[11px] text-faint"
          >
            建议先「运行样例」确认输出无误，再「提交评测」；提交后仅展示评测结论，不展示用例详情。
          </p>

          <div
            v-show="showCode"
            class="mt-3 h-[380px] overflow-hidden rounded-md border border-line lg:h-auto lg:min-h-0 lg:flex-1"
          >
            <CodeEditor
              v-model="code"
              :language="languageId"
            />
          </div>

          <!-- Self-test run output (full detail — the submission path hides detail on purpose) -->
          <div
            v-if="showCode && (runResult || runError)"
            class="mt-3 rounded-md border border-line bg-soft p-2"
          >
            <div class="flex flex-wrap items-center gap-x-2 gap-y-1 text-[11px]">
              <span class="font-medium text-ink">运行样例</span>
              <span
                v-if="runError"
                class="text-[#f43f5e]"
              >{{ runError }}</span>
              <template v-else-if="runResult">
                <span :class="runStatusMeta(runResult.status).color">{{ runStatusMeta(runResult.status).label }}</span>
                <span
                  v-if="runResult.wallTimeMs != null"
                  class="text-faint"
                >{{ runResult.wallTimeMs }} ms</span>
                <span
                  v-if="sampleCase"
                  class="ml-auto text-faint"
                >期望输出：<span class="font-mono text-ink">{{ sampleCase.expectedOutput }}</span></span>
              </template>
            </div>
            <template v-if="runResult">
              <pre
                v-if="runResult.stdout"
                class="m-0 mt-1 max-h-40 overflow-auto whitespace-pre-wrap break-all font-mono text-[12px] text-ink"
              >{{ runResult.stdout }}</pre>
              <pre
                v-if="runResult.stderr"
                class="m-0 mt-1 max-h-40 overflow-auto whitespace-pre-wrap break-all font-mono text-[12px] text-[#f43f5e]"
              >{{ runResult.stderr }}</pre>
              <div
                v-if="!runResult.stdout && !runResult.stderr"
                class="mt-1 text-[11px] text-faint"
              >
                （无输出）
              </div>
              <div
                v-if="runResult.message"
                class="mt-1 text-[11px] text-faint"
              >
                {{ runResult.message }}
              </div>
            </template>
          </div>
        </div>

        <!-- Verdict -->
        <div
          class="panel flex flex-col"
          :class="showResult ? 'lg:min-h-0 lg:flex-[2]' : ''"
        >
          <button
            type="button"
            class="flex items-center gap-2 text-left"
            :aria-expanded="showResult"
            @click="showResult = !showResult"
          >
            <svg
              class="size-3.5 shrink-0 text-faint transition-transform"
              :class="showResult ? 'rotate-90' : ''"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M9 5l7 7-7 7" />
            </svg>
            <span class="text-sm font-medium text-ink">评测结果</span>
            <span
              v-if="submission?.verdict"
              class="text-xs"
              :class="verdictMeta(submission.verdict)?.color"
            >{{ verdictMeta(submission.verdict)?.label }}</span>
          </button>

          <div
            v-show="showResult"
            class="mt-3 lg:min-h-0 lg:flex-1 lg:overflow-y-auto"
          >
            <div
              v-if="submitError"
              class="text-sm text-[#f43f5e]"
            >
              {{ submitError }}
            </div>

            <div
              v-else-if="!submission"
              class="py-6 text-center text-xs text-faint"
            >
              尚未提交，点击「提交评测」查看结果。
            </div>

            <template v-else>
              <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
                <span
                  class="text-sm font-semibold"
                  :class="verdictMeta(submission.verdict)?.color"
                >{{ verdictMeta(submission.verdict)?.label }}</span>
                <span class="text-xs text-faint">{{ statusLabel(submission.status) }}</span>
                <span
                  v-if="submission.status === 'DONE'"
                  class="ml-auto text-xs text-dim"
                >
                  通过 {{ submission.passed }}/{{ submission.total }} · 得分 {{ submission.score }}
                  <template v-if="submission.timeMs != null"> · {{ submission.timeMs }} ms</template>
                  <template v-if="submission.memoryKb != null"> · {{ Math.round(submission.memoryKb / 1024) }} MB</template>
                </span>
              </div>

              <div
                v-if="submission.compileMessage"
                class="mt-2 whitespace-pre-wrap break-all font-mono text-[12px] text-[#f43f5e]"
              >
                {{ submission.compileMessage }}
              </div>
            </template>
          </div>
        </div>
      </section>
    </div>
  </PageShell>
</template>

<style>
/* Markdown statement styling (admin-authored, rendered via marked). */
.pg-statement {
  color: var(--ink);
  font-size: 13.5px;
  line-height: 1.7;
}
.pg-statement h1,
.pg-statement h2,
.pg-statement h3 {
  margin: 1em 0 0.5em;
  font-weight: 600;
  line-height: 1.3;
}
.pg-statement h1 {
  font-size: 1.25em;
}
.pg-statement h2 {
  font-size: 1.15em;
}
.pg-statement h3 {
  font-size: 1.05em;
}
.pg-statement p {
  margin: 0.6em 0;
}
.pg-statement ul,
.pg-statement ol {
  margin: 0.6em 0;
  padding-left: 1.4em;
}
.pg-statement ul {
  list-style: disc;
}
.pg-statement ol {
  list-style: decimal;
}
.pg-statement code {
  padding: 0.1em 0.35em;
  border-radius: 4px;
  background: var(--soft);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.92em;
}
.pg-statement pre {
  margin: 0.7em 0;
  padding: 0.7em 0.9em;
  overflow-x: auto;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--soft);
  font-size: 12.5px;
}
.pg-statement pre code {
  padding: 0;
  background: none;
}
.pg-statement a {
  color: var(--accent);
}
.pg-statement table {
  margin: 0.7em 0;
  border-collapse: collapse;
}
.pg-statement th,
.pg-statement td {
  border: 1px solid var(--line);
  padding: 0.35em 0.6em;
}
</style>
