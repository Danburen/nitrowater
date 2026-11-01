<script setup lang="ts">
/**
 * NitroBot — the site's general-purpose AI assistant (`/chat`).
 *
 * <p>Retrieval, skills and tools are automatic: the page always talks to the agent endpoint, never
 * sends a pre-selected skill, and lets the backend decide which tools the turn needs. The only
 * state the user owns here is the conversation itself.</p>
 *
 * <p>Layout follows the standard chat-app shell: conversation sidebar, a thin top bar, the
 * transcript, and a floating composer. The transcript is stored in the browser
 * ({@code useConversations}) because the backend keeps no session state: history is replayed with
 * each request.</p>
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuth } from '../../auth/useAuth'
import {
  AiQuotaError,
  fetchAiStatus,
  fetchAiUsage,
  streamAgent,
  type AiDailyUsage,
  type AiStatus,
  type ChatTurn,
} from '../../api/ai'
import { useConversations, type Conversation } from './composables/useConversations'
import { parseSlash, type SlashCommand } from './slash'
import {
  clearSlashContext,
  fetchSlashProfile,
  fetchSlashQuota,
  fetchSlashStatus,
} from '../../api/slash'
import { ResponseCode } from '../../api/codes'
import type { ChatMessage, SystemCard, TimelineItem } from './types'
import ChatSidebar from './components/ChatSidebar.vue'
import ChatEmpty from './components/ChatEmpty.vue'
import ChatComposer from './components/ChatComposer.vue'
import MessageItem from './components/MessageItem.vue'
import GlobalQuotaNotice from './components/GlobalQuotaNotice.vue'
import QuotaNotice from './components/QuotaNotice.vue'
import SystemCardView from './components/SystemCard.vue'
import ChatTimeline from './components/ChatTimeline.vue'

const route = useRoute()
const { session, loading: authLoading, login, logout } = useAuth()
const { conversations, mode, initialize, create, save, remove, loadMessages, loadOlder } = useConversations()

const authenticated = computed(() => session.value?.authenticated === true)
const userLabel = computed(() => session.value?.name || session.value?.username || '已登录')
const userInitial = computed(() => (userLabel.value.trim()[0] ?? 'U').toUpperCase())

const activeId = ref<string | null>(null)
const messages = ref<ChatMessage[]>([])
const streamingId = ref<string | null>(null)
const input = ref('')
const status = ref<AiStatus | null>(null)
const statusError = ref<string | null>(null)
const sidebarOpen = ref(false)
const sidebarCollapsed = ref(false)
const usage = ref<AiDailyUsage | null>(null)
/**
 * UTC date on which the backend last rejected a turn with {@code token_quota}.
 *
 * <p>The flag is sticky for that day on purpose: a rejected turn never moves
 * {@code totalTokens}, so deriving "exhausted" from the counters alone would hide the notice
 * again on the next refresh.</p>
 */
const exhaustedDate = ref<string | null>(null)
/** Previous snapshot total, used to notice an administrator reset (usage went down). */
const lastUsageTotal = ref<number | null>(null)
const warnedDate = ref<string | null>(readWarnedDate())
/** Web-search intent; only honoured when the server exposes a provider. Persisted per browser. */
const WEB_SEARCH_KEY = 'nitrowater:ai:web-search'
const webSearch = ref(readWebSearchPreference())

function readWebSearchPreference(): boolean {
  try {
    return window.localStorage.getItem(WEB_SEARCH_KEY) !== 'off'
  } catch {
    return true
  }
}

watch(webSearch, (value) => {
  try {
    window.localStorage.setItem(WEB_SEARCH_KEY, value ? 'on' : 'off')
  } catch {
    // Storage unavailable: the choice lasts for this page only.
  }
})

const scrollEl = ref<HTMLElement | null>(null)
/** Paging state for "load older". */
const loadingOlder = ref(false)
/** User message currently at the top of the viewport (drives the timeline highlight). */
const activeAnchor = ref<string | null>(null)
let anchorFrame: number | null = null
const composer = ref<InstanceType<typeof ChatComposer> | null>(null)
let controller: AbortController | null = null

const busy = computed(() => streamingId.value !== null)
const activeConversation = computed(() =>
  conversations.value.find((item) => item.id === activeId.value) ?? null,
)
const title = computed(() => activeConversation.value?.title ?? '新对话')

/** Share of the daily budget at which the one-shot warning appears. */
const WARNING_RATIO = 0.9
const QUOTA_WARNING_KEY = 'nitrowater:ai:quota-warning'

const quotaState = computed<'hidden' | 'warning' | 'exhausted'>(() => {
  if (!authenticated.value) return 'hidden'
  const snapshot = usage.value
  // A backend rejection is authoritative even before the first snapshot arrives (or if the usage
  // endpoint is temporarily down), otherwise the composer would stay enabled after a 429.
  if (exhaustedDate.value !== null && (snapshot === null || exhaustedDate.value === snapshot.date)) {
    return 'exhausted'
  }
  if (!snapshot || snapshot.dailyTokenLimit <= 0) return 'hidden'
  if (snapshot.totalTokens >= snapshot.dailyTokenLimit) return 'exhausted'
  if (snapshot.totalTokens / snapshot.dailyTokenLimit < WARNING_RATIO) return 'hidden'
  // The warning is a one-shot nudge: once dismissed it stays hidden for the rest of the UTC day.
  return warnedDate.value === snapshot.date ? 'hidden' : 'warning'
})
// Only a missing session locks the composer. An exhausted budget still allows slash commands,
// because they never call a provider and therefore cost nothing.
/** Platform-wide breaker: chat is closed for everyone, but slash commands still work. */
const globalTripped = computed(() => status.value?.globalQuota?.tripped === true)
const composerDisabled = computed(() => !authenticated.value)
const composerHint = computed(() =>
  quotaState.value === 'exhausted'
    ? '今日额度已用完 · 仍可执行 / 指令查看状态'
    : undefined,
)
const placeholder = computed(() => {
  if (!authenticated.value) return '登录后即可与 NitroBot 对话'
  if (quotaState.value === 'exhausted') return '今日额度已用完，可输入 / 查看状态'
  return '给 NitroBot 发送消息…'
})
/** Regenerate belongs to the newest answer even when a system card was appended after it. */
const lastAssistantIndex = computed(() => {
  for (let index = messages.value.length - 1; index >= 0; index -= 1) {
    if (messages.value[index].role === 'assistant') return index
  }
  return -1
})

/** Whether an older page can be fetched (server mode only). */
const hasOlder = computed(() => mode.value === 'server' && activeConversation.value?.hasMore === true)

/** One timeline dot per user question. */
const timelineItems = computed<TimelineItem[]>(() =>
  messages.value
    .filter((message) => message.role === 'user' && message.content.trim().length > 0)
    .map((message) => ({
      id: message.id,
      label: message.content.trim().slice(0, 40),
    })),
)

/** Prepends one older page while keeping the viewport anchored on the same message. */
async function loadOlderPage(): Promise<void> {
  const conversation = activeConversation.value
  const container = scrollEl.value
  if (!conversation || !container || loadingOlder.value || !hasOlder.value) return
  loadingOlder.value = true
  const id = conversation.id
  const previousHeight = container.scrollHeight
  const previousTop = container.scrollTop
  try {
    const older = await loadOlder(conversation)
    if (activeId.value !== id || older.length === 0) return
    messages.value = [...older, ...messages.value]
    await nextTick()
    // Content grew above the viewport: shift down by exactly the inserted height.
    container.scrollTop = container.scrollHeight - previousHeight + previousTop
    onTranscriptScroll()
  } catch (error) {
    pushNotice(error instanceof Error ? error.message : '加载更早的消息失败', 'warn')
  } finally {
    loadingOlder.value = false
  }
}

/** Tracks the user message nearest the top so the timeline can highlight it. */
function onTranscriptScroll(): void {
  if (anchorFrame !== null) return
  anchorFrame = window.requestAnimationFrame(() => {
    anchorFrame = null
    const container = scrollEl.value
    if (!container) return
    const origin = container.getBoundingClientRect().top
    let active: string | null = null
    for (const element of container.querySelectorAll<HTMLElement>('[data-msg-id]')) {
      if (element.getBoundingClientRect().top - origin <= 120) {
        active = element.dataset.msgId ?? null
      } else {
        break
      }
    }
    activeAnchor.value = active
  })
}

/** Scrolls to one message and marks its dot active immediately. */
function jumpToMessage(id: string): void {
  const target = document.getElementById(`msg-${id}`)
  if (!target) return
  target.scrollIntoView({ behavior: 'smooth', block: 'start' })
  activeAnchor.value = id
}

onMounted(async () => {
  await initialize(authenticated.value)
  const latest = conversations.value[0]
  if (latest) void openConversation(latest.id)
  if (authenticated.value) {
    await loadStatus()
    await loadUsage()
  }
  await nextTick()
  composer.value?.focus()
})

// The session resolves asynchronously; reload the conversation list, the index status and the
// quota snapshot once it is known whether the user is signed in.
watch(authenticated, (value) => {
  void initialize(value)
  if (value) {
    if (!status.value && !statusError.value) void loadStatus()
    void loadUsage()
  } else {
    usage.value = null
    exhaustedDate.value = null
    lastUsageTotal.value = null
  }
})

onBeforeUnmount(() => controller?.abort())

async function loadStatus(): Promise<void> {
  statusError.value = null
  try {
    status.value = await fetchAiStatus()
  } catch (error) {
    statusError.value = error instanceof Error ? error.message : '读取助手状态失败'
  }
}

/**
 * Refreshes the UTC-day token snapshot. Failures are silent: the snapshot is advisory, and the
 * transcript must stay usable when only the usage endpoint is down.
 */
async function loadUsage(): Promise<void> {
  if (!authenticated.value) {
    usage.value = null
    return
  }
  try {
    const snapshot = await fetchAiUsage()
    // A lower total than the previous snapshot means an administrator reset today's counters.
    const previous = lastUsageTotal.value
    if (previous !== null && snapshot.totalTokens < previous) exhaustedDate.value = null
    // A new UTC day also clears the sticky exhaustion flag.
    if (exhaustedDate.value !== null && exhaustedDate.value !== snapshot.date) exhaustedDate.value = null
    usage.value = snapshot
    lastUsageTotal.value = snapshot.totalTokens
  } catch {
    // Keep the previous snapshot.
  }
}

/** Marks the current UTC day as exhausted after the backend answered {@code token_quota}. */
function markQuotaExhausted(): void {
  exhaustedDate.value = usage.value?.date ?? new Date().toISOString().slice(0, 10)
}

/** Hides the warning for the rest of the UTC day it belongs to. */
function dismissQuotaWarning(): void {
  const date = usage.value?.date
  if (!date) return
  warnedDate.value = date
  try {
    window.localStorage.setItem(QUOTA_WARNING_KEY, date)
  } catch {
    // Private mode / storage disabled: the warning simply reappears next load.
  }
}

function readWarnedDate(): string | null {
  try {
    return window.localStorage.getItem(QUOTA_WARNING_KEY)
  } catch {
    return null
  }
}

async function send(preset?: string): Promise<void> {
  const raw = (preset ?? input.value).trim()
  if (!raw || busy.value) return
  if (!authenticated.value) {
    login(route.fullPath)
    return
  }

  // Slash commands are application shortcuts: they never create a conversation and never reach
  // the model, so they are answered before any chat state is touched.
  const parsed = parseSlash(raw)
  if (parsed?.kind === 'command') {
    input.value = ''
    await runSlash(parsed.command)
    return
  }
  if (parsed?.kind === 'unknown') {
    input.value = ''
    pushNotice(`未知指令 /${parsed.token}，输入 / 查看可用指令`, 'warn')
    return
  }
  const question = parsed?.kind === 'escape' ? parsed.text : raw
  if (!question) return
  // An exhausted budget blocks provider calls only: slash commands were already handled above.
  if (quotaState.value === 'exhausted') {
    pushNotice('今日额度已用完，明天再来试试吧。当前仍可输入 / 查看状态。', 'warn')
    return
  }

  if (globalTripped.value) {
    pushNotice('全站今日 AI 额度已用完，明天再来吧。历史会话与 / 指令仍可用。', 'warn')
    return
  }

  const conversation = await ensureConversation(question)
  // Signed in: the server replays history from its own store, so the client sends none.
  const history = mode.value === 'local' ? toTurns(messages.value) : []
  input.value = ''
  messages.value.push(newMessage('user', question))
  // The bubble only exists after Vue renders; scrolling now would use the previous scrollHeight.
  void scrollToBottomSoon()
  await startTurn(conversation, question, history)
}

/** Runs a deterministic shortcut; the result is rendered as an ephemeral system card. */
async function runSlash(command: SlashCommand): Promise<void> {
  try {
    switch (command.name) {
      case 'quota':
        pushCard({ type: 'quota', data: await fetchSlashQuota() })
        return
      case 'status':
        pushCard({ type: 'status', data: await fetchSlashStatus() })
        return
      case 'profile':
        pushCard({ type: 'profile', data: await fetchSlashProfile() })
        return
      case 'clear':
        await clearContext()
        return
    }
  } catch (error) {
    pushNotice(error instanceof Error ? error.message : '指令执行失败，请稍后重试', 'warn')
  }
}

/** `/clear` keeps the transcript but removes every stored turn from the model context. */
async function clearContext(): Promise<void> {
  const conversation = activeConversation.value
  if (!conversation) {
    pushNotice('当前没有进行中的对话，无需重置', 'info')
    return
  }
  if (busy.value) stop()
  const result = await clearSlashContext(conversation.id)
  pushNotice(result.droppedMessages > 0
    ? `上下文已重置：${result.droppedMessages} 条消息不再发送给模型，聊天记录仍保留`
    : '上下文已重置（当前没有可清理的消息）', 'info')
  void loadUsage()
}

/** Appends a display-only card; system messages are never persisted or sent to the model. */
function pushCard(card: SystemCard): void {
  messages.value.push({ ...newMessage('system', ''), card })
  void scrollToBottomSoon()
}

function pushNotice(text: string, tone: 'info' | 'warn'): void {
  pushCard({ type: 'notice', text, tone })
}

/** Streams one assistant turn into a fresh reply message. */
async function startTurn(conversation: Conversation, question: string, history: ChatTurn[]): Promise<void> {
  messages.value.push(newMessage('assistant', ''))
  const reply = messages.value[messages.value.length - 1]
  await streamInto(reply, conversation, question, history, false)
}

/**
 * Streams a turn into an existing reply bubble.
 *
 * <p>Deltas are revealed by a requestAnimationFrame pump rather than a fixed timer: the provider
 * may deliver several characters per chunk, and painting them in equal slices per frame keeps the
 * visible cadence smooth regardless of upstream burst size. Scrolling happens inside the same
 * frame, so the forced layout is paid at most once per frame.</p>
 */
async function streamInto(
  reply: ChatMessage,
  conversation: Conversation,
  question: string,
  history: ChatTurn[],
  continueGeneration: boolean,
): Promise<void> {
  streamingId.value = reply.id
  reply.activity = null
  controller = new AbortController()
  void scrollToBottomSoon()

  let received = ''
  let shown = 0
  let streaming = true
  let frame: number | null = null
  let lastFrameAt = 0
  /** When the first delta of this turn arrived (used to hold back a one-character start). */
  let firstDeltaAt = 0
  /** Fractional characters carried between frames so slow reveals stay smooth. */
  let carry = 0
  const pump = (timestamp: number): void => {
    frame = null
    if (!streaming) return
    const elapsed = lastFrameAt === 0 ? 16 : Math.min(120, timestamp - lastFrameAt)
    lastFrameAt = timestamp
    const backlog = received.length - shown
    // Hold the very first paint until a small cushion exists: revealing one character, waiting for
    // the next chunk and then bursting is exactly the "chunk … pause" feel this pump removes.
    const starting = shown === 0 && backlog < 8 && timestamp - firstDeltaAt < 160
    if (backlog > 0 && !starting) {
      // Reveal at a rate instead of "drain the buffer": a provider burst would otherwise be
      // painted in a few frames and then sit idle. Aim to clear the backlog in ~0.45s, never
      // slower than 55 chars/s so a provider stall still looks like steady typing.
      const rate = Math.min(320, Math.max(55, backlog / 0.45))
      carry += (rate * elapsed) / 1000
      const step = Math.min(backlog, Math.floor(carry))
      if (step > 0) {
        carry -= step
        reply.content += received.slice(shown, shown + step)
        shown += step
        scrollToBottom()
      }
    }
    if (streaming) frame = window.requestAnimationFrame(pump)
  }
  const ensurePump = (): void => {
    if (frame === null) frame = window.requestAnimationFrame(pump)
  }

  try {
    await streamAgent(
      question,
      conversation.id,
      history,
      '',
      status.value?.webSearchEnabled === true && webSearch.value,
      continueGeneration,
      {
        onConversation: (uuid) => adoptConversationId(conversation, uuid),
        onSources: (sources, grounded) => {
          reply.sources = sources
          reply.grounded = grounded
        },
        onTools: (tools) => {
          reply.tools = tools
        },
        onSkills: (skills) => {
          reply.skills = skills
        },
        onStep: (step) => {
          const phase = step.phase === 'tool_start' || step.phase === 'tool_end' ? step.phase : 'thinking'
          reply.activity = { phase, tool: step.tool }
        },
        onDelta: (text) => {
          if (firstDeltaAt === 0) firstDeltaAt = performance.now()
          received += text
          // The live card describes the pre-answer phase only: once tokens flow, the answer itself
          // is the feedback and the spinner would just sit above the text.
          if (reply.activity) reply.activity = null
          ensurePump()
        },
        onModeration: (text) => {
          // Tokens that already reached the browser cannot be recalled. Replace the bubble with the
          // text the server actually stored and move the reveal cursor to the end, so the pending
          // pump cannot append the un-masked buffer afterwards.
          received = text
          shown = text.length
          reply.content = text
          reply.moderated = true
        },
        onDone: (meta) => {
          reply.truncated = meta.truncated
        },
        onError: (message, code) => {
          reply.error = message
          if (code === ResponseCode.TokenQuota) markQuotaExhausted()
        },
      },
      controller.signal,
    )
  } catch (error) {
    if (controller?.signal.aborted) {
      reply.stopped = true
    } else if (error instanceof AiQuotaError) {
      markQuotaExhausted()
      reply.error = error.message
    } else {
      reply.error = error instanceof Error ? error.message : '请求失败，请稍后重试'
    }
  } finally {
    streaming = false
    if (frame !== null) {
      window.cancelAnimationFrame(frame)
      frame = null
    }
    // Flush whatever the pump had not revealed yet (also covers abort/failure).
    if (shown < received.length) reply.content += received.slice(shown)
    reply.activity = null
    streamingId.value = null
    controller = null
    void loadUsage()
    persist(conversation)
    await nextTick()
    scrollToBottom(true)
    composer.value?.focus()
  }
}

/** Continues a truncated answer in place, appending the new tokens to the same bubble. */
function continueAnswer(): void {
  if (busy.value) return
  const conversation = activeConversation.value
  if (!conversation) return
  let reply: ChatMessage | null = null
  for (let index = messages.value.length - 1; index >= 0; index -= 1) {
    if (messages.value[index].role === 'assistant') {
      reply = messages.value[index]
      break
    }
  }
  if (!reply) return
  reply.truncated = false
  void streamInto(reply, conversation, '继续', [], true)
}

/** Drops the last answer and asks the same question again. */
function regenerate(): void {
  if (busy.value) return
  let lastUser = -1
  for (let index = messages.value.length - 1; index >= 0; index -= 1) {
    if (messages.value[index].role === 'user') {
      lastUser = index
      break
    }
  }
  if (lastUser < 0) return

  const conversation = activeConversation.value
  if (!conversation) return
  const question = messages.value[lastUser].content
  const history = toTurns(messages.value.slice(0, lastUser))
  messages.value = messages.value.slice(0, lastUser + 1)
  void startTurn(conversation, question, history)
}

function stop(): void {
  controller?.abort()
}

function newChat(): void {
  stop()
  activeId.value = null
  messages.value = []
  input.value = ''
  sidebarOpen.value = false
  void nextTick(() => composer.value?.focus())
}

function openConversation(id: string): void {
  if (busy.value) stop()
  const conversation = conversations.value.find((item) => item.id === id)
  if (!conversation) return
  activeId.value = id
  messages.value = conversation.messages.map(cloneMessage)
  sidebarOpen.value = false
  void scrollToBottomSoon()
  void loadMessages(conversation).then(() => {
    if (activeId.value !== id) return
    messages.value = conversation.messages.map(cloneMessage)
    void scrollToBottomSoon()
  })
}

function removeConversation(id: string): void {
  void remove(id)
  if (activeId.value === id) newChat()
}

/** Starter cards only prefill the composer — sending stays explicit. */
function prefill(text: string): void {
  input.value = text
  void nextTick(() => composer.value?.focus())
}

function onLogout(): void {
  if (window.confirm('退出登录？')) logout()
}

async function ensureConversation(question: string): Promise<Conversation> {
  if (!activeConversation.value) {
    const created = await create()
    activeId.value = created.id
  }
  const conversation = activeConversation.value
  if (!conversation) throw new Error('conversation unavailable')
  if (conversation.title === '新对话') conversation.title = titleFrom(question)
  return conversation
}

/** Adopts the server uuid when the backend created the conversation for this turn. */
function adoptConversationId(conversation: Conversation, uuid: string): void {
  if (conversation.id === uuid) return
  const previous = conversation.id
  conversation.id = uuid
  if (activeId.value === previous) activeId.value = uuid
  persist(conversation)
}

function persist(conversation: Conversation): void {
  // System cards are display-only: dropping them here keeps them out of the local cache too.
  conversation.messages = messages.value
    .filter((message) => message.role !== 'system')
    .map(cloneMessage)
  save(conversation)
}

function toTurns(list: ChatMessage[]): ChatTurn[] {
  return list
    .filter((message): message is ChatMessage & { role: 'user' | 'assistant' } =>
      (message.role === 'user' || message.role === 'assistant')
      && !message.error
      && message.content.trim().length > 0)
    .slice(-8)
    .map((message) => ({ role: message.role, content: message.content.trim() }))
}

function newMessage(role: ChatMessage['role'], content: string): ChatMessage {
  return {
    id: newId(),
    role,
    content,
    sources: [],
    tools: [],
    skills: [],
    grounded: null,
    error: null,
  }
}

function cloneMessage(message: ChatMessage): ChatMessage {
  return {
    ...message,
    sources: [...message.sources],
    tools: [...message.tools],
    skills: [...message.skills],
  }
}

/** Scrolls to the newest content once Vue has applied the pending DOM update. */
async function scrollToBottomSoon(): Promise<void> {
  await nextTick()
  scrollToBottom(true)
}

function scrollToBottom(force = false): void {
  const element = scrollEl.value
  if (!element) return
  const nearBottom = element.scrollHeight - element.scrollTop - element.clientHeight < 140
  if (force || nearBottom) element.scrollTop = element.scrollHeight
}

function titleFrom(question: string): string {
  return question.length > 24 ? `${question.slice(0, 24)}…` : question
}

function newId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return `m-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}
</script>

<template>
  <div class="flex h-dvh overflow-hidden bg-bg text-ink">
    <div
      v-if="sidebarOpen"
      class="fixed inset-0 z-30 bg-black/40 lg:hidden"
      @click="sidebarOpen = false"
    />

    <ChatSidebar
      :conversations="conversations"
      :active-id="activeId"
      :open="sidebarOpen"
      :collapsed="sidebarCollapsed"
      :authenticated="authenticated"
      :user-label="userLabel"
      :user-initial="userInitial"
      @new-chat="newChat"
      @select="openConversation"
      @remove="removeConversation"
      @toggle="sidebarCollapsed = true"
      @close="sidebarOpen = false"
      @logout="onLogout"
    />

    <div class="flex min-w-0 flex-1 flex-col">
      <header class="flex h-[52px] shrink-0 items-center gap-2 border-b border-line px-3 sm:px-4">
        <button
          v-if="sidebarCollapsed"
          type="button"
          class="hidden size-8 place-items-center rounded-lg text-dim transition hover:bg-soft hover:text-ink lg:grid"
          title="展开侧边栏"
          aria-label="展开侧边栏"
          @click="sidebarCollapsed = false"
        >
          <svg
            class="size-4"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.9"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <line
              x1="3"
              y1="6"
              x2="21"
              y2="6"
            />
            <line
              x1="3"
              y1="12"
              x2="21"
              y2="12"
            />
            <line
              x1="3"
              y1="18"
              x2="21"
              y2="18"
            />
          </svg>
        </button>
        <button
          type="button"
          class="grid size-8 place-items-center rounded-lg text-dim transition hover:bg-soft hover:text-ink lg:hidden"
          title="打开对话列表"
          aria-label="打开对话列表"
          @click="sidebarOpen = true"
        >
          <svg
            class="size-4"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.9"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <line
              x1="3"
              y1="6"
              x2="21"
              y2="6"
            />
            <line
              x1="3"
              y1="12"
              x2="21"
              y2="12"
            />
            <line
              x1="3"
              y1="18"
              x2="21"
              y2="18"
            />
          </svg>
        </button>

        <h1 class="min-w-0 flex-1 truncate text-center text-[13.5px] font-semibold text-dim">
          {{ title }}
        </h1>

        <span class="shrink-0 rounded-full bg-accent/10 px-2.5 py-1 text-[11px] font-semibold text-accent">
          通用模式
        </span>
      </header>

      <div class="relative min-h-0 flex-1">
        <div
          ref="scrollEl"
          class="scroll-thin h-full overflow-y-auto overscroll-contain"
          @scroll.passive="onTranscriptScroll"
        >
        <div class="mx-auto w-full max-w-[760px] px-4 pb-6 pt-5">
          <div
            v-if="hasOlder"
            class="mb-4 flex justify-center"
          >
            <button
              type="button"
              class="btn btn-sm"
              :disabled="loadingOlder"
              @click="loadOlderPage"
            >
              {{ loadingOlder ? '加载中…' : '加载更早的消息' }}
            </button>
          </div>
          <div
            v-if="statusError"
            class="mb-4 rounded-xl border border-danger/30 bg-danger/10 px-4 py-2.5 text-[12.5px] text-danger"
          >
            {{ statusError }}
          </div>
          <div
            v-else-if="status && (!status.enabled || status.error)"
            class="mb-4 rounded-xl border border-danger/30 bg-danger/10 px-4 py-2.5 text-[12.5px] text-danger"
          >
            {{ status.error || 'NitroBot 暂不可用' }}
          </div>
          <div
            v-else-if="status && !status.agentEnabled"
            class="mb-4 rounded-xl border border-danger/30 bg-danger/10 px-4 py-2.5 text-[12.5px] text-danger"
          >
            管理后台已关闭 Agent 模式，当前无法对话。
          </div>
          <div
            v-else-if="status && !status.indexed"
            class="mb-4 rounded-xl border border-warn/30 bg-warn/10 px-4 py-2.5 text-[12.5px] text-warn"
          >
            文档索引尚未构建，回答暂时不会带站点文档引用（通用问题仍可正常回答）。
          </div>

          <div
            v-if="authLoading"
            class="py-16 text-center text-[12.5px] text-dim"
          >
            正在读取登录状态…
          </div>

          <ChatEmpty
            v-else-if="messages.length === 0"
            :authenticated="authenticated"
            @pick="prefill"
            @login="login(route.fullPath)"
          />

          <div
            v-else
            class="flex flex-col gap-6"
          >
            <template
              v-for="(message, index) in messages"
              :key="message.id"
            >
              <SystemCardView
                v-if="message.role === 'system' && message.card"
                :card="message.card"
              />
              <MessageItem
                v-else
                :id="`msg-${message.id}`"
                :data-msg-id="message.id"
                :message="message"
                :streaming="message.id === streamingId"
                :can-regenerate="message.role === 'assistant' && index === lastAssistantIndex"
                :busy="busy"
                :user-initial="userInitial"
                @regenerate="regenerate"
                @continue="continueAnswer"
              />
            </template>
          </div>
        </div>
        </div>

        <ChatTimeline
          :items="timelineItems"
          :active-id="activeAnchor"
          @select="jumpToMessage"
        />
      </div>

      <GlobalQuotaNotice
        v-if="globalTripped"
        :quota="status?.globalQuota ?? null"
      />

      <QuotaNotice
        :usage="usage"
        :state="quotaState"
        @dismiss="dismissQuotaWarning"
      />

      <ChatComposer
        ref="composer"
        v-model="input"
        :busy="busy"
        :disabled="composerDisabled"
        :hint="composerHint"
        :commands-enabled="authenticated"
        :web-search-available="status?.webSearchEnabled === true"
        v-model:web-search="webSearch"
        :placeholder="placeholder"
        @submit="send()"
        @stop="stop"
        @login="login(route.fullPath)"
      />
    </div>
  </div>
</template>
