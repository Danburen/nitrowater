<script setup lang="ts">
import { InfoFilled } from '@element-plus/icons-vue'

defineProps<{
  title: string
  desc?: string
  /**
   * Full-height layout: the tool body fills the sidebar content area and never scrolls the
   * page (inner panels scroll). When false the body scrolls internally instead.
   */
  fill?: boolean
}>()
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <header class="flex shrink-0 items-center gap-1.5 border-b border-line px-5 py-3">
      <h1 class="text-base font-semibold tracking-tight text-ink">
        {{ title }}
      </h1>
      <el-tooltip
        v-if="desc"
        :content="desc"
        placement="bottom-start"
        :show-after="150"
      >
        <el-button
          size="small"
          :icon="InfoFilled"
          circle
          text
        />
      </el-tooltip>
    </header>

    <div
      v-if="fill"
      class="flex min-h-0 flex-1 flex-col overflow-hidden p-4"
    >
      <slot />
    </div>
    <div
      v-else
      class="min-h-0 flex-1 overflow-y-auto p-4 sm:p-5"
    >
      <slot />
    </div>
  </div>
</template>
