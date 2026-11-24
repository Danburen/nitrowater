<script setup lang="ts">
import { computed } from 'vue'
import { toggleTheme, useTheme } from '../theme/useTheme'
import SvgIcon from './SvgIcon.vue'

const { theme } = useTheme()
const isDark = computed(() => theme.value === 'dark')
const label = computed(() => (isDark.value ? '切换到浅色模式' : '切换到深色模式'))
</script>

<template>
  <button
    type="button"
    class="inline-flex size-9 shrink-0 items-center justify-center rounded-[9px] border border-line bg-soft text-ink transition hover:border-line-strong hover:text-accent"
    :title="label"
    :aria-label="label"
    :aria-pressed="isDark"
    @click="toggleTheme"
  >
    <SvgIcon
      v-if="isDark"
      name="sun"
      :size="18"
    />
    <SvgIcon
      v-else
      name="moon"
      :size="18"
    />
  </button>
</template>
