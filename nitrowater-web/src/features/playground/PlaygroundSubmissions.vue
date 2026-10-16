<script setup lang="ts">
/**
 * Playground — my submissions.
 *
 * Lists the signed-in user's judging history (newest first) and lets each row expand into the
 * per-case breakdown. The list endpoint is login-gated on the backend, so a 401 falls back to a
 * login prompt. Problem titles are enriched from the public problem bank (best-effort).
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../../auth/useAuth'
import { RUNNER_LANGUAGES } from '../tools/code-runner/langCatalog'
import { listProblems, listSubmissions, type ProblemSummary, type Submission } from './api'
import { statusLabel, verdictMeta } from './labels'
import PageShell from '../../shared/ui/PageShell.vue'

const router = useRouter()
const { login } = useAuth()

const submissions = ref<Submission[]>([])
const problemsById = ref<Record<number, ProblemSummary>>({})
const loading = ref(false)
const error = ref('')
const needsLogin = ref(false)
const expanded = ref<Set<number>>(new Set())

function languageLabel(id: string): string {
  return RUNNER_LANGUAGES.find((item) => item.id === id)?.label ?? id
}

function problemTitle(id: number): string {
  const problem = problemsById.value[id]
  return problem ? `#${problem.id} ${problem.title}` : `#${id}`
}

function formatTime(iso: string | null): string {
  if (!iso) return '-'
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString()
}

function toggle(id: number): void {
  const next = new Set(expanded.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expanded.value = next
}

function openProblem(id: number): void {
  void router.push(`/playground/${id}`)
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  needsLogin.value = false
  try {
    // Problem bank is public (best-effort title enrichment); the submission list needs login.
    problemsById.value = Object.fromEntries((await listProblems()).map((p) => [p.id, p]))
    submissions.value = await listSubmissions()
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e)
    if (message.includes('401')) needsLogin.value = true
    else error.value = message
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <PageShell
    title="我的提交"
    desc="当前账号的判题记录（最新在前）；点击某行可展开每个测试用例的结果。"
    content-width="1120px"
  >
    <div class="min-h-0 flex-1 overflow-y-auto">
      <div class="mx-auto w-full max-w-[1120px]">
        <div
          v-if="loading"
          class="py-16 text-center text-sm text-faint"
        >
          加载提交记录…
        </div>

        <div
          v-else-if="needsLogin"
          class="mx-auto max-w-md py-16 text-center"
        >
          <p class="text-sm text-dim">
            需要登录后才能查看提交记录。
          </p>
          <button
            class="btn mt-4"
            @click="login('/playground/submissions')"
          >
            登录
          </button>
        </div>

        <div
          v-else-if="error"
          class="py-16 text-center text-sm text-[#f43f5e]"
        >
          加载失败：{{ error }}
        </div>

        <div
          v-else-if="!submissions.length"
          class="py-16 text-center text-sm text-faint"
        >
          还没有提交记录，去
          <router-link
            to="/playground"
            class="text-accent"
          >
            题目列表
          </router-link>
          提交一题吧。
        </div>

        <table
          v-else
          class="w-full border-collapse text-left text-sm"
        >
          <thead>
            <tr class="text-[11px] text-faint">
              <th class="border-b border-line py-2 pr-3 font-normal">
                题目
              </th>
              <th class="border-b border-line py-2 pr-3 font-normal">
                语言
              </th>
              <th class="border-b border-line py-2 pr-3 font-normal">
                结果
              </th>
              <th class="border-b border-line py-2 pr-3 font-normal">
                通过
              </th>
              <th class="border-b border-line py-2 pr-3 font-normal">
                得分
              </th>
              <th class="border-b border-line py-2 pr-3 font-normal">
                用时 / 内存
              </th>
              <th class="border-b border-line py-2 pr-3 font-normal">
                提交时间
              </th>
              <th class="border-b border-line py-2 font-normal" />
            </tr>
          </thead>
          <tbody>
            <template
              v-for="s in submissions"
              :key="s.id"
            >
              <tr class="border-b border-line align-middle hover:bg-soft">
                <td class="py-2 pr-3">
                  <button
                    type="button"
                    class="text-left text-ink transition hover:text-accent"
                    @click="openProblem(s.problemId)"
                  >
                    {{ problemTitle(s.problemId) }}
                  </button>
                </td>
                <td class="py-2 pr-3 text-dim">
                  {{ languageLabel(s.language) }}
                </td>
                <td class="py-2 pr-3">
                  <span
                    class="font-medium"
                    :class="verdictMeta(s.verdict)?.color"
                  >{{ verdictMeta(s.verdict)?.label ?? statusLabel(s.status) }}</span>
                </td>
                <td class="py-2 pr-3 text-dim">
                  {{ s.passed }}/{{ s.total }}
                </td>
                <td class="py-2 pr-3 text-dim">
                  {{ s.score }}
                </td>
                <td class="py-2 pr-3 text-dim">
                  <template v-if="s.status === 'DONE'">
                    <template v-if="s.timeMs != null">
                      {{ s.timeMs }} ms
                    </template>
                    <template v-if="s.memoryKb != null">
                      · {{ Math.round(s.memoryKb / 1024) }} MB
                    </template>
                  </template>
                  <template v-else>
                    -
                  </template>
                </td>
                <td class="py-2 pr-3 text-faint">
                  {{ formatTime(s.createdAt) }}
                </td>
                <td class="py-2 text-right">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 rounded-[8px] border border-line bg-card px-2 py-0.5 text-[12px] text-dim transition hover:border-line-strong hover:text-ink"
                    :aria-expanded="expanded.has(s.id)"
                    @click="toggle(s.id)"
                  >
                    <svg
                      class="size-3 shrink-0 transition-transform"
                      :class="expanded.has(s.id) ? 'rotate-90' : ''"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    >
                      <path d="M9 5l7 7-7 7" />
                    </svg>
                    详情
                  </button>
                </td>
              </tr>

              <tr
                v-if="expanded.has(s.id)"
                class="border-b border-line"
              >
                <td
                  colspan="8"
                  class="bg-soft px-3 py-3"
                >
                  <div
                    v-if="s.compileMessage"
                    class="mb-2 whitespace-pre-wrap break-all font-mono text-[12px] text-[#f43f5e]"
                  >
                    {{ s.compileMessage }}
                  </div>

                  <table
                    v-if="s.cases.length"
                    class="w-full text-left"
                  >
                    <thead>
                      <tr class="text-[11px] text-faint">
                        <th class="py-1 pr-3 font-normal">
                          #
                        </th>
                        <th class="py-1 pr-3 font-normal">
                          结果
                        </th>
                        <th class="py-1 pr-3 font-normal">
                          用时
                        </th>
                        <th class="py-1 pr-3 font-normal">
                          内存
                        </th>
                        <th class="py-1 font-normal">
                          备注
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr
                        v-for="c in s.cases"
                        :key="c.index"
                        class="border-t border-line text-[12px]"
                      >
                        <td class="py-1 pr-3 text-dim">
                          {{ c.index + 1 }}
                        </td>
                        <td
                          class="py-1 pr-3 font-medium"
                          :class="verdictMeta(c.verdict)?.color"
                        >
                          {{ verdictMeta(c.verdict)?.label }}
                        </td>
                        <td class="py-1 pr-3 text-dim">
                          {{ c.timeMs }} ms
                        </td>
                        <td class="py-1 pr-3 text-dim">
                          {{ Math.round(c.memoryKb / 1024) }} MB
                        </td>
                        <td class="py-1 text-faint">
                          {{ c.message || '-' }}
                        </td>
                      </tr>
                    </tbody>
                  </table>
                  <div
                    v-else
                    class="text-xs text-faint"
                  >
                    暂无逐用例结果。
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </div>
  </PageShell>
</template>
