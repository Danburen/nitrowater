<script setup lang="ts">
/**
 * Lightweight tooltip for user-facing pages (no Element Plus dependency).
 *
 * <p>Hover and keyboard focus both reveal it; the bubble is CSS-only, so there is no portal or
 * positioning script. Keep the text short — the bubble clamps to `min(280px, 70vw)`.</p>
 */
withDefaults(defineProps<{ text: string; placement?: 'bottom' | 'top' | 'right' }>(), {
  placement: 'bottom',
})
</script>

<template>
  <span class="tooltip-wrap">
    <slot />
    <span
      class="tooltip-bubble"
      :class="`is-${placement}`"
      role="tooltip"
    >{{ text }}</span>
  </span>
</template>

<style scoped>
.tooltip-wrap {
  position: relative;
  display: inline-flex;
}

.tooltip-bubble {
  position: absolute;
  z-index: 30;
  width: max-content;
  max-width: min(280px, 70vw);
  padding: 6px 9px;
  border-radius: 8px;
  background: var(--color-ink);
  color: var(--color-bg);
  font-size: 12px;
  line-height: 1.45;
  opacity: 0;
  visibility: hidden;
  transform: translateY(2px);
  transition: opacity 0.12s ease, transform 0.12s ease;
  pointer-events: none;
}

.tooltip-wrap:hover .tooltip-bubble,
.tooltip-wrap:has(:focus-visible) .tooltip-bubble {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}

.is-bottom {
  top: calc(100% + 6px);
  left: 0;
}

.is-top {
  bottom: calc(100% + 6px);
  left: 0;
}

.is-right {
  top: 50%;
  left: calc(100% + 6px);
  transform: translateY(-50%);
}
</style>
