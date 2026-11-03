<script setup lang="ts">
/**
 * Plugin detail — one plugin at `/plugins/:key`.
 *
 * Layout (Bukkit-style): a header (name / tagline / tags), an action bar (下载最新版 / 查看完整文档 /
 * 条件外链), then two tabs —【介绍】renders the plugin's single-page markdown (read from the business
 * service) and【文件】lists the published downloads.
 *
 * Two independent reads run in parallel on mount / key change:
 * - metadata + 说明 : `GET /api/public/plugins/{key}` (see `fetchPlugin`);
 * - downloads       : `GET /api/public/resources?app=…&group=<key>` (see `fetchPluginResources`).
 * The download list is fetched eagerly (not on tab switch) so 下载最新版 works from either tab.
 */
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { useRoute } from 'vue-router'
import { useAuth } from '../../auth/useAuth'
import {
  fetchPlugin,
  fetchPluginResources,
  SUBMIT_PLUGIN_URL,
  type PluginDetail,
  type PluginResource,
} from '../../api/plugins'

/** Lazy: the editor (and `md-editor-v3`) must never enter the public bundle. */
const PluginEditorDrawer = defineAsyncComponent(() => import('./PluginEditorDrawer.vue'))

const route = useRoute()
const { isAdmin } = useAuth()

const TABS = [
  { value: 'intro', label: '介绍' },
  { value: 'files', label: '文件' },
] as const
type TabValue = (typeof TABS)[number]['value']

const key = computed(() => String(route.params.key ?? ''))

const plugin = ref<PluginDetail | null>(null)
const loadingPlugin = ref(true)
const pluginError = ref(false)

const files = ref<PluginResource[]>([])
const loadingRes = ref(true)
const resError = ref(false)

const tab = ref<TabValue>('intro')
const editorOpen = ref(false)

/**
 * Sanitized readme HTML. `readme` is author-authored, but a compromised admin account must not be
 * able to turn the public detail page into a stored-XSS vector, so every render goes through
 * DOMPurify.
 */
const mdHtml = computed(() =>
  plugin.value?.readme
    ? DOMPurify.sanitize(marked.parse(plugin.value.readme) as string, { USE_PROFILES: { html: true } })
    : '',
)
const latest = computed(() => files.value[0] ?? null)

async function load(): Promise<void> {
  const k = key.value
  plugin.value = null
  pluginError.value = false
  files.value = []
  resError.value = false
  tab.value = 'intro'
  loadingPlugin.value = true
  loadingRes.value = true
  if (!k) {
    loadingPlugin.value = false
    loadingRes.value = false
    return
  }
  const [pluginResult, filesResult] = await Promise.allSettled([
    fetchPlugin(k),
    fetchPluginResources(k),
  ])
  if (pluginResult.status === 'fulfilled') {
    plugin.value = pluginResult.value
  } else {
    pluginError.value = true
  }
  if (filesResult.status === 'fulfilled') {
    files.value = filesResult.value
  } else {
    resError.value = true
  }
  loadingPlugin.value = false
  loadingRes.value = false
}

/** Refreshes data in place after an inline edit, keeping the current tab (no scroll jump). */
async function refresh(): Promise<void> {
  const k = key.value
  if (!k) return
  const [pluginResult, filesResult] = await Promise.allSettled([
    fetchPlugin(k),
    fetchPluginResources(k),
  ])
  if (pluginResult.status === 'fulfilled') {
    plugin.value = pluginResult.value
  }
  if (filesResult.status === 'fulfilled') {
    files.value = filesResult.value
    resError.value = false
  }
}

watch(key, load, { immediate: true })

function fmtSize(bytes: number | null): string {
  if (bytes == null) return '—'
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
  return `${bytes} B`
}
function fmtDate(iso: string | null): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toISOString().slice(0, 10)
}
</script>

<template>
  <!-- The section layout's <main> clips overflow (overflow-hidden) and delegates scrolling to the
       page, so this root must own a definite height + its own scroll container. -->
  <div class="h-full overflow-y-auto">
    <div class="mx-auto max-w-[860px] px-4 py-6 sm:px-6 sm:py-8">
      <p
        v-if="loadingPlugin"
        class="text-[13px] text-dim"
      >
        正在读取插件信息…
      </p>

      <template v-else-if="plugin">
        <!-- Header -->
        <header class="pb-6">
          <div class="flex items-center gap-3">
            <span class="grid size-[44px] shrink-0 place-items-center rounded-[12px] border border-accent/25 bg-gradient-to-br from-accent/15 to-accent-2/15 text-accent">
              <svg
                class="size-6"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path :d="plugin.icon ?? ''" />
              </svg>
            </span>
            <div class="min-w-0">
              <h1 class="text-[24px] font-bold tracking-tight">
                {{ plugin.name }}
              </h1>
              <p class="text-[13px] text-dim">
                {{ plugin.tagline }}
              </p>
            </div>
          </div>
          <div
            v-if="plugin.tags.length"
            class="mt-3 flex flex-wrap gap-1.5"
          >
            <span
              v-for="t in plugin.tags"
              :key="t"
              class="tag"
            >{{ t }}</span>
          </div>
        </header>

        <!-- Actions -->
        <div class="flex flex-wrap items-center gap-3 border-y border-line py-4">
          <a
            v-if="latest"
            :href="latest.url"
            download
            class="btn btn-primary"
          >↓ 下载最新版 ({{ latest.version ?? latest.name ?? 'jar' }})</a>
          <span
            v-else-if="loadingRes"
            class="text-[13px] text-dim"
          >正在读取下载列表…</span>
          <a
            v-if="plugin.docsUrl"
            :href="plugin.docsUrl"
            target="_blank"
            rel="noreferrer"
            class="btn"
          >查看完整文档 ↗</a>
          <a
            v-if="plugin.homepageUrl"
            :href="plugin.homepageUrl"
            target="_blank"
            rel="noreferrer"
            class="text-[13.5px] text-accent transition hover:opacity-80"
          >官网 ↗</a>
          <a
            v-if="plugin.repoUrl"
            :href="plugin.repoUrl"
            target="_blank"
            rel="noreferrer"
            class="text-[13.5px] text-accent transition hover:opacity-80"
          >源代码 ↗</a>
          <a
            v-if="plugin.sponsorUrl"
            :href="plugin.sponsorUrl"
            target="_blank"
            rel="noreferrer"
            class="text-[13.5px] text-accent transition hover:opacity-80"
          >赞助 ↗</a>
          <button
            v-if="isAdmin"
            type="button"
            class="btn"
            @click="editorOpen = true"
          >
            编辑
          </button>
          <a
            :href="SUBMIT_PLUGIN_URL"
            target="_blank"
            rel="noreferrer"
            class="ml-auto text-[13.5px] text-accent transition hover:opacity-80"
          >提供更多插件 ↗</a>
        </div>

        <!-- Tabs -->
        <div class="mt-5 flex gap-1 border-b border-line">
          <button
            v-for="t in TABS"
            :key="t.value"
            type="button"
            class="-mb-px border-b-2 px-3 py-2 text-[13.5px] transition"
            :class="tab === t.value
              ? 'border-accent font-medium text-accent'
              : 'border-transparent text-dim hover:text-ink'"
            @click="tab = t.value"
          >
            {{ t.label }}{{ t.value === 'files' && files.length ? ` (${files.length})` : '' }}
          </button>
        </div>

        <!-- Tab: 介绍 -->
        <section
          v-if="tab === 'intro'"
          class="pt-6"
        >
          <template v-if="mdHtml">
            <!-- eslint-disable vue/no-v-html -- author-authored markdown -->
            <div
              class="md-body"
              v-html="mdHtml"
            />
            <!-- eslint-enable vue/no-v-html -->
          </template>
          <p
            v-else
            class="text-[13px] text-dim"
          >
            该插件暂无单页说明。
            <template v-if="plugin.docsUrl">
              可前往
              <a
                :href="plugin.docsUrl"
                target="_blank"
                rel="noreferrer"
                class="text-accent"
              >完整文档</a>。
            </template>
          </p>
        </section>

        <!-- Tab: 文件 -->
        <section
          v-else
          class="pt-6"
        >
          <p
            v-if="loadingRes"
            class="text-[13px] text-dim"
          >
            正在读取下载列表…
          </p>
          <p
            v-else-if="resError"
            class="text-[13px] text-dim"
          >
            暂时无法读取下载列表，可直接前往
            <a
              v-if="plugin.docsUrl"
              :href="plugin.docsUrl"
              target="_blank"
              rel="noreferrer"
              class="text-accent"
            >完整文档</a>
            <template v-if="plugin.repoUrl">
              或
              <a
                :href="plugin.repoUrl + '/releases'"
                target="_blank"
                rel="noreferrer"
                class="text-accent"
              >GitHub Releases</a>
            </template>。
          </p>
          <div
            v-else-if="files.length"
            class="overflow-hidden rounded-[12px] border border-line"
          >
            <div
              v-for="(f, i) in files"
              :key="f.uuid"
              class="flex items-center gap-3 border-b border-line px-4 py-2.5 last:border-b-0"
              :class="i === 0 ? 'bg-accent/5' : ''"
            >
              <span class="min-w-0 flex-1">
                <span class="block truncate text-[13.5px] font-medium text-ink">{{ f.name ?? f.uuid }}</span>
                <span class="block text-[12px] text-faint">{{ fmtDate(f.createdAt) }} · {{ fmtSize(f.sizeBytes) }}</span>
              </span>
              <span
                v-if="i === 0"
                class="tag tag-lock shrink-0"
              >最新</span>
              <span
                v-if="f.version"
                class="hidden shrink-0 text-[12.5px] text-dim sm:inline"
              >v{{ f.version }}</span>
              <a
                :href="f.url"
                download
                class="shrink-0 text-[13.5px] text-accent transition hover:opacity-80"
              >下载</a>
            </div>
          </div>
          <p
            v-else
            class="text-[13px] text-dim"
          >
            暂无可用下载。
          </p>
        </section>
      </template>

      <template v-else>
        <h1 class="text-[22px] font-bold tracking-tight">
          {{ pluginError ? '插件信息加载失败' : '未找到该插件' }}
        </h1>
        <p class="mt-2 text-sm text-dim">
          {{ pluginError ? '请稍后刷新重试，或' : '请从左侧列表选择插件，或' }}
          <router-link
            to="/plugins"
            class="text-accent"
          >
            返回插件中心
          </router-link>。
        </p>
      </template>
    </div>

    <PluginEditorDrawer
      :open="editorOpen"
      :plugin-key="key"
      @close="editorOpen = false"
      @saved="refresh"
    />
  </div>
</template>

<style>
/* Rendered plugin markdown (author-authored, rendered via marked). */
.md-body {
  color: var(--ink);
  font-size: 14px;
  line-height: 1.75;
}
.md-body h1,
.md-body h2,
.md-body h3,
.md-body h4 {
  margin: 1.3em 0 0.6em;
  font-weight: 600;
  line-height: 1.3;
}
.md-body h1 {
  font-size: 1.5em;
}
.md-body h2 {
  font-size: 1.25em;
}
.md-body h3 {
  font-size: 1.1em;
}
.md-body h4 {
  font-size: 1em;
}
.md-body p {
  margin: 0.7em 0;
}
.md-body ul,
.md-body ol {
  margin: 0.7em 0;
  padding-left: 1.5em;
}
.md-body ul {
  list-style: disc;
}
.md-body ol {
  list-style: decimal;
}
.md-body li {
  margin: 0.25em 0;
}
.md-body a {
  color: var(--accent);
  text-decoration: none;
}
.md-body a:hover {
  text-decoration: underline;
}
.md-body strong {
  font-weight: 600;
}
.md-body blockquote {
  margin: 0.8em 0;
  padding: 0.4em 0 0.4em 0.9em;
  border-left: 3px solid var(--accent);
  color: var(--dim);
}
.md-body code {
  padding: 0.1em 0.35em;
  border-radius: 4px;
  background: var(--soft);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.92em;
}
.md-body pre {
  margin: 0.8em 0;
  padding: 0.8em 1em;
  overflow-x: auto;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--soft);
  font-size: 12.5px;
}
.md-body pre code {
  padding: 0;
  background: none;
}
.md-body table {
  width: 100%;
  margin: 0.9em 0;
  border-collapse: collapse;
  font-size: 13px;
}
.md-body th,
.md-body td {
  padding: 0.5em 0.7em;
  border: 1px solid var(--line);
  text-align: left;
}
.md-body th {
  background: var(--soft);
  font-weight: 600;
}
.md-body hr {
  margin: 1.4em 0;
  border: 0;
  border-top: 1px solid var(--line);
}
</style>
