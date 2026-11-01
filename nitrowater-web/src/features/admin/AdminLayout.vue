<script setup lang="ts">
/**
 * Admin console shell — a role-gated layout with an Element Plus menu.
 *
 * <p>The outer frame is intentionally plain flexbox (not {@code el-container}): Element Plus's
 * layout components carry their own padding/height rules that fight a full-height app shell.
 * Element Plus owns the interactive pieces (menu, drawer, dropdown, result) while the frame keeps
 * the proven Tailwind structure.</p>
 *
 * <p>Access requires a session whose {@code roles} claim contains {@code ADMIN}; the resource
 * server enforces the same with {@code ROLE_ADMIN}. This is only the UI gate.</p>
 */
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Back, Expand, Fold } from '@element-plus/icons-vue'
import { useAuth } from '../../auth/useAuth'
import ThemeToggle from '../../shared/ui/ThemeToggle.vue'
import AdminNav from './AdminNav.vue'
import { ADMIN_MENU } from './menu'

const route = useRoute()
const router = useRouter()
const { session, loading, login, logout } = useAuth()

const collapsed = ref(false)
const drawerOpen = ref(false)

const isAdmin = computed(() => {
  const roles = session.value?.roles ?? []
  return roles.includes('ADMIN') || roles.includes('ROLE_ADMIN')
})
const displayName = computed(() => session.value?.name || session.value?.username || '已登录')

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

function onCommand(command: string): void {
  if (command === 'home') {
    void router.push('/')
    return
  }
  if (command === 'logout') logout()
}
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
        <router-link
          to="/"
          class="inline-flex size-8 shrink-0 place-items-center rounded-[9px] border border-line bg-card text-dim transition hover:border-line-strong hover:text-ink"
          title="返回门户"
          aria-label="返回门户"
        >
          <el-icon><Back /></el-icon>
        </router-link>
        <span
          v-if="!collapsed"
          class="truncate text-[13px] text-dim"
        >管理后台</span>
      </div>
      <div class="min-h-0 flex-1 overflow-y-auto py-2">
        <AdminNav :collapsed="collapsed" />
      </div>
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
        <ThemeToggle />
        <el-dropdown @command="onCommand">
          <button
            type="button"
            class="inline-flex items-center gap-1.5 rounded-[9px] border border-line bg-card px-2.5 py-1.5 text-[12.5px] text-dim transition hover:border-line-strong hover:text-ink"
          >
            <span class="max-w-[120px] truncate">{{ displayName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="home">
                返回门户
              </el-dropdown-item>
              <el-dropdown-item
                command="logout"
                divided
              >
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <main class="min-h-0 flex-1 overflow-y-auto">
        <router-view />
      </main>
    </div>
  </div>

  <el-drawer
    v-model="drawerOpen"
    direction="ltr"
    size="240px"
    :with-header="false"
  >
    <AdminNav />
  </el-drawer>
</template>
