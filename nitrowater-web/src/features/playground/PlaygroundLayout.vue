<script setup lang="ts">
/**
 * Playground section shell — the shared SectionLayout configured for 演练场.
 * Pages (题目 / 我的题单 / 题单广场 / 我的提交 / 题单与题目详情) render inside the sidebar's
 * <router-view>.
 */
import SectionLayout from '../../shared/ui/SectionLayout.vue'

const nav = [
  {
    label: '题目',
    to: '/playground',
    icon: 'M4 5h16v14H4zM4 9h16M9 9v10',
  },
  {
    label: '我的题单',
    to: '/playground/sets',
    icon: 'M4 6h16M4 12h16M4 18h10',
  },
  {
    label: '题单广场',
    to: '/playground/square',
    icon: 'M3 21V8l9-5 9 5v13M9 21v-6h6v6',
  },
  {
    label: '我的提交',
    to: '/playground/submissions',
    icon: 'M9 11l3 3L22 4M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11',
  },
]
</script>

<template>
  <SectionLayout
    title="演练场"
    :nav="nav"
    collapse-key="nitrowater:playground-sidebar-collapsed"
  />
</template>
