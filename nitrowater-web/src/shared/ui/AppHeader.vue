<script setup lang="ts">
import { computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../../auth/useAuth'
import ThemeToggle from './ThemeToggle.vue'
import UserMenu from './UserMenu.vue'

const route = useRoute()
const router = useRouter()
const { session, loading, login, logout, logoutStuck, isAdmin } = useAuth()

const isLoggedIn = computed(() => !!session.value?.authenticated)

/** Landing-page sections + standalone routes reachable from the site nav. */
interface NavItem {
  label: string
  /** Home section id to scroll to (mutually exclusive with `to`). */
  id?: string
  /** Route to navigate to. */
  to?: string
}

const nav = computed<NavItem[]>(() => {
  const items: NavItem[] = [
    { to: '/tools', label: '工具箱' },
    { to: '/plugins', label: '插件中心' },
    { to: '/chat', label: 'NitroBot' },
    // 文档 / 服务器 入口暂缓：托管的文档后续再独立分开，入口统一走「插件中心」；服务器列表功能未开发。
    // { id: 'docs', label: '文档' },
    // { id: 'servers', label: '服务器' },
    { to: '/about', label: '关于' },
  ]
  // Plugin metadata/artifacts are edited inline on the public pages; the console is now the
  // operations view (resource table / orphan cleanup), so it gets a quieter entry.
  if (isAdmin.value) items.push({ to: '/admin/resources', label: '运维' })
  return items
})

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
        <img
          src="/favicon.svg"
          alt=""
          class="size-[26px] shrink-0"
        >
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
          @click="login()"
        >
          登录
        </button>
        <template v-else>
          <button
            v-if="logoutStuck"
            type="button"
            class="rounded-[9px] border border-danger/40 bg-danger/10 px-2 py-1 text-[12px] text-danger transition hover:border-danger"
            title="上次退出没有完成，SSO 会话可能仍然有效"
            @click="logout"
          >
            退出未完成，重试
          </button>
          <UserMenu />
        </template>
      </div>
    </div>
  </header>
</template>
