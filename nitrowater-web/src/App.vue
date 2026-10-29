<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from './auth/useAuth'
import AppHeader from './shared/ui/AppHeader.vue'
import AuthExpiredBanner from './shared/ui/AuthExpiredBanner.vue'
import ToastHost from './shared/ui/ToastHost.vue'

const route = useRoute()
const router = useRouter()
const { session, consumePostLogin } = useAuth()
/** The tools / admin / plugins areas and the full-bleed chat shell render their own chrome. */
const showHeader = computed(() => {
  const layout = route.meta.layout
  return layout !== 'tools' && layout !== 'admin' && layout !== 'plugins' && layout !== 'chat'
})

// Resume the page the user intended before logging in (e.g. from a gated tools page).
watch(
  () => session.value?.authenticated,
  (authed) => {
    if (!authed) return
    const target = consumePostLogin()
    if (target && target !== route.fullPath) void router.replace(target)
  },
  { immediate: true },
)
</script>

<template>
  <AuthExpiredBanner />
  <AppHeader v-if="showHeader" />
  <router-view />
  <ToastHost />
</template>
