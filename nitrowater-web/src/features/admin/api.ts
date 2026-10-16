import { apiFetch } from '../../auth/bff'

/**
 * Admin API client — problems / tags / problem sets / editorials.
 *
 * Every call goes through the BFF proxy (`/api/**`); the resource server gates them all behind
 * `ROLE_ADMIN` (class-level `@PreAuthorize`), so a non-admin gets `403`. Mutating calls attach
 * the CSRF header via {@link apiFetch}.
 */

export interface Tag {
  id: number
  slug: string
  name: string
  nameEn: string | null
  kind: string
  color: string | null
  enabled: boolean
}

export interface ProblemSet {
  id: number
  slug: string
  name: string
  nameEn: string | null
  description: string | null
  descriptionEn: string | null
  color: string | null
  sortOrder: number
  enabled: boolean
  problemCount: number
}

export interface ProblemSetBrief {
  id: number
  slug: string
  name: string
  nameEn: string | null
  color: string | null
}

export interface ProblemSummary {
  id: number
  slug: string
  title: string
  titleEn: string | null
  difficulty: string
  tags: Tag[]
  sets: ProblemSetBrief[]
  rewardPoints: number
  timeLimitMs: number
  memoryLimitKb: number
  enabled: boolean
}

export interface TestCase {
  idx: number
  stdin: string | null
  expectedOutput: string | null
  isSample: boolean
  score: number
}

export interface AdminProblemDetail {
  id: number
  slug: string
  title: string
  titleEn: string | null
  difficulty: string
  tags: Tag[]
  sets: ProblemSetBrief[]
  description: string
  descriptionEn: string | null
  ioMode: string
  templates: Record<string, string> | null
  judgeConfig: Record<string, unknown> | null
  timeLimitMs: number
  memoryLimitKb: number
  rewardPoints: number
  enabled: boolean
  testCases: TestCase[]
}

export interface EditorialSummary {
  id: number
  problemId: number
  authorType: string
  title: string
  language: string | null
  status: string
  pinned: boolean
  likeCount: number
  viewCount: number
  publishedAt: string | null
  createdAt: string
}

export interface EditorialDetail {
  id: number
  problemId: number
  authorUid: number | null
  authorType: string
  title: string
  content: string
  language: string | null
  status: string
  pinned: boolean
  likeCount: number
  viewCount: number
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface ProblemUpsert {
  slug?: string | null
  title: string
  titleEn?: string | null
  difficulty?: string | null
  tagIds?: number[]
  setIds?: number[]
  description: string
  descriptionEn?: string | null
  ioMode?: string | null
  templates?: Record<string, string> | null
  judgeConfig?: Record<string, unknown> | null
  timeLimitMs?: number | null
  memoryLimitKb?: number | null
  rewardPoints?: number | null
  enabled?: boolean | null
}

export interface TestCaseUpsert {
  idx: number
  stdin?: string | null
  expectedOutput?: string | null
  isSample?: boolean
  score?: number
}

export interface TagUpsert {
  slug?: string | null
  name: string
  nameEn?: string | null
  kind?: string | null
  color?: string | null
  enabled?: boolean | null
}

export interface ProblemSetUpsert {
  slug?: string | null
  name: string
  nameEn?: string | null
  description?: string | null
  descriptionEn?: string | null
  color?: string | null
  sortOrder?: number | null
  enabled?: boolean | null
}

export interface EditorialUpsert {
  title: string
  content: string
  language?: string | null
  status?: string | null
  pinned?: boolean | null
}

/** Unwrap a JSON response, turning non-2xx into a thrown Error with the server body. */
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

function jsonBody(payload: unknown): RequestInit {
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }
}

/* ------------------------------- Problems ------------------------------- */

export async function listProblems(): Promise<ProblemSummary[]> {
  return toJson<ProblemSummary[]>(await apiFetch('/api/problems'))
}

export async function getProblem(id: number | string): Promise<AdminProblemDetail> {
  return toJson<AdminProblemDetail>(await apiFetch(`/api/problems/${id}`))
}

export async function createProblem(payload: ProblemUpsert): Promise<ProblemSummary> {
  return toJson<ProblemSummary>(await apiFetch('/api/problems', jsonBody(payload)))
}

export async function updateProblem(id: number, payload: ProblemUpsert): Promise<ProblemSummary> {
  return toJson<ProblemSummary>(
    await apiFetch(`/api/problems/${id}`, { ...jsonBody(payload), method: 'PUT' }),
  )
}

export async function replaceTestCases(id: number, cases: TestCaseUpsert[]): Promise<void> {
  return toVoid(await apiFetch(`/api/problems/${id}/testcases`, jsonBody(cases)))
}

/* -------------------------------- Tags ---------------------------------- */

export async function listTags(): Promise<Tag[]> {
  return toJson<Tag[]>(await apiFetch('/api/tags'))
}

export async function createTag(payload: TagUpsert): Promise<Tag> {
  return toJson<Tag>(await apiFetch('/api/tags', jsonBody(payload)))
}

export async function updateTag(id: number, payload: TagUpsert): Promise<Tag> {
  return toJson<Tag>(await apiFetch(`/api/tags/${id}`, { ...jsonBody(payload), method: 'PUT' }))
}

export async function deleteTag(id: number): Promise<void> {
  return toVoid(await apiFetch(`/api/tags/${id}`, { method: 'DELETE' }))
}

/* ------------------------------- Plugins -------------------------------- */
/*
 * Plugin-center metadata (the `/plugins` pages). Uses the same `/api/**` namespace as the other
 * business endpoints (NOT `/api/admin/**`, which is reserved for the resource service); the server
 * gates the whole controller behind ROLE_ADMIN. `key` is immutable after creation — it doubles as
 * the docs path and the resource group — so the update payload's key is ignored server-side.
 */

export interface PluginAdmin {
  id: number
  key: string
  name: string
  tagline: string | null
  summary: string | null
  icon: string | null
  tags: string[]
  docsUrl: string | null
  homepageUrl: string | null
  repoUrl: string | null
  sponsorUrl: string | null
  readme: string | null
  sortOrder: number
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface PluginUpsert {
  /** Stable key — required on create (`^[a-z0-9][a-z0-9-]*$`), ignored on update. */
  key?: string | null
  name: string
  tagline?: string | null
  summary?: string | null
  icon?: string | null
  tags?: string[]
  docsUrl?: string | null
  homepageUrl?: string | null
  repoUrl?: string | null
  sponsorUrl?: string | null
  readme?: string | null
  sortOrder?: number | null
  enabled?: boolean | null
}

export async function listPlugins(): Promise<PluginAdmin[]> {
  return toJson<PluginAdmin[]>(await apiFetch('/api/plugins'))
}

export async function createPlugin(payload: PluginUpsert): Promise<PluginAdmin> {
  return toJson<PluginAdmin>(await apiFetch('/api/plugins', jsonBody(payload)))
}

export async function updatePlugin(id: number, payload: PluginUpsert): Promise<PluginAdmin> {
  return toJson<PluginAdmin>(
    await apiFetch(`/api/plugins/${id}`, { ...jsonBody(payload), method: 'PUT' }),
  )
}

export async function deletePlugin(id: number): Promise<void> {
  return toVoid(await apiFetch(`/api/plugins/${id}`, { method: 'DELETE' }))
}

/* ----------------------------- Problem sets ----------------------------- */

export async function listSets(): Promise<ProblemSet[]> {
  return toJson<ProblemSet[]>(await apiFetch('/api/problem-sets'))
}

export async function createSet(payload: ProblemSetUpsert): Promise<ProblemSet> {
  return toJson<ProblemSet>(await apiFetch('/api/problem-sets', jsonBody(payload)))
}

export async function updateSet(id: number, payload: ProblemSetUpsert): Promise<ProblemSet> {
  return toJson<ProblemSet>(
    await apiFetch(`/api/problem-sets/${id}`, { ...jsonBody(payload), method: 'PUT' }),
  )
}

export async function deleteSet(id: number): Promise<void> {
  return toVoid(await apiFetch(`/api/problem-sets/${id}`, { method: 'DELETE' }))
}

/* ------------------------------ Editorials ------------------------------ */

export async function listEditorials(problemId: number): Promise<EditorialSummary[]> {
  return toJson<EditorialSummary[]>(await apiFetch(`/api/problems/${problemId}/editorials`))
}

export async function createEditorial(
  problemId: number,
  payload: EditorialUpsert,
): Promise<EditorialDetail> {
  return toJson<EditorialDetail>(
    await apiFetch(`/api/problems/${problemId}/editorials`, jsonBody(payload)),
  )
}

export async function updateEditorial(id: number, payload: EditorialUpsert): Promise<EditorialDetail> {
  return toJson<EditorialDetail>(
    await apiFetch(`/api/editorials/${id}`, { ...jsonBody(payload), method: 'PUT' }),
  )
}

export async function deleteEditorial(id: number): Promise<void> {
  return toVoid(await apiFetch(`/api/editorials/${id}`, { method: 'DELETE' }))
}

export async function publishEditorial(id: number): Promise<EditorialDetail> {
  return toJson<EditorialDetail>(await apiFetch(`/api/editorials/${id}/publish`, { method: 'POST' }))
}

export async function unpublishEditorial(id: number): Promise<EditorialDetail> {
  return toJson<EditorialDetail>(await apiFetch(`/api/editorials/${id}/unpublish`, { method: 'POST' }))
}

export async function pinEditorial(id: number): Promise<EditorialDetail> {
  return toJson<EditorialDetail>(await apiFetch(`/api/editorials/${id}/pin`, { method: 'POST' }))
}

export async function unpinEditorial(id: number): Promise<EditorialDetail> {
  return toJson<EditorialDetail>(await apiFetch(`/api/editorials/${id}/unpin`, { method: 'POST' }))
}

/* ------------------------------- Resources ------------------------------- */
/*
 * Admin resource management. The resource server exposes these under `/api/admin/resources`
 * (ROLE_ADMIN / ROLE_SUPER_ADMIN); the BFF proxy forwards that prefix. The wire contract is locked
 * by .ai/testkit/smoke-admin-resource.ps1: list and detail render the shared `ResourceMetaResp`
 * view (12 fields below) and the list envelope is `PageResp { items, total, page, size, totalPages }`
 * — NOT a Spring `Page` (`content`/`totalElements`/`number`). A non-super admin only ever receives
 * rows they own, so every listed row is deletable by the viewer; the server re-checks on DELETE.
 */

export interface AdminResourceItem {
  uuid: string
  app: string
  groupKey: string | null
  name: string | null
  resourceType: string
  mimeType: string | null
  sizeBytes: number | null
  visibility: string
  status: string
  version: string | null
  createdAt: string | null
  url: string | null
}

export interface AdminResourcePage {
  items: AdminResourceItem[]
  total: number
  page: number
  size: number
  totalPages: number
}

/** One option of a server-provided filter dictionary: the code sent back plus its display label. */
export interface DictionaryOption {
  value: string
  label: string
}

/**
 * Filter dictionaries for the console, shipped by the server so the selects are driven by the domain
 * enums instead of a hardcoded front-end copy (which previously drifted from the real codes).
 * `app` is data-driven: the namespaces that actually exist in the resource table.
 */
export interface AdminResourceDictionaries {
  app: DictionaryOption[]
  sourceType: DictionaryOption[]
  status: DictionaryOption[]
}

/** `GET /{uuid}` renders the same `ResourceMetaResp` view as a list row. */
export type AdminResourceDetail = AdminResourceItem

export async function adminListResources(params: {
  q?: string
  sourceType?: string
  status?: string
  app?: string
  ownerUid?: number
  group?: string
  page?: number
  size?: number
} = {}): Promise<AdminResourcePage> {
  const sp = new URLSearchParams()
  if (params.q) sp.set('q', params.q)
  if (params.sourceType) sp.set('sourceType', params.sourceType)
  if (params.status) sp.set('status', params.status)
  if (params.app) sp.set('app', params.app)
  if (params.ownerUid !== undefined && params.ownerUid !== null) sp.set('ownerUid', String(params.ownerUid))
  if (params.group) sp.set('group', params.group)
  sp.set('page', String(params.page ?? 0))
  sp.set('size', String(params.size ?? 20))
  return toJson<AdminResourcePage>(await apiFetch(`/api/admin/resources?${sp.toString()}`))
}

export async function adminGetResource(uuid: string): Promise<AdminResourceDetail> {
  return toJson<AdminResourceDetail>(await apiFetch(`/api/admin/resources/${uuid}`))
}

export async function adminDeleteResource(uuid: string): Promise<void> {
  return toVoid(await apiFetch(`/api/admin/resources/${uuid}`, { method: 'DELETE' }))
}

/**
 * Fetches the console filter dictionaries (enum options + the app namespaces present in the data).
 *
 * @returns the dictionaries backing the filter selects
 */
export async function adminGetResourceDictionaries(): Promise<AdminResourceDictionaries> {
  return toJson<AdminResourceDictionaries>(await apiFetch('/api/admin/resources/dictionaries'))
}
