/**
 * Anonymous plugin-center reads.
 *
 * A plugin is published **independently** of the web build:
 * - its metadata (name / tagline / links / 单页说明) lives in the business service, read anonymously
 *   from `/api/public/plugins`, so it can be maintained from the admin console without a redeploy;
 * - its downloads are jars on the unified resource service
 *   (`/api/public/resources?app=…&group=<key>`, see {@link fetchPluginResources});
 * - its documentation is a VitePress sub-site under the BFF's deploy-owned dir
 *   (`deploy/plugin-docs/<key>/`) served at `/plugin/<key>/**`, i.e. a plain same-origin URL.
 */
import { readJson } from '../envelope'
import type { Page } from '../types'
import type { PluginDetail, PluginListQuery, PluginResource, PluginSummary } from './types'

/** App namespace under which plugin downloads are published on the unified resource service. */
export const PLUGIN_RES_APP = 'nitrowater'

/** Where users propose / request more plugins (GitHub Issues, no mailbox exposed). */
export const SUBMIT_PLUGIN_URL = 'https://github.com/danburen/nitrowater/issues/new'

/** Serializes the public list filters; page/size are always explicit. */
function listQuery(query: PluginListQuery): string {
  const params = new URLSearchParams()
  const q = query.q?.trim()
  if (q) params.set('q', q)
  params.set('page', String(query.page ?? 0))
  params.set('size', String(query.size ?? 20))
  return params.toString()
}

/**
 * Lists one page of enabled plugins (cards) for the sidebar / overview / home teaser.
 *
 * @param query optional keyword / page filters
 * @returns one page of enabled plugins, server-ordered
 * @throws when the request fails
 */
export async function fetchPlugins(query: PluginListQuery = {}): Promise<Page<PluginSummary>> {
  const res = await fetch(`/api/public/plugins?${listQuery(query)}`, { cache: 'no-cache' })
  if (!res.ok) throw new Error(String(res.status))
  return readJson<Page<PluginSummary>>(res)
}

/**
 * Reads one plugin detail, including its single-page 说明.
 *
 * @param key plugin key (route segment)
 * @returns the plugin, or `null` when it does not exist or is disabled
 * @throws when the request fails for any other reason
 */
export async function fetchPlugin(key: string): Promise<PluginDetail | null> {
  const res = await fetch(`/api/public/plugins/${encodeURIComponent(key)}`, { cache: 'no-cache' })
  if (res.status === 404) return null
  if (!res.ok) throw new Error(String(res.status))
  return readJson<PluginDetail>(res)
}

/**
 * Lists one page of a plugin's published downloads (newest first) from the unified resource
 * service, via the BFF proxy. The plugin `key` doubles as the resource `group`; the `app` is
 * {@link PLUGIN_RES_APP}.
 *
 * @param key plugin key (also the resource group)
 * @param query optional keyword / page filters
 * @returns one page of published downloads, newest first
 * @throws when the request fails
 */
export async function fetchPluginResources(
  key: string,
  query: PluginListQuery = {},
): Promise<Page<PluginResource>> {
  const params = new URLSearchParams({
    app: PLUGIN_RES_APP,
    group: key,
    page: String(query.page ?? 0),
    size: String(query.size ?? 20),
  })
  const q = query.q?.trim()
  if (q) params.set('q', q)
  const res = await fetch(`/api/public/resources?${params.toString()}`, { cache: 'no-cache' })
  if (!res.ok) throw new Error(String(res.status))
  return readJson<Page<PluginResource>>(res)
}
