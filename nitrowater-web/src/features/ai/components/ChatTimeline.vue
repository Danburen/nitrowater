<script setup lang="ts">
/**
 * Right-side timeline (one dot per user message).
 *
 * <p>A minimap for long transcripts: hover/focus shows the question, click scrolls to it. The rail
 * is height-capped and scrolls on its own, so a 200-message conversation never overflows the page.
 * Hidden below `lg` to keep the reading column wide on small screens.</p>
 */
import { ref } from 'vue'
import type { TimelineItem } from '../types'

defineProps<{
  items: readonly TimelineItem[]
  activeId: string | null
}>()

const emit = defineEmits<{
  (e: 'select', id: string): void
}>()

const hovered = ref<TimelineItem | null>(null)
</script>

<template>
  <div
    v-if="items.length > 1"
    class="absolute right-2 top-1/2 z-10 hidden -translate-y-1/2 lg:block"
  >
    <div class="timeline-rail scroll-thin">
      <button
        v-for="item in items"
        :key="item.id"
        type="button"
        class="timeline-dot"
        :class="{ 'is-active': item.id === activeId }"
        :aria-label="`跳转到：${item.label}`"
        :aria-current="item.id === activeId ? 'true' : undefined"
        @mouseenter="hovered = item"
        @mouseleave="hovered = null"
        @focus="hovered = item"
        @blur="hovered = null"
        @click="emit('select', item.id)"
      />
    </div>

    <p
      v-if="hovered"
      class="timeline-label"
      role="tooltip"
    >
      {{ hovered.label }}
    </p>
  </div>
</template>

<style scoped>
.timeline-rail {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: min(60vh, 520px);
  overflow-y: auto;
  padding: 6px 5px;
  border-radius: 999px;
}

.timeline-dot {
  width: 9px;
  height: 9px;
  flex-shrink: 0;
  border-radius: 999px;
  background: var(--color-line-strong);
  transition: transform 0.15s ease, background 0.15s ease;
}

.timeline-dot:hover,
.timeline-dot:focus-visible,
.timeline-dot.is-active {
  background: var(--color-accent);
  transform: scale(1.5);
}

.timeline-label {
  position: absolute;
  top: 50%;
  right: calc(100% + 8px);
  max-width: 220px;
  padding: 4px 9px;
  transform: translateY(-50%);
  overflow: hidden;
  border-radius: 8px;
  background: var(--color-ink);
  color: var(--color-bg);
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
  pointer-events: none;
}
</style>
