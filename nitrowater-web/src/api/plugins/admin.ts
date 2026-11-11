/**
 * Plugin administration: metadata CRUD (`/api/admin/plugins`) and the jar publish/list/delete calls used
 * by the inline editor on `/plugins/:key`.
 *
 * Uploads go through the BFF proxy as a raw body (`Content-Length` is required by the server, which
 * streams it straight into storage). XHR is used instead of `fetch` solely to expose progress for
 * multi-megabyte jars; the CSRF header is attached the same way as {@link apiFetch}.
 */
import { apiFetch, csrfHeaders } from '../../auth/bff'
import { readJson, readVoid, unwrap } from '../envelope'
import type { Page } from '../types'
import type { AdminPluginJar, PluginAdmin, PluginAdminQuery, PluginUpsert } from './types'

function jsonBody(payload: unknown): RequestInit {
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }
}

/**
 * Plugin-center metadata (the `/plugins` pages). Uses the same `/api/**` namespace as the other
 * business endpoints (NOT `/api/admin/**`, which is reserved for the resource service); the server
 * gates the whole controller behind ROLE_ADMIN. `key` is immutable after creation — it doubles as
 * the docs path and the resource group — so the update payload's key is ignored server-side.
 */
/** Serializes the admin list filters; page/size are always explicit. */
function pluginQuery(query: PluginAdminQuery): string {
  const params = new URLSearchParams()
  const q = query.q?.trim()
  if (q) params.set('q', q)
  if (query.enabled !== undefined) params.set('enabled', String(query.enabled))
  if (query.sort) params.set('sort', query.sort)
  params.set('page', String(query.page ?? 0))
  params.set('size', String(query.size ?? 20))
  return params.toString()
}

/**
 * Paged plugin metadata for the admin console.
 *
 * @param query optional keyword / enabled / sort / page filters
 * @returns one page of rows, newest sort order first by default
 */
export async function listPlugins(query: PluginAdminQuery = {}): Promise<Page<PluginAdmin>> {
  return readJson<Page<PluginAdmin>>(await apiFetch(`/api/admin/plugins?${pluginQuery(query)}`))
}

/**
 * Finds one plugin's admin metadata by key.
 *
 * <p>There is no admin "get by key" endpoint; the list is filtered by key and paged until the exact
 * row shows up, so the editor still works when the catalog grows past one page.</p>
 */
export async function findPluginAdmin(key: string): Promise<PluginAdmin | null> {
  for (let page = 0; ; page++) {
    const result = await listPlugins({ q: key, page, size: 100 })
    const found = result.content.find((plugin) => plugin.key === key)
    if (found) return found
    if (page + 1 >= result.page.totalPages) return null
  }
}

export async function createPlugin(payload: PluginUpsert): Promise<PluginAdmin> {
  return readJson<PluginAdmin>(await apiFetch('/api/admin/plugins', jsonBody(payload)))
}

export async function updatePlugin(id: number, payload: PluginUpsert): Promise<PluginAdmin> {
  return readJson<PluginAdmin>(
    await apiFetch(`/api/admin/plugins/${id}`, { ...jsonBody(payload), method: 'PUT' }),
  )
}

export async function deletePlugin(id: number): Promise<void> {
  return readVoid(await apiFetch(`/api/admin/plugins/${id}`, { method: 'DELETE' }))
}

/** Lists one page of a plugin's published jars (admin view; the public list hides non-active rows). */
export async function listPluginJars(
  key: string,
  page = 0,
  size = 20,
): Promise<Page<AdminPluginJar>> {
  const res = await apiFetch(
    `/api/admin/plugin-resources/${encodeURIComponent(key)}?page=${page}&size=${size}`,
  )
  return readJson<Page<AdminPluginJar>>(res)
}

/**
 * Publishes (or overwrites) one jar. Re-uploading the same file name keeps the download URL stable.
 *
 * @param key plugin key (resource group)
 * @param file jar file selected by the admin
 * @param version logical version, or an empty string to omit it
 * @param onProgress receives a 0..1 ratio while the body uploads
 * @returns the activated artifact metadata
 */
export function uploadPluginJar(
  key: string,
  file: File,
  version: string,
  onProgress?: (ratio: number) => void,
): Promise<AdminPluginJar> {
  return new Promise((resolve, reject) => {
    const params = new URLSearchParams({ name: file.name })
    if (version) params.set('version', version)
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `/api/admin/plugin-resources/${encodeURIComponent(key)}?${params.toString()}`)
    xhr.withCredentials = true
    for (const [name, value] of Object.entries(csrfHeaders())) {
      xhr.setRequestHeader(name, value)
    }
    // The server derives the MIME from the extension; this header is only informational.
    xhr.setRequestHeader('Content-Type', 'application/java-archive')
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && event.total > 0) onProgress?.(event.loaded / event.total)
    }
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          // XHR already parsed to text, so the envelope is unwrapped here instead of via readJson.
          resolve(unwrap<AdminPluginJar>(JSON.parse(xhr.responseText)))
        } catch {
          reject(new Error('服务端返回了无法解析的响应'))
        }
        return
      }
      reject(new Error(`HTTP ${xhr.status}${xhr.responseText ? `: ${xhr.responseText}` : ''}`))
    }
    xhr.onerror = () => reject(new Error('网络错误，上传失败'))
    xhr.send(file)
  })
}

/** Deletes one jar after the server verifies it belongs to this plugin. */
export async function deletePluginJar(key: string, uuid: string): Promise<void> {
  return readVoid(
    await apiFetch(
      `/api/admin/plugin-resources/${encodeURIComponent(key)}/${encodeURIComponent(uuid)}`,
      { method: 'DELETE' },
    ),
  )
}

/**
 * Extracts the version from a jar file name (same rule as the operator script).
 *
 * @param name original file name, e.g. `VeloChatX-1.2.3.jar`
 * @returns the version, or `null` when the name carries none
 */
export function versionFromName(name: string): string | null {
  const match = name.match(/-(\d+\.\d+(?:\.\d+)?(?:-[0-9A-Za-z.]+)?)\.jar$/i)
  return match ? match[1] : null
}
