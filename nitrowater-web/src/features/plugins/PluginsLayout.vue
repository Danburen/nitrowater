<script setup lang="ts">
/**
 * Plugin center shell — the shared SectionLayout (like 工具箱) configured for 插件中心.
 *
 * The left sidebar lists the plugins (with a search box supplied via the `sidebar-extra` slot);
 * the selected plugin's single-page 说明 renders in the routed page on the right. The list is read
 * from the business service (`GET /api/public/plugins`) so it stays in sync with the admin console.
 */
import { computed, onMounted, ref } from 'vue'
import SectionLayout from '../../shared/ui/SectionLayout.vue'
import { fetchPlugins, type PluginSummary } from '../../api/plugins'

const plugins = ref<PluginSummary[]>([])
const query = ref('')

/** Overview entry above the divider (mirrors the tools layout). */
const overview = {
  label: '插件中心总览',
  to: '/plugins',
  icon: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
}

const nav = computed(() =>
  plugins.value.map((p) => ({ label: p.name, to: `/plugins/${p.key}`, icon: p.icon ?? '' })),
)

/** Search filters the sidebar by name / tagline / tags; the routed page is unaffected. */
const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return nav.value
  return plugins.value
    .filter((p) => `${p.name} ${p.tagline ?? ''} ${p.tags.join(' ')}`.toLowerCase().includes(q))
    .map((p) => ({ label: p.name, to: `/plugins/${p.key}`, icon: p.icon ?? '' }))
})

onMounted(async () => {
  try {
    plugins.value = await fetchPlugins()
  } catch {
    plugins.value = []
  }
})
</script>

<template>
  <SectionLayout
    title="插件中心"
    :nav="filtered"
    :overview="overview"
    collapse-key="nitrowater:plugins-sidebar-collapsed"
  >
    <template #sidebar-extra>
      <input
        v-model="query"
        type="search"
        class="input h-9 w-full text-[13px]"
        placeholder="搜索插件…"
        aria-label="搜索插件"
      >
    </template>
  </SectionLayout>
</template>
