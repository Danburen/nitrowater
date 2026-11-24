<script setup lang="ts">
import PublicIcon from '../../shared/ui/PublicIcon.vue'
import { toolCategories, readyToolsIn } from './catalog'

/** Two labelled groups, server tools first. */
const groups = toolCategories.map((category) => ({
  ...category,
  tools: readyToolsIn(category.key),
}))
</script>

<template>
  <div class="h-full overflow-y-auto px-4 py-6 sm:px-6">
    <header class="mb-6">
      <h1 class="text-lg font-semibold tracking-tight text-ink">
        离线工具箱
      </h1>
      <p class="mt-1 text-sm text-dim">
        本工具箱全部浏览器端运行。
      </p>
    </header>

    <section
      v-for="group in groups"
      :key="group.key"
      class="mb-8 last:mb-0"
    >
      <div class="mb-3 flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <h2 class="text-[15px] font-semibold text-ink">
          {{ group.label }}
        </h2>
        <span class="text-[12.5px] text-dim">{{ group.desc }}</span>
      </div>

      <div class="grid gap-3 grid-cols-[repeat(auto-fill,minmax(260px,1fr))]">
        <component
          :is="tool.to ? 'router-link' : 'div'"
          v-for="tool in group.tools"
          :key="tool.key"
          :to="tool.to"
          class="group flex gap-2.5 rounded-[14px] border border-line bg-card p-3 transition hover:border-line-strong"
          :class="{ 'opacity-70': tool.status === 'soon' }"
        >
          <span class="grid size-9 shrink-0 self-center place-items-center rounded-xl border border-accent/25 bg-gradient-to-br from-accent/15 to-accent-2/15 text-accent">
            <PublicIcon
              :name="tool.icon"
              :size="18"
            />
          </span>
          <span class="min-w-0">
            <span class="flex items-center gap-1.5">
              <span class="font-medium text-ink">{{ tool.title }}</span>
              <span
                v-if="tool.status === 'soon'"
                class="rounded-full border border-line px-2 py-0.5 text-[10px] uppercase tracking-wide text-faint"
              >soon</span>
            </span>
            <span class="mt-0.5 block text-[13px] leading-relaxed text-dim">{{ tool.desc }}</span>
            <span class="mt-1.5 flex flex-wrap gap-1.5">
              <span
                v-for="tag in tool.tags"
                :key="tag"
                class="tag"
              >{{ tag }}</span>
            </span>
          </span>
        </component>
      </div>
    </section>
  </div>
</template>
