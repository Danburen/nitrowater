<script setup lang="ts">
/**
 * Tools area shell — a thin config over the shared SectionLayout (sidebar + section title 工具箱).
 */
import SectionLayout from '../../shared/ui/SectionLayout.vue'
import { toolCategories, readyToolsIn } from './catalog'

/** Overview row + the ready tools grouped server-first, mapped to the generic nav shape. */
const overview = {
  label: '工具箱总览',
  to: '/tools',
  icon: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
}
const nav = toolCategories.flatMap((category) =>
  readyToolsIn(category.key).map((tool) => ({
    label: tool.title,
    to: tool.to,
    icon: tool.icon,
    group: category.label,
  })),
)
</script>

<template>
  <SectionLayout
    title="工具箱"
    :nav="nav"
    :overview="overview"
    collapse-key="nitrowater:tools-sidebar-collapsed"
  />
</template>
