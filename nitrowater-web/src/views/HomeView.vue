<script setup lang="ts">
/**
 * Portal home: one full-height opening screen (title + quick entries), then three short feature
 * bands (tools / NitroBot / plugin centre), then the shared footer.
 *
 * The opening screen is deliberately a whole viewport tall so the first thing a visitor sees is
 * the title and the three ways in — no scrolling needed to find them. Everything below is optional
 * reading, one band per subject.
 *
 * The tool band is derived from the tool catalog, so the entrance copy never drifts from the real
 * tool list. Artwork is local SVG (src/assets/home/*.svg) standing in for real screenshots: it
 * renders offline, weighs a few KB and uses the same palette as the page.
 */
import { onMounted, onUnmounted, ref } from 'vue'
import { readyTools } from '../features/tools/catalog'
import toolboxShot from '../assets/home/toolbox-shot.svg'
import nitrobotShot from '../assets/home/nitrobot-shot.svg'
import pluginsShot from '../assets/home/plugins-shot.svg'

const toolCount = readyTools.length

/*
 * "Scroll for more" hint.
 *
 * A permanently bouncing arrow is noise once you already know how a page works, so the hint is
 * hidden until the pointer shows intent: it appears when the cursor reaches the lower part of the
 * opening screen (where the hint sits), and it disappears again as soon as the page scrolls — at
 * that point the reader is already moving and no longer needs to be told.
 *
 * On touch / pen devices there is no hover to reveal it with, so it stays visible there; the hint
 * is the only affordance those readers get.
 */
const heroRef = ref<HTMLElement | null>(null)
const hintVisible = ref(false)
/** Keyboard focus keeps the hint on screen regardless of pointer position. */
let hintPinned = false
/** Set once the idle timer fires: the reader has had time to look around, so stop hiding it. */
let hintRevealed = false
let pointerNear = false
let frame = 0

/** Share of the opening screen that counts as "near the hint". */
const HINT_ZONE = 0.62
/** Scroll offset after which the hint is pointless. */
const HINT_HIDE_SCROLL = 24
/** Fallback: a reader who never moves the pointer still gets told the page continues. */
const HINT_IDLE_MS = 6000
let idleTimer = 0

function scheduleHintUpdate(): void {
  if (frame) return
  frame = requestAnimationFrame(() => {
    frame = 0
    const hoverable = window.matchMedia('(hover: hover) and (pointer: fine)').matches
    const atTop = window.scrollY < HINT_HIDE_SCROLL
    hintVisible.value = atTop && (hintPinned || hintRevealed || !hoverable || pointerNear)
  })
}

function onHeroPointerMove(event: PointerEvent): void {
  if (event.pointerType === 'touch') return
  const hero = heroRef.value
  if (!hero) return
  const rect = hero.getBoundingClientRect()
  pointerNear = event.clientY >= rect.top + rect.height * HINT_ZONE
  scheduleHintUpdate()
}

function onHeroPointerLeave(): void {
  pointerNear = false
  scheduleHintUpdate()
}

function onHintFocus(): void {
  hintPinned = true
  scheduleHintUpdate()
}

function onHintBlur(): void {
  hintPinned = false
  scheduleHintUpdate()
}

function onScroll(): void {
  scheduleHintUpdate()
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  scheduleHintUpdate()
  idleTimer = window.setTimeout(() => {
    if (window.scrollY < HINT_HIDE_SCROLL) {
      hintRevealed = true
      hintVisible.value = true
    }
  }, HINT_IDLE_MS)
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  window.clearTimeout(idleTimer)
  if (frame) cancelAnimationFrame(frame)
})

/** Three ways into the site, rendered on the opening screen. */
const entries = [
  {
    key: 'tools',
    to: '/tools',
    title: '工具箱',
    sub: `${toolCount} 个工具 · 全部本地运行`,
    icon: 'M4 7.5h16M5 7.5l.9 11a2 2 0 0 0 2 1.9h8.2a2 2 0 0 0 2-1.9l.9-11M9.5 7.5V5.5a2 2 0 0 1 2-2h1a2 2 0 0 1 2 2v2',
  },
  {
    key: 'agent',
    to: '/chat',
    title: 'NitroBot',
    sub: '文档 · 代码 · 联网',
    icon: 'M12 3.5a8.5 8.5 0 0 0-6.9 13.4L4 21l4.2-1.1A8.5 8.5 0 1 0 12 3.5zM9 11h.01M15 11h.01M9.5 14.5c1.4.9 3.6.9 5 0',
  },
  {
    key: 'plugins',
    to: '/plugins',
    title: '插件中心',
    sub: '说明 · 版本 · 下载',
    icon: 'M12 3.2l7.8 4.4v8.8L12 20.8l-7.8-4.4V7.6zM12 12v8.8M12 12L4.2 7.6M12 12l7.8-4.4',
  },
] as const

interface FeatureRow {
  key: string
  title: string
  desc: string
  /** Second paragraph: the "why it matters" line, in a quieter colour. */
  detail: string
  to: string
  cta: string
  image: string
  alt: string
  /** Show the artwork first on wide screens, so the bands alternate. */
  reverse: boolean
}

const features: FeatureRow[] = [
  {
    key: 'tools',
    title: '工具箱',
    desc: `${toolCount} 个开服小工具：MOTD 配色预览、server.properties 生成与校验、64×64 服务器图标裁切、启动脚本生成，另有代码运行、压缩解压、结构可视化与格式转换。`,
    detail: '开箱即用',
    to: '/tools',
    cta: '进入工具箱',
    image: toolboxShot,
    alt: '工具箱界面示意',
    reverse: false,
  },
  {
    key: 'agent',
    title: 'NitroBot',
    desc: '本站最新推出的多功能的对话Agent，能够执行联网搜索、文档查询、代码生成与运行等任务，可以根据本站的插件文档检索你的对于插件的疑问。',
    detail: '能读站内插件与项目文档、能上网确认最新信息，也能直接跑一段代码验证结果；追问会记得前文，也能记住你的偏好。',
    to: '/chat',
    cta: '开始对话',
    image: nitrobotShot,
    alt: 'NitroBot 对话界面示意',
    reverse: true,
  },
  {
    key: 'plugins',
    title: '插件中心',
    desc: '收录常用服务端插件，提供插件的说明、版本列表与下载入口。',
    detail: '',
    to: '/plugins',
    cta: '浏览插件',
    image: pluginsShot,
    alt: '插件中心界面示意',
    reverse: false,
  },
]

/** Scrolls to the first feature band (the hint is decoration, not navigation state). */
function scrollToFeatures(): void {
  document.getElementById('features')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const REPO = {
  github: 'https://github.com/danburen/nitrowater',
  gitee: 'https://gitee.com/blackwallet/nitrowater',
} as const

/** Contribution entry: issues are the lowest-friction way in for a small project. */
const CONTRIBUTE_URL = `${REPO.github}/issues` as const
</script>

<template>
  <div class="min-h-screen">
    <main class="mx-auto max-w-[1120px] px-4 sm:px-6">
      <!-- ===== Opening screen: title + quick entries ===== -->
      <!--
        Fills the viewport but stops growing past 880px: on a 1600px-tall monitor a full-height
        hero would strand the entries in the middle of empty space. `min()` (not max-height) is
        what allows the cap — min-height always wins over max-height, so a `max-h` here would be
        silently ignored.
      -->
      <section
        ref="heroRef"
        class="flex min-h-[min(calc(100svh_-_var(--app-header-h)),880px)] flex-col py-14 text-center"
        @pointermove="onHeroPointerMove"
        @pointerleave="onHeroPointerLeave"
      >
        <div class="my-auto">
          <h1 class="relative inline-block text-[34px] font-bold leading-[1.2] tracking-tight sm:text-[48px] lg:text-[58px]">
            <span
              class="hero-glow"
              aria-hidden="true"
            />
            <span class="hero-gradient relative">为 Minecraft 腐竹<br>提供一站式开服工具</span>
          </h1>

          <p class="mx-auto mt-6 max-w-[640px] text-[15px] leading-relaxed text-dim sm:text-[16px] sm:leading-[1.8]">
            本站宗旨是为每位 Minecraft 腐竹提供更好的开服和运维服务，一起进步。
          </p>

          <div class="mx-auto mt-10 grid w-full max-w-[760px] gap-3 text-left sm:grid-cols-3">
            <router-link
              v-for="entry in entries"
              :key="entry.key"
              :to="entry.to"
              class="group flex items-center gap-3 rounded-[14px] border border-line bg-card px-4 py-3.5 transition hover:-translate-y-0.5 hover:border-line-strong"
            >
              <span class="grid size-9 shrink-0 place-items-center rounded-[10px] border border-accent/25 bg-accent/10 text-accent">
                <svg
                  class="size-[18px]"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  aria-hidden="true"
                >
                  <path :d="entry.icon" />
                </svg>
              </span>
              <span class="min-w-0">
                <span class="block text-[13.5px] font-medium text-ink">{{ entry.title }}</span>
                <span class="block truncate text-[12px] text-faint">{{ entry.sub }}</span>
              </span>
            </router-link>
          </div>

          <!--
            Open source entries: repository + issue tracker. Plain anchors and inline SVG, so the row
            costs no extra request. If a live counter is ever wanted, an <img> from
            `https://img.shields.io/github/stars/danburen/nitrowater` drops in here, but it would be
            a third-party request on every visit and cannot follow the theme tokens.
          -->
          <div class="mt-6 flex flex-wrap items-center justify-center gap-x-4 gap-y-2 text-[12.5px] text-faint">
            <a
              :href="REPO.github"
              target="_blank"
              rel="noreferrer"
              title="在 GitHub 上查看本项目"
              class="inline-flex items-center gap-1.5 transition hover:text-ink"
            >
              <svg
                class="size-[15px]"
                viewBox="0 0 24 24"
                fill="currentColor"
                aria-hidden="true"
              >
                <path d="M12 .5C5.73.5.5 5.73.5 12c0 5.08 3.29 9.39 7.86 10.91.58.11.79-.25.79-.55v-2.1c-3.2.7-3.88-1.37-3.88-1.37-.53-1.34-1.29-1.7-1.29-1.7-1.05-.72.08-.7.08-.7 1.16.08 1.77 1.19 1.77 1.19 1.03 1.77 2.7 1.26 3.36.96.1-.75.4-1.26.73-1.55-2.55-.29-5.23-1.28-5.23-5.7 0-1.26.45-2.29 1.19-3.1-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.18 1.18a11 11 0 0 1 5.8 0c2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.23 2.76.11 3.05.74.81 1.19 1.84 1.19 3.1 0 4.43-2.69 5.41-5.25 5.69.41.36.78 1.06.78 2.14v3.17c0 .3.21.67.8.55A11.5 11.5 0 0 0 23.5 12C23.5 5.73 18.27.5 12 .5z" />
              </svg>
              GitHub 源码
            </a>

            <a
              :href="CONTRIBUTE_URL"
              target="_blank"
              rel="noreferrer"
              title="提交 issue 或改进建议"
              class="inline-flex items-center gap-1.5 transition hover:text-ink"
            >
              <svg
                class="size-[15px]"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <circle
                  cx="18"
                  cy="6"
                  r="2.6"
                />
                <circle
                  cx="6"
                  cy="18"
                  r="2.6"
                />
                <path d="M6 9v6M18 9v2.4a3 3 0 0 1-3 3h-3.6" />
              </svg>
              参与贡献
            </a>
          </div>
        </div>

        <button
          type="button"
          class="group mx-auto mt-10 flex flex-col items-center gap-1 text-[12px] text-faint transition duration-300 hover:text-dim"
          :class="hintVisible ? 'translate-y-0 opacity-100' : 'pointer-events-none translate-y-1 opacity-0'"
          @click="scrollToFeatures"
          @focus="onHintFocus"
          @blur="onHintBlur"
        >
          向下了解更多
          <svg
            class="size-4 animate-bounce motion-reduce:animate-none"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path d="M12 5v14M6 13l6 6 6-6" />
          </svg>
        </button>
      </section>

      <!-- ===== Feature bands ===== -->
      <section
        id="features"
        class="divide-y divide-line border-t border-line"
      >
        <article
          v-for="row in features"
          :key="row.key"
          class="flex flex-col gap-8 py-12 sm:py-16 lg:flex-row lg:items-center lg:gap-16"
          :class="row.reverse ? 'lg:flex-row-reverse' : ''"
        >
          <div class="flex-1">
            <h2 class="text-[24px] font-semibold tracking-tight sm:text-[30px] sm:leading-[1.25]">
              {{ row.title }}
            </h2>
            <p class="mt-4 max-w-[520px] text-[14px] leading-relaxed text-dim sm:text-[15px] sm:leading-[1.8]">
              {{ row.desc }}
            </p>
            <p
              v-if="row.detail"
              class="mt-3 max-w-[520px] text-[13px] leading-relaxed text-faint sm:text-[13.5px]"
            >
              {{ row.detail }}
            </p>
            <router-link
              :to="row.to"
              class="group mt-6 inline-flex items-center gap-1.5 text-[14px] font-medium text-accent transition hover:text-accent-strong"
            >
              {{ row.cta }}
              <span
                class="transition-transform group-hover:translate-x-0.5"
                aria-hidden="true"
              >→</span>
            </router-link>
          </div>

          <div class="flex-1">
            <div class="overflow-hidden rounded-[20px] border border-line bg-soft shadow-[0_16px_40px_var(--shadow-color)]">
              <img
                :src="row.image"
                :alt="row.alt"
                loading="lazy"
                class="block aspect-[4/3] w-full object-cover"
              >
            </div>
          </div>
        </article>
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

<style scoped>
/*
 * Hero headline: the palette sweeps across the glyphs, on a 9s ping-pong so the loop never jumps.
 * Only token colours are used, which keeps it readable in both themes — a pale highlight would
 * disappear on the light background. Both the sweep and the halo stop under `prefers-reduced-motion`.
 */
.hero-gradient {
  background-image: linear-gradient(
    105deg,
    var(--color-ink) 0%,
    var(--color-accent) 18%,
    var(--color-accent-2) 38%,
    var(--color-accent) 58%,
    var(--color-ink) 78%,
    var(--color-accent) 100%
  );
  background-size: 280% 100%;
  background-position: 0% 50%;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent;
  animation: hero-flow 9s ease-in-out infinite alternate;
}

/* Soft halo behind the headline; breathing slowly keeps the screen alive without moving text. */
.hero-glow {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 82%;
  height: 140%;
  transform: translate(-50%, -50%);
  background: radial-gradient(
    ellipse at center,
    color-mix(in srgb, var(--color-accent) 20%, transparent) 0%,
    transparent 70%
  );
  filter: blur(36px);
  pointer-events: none;
  animation: hero-breathe 6s ease-in-out infinite alternate;
}

@keyframes hero-flow {
  from {
    background-position: 0% 50%;
  }

  to {
    background-position: 100% 50%;
  }
}

@keyframes hero-breathe {
  from {
    opacity: 0.55;
    transform: translate(-50%, -50%) scale(1);
  }

  to {
    opacity: 0.95;
    transform: translate(-50%, -50%) scale(1.06);
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-gradient,
  .hero-glow {
    animation: none;
  }

  .hero-gradient {
    background-position: 40% 50%;
  }
}
</style>
