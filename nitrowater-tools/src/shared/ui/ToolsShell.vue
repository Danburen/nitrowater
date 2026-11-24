<script setup lang="ts">
/**
 * Standalone tools-site shell (no auth / no BFF): desktop sidebar + right-pane top bar + routed content.
 *
 * <p>Deliberately self-contained: no link back to the portal, no shared config. Once the portal
 * opens this site in a new tab, the two origins are completely independent.</p>
 */
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { ToolDef, ToolCategory } from '../../features/tools/catalog'
import GitHubIconButton from './GitHubIconButton.vue'
import PublicIcon from './PublicIcon.vue'
import SvgIcon from './SvgIcon.vue'
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
  <div class="flex h-[100dvh] overflow-hidden bg-bg text-ink">
    <!-- Desktop sidebar: brand + navigation. Collapsed state keeps only the logo (click to expand). -->
    <aside
      class="hidden shrink-0 flex-col border-r border-line bg-soft/40 transition-[width] duration-200 ease-out md:flex"
      :class="collapsed ? 'w-14' : 'w-[210px]'"
    >
      <button
        type="button"
        class="flex h-[52px] shrink-0 cursor-pointer items-center border-b border-line transition hover:bg-soft"
        :class="collapsed ? 'justify-center px-2' : 'gap-2 px-3'"
        :title="collapsed ? '展开导航' : '收起导航'"
        :aria-label="collapsed ? '展开导航' : '收起导航'"
        :aria-expanded="!collapsed"
        @click="toggleCollapse"
      >
        <div class="flex min-w-0 items-center gap-1.5">
          <img
            src="/favicon.svg"
            alt=""
            class="size-[26px] shrink-0"
          >
          <span
            v-if="!collapsed"
            class="truncate text-sm font-semibold text-ink"
          >NitroWater</span>
        </div>
        <PublicIcon
          v-if="!collapsed"
          name="chevron-left"
          :size="16"
          class="ml-auto text-dim"
        />
      </button>

      <nav class="min-h-0 flex-1 overflow-y-auto p-2">
        <router-link
          to="/tools"
          class="flex items-center gap-2 rounded-[9px] px-3 py-2 text-sm transition"
          :class="[active('/tools') ? 'bg-accent/10 font-medium text-accent' : 'text-dim hover:bg-soft hover:text-ink', collapsed ? 'justify-center px-2' : '']"
          :title="collapsed ? '全部工具' : undefined"
        >
          <SvgIcon
            name="grid"
            :size="16"
          />
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
            <PublicIcon
              :name="tool.icon"
              :size="16"
            />
            <span
              v-if="!collapsed"
              class="truncate"
            >{{ tool.title }}</span>
          </router-link>
        </template>
      </nav>
    </aside>

    <!-- Right pane: its own top bar (not spanning the sidebar) and the routed content. -->
    <div class="flex min-w-0 flex-1 flex-col">
      <header class="flex h-[52px] shrink-0 items-center gap-2 border-b border-line px-3">
        <!-- Tool views teleport their title + info here; the catalogue shows a fallback label. -->
        <div
          id="tools-topbar-title"
          class="flex min-w-0 flex-1 items-center gap-1.5"
        >
          <span
            v-if="route.path === '/tools'"
            class="truncate text-sm text-dim"
          >全部工具</span>
        </div>
        <span class="flex shrink-0 items-center gap-2">
          <GitHubIconButton />
          <ThemeToggle />
        </span>
      </header>

      <!-- Mobile tool picker: a slim strip below the top bar. -->
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
      </div>

      <main class="min-h-0 flex-1 overflow-hidden">
        <router-view />
      </main>
    </div>
  </div>
</template>
