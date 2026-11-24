<script setup lang="ts">
/**
 * Standalone tools-site shell (no auth / no BFF): desktop sidebar + mobile select + routed content.
 *
 * <p>Deliberately self-contained: no link back to the portal, no shared config. Once the portal
 * opens this site in a new tab, the two origins are completely independent.</p>
 */
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { ToolDef, ToolCategory } from '../../features/tools/catalog'
import ThemeToggle from './ThemeToggle.vue'

const props = defineProps<{ tools: ToolDef[] }>()

const route = useRoute()
const router = useRouter()

const groups = computed<Array<{ key: ToolCategory; label: string; tools: ToolDef[] }>>(() => {
  const order: ToolCategory[] = ['server', 'general']
  return order.map((key) => ({
    key,
    label: key === 'server' ? '我的世界服务' : '通用功能',
    tools: props.tools.filter((tool) => tool.category === key),
  }))
})

const COLLAPSE_KEY = 'nitrowater:tools-collapsed'
const collapsed = ref(localStorage.getItem(COLLAPSE_KEY) === '1')

function toggleCollapse(): void {
  collapsed.value = !collapsed.value
  localStorage.setItem(COLLAPSE_KEY, collapsed.value ? '1' : '0')
}

function go(to: string): void {
  void router.push(to)
}

function active(to: string): boolean {
  return route.path === to
}
</script>

<template>
  <div class="flex h-[100dvh] flex-col overflow-hidden bg-bg text-ink md:flex-row">
    <!-- Mobile top bar -->
    <div class="flex h-12 shrink-0 items-center gap-2 border-b border-line px-3 md:hidden">
      <select
        class="select max-w-[190px] py-1.5 text-[13px]"
        :value="route.path"
        aria-label="选择导航"
        @change="go(($event.target as HTMLSelectElement).value)"
      >
        <option value="/tools">
          全部工具
        </option>
        <option
          v-for="tool in tools"
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
      <div
        class="flex h-[52px] shrink-0 items-center border-b border-line"
        :class="collapsed ? 'justify-center px-2' : 'gap-2 px-3'"
      >
        <div class="flex min-w-0 items-center gap-1.5 px-1 py-1">
          <img
            src="/favicon.svg"
            alt=""
            class="size-[26px] shrink-0"
          >
          <span
            v-if="!collapsed"
            class="truncate text-sm font-semibold text-ink"
          >离线工具箱</span>
        </div>
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
        <router-link
          to="/tools"
          class="flex items-center gap-2 rounded-[9px] px-3 py-2 text-sm transition"
          :class="[active('/tools') ? 'bg-accent/10 font-medium text-accent' : 'text-dim hover:bg-soft hover:text-ink', collapsed ? 'justify-center px-2' : '']"
          :title="collapsed ? '全部工具' : undefined"
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
          >全部工具</span>
        </router-link>

        <template
          v-for="group in groups"
          :key="group.key"
        >
          <div
            class="px-3 pb-1 pt-3 text-[11px] font-medium uppercase tracking-wide text-faint"
            :class="collapsed ? 'h-px bg-line p-0 mx-2 my-3' : ''"
          >
            <span v-if="!collapsed">{{ group.label }}</span>
          </div>
          <router-link
            v-for="tool in group.tools"
            :key="tool.key"
            :to="tool.to"
            class="flex items-center gap-2 rounded-[9px] px-3 py-2 text-sm transition"
            :class="[active(tool.to) ? 'bg-accent/10 font-medium text-accent' : 'text-dim hover:bg-soft hover:text-ink', collapsed ? 'justify-center px-2' : '']"
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
        </template>
      </nav>

      <div
        class="flex shrink-0 items-center gap-2 border-t border-line p-2"
        :class="collapsed ? 'flex-col' : ''"
      >
        <ThemeToggle />
        <span
          v-if="!collapsed"
          class="min-w-0 flex-1 truncate text-right text-[11.5px] text-faint"
        >本地运行 · 不上传文件</span>
      </div>
    </aside>

    <main class="min-h-0 flex-1 overflow-hidden">
      <router-view />
    </main>
  </div>
</template>
