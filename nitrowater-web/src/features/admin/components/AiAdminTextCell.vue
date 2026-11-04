<script setup lang="ts">
/**
 * One-line table cell for long prose (tool description, skill trigger phrase).
 *
 * <p>The cell stays one line so the table keeps its width; hovering opens the full text in a
 * popover, which wraps at a readable measure instead of the browser's narrow native tooltip.
 * The short show delay matters: without it, sweeping the cursor across the table pops a card
 * on every row it passes over.</p>
 */
defineProps<{
  /** Full text; blank renders an em dash instead of an empty cell. */
  text?: string | null
}>()
</script>

<template>
  <el-popover
    v-if="text"
    trigger="hover"
    placement="top-start"
    :width="340"
    :show-after="250"
  >
    <template #reference>
      <span
        class="block cursor-help truncate decoration-dotted underline-offset-2 hover:underline"
      >
        {{ text }}
      </span>
    </template>
    <p class="m-0 max-h-60 overflow-auto whitespace-pre-wrap break-words text-[13px] leading-relaxed">
      {{ text }}
    </p>
  </el-popover>
  <span
    v-else
    class="text-dim"
  >—</span>
</template>
