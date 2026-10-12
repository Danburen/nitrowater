<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from './auth/useAuth'
import AppHeader from './shared/ui/AppHeader.vue'
import ToastHost from './shared/ui/ToastHost.vue'

const route = useRoute()
const router = useRouter()
const { session, consumePostLogin } = useAuth()
/** The tools and admin areas render their own shells — no global header there. */
const showHeader = computed(() => route.meta.layout !== 'tools' && route.meta.layout !== 'admin')

// Resume the page the user intended before logging in (e.g. from home's "进入演练场").
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
  <AppHeader v-if="showHeader" />
  <router-view />
  <ToastHost />
</template>
