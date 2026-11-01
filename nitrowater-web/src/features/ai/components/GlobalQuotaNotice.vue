<script setup lang="ts">
/**
 * Platform-wide quota notice.
 *
 * <p>Separate from {@link QuotaNotice} (which is per-user) because the explanation differs: the
 * site-wide ceiling is a maintainer/budget decision, not the user's own consumption. Both share the
 * same placement (above the composer) so the composer never looks available when it is not.</p>
 */
import { computed } from 'vue'
import type { AiGlobalQuota } from '../../../api/ai'

const props = defineProps<{
  quota: AiGlobalQuota | null
}>()

const percent = computed(() => {
  const quota = props.quota
  if (!quota || quota.limit <= 0) return 100
  return Math.min(100, Math.round((quota.used / quota.limit) * 100))
})

function formatTokens(value: number): string {
  return value.toLocaleString('zh-CN')
}
</script>

<template>
  <div
    class="mx-auto w-full max-w-[780px] shrink-0 px-3 pb-1 sm:px-4"
    role="status"
    aria-live="polite"
  >
    <div class="rounded-[14px] border border-danger/30 bg-danger/10 px-4 py-3">
      <div class="flex items-start gap-2.5">
        <svg
          class="mt-0.5 size-4 shrink-0 text-danger"
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
            全站今日 AI 额度已用完
          </p>
          <p class="mt-1 text-[12.5px] leading-relaxed text-dim">
            NitroWater 是开源项目，模型与向量检索按量计费、由个人出资维护，因此设有每日全站上限。
            额度用完后暂时无法提问，<strong class="font-medium text-ink">历史会话与 / 指令仍然可用</strong>；
            额度会在每天 08:00（北京时间）重置。
          </p>

          <div class="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1.5">
            <router-link
              to="/about#ai-quota"
              class="text-[12px] font-medium text-accent transition hover:text-accent-strong hover:underline"
            >
              查看额度说明与用量
            </router-link>
            <span class="text-[11.5px] text-faint">
              今日已用 {{ formatTokens(quota?.used ?? 0) }} / {{ formatTokens(quota?.limit ?? 0) }} tokens（{{ percent }}%）
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
