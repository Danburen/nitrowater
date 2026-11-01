<script setup lang="ts">
/**
 * Collapsible "process" row: which skills were applied and which tools ran.
 *
 * <p>Hidden by default so the transcript reads like a normal chat; the detail stays one click away
 * for anyone who wants to audit retrieval / sandbox usage.</p>
 */
import { computed, ref } from 'vue'
import type { AiToolCall } from '../../../api/ai'

const props = defineProps<{ tools: AiToolCall[]; skills: string[] }>()

const open = ref(false)

const summary = computed(() => {
  const parts: string[] = []
  if (props.skills.length) parts.push(`${props.skills.length} 个技能`)
  if (props.tools.length) parts.push(`${props.tools.length} 次工具调用`)
  return `过程 · ${parts.join(' · ')}`
})
</script>

<template>
  <div class="mt-1.5">
    <button
      type="button"
      class="inline-flex items-center gap-1 text-[11.5px] text-faint transition hover:text-dim"
      :aria-expanded="open"
      @click="open = !open"
    >
      <svg
        class="size-3 transition-transform"
        :class="open ? 'rotate-90' : ''"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2.2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <polyline points="9 18 15 12 9 6" />
      </svg>
      {{ summary }}
    </button>

    <div
      v-if="open"
      class="mt-1.5 flex flex-wrap gap-1.5"
    >
      <span
        v-for="skill in skills"
        :key="`skill-${skill}`"
        class="tag border-accent/30 text-accent"
      >skill: {{ skill }}</span>
      <span
        v-for="(tool, index) in tools"
        :key="`tool-${index}`"
        class="tag"
        :class="tool.status && tool.status !== 'ok' ? 'border-warn/40 text-warn' : ''"
        :title="tool.summary || tool.argument"
      >
        {{ tool.name }}<template v-if="tool.status && tool.status !== 'ok'"> · {{ tool.status }}</template>
        <template v-else-if="tool.resultCount"> · {{ tool.resultCount }}</template>
      </span>
    </div>
  </div>
</template>
