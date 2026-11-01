import { apiFetch } from '../auth/bff'
import { readJson, readVoid } from './envelope'
import type { Page } from './types'

/**
 * Admin API client for the resource console (`/api/admin/resources`).
 *
 * Every call goes through the BFF proxy (`/api/**`); the resource server gates them all behind
 * `ROLE_ADMIN` (class-level `@PreAuthorize`), so a non-admin gets `403`. Mutating calls attach
 * the CSRF header via {@link apiFetch}.
 */

/* ------------------------------- Resources ------------------------------- */
/*
 * Admin resource management. The resource server exposes these under `/api/admin/resources`
 * (ROLE_ADMIN / ROLE_SUPER_ADMIN); the BFF proxy forwards that prefix. The wire contract is locked
 * by .ai/testkit/smoke-admin-resource.ps1: list and detail render the shared `ResourceMetaResp`
 * view (12 fields below) and the list envelope is `ApiResponse<Page<ResourceMetaResp>>`:
 * i.e. `data.content` + `data.page{size,number,totalElements,totalPages}`, which `adminListResources`
 * flattens back to `items/total/page/size/totalPages`. A non-super admin only ever receives rows
 * they own, so every listed row is deletable by the viewer; the server re-checks on DELETE.
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
  // The server answers `ApiResponse<Page<ResourceMetaResp>>`: `data.content` + `data.page{size,number,
  // totalElements,totalPages}` (VIA_DTO). Flattened back into the shape the console components use
  // so the paging UI stays untouched by the wire change.
  // Tolerant on purpose: accept the VIA_DTO `PagedModel` shape (`content` + `page{...}`) and the raw
  // `PageImpl` shape (`content` + flat `number/totalElements/...`) so a serialization-mode change
  // cannot blank out the console.
  const raw = await readJson<Partial<Page<AdminResourceItem>> & {
    number?: number
    size?: number
    totalElements?: number
    totalPages?: number
    items?: AdminResourceItem[]
  }>(await apiFetch(`/api/admin/resources?${sp.toString()}`))
  const items = raw.content ?? raw.items ?? []
  return {
    items,
    total: raw.page?.totalElements ?? raw.totalElements ?? items.length,
    page: raw.page?.number ?? raw.number ?? 0,
    size: raw.page?.size ?? raw.size ?? items.length,
    totalPages: raw.page?.totalPages ?? raw.totalPages ?? 1,
  }
}

export async function adminGetResource(uuid: string): Promise<AdminResourceDetail> {
  return readJson<AdminResourceDetail>(await apiFetch(`/api/admin/resources/${uuid}`))
}

export async function adminDeleteResource(uuid: string): Promise<void> {
  return readVoid(await apiFetch(`/api/admin/resources/${uuid}`, { method: 'DELETE' }))
}

/**
 * Fetches the console filter dictionaries (enum options + the app namespaces present in the data).
 *
 * @returns the dictionaries backing the filter selects
 */
export async function adminGetResourceDictionaries(): Promise<AdminResourceDictionaries> {
  return readJson<AdminResourceDictionaries>(await apiFetch('/api/admin/resources/dictionaries'))
}