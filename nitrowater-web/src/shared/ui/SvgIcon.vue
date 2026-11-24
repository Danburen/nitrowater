<script setup lang="ts">
/**
 * Renders an icon stored as a real .svg file under src/assets/icons.
 *
 * <p>Files are imported with `?raw`, so their markup ships in the JS bundle (no extra request) and
 * `currentColor` keeps following the surrounding text colour in both themes. Keep every glyph in
 * that directory instead of pasting <svg> blocks into templates.</p>
 *
 * <p>Data-driven glyphs (catalogue icons chosen at runtime) use PathIcon instead.</p>
 */
import { computed } from 'vue'

const files = import.meta.glob('../../assets/icons/*.svg', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>

const byName = new Map(
  Object.entries(files).map(([file, svg]) => [file.split('/').pop()!.replace(/\.svg$/, ''), svg]),
)

const props = withDefaults(defineProps<{ name: string; size?: number }>(), { size: 16 })
const markup = computed(() => byName.get(props.name) ?? '')
const box = computed(() => ({ width: `${props.size}px`, height: `${props.size}px` }))
</script>

<template>
  <!-- eslint-disable vue/no-v-html -- markup comes from local build-time assets -->
  <span
    class="svg-icon"
    :style="box"
    aria-hidden="true"
    v-html="markup"
  />
  <!-- eslint-enable vue/no-v-html -->
</template>

<style scoped>
.svg-icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  vertical-align: middle;
}

.svg-icon :deep(svg) {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
