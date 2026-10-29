<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../../auth/useAuth'
import ThemeToggle from './ThemeToggle.vue'

const route = useRoute()
const router = useRouter()
const { session, loading, login, logout, isAdmin, accounts, addAccount, switchAccount, removeAccount } = useAuth()

const isLoggedIn = computed(() => !!session.value?.authenticated)
const displayName = computed(() => session.value?.name || session.value?.username || '已登录')
const accountBusy = ref(false)
const accountError = ref<string | null>(null)

/** Switches to another account held by the BFF session (the helper reloads the SPA). */
async function onSwitchAccount(uid: string): Promise<void> {
  if (accountBusy.value || !uid || session.value?.activeUid === uid) return
  accountBusy.value = true
  accountError.value = null
  try {
    await switchAccount(uid)
  } catch {
    accountError.value = '切换账号失败，请重试'
    accountBusy.value = false
  }
}

/** Removes one account; removing the last one triggers a full SSO logout. */
async function onRemoveAccount(uid: string, active: boolean): Promise<void> {
  if (accountBusy.value || !uid) return
  if (!window.confirm(active ? '退出当前账号？' : '移除该账号？')) return
  accountBusy.value = true
  accountError.value = null
  try {
    await removeAccount(uid)
  } catch {
    accountError.value = '操作失败，请重试'
    accountBusy.value = false
  }
}

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
          <details class="relative">
            <summary
              class="flex cursor-pointer list-none items-center gap-2 rounded-[9px] border border-line bg-soft px-3 py-1.5 text-[13px] text-ink transition hover:border-line-strong [&::-webkit-details-marker]:hidden"
            >
              <img
                v-if="session?.picture"
                :src="session.picture"
                alt=""
                class="size-5 rounded-full object-cover"
              >
              <span
                v-else
                class="size-1.5 rounded-full bg-ok"
              />
              <span class="max-w-[8rem] truncate">{{ displayName }}</span>
              <span class="text-[10px] text-dim">▼</span>
            </summary>

            <div class="absolute right-0 z-30 mt-2 w-64 overflow-hidden rounded-xl border border-line bg-card shadow-xl">
              <p class="border-b border-line px-3 py-2 text-[11px] uppercase tracking-wide text-faint">
                账号
              </p>
              <button
                v-for="account in accounts"
                :key="account.uid"
                type="button"
                class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-ink transition hover:bg-soft disabled:cursor-default disabled:opacity-70"
                :disabled="accountBusy || account.active"
                @click="onSwitchAccount(account.uid)"
              >
                <img
                  v-if="account.picture"
                  :src="account.picture"
                  alt=""
                  class="size-6 rounded-full object-cover"
                >
                <span
                  v-else
                  class="grid size-6 shrink-0 place-items-center rounded-full bg-soft text-[11px] text-dim"
                >
                  {{ (account.name || account.username || '?').slice(0, 1) }}
                </span>
                <span class="min-w-0 flex-1 truncate">{{ account.name || account.username || account.uid }}</span>
                <span
                  v-if="account.active"
                  class="text-[11px] text-ok"
                >
                  当前
                </span>
              </button>
              <div class="border-t border-line">
                <button
                  type="button"
                  class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-ink transition hover:bg-soft"
                  @click="addAccount"
                >
                  <span class="text-dim">＋</span> 添加账号
                </button>
                <button
                  type="button"
                  class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-ink transition hover:bg-soft disabled:opacity-60"
                  :disabled="accountBusy"
                  @click="onRemoveAccount(session?.activeUid ?? '', true)"
                >
                  退出当前账号
                </button>
                <button
                  type="button"
                  class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-danger transition hover:bg-soft"
                  @click="logout"
                >
                  退出全部
                </button>
              </div>
              <p
                v-if="accountError"
                class="px-3 py-2 text-xs text-danger"
              >
                {{ accountError }}
              </p>
            </div>
          </details>
        </template>
      </div>
    </div>
  </header>
</template>
