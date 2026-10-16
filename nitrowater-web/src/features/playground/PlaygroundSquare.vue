<script setup lang="ts">
/**
 * Playground — 题单广场 (problem-set square).
 *
 * Anonymous browse: official curated sets ("官方题单") plus public user-shared sets ("大家分享的题单").
 * Clicking a card opens the set detail. No login required.
 */
import { onMounted, ref } from 'vue'
import { listOfficialSets, listSquareSets, type ProblemSetSummary } from './sets'
import PageShell from '../../shared/ui/PageShell.vue'

const official = ref<ProblemSetSummary[]>([])
const shared = ref<ProblemSetSummary[]>([])
const loading = ref(true)
const error = ref('')

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [o, s] = await Promise.all([listOfficialSets(), listSquareSets()])
    official.value = o
    shared.value = s
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
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
    title="题单广场"
    desc="官方精选题单与大家公开分享的题单，选一份开始刷题。"
    content-width="1120px"
  >
    <template #actions>
      <router-link
        to="/playground/sets"
        class="inline-flex items-center gap-1 rounded-[9px] border border-line bg-card px-2.5 py-1 text-[13px] text-dim transition hover:border-line-strong hover:text-ink"
      >
        我的题单
      </router-link>
    </template>

    <div class="min-h-0 flex-1 overflow-y-auto">
      <div class="mx-auto w-full max-w-[1120px]">
        <div
          v-if="loading"
          class="py-16 text-center text-sm text-faint"
        >
          加载题单…
        </div>

        <div
          v-else-if="error"
          class="py-16 text-center text-sm text-[#f43f5e]"
        >
          加载失败：{{ error }}
        </div>

        <template v-else>
          <section class="mb-8">
            <div class="section-head">
              <h2 class="text-sm font-semibold text-ink">
                官方题单
              </h2>
              <span class="text-[12px] text-faint">{{ official.length }} 份</span>
            </div>
            <div
              v-if="!official.length"
              class="py-10 text-center text-sm text-faint"
            >
              暂无官方题单。
            </div>
            <div
              v-else
              class="grid gap-3 grid-cols-[repeat(auto-fill,minmax(280px,1fr))]"
            >
              <router-link
                v-for="set in official"
                :key="set.id"
                :to="`/playground/sets/${set.id}`"
                class="flex flex-col rounded-[14px] border border-line bg-card p-4 transition hover:border-line-strong"
              >
                <div class="flex items-center gap-2">
                  <span class="truncate font-medium text-ink">{{ set.name }}</span>
                  <span class="ml-auto shrink-0 rounded-[5px] bg-soft px-1.5 py-0.5 text-[11px] text-faint">官方</span>
                </div>
                <div class="mt-1 text-[12px] text-faint">
                  {{ set.problemCount }} 题
                </div>
                <p
                  v-if="set.description"
                  class="mt-2 line-clamp-2 text-[12.5px] text-dim"
                >
                  {{ set.description }}
                </p>
              </router-link>
            </div>
          </section>

          <section>
            <div class="section-head">
              <h2 class="text-sm font-semibold text-ink">
                大家分享的题单
              </h2>
              <span class="text-[12px] text-faint">{{ shared.length }} 份</span>
            </div>
            <div
              v-if="!shared.length"
              class="py-10 text-center text-sm text-faint"
            >
              还没有公开分享的题单。
            </div>
            <div
              v-else
              class="grid gap-3 grid-cols-[repeat(auto-fill,minmax(280px,1fr))]"
            >
              <router-link
                v-for="set in shared"
                :key="set.id"
                :to="`/playground/sets/${set.id}`"
                class="flex flex-col rounded-[14px] border border-line bg-card p-4 transition hover:border-line-strong"
              >
                <div class="flex items-center gap-2">
                  <span class="truncate font-medium text-ink">{{ set.name }}</span>
                  <span class="ml-auto shrink-0 rounded-[5px] px-1.5 py-0.5 text-[11px] text-ok">公开</span>
                </div>
                <div class="mt-1 text-[12px] text-faint">
                  {{ set.problemCount }} 题
                </div>
                <p
                  v-if="set.description"
                  class="mt-2 line-clamp-2 text-[12.5px] text-dim"
                >
                  {{ set.description }}
                </p>
              </router-link>
            </div>
          </section>
        </template>
      </div>
    </div>
  </PageShell>
</template>
