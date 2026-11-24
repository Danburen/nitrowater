<script setup lang="ts">
/** Static tools site shell: sidebar navigation + routed tool pages + toast host. */
import ToolsShell from './shared/ui/ToolsShell.vue'
import ToastHost from './shared/ui/ToastHost.vue'
import { tools } from './features/tools/catalog'
</script>

<template>
  <ToolsShell :tools="tools">
    <router-view />
  </ToolsShell>
  <ToastHost />
</template>
