/**
 * Documentation-assistant client.
 *
 * The chat stream is a POST (it carries the question and the short history), so `EventSource`
 * cannot be used; the response body is parsed as SSE frames here instead. Requests go through the
 * BFF (`/api/ai/**`), which owns the session cookie and forwards the question with the server-held
 * access token.
 */
import { apiFetch } from '../../auth/bff'

/** A citation returned with an answer (local doc chunk or web result). */
export interface AiSource {
  index: number
  kind: 'doc' | 'web'
  title: string
  section: string
  path: string
  url: string
  score: number
  snippet: string
}

/** Index/model status. */
export interface AiStatus {
  enabled: boolean
  indexing: boolean
  indexed: boolean
  files: number
  chunks: number
  indexedAt: string | null
  durationMs: number
  error: string | null
  collection: string
  chatModel: string
  embeddingModel: string
  topK: number
  similarityThreshold: number
  webSearchEnabled: boolean
  dailyTokenLimit: number
  chatEnabled: boolean
  agentEnabled: boolean
  documentSearchEnabled: boolean
  codeRunnerEnabled: boolean
  skillsEnabled: boolean
}

/** One user's UTC-day token usage. */
export interface AiDailyUsage {
  date: string
  inputTokens: number
  outputTokens: number
  reservedTokens: number
  requestCount: number
  estimatedRequests: number
  dailyTokenLimit: number
  totalTokens: number
}

/** Runtime controls and per-user token ceiling edited by administrators. */
export interface AiAdminSettings {
  dailyTokenLimit: number
  aiEnabled: boolean
  chatEnabled: boolean
  agentEnabled: boolean
  documentSearchEnabled: boolean
  webSearchEnabled: boolean
  codeRunnerEnabled: boolean
  skillsEnabled: boolean
}

/** Administrator view of one user's usage for the current UTC day. */
export interface AiUserUsage {
  uid: number
  username: string | null
  nickname: string | null
  usage: AiDailyUsage
}

/** A skill shown in the picker / admin console. */
export interface AiSkill {
  name: string
  description: string
  whenToUse: string
  source: 'builtin' | 'external'
}

/** One tool invocation recorded by the agent. */
export interface AiToolCall {
  name: string
  argument: string
  resultCount: number
  /** Normalised outcome, e.g. ok / compile_error / queued / retry_exhausted. */
  status?: string
  /** Short human-readable detail for the tool chip. */
  summary?: string | null
}

/** A tool in the admin catalog. */
export interface AiTool {
  name: string
  description: string
  source: 'core' | 'web'
  enabled: boolean
  inputSchema: string
}

/** One previous turn; the server keeps no session state. */
export interface ChatTurn {
  role: 'user' | 'assistant'
  content: string
}

/** Reads the assistant status (login required). */
export async function fetchAiStatus(): Promise<AiStatus> {
  return getJson<AiStatus>('/api/ai/status')
}

/** Reads the authenticated user's UTC-day token usage. */
export async function fetchAiUsage(): Promise<AiDailyUsage> {
  return getJson<AiDailyUsage>('/api/ai/usage')
}

/** Admin: current runtime settings. */
export async function fetchAdminAiSettings(): Promise<AiAdminSettings> {
  return getJson<AiAdminSettings>('/api/admin/ai/settings')
}

/** Admin: update the complete runtime settings snapshot. */
export async function updateAdminAiSettings(settings: AiAdminSettings): Promise<AiAdminSettings> {
  const response = await apiFetch('/api/admin/ai/settings', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(settings),
  })
  if (!response.ok) throw new Error(await readErrorMessage(response))
  return (await response.json()) as AiAdminSettings
}

/** Admin: all users with recorded usage today, ordered by consumption. */
export async function fetchAdminAiUsage(): Promise<AiUserUsage[]> {
  return getJson<AiUserUsage[]>('/api/admin/ai/usage')
}

/** Admin: resets selected users or all users when {@code uids} is empty. */
export async function resetAdminAiUsage(uids: readonly number[]): Promise<number> {
  const response = await apiFetch('/api/admin/ai/usage/reset', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ uids }),
  })
  if (!response.ok) throw new Error(await readErrorMessage(response))
  return (await response.json()) as number
}

/** Reads the skills available to the current user. */
export async function fetchSkills(): Promise<AiSkill[]> {
  return getJson<AiSkill[]>('/api/ai/skills')
}

/** Admin: skills with their source. */
export async function fetchAdminSkills(): Promise<AiSkill[]> {
  return getJson<AiSkill[]>('/api/admin/ai/skills')
}

/** Admin: the tool catalog advertised to the agent. */
export async function fetchAdminTools(): Promise<AiTool[]> {
  return getJson<AiTool[]>('/api/admin/ai/tools')
}

/**
 * Rebuilds the documentation index (admin only). The backend re-embeds the whole corpus, so this
 * can take a while on a cold model.
 */
export async function reindexDocs(): Promise<{ files: number; chunks: number; indexedAt: string; durationMs: number }> {
  const response = await apiFetch('/api/admin/ai/reindex', { method: 'POST' })
  if (!response.ok) throw new Error(await readErrorMessage(response))
  return (await response.json()) as { files: number; chunks: number; indexedAt: string; durationMs: number }
}

/** Raised when the daily token budget is exhausted (HTTP 429 with {@code token_quota}). */
export class AiQuotaError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'AiQuotaError'
  }
}

/** Callbacks for one streamed RAG answer. */
export interface ChatStreamHandlers {
  onConversation?: (uuid: string) => void
  onSources?: (sources: AiSource[], grounded: boolean) => void
  onDelta?: (text: string) => void
  onDone?: () => void
  onError?: (message: string, code?: string) => void
}

/** One live progress marker from the agent loop. */
export interface AgentStep {
  phase: 'thinking' | 'tool_start' | 'tool_end' | string
  tool: string | null
}

/** How the model ended the turn. */
export interface AgentDoneMeta {
  finishReason: string | null
  /** True when the provider stopped because the token limit was reached. */
  truncated: boolean
}

/** Callbacks for one streamed agent turn. */
export interface AgentStreamHandlers {
  onConversation?: (uuid: string) => void
  onStart?: () => void
  onDelta?: (text: string) => void
  onSources?: (sources: AiSource[], grounded: boolean) => void
  onTools?: (tools: AiToolCall[]) => void
  onSkills?: (skills: string[]) => void
  onStep?: (step: AgentStep) => void
  onDone?: (meta: AgentDoneMeta) => void
  onError?: (message: string, code?: string) => void
}

/**
 * Posts a question and consumes the RAG SSE answer stream.
 *
 * @param question user question
 * @param conversationUuid server-side conversation to continue; blank starts a new one
 * @param history previous turns (ignored by the server when a uuid is supplied)
 * @param handlers stream callbacks
 * @param signal abort signal (the page aborts on unmount / "stop")
 * @throws when the request is rejected before the stream starts
 */
export async function streamChat(
  question: string,
  conversationUuid: string | null,
  history: ChatTurn[],
  handlers: ChatStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const response = await apiFetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({ question, conversationUuid, history }),
    signal,
  })
  if (!response.ok) await reject(response)
  await readSse(response, (event, payload) => dispatchChat(event, payload, handlers))
}

/**
 * Posts a question and consumes the agent SSE stream.
 *
 * <p>The agent runs tools before the first answer token; citations and the tool trace arrive at
 * the end of the stream. The first {@code conversation} event carries the conversation uuid, which
 * a client that started a new conversation should keep.</p>
 *
 * @param question user question
 * @param conversationUuid server-side conversation to continue; blank starts a new one
 * @param history previous turns (ignored by the server when a uuid is supplied)
 * @param skill optional selected skill name
 * @param allowWeb whether the user allows web search for this turn
 * @param continueGeneration continue a truncated answer instead of asking a new question
 * @param handlers stream callbacks
 * @param signal abort signal
 */
export async function streamAgent(
  question: string,
  conversationUuid: string | null,
  history: ChatTurn[],
  skill: string,
  allowWeb: boolean,
  continueGeneration: boolean,
  handlers: AgentStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const response = await apiFetch('/api/ai/agent/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({
      question,
      conversationUuid,
      history,
      skill: skill || null,
      allowWeb,
      continueGeneration,
    }),
    signal,
  })
  if (!response.ok) await reject(response)
  await readSse(response, (event, payload) => dispatchAgent(event, payload, handlers))
}

/** Reads an SSE body and calls {@code onFrame(event, data)} for every complete frame. */
async function readSse(
  response: Response,
  onFrame: (event: string, payload: string) => void,
): Promise<void> {
  if (!response.body) throw new Error('当前浏览器不支持流式响应')
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      for (;;) {
        const separator = /\r?\n\r?\n/.exec(buffer)
        if (!separator || separator.index === undefined) break
        const frame = buffer.slice(0, separator.index)
        buffer = buffer.slice(separator.index + separator[0].length)
        const parsed = parseFrame(frame)
        if (!parsed) continue
        onFrame(parsed.event, parsed.payload)
      }
    }
  } finally {
    reader.releaseLock()
  }
}

function parseFrame(frame: string): { event: string; payload: string } | null {
  let event = 'message'
  const data: string[] = []
  for (const line of frame.split(/\r?\n/)) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
  }
  if (data.length === 0) return null
  return { event, payload: data.join('\n') }
}

function dispatchChat(event: string, payload: string, handlers: ChatStreamHandlers): void {
  try {
    if (event === 'conversation') {
      const parsed = JSON.parse(payload) as { uuid?: string }
      if (parsed.uuid) handlers.onConversation?.(parsed.uuid)
    } else if (event === 'sources') {
      const parsed = JSON.parse(payload) as { sources?: AiSource[]; grounded?: boolean }
      handlers.onSources?.(parsed.sources ?? [], parsed.grounded ?? false)
    } else if (event === 'delta') {
      const parsed = JSON.parse(payload) as { text?: string }
      if (parsed.text) handlers.onDelta?.(parsed.text)
    } else if (event === 'error') {
      const parsed = JSON.parse(payload) as { message?: string; code?: string }
      handlers.onError?.(parsed.message ?? '生成回答时出错', parsed.code)
    } else if (event === 'done') {
      handlers.onDone?.()
    }
  } catch {
    // A malformed frame is ignored: the stream may have been cut mid-line.
  }
}

function dispatchAgent(event: string, payload: string, handlers: AgentStreamHandlers): void {
  try {
    if (event === 'conversation') {
      const parsed = JSON.parse(payload) as { uuid?: string }
      if (parsed.uuid) handlers.onConversation?.(parsed.uuid)
    } else if (event === 'start') {
      handlers.onStart?.()
    } else if (event === 'sources') {
      const parsed = JSON.parse(payload) as { sources?: AiSource[]; grounded?: boolean }
      handlers.onSources?.(parsed.sources ?? [], parsed.grounded ?? false)
    } else if (event === 'tools') {
      const parsed = JSON.parse(payload) as { tools?: AiToolCall[] }
      handlers.onTools?.(parsed.tools ?? [])
    } else if (event === 'skills') {
      const parsed = JSON.parse(payload) as { skills?: string[] }
      handlers.onSkills?.(parsed.skills ?? [])
    } else if (event === 'delta') {
      const parsed = JSON.parse(payload) as { text?: string }
      if (parsed.text) handlers.onDelta?.(parsed.text)
    } else if (event === 'step') {
      const parsed = JSON.parse(payload) as { phase?: string; tool?: string | null }
      if (parsed.phase) handlers.onStep?.({ phase: parsed.phase, tool: parsed.tool ?? null })
    } else if (event === 'error') {
      const parsed = JSON.parse(payload) as { message?: string; code?: string }
      handlers.onError?.(parsed.message ?? 'Agent 执行出错', parsed.code)
    } else if (event === 'done') {
      const parsed = JSON.parse(payload) as { finishReason?: string | null; truncated?: boolean }
      handlers.onDone?.({ finishReason: parsed.finishReason ?? null, truncated: parsed.truncated === true })
    }
  } catch {
    // Ignore malformed frames.
  }
}

async function getJson<T>(url: string): Promise<T> {
  const response = await apiFetch(url)
  if (!response.ok) throw new Error(await readErrorMessage(response))
  return (await response.json()) as T
}

/**
 * Throws the error for a rejected stream request, tagging an exhausted daily budget as
 * {@link AiQuotaError} so the page can switch the composer without parsing the message.
 */
async function reject(response: Response): Promise<never> {
  const text = await response.text().catch(() => '')
  const message = messageFor(response.status, text)
  throw isQuotaRejection(text) ? new AiQuotaError(message) : new Error(message)
}

/** Maps a rejected request to a user-facing Chinese message. */
export async function readErrorMessage(response: Response): Promise<string> {
  const text = await response.text().catch(() => '')
  return messageFor(response.status, text)
}

/** Whether the JSON body marks an exhausted daily budget instead of a per-minute limit. */
function isQuotaRejection(text: string): boolean {
  try {
    return (JSON.parse(text) as { error?: string }).error === 'token_quota'
  } catch {
    return false
  }
}

function messageFor(status: number, text: string): string {
  if (status === 401) return '请先登录后再使用文档助手'
  // 429 and 503 both carry a specific server reason now (minute rate limit vs. daily token quota
  // vs. a disabled feature), so the body wins over the generic fallback.
  try {
    const parsed = JSON.parse(text) as { message?: string; detail?: string; error?: string }
    if (parsed.message) return parsed.message
    if (parsed.detail) return parsed.detail
    if (parsed.error === 'daily_quota' || parsed.error === 'token_quota') {
      return '今日 AI token 额度已用完，请明天再试'
    }
  } catch {
    // Not JSON; fall through to the status-based message.
  }
  if (status === 429) return '提问过于频繁，请稍后再试'
  if (status === 503) return '文档助手暂不可用：索引或模型尚未就绪'
  return text || `请求失败（HTTP ${status}）`
}
