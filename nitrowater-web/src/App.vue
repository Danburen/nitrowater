<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from './shared/ui/AppHeader.vue'
import ToastHost from './shared/ui/ToastHost.vue'

const route = useRoute()
/** The tools area renders its own sidebar shell (ToolsLayout) — no global header there. */
const showHeader = computed(() => route.meta.layout !== 'tools')
</script>

<template>
  <AppHeader v-if="showHeader" />
  <router-view />
  <ToastHost />
</template>
