<script setup lang="ts">
/**
 * Display-only card for slash-command output.
 *
 * <p>It is deliberately inert: no buttons, no links, no network calls. The payload comes from a
 * dedicated endpoint, is never persisted and is never sent to the model, which is what keeps system
 * data out of the conversation context.</p>
 */
import { computed } from 'vue'
import type { SystemCard } from '../types'

const props = defineProps<{
  card: SystemCard
}>()

const quota = computed(() => (props.card.type === 'quota' ? props.card.data : null))
const profile = computed(() => (props.card.type === 'profile' ? props.card.data : null))
const status = computed(() => (props.card.type === 'status' ? props.card.data : null))
const notice = computed(() => (props.card.type === 'notice' ? props.card : null))

const header = computed(() => {
  switch (props.card.type) {
    case 'quota':
      return '/quota · 今日额度'
    case 'profile':
      return '/profile · 用户画像'
    case 'status':
      return '/status · 系统状态'
    default:
      return '系统'
  }
})

const percent = computed(() => {
  const data = quota.value
  if (!data || data.dailyTokenLimit <= 0) return 0
  return Math.min(100, Math.round((data.totalTokens / data.dailyTokenLimit) * 100))
})
const remaining = computed(() => {
  const data = quota.value
  if (!data) return 0
  return Math.max(0, data.dailyTokenLimit - data.totalTokens)
})

const REPLY_STYLE: Record<string, string> = { CONCISE: '简洁', BALANCED: '均衡', DETAILED: '详细' }
const EXPERTISE: Record<string, string> = { BEGINNER: '入门', INTERMEDIATE: '熟练', EXPERT: '专家' }
const FEATURE_LABELS: Array<[string, string]> = [
  ['ai', 'AI 总开关'],
  ['chat', '对话'],
  ['agent', 'Agent'],
  ['docs', '文档检索'],
  ['web', '联网'],
  ['code', '代码运行'],
  ['skills', '技能'],
]

function formatTokens(value: number): string {
  return value.toLocaleString('zh-CN')
}

function styleLabel(value: string): string {
  return REPLY_STYLE[value] ?? value
}

function expertiseLabel(value: string): string {
  return EXPERTISE[value] ?? value
}
</script>

<template>
  <div class="msg-in flex">
    <div class="w-full max-w-[600px]">
      <p
        v-if="notice"
        class="flex items-start gap-2 px-1 text-[12.5px] leading-relaxed"
        :class="notice.tone === 'warn' ? 'text-danger' : 'text-dim'"
      >
        <span
          class="mt-[7px] size-1.5 shrink-0 rounded-full"
          :class="notice.tone === 'warn' ? 'bg-danger' : 'bg-accent'"
        />
        {{ notice.text }}
      </p>

      <div
        v-else
        class="rounded-[14px] border border-line bg-card p-4"
      >
        <header class="mb-3 flex items-center justify-between gap-2">
          <span class="font-mono text-[11.5px] text-faint">{{ header }}</span>
        </header>

        <!-- /quota -->
        <div v-if="quota">
          <div class="flex flex-wrap items-baseline gap-x-1.5 gap-y-1">
            <span class="text-[22px] font-semibold leading-none text-ink">
              {{ formatTokens(quota.totalTokens) }}
            </span>
            <span class="text-[12px] text-faint">/ {{ formatTokens(quota.dailyTokenLimit) }} tokens</span>
            <span class="ml-auto text-[11.5px] text-dim">剩余约 {{ formatTokens(remaining) }}</span>
          </div>

          <div
            class="mt-2.5 h-1.5 overflow-hidden rounded-full bg-soft"
            role="progressbar"
            :aria-valuenow="percent"
            aria-valuemin="0"
            aria-valuemax="100"
            aria-label="今日 token 使用率"
          >
            <div
              class="h-full rounded-full"
              :class="percent >= 90 ? 'bg-warn' : 'bg-accent'"
              :style="{ width: `${percent}%` }"
            />
          </div>

          <dl class="mt-3 grid grid-cols-3 gap-2 text-[11.5px]">
            <div>
              <dt class="text-faint">输入</dt>
              <dd class="mt-0.5 text-ink">{{ formatTokens(quota.inputTokens) }}</dd>
            </div>
            <div>
              <dt class="text-faint">输出</dt>
              <dd class="mt-0.5 text-ink">{{ formatTokens(quota.outputTokens) }}</dd>
            </div>
            <div>
              <dt class="text-faint">今日请求</dt>
              <dd class="mt-0.5 text-ink">{{ quota.requestCount }}</dd>
            </div>
          </dl>

          <p class="mt-3 text-[11.5px] text-faint">
            每天 UTC 0 点（北京时间 08:00）重置
          </p>
        </div>

        <!-- /profile -->
        <div v-else-if="profile">
          <p
            v-if="!profile.exists"
            class="text-[13px] leading-relaxed text-dim"
          >
            尚未建立画像。助手目前使用默认偏好：中文、均衡篇幅、中级经验。
          </p>
          <dl
            v-else
            class="grid gap-x-4 gap-y-2 text-[12.5px] sm:grid-cols-2"
          >
            <div>
              <dt class="text-faint">语言</dt>
              <dd class="mt-0.5 text-ink">{{ profile.language }}</dd>
            </div>
            <div>
              <dt class="text-faint">回复风格</dt>
              <dd class="mt-0.5 text-ink">{{ styleLabel(profile.replyStyle) }}</dd>
            </div>
            <div>
              <dt class="text-faint">经验水平</dt>
              <dd class="mt-0.5 text-ink">{{ expertiseLabel(profile.expertise) }}</dd>
            </div>
            <div>
              <dt class="text-faint">时区</dt>
              <dd class="mt-0.5 text-ink">{{ profile.timezone || '未设置' }}</dd>
            </div>
            <div class="sm:col-span-2">
              <dt class="text-faint">偏好标签</dt>
              <dd class="mt-1 flex flex-wrap gap-1.5">
                <span
                  v-for="item in profile.preferences"
                  :key="item"
                  class="rounded-full bg-soft px-2 py-0.5 text-[11px] text-dim"
                >{{ item }}</span>
                <span
                  v-if="!profile.preferences.length"
                  class="text-[12px] text-faint"
                >暂无</span>
              </dd>
            </div>
          </dl>
          <p class="mt-3 text-[11.5px] text-faint">
            自动推断：{{ profile.inferenceOptIn ? '已开启' : '未开启（默认关闭）' }}
          </p>
        </div>

        <!-- /status -->
        <div v-else-if="status">
          <dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-[12.5px]">
            <dt class="text-faint">对话模型</dt>
            <dd class="truncate font-mono text-ink">{{ status.chatModel || '未配置' }}</dd>
            <dt class="text-faint">向量模型</dt>
            <dd class="truncate font-mono text-ink">{{ status.embeddingModel || '未配置' }}</dd>
            <dt class="text-faint">文档索引</dt>
            <dd class="text-ink">
              <template v-if="status.indexing">重建中…</template>
              <template v-else-if="status.indexed">
                {{ status.files }} 文件 / {{ status.chunks }} 块
              </template>
              <template v-else>未构建</template>
            </dd>
            <dt class="text-faint">额度上限</dt>
            <dd class="text-ink">{{ formatTokens(status.dailyTokenLimit) }} tokens / 天</dd>
          </dl>

          <p
            v-if="status.indexError"
            class="mt-2 text-[11.5px] text-danger"
          >
            最近一次重建失败：{{ status.indexError }}
          </p>

          <div class="mt-3">
            <p class="text-[11px] text-faint">
              功能开关
            </p>
            <div class="mt-1.5 flex flex-wrap gap-1.5">
              <span
                v-for="[key, label] in FEATURE_LABELS"
                :key="key"
                class="inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[11px]"
                :class="status.features[key] ? 'bg-accent/10 text-accent' : 'bg-soft text-faint'"
              >
                <span
                  class="size-1.5 rounded-full"
                  :class="status.features[key] ? 'bg-accent' : 'bg-line-strong'"
                />
                {{ label }}
              </span>
            </div>
          </div>

          <div class="mt-3">
            <p class="text-[11px] text-faint">
              已挂载工具（{{ status.tools.length }}）
            </p>
            <div class="mt-1.5 flex flex-wrap gap-1.5">
              <span
                v-for="tool in status.tools"
                :key="tool"
                class="rounded-full bg-soft px-2 py-0.5 font-mono text-[11px] text-dim"
              >{{ tool }}</span>
              <span
                v-if="!status.tools.length"
                class="text-[12px] text-faint"
              >无</span>
            </div>
          </div>

          <div class="mt-3">
            <p class="text-[11px] text-faint">
              技能（{{ status.skills.length }}）
            </p>
            <div class="mt-1.5 flex flex-wrap gap-1.5">
              <span
                v-for="skill in status.skills"
                :key="skill"
                class="rounded-full bg-soft px-2 py-0.5 text-[11px] text-dim"
              >{{ skill }}</span>
              <span
                v-if="!status.skills.length"
                class="text-[12px] text-faint"
              >无</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
