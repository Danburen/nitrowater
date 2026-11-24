<script setup lang="ts">
/**
 * Minimal stroke icon set for user-facing pages.
 *
 * <p>Element Plus icons stay inside the admin console; public pages use these so the visual
 * language (stroke width, size, currentColor) is consistent without pulling the icon package.</p>
 */
const PATHS = {
  info: ['M12 16v-4', 'M12 8h.01'],
  copy: [
    'M9 9h10a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V10a1 1 0 0 1 1-1z',
    'M5 15H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1',
  ],
  trash: ['M3 6h18', 'M8 6V4h8v2', 'M6 6l1 14h10l1-14'],
  folder: ['M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z'],
  download: ['M12 3v12', 'M7 10l5 5 5-5', 'M5 21h14'],
} as const

withDefaults(defineProps<{ name: keyof typeof PATHS; size?: number }>(), { size: 16 })
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.9"
    stroke-linecap="round"
    stroke-linejoin="round"
    class="shrink-0"
    aria-hidden="true"
  >
    <circle
      v-if="name === 'info'"
      cx="12"
      cy="12"
      r="9"
    />
    <path
      v-for="(path, index) in PATHS[name]"
      :key="index"
      :d="path"
    />
  </svg>
</template>
