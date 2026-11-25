<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { creditGroups } from '../shared/data/credits'
import { fetchLegalIndex, type LegalDocument } from '../api/legal'
import { fetchSponsors, type Sponsor } from '../api/sponsors'
import SvgIcon from '../shared/ui/SvgIcon.vue'

const route = useRoute()
const router = useRouter()

/** Footer legal links; see HomeView — published index, never a hard-coded pair. */
const legalDocuments = ref<LegalDocument[]>([])

/** Sponsor cards ("鸣谢"); each page is cached server-side and fetched on demand. */
const sponsors = ref<Sponsor[]>([])
const sponsorPage = ref(0)
const sponsorTotalPages = ref(0)
const sponsorLoading = ref(false)
const sponsorLoaded = ref(false)
const sponsorError = ref('')

/** ifdian returns epoch seconds; keep the display in the project's Asia/Shanghai timezone. */
function formatSponsorTime(value: string): string {
  if (!value) return '感谢支持'
  if (/^\d{10}$/.test(value)) {
    return new Intl.DateTimeFormat('zh-CN', {
      timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
    }).format(new Date(Number(value) * 1000))
  }
  return value
}

/** Compact page numbers with ellipses so a growing sponsor list keeps one pager row. */
const sponsorPages = computed<(number | 'gap')[]>(() => {
  const total = sponsorTotalPages.value
  const current = sponsorPage.value
  if (total <= 7) return Array.from({ length: total }, (_, index) => index)
  const nearby = new Set([0, 1, total - 2, total - 1, current - 1, current, current + 1])
  const numbers = [...nearby].filter((page) => page >= 0 && page < total).sort((a, b) => a - b)
  const result: (number | 'gap')[] = []
  numbers.forEach((page, index) => {
    if (index > 0 && page - numbers[index - 1] > 1) result.push('gap')
    result.push(page)
  })
  return result
})

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

/** Loads one sponsor page; retrying a failed page replaces the previous result. */
async function loadSponsors(page = 0): Promise<void> {
  if (sponsorLoading.value) return
  sponsorLoading.value = true
  sponsorError.value = ''
  try {
    const result = await fetchSponsors(page)
    sponsors.value = result.content
    sponsorPage.value = page
    sponsorTotalPages.value = result.page.totalPages
    sponsorLoaded.value = true
  } catch {
    sponsorError.value = '鸣谢列表暂时无法加载'
  } finally {
    sponsorLoading.value = false
  }
}

/** Deep-link support: /about#credits should land on the credits section. */
onMounted(() => {
  if (route.hash) {
    document.getElementById(route.hash.slice(1))?.scrollIntoView({ behavior: 'smooth' })
  }
  void fetchLegalIndex()
    .then((documents) => {
      legalDocuments.value = documents
    })
    .catch(() => {
      legalDocuments.value = []
    })
  void loadSponsors()
})
</script>

<template>
  <div class="min-h-screen">
    <main class="mx-auto max-w-[1120px] px-4 sm:px-6">
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
        <p class="mt-3 max-w-[800px] text-sm leading-relaxed text-dim">
          NitroWater 是一个面向 Minecraft 腐竹与插件/服务器社区的多模块平台，包含一个聚合展示站点（导航 · 开服工具箱 · 插件中心 · 文档）与独立的 SSO / OIDC 身份中心。
          开服工具箱覆盖 MOTD 格式化预览、server.properties 生成/校验、server-icon 生成与启动脚本生成；所有本地工具都在浏览器内完成计算，文件与数据不会上传服务器。
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

      <!-- ===== AI 额度说明 ===== -->
      <section
        id="ai-quota"
        class="py-6"
      >
        <h2 class="text-[21px] font-semibold tracking-tight">
          AI 额度说明
        </h2>
        <p class="mt-1.5 max-w-[800px] text-sm leading-relaxed text-dim">
          NitroBot 对每个登录账号设置了每日 token 上限。额度按天重置、不累计，也不与设备绑定。
        </p>

        <div class="mt-4 grid gap-3 sm:grid-cols-2">
          <div class="rounded-[14px] border border-line bg-card p-4 sm:col-span-2">
            <h3 class="text-[13.5px] font-medium text-ink">
              除了个人额度，还有全站额度
            </h3>
            <p class="mt-1.5 text-[13px] leading-relaxed text-dim">
              NitroWater 是开源项目，服务器、模型调用和向量检索都由个人出资维护，没有商业收入兜底。
              为了避免某一天的用量把预算打穿、也为了让服务能长期稳定运行，全站设有每日用量上限。
              达到上限后当天无法继续提问，<strong class="font-medium text-ink">历史会话与斜杠指令仍然可用</strong>，
              第二天 08:00（北京时间）自动恢复。如果你觉得这个限制影响使用，欢迎在下方支持项目。
            </p>
          </div>
          <div class="rounded-[14px] border border-line bg-card p-4">
            <h3 class="text-[13.5px] font-medium text-ink">
              为什么要限制
            </h3>
            <p class="mt-1.5 text-[13px] leading-relaxed text-dim">
              每次回答都要调用大模型并按 token 计费，检索用的向量库、代码沙箱和服务器也需要持续运行。
              有了上限，站点才能对所有人保持免费，而不会被少数高频请求拖垮。
            </p>
          </div>
          <div class="rounded-[14px] border border-line bg-card p-4">
            <h3 class="text-[13.5px] font-medium text-ink">
              额度怎么计算
            </h3>
            <p class="mt-1.5 text-[13px] leading-relaxed text-dim">
              输入和输出都计入：你的问题、检索到的文档片段、近期对话，以及模型生成的回答。
              一次普通问答通常在几千 token 量级，长文档检索或多次工具调用会消耗更多。
            </p>
          </div>
          <div class="rounded-[14px] border border-line bg-card p-4">
            <h3 class="text-[13.5px] font-medium text-ink">
              什么时候重置
            </h3>
            <p class="mt-1.5 text-[13px] leading-relaxed text-dim">
              每天 UTC 0 点（北京时间 08:00）自动重置，无需任何操作。
              用量快用完时，聊天页会在输入框上方提示一次。
            </p>
          </div>
          <div class="rounded-[14px] border border-line bg-card p-4">
            <h3 class="text-[13.5px] font-medium text-ink">
              用完了怎么办
            </h3>
            <p class="mt-1.5 text-[13px] leading-relaxed text-dim">
              等第二天自动恢复；也可以先用工具箱或文档自己查。确实需要继续对话时，可以联系管理员重置当日用量。
            </p>
          </div>
        </div>

        <p class="mt-4 text-[12.5px] leading-relaxed text-faint">
          本站不投放广告、不出售额度，也没有付费套餐。额度上限由管理员按服务器成本统一调整。
        </p>
      </section>

      <!-- ===== 鸣谢（爱发电赞助者） ===== -->
      <section
        id="sponsors"
        class="py-6"
      >
        <h2 class="text-[21px] font-semibold tracking-tight">
          鸣谢
        </h2>
        <p class="mt-1.5 text-sm text-dim">
          感谢每一位通过爱发电支持项目的朋友。
        </p>

        <div
          v-if="sponsors.length"
          class="mt-4 grid gap-3 grid-cols-[repeat(auto-fill,minmax(260px,1fr))]"
        >
          <div
            v-for="sponsor in sponsors"
            :key="sponsor.userId || sponsor.nickname"
            class="flex items-center gap-3 rounded-[14px] border border-line bg-card p-4"
          >
            <img
              v-if="sponsor.avatarUrl"
              :src="sponsor.avatarUrl"
              :alt="sponsor.nickname"
              referrerpolicy="no-referrer"
              class="h-9 w-9 shrink-0 rounded-full object-cover"
            >
            <span
              v-else
              class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-line text-[13px] text-dim"
            >{{ sponsor.nickname.slice(0, 1) || '赞' }}</span>

            <span class="min-w-0 flex-1">
              <span class="block truncate font-medium text-ink">
                {{ sponsor.nickname || '匿名赞助者' }}
              </span>
              <span class="mt-0.5 block truncate text-[12.5px] text-faint">
                最近赞助 {{ formatSponsorTime(sponsor.lastSponsorTime) }}
              </span>
            </span>
          </div>
        </div>

        <p
          v-else-if="sponsorLoaded && !sponsorLoading"
          class="mt-4 text-[13px] text-faint"
        >
          暂无线上鸣谢记录。
        </p>

        <div
          v-if="sponsorError"
          class="mt-4 flex items-center gap-3"
        >
          <button
            type="button"
            class="btn btn-sm"
            @click="loadSponsors(sponsorPage)"
          >
            重试
          </button>
          <span class="text-[12.5px] text-faint">{{ sponsorError }}</span>
        </div>

        <nav
          v-if="sponsorTotalPages > 1"
          class="mt-4 flex flex-wrap items-center justify-center gap-1.5"
          aria-label="赞助列表分页"
        >
          <button
            type="button"
            class="btn btn-sm"
            :disabled="sponsorPage === 0 || sponsorLoading"
            @click="loadSponsors(sponsorPage - 1)"
          >
            上一页
          </button>
          <template
            v-for="(item, index) in sponsorPages"
            :key="item === 'gap' ? `gap-${index}` : item"
          >
            <span
              v-if="item === 'gap'"
              class="px-1 text-[12px] text-faint"
            >…</span>
            <button
              v-else
              type="button"
              class="btn btn-sm min-w-8 justify-center"
              :class="item === sponsorPage ? 'border-accent bg-accent text-white' : ''"
              :disabled="sponsorLoading"
              :aria-current="item === sponsorPage ? 'page' : undefined"
              @click="loadSponsors(item)"
            >
              {{ item + 1 }}
            </button>
          </template>
          <button
            type="button"
            class="btn btn-sm"
            :disabled="sponsorPage >= sponsorTotalPages - 1 || sponsorLoading"
            @click="loadSponsors(sponsorPage + 1)"
          >
            下一页
          </button>
        </nav>

        <p
          v-if="sponsors.length"
          class="mt-2 text-center text-[12px] text-faint"
        >
          排序按照最后一次赞助时间
        </p>
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
                  <SvgIcon
                    name="external-link"
                    :size="14"
                    class="shrink-0 text-faint transition group-hover:text-accent"
                  />
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
        <div class="flex flex-wrap items-center gap-x-[18px] gap-y-1">
          <span>© 2026 NitroWater</span>
          <a
            href="https://console.starxn.com?l=AV1ARI"
            target="_blank"
            rel="noreferrer noopener"
            class="transition hover:text-accent"
          >PowerBy Starxn</a>
        </div>
        <div class="flex gap-[18px]">
          <router-link
            to="/"
            class="transition hover:text-ink"
          >
            返回门户
          </router-link>
          <router-link
            v-for="document in legalDocuments"
            :key="document.key"
            :to="`/legal/${document.key}`"
            class="transition hover:text-ink"
          >
            {{ document.title }}
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
