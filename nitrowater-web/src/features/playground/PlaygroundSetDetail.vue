<script setup lang="ts">
/**
 * Playground — 题单详情 (problem-set detail).
 *
 * Resolves set metadata via the public endpoint for official/public sets, and falls back to the
 * owner-scoped endpoint for the caller's own PRIVATE set. Lists the set's problems and links each
 * to the problem page. "开始刷题" jumps to the first problem.
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../../auth/useAuth'
import { difficultyMeta } from './labels'
import { getMySet, getPublicSet, listSetProblems, type ProblemSetSummary } from './sets'
import type { ProblemSummary } from './api'
import PageShell from '../../shared/ui/PageShell.vue'

const route = useRoute()
const router = useRouter()
const { session } = useAuth()

const set = ref<ProblemSetSummary | null>(null)
const problems = ref<ProblemSummary[]>([])
const loading = ref(true)
const error = ref('')
const notFound = ref(false)

const id = computed(() => String(route.params.id))

/** Resolve metadata: public first (official/public sets); fall back to owner-scoped for PRIVATE. */
async function loadMeta(): Promise<ProblemSetSummary | null> {
  try {
    return await getPublicSet(id.value)
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e)
    if (message.includes('404')) {
      if (session.value?.authenticated) {
        try {
          return await getMySet(id.value)
        } catch (inner) {
          const innerMessage = inner instanceof Error ? inner.message : String(inner)
          if (innerMessage.includes('403') || innerMessage.includes('404')) {
            notFound.value = true
            return null
          }
          throw inner
        }
      }
      notFound.value = true
      return null
    }
    throw e
  }
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  notFound.value = false
  set.value = null
  problems.value = []
  try {
    const meta = await loadMeta()
    if (!meta) return
    set.value = meta
    // The public problems endpoint returns the set's enabled problems for both official and
    // user-owned sets; fall back to filtering the whole bank if that ever fails.
    try {
      problems.value = await listSetProblems(id.value)
    } catch {
      problems.value = []
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function startFirst(): void {
  const first = problems.value[0]
  if (first) void router.push(`/playground/${first.id}`)
}

const isOwner = computed(() => set.value?.ownerUid != null)

onMounted(() => {
  void load()
})

watch(id, () => {
  void load()
})
</script>

<template>
  <PageShell
    :title="set?.name || '题单详情'"
    :desc="set?.description || undefined"
  >
    <template #actions>
      <button
        v-if="problems.length"
        class="btn btn-primary"
        @click="startFirst"
      >
        开始刷题
      </button>
    </template>

    <div class="min-h-0 flex-1 overflow-y-auto">
      <div class="mx-auto w-full max-w-[960px]">
        <div
          v-if="loading"
          class="py-16 text-center text-sm text-faint"
        >
          加载题单…
        </div>

        <div
          v-else-if="notFound"
          class="py-16 text-center"
        >
          <p class="text-sm text-dim">
            题单不存在，或你没有访问权限。
          </p>
          <router-link
            to="/playground/square"
            class="btn mt-4 inline-flex"
          >
            返回题单广场
          </router-link>
        </div>

        <div
          v-else-if="error"
          class="py-16 text-center text-sm text-[#f43f5e]"
        >
          加载失败：{{ error }}
        </div>

        <template v-else-if="set">
          <div class="mb-4 flex items-center gap-2 text-[12px] text-faint">
            <router-link
              to="/playground/square"
              class="transition hover:text-ink"
            >
              题单广场
            </router-link>
            <span>/</span>
            <span class="text-dim">{{ set.name }}</span>
            <span
              v-if="isOwner"
              class="ml-auto rounded-[5px]"
              :class="set.visibility === 'PUBLIC' ? 'text-ok' : 'text-faint'"
            >
              {{ set.visibility === 'PUBLIC' ? '公开' : '私密' }}
            </span>
          </div>

          <div
            v-if="!problems.length"
            class="py-16 text-center text-sm text-faint"
          >
            该题单暂无题目。
          </div>

          <div
            v-else
            class="overflow-hidden rounded-[14px] border border-line bg-card"
          >
            <div class="flex items-center gap-2 border-b border-line px-4 py-2 text-[12px] text-faint">
              <span>题目</span>
              <span class="ml-auto">{{ problems.length }} 题</span>
            </div>
            <button
              v-for="(p, index) in problems"
              :key="p.id"
              type="button"
              class="flex w-full items-center gap-3 border-b border-line px-4 py-3 text-left transition last:border-b-0 hover:bg-soft"
              @click="router.push(`/playground/${p.id}`)"
            >
              <span class="w-6 shrink-0 text-[12px] text-faint">{{ index + 1 }}</span>
              <span class="min-w-0 flex-1 truncate text-[13.5px] text-ink">{{ p.title }}</span>
              <span
                class="shrink-0 text-[12px]"
                :class="difficultyMeta(p.difficulty).color"
              >{{ difficultyMeta(p.difficulty).label }}</span>
            </button>
          </div>
        </template>
      </div>
    </div>
  </PageShell>
</template>
