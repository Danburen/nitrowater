<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { creditGroups } from '../shared/data/credits'

const route = useRoute()
const router = useRouter()

const REPO = {
  github: 'https://github.com/danburen/nitrowater',
  gitee: 'https://gitee.com/blackwallet/nitrowarer',
} as const

/** Tech-stack chips, grouped by layer. */
const stack = [
  { group: '前端', items: ['Vue 3', 'TypeScript', 'Vite', 'Tailwind CSS v4', 'Element Plus', 'vue-router'] },
  { group: '后端', items: ['Java 25', 'Spring Boot 4.1.1', 'Spring Security 7', 'Spring Authorization Server 7', 'JPA / MySQL 8', 'Redis', 'Flyway'] },
  { group: '架构', items: ['OIDC SSO', 'BFF Token Handler', 'Resource Server', 'VitePress 文档'] },
]

/** Return to where we came from; fall back to the portal when there is no history. */
function goBack(): void {
  const state = window.history.state as { back?: string } | null
  if (state?.back) {
    router.back()
  } else {
    void router.push('/')
  }
}

/** Deep-link support: /about#credits should land on the credits section. */
onMounted(() => {
  if (route.hash) {
    document.getElementById(route.hash.slice(1))?.scrollIntoView({ behavior: 'smooth' })
  }
})
</script>

<template>
  <div class="min-h-screen">
    <main class="mx-auto max-w-[1120px] px-6">
      <div class="pt-8">
        <button
          type="button"
          class="btn"
          @click="goBack"
        >
          ← 返回
        </button>
      </div>

      <!-- ===== 站点简介 ===== -->
      <section class="py-10">
        <h1 class="text-[28px] font-bold tracking-tight">
          关于 NitroWater
        </h1>
        <p class="mt-3 max-w-[760px] text-sm leading-relaxed text-dim">
          NitroWater 是一个多项目后端平台，包含一个聚合展示站点（导航 · 工具箱 · 编程演练场 · 文档）与独立的 SSO / OIDC 身份中心。
          所有本地工具都在浏览器内完成计算，文件与数据不会上传服务器。
        </p>
        <div class="mt-5 flex flex-wrap gap-[18px] text-[13.5px]">
          <a
            :href="REPO.github"
            target="_blank"
            rel="noreferrer"
            class="text-accent transition hover:opacity-80"
          >GitHub 仓库 ↗</a>
          <a
            :href="REPO.gitee"
            target="_blank"
            rel="noreferrer"
            class="text-accent transition hover:opacity-80"
          >Gitee 仓库 ↗</a>
        </div>
      </section>

      <!-- ===== 技术栈 ===== -->
      <section class="py-6">
        <h2 class="text-[21px] font-semibold tracking-tight">
          技术栈
        </h2>
        <div class="mt-4 grid gap-3 sm:grid-cols-3">
          <div
            v-for="block in stack"
            :key="block.group"
            class="rounded-[14px] border border-line bg-card p-4"
          >
            <h3 class="mb-2.5 text-[13px] font-medium uppercase tracking-wide text-faint">
              {{ block.group }}
            </h3>
            <div class="flex flex-wrap gap-1.5">
              <span
                v-for="item in block.items"
                :key="item"
                class="tag"
              >{{ item }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- ===== 开源鸣谢 ===== -->
      <section
        id="credits"
        class="py-10"
      >
        <h2 class="text-[21px] font-semibold tracking-tight">
          开源鸣谢
        </h2>
        <p class="mt-1.5 text-sm text-dim">
          本站构建在众多开源项目之上，谨向每一位作者与贡献者致谢。点击卡片可前往对应仓库。
        </p>

        <div
          v-for="group in creditGroups"
          :key="group.title"
          class="mt-6"
        >
          <h3 class="mb-2.5 text-[13px] font-medium uppercase tracking-wide text-faint">
            {{ group.title }}
          </h3>
          <div class="grid gap-3 grid-cols-[repeat(auto-fill,minmax(240px,1fr))]">
            <a
              v-for="item in group.items"
              :key="item.name"
              :href="item.url"
              target="_blank"
              rel="noreferrer"
              class="group flex items-start gap-3 rounded-[14px] border border-line bg-card p-4 transition hover:border-line-strong"
            >
              <span class="min-w-0 flex-1">
                <span class="flex items-center gap-1.5 font-medium text-ink">
                  {{ item.name }}
                  <svg
                    class="size-3.5 shrink-0 text-faint transition group-hover:text-accent"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M7 17 17 7M8 7h9v9" />
                  </svg>
                </span>
                <span class="mt-1 block text-[13px] leading-relaxed text-dim">{{ item.desc }}</span>
              </span>
            </a>
          </div>
        </div>

        <p class="mt-6 text-[12.5px] text-faint">
          以上项目分别遵循其各自的许可证。
        </p>
      </section>

      <!-- ===== Footer ===== -->
      <footer class="mt-6 flex flex-wrap justify-between gap-3 border-t border-line py-7 text-[13px] text-dim">
        <div>© 2026 NitroWater</div>
        <div class="flex gap-[18px]">
          <router-link
            to="/"
            class="transition hover:text-ink"
          >
            返回门户
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
