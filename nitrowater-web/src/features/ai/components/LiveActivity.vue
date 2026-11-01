<script setup lang="ts">
/**
 * Live activity card shown while an agent turn is running.
 *
 * <p>Mirrors the reference sample: a spinner plus the current phase ("查阅文档" / "联网搜索" /
 * "运行代码" / "编写代码"), driven by {@code step} events from the backend. "Writing code" is
 * derived from the streamed text (an unclosed fence) because no tool event exists for it.</p>
 */
import { computed } from 'vue'
import type { ChatActivity } from '../types'

const props = defineProps<{
  activity: ChatActivity | null
}>()

const TOOL_LABELS: Readonly<Record<string, string>> = {
  searchDocs: '查阅文档',
  webSearch: '联网搜索',
  runCode: '运行代码',
  loadSkill: '加载技能',
}

const text = computed(() => {
  const activity = props.activity
  if (!activity || activity.phase === 'thinking') return '正在思考…'
  const label = TOOL_LABELS[activity.tool ?? ''] ?? activity.tool ?? '工具'
  return activity.phase === 'tool_start' ? `正在${label}…` : `已完成${label}`
})
</script>

<template>
  <div
    class="live-card"
    role="status"
    aria-live="polite"
  >
    <svg
      class="live-icon"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      aria-hidden="true"
    >
      <path d="M21 12a9 9 0 1 1-6.2-8.6" />
    </svg>
    <span class="live-text">{{ text }}</span>
  </div>
</template>

<style scoped>
.live-card {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding: 6px 10px;
  border-radius: 9px;
  background: color-mix(in srgb, var(--color-accent) 7%, transparent);
  font-size: 12.5px;
  color: var(--color-dim);
}

.live-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  color: var(--color-accent);
  animation: live-spin 1.4s linear infinite;
}

@keyframes live-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .live-icon {
    animation: none;
  }
}
</style>
