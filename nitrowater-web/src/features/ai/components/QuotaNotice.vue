<script setup lang="ts">
/**
 * Daily-quota notice shown directly above the composer.
 *
 * <p>It is deliberately not attached to a transcript message: the conversation is persisted
 * server-side, so a per-message notice would disappear on reload while the budget is still
 * relevant. The card is presentational; the page decides whether the warning or the exhausted
 * state applies (and whether the once-per-day warning was already dismissed).</p>
 */
import { computed } from 'vue'
import type { AiDailyUsage } from '../../../api/ai'

const props = defineProps<{
  usage: AiDailyUsage | null
  state: 'hidden' | 'warning' | 'exhausted'
}>()

const emit = defineEmits<{
  (e: 'dismiss'): void
}>()

/** Used share of the daily budget, clamped to 0–100. */
const percent = computed(() => {
  const usage = props.usage
  if (!usage || usage.dailyTokenLimit <= 0) return 0
  return Math.min(100, Math.round((usage.totalTokens / usage.dailyTokenLimit) * 100))
})

/** Tokens left today; reservations of in-flight turns are excluded from the display value. */
const remaining = computed(() => {
  const usage = props.usage
  if (!usage) return 0
  return Math.max(0, usage.dailyTokenLimit - usage.totalTokens)
})

function formatTokens(value: number): string {
  return value.toLocaleString('zh-CN')
}

function dismiss(): void {
  emit('dismiss')
}
</script>

<template>
  <div
    v-if="state !== 'hidden'"
    class="mx-auto w-full max-w-[780px] shrink-0 px-3 pb-1 sm:px-4"
    role="status"
    aria-live="polite"
  >
    <div
      class="rounded-[14px] border px-4 py-3"
      :class="state === 'exhausted'
        ? 'border-danger/30 bg-danger/10'
        : 'border-warn/30 bg-warn/10'"
    >
      <div class="flex items-start gap-2.5">
        <svg
          class="mt-0.5 size-4 shrink-0"
          :class="state === 'exhausted' ? 'text-danger' : 'text-warn'"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.9"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path d="M12 9v4" />
          <path d="M12 17h.01" />
          <path d="M10.3 3.9 2.6 17.2A2 2 0 0 0 4.3 20h15.4a2 2 0 0 0 1.7-2.8L13.7 3.9a2 2 0 0 0-3.4 0Z" />
        </svg>

        <div class="min-w-0 flex-1">
          <p class="text-[13px] font-semibold text-ink">
            {{ state === 'exhausted' ? '今日额度已用完' : '今日额度即将用完' }}
          </p>
          <p class="mt-1 text-[12.5px] leading-relaxed text-dim">
            <template v-if="state === 'exhausted'">
              为保证所有用户都能稳定使用，每日额度会自动重置。明天再来试试吧。
            </template>
            <template v-else>
              已使用 {{ percent }}%，剩余约 {{ formatTokens(remaining) }} tokens。
            </template>
          </p>

          <div
            v-if="state === 'warning'"
            class="mt-2 flex items-center gap-2"
          >
            <div
              class="h-1 min-w-12 flex-1 overflow-hidden rounded-full bg-soft"
              role="progressbar"
              :aria-valuenow="percent"
              aria-valuemin="0"
              aria-valuemax="100"
              aria-label="今日 token 使用率"
            >
              <div
                class="h-full rounded-full bg-warn"
                :style="{ width: `${percent}%` }"
              />
            </div>
            <span class="shrink-0 font-mono text-[11px] text-faint">{{ percent }}%</span>
          </div>

          <div class="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1.5">
            <router-link
              to="/about#ai-quota"
              class="text-[12px] font-medium text-accent transition hover:text-accent-strong hover:underline"
            >
              为什么限制额度？
            </router-link>
            <span class="text-[11.5px] text-faint">
              每天 UTC 0 点（北京时间 08:00）重置
            </span>
            <button
              v-if="state === 'warning'"
              type="button"
              class="ml-auto text-[11.5px] text-dim transition hover:text-ink"
              @click="dismiss"
            >
              知道了
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
