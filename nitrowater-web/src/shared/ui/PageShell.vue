<script setup lang="ts">
/**
 * Shared page header shell used inside a SectionLayout (工具箱 tool pages).
 *
 * Renders a compact title bar (title + optional info tooltip + an `actions` slot on the right)
 * above the page body. With `fill` the body fills the available height and never scrolls the
 * page (inner panels scroll); otherwise the body scrolls internally.
 *
 * `contentWidth` keeps the header content aligned with the page's own centered content column:
 * pass the same max width the body uses (e.g. `'1120px'`) so the title / action buttons line up
 * with the content below at every viewport size. `'full'` (default) keeps the header edge-to-edge
 * for full-bleed layouts (tool pages).
 */
import { computed } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    title: string
    desc?: string
    fill?: boolean
    /** Max width of the header content column, e.g. `'1120px'`; `'full'` = no constraint. */
    contentWidth?: string
  }>(),
  { contentWidth: 'full' },
)

const headerStyle = computed(() =>
  props.contentWidth === 'full' ? undefined : { maxWidth: props.contentWidth },
)
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <header class="shrink-0 border-b border-line px-4 sm:px-5">
      <div
        class="mx-auto flex w-full min-w-0 items-center gap-1.5 py-3"
        :style="headerStyle"
      >
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
