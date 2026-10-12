<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../../auth/useAuth'
import ThemeToggle from '../../shared/ui/ThemeToggle.vue'
import { readyTools } from './catalog'

const route = useRoute()
const router = useRouter()
const { session, loading, login, logout } = useAuth()

const isLoggedIn = computed(() => !!session.value?.authenticated)
const displayName = computed(() => session.value?.name || session.value?.username || '已登录')

/** Tool links shown in the sidebar (available tools only). */
const navTools = readyTools

/** Desktop sidebar collapse state, persisted across sessions. */
const COLLAPSE_KEY = 'nitrowater:tools-sidebar-collapsed'
const collapsed = ref(localStorage.getItem(COLLAPSE_KEY) === '1')

function toggleCollapse(): void {
  collapsed.value = !collapsed.value
  localStorage.setItem(COLLAPSE_KEY, collapsed.value ? '1' : '0')
}

function go(path: string): void {
  void router.push(path)
}
</script>

<template>
  <div class="flex h-[100dvh] flex-col overflow-hidden bg-bg text-ink md:flex-row">
    <!-- Mobile top bar -->
    <div class="flex h-12 shrink-0 items-center gap-2 border-b border-line px-3 md:hidden">
      <router-link
        to="/"
        class="btn px-2.5"
        title="返回门户"
        aria-label="返回门户"
      >
        ←
      </router-link>
      <select
        class="select max-w-[190px] py-1.5 text-[13px]"
        :value="route.path"
        aria-label="选择工具"
        @change="go(($event.target as HTMLSelectElement).value)"
      >
        <option value="/tools">
          工具箱总览
        </option>
        <option
          v-for="tool in navTools"
          :key="tool.key"
          :value="tool.to"
        >
          {{ tool.title }}
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
      <!-- Header: back-to-portal + collapse toggle -->
      <div
        class="flex h-[52px] shrink-0 items-center border-b border-line"
        :class="collapsed ? 'justify-center px-2' : 'gap-2 px-3'"
      >
        <router-link
          v-if="!collapsed"
          to="/"
          class="inline-flex items-center gap-1.5 rounded-[9px] border border-line bg-card px-2.5 py-1.5 text-[13px] text-dim transition hover:border-line-strong hover:text-ink"
          title="返回门户"
          aria-label="返回门户"
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
          返回门户
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

      <nav class="min-h-0 flex-1 overflow-y-auto p-2">
        <!-- Tools overview -->
        <router-link
          to="/tools"
          class="flex items-center gap-2 rounded-[9px] px-3 py-2 text-sm transition"
          :class="[route.path === '/tools' ? 'bg-accent/10 font-medium text-accent' : 'text-dim hover:bg-soft hover:text-ink', collapsed ? 'justify-center px-2' : '']"
          :title="collapsed ? '工具箱总览' : undefined"
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
            <path d="M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z" />
          </svg>
          <span
            v-if="!collapsed"
            class="truncate"
          >工具箱总览</span>
        </router-link>

        <!-- Divider: overview vs. the tool list -->
        <div
          class="mx-2 my-1.5 h-px bg-line"
          role="separator"
        />

        <!-- Tools -->
        <router-link
          v-for="tool in navTools"
          :key="tool.key"
          :to="tool.to"
          class="flex items-center gap-2 rounded-[9px] px-3 py-2 text-sm transition"
          :class="[route.path === tool.to ? 'bg-accent/10 font-medium text-accent' : 'text-dim hover:bg-soft hover:text-ink', collapsed ? 'justify-center px-2' : '']"
          :title="collapsed ? tool.title : undefined"
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
            <path :d="tool.icon" />
          </svg>
          <span
            v-if="!collapsed"
            class="truncate"
          >{{ tool.title }}</span>
        </router-link>
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
