<script setup lang="ts">
/**
 * Admin console shell — a role-gated sidebar layout.
 *
 * Renders below nothing (the global header is hidden for `meta.layout = 'admin'`), fills the
 * viewport and hosts the admin pages in a nested `<router-view>`. Access requires a session
 * whose `roles` claim contains `ADMIN` (the resource server enforces the same via `ROLE_ADMIN`,
 * this is just the UI gate so non-admins never see the tooling).
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuth } from '../../auth/useAuth'
import ThemeToggle from '../../shared/ui/ThemeToggle.vue'

const route = useRoute()
const { session, loading, login, logout } = useAuth()

const isAdmin = computed(() => {
  const roles = session.value?.roles ?? []
  return roles.includes('ADMIN') || roles.includes('ROLE_ADMIN')
})

const displayName = computed(() => session.value?.name || session.value?.username || '已登录')

interface NavItem {
  to: string
  label: string
  icon: string
}

const nav: NavItem[] = [
  { to: '/admin/problems', label: '题目', icon: 'M4 5h16M4 12h16M4 19h10' },
  { to: '/admin/tags', label: '标签', icon: 'M20.6 13.4l-7.2 7.2a2 2 0 0 1-2.8 0l-7-7A2 2 0 0 1 3 12.2V5a2 2 0 0 1 2-2h7.2a2 2 0 0 1 1.4.6l7 7a2 2 0 0 1 0 2.8zM7.5 7.5h.01' },
  { to: '/admin/sets', label: '题单', icon: 'M4 6h16M4 12h16M4 18h16M8 4v16' },
  { to: '/admin/editorials', label: '题解', icon: 'M4 4h16v14H8l-4 4z' },
  { to: '/admin/plugins', label: '插件', icon: 'M20.5 11H19V7a1 1 0 0 0-1-1h-4V4.5a2.5 2.5 0 0 0-5 0V6H5a1 1 0 0 0-1 1v3.8h1.5a2.7 2.7 0 0 1 0 5.4H4V20a1 1 0 0 0 1 1h4.2v-1.5a2.5 2.5 0 0 1 5 0V21H19a1 1 0 0 0 1-1v-4.5h.5a2.5 2.5 0 0 0 0-5z' },
  { to: '/admin/resources', label: '资源管理', icon: 'M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z' },
]

function isActive(to: string): boolean {
  return route.path === to || route.path.startsWith(to + '/')
}
</script>

<template>
  <!-- Gate: resolving the session -->
  <div
    v-if="loading"
    class="flex h-[100dvh] items-center justify-center bg-bg text-sm text-faint"
  >
    加载中…
  </div>

  <!-- Gate: not signed in -->
  <div
    v-else-if="!session?.authenticated"
    class="flex h-[100dvh] flex-col items-center justify-center gap-4 bg-bg px-6 text-center"
  >
    <p class="text-base font-medium text-ink">
      管理后台需要登录
    </p>
    <p class="max-w-md text-sm text-dim">
      请使用管理员账号登录后访问。
    </p>
    <button
      class="btn btn-primary"
      @click="login('/admin')"
    >
      登录
    </button>
  </div>

  <!-- Gate: signed in but not an admin -->
  <div
    v-else-if="!isAdmin"
    class="flex h-[100dvh] flex-col items-center justify-center gap-4 bg-bg px-6 text-center"
  >
    <p class="text-base font-medium text-ink">
      无访问权限
    </p>
    <p class="max-w-md text-sm text-dim">
      当前账号不是管理员，无法进入管理后台。
    </p>
    <router-link
      to="/"
      class="btn"
    >
      返回门户
    </router-link>
  </div>

  <!-- Admin shell -->
  <div
    v-else
    class="flex h-[100dvh] flex-col overflow-hidden bg-bg text-ink md:flex-row"
  >
    <!-- Desktop / mobile-native sidebar -->
    <aside class="flex shrink-0 flex-col border-b border-line bg-soft/40 md:h-full md:w-[210px] md:border-b-0 md:border-r">
      <div class="flex h-[52px] shrink-0 items-center gap-2 border-b border-line px-3">
        <router-link
          to="/"
          class="inline-flex items-center gap-1.5 rounded-[9px] border border-line bg-card px-2.5 py-1.5 text-[13px] text-dim transition hover:border-line-strong hover:text-ink"
          title="返回门户"
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
            <path d="M19 12H5M12 19l-7-7 7-7" />
          </svg>
          门户
        </router-link>
        <span class="ml-auto rounded-md border border-accent/30 bg-accent/10 px-2 py-0.5 text-[11px] text-accent">管理后台</span>
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

      <div class="hidden shrink-0 items-center gap-2 border-t border-line p-2 md:flex">
        <ThemeToggle />
        <span class="min-w-0 flex-1 truncate text-[12.5px] text-dim">{{ displayName }}</span>
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
