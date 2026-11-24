<script setup lang="ts">
/**
 * Shared page body shell for tool views.
 *
 * <p>The title bar now lives in the global top bar: `title` / `desc` are teleported into the
 * shell's `#tools-topbar-title` slot so the tool pages no longer render a second header. The
 * `actions` slot is intentionally gone — no tool uses it, and page actions belong in the body.</p>
 *
 * <p>With `fill` the body fills the available height and never scrolls the page (inner panels
 * scroll); otherwise the body scrolls internally.</p>
 */
import UiIcon from './UiIcon.vue'
import UiTooltip from './UiTooltip.vue'

withDefaults(
  defineProps<{
    title: string
    desc?: string
    fill?: boolean
  }>(),
  { desc: undefined },
)
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <Teleport to="#tools-topbar-title">
      <h1 class="min-w-0 truncate text-sm font-medium text-ink">
        {{ title }}
      </h1>
      <UiTooltip
        v-if="desc"
        :text="desc"
        class="topbar-tip"
      >
        <button
          type="button"
          class="inline-flex size-6 shrink-0 items-center justify-center rounded-[6px] text-faint transition hover:text-ink"
          aria-label="页面说明"
        >
          <UiIcon
            name="info"
            :size="14"
          />
        </button>
      </UiTooltip>
    </Teleport>

    <div
      v-if="fill"
      class="flex min-h-0 flex-1 flex-col overflow-hidden p-4"
    >
      <slot />
    </div>
    <div
      v-else
      class="min-h-0 flex-1 overflow-y-auto p-4 sm:p-6"
    >
      <slot />
    </div>
  </div>
</template>

<style scoped>
/* The info bubble is teleported into the narrow top bar, so anchor it to the icon's right edge. */
.topbar-tip :deep(.tooltip-bubble) {
  right: 0;
  left: auto;
}
</style>
