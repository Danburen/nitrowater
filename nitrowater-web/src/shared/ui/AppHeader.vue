<script setup lang="ts">
import { computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../../auth/useAuth'
import ThemeToggle from './ThemeToggle.vue'

const route = useRoute()
const router = useRouter()
const { session, loading, login, logout } = useAuth()

const isLoggedIn = computed(() => !!session.value?.authenticated)
const displayName = computed(() => session.value?.name || session.value?.username || '已登录')

/** Landing-page sections + one standalone route reachable from the site nav. */
interface NavItem {
  label: string
  /** Home section id to scroll to (mutually exclusive with `to`). */
  id?: string
  /** Route to navigate to. */
  to?: string
}

const nav: NavItem[] = [
  { id: 'tools', label: '工具箱' },
  { id: 'playground', label: '演练场' },
  { id: 'docs', label: '文档' },
  { id: 'servers', label: '服务器' },
  { to: '/about', label: '关于' },
]

/** Navigate to a route item, or scroll to a home section (navigating home first). */
async function onNav(item: NavItem): Promise<void> {
  if (item.to) {
    if (route.path !== item.to) await router.push(item.to)
    return
  }
  if (route.name !== 'home') {
    await router.push('/')
  }
  await nextTick()
  if (item.id) document.getElementById(item.id)?.scrollIntoView({ behavior: 'smooth' })
}
</script>

<template>
  <header class="h-[var(--app-header-h)] border-b border-line bg-bg/80 backdrop-blur-md">
    <div class="mx-auto flex h-full max-w-[1120px] items-center justify-between gap-4 px-4 sm:px-6">
      <router-link
        to="/"
        class="flex shrink-0 items-center gap-2.5 text-base font-semibold text-ink"
      >
        <span class="size-[26px] rounded-lg bg-gradient-to-br from-accent to-accent-2" />
        <span>NitroWater</span>
      </router-link>

      <nav class="hidden gap-[26px] text-sm text-dim md:flex">
        <button
          v-for="item in nav"
          :key="item.label"
          type="button"
          class="cursor-pointer transition hover:text-ink"
          @click="onNav(item)"
        >
          {{ item.label }}
        </button>
      </nav>

      <div class="flex shrink-0 items-center gap-2.5">
        <ThemeToggle />
        <button
          v-if="!isLoggedIn"
          class="btn"
          :disabled="loading"
          @click="login"
        >
          登录
        </button>
        <template v-else>
          <span class="inline-flex items-center gap-2 rounded-[9px] border border-line bg-soft px-3 py-1.5 text-[13px] text-ink">
            <span class="size-1.5 rounded-full bg-ok" />
            {{ displayName }}
          </span>
          <button
            class="btn"
            @click="logout"
          >
            退出
          </button>
        </template>
      </div>
    </div>
  </header>
</template>
