/**
 * Server-side NitroBot conversation API (`/api/ai/conversations`).
 *
 * The server is the source of truth once the user is signed in; the browser keeps a cache so a
 * transient failure degrades to the local transcript instead of an empty sidebar.
 */
import { apiFetch } from '../auth/bff'
import { readJson } from './envelope'
import type { AiSource, AiToolCall } from './ai'

/** A conversation as returned by the list endpoint. */
export interface ServerConversation {
  uuid: string
  title: string | null
  messageCount: number
  lastMessageAt: string | null
  createdAt: string
  status: 'ACTIVE' | 'ARCHIVED'
}

/** One stored message. */
export interface ServerMessage {
  uuid: string
  seq: number
  role: 'user' | 'assistant' | 'error'
  content: string
  sources: AiSource[]
  tools: AiToolCall[]
  /** Skills loaded by the turn; persisted so the process trace survives a reload. */
  skills: string[]
  model: string | null
  /** True when the stored answer was masked by the content gate. */
  moderated?: boolean
  createdAt: string
}

/** A page of messages in ascending sequence order. */
export interface ServerMessagePage {
  items: ServerMessage[]
  hasMore: boolean
}

/** Lists active conversations, newest first. */
export async function listConversations(cursor?: string | null, limit = 50): Promise<ServerConversation[]> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (cursor) params.set('cursor', cursor)
  return getJson<ServerConversation[]>(`/api/ai/conversations?${params.toString()}`)
}

/** Creates an empty conversation. */
export async function createConversation(title?: string): Promise<ServerConversation> {
  return sendJson<ServerConversation>('/api/ai/conversations', 'POST', title ? { title } : {})
}

/** Reads a page of messages; pass {@code before} to walk upwards. */
export async function fetchMessages(uuid: string, before?: number, limit = 50): Promise<ServerMessagePage> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (before !== undefined) params.set('before', String(before))
  return getJson<ServerMessagePage>(`/api/ai/conversations/${encodeURIComponent(uuid)}/messages?${params.toString()}`)
}

/** Renames and/or archives a conversation. */
export async function patchConversation(
  uuid: string,
  patch: { title?: string; archived?: boolean },
): Promise<ServerConversation> {
  return sendJson<ServerConversation>(`/api/ai/conversations/${encodeURIComponent(uuid)}`, 'PATCH', patch)
}

/** Soft-deletes a conversation. */
export async function deleteConversation(uuid: string): Promise<void> {
  const response = await apiFetch(`/api/ai/conversations/${encodeURIComponent(uuid)}`, { method: 'DELETE' })
  if (!response.ok) throw new Error(await errorMessage(response))
}

async function getJson<T>(url: string): Promise<T> {
  const response = await apiFetch(url)
  if (!response.ok) throw new Error(await errorMessage(response))
  return readJson<T>(response)
}

async function sendJson<T>(url: string, method: 'POST' | 'PATCH', body: unknown): Promise<T> {
  const response = await apiFetch(url, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) throw new Error(await errorMessage(response))
  return readJson<T>(response)
}

async function errorMessage(response: Response): Promise<string> {
  if (response.status === 401) return '登录状态已失效，请重新登录'
  if (response.status === 404) return '对话不存在或已被删除'
  const text = await response.text().catch(() => '')
  try {
    const parsed = JSON.parse(text) as { message?: string; detail?: string }
    if (parsed.message) return parsed.message
    if (parsed.detail) return parsed.detail
  } catch {
    // Not JSON; fall through.
  }
  return text || `请求失败（HTTP ${response.status}）`
}
