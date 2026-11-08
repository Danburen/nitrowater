/**
 * `/api/admin/ai/**` calls: runtime settings, usage/quota console, moderation review, skill and
 * tool catalogs, and the index rebuild. All require ROLE_ADMIN (enforced server-side).
 */
import { apiFetch } from '../../auth/bff'
import { readJson } from '../envelope'
import { getJson, readErrorMessage } from '../http'
import type { Page } from '../types'
import type {
  AiAdminSettings,
  AiModerationQuery,
  AiModerationReport,
  AiReindexResult,
  AiSkill,
  AiSkillDetail,
  AiSkillQuery,
  AiSkillReq,
  AiTool,
  AiUsageQuery,
  AiUsageSummary,
  AiUserUsage,
} from './types'

function moderationQuery(query: AiModerationQuery): string {
  const params = new URLSearchParams()
  if (query.direction) params.set('direction', query.direction)
  if (query.action) params.set('action', query.action)
  const keyword = query.keyword?.trim()
  if (keyword) params.set('keyword', keyword)
  if (query.from) params.set('from', query.from)
  if (query.to) params.set('to', query.to)
  params.set('page', String(query.page ?? 0))
  params.set('size', String(query.size ?? 50))
  return params.toString()
}

function usageQuery(query: AiUsageQuery): string {
  const params = new URLSearchParams()
  const keyword = query.keyword?.trim()
  if (keyword) params.set('keyword', keyword)
  if (query.onlyExceeded) params.set('onlyExceeded', 'true')
  if (query.sort) params.set('sort', query.sort)
  params.set('page', String(query.page ?? 0))
  params.set('size', String(query.size ?? 20))
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

/** Admin: one page of users with recorded usage today, ordered by consumption. */
export async function fetchAdminAiUsage(query: AiUsageQuery = {}): Promise<Page<AiUserUsage>> {
  return getJson<Page<AiUserUsage>>(`/api/admin/ai/usage?${usageQuery(query)}`)
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

/** Admin: one page of the skill catalog (built-ins overlaid with managed rows). */
export async function fetchAdminSkills(query: AiSkillQuery = {}): Promise<Page<AiSkill>> {
  const params = new URLSearchParams()
  const keyword = query.keyword?.trim()
  if (keyword) params.set('keyword', keyword)
  params.set('page', String(query.page ?? 0))
  params.set('size', String(query.size ?? 20))
  return getJson<Page<AiSkill>>(`/api/admin/ai/skills?${params.toString()}`)
}

/** Admin: one skill with its body and provenance. */
export async function fetchAdminSkill(name: string): Promise<AiSkillDetail> {
  return getJson<AiSkillDetail>(`/api/admin/ai/skills/${encodeURIComponent(name)}`)
}

/** Admin: create a managed skill (its name may shadow a built-in). */
export async function createAdminSkill(req: AiSkillReq): Promise<AiSkillDetail> {
  const response = await apiFetch('/api/admin/ai/skills', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  })
  if (!response.ok) throw new Error(await readErrorMessage(response))
  return readJson<AiSkillDetail>(response)
}

/** Admin: update a managed skill (the name is immutable). */
export async function updateAdminSkill(name: string, req: AiSkillReq): Promise<AiSkillDetail> {
  const response = await apiFetch(`/api/admin/ai/skills/${encodeURIComponent(name)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  })
  if (!response.ok) throw new Error(await readErrorMessage(response))
  return readJson<AiSkillDetail>(response)
}

/** Admin: delete a managed skill, restoring a same-named built-in. */
export async function deleteAdminSkill(name: string): Promise<void> {
  const response = await apiFetch(`/api/admin/ai/skills/${encodeURIComponent(name)}`, {
    method: 'DELETE',
  })
  if (!response.ok) throw new Error(await readErrorMessage(response))
}

/** Admin: import a SKILL.md; a same-name managed row is replaced. */
export async function importAdminSkill(file: File): Promise<AiSkillDetail> {
  const form = new FormData()
  form.append('file', file)
  const response = await apiFetch('/api/admin/ai/skills/import', {
    method: 'POST',
    body: form,
  })
  if (!response.ok) throw new Error(await readErrorMessage(response))
  return readJson<AiSkillDetail>(response)
}

/**
 * Admin: download one skill as a SKILL.md (front matter + body). Built-ins export too, so a
 * built-in can be exported, tweaked and re-imported as an override.
 *
 * @param name skill name
 * @returns the markdown file as a blob
 */
export async function exportAdminSkill(name: string): Promise<Blob> {
  const response = await apiFetch(`/api/admin/ai/skills/${encodeURIComponent(name)}/export`)
  if (!response.ok) throw new Error(await readErrorMessage(response))
  return response.blob()
}

/** Admin: the tool catalog advertised to the agent. */
export async function fetchAdminTools(): Promise<AiTool[]> {
  return getJson<AiTool[]>('/api/admin/ai/tools')
}

/** Admin: show/hide one registered tool; the callback stays registered (hot toggle). */
export async function setAdminToolEnabled(name: string, enabled: boolean): Promise<AiTool[]> {
  const response = await apiFetch(
    `/api/admin/ai/tools/${encodeURIComponent(name)}?enabled=${enabled}`,
    { method: 'PUT' },
  )
  if (!response.ok) throw new Error(await readErrorMessage(response))
  return readJson<AiTool[]>(response)
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
