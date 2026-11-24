<script setup lang="ts">
/**
 * Minimal stroke icon set for user-facing tools.
 *
 * <p>The glyph markup now lives in `src/assets/icons/*.svg`; this component is only a typed name
 * mapping so callers keep the compact `<UiIcon name="info" />` vocabulary. Element Plus icons stay
 * inside the heavyweight tool views.</p>
 */
import SvgIcon from './SvgIcon.vue'

withDefaults(
  defineProps<{
    name: 'info' | 'copy' | 'trash' | 'folder' | 'download'
    size?: number
  }>(),
  { size: 16 },
)
</script>

<template>
  <SvgIcon
    :name="name"
    :size="size"
  />
</template>
