<script setup lang="ts">
import { tools } from './catalog'
</script>

<template>
  <div class="h-full overflow-y-auto p-5">
    <header class="mb-5">
      <h1 class="text-lg font-semibold tracking-tight text-ink">
        在线工具箱
      </h1>
      <p class="mt-1 text-sm text-dim">
        纯前端本地计算，文件与数据不会上传服务器。
      </p>
    </header>

    <div class="grid gap-4 grid-cols-[repeat(auto-fill,minmax(260px,1fr))]">
      <component
        :is="tool.to ? 'router-link' : 'div'"
        v-for="tool in tools"
        :key="tool.key"
        :to="tool.to"
        class="group flex gap-3 rounded-[14px] border border-line bg-card p-4 transition hover:border-line-strong"
        :class="{ 'opacity-70': tool.status === 'soon' }"
      >
        <span class="grid size-10 shrink-0 place-items-center rounded-xl border border-accent/25 bg-gradient-to-br from-accent/15 to-accent-2/15 text-accent">
          <svg
            class="size-5"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path :d="tool.icon" />
          </svg>
        </span>
        <span class="min-w-0">
          <span class="flex items-center gap-2">
            <span class="font-medium text-ink">{{ tool.title }}</span>
            <span
              v-if="tool.status === 'soon'"
              class="rounded-full border border-line px-2 py-0.5 text-[10px] uppercase tracking-wide text-faint"
            >soon</span>
          </span>
          <span class="mt-0.5 block text-[13px] leading-relaxed text-dim">{{ tool.desc }}</span>
          <span class="mt-2 flex flex-wrap gap-1.5">
            <span
              v-for="tag in tool.tags"
              :key="tag"
              class="tag"
            >{{ tag }}</span>
          </span>
        </span>
      </component>
    </div>

    <!-- Divider + shortcut to the standalone coding playground -->
    <div class="mt-6 border-t border-line pt-5">
      <router-link
        to="/playground"
        class="flex items-center gap-3 rounded-[14px] border border-accent/25 bg-gradient-to-br from-accent/10 to-accent-2/10 px-4 py-3.5 transition hover:border-accent/40"
      >
        <span class="grid size-10 shrink-0 place-items-center rounded-xl border border-accent/25 bg-gradient-to-br from-accent/15 to-accent-2/15 text-accent">
          <svg
            class="size-5"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M6 3h9l5 5v13H6zM15 3v5h5M9 15l2 2 4-4" />
          </svg>
        </span>
        <span class="min-w-0">
          <span class="flex items-center gap-2">
            <span class="font-medium text-ink">编程演练场</span>
            <span class="tag">ACM · 需登录</span>
          </span>
          <span class="mt-0.5 block text-[13px] leading-relaxed text-dim">ACM 模式在线判题：浏览题目、提交代码、查看评测结果。</span>
        </span>
        <span class="ml-auto shrink-0 text-accent">进入 →</span>
      </router-link>
    </div>

    <router-link
      to="/about#credits"
      class="mt-5 flex items-center justify-between gap-3 rounded-[14px] border border-line bg-card px-4 py-3.5 text-[13.5px] transition hover:border-line-strong"
    >
      <span class="flex items-center gap-2 text-dim">
        <svg
          class="size-4 shrink-0 text-accent"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
        </svg>
        本站工具构建在众多开源项目之上
      </span>
      <span class="shrink-0 text-accent">开源鸣谢 →</span>
    </router-link>
  </div>
</template>
