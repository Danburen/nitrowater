/**
 * Admin-only plugin client: metadata CRUD (re-exported from the console client) plus the jar
 * publish/list/delete calls used by the inline editor on `/plugins/:key`.
 *
 * Uploads go through the BFF proxy as a raw body (`Content-Length` is required by the server, which
 * streams it straight into storage). XHR is used instead of `fetch` solely to expose progress for
 * multi-megabyte jars; the CSRF header is attached the same way as {@link apiFetch}.
 */
import { apiFetch, csrfHeaders } from '../../auth/bff'
import type { PluginResource } from './catalog'

/** One artifact as returned by the admin plugin-resource list (a superset of the public view). */
export interface AdminPluginJar extends PluginResource {
  /** Lifecycle status (`ACTIVE` for published rows). */
  status?: string
  /** Access scope (`PUBLIC` for plugin jars). */
  visibility?: string
}

export {
  createPlugin,
  deletePlugin,
  listPlugins,
  updatePlugin,
  type PluginAdmin,
  type PluginUpsert,
} from '../admin/api'

/** Unwrap a JSON response, turning non-2xx into a thrown Error with the server body. */
async function toJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.text().catch(() => '')
    throw new Error(`HTTP ${response.status}${body ? `: ${body}` : ''}`)
  }
  return (await response.json()) as T
}

/** Lists a plugin's published jars (admin view; the public list hides non-active rows). */
export async function listPluginJars(key: string): Promise<AdminPluginJar[]> {
  const res = await apiFetch(`/api/admin/plugin-resources/${encodeURIComponent(key)}`)
  const data = await toJson<AdminPluginJar[]>(res)
  return Array.isArray(data) ? data : []
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
          resolve(JSON.parse(xhr.responseText) as AdminPluginJar)
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
  const res = await apiFetch(
    `/api/admin/plugin-resources/${encodeURIComponent(key)}/${encodeURIComponent(uuid)}`,
    { method: 'DELETE' },
  )
  if (!res.ok) {
    const body = await res.text().catch(() => '')
    throw new Error(`HTTP ${res.status}${body ? `: ${body}` : ''}`)
  }
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
