<script setup lang="ts">
/**
 * Shared sidebar shell for top-level sections (工具箱 / 插件中心).
 *
 * Left column (desktop): a back-to-portal arrow + a brand block (logo mark + section title,
 * both link home) + a collapse toggle, then the section nav and a footer with theme + session.
 * The routed page renders in <router-view>. On mobile the sidebar becomes a top bar with a
 * back arrow and a <select> over the same nav.
 */
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../../auth/useAuth'
import ThemeToggle from './ThemeToggle.vue'

interface NavItem {
  label: string
  to: string
  /** 24x24 stroke SVG path data. */
  icon?: string
  /** Optional group heading; a header is rendered whenever this differs from the previous item. */
  group?: string
}

const props = defineProps<{
  /** Section name shown next to the logo mark (工具箱 / 插件中心). */
  title: string
  /** Nav entries (routed). */
  nav: NavItem[]
  /** Optional single "overview" entry rendered above a divider. */
  overview?: NavItem
  /** localStorage key for the desktop collapse state. */
  collapseKey?: string
}>()

const route = useRoute()
const router = useRouter()
const { session, loading, login, logout } = useAuth()

const isLoggedIn = computed(() => !!session.value?.authenticated)
const displayName = computed(() => session.value?.name || session.value?.username || '已登录')

const COLLAPSE_KEY = props.collapseKey ?? 'nitrowater:section-collapsed'
const collapsed = ref(localStorage.getItem(COLLAPSE_KEY) === '1')

function toggleCollapse(): void {
  collapsed.value = !collapsed.value
  localStorage.setItem(COLLAPSE_KEY, collapsed.value ? '1' : '0')
}
function go(to: string): void {
  void router.push(to)
}
/** Exact-match active state (nav targets are leaf routes). */
function active(to: string): boolean {
  return route.path === to
}
</script>

<template>
  <div class="flex h-[100dvh] flex-col overflow-hidden bg-bg text-ink md:flex-row">
    <!-- Mobile top bar -->
    <div class="flex h-12 shrink-0 items-center gap-2 border-b border-line px-3 md:hidden">
      <button
        type="button"
        class="btn px-2.5"
        title="返回门户"
        aria-label="返回门户"
        @click="go('/')"
      >
        ←
      </button>
      <select
        class="select max-w-[190px] py-1.5 text-[13px]"
        :value="route.path"
        aria-label="选择导航"
        @change="go(($event.target as HTMLSelectElement).value)"
      >
        <option
          v-if="overview"
          :value="overview.to"
        >
          {{ overview.label }}
        </option>
        <option
          v-for="item in nav"
          :key="item.to"
          :value="item.to"
        >
          {{ item.label }}
        </option>
      </select>
      <span class="ml-auto">
        <ThemeToggle />
      </span>
    </div>

    <!-- Desktop sidebar -->
    <aside
      class="hidden shrink-0 flex-col border-r border-line bg-soft/40 transition-[width] duration-200 ease-out md:flex"
      :class="collapsed ? 'w-14' : 'w-[210px]'"
    >
      <!-- Head: back arrow + brand (logo + title) + collapse toggle -->
      <div
        class="flex h-[52px] shrink-0 items-center border-b border-line"
        :class="collapsed ? 'justify-center px-2' : 'gap-2 px-3'"
      >
        <router-link
          to="/"
          class="flex min-w-0 items-center gap-1.5 rounded-[9px] px-1 py-1 transition hover:bg-soft"
          title="返回门户"
          aria-label="返回门户"
        >
          <img
            src="/favicon.svg"
            alt=""
            class="size-[26px] shrink-0"
          >
          <span
            v-if="!collapsed"
            class="truncate text-sm font-semibold text-ink"
          >{{ title }}</span>
        </router-link>
        <button
          type="button"
          class="grid shrink-0 place-items-center rounded-[9px] text-dim transition hover:bg-soft hover:text-ink"
          :class="collapsed ? 'size-9' : 'ml-auto size-8'"
          :title="collapsed ? '展开导航' : '收起导航'"
          :aria-label="collapsed ? '展开导航' : '收起导航'"
          :aria-expanded="!collapsed"
          @click="toggleCollapse"
        >
          <svg
            class="size-4"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path :d="collapsed ? 'M9 5l7 7-7 7' : 'M15 5l-7 7 7 7'" />
          </svg>
        </button>
      </div>

      <!-- Optional per-section extra (e.g. the plugin center's search box); hidden when collapsed. -->
      <div
        v-if="!collapsed && $slots['sidebar-extra']"
        class="shrink-0 px-3 pt-3"
      >
        <slot name="sidebar-extra" />
      </div>

      <nav class="min-h-0 flex-1 overflow-y-auto p-2">
        <router-link
          v-if="overview"
          :to="overview.to"
          class="flex items-center gap-2 rounded-[9px] px-3 py-2 text-sm transition"
          :class="[active(overview.to) ? 'bg-accent/10 font-medium text-accent' : 'text-dim hover:bg-soft hover:text-ink', collapsed ? 'justify-center px-2' : '']"
          :title="collapsed ? overview.label : undefined"
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
            <path :d="overview.icon || 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z'" />
          </svg>
          <span
            v-if="!collapsed"
            class="truncate"
          >{{ overview.label }}</span>
        </router-link>

        <div
          v-if="overview"
          class="mx-2 my-1.5 h-px bg-line"
          role="separator"
        />

        <template
          v-for="(item, index) in nav"
          :key="item.to"
        >
          <div
            v-if="item.group && (index === 0 || nav[index - 1]?.group !== item.group)"
            class="px-3 pb-1 pt-3 text-[11px] font-medium uppercase tracking-wide text-faint"
            :class="collapsed ? 'px-2' : ''"
          >
            <span v-if="!collapsed">{{ item.group }}</span>
            <div
              v-else
              class="h-px bg-line"
            />
          </div>
          <router-link
            :to="item.to"
            class="flex items-center gap-2 rounded-[9px] px-3 py-2 text-sm transition"
            :class="[active(item.to) ? 'bg-accent/10 font-medium text-accent' : 'text-dim hover:bg-soft hover:text-ink', collapsed ? 'justify-center px-2' : '']"
            :title="collapsed ? item.label : undefined"
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
              <path :d="item.icon || 'M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01'" />
            </svg>
            <span
              v-if="!collapsed"
              class="truncate"
            >{{ item.label }}</span>
          </router-link>
        </template>
      </nav>

      <!-- Footer: theme + session -->
      <div
        class="flex shrink-0 items-center gap-2 border-t border-line p-2"
        :class="collapsed ? 'flex-col' : ''"
      >
        <ThemeToggle />
        <template v-if="collapsed">
          <button
            v-if="!isLoggedIn"
            type="button"
            class="grid size-9 shrink-0 place-items-center rounded-[9px] border border-line bg-soft text-ink transition hover:border-line-strong hover:text-accent disabled:opacity-60"
            title="登录"
            aria-label="登录"
            :disabled="loading"
            @click="login()"
          >
            <svg
              class="size-[18px]"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z" />
            </svg>
          </button>
          <button
            v-else
            type="button"
            class="grid size-9 shrink-0 place-items-center rounded-[9px] border border-line bg-soft text-ink transition hover:border-line-strong hover:text-accent"
            title="退出登录"
            aria-label="退出登录"
            @click="logout"
          >
            <svg
              class="size-[18px]"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" />
            </svg>
          </button>
        </template>
        <template v-else>
          <button
            v-if="!isLoggedIn"
            class="btn flex-1 justify-center"
            :disabled="loading"
            @click="login()"
          >
            登录
          </button>
          <template v-else>
            <span class="min-w-0 flex-1 truncate text-[12.5px] text-dim">{{ displayName }}</span>
            <button
              class="btn"
              @click="logout"
            >
              退出
            </button>
          </template>
        </template>
      </div>
    </aside>

    <!-- Content -->
    <main class="min-h-0 flex-1 overflow-hidden">
      <router-view />
    </main>
  </div>
</template>
