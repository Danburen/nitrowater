<script setup lang="ts">
/**
 * Standalone playground page shell.
 *
 * The playground is a top-level route (not under the `/tools` sidebar layout), so it renders
 * below the global `AppHeader`. The shell fills the viewport minus the header height
 * (`--app-header-h`) and never scrolls the page — inner panels own their own scroll, so the
 * editor/statement always absorb the available space with no bottom gap.
 */
defineProps<{
  title: string
  desc?: string
  /** Optional in-app path for a leading back link (e.g. the problem list). */
  backTo?: string
  backLabel?: string
}>()
</script>

<template>
  <div class="flex h-[calc(100dvh-var(--app-header-h))] flex-col overflow-hidden bg-bg text-ink">
    <header class="flex shrink-0 items-center gap-2.5 border-b border-line px-4 py-2.5 sm:px-5 sm:py-3">
      <router-link
        v-if="backTo"
        :to="backTo"
        class="inline-flex shrink-0 items-center gap-1 rounded-[9px] border border-line bg-card px-2.5 py-1 text-[13px] text-dim transition hover:border-line-strong hover:text-ink"
        :title="backLabel || '返回'"
        :aria-label="backLabel || '返回'"
      >
        <svg
          class="size-3.5 shrink-0"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M19 12H5M12 19l-7-7 7-7" />
        </svg>
        {{ backLabel || '返回' }}
      </router-link>
      <h1 class="min-w-0 truncate text-base font-semibold tracking-tight text-ink">
        {{ title }}
      </h1>
      <el-tooltip
        v-if="desc"
        :content="desc"
        placement="bottom-start"
        :show-after="150"
      >
        <button
          type="button"
          class="grid size-6 shrink-0 place-items-center rounded-full text-faint transition hover:bg-soft hover:text-ink"
          aria-label="说明"
        >
          <svg
            class="size-4"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <circle
              cx="12"
              cy="12"
              r="9"
            />
            <path d="M12 16v-4M12 8h.01" />
          </svg>
        </button>
      </el-tooltip>
      <div class="ml-auto flex shrink-0 items-center gap-2">
        <slot name="actions" />
      </div>
    </header>

    <div class="flex min-h-0 flex-1 flex-col overflow-hidden p-3 sm:p-4">
      <slot />
    </div>
  </div>
</template>
