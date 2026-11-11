<script setup lang="ts">
/**
 * One transcript message: avatar, bubble, hover actions and citations.
 *
 * <p>Assistant turns render sanitized markdown and keep their citations; the process trace lives in
 * {@code ToolTrace} and is collapsed by default. User turns are plain text.</p>
 */
import { computed, ref } from 'vue'
import type { ChatMessage, LiveStep } from '../types'
import MarkdownContent from './MarkdownContent.vue'
import ToolTrace from './ToolTrace.vue'
import AgentLiveSteps from './AgentLiveSteps.vue'

const props = defineProps<{
  message: ChatMessage
  streaming: boolean
  canRegenerate: boolean
  busy: boolean
  userInitial: string
  /** Live step timeline of the running turn; empty for every settled message. */
  steps: readonly LiveStep[]
}>()

const emit = defineEmits<{
  (e: 'regenerate'): void
  (e: 'continue'): void
}>()

const copied = ref(false)

const isUser = computed(() => props.message.role === 'user')

/**
 * Whether the turn has been accepted but has nothing to show yet.
 *
 * <p>Two windows need this: the request round trip before the server's first marker (conversation
 * creation, then the stream opening), and the moment between the first delta and the first painted
 * characters. Without it the turn looked like it had not started at all.</p>
 */
const awaiting = computed(() => props.streaming && props.steps.length === 0 && !props.message.content)

/**
 * Historical process cards, rebuilt from the persisted tool calls.
 *
 * <p>{@code tools_json} already travels with every stored message, so the same timeline the live
 * turn showed survives a reload without a new column or a client-side copy. Only the per-call
 * duration is absent, and the card simply omits it.</p>
 */
const settledSteps = computed<LiveStep[]>(() => props.message.tools.map((tool, index): LiveStep => ({
  id: `tool-${index}`,
  phase: 'tool_end',
  tool: tool.name,
  argument: tool.argument || null,
  status: tool.status ?? null,
  ok: tool.ok ?? null,
  resultCount: tool.resultCount,
  startedAt: 0,
  durationMs: null,
})))

/** Live cards while the turn runs, the ones kept on the message afterwards, else the persisted calls. */
const shownSteps = computed<readonly LiveStep[]>(() => {
  if (props.streaming) return props.steps
  return props.message.steps?.length ? props.message.steps : settledSteps.value
})

async function copy(): Promise<void> {
  if (!props.message.content) return
  try {
    await navigator.clipboard.writeText(props.message.content)
    copied.value = true
    window.setTimeout(() => {
      copied.value = false
    }, 1200)
  } catch {
    // Clipboard permission denied: no feedback.
  }
}
</script>

<template>
  <div
    class="msg-in group flex gap-3"
    :class="isUser ? 'flex-row-reverse' : ''"
  >
    <span
      class="mt-0.5 grid size-8 shrink-0 place-items-center rounded-full text-[11.5px] font-bold text-white"
      :class="isUser ? 'bg-gradient-to-br from-warn to-danger' : 'bg-gradient-to-br from-accent to-accent-2'"
    >{{ isUser ? userInitial : 'N' }}</span>

    <div
      class="min-w-0 flex-1"
      :class="isUser ? 'flex flex-col items-end' : ''"
    >
      <div class="mb-1 flex items-center gap-1.5">
        <p class="text-[11.5px] font-semibold text-faint">
          {{ isUser ? '你' : 'NitroBot' }}
        </p>
        <span
          v-if="message.moderated"
          class="rounded-full bg-warn/10 px-1.5 py-0.5 text-[10.5px] font-medium text-warn"
          title="该回答中的违规内容已被替换为 *"
        >内容已过滤</span>
      </div>

      <AgentLiveSteps
        v-if="!isUser && (shownSteps.length || awaiting)"
        :steps="shownSteps"
        :awaiting="awaiting"
      />

      <div
        v-if="isUser || message.content || message.error || message.stopped"
        class="w-fit max-w-full rounded-2xl px-4 py-2.5 text-[14px] leading-[1.65]"
        :class="isUser
          ? 'rounded-tr-[6px] bg-accent/10 text-ink'
          : 'rounded-tl-[6px] border border-line bg-card text-ink'"
      >
        <p
          v-if="isUser"
          class="whitespace-pre-wrap break-words"
        >
          {{ message.content }}
        </p>
        <template v-else>
          <MarkdownContent
            v-if="message.content"
            :text="message.content"
            :streaming="streaming"
            :sources="message.sources"
          />
          <span
            v-if="streaming && message.content"
            class="ml-0.5 inline-block h-[0.95em] w-[3px] animate-pulse rounded-sm bg-accent align-[-0.15em]"
          />
          <p
            v-if="message.stopped"
            class="mt-1 text-[11.5px] text-faint"
          >
            已停止生成
          </p>
          <p
            v-if="message.error"
            class="mt-1 text-[12.5px] text-danger"
          >
            {{ message.error }}
          </p>
        </template>
      </div>

      <div
        v-if="!isUser"
        class="mt-1 flex items-center gap-0.5 opacity-100 transition sm:opacity-0 sm:group-hover:opacity-100 sm:focus-within:opacity-100"
      >
        <button
          type="button"
          class="inline-flex items-center gap-1 rounded-md px-1.5 py-1 text-[11.5px] text-faint transition hover:bg-soft hover:text-ink"
          :disabled="!message.content"
          @click="copy"
        >
          <svg
            class="size-3.5"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <rect
              x="9"
              y="9"
              width="13"
              height="13"
              rx="2"
            />
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
          </svg>
          {{ copied ? '已复制' : '复制' }}
        </button>
        <button
          v-if="message.truncated && !streaming"
          type="button"
          class="inline-flex items-center gap-1 rounded-md px-1.5 py-1 text-[11.5px] font-medium text-accent transition hover:bg-soft disabled:opacity-40"
          :disabled="busy"
          @click="emit('continue')"
        >
          继续生成 →
        </button>
        <button
          v-if="canRegenerate"
          type="button"
          class="inline-flex items-center gap-1 rounded-md px-1.5 py-1 text-[11.5px] text-faint transition hover:bg-soft hover:text-ink disabled:opacity-40"
          :disabled="busy"
          @click="emit('regenerate')"
        >
          <svg
            class="size-3.5"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <polyline points="23 4 23 10 17 10" />
            <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
          </svg>
          重新生成
        </button>
      </div>

      <ToolTrace
        v-if="!isUser && message.skills.length"
        :skills="message.skills"
      />

      <div
        v-if="!isUser && message.sources.length"
        class="mt-2 flex max-w-full flex-wrap gap-1.5"
      >
        <a
          v-for="source in message.sources"
          :key="`${message.id}-${source.index}`"
          :href="source.url || undefined"
          target="_blank"
          rel="noreferrer"
          class="tag max-w-[320px] truncate transition hover:border-accent/40 hover:text-ink"
          :class="source.kind === 'web' ? 'border-warn/30' : ''"
          :title="source.snippet"
        >[{{ source.index }}] {{ source.section || source.title }}<span
          v-if="source.kind === 'web'"
          class="ml-1 text-warn"
        >网页</span></a>
      </div>
    </div>
  </div>
</template>

<style scoped>
.msg-in {
  animation: msg-in 0.28s ease both;
}

@keyframes msg-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
