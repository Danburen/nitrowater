<script setup lang="ts">
/**
 * Admin console shell — a role-gated layout with an Element Plus menu.
 *
 * <p>The outer frame is intentionally plain flexbox (not {@code el-container}): Element Plus's
 * layout components carry their own padding/height rules that fight a full-height app shell.
 * Element Plus only owns the menu; the account menu is the same Tailwind {@link UserMenu} the
 * portal header uses (top-right), and the mobile drawer is the shared {@link AppDrawer}, so every
 * popup matches the portal and the admin pages.</p>
 *
 * <p>Global actions that are not menu entries live at the bottom-left of the sidebar: back to the
 * portal and the theme switch. Access requires a session whose {@code roles} claim contains
 * {@code ADMIN}; the resource server enforces the same with {@code ROLE_ADMIN}. This is only the UI
 * gate.</p>
 */
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Expand, Fold } from '@element-plus/icons-vue'
import { useAuth } from '../../auth/useAuth'
import AppDrawer from '../../shared/ui/AppDrawer.vue'
import ThemeToggle from '../../shared/ui/ThemeToggle.vue'
import UserMenu from '../../shared/ui/UserMenu.vue'
import AdminNav from './AdminNav.vue'
import AdminSidebarFooter from './AdminSidebarFooter.vue'
import { ADMIN_MENU } from './menu'

const route = useRoute()
const router = useRouter()
const { session, loading, login } = useAuth()

const collapsed = ref(false)
const drawerOpen = ref(false)

const isAdmin = computed(() => {
  const roles = session.value?.roles ?? []
  return roles.includes('ADMIN') || roles.includes('ROLE_ADMIN')
})

/** Current page label, resolved from the menu (used as the header title). */
const pageTitle = computed(() => {
  const path = typeof route.meta.activeMenu === 'string' ? route.meta.activeMenu : route.path
  for (const item of ADMIN_MENU) {
    if (item.path === path) return item.label
    const child = item.children?.find((entry) => entry.path === path)
    if (child) return `${item.label} · ${child.label}`
  }
  return '管理后台'
})
</script>

<template>
  <div
    v-if="loading"
    class="flex h-[100dvh] items-center justify-center bg-bg text-sm text-faint"
  >
    加载中…
  </div>

  <div
    v-else-if="!session?.authenticated"
    class="flex h-[100dvh] items-center justify-center bg-bg px-6"
  >
    <el-result
      icon="warning"
      title="管理后台需要登录"
      sub-title="请使用管理员账号登录后访问。"
    >
      <template #extra>
        <el-button
          type="primary"
          @click="login('/admin')"
        >
          登录
        </el-button>
      </template>
    </el-result>
  </div>

  <div
    v-else-if="!isAdmin"
    class="flex h-[100dvh] items-center justify-center bg-bg px-6"
  >
    <el-result
      icon="error"
      title="无访问权限"
      sub-title="当前账号不是管理员，无法进入管理后台。"
    >
      <template #extra>
        <el-button @click="router.push('/')">
          返回门户
        </el-button>
      </template>
    </el-result>
  </div>

  <div
    v-else
    class="flex h-[100dvh] overflow-hidden bg-bg text-ink"
  >
    <aside
      class="hidden shrink-0 flex-col border-r border-line bg-soft/40 transition-[width] duration-200 md:flex"
      :class="collapsed ? 'md:w-[64px]' : 'md:w-[220px]'"
    >
      <div class="flex h-[52px] shrink-0 items-center gap-2 border-b border-line px-3">
        <img
          src="/favicon.svg"
          alt=""
          class="size-[22px] shrink-0"
        >
        <span
          v-if="!collapsed"
          class="truncate text-[13px] font-semibold text-ink"
        >管理后台</span>
      </div>
      <div class="min-h-0 flex-1 overflow-y-auto py-2">
        <AdminNav :collapsed="collapsed" />
      </div>
      <AdminSidebarFooter :collapsed="collapsed" />
    </aside>

    <div class="flex min-w-0 flex-1 flex-col">
      <header class="flex h-[52px] shrink-0 items-center gap-2 border-b border-line px-3">
        <!-- Two toggles, one per breakpoint. Plain buttons (not el-button): Element Plus forces its
             own display/padding, so `md:hidden` lost to `display:inline-flex` and BOTH buttons showed
             at different sizes. The wrapper-free plain button keeps Tailwind in charge. -->
        <button
          type="button"
          class="inline-flex size-8 shrink-0 items-center justify-center rounded-[9px] border border-line bg-card text-dim transition hover:border-line-strong hover:text-ink md:hidden"
          aria-label="打开菜单"
          @click="drawerOpen = true"
        >
          <el-icon><Expand /></el-icon>
        </button>
        <button
          type="button"
          class="hidden size-8 shrink-0 items-center justify-center rounded-[9px] border border-line bg-card text-dim transition hover:border-line-strong hover:text-ink md:inline-flex"
          :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'"
          @click="collapsed = !collapsed"
        >
          <el-icon><component :is="collapsed ? Expand : Fold" /></el-icon>
        </button>
        <span class="min-w-0 flex-1 truncate text-[13px] font-semibold text-dim">{{ pageTitle }}</span>
        <ThemeToggle class="md:hidden" />
        <UserMenu />
      </header>

      <main class="min-h-0 flex-1 overflow-y-auto">
        <router-view />
      </main>
    </div>
  </div>

  <AppDrawer
    v-model="drawerOpen"
    side="left"
    width="240px"
    :pad="false"
  >
    <div class="flex h-full flex-col">
      <div class="min-h-0 flex-1 overflow-y-auto py-2">
        <AdminNav />
      </div>
      <AdminSidebarFooter />
    </div>
  </AppDrawer>
</template>
