<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchPlugins, type PluginSummary } from '../api/plugins'
import { toolCategories, readyToolsIn } from '../features/tools/catalog'

/** 插件中心预告：与插件中心同源（业务服务 `GET /api/public/plugins`）。 */
const pluginList = ref<PluginSummary[]>([])

onMounted(async () => {
  try {
    pluginList.value = await fetchPlugins()
  } catch {
    pluginList.value = []
  }
})

/** 工具箱：开服工具在前，通用功能在后。 */
const toolGroups = toolCategories.map((category) => ({
  ...category,
  tools: readyToolsIn(category.key),
}))

// 文档入口暂缓：托管的文档后续再独立分开，入口统一走「插件中心」。
// const docs: { key: string; title: string; sub: string; to?: string; href?: string }[] = [
//   { key: 'arch', title: '项目工程文档', sub: '架构说明 · 部署指南 · API 参考' },
//   { key: 'plugin', title: '插件中心', sub: '插件说明 · 下载 · 提交', to: '/plugins' },
//   { key: 'notes', title: '开发笔记', sub: '更新日志 · 踩坑记录' },
//   { key: 'changelog', title: '更新日志', sub: '版本历史与变更说明' },
// ]

// 服务器目录暂缓：功能尚未开发（「入住」语义待定），先不展示。

const REPO = {
  github: 'https://github.com/danburen/nitrowater',
  gitee: 'https://gitee.com/blackwallet/nitrowarer',
} as const
</script>

<template>
  <div class="min-h-screen">
    <main class="mx-auto max-w-[1120px] px-4 sm:px-6">
      <!-- ===== Hero ===== -->
      <section class="py-[72px] pb-12 text-center">
        <div class="mb-5 inline-block rounded-full border border-accent/25 bg-accent/10 px-3 py-[5px] text-[12.5px] text-accent">
          Minecraft 腐竹 · 开服工具 · 插件中心
        </div>
        <h1 class="mb-4 text-[40px] font-bold leading-tight tracking-tight">
          为 Minecraft 腐竹<br>
          <span class="bg-gradient-to-br from-accent to-accent-2 bg-clip-text text-transparent">提供一站式开服工具</span>
        </h1>
        <p class="mx-auto mb-7 max-w-[600px] text-base text-dim">
          从 MOTD、server.properties 到服务器图标与启动脚本，常用开服工具一站完成；插件说明与下载统一在插件中心。
        </p>
        <div class="flex flex-wrap justify-center gap-3">
          <router-link
            to="/tools"
            class="btn btn-primary"
          >
            进入工具箱
          </router-link>
          <router-link
            to="/plugins"
            class="btn"
          >
            浏览插件中心
          </router-link>
        </div>
      </section>

      <!-- ===== 在线工具箱 ===== -->
      <section
        id="tools"
        class="py-10"
      >
        <div class="mb-[22px] flex items-end justify-between gap-4">
          <div>
            <h2 class="text-[21px] font-semibold tracking-tight">
              在线工具箱
            </h2>
            <p class="mt-1 text-[13.5px] text-dim">
              MOTD · 配置 · 图标 · 启动脚本，开服常用工具一站完成
            </p>
          </div>
          <router-link
            to="/tools"
            class="text-[13.5px] text-accent"
          >
            查看全部 →
          </router-link>
        </div>

        <div
          v-for="group in toolGroups"
          :key="group.key"
          class="mb-7 last:mb-0"
        >
          <div class="mb-3 flex flex-wrap items-baseline gap-x-3 gap-y-1">
            <h3 class="text-[15px] font-semibold">
              {{ group.label }}
            </h3>
            <span class="text-[12.5px] text-dim">{{ group.desc }}</span>
          </div>

          <div class="grid gap-4 grid-cols-[repeat(auto-fill,minmax(240px,1fr))]">
            <router-link
              v-for="tool in group.tools"
              :key="tool.key"
              :to="tool.to"
              class="flex flex-col gap-2.5 rounded-[14px] border border-line bg-card p-[18px] transition hover:-translate-y-0.5 hover:border-line-strong"
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
                    <path :d="tool.icon" />
                  </svg>
                </span>
                <h3 class="text-[15px] font-semibold">
                  {{ tool.title }}
                </h3>
              </div>
              <p class="text-[13px] leading-relaxed text-dim">
                {{ tool.desc }}
              </p>
              <span class="tag w-fit">已上线</span>
            </router-link>
          </div>
        </div>
      </section>

      <!-- ===== 插件中心 ===== -->
      <section
        id="plugins"
        class="py-10"
      >
        <div class="mb-[22px] flex items-end justify-between gap-4">
          <div>
            <h2 class="text-[21px] font-semibold tracking-tight">
              插件中心
            </h2>
            <p class="mt-1 text-[13.5px] text-dim">
              插件说明 · 下载 · 提交
            </p>
          </div>
          <router-link
            to="/plugins"
            class="text-[13.5px] text-accent"
          >
            进入插件中心 →
          </router-link>
        </div>

        <p
          v-if="pluginList.length === 0"
          class="text-[13px] text-dim"
        >
          暂无插件。
        </p>
        <div
          v-else
          class="grid gap-4 grid-cols-[repeat(auto-fill,minmax(300px,1fr))]"
        >
          <router-link
            v-for="p in pluginList"
            :key="p.key"
            :to="`/plugins/${p.key}`"
            class="flex flex-col gap-2.5 rounded-[14px] border border-line bg-card p-[18px] transition hover:-translate-y-0.5 hover:border-line-strong"
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
                <h3 class="truncate text-[15px] font-semibold">
                  {{ p.name }}
                </h3>
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
          </router-link>
        </div>
      </section>

      <!-- ===== Footer ===== -->
      <footer class="mt-14 flex flex-wrap justify-between gap-3 border-t border-line py-7 text-[13px] text-dim">
        <div>© 2026 NitroWater · 为 Minecraft 腐竹提供一站式服务</div>
        <div class="flex gap-[18px]">
          <router-link
            to="/about"
            class="transition hover:text-ink"
          >
            关于
          </router-link>
          <a
            :href="REPO.github"
            target="_blank"
            rel="noreferrer"
            class="transition hover:text-ink"
          >GitHub</a>
          <a
            :href="REPO.gitee"
            target="_blank"
            rel="noreferrer"
            class="transition hover:text-ink"
          >Gitee</a>
        </div>
      </footer>
    </main>
  </div>
</template>
