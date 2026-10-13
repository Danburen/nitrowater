import { apiFetch } from '../../auth/bff'
import type { ProblemSummary } from './api'

/**
 * Problem-set ("题单") API client for the playground.
 *
 * Reads of curated/official and public user sets go through the anonymous public endpoints; the
 * caller's own sets (including PRIVATE ones) go through the login-gated `/api/me/**` endpoints.
 * Mutating calls attach the CSRF header via {@link apiFetch}.
 */

/** Visibility of a user-owned set; curated (official) sets are always publicly visible. */
export type SetVisibility = 'PRIVATE' | 'PUBLIC'

/** A problem set ("题单") view (official or user-owned). */
export interface ProblemSetSummary {
  id: number
  /** Stable slug (official sets only); null for user-owned sets. */
  slug: string | null
  name: string
  nameEn: string | null
  description: string | null
  descriptionEn: string | null
  color: string | null
  sortOrder: number
  enabled: boolean
  problemCount: number
  /** Owner uid; null for curated/official sets. */
  ownerUid: number | null
  visibility: SetVisibility
}

/** Create/update payload for a user-owned set (owner + slug/sortOrder/enabled are system-managed). */
export interface MySetUpsert {
  name: string
  description?: string | null
  visibility?: SetVisibility | null
}

/** Unwrap a JSON response, turning non-2xx into a thrown Error carrying the status + body. */
async function toJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.text().catch(() => '')
    throw new Error(`HTTP ${response.status}${body ? `: ${body}` : ''}`)
  }
  return (await response.json()) as T
}

/** Await a mutating call that returns no body (asserting only success). */
async function toVoid(response: Response): Promise<void> {
  if (!response.ok) {
    const body = await response.text().catch(() => '')
    throw new Error(`HTTP ${response.status}${body ? `: ${body}` : ''}`)
  }
}

function jsonBody(payload: unknown, method = 'POST'): RequestInit {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }
}

/* ------------------------------ public reads ----------------------------- */

/** Official, enabled curated sets (anonymous). */
export async function listOfficialSets(): Promise<ProblemSetSummary[]> {
  return toJson<ProblemSetSummary[]>(await apiFetch('/api/public/problem-sets'))
}

/** Public user-owned sets ("题单广场", anonymous). */
export async function listSquareSets(): Promise<ProblemSetSummary[]> {
  return toJson<ProblemSetSummary[]>(await apiFetch('/api/public/problem-sets/square'))
}

/** One publicly visible set (official, or a user set with PUBLIC visibility). */
export async function getPublicSet(id: number | string): Promise<ProblemSetSummary> {
  return toJson<ProblemSetSummary>(await apiFetch(`/api/public/problem-sets/${id}`))
}

/** Enabled problems filed under a set, in the set's order (anonymous). */
export async function listSetProblems(id: number | string): Promise<ProblemSummary[]> {
  return toJson<ProblemSummary[]>(await apiFetch(`/api/public/problem-sets/${id}/problems`))
}

/* ------------------------------- my sets -------------------------------- */

/** The caller's own sets (newest first). */
export async function listMySets(): Promise<ProblemSetSummary[]> {
  return toJson<ProblemSetSummary[]>(await apiFetch('/api/me/problem-sets'))
}

/** One of the caller's own sets (works for PRIVATE sets too). */
export async function getMySet(id: number | string): Promise<ProblemSetSummary> {
  return toJson<ProblemSetSummary>(await apiFetch(`/api/me/problem-sets/${id}`))
}

export async function createMySet(payload: MySetUpsert): Promise<ProblemSetSummary> {
  return toJson<ProblemSetSummary>(await apiFetch('/api/me/problem-sets', jsonBody(payload)))
}

export async function updateMySet(id: number, payload: MySetUpsert): Promise<ProblemSetSummary> {
  return toJson<ProblemSetSummary>(
    await apiFetch(`/api/me/problem-sets/${id}`, jsonBody(payload, 'PUT')),
  )
}

export async function deleteMySet(id: number): Promise<void> {
  return toVoid(await apiFetch(`/api/me/problem-sets/${id}`, { method: 'DELETE' }))
}

/** Replace the set's ordered membership with exactly {@code problemIds}. */
export async function replaceMySetProblems(id: number, problemIds: number[]): Promise<ProblemSetSummary> {
  return toJson<ProblemSetSummary>(
    await apiFetch(`/api/me/problem-sets/${id}/problems`, jsonBody({ problemIds }, 'PUT')),
  )
}
