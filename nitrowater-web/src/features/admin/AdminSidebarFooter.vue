<script setup lang="ts">
/**
 * Sidebar footer of the admin console: back to the portal and the theme switch.
 *
 * <p>Both are global actions rather than admin menu entries, so they sit at the bottom-left of the
 * column and reuse the portal's own vocabulary ({@link ThemeToggle}), which keeps both shells
 * looking like one product. The account menu lives in the top bar instead, mirroring the portal
 * header. Collapsed to the icon rail, the label drops away and the two controls stack.</p>
 */
import { Back } from '@element-plus/icons-vue'
import ThemeToggle from '../../shared/ui/ThemeToggle.vue'

withDefaults(defineProps<{ collapsed?: boolean }>(), { collapsed: false })
</script>

<template>
  <div class="shrink-0 border-t border-line p-2">
    <div
      v-if="collapsed"
      class="flex flex-col items-center gap-2"
    >
      <router-link
        to="/"
        class="inline-flex size-9 shrink-0 items-center justify-center rounded-[9px] border border-line bg-card text-dim transition hover:border-line-strong hover:text-ink"
        title="返回门户"
        aria-label="返回门户"
      >
        <el-icon><Back /></el-icon>
      </router-link>
      <ThemeToggle />
    </div>

    <div
      v-else
      class="flex items-center gap-2"
    >
      <router-link
        to="/"
        class="inline-flex min-w-0 flex-1 items-center gap-1.5 rounded-[9px] border border-line bg-card px-2.5 py-[9px] text-[12.5px] text-dim transition hover:border-line-strong hover:text-ink"
      >
        <el-icon><Back /></el-icon>
        <span class="truncate">返回门户</span>
      </router-link>
      <ThemeToggle />
    </div>
  </div>
</template>
