<script setup lang="ts">
import { computed } from 'vue'
import { useAuth } from '../auth/useAuth'
import { readyTools } from '../features/tools/catalog'

const { session, login } = useAuth()

const isLoggedIn = computed(() => !!session.value?.authenticated)

const PICS = 'https://picsum.photos/seed'

/** 在线工具箱（已上线工具，纯前端本地计算）。 */
const toolList = readyTools

const playgroundLangs = ['C++', 'Python', 'Java']

const docs = [
  { key: 'arch', title: '项目工程文档', sub: '架构说明 · 部署指南 · API 参考' },
  { key: 'plugin', title: '插件使用说明', sub: '安装 · 配置 · 常见问题' },
  { key: 'notes', title: '开发笔记', sub: '更新日志 · 踩坑记录' },
  { key: 'changelog', title: '更新日志', sub: '版本历史与变更说明' },
]

/** 服务器列表（示例数据，图片占位同源）。 */
const servers = [
  { key: 's1', name: '生存服 · 示例一', img: `${PICS}/nw-mc-survival/120/120`, meta: ['Java 1.20', '生存 / 建筑'], online: true, status: '在线 · 12 人' },
  { key: 's2', name: '模组服 · 示例二', img: `${PICS}/nw-mc-mod/120/120`, meta: ['Forge 1.19', '科技 / 冒险'], online: false, status: '离线' },
  { key: 's3', name: '小游戏服 · 示例三', img: `${PICS}/nw-mc-mini/120/120`, meta: ['Paper 1.20', '起床战争 / 空岛'], online: true, status: '在线 · 8 人' },
]

const REPO = {
  github: 'https://github.com/danburen/nitrowater',
  gitee: 'https://gitee.com/blackwallet/nitrowarer',
} as const

function scrollTo(id: string): void {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
}

function enterPlayground(): void {
  if (isLoggedIn.value) {
    scrollTo('playground')
  } else {
    login()
  }
}
</script>

<template>
  <div class="min-h-screen">
    <main class="mx-auto max-w-[1120px] px-6">
      <!-- ===== Hero ===== -->
      <section class="py-[72px] pb-12 text-center">
        <div class="mb-5 inline-block rounded-full border border-accent/25 bg-accent/10 px-3 py-[5px] text-[12.5px] text-accent">
          匿名工具箱 · 在线演练场 · 插件文档 · 服务器列表
        </div>
        <h1 class="mb-4 text-[40px] font-bold leading-tight tracking-tight">
          一个站点，装下你的<br>
          <span class="bg-gradient-to-br from-accent to-accent-2 bg-clip-text text-transparent">工具、代码与服务器</span>
        </h1>
        <p class="mx-auto mb-7 max-w-[560px] text-base text-dim">
          匿名即可使用在线工具箱；登录后进入 ACM 模式编程演练场；工程文档、插件说明与服务器列表集中在这里。
        </p>
        <div class="flex flex-wrap justify-center gap-3">
          <router-link
            to="/tools"
            class="btn btn-primary"
          >
            开始使用工具箱
          </router-link>
          <button
            class="btn"
            @click="enterPlayground"
          >
            进入演练场
          </button>
        </div>
      </section>

      <!-- ===== 在线工具箱 ===== -->
      <section
        id="tools"
        class="py-10"
      >
        <div class="section-head">
          <div>
            <h2 class="text-[21px] font-semibold tracking-tight">
              在线工具箱
            </h2>
            <p class="mt-1 text-[13.5px] text-dim">
              无需登录，打开即用
            </p>
          </div>
          <router-link
            to="/tools"
            class="text-[13.5px] text-accent"
          >
            查看全部 →
          </router-link>
        </div>

        <div class="grid gap-4 grid-cols-[repeat(auto-fill,minmax(240px,1fr))]">
          <router-link
            v-for="tool in toolList"
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
      </section>

      <!-- ===== 编程演练场 ===== -->
      <section
        id="playground"
        class="py-10"
      >
        <div class="section-head">
          <div>
            <h2 class="text-[21px] font-semibold tracking-tight">
              在线编程演练场
            </h2>
            <p class="mt-1 text-[13.5px] text-dim">
              ACM 模式 · 登录后可用
            </p>
          </div>
        </div>

        <div class="grid overflow-hidden rounded-[14px] border border-line bg-card md:grid-cols-[1.1fr_0.9fr]">
          <div class="flex flex-col justify-center gap-3.5 p-8">
            <h3 class="text-[22px] font-semibold tracking-tight">
              ACM 模式在线判题
            </h3>
            <p class="text-sm text-dim">
              提交代码、实时判题、查看运行结果。支持多语言，题目按难度与标签组织。
            </p>
            <div class="flex flex-wrap gap-2">
              <span
                v-for="lang in playgroundLangs"
                :key="lang"
                class="tag"
              >{{ lang }}</span>
              <span class="tag tag-lock">需登录</span>
            </div>
            <div class="mt-2">
              <button
                class="btn btn-primary"
                @click="enterPlayground"
              >
                登录后进入 →
              </button>
            </div>
          </div>
          <img
            class="min-h-[260px] w-full object-cover opacity-90 md:min-h-full"
            :src="`${PICS}/nw-playground/900/600`"
            alt="演练场预览"
          >
        </div>
      </section>

      <!-- ===== 文档中心 ===== -->
      <section
        id="docs"
        class="py-10"
      >
        <div class="section-head">
          <div>
            <h2 class="text-[21px] font-semibold tracking-tight">
              文档中心
            </h2>
            <p class="mt-1 text-[13.5px] text-dim">
              工程文档与插件说明
            </p>
          </div>
          <a
            href="#docs"
            class="text-[13.5px] text-accent"
          >全部文档 →</a>
        </div>

        <div class="grid gap-3 grid-cols-[repeat(auto-fill,minmax(280px,1fr))]">
          <a
            v-for="doc in docs"
            :key="doc.key"
            href="#docs"
            class="flex items-center gap-3 rounded-[11px] border border-line bg-card px-4 py-3.5 transition hover:border-line-strong"
          >
            <span class="size-2 shrink-0 rounded-full bg-accent" />
            <span>
              <span class="block text-sm font-medium">{{ doc.title }}</span>
              <span class="block text-[12.5px] text-dim">{{ doc.sub }}</span>
            </span>
          </a>
        </div>
      </section>

      <!-- ===== 服务器列表 ===== -->
      <section
        id="servers"
        class="py-10"
      >
        <div class="section-head">
          <div>
            <h2 class="text-[21px] font-semibold tracking-tight">
              我的世界服务器列表
            </h2>
            <p class="mt-1 text-[13.5px] text-dim">
              朋友与未来腐竹的服务器
            </p>
          </div>
          <a
            href="#servers"
            class="text-[13.5px] text-accent"
          >提交服务器 →</a>
        </div>

        <div class="grid gap-3.5 grid-cols-[repeat(auto-fill,minmax(300px,1fr))]">
          <div
            v-for="server in servers"
            :key="server.key"
            class="flex gap-3.5 rounded-[14px] border border-line bg-card p-4 transition hover:border-line-strong"
          >
            <img
              class="size-[68px] shrink-0 rounded-[10px] bg-soft object-cover"
              :src="server.img"
              alt=""
            >
            <div class="flex min-w-0 flex-col gap-1.5">
              <h4 class="text-[14.5px] font-semibold">
                {{ server.name }}
              </h4>
              <div class="flex flex-wrap gap-2.5 text-[12.5px] text-dim">
                <span
                  v-for="m in server.meta"
                  :key="m"
                >{{ m }}</span>
              </div>
              <span
                class="inline-flex items-center gap-1.5 text-[12px]"
                :class="server.online ? 'text-ok' : 'text-danger'"
              >
                <span
                  class="size-1.5 rounded-full"
                  :class="server.online ? 'bg-ok' : 'bg-danger'"
                />
                {{ server.status }}
              </span>
            </div>
          </div>
        </div>

        <div class="mt-6 flex flex-wrap items-center justify-between gap-5 rounded-[14px] border border-accent/25 bg-gradient-to-br from-accent/10 to-accent-2/10 p-7">
          <div>
            <h3 class="mb-1 text-lg font-semibold">
              你是腐竹？
            </h3>
            <p class="text-[13.5px] text-dim">
              提交你的服务器信息，审核通过后会出现在这里。
            </p>
          </div>
          <a
            href="#servers"
            class="btn btn-primary"
          >提交服务器</a>
        </div>
      </section>

      <!-- ===== Footer ===== -->
      <footer class="mt-14 flex flex-wrap justify-between gap-3 border-t border-line py-7 text-[13px] text-dim">
        <div>© 2026 NitroWater · 工具 / 演练场 / 文档 / 服务器</div>
        <div class="flex gap-[18px]">
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
