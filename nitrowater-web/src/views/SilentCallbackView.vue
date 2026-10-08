<script setup lang="ts">
import { onMounted } from 'vue'
import { userManager } from '../auth/oidc'

// Hidden iframe target for oidc-client-ts automaticSilentRenew (prompt=none).
onMounted(() => {
  void userManager.signinSilentCallback()
})
</script>

<template>
  <span />
</template>
