import { apiFetch } from '../../auth/bff'

/**
 * Admin API client — plugins / resources.
 *
 * Every call goes through the BFF proxy (`/api/**`); the resource server gates them all behind
 * `ROLE_ADMIN` (class-level `@PreAuthorize`), so a non-admin gets `403`. Mutating calls attach
 * the CSRF header via {@link apiFetch}.
 */

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
