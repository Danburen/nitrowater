<script setup lang="ts">
/**
 * Plugin detail — the single-page 说明 for one plugin (`/plugins/:key`).
 *
 * Layout: a header (name / tagline / tags / repo), an action bar (下载最新版 / 查看完整文档 /
 * 提交插件), the download version list, then the bundled markdown rendered by `marked`.
 *
 * Downloads are the plugin's own mirrored jars: the version list is fetched from the plugin's
 * `res/manifest.json` (produced by bin/sync-res.ps1), so publishing a new jar needs no code change.
 */
import { computed, ref, watch } from 'vue'
import { marked } from 'marked'
import { useRoute } from 'vue-router'
import { findPlugin, SUBMIT_PLUGIN_URL } from './catalog'

interface ResFile {
  name: string
  version: string | null
  bytes: number
  modified: string
}

const route = useRoute()
const plugin = computed(() => findPlugin(String(route.params.key ?? '')))
const mdHtml = computed(() => (plugin.value ? (marked.parse(plugin.value.markdown) as string) : ''))

const files = ref<ResFile[]>([])
const loadingRes = ref(false)
const resError = ref(false)

async function loadManifest(): Promise<void> {
  const p = plugin.value
  files.value = []
  resError.value = false
  if (!p) return
  loadingRes.value = true
  try {
    const res = await fetch(`${p.res}manifest.json`, { cache: 'no-cache' })
    if (!res.ok) throw new Error(String(res.status))
    const data = (await res.json()) as { files?: ResFile[] }
    files.value = Array.isArray(data.files) ? data.files : []
  } catch {
    resError.value = true
  } finally {
    loadingRes.value = false
  }
}

watch(() => route.params.key, loadManifest, { immediate: true })

const latest = computed(() => files.value[0] ?? null)

function fmtSize(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
  return `${bytes} B`
}
function fmtDate(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toISOString().slice(0, 10)
}
</script>

<template>
  <div class="min-h-full">
    <div class="mx-auto max-w-[860px] px-6 py-8">
      <template v-if="plugin">
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
                <path :d="plugin.icon" />
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
          <div class="mt-3 flex flex-wrap gap-1.5">
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
            :href="plugin.res + latest.name"
            download
            class="btn btn-primary"
          >↓ 下载最新版 ({{ latest.version ?? latest.name }})</a>
          <span
            v-else-if="loadingRes"
            class="text-[13px] text-dim"
          >正在读取下载列表…</span>
          <a
            :href="plugin.docs"
            target="_blank"
            rel="noreferrer"
            class="btn"
          >查看完整文档 ↗</a>
          <a
            :href="SUBMIT_PLUGIN_URL"
            target="_blank"
            rel="noreferrer"
            class="text-[13.5px] text-accent transition hover:opacity-80"
          >提供更多插件 ↗</a>
          <a
            v-if="plugin.repo"
            :href="plugin.repo"
            target="_blank"
            rel="noreferrer"
            class="ml-auto text-[13.5px] text-accent transition hover:opacity-80"
          >源代码 ↗</a>
        </div>

        <!-- Download versions -->
        <section class="pt-6">
          <h2 class="mb-3 text-[15px] font-semibold">
            下载 · 历史版本
          </h2>
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
              :href="plugin.docs"
              target="_blank"
              rel="noreferrer"
              class="text-accent"
            >完整文档</a>
            或
            <a
              v-if="plugin.repo"
              :href="plugin.repo + '/releases'"
              target="_blank"
              rel="noreferrer"
              class="text-accent"
            >GitHub Releases</a>。
          </p>
          <div
            v-else-if="files.length"
            class="overflow-hidden rounded-[12px] border border-line"
          >
            <div
              v-for="(f, i) in files"
              :key="f.name"
              class="flex items-center gap-3 border-b border-line px-4 py-2.5 last:border-b-0"
              :class="i === 0 ? 'bg-accent/5' : ''"
            >
              <span class="min-w-0 flex-1">
                <span class="block truncate text-[13.5px] font-medium text-ink">{{ f.name }}</span>
                <span class="block text-[12px] text-faint">{{ fmtDate(f.modified) }} · {{ fmtSize(f.bytes) }}</span>
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
                :href="plugin.res + f.name"
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

        <!-- Single-page 说明 -->
        <section class="pt-8">
          <!-- eslint-disable vue/no-v-html -- author-authored markdown -->
          <div
            class="md-body"
            v-html="mdHtml"
          />
          <!-- eslint-enable vue/no-v-html -->
        </section>
      </template>

      <template v-else>
        <h1 class="text-[22px] font-bold tracking-tight">
          未找到该插件
        </h1>
        <p class="mt-2 text-sm text-dim">
          请从左侧列表选择插件，或
          <router-link
            to="/plugins"
            class="text-accent"
          >
            返回插件中心
          </router-link>。
        </p>
      </template>
    </div>
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
