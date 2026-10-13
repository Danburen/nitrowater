<script setup lang="ts">
/**
 * Admin console shell — a role-gated sidebar layout, styled after nitrowater-web's
 * features/admin/AdminLayout. Fills the viewport and hosts the admin pages in a nested
 * <router-view>; the account service enforces ROLE_SUPER_ADMIN on /api/admin/**.
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import { useSession } from '@/auth/session'
import ThemeToggle from '@/components/ThemeToggle.vue'

const route = useRoute()
// Resolved by the router guard before this layout renders — read from the shared singleton.
const { session } = useSession()

const displayName = computed(() => session.value?.name || session.value?.username || '已登录')
const roleLabels = computed(() => (session.value?.roles ?? []).map((r) => r.replace(/^ROLE_/, '')))

interface NavItem {
  to: string
  label: string
  /** 24x24 stroke SVG path data. */
  icon: string
}

const nav: NavItem[] = [
  {
    to: '/users',
    label: '用户管理',
    icon: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75',
  },
  {
    to: '/roles',
    label: '角色管理',
    icon: 'M20.6 13.4l-7.2 7.2a2 2 0 0 1-2.8 0l-7-7A2 2 0 0 1 3 12.2V5a2 2 0 0 1 2-2h7.2a2 2 0 0 1 1.4.6l7 7a2 2 0 0 1 0 2.8zM7.5 7.5h.01',
  },
  {
    to: '/clients',
    label: '应用管理',
    icon: 'M3 3h7v7H3zM14 3h7v7h-7zM14 14h7v7h-7zM3 14h7v7H3z',
  },
]

function isActive(to: string): boolean {
  return route.path === to || route.path.startsWith(to + '/')
}

function logout(): void {
  // Same-origin redirect to Spring Security's logout; the account service then returns to login.
  window.location.assign('/logout')
}
</script>

<template>
  <div class="flex h-[100dvh] flex-col overflow-hidden bg-bg text-ink md:flex-row">
    <!-- Sidebar (top bar on mobile) -->
    <aside class="flex shrink-0 flex-col border-b border-line bg-soft/40 md:h-full md:w-[220px] md:border-b-0 md:border-r">
      <div class="flex h-[52px] shrink-0 items-center gap-2 border-b border-line px-3">
        <span class="size-[26px] shrink-0 rounded-lg bg-gradient-to-br from-accent to-accent-2" />
        <span class="truncate text-sm font-semibold text-ink">NitroWater 管理台</span>
        <span class="ml-auto shrink-0 rounded-md border border-accent/30 bg-accent/10 px-2 py-0.5 text-[11px] text-accent">管理后台</span>
      </div>

      <nav class="flex min-h-0 flex-1 gap-1 overflow-x-auto p-2 md:flex-col md:overflow-y-auto">
        <router-link
          v-for="item in nav"
          :key="item.to"
          :to="item.to"
          class="flex shrink-0 items-center gap-2 whitespace-nowrap rounded-[9px] px-3 py-2 text-sm transition"
          :class="isActive(item.to) ? 'bg-accent/10 font-medium text-accent' : 'text-dim hover:bg-soft hover:text-ink'"
        >
          <svg
            class="size-4 shrink-0"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path :d="item.icon" />
          </svg>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="flex shrink-0 items-center gap-2 border-t border-line p-2">
        <ThemeToggle />
        <span
          class="min-w-0 flex-1 truncate text-[12.5px] text-dim"
          :title="roleLabels.join(' · ')"
        >{{ displayName }}</span>
        <button
          class="btn"
          @click="logout"
        >
          退出
        </button>
      </div>
    </aside>

    <!-- Content -->
    <main class="min-h-0 flex-1 overflow-y-auto">
      <router-view />
    </main>
  </div>
</template>
