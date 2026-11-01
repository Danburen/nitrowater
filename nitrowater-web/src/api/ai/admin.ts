/**
 * `/api/admin/ai/**` calls: runtime settings, usage/quota console, moderation review, skill and
 * tool catalogs, and the index rebuild. All require ROLE_ADMIN (enforced server-side).
 */
import { apiFetch } from '../../auth/bff'
import { readJson } from '../envelope'
import { getJson, readErrorMessage } from '../http'
import type {
  AiAdminSettings,
  AiModerationQuery,
  AiModerationReport,
  AiReindexResult,
  AiSkill,
  AiTool,
  AiUsageSummary,
  AiUserUsage,
} from './types'

function moderationQuery(query: AiModerationQuery): string {
  const params = new URLSearchParams()
  if (query.direction) params.set('direction', query.direction)
  if (query.action) params.set('action', query.action)
  if (query.beforeId !== undefined) params.set('beforeId', String(query.beforeId))
  params.set('limit', String(query.limit ?? 50))
  return params.toString()
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
  return readJson<AiAdminSettings>(response)
}

/** Admin: platform-wide usage (today + per-day series) for the quota console. */
export async function fetchAdminAiUsageSummary(days = 31): Promise<AiUsageSummary> {
  return getJson<AiUsageSummary>(`/api/admin/ai/usage/summary?days=${days}`)
}

/** Admin: clear today's platform-wide counter, i.e. lift the breaker for the rest of the day. */
export async function resetAdminGlobalUsage(): Promise<number> {
  const response = await apiFetch('/api/admin/ai/usage/reset-global', { method: 'POST' })
  if (!response.ok) throw new Error(await readErrorMessage(response))
  return readJson<number>(response)
}

/** Admin: all users with recorded usage today, ordered by consumption. */
export async function fetchAdminAiUsage(): Promise<AiUserUsage[]> {
  return getJson<AiUserUsage[]>('/api/admin/ai/usage')
}

/** Admin: moderation posture, dictionary size and a page of hits. */
export async function fetchAdminModeration(query: AiModerationQuery = {}): Promise<AiModerationReport> {
  return getJson<AiModerationReport>(`/api/admin/ai/moderation?${moderationQuery(query)}`)
}

/**
 * Admin: reload the violation dictionary from its files, so a false positive can be corrected
 * without a redeploy. Returns the refreshed report.
 */
export async function reloadAdminModeration(query: AiModerationQuery = {}): Promise<AiModerationReport> {
  const response = await apiFetch(`/api/admin/ai/moderation/reload?${moderationQuery(query)}`, {
    method: 'POST',
  })
  if (!response.ok) throw new Error(await readErrorMessage(response))
  return readJson<AiModerationReport>(response)
}

/** Admin: resets selected users or all users when {@code uids} is empty. */
export async function resetAdminAiUsage(uids: readonly number[]): Promise<number> {
  const response = await apiFetch('/api/admin/ai/usage/reset', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ uids }),
  })
  if (!response.ok) throw new Error(await readErrorMessage(response))
  return readJson<number>(response)
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
export async function reindexDocs(): Promise<AiReindexResult> {
  const response = await apiFetch('/api/admin/ai/reindex', { method: 'POST' })
  if (!response.ok) throw new Error(await readErrorMessage(response))
  return readJson<AiReindexResult>(response)
}
