<script setup lang="ts">
/**
 * Stroke icon whose path data comes from a runtime catalogue (plugins, tools, agent tools).
 *
 * <p>Fixed glyphs live as .svg files and render through SvgIcon; this component exists only for
 * icons whose `d` string is data, so the SVG wrapper stays in one place.</p>
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    path: string | readonly string[]
    size?: number
    strokeWidth?: number
  }>(),
  { size: 16, strokeWidth: 1.8 },
)

const paths = computed(() => (typeof props.path === 'string' ? [props.path] : props.path))
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    class="shrink-0"
    aria-hidden="true"
  >
    <path
      v-for="(d, index) in paths"
      :key="index"
      :d="d"
    />
  </svg>
</template>
