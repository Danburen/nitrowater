<script setup lang="ts">
/**
 * Playground problem workspace — statement / code / verdict panels.
 *
 * A standalone top-level page (global header, no tools sidebar). The shell fills the viewport
 * and every panel owns its own scroll, so the layout absorbs all available height. Nested
 * splitpanes let the user drag the dividers between panels (side-by-side on desktop, stacked
 * rows on mobile); the pane shares are persisted to localStorage. Collapsing a panel removes
 * it from the split and leaves a compact tab (a vertical rail on desktop for the statement,
 * horizontal title bars for the editor/verdict) so every panel can always be expanded again.
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { marked } from 'marked'
import { Refresh, VideoPlay } from '@element-plus/icons-vue'
import { Pane, Splitpanes, type SplitpanesResizedPayload } from 'splitpanes'
import 'splitpanes/dist/splitpanes.css'
import { useAuth } from '../../auth/useAuth'
import { RUNNER_LANGUAGES, isRunnerLanguage, type RunnerLanguageId } from '../tools/code-runner/langCatalog'
import CodeEditor from '../tools/code-runner/CodeEditor.vue'
import {
  createSubmission,
  getProblem,
  getSubmission,
  runProblem,
  type ProblemDetail,
  type ProblemRunResult,
  type Submission,
} from './api'
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

/**
 * The single free-form self-test input: a plain "stdin in, stdout out" run (code-runner style).
 * The backend never compares anything — the result simply shows what the program printed.
 */
const runStdin = ref('')
const running = ref(false)
const runResult = ref<ProblemRunResult | null>(null)
const runError = ref('')

/** First non-AC case of the finished submission, or null while pending / all-passed. */
const firstFailedCase = computed(() => submission.value?.cases?.find((c) => c.verdict !== 'AC') ?? null)

/** e.g. "答案错误：第 3 个用例未通过（通过 2/5）"; null for AC/CE/pending. */
const failureSummary = computed(() => {
  const s = submission.value
  if (!s || s.verdict == null || s.verdict === 'CE') return null
  const failed = firstFailedCase.value
  if (!failed) return null
  return `${verdictMeta(failed.verdict)?.label ?? failed.verdict}：第 ${failed.index + 1} 个用例未通过（通过 ${s.passed}/${s.total}）`
})

/** Whether to render the per-case breakdown (hidden for CE — the compile message says it all). */
const showCaseBreakdown = computed(() =>
  submission.value?.status === 'DONE'
  && submission.value.verdict != null
  && submission.value.verdict !== 'CE'
  && (submission.value.cases?.length ?? 0) > 0)

/** Panel collapse state — a collapsed panel leaves a compact tab and its pane leaves the split. */
const showStatement = ref(true)
const showCode = ref(true)
const showResult = ref(false)

let pollTimer: ReturnType<typeof setTimeout> | null = null

const title = computed(() => problem.value?.title || '题目')

/** Rendered markdown statement (admin-authored). */
const statementHtml = computed(() => (problem.value ? marked.parse(problem.value.description) : ''))

// ---------------------------------------------------------------------------
// Responsive direction + persisted pane sizes
// ---------------------------------------------------------------------------

/** Breakpoint shared with the Tailwind `lg:` utilities — drives the splitpanes direction. */
const DESKTOP_QUERY = '(min-width: 1024px)'
const desktopMedia = window.matchMedia(DESKTOP_QUERY)
const isDesktop = ref(desktopMedia.matches)

function onDesktopChange(event: MediaQueryListEvent): void {
  isDesktop.value = event.matches
}

/** Persisted pane shares (%). The statement share means width/height per orientation. */
interface LayoutState {
  stmtDesktop: number
  stmtMobile: number
  code: number
  res: number
}

const LAYOUT_KEY = 'nitrowater:playground-layout'
const DEFAULT_LAYOUT: LayoutState = { stmtDesktop: 32, stmtMobile: 40, code: 60, res: 40 }

/** Clamps a persisted share into a sane range, falling back to the default on garbage input. */
function sanitizeShare(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 5 && value <= 95
    ? value
    : fallback
}

function loadLayout(): LayoutState {
  try {
    const raw = localStorage.getItem(LAYOUT_KEY)
    if (!raw) return { ...DEFAULT_LAYOUT }
    const parsed = JSON.parse(raw) as Partial<LayoutState>
    return {
      stmtDesktop: sanitizeShare(parsed.stmtDesktop, DEFAULT_LAYOUT.stmtDesktop),
      stmtMobile: sanitizeShare(parsed.stmtMobile, DEFAULT_LAYOUT.stmtMobile),
      code: sanitizeShare(parsed.code, DEFAULT_LAYOUT.code),
      res: sanitizeShare(parsed.res, DEFAULT_LAYOUT.res),
    }
  } catch {
    return { ...DEFAULT_LAYOUT }
  }
}

const layout = ref<LayoutState>(loadLayout())

function persistLayout(): void {
  try {
    localStorage.setItem(LAYOUT_KEY, JSON.stringify(layout.value))
  } catch {
    // Storage unavailable (private mode etc.) — the layout simply won't persist.
  }
}

/** Persists the statement share after a drag, but only while both outer panes exist. */
function onOuterResized({ panes }: SplitpanesResizedPayload): void {
  if (!showStatement.value || panes.length < 2) return
  layout.value[isDesktop.value ? 'stmtDesktop' : 'stmtMobile'] = panes[0].size
  persistLayout()
}

/** Persists the editor/verdict shares after a drag, but only while both panes exist. */
function onInnerResized({ panes }: SplitpanesResizedPayload): void {
  if (!showCode.value || !showResult.value || panes.length < 2) return
  layout.value.code = panes[0].size
  layout.value.res = panes[1].size
  persistLayout()
}

// ---------------------------------------------------------------------------
// Self-test run (stdin → stdout, no cases, no judging)
// ---------------------------------------------------------------------------

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

async function runTests(): Promise<void> {
  if (running.value || !problem.value) return
  if (!session.value?.authenticated) {
    login(`/playground/${problem.value.id}`)
    return
  }
  running.value = true
  runError.value = ''
  runResult.value = null
  try {
    runResult.value = await runProblem(
      problem.value.id,
      languageId.value,
      code.value,
      runStdin.value,
    )
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
    runStdin.value = ''
    void loadProblem()
  },
  { immediate: true },
)

// A different language gets its own starter snippet.
watch(languageId, (lang) => {
  code.value = defaultCodeFor(lang)
})

onMounted(() => {
  desktopMedia.addEventListener('change', onDesktopChange)
})

onBeforeUnmount(() => {
  desktopMedia.removeEventListener('change', onDesktopChange)
  if (pollTimer) clearTimeout(pollTimer)
})
</script>

<template>
  <PageShell
    fill
    :title="title"
    desc="ACM 模式在线判题：右侧写代码、提交评测，左侧查看题目与样例。分隔条可拖动调整面板宽度，三个面板均可收起/展开。"
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
      class="flex min-h-0 flex-1 flex-col gap-2.5 lg:flex-row"
    >
      <!-- Collapsed statement: full-width bar on mobile, vertical rail on desktop -->
      <button
        v-if="!showStatement"
        type="button"
        class="panel-tab shrink-0 justify-center lg:h-full lg:w-10 lg:flex-col lg:px-0 lg:py-3"
        :aria-expanded="false"
        aria-label="展开题目面板"
        @click="showStatement = true"
      >
        <svg
          class="size-3.5 shrink-0 rotate-90 text-faint transition-transform lg:rotate-0"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M9 5l7 7-7 7" />
        </svg>
        <span class="text-sm font-medium text-ink lg:[writing-mode:vertical-rl]">题目</span>
        <span
          class="text-xs lg:[writing-mode:vertical-rl]"
          :class="difficultyMeta(problem.difficulty).color"
        >{{ difficultyMeta(problem.difficulty).label }}</span>
        <span class="min-w-0 truncate text-xs text-faint lg:hidden">#{{ problem.id }} · {{ problem.slug }}</span>
      </button>

      <Splitpanes
        class="min-h-0 flex-1"
        :horizontal="!isDesktop"
        @resized="onOuterResized"
      >
        <!-- Statement -->
        <Pane
          v-if="showStatement"
          :size="isDesktop ? layout.stmtDesktop : layout.stmtMobile"
          min-size="18"
          max-size="70"
        >
          <section class="panel flex h-full min-h-0 flex-col overflow-hidden">
            <button
              type="button"
              class="flex items-center gap-2 text-left"
              :aria-expanded="true"
              @click="showStatement = false"
            >
              <svg
                class="size-3.5 shrink-0 rotate-90 text-faint transition-transform"
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

            <div class="mt-3 min-h-0 flex-1 overflow-y-auto">
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
        </Pane>

        <!-- Right column: editor + verdict -->
        <Pane min-size="28">
          <div class="flex h-full min-h-0 flex-col gap-2.5">
            <!-- Collapsed editor: compact tab pinned above the inner split -->
            <button
              v-if="!showCode"
              type="button"
              class="panel-tab shrink-0"
              :aria-expanded="false"
              aria-label="展开代码面板"
              @click="showCode = true"
            >
              <svg
                class="size-3.5 shrink-0 rotate-90 text-faint transition-transform"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M9 5l7 7-7 7" />
              </svg>
              <span class="text-sm font-medium text-ink">代码</span>
            </button>

            <Splitpanes
              v-if="showCode || showResult"
              horizontal
              class="min-h-0 flex-1"
              @resized="onInnerResized"
            >
              <!-- Editor -->
              <Pane
                v-if="showCode"
                :size="showResult ? layout.code : undefined"
                min-size="20"
              >
                <section class="panel flex h-full min-h-0 flex-col overflow-hidden">
                  <div class="flex flex-wrap items-center gap-2">
                    <button
                      type="button"
                      class="flex items-center gap-2 text-left"
                      :aria-expanded="true"
                      @click="showCode = false"
                    >
                      <svg
                        class="size-3.5 shrink-0 rotate-90 text-faint transition-transform"
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
                      @click="runTests"
                    >
                      {{ running ? '运行中…' : '运行' }}
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
                  </div>

                  <p class="mt-2 text-[11px] text-faint">
                    先「运行」用自测输入确认输出无误，再「提交评测」；提交后仅展示评测结论，不展示用例详情。
                  </p>

                  <div class="mt-3 min-h-[200px] flex-1 overflow-hidden rounded-md border border-line">
                    <CodeEditor
                      v-model="code"
                      :language="languageId"
                    />
                  </div>

                  <!-- Self-test: one free-form input → the program's output (no cases, no judging) -->
                  <div class="mt-3 max-h-[320px] min-h-[140px] overflow-y-auto rounded-md border border-line p-2">
                    <div class="flex flex-wrap items-center gap-2 text-[11px]">
                      <span class="font-medium text-ink">自测输入</span>
                      <span class="text-faint">LeetCode 题填 JSON 参数，ACM 题填标准输入；点「运行」直接看输出，不评判</span>
                    </div>

                    <textarea
                      v-model="runStdin"
                      rows="3"
                      spellcheck="false"
                      class="mt-2 w-full resize-y rounded border border-line bg-soft p-1.5 font-mono text-[12px] text-ink outline-none focus:border-[var(--color-accent)]"
                      placeholder="输入（stdin，可多行；可留空）"
                    />

                    <!-- Run result -->
                    <div
                      v-if="runError || runResult"
                      class="mt-2 border-t border-line pt-2"
                    >
                      <div class="flex flex-wrap items-center gap-x-2 gap-y-1 text-[11px]">
                        <span class="font-medium text-ink">输出</span>
                        <span
                          v-if="runError"
                          class="text-[#f43f5e]"
                        >{{ runError }}</span>
                        <template v-else-if="runResult">
                          <span :class="runStatusMeta(runResult.status).color">{{ runStatusMeta(runResult.status).label }}</span>
                          <span
                            v-if="runResult.timeMs != null"
                            class="text-faint"
                          >{{ runResult.timeMs }} ms</span>
                          <span
                            v-if="runResult.memoryKb != null"
                            class="text-faint"
                          >{{ Math.round(runResult.memoryKb / 1024) }} MB</span>
                          <span
                            v-if="runResult.message"
                            class="text-faint"
                          >{{ runResult.message }}</span>
                        </template>
                      </div>

                      <pre
                        v-if="runResult && runResult.stderr"
                        class="mt-1 max-h-40 overflow-auto whitespace-pre-wrap break-all font-mono text-[12px] text-[#f43f5e]"
                      >{{ runResult.stderr }}</pre>

                      <pre
                        v-if="runResult"
                        class="mt-1 min-h-[1.5rem] max-h-60 overflow-auto whitespace-pre-wrap break-all font-mono text-[12px] text-ink"
                      >{{ runResult.stdout || '（无输出）' }}</pre>
                    </div>
                  </div>
                </section>
              </Pane>

              <!-- Verdict -->
              <Pane
                v-if="showResult"
                :size="showCode ? layout.res : undefined"
                min-size="12"
              >
                <section class="panel flex h-full min-h-0 flex-col overflow-hidden">
                  <button
                    type="button"
                    class="flex items-center gap-2 text-left"
                    :aria-expanded="true"
                    @click="showResult = false"
                  >
                    <svg
                      class="size-3.5 shrink-0 rotate-90 text-faint transition-transform"
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

                  <div class="mt-3 min-h-0 flex-1 overflow-y-auto">
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

                      <div
                        v-if="failureSummary"
                        class="mt-2 text-sm font-medium text-[#f43f5e]"
                      >
                        {{ failureSummary }}
                      </div>

                      <div
                        v-if="showCaseBreakdown"
                        class="mt-2 flex flex-col gap-1"
                      >
                        <div
                          v-for="item in submission.cases"
                          :key="item.index"
                          class="flex flex-wrap items-center gap-x-2 gap-y-1 rounded border border-line px-2 py-1 text-[11px]"
                        >
                          <span class="text-faint">用例 {{ item.index + 1 }}</span>
                          <span :class="verdictMeta(item.verdict)?.color">{{ verdictMeta(item.verdict)?.label }}</span>
                          <span
                            v-if="item.timeMs"
                            class="text-faint"
                          >{{ item.timeMs }} ms</span>
                          <span
                            v-if="item.message"
                            class="text-faint"
                          >{{ item.message }}</span>
                        </div>
                      </div>
                    </template>
                  </div>
                </section>
              </Pane>
            </Splitpanes>

            <!-- Collapsed verdict: compact tab pinned below the inner split -->
            <button
              v-if="!showResult"
              type="button"
              class="panel-tab shrink-0"
              :aria-expanded="false"
              aria-label="展开评测结果面板"
              @click="showResult = true"
            >
              <svg
                class="size-3.5 shrink-0 -rotate-90 text-faint transition-transform"
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
          </div>
        </Pane>
      </Splitpanes>
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
