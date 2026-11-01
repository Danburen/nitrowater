import { computed, ref } from 'vue'
import * as api from '../../../api/conversations'
import type { ServerMessage } from '../../../api/conversations'
import type { ChatMessage } from '../types'

/** One conversation in the sidebar. `id` is the server uuid when signed in, a local id otherwise. */
export interface Conversation {
  id: string
  title: string
  createdAt: number
  updatedAt: number
  messages: ChatMessage[]
  /** Server paging state: older pages exist and where to continue from. */
  hasMore?: boolean
  oldestSeq?: number | null
}

/** How the store is backed: the server when signed in, localStorage otherwise. */
export type ConversationMode = 'server' | 'local'

/** Storage key; bump the suffix when the cached shape changes incompatibly. */
const STORAGE_KEY = 'nitrowater.ai.conversations.v2'
/** Keep the sidebar short and the payload well inside the ~5 MB localStorage budget. */
const MAX_CONVERSATIONS = 40
const MAX_BYTES = 1_500_000
const MESSAGE_PAGE = 50

const conversations = ref<Conversation[]>(readStored())
const mode = ref<ConversationMode>('local')
const loading = ref(false)
const syncError = ref<string | null>(null)
let initializedFor: boolean | null = null

/**
 * Conversation store with a server backend and a local fallback.
 *
 * <p>Signed in: the server owns conversations and messages, and the browser keeps a cache so the
 * sidebar and the transcript still render when the API is briefly unavailable. Signed out (or when
 * the API is unreachable): everything stays in {@code localStorage}, exactly like before T11.</p>
 */
export function useConversations() {
  return {
    conversations: computed(() => conversations.value),
    mode: computed(() => mode.value),
    loading: computed(() => loading.value),
    syncError: computed(() => syncError.value),
    initialize,
    create,
    remove,
    rename,
    loadMessages,
    loadOlder,
    save,
    find,
  }
}

/** Loads the server list once per auth state; degrades to local mode on failure. */
async function initialize(authenticated: boolean): Promise<void> {
  if (initializedFor === authenticated) return
  initializedFor = authenticated
  syncError.value = null
  if (!authenticated) {
    // Drop the previous user's server-side transcripts from the sidebar on sign-out.
    if (mode.value === 'server') {
      conversations.value = []
      persist()
    }
    mode.value = 'local'
    return
  }
  loading.value = true
  try {
    const server = await api.listConversations(null, MAX_CONVERSATIONS)
    const cached = new Map(conversations.value.map((item) => [item.id, item]))
    conversations.value = server.map((item) => {
      const previous = cached.get(item.uuid)
      return {
        id: item.uuid,
        title: item.title?.trim() || '新对话',
        createdAt: Date.parse(item.createdAt),
        updatedAt: Date.parse(item.lastMessageAt ?? item.createdAt),
        messages: previous?.messages ?? [],
      }
    })
    mode.value = 'server'
    persist()
  } catch (error) {
    // Keep whatever is cached: a stale transcript beats an empty screen.
    mode.value = 'local'
    syncError.value = error instanceof Error ? error.message : '读取对话列表失败'
  } finally {
    loading.value = false
  }
}

/** Creates a conversation on the server (or locally when offline). */
async function create(title?: string): Promise<Conversation> {
  const now = Date.now()
  if (mode.value === 'server') {
    try {
      const created = await api.createConversation(title)
      const conversation: Conversation = {
        id: created.uuid,
        title: created.title?.trim() || '新对话',
        createdAt: Date.parse(created.createdAt),
        updatedAt: Date.parse(created.lastMessageAt ?? created.createdAt),
        messages: [],
      }
      conversations.value = [conversation, ...conversations.value]
      persist()
      return conversation
    } catch (error) {
      syncError.value = error instanceof Error ? error.message : '创建对话失败'
    }
  }
  const conversation: Conversation = {
    id: newId(),
    title: title?.trim() || '新对话',
    createdAt: now,
    updatedAt: now,
    messages: [],
  }
  conversations.value = [conversation, ...conversations.value]
  persist()
  return conversation
}

/** Saves the transcript to the local cache and reorders the sidebar. */
function save(conversation: Conversation): void {
  conversation.updatedAt = Date.now()
  const index = conversations.value.findIndex((item) => item.id === conversation.id)
  if (index === -1) {
    conversations.value = [conversation, ...conversations.value]
  } else {
    conversations.value[index] = conversation
  }
  conversations.value = [...conversations.value].sort((a, b) => b.updatedAt - a.updatedAt)
  persist()
}

/** Deletes the conversation on the server (best effort) and locally. */
async function remove(id: string): Promise<void> {
  if (mode.value === 'server') {
    try {
      await api.deleteConversation(id)
    } catch (error) {
      syncError.value = error instanceof Error ? error.message : '删除对话失败'
    }
  }
  conversations.value = conversations.value.filter((item) => item.id !== id)
  persist()
}

/** Renames a conversation on the server (best effort) and locally. */
async function rename(id: string, title: string): Promise<void> {
  const trimmed = title.trim()
  if (!trimmed) return
  const conversation = conversations.value.find((item) => item.id === id)
  if (!conversation) return
  conversation.title = trimmed.slice(0, 60)
  persist()
  if (mode.value === 'server') {
    try {
      await api.patchConversation(id, { title: conversation.title })
    } catch (error) {
      syncError.value = error instanceof Error ? error.message : '重命名对话失败'
    }
  }
}

/** Fills a conversation from the server and records its paging state. */
async function loadMessages(conversation: Conversation): Promise<void> {
  if (mode.value !== 'server') return
  // Cached transcripts render immediately, but the paging metadata may still be unknown (the
  // cache only stores messages). Fetch once in that case so "load older" can work.
  if (conversation.messages.length > 0 && conversation.hasMore !== undefined) return
  try {
    const page = await api.fetchMessages(conversation.id, undefined, MESSAGE_PAGE)
    conversation.messages = page.items.map(toChatMessage)
    conversation.hasMore = page.hasMore
    conversation.oldestSeq = page.items[0]?.seq ?? null
    persist()
  } catch (error) {
    syncError.value = error instanceof Error ? error.message : '读取对话内容失败'
  }
}

/**
 * Loads the page before the oldest loaded message (server mode only).
 *
 * @param conversation conversation to extend
 * @returns the newly prepended messages (empty when there is nothing older)
 */
async function loadOlder(conversation: Conversation): Promise<ChatMessage[]> {
  if (mode.value !== 'server' || !conversation.hasMore || conversation.oldestSeq == null) return []
  const page = await api.fetchMessages(conversation.id, conversation.oldestSeq, MESSAGE_PAGE)
  const older = page.items.map(toChatMessage)
  conversation.messages = [...older, ...conversation.messages]
  conversation.hasMore = page.hasMore
  conversation.oldestSeq = page.items[0]?.seq ?? conversation.oldestSeq
  persist()
  return older
}

/** Maps a stored message onto the transcript shape. */
function toChatMessage(message: ServerMessage): ChatMessage {
  return {
    id: message.uuid,
    role: message.role === 'user' ? 'user' : 'assistant',
    content: message.content,
    sources: message.sources ?? [],
    tools: message.tools ?? [],
    skills: [],
    grounded: (message.sources?.length ?? 0) > 0,
    error: message.role === 'error' ? message.content : null,
    moderated: message.moderated === true ? true : undefined,
  }
}

function find(id: string | null): Conversation | null {
  if (!id) return null
  return conversations.value.find((item) => item.id === id) ?? null
}

/** Writes the list, trimming the oldest conversations until it fits the byte budget. */
function persist(): void {
  conversations.value = conversations.value.slice(0, MAX_CONVERSATIONS)
  for (;;) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(conversations.value))
      return
    } catch {
      // Quota exceeded (or storage disabled): drop the oldest conversation and retry.
      if (conversations.value.length <= 1) return
      conversations.value = conversations.value.slice(0, -1)
    }
  }
}

function readStored(): Conversation[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    if (raw.length > MAX_BYTES) localStorage.removeItem(STORAGE_KEY)
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter(isConversation)
      .map((item) => ({ ...item, messages: item.messages.filter(isMessage) }))
      .sort((a, b) => b.updatedAt - a.updatedAt)
      .slice(0, MAX_CONVERSATIONS)
  } catch {
    return []
  }
}

/** Structural check so a hand-edited/corrupted entry cannot crash the page. */
function isConversation(value: unknown): value is Conversation {
  if (typeof value !== 'object' || value === null) return false
  const item = value as Partial<Conversation>
  return typeof item.id === 'string'
    && typeof item.title === 'string'
    && typeof item.createdAt === 'number'
    && typeof item.updatedAt === 'number'
    && Array.isArray(item.messages)
}

/** Keeps only well-formed messages (older payloads may lack the newer fields). */
function isMessage(value: unknown): value is ChatMessage {
  if (typeof value !== 'object' || value === null) return false
  const item = value as Partial<ChatMessage>
  return typeof item.id === 'string'
    && (item.role === 'user' || item.role === 'assistant')
    && typeof item.content === 'string'
}

function newId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return `c-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}
