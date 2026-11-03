<script setup lang="ts">
/**
 * Plugin center overview — the landing page shown at `/plugins`.
 *
 * Lists the integrated plugins (cards, read from `GET /api/public/plugins`) and explains how the
 * center works + how to submit more. Clicking a card opens that plugin's single-page 说明 at
 * `/plugins/:key`.
 */
import { defineAsyncComponent, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../../auth/useAuth'
import { fetchPlugins, SUBMIT_PLUGIN_URL, type PluginSummary } from '../../api/plugins'

/** Lazy: the editor (and `md-editor-v3`) must never enter the public bundle. */
const PluginEditorDrawer = defineAsyncComponent(() => import('./PluginEditorDrawer.vue'))

const router = useRouter()
const { isAdmin } = useAuth()

const plugins = ref<PluginSummary[]>([])
const loading = ref(true)
const failed = ref(false)
const editorOpen = ref(false)

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    plugins.value = await fetchPlugins()
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate(): void {
  editorOpen.value = true
}

async function onSaved(key: string): Promise<void> {
  editorOpen.value = false
  await load()
  await router.push(`/plugins/${key}`)
}
</script>

<template>
  <!-- Definite height + own scroll container: the parent <main> is overflow-hidden. -->
  <div class="h-full overflow-y-auto">
    <div class="mx-auto max-w-[900px] px-4 py-6 sm:px-6 sm:py-8">
      <header class="flex flex-wrap items-start justify-between gap-3 pb-6">
        <div>
          <h1 class="text-[26px] font-bold tracking-tight">
            插件中心
          </h1>
          <p class="mt-2 max-w-[680px] text-sm leading-relaxed text-dim">
            这里汇总本站接入的插件：左侧可<b class="text-ink">选择 / 搜索</b>，右侧展示每个插件的单页说明。
            每个插件都提供
            <b class="text-ink">本地下载</b>（历史版本随取）、<b class="text-ink">完整文档</b>（独立静态站）
            与仓库链接。
          </p>
        </div>
        <button
          v-if="isAdmin"
          type="button"
          class="btn btn-primary shrink-0"
          @click="openCreate"
        >
          新建插件
        </button>
      </header>

      <p
        v-if="loading"
        class="text-[13px] text-dim"
      >
        正在读取插件列表…
      </p>
      <p
        v-else-if="failed"
        class="text-[13px] text-dim"
      >
        暂时无法读取插件列表，请稍后刷新。
      </p>

      <section
        v-else
        class="grid gap-4 grid-cols-[repeat(auto-fill,minmax(300px,1fr))]"
      >
        <router-link
          v-for="p in plugins"
          :key="p.key"
          :to="`/plugins/${p.key}`"
          class="flex flex-col gap-3 rounded-[14px] border border-line bg-card p-[18px] transition hover:-translate-y-0.5 hover:border-line-strong"
        >
          <div class="flex items-center gap-3">
            <span class="grid size-[38px] shrink-0 place-items-center rounded-[10px] border border-accent/25 bg-gradient-to-br from-accent/15 to-accent-2/15 text-accent">
              <svg
                class="size-5"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path :d="p.icon ?? ''" />
              </svg>
            </span>
            <div class="min-w-0">
              <h2 class="truncate text-[15px] font-semibold">
                {{ p.name }}
              </h2>
              <p class="truncate text-[12.5px] text-dim">
                {{ p.tagline }}
              </p>
            </div>
          </div>
          <p class="text-[13px] leading-relaxed text-dim">
            {{ p.summary }}
          </p>
          <div class="flex flex-wrap gap-1.5">
            <span
              v-for="t in p.tags"
              :key="t"
              class="tag"
            >{{ t }}</span>
          </div>
          <span class="mt-auto pt-1 text-[13px] font-medium text-accent">查看说明 →</span>
        </router-link>
      </section>

      <section class="mt-8 flex flex-wrap items-center justify-between gap-4 rounded-[14px] border border-accent/25 bg-gradient-to-br from-accent/10 to-accent-2/10 p-6">
        <div>
          <h3 class="mb-1 text-base font-semibold">
            提供更多插件
          </h3>
          <p class="text-[13px] text-dim">
            有想接入的插件，或想把自己写的插件放到这里？在仓库提一个 Issue 即可。
          </p>
        </div>
        <a
          :href="SUBMIT_PLUGIN_URL"
          target="_blank"
          rel="noreferrer"
          class="btn btn-primary"
        >提交插件 →</a>
      </section>
    </div>

    <PluginEditorDrawer
      :open="editorOpen"
      :plugin-key="null"
      @close="editorOpen = false"
      @saved="onSaved"
    />
  </div>
</template>
