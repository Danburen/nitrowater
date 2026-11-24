<script setup lang="ts">
/**
 * Icon rendered from a real SVG file under public/icons.
 *
 * <p>Public assets are served as static files, so the glyph is applied as a CSS mask and painted
 * with currentColor. This keeps every glyph in a managed .svg file — no path data or inline <svg>
 * markup in source — while still following the active theme.</p>
 */
import { computed } from 'vue'

const props = withDefaults(defineProps<{ name: string; size?: number }>(), { size: 16 })

const url = computed(() => `url(/icons/${props.name}.svg)`)
const box = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  maskImage: url.value,
  WebkitMaskImage: url.value,
}))
</script>

<template>
  <span
    class="public-icon"
    :style="box"
    aria-hidden="true"
  />
</template>

<style scoped>
.public-icon {
  display: inline-block;
  flex-shrink: 0;
  background-color: currentColor;
  mask-repeat: no-repeat;
  mask-position: center;
  mask-size: contain;
  -webkit-mask-repeat: no-repeat;
  -webkit-mask-position: center;
  -webkit-mask-size: contain;
}
</style>
