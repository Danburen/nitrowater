<script setup lang="ts">
/**
 * Shared page header shell used inside a SectionLayout (both 工具箱 tools and 演练场 pages).
 *
 * Renders a compact title bar (title + optional info tooltip + an `actions` slot on the right)
 * above the page body. With `fill` the body fills the available height and never scrolls the
 * page (inner panels scroll); otherwise the body scrolls internally.
 */
import { InfoFilled } from '@element-plus/icons-vue'

defineProps<{
  title: string
  desc?: string
  fill?: boolean
}>()
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <header class="flex shrink-0 items-center gap-1.5 border-b border-line px-5 py-3">
      <h1 class="min-w-0 truncate text-base font-semibold tracking-tight text-ink">
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
      <div class="ml-auto flex shrink-0 items-center gap-2">
        <slot name="actions" />
      </div>
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
