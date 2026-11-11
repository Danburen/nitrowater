<script setup lang="ts">
/**
 * Collapsible "which skills were applied" row.
 *
 * <p>Tool calls are not listed here anymore: they render as the process timeline above the answer
 * (live cards while the turn runs, rebuilt from {@code tools_json} once it settles), so a second
 * list of chips would only duplicate it. Skills have no card of their own, so they keep this row.</p>
 */
import { computed, ref } from 'vue'

const props = defineProps<{ skills: string[] }>()

const open = ref(false)

const summary = computed(() => `技能 · ${props.skills.length} 个`)
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
        <polyline
          points="9 18 15 12 9 6"
        />
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
    </div>
  </div>
</template>
