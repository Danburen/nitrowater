<script setup lang="ts">
import { computed } from 'vue'
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
    <aside class="hidden w-[210px] shrink-0 flex-col border-r border-line bg-soft/40 md:flex">
      <div class="flex h-[52px] shrink-0 items-center border-b border-line px-3">
        <router-link
          to="/"
          class="inline-flex items-center gap-1.5 rounded-[9px] border border-line bg-card px-2.5 py-1.5 text-[13px] text-dim transition hover:border-line-strong hover:text-ink"
        >
          ← 返回门户
        </router-link>
      </div>

      <nav class="min-h-0 flex-1 overflow-y-auto p-2">
        <router-link
          to="/tools"
          class="mb-1 flex items-center rounded-[9px] px-3 py-2 text-sm transition"
          :class="route.path === '/tools' ? 'bg-accent/10 font-medium text-accent' : 'text-dim hover:bg-soft hover:text-ink'"
        >
          工具箱总览
        </router-link>
        <router-link
          v-for="tool in navTools"
          :key="tool.key"
          :to="tool.to"
          class="flex items-center gap-2 rounded-[9px] px-3 py-2 text-sm transition"
          :class="route.path === tool.to ? 'bg-accent/10 font-medium text-accent' : 'text-dim hover:bg-soft hover:text-ink'"
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
          <span class="truncate">{{ tool.title }}</span>
        </router-link>
      </nav>

      <div class="flex shrink-0 items-center gap-2 border-t border-line p-2">
        <ThemeToggle />
        <button
          v-if="!isLoggedIn"
          class="btn flex-1 justify-center"
          :disabled="loading"
          @click="login"
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
      </div>
    </aside>

    <!-- Content -->
    <main class="min-h-0 flex-1 overflow-hidden">
      <router-view />
    </main>
  </div>
</template>
