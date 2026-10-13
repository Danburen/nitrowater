<script setup lang="ts">
/**
 * Playground — problem list. The problem bank is public on the backend (`/api/public/problems`,
 * permitAll both at the BFF and the resource server), so browsing is anonymous; only submitting a
 * solution requires login. (If the API ever answers 401 we fall back to a login prompt.)
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../../auth/useAuth'
import { listProblems, type ProblemSummary } from './api'
import { difficultyMeta } from './labels'
import PageShell from '../../shared/ui/PageShell.vue'

const router = useRouter()
const { login } = useAuth()

const problems = ref<ProblemSummary[]>([])
const loading = ref(false)
const error = ref('')
const needsLogin = ref(false)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  needsLogin.value = false
  try {
    problems.value = await listProblems()
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e)
    if (message.includes('401')) needsLogin.value = true
    else error.value = message
  } finally {
    loading.value = false
  }
}

function open(id: number): void {
  void router.push(`/playground/${id}`)
}

onMounted(() => {
  void load()
})
</script>

<template>
  <PageShell
    title="编程演练场"
    desc="ACM 模式在线判题：浏览题目（开放），提交代码并查看评测结果（需登录）。"
  >
    <template #actions>
      <router-link
        to="/playground/submissions"
        class="inline-flex items-center gap-1 rounded-[9px] border border-line bg-card px-2.5 py-1 text-[13px] text-dim transition hover:border-line-strong hover:text-ink"
      >
        我的提交
      </router-link>
    </template>

    <div class="min-h-0 flex-1 overflow-y-auto">
      <div class="mx-auto w-full max-w-[1120px]">
        <div
          v-if="loading"
          class="py-16 text-center text-sm text-faint"
        >
          加载题目…
        </div>

        <div
          v-else-if="needsLogin"
          class="mx-auto max-w-md py-16 text-center"
        >
          <p class="text-sm text-dim">
            需要登录后才能浏览题目。
          </p>
          <button
            class="btn mt-4"
            @click="login('/playground')"
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
          v-else-if="!problems.length"
          class="py-16 text-center text-sm text-faint"
        >
          暂无可用题目。
        </div>

        <div
          v-else
          class="grid gap-3 grid-cols-[repeat(auto-fill,minmax(280px,1fr))]"
        >
          <button
            v-for="p in problems"
            :key="p.id"
            type="button"
            class="group flex flex-col rounded-[14px] border border-line bg-card p-4 text-left transition hover:border-line-strong"
            @click="open(p.id)"
          >
            <div class="flex items-center gap-2">
              <span class="font-medium text-ink">{{ p.title }}</span>
              <span
                class="ml-auto text-xs"
                :class="difficultyMeta(p.difficulty).color"
              >{{ difficultyMeta(p.difficulty).label }}</span>
            </div>
            <div class="mt-1 text-[12px] text-faint">
              #{{ p.id }} · {{ p.slug }}
            </div>
            <div class="mt-2 flex flex-wrap gap-1.5">
              <span
                v-for="set in p.sets"
                :key="`set-${set.id}`"
                class="tag"
              >{{ set.name }}</span>
              <span
                v-for="tag in p.tags"
                :key="`tag-${tag.id}`"
                class="tag"
              >{{ tag.name }}</span>
            </div>
            <div class="mt-3 flex items-center gap-3 text-[12px] text-dim">
              <span>时限 {{ p.timeLimitMs }} ms</span>
              <span>内存 {{ Math.round(p.memoryLimitKb / 1024) }} MB</span>
              <span
                v-if="p.rewardPoints > 0"
                class="text-accent"
              >+{{ p.rewardPoints }} 分</span>
            </div>
          </button>
        </div>
      </div>
    </div>
  </PageShell>
</template>
