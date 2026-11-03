<script setup lang="ts">
/**
 * First-run state: product intro plus a handful of randomly picked starter prompts.
 *
 * <p>The catalog lives in {@code suggestions.json} so non-developers can add capabilities without
 * touching the component; each mount (i.e. each new conversation) draws a different subset, which
 * keeps the empty state from looking frozen. Picking a card only fills the composer — it does not
 * spend a model call — so the user can still adjust the question first.</p>
 */
import { computed } from 'vue'
import suggestions from '../suggestions.json'

defineProps<{ authenticated: boolean }>()

const emit = defineEmits<{
  (e: 'pick', prompt: string): void
  (e: 'login'): void
}>()

interface Suggestion {
  icon: string
  title: string
  desc: string
  prompt: string
}

interface StarterCard extends Suggestion {
  paths: string[]
}

/** How many cards the empty state shows. */
const CARD_COUNT = 4

/** Stroke icons keyed by the `icon` field in suggestions.json; unknown keys fall back to `code`. */
const ICONS: Record<string, string[]> = {
  code: ['M16 18l6-6-6-6', 'M8 6l-6 6 6 6'],
  bug: ['M8 2l1.5 2', 'M16 2l-1.5 2', 'M12 6a4 4 0 0 1 4 4v4a4 4 0 0 1-8 0v-4a4 4 0 0 1 4-4z', 'M4 10h2', 'M18 10h2', 'M4 16h2', 'M18 16h2'],
  bulb: ['M9 18h6', 'M10 22h4', 'M12 2a7 7 0 0 0-4 12.7V17h8v-2.3A7 7 0 0 0 12 2z'],
  translate: ['M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z', 'M2 12h20', 'M12 2c2.8 3.4 2.8 16.6 0 20', 'M12 2c-2.8 3.4-2.8 16.6 0 20'],
  book: ['M4 19.5A2.5 2.5 0 0 1 6.5 17H20', 'M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z'],
  terminal: ['M4 17l6-6-6-6', 'M12 19h8'],
  server: ['M4 4h16v6H4z', 'M4 14h16v6H4z', 'M8 7h.01', 'M8 17h.01'],
  plugin: ['M9 3v4', 'M15 3v4', 'M5 7h14v6a5 5 0 0 1-5 5h-4a5 5 0 0 1-5-5z'],
  chart: ['M4 20v-6', 'M10 20V8', 'M16 20v-10', 'M2 20h20'],
  wrench: ['M3 21l6-6', 'M14 4l6 6-8 8H6v-6z'],
  file: ['M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z', 'M14 2v6h6'],
  globe: ['M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z', 'M2 12h20', 'M12 2c2.8 3.4 2.8 16.6 0 20', 'M12 2c-2.8 3.4-2.8 16.6 0 20'],
}

/** Draws a uniformly random, order-preserving-free subset (partial Fisher-Yates). */
function drawCards(): StarterCard[] {
  const pool = [...(suggestions as Suggestion[])]
  const count = Math.min(CARD_COUNT, pool.length)
  for (let index = 0; index < count; index += 1) {
    const swap = index + Math.floor(Math.random() * (pool.length - index))
    const picked = pool[swap]
    pool[swap] = pool[index]
    pool[index] = picked
  }
  return pool.slice(0, count).map((item) => ({
    ...item,
    paths: ICONS[item.icon] ?? ICONS.code,
  }))
}

// Computed without reactive dependencies: drawn once per mount, so a fresh conversation reshuffles.
const cards = computed(drawCards)
</script>

<template>
  <div class="flex flex-col items-center px-2 py-12 text-center sm:py-16">
    <span class="mb-5 grid size-14 place-items-center rounded-[16px] bg-gradient-to-br from-accent to-accent-2 text-[22px] font-extrabold text-white shadow-lg">N</span>
    <h1 class="text-[24px] font-bold tracking-tight text-ink">
      你好，我是 NitroBot
    </h1>
    <p class="mt-2 max-w-[420px] text-[14px] leading-relaxed text-dim">
      通用能力助手 · 写作、编程、分析、翻译，也能查 NitroWater 与 VeloChatX 的站点文档。
    </p>

    <button
      v-if="!authenticated"
      type="button"
      class="btn btn-primary mt-6 px-5 py-2"
      @click="emit('login')"
    >
      登录后开始对话
    </button>

    <div
      v-else
      class="mt-8 grid w-full max-w-[560px] grid-cols-1 gap-2.5 sm:grid-cols-2"
    >
      <button
        v-for="card in cards"
        :key="card.title"
        type="button"
        class="flex flex-col items-start gap-1 rounded-[14px] border border-line bg-card px-4 py-3.5 text-left transition hover:-translate-y-px hover:border-accent/50 hover:shadow-md"
        @click="emit('pick', card.prompt)"
      >
        <svg
          class="mb-1 size-4 text-accent"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.7"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path
            v-for="(path, index) in card.paths"
            :key="index"
            :d="path"
          />
        </svg>
        <span class="text-[13.5px] font-semibold text-ink">{{ card.title }}</span>
        <span class="text-[12px] leading-snug text-faint">{{ card.desc }}</span>
      </button>
    </div>
  </div>
</template>
