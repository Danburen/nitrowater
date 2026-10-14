<script setup lang="ts">
/**
 * Plugin center overview — the landing page shown at `/plugins`.
 *
 * Lists the integrated plugins (cards) and explains how the center works + how to submit more.
 * Clicking a card opens that plugin's single-page 说明 at `/plugins/:key`.
 */
import { plugins, SUBMIT_PLUGIN_URL } from './catalog'
</script>

<template>
  <div class="min-h-full">
    <div class="mx-auto max-w-[900px] px-6 py-8">
      <header class="pb-6">
        <h1 class="text-[26px] font-bold tracking-tight">
          插件中心
        </h1>
        <p class="mt-2 max-w-[680px] text-sm leading-relaxed text-dim">
          这里汇总本站接入的插件：左侧可<b class="text-ink">选择 / 搜索</b>，右侧展示每个插件的单页说明。
          每个插件都提供
          <b class="text-ink">本地下载</b>（历史版本随取）、<b class="text-ink">完整文档</b>（独立静态站）
          与仓库链接。
        </p>
      </header>

      <section class="grid gap-4 grid-cols-[repeat(auto-fill,minmax(300px,1fr))]">
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
                <path :d="p.icon" />
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
            {{ p.desc }}
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
  </div>
</template>
