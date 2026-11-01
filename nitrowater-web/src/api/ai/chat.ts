/**
 * User-facing documentation-assistant calls.
 *
 * The chat stream is a POST (it carries the question and the short history), so `EventSource`
 * cannot be used; the response body is parsed as SSE frames here instead. Requests go through the
 * BFF (`/api/ai/**`), which owns the session cookie and forwards the question with the server-held
 * access token.
 */
import { apiFetch } from '../../auth/bff'
import { getJson, isQuotaRejection, messageFor } from '../http'
import type {
  AiDailyUsage,
  AiSource,
  AiStatus,
  AiToolCall,
  AgentStreamHandlers,
  ChatTurn,
} from './types'

/** Raised when the daily token budget is exhausted (HTTP 429 with {@code token_quota}). */
export class AiQuotaError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'AiQuotaError'
  }
}

/** Reads the assistant status (login required). */
export async function fetchAiStatus(): Promise<AiStatus> {
  return getJson<AiStatus>('/api/ai/status')
}

/** Reads the authenticated user's UTC-day token usage. */
export async function fetchAiUsage(): Promise<AiDailyUsage> {
  return getJson<AiDailyUsage>('/api/ai/usage')
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
    } else if (event === 'moderation') {
      const parsed = JSON.parse(payload) as { text?: string }
      handlers.onModeration?.(parsed.text ?? '')
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

/**
 * Throws the error for a rejected stream request, tagging an exhausted daily budget as
 * {@link AiQuotaError} so the page can switch the composer without parsing the message.
 */
async function reject(response: Response): Promise<never> {
  const text = await response.text().catch(() => '')
  const message = messageFor(response.status, text)
  throw isQuotaRejection(text) ? new AiQuotaError(message) : new Error(message)
}
