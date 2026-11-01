/**
 * Shared JSON transport for the `api/` layer.
 *
 * `readJson` only knows the envelope; this module adds the HTTP/status fallbacks that produce a
 * user-facing Chinese message when the body carries none. It is used by the AI surfaces (chat,
 * admin, slash); domains with their own wording keep a local mapper
 * (see `conversations.ts`).
 */
import { apiFetch } from '../auth/bff'
import { ResponseCode } from './codes'
import { readJson } from './envelope'

/**
 * GETs a JSON endpoint and unwraps the response envelope.
 *
 * @param url endpoint path
 * @param init optional fetch options
 * @returns the unwrapped payload
 * @throws when the request is rejected (message from the envelope, else a status fallback)
 */
export async function getJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(url, init)
  if (!response.ok) throw new Error(await readErrorMessage(response))
  return readJson<T>(response)
}

/**
 * Maps a rejected response to a user-facing Chinese message.
 *
 * @param response rejected response
 * @returns the server's {@code message}/{@code detail}, or a status-based fallback
 */
export async function readErrorMessage(response: Response): Promise<string> {
  const text = await response.text().catch(() => '')
  return messageFor(response.status, text)
}

/** Whether the JSON body marks an exhausted daily budget instead of a per-minute limit. */
export function isQuotaRejection(text: string): boolean {
  try {
    return ((JSON.parse(text) as { code?: string; error?: string }).code ?? (JSON.parse(text) as { error?: string }).error) === ResponseCode.TokenQuota
  } catch {
    return false
  }
}

/**
 * Status + body → user-facing message. The envelope's {@code message} always wins; the status
 * fallbacks only apply when the body is empty or not JSON.
 */
export function messageFor(status: number, text: string): string {
  if (status === 401) return '请先登录后再使用文档助手'
  // 429 and 503 both carry a specific server reason now (minute rate limit vs. daily token quota
  // vs. a disabled feature), so the body wins over the generic fallback.
  try {
    const parsed = JSON.parse(text) as { message?: string; detail?: string; error?: string; code?: string }
    if (parsed.message) return parsed.message
    if (parsed.detail) return parsed.detail
    const reason = parsed.code ?? parsed.error
    if (reason === ResponseCode.DailyQuota || reason === ResponseCode.TokenQuota || reason === ResponseCode.GlobalQuotaExceeded) {
      return '今日 AI token 额度已用完，请明天再试'
    }
  } catch {
    // Not JSON; fall through to the status-based message.
  }
  if (status === 429) return '提问过于频繁，请稍后再试'
  if (status === 503) return '文档助手暂不可用：索引或模型尚未就绪'
  return text || `请求失败（HTTP ${status}）`
}
