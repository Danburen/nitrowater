<script setup lang="ts">
import { toasts } from './toast'

const styles: Record<string, string> = {
  success: 'border-ok/30 bg-ok/10 text-ok',
  warning: 'border-warn/30 bg-warn/10 text-warn',
  error: 'border-danger/30 bg-danger/10 text-danger',
  info: 'border-accent/30 bg-accent/10 text-accent',
}
</script>

<template>
  <div class="pointer-events-none fixed bottom-4 right-4 z-50 flex w-72 flex-col gap-2">
    <div
      v-for="item in toasts"
      :key="item.id"
      class="pointer-events-auto rounded-lg border px-3 py-2 text-sm shadow-lg backdrop-blur-md"
      :class="styles[item.type]"
    >
      {{ item.text }}
    </div>
  </div>
</template>
