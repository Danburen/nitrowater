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
import type { PluginDetail, PluginResource, PluginSummary } from './types'

/** App namespace under which plugin downloads are published on the unified resource service. */
export const PLUGIN_RES_APP = 'nitrowater'

/** Where users propose / request more plugins (GitHub Issues, no mailbox exposed). */
export const SUBMIT_PLUGIN_URL = 'https://github.com/danburen/nitrowater/issues/new'

/**
 * Lists the enabled plugins (cards) for the sidebar / overview / home teaser.
 *
 * @returns the enabled plugins, server-ordered
 * @throws when the request fails
 */
export async function fetchPlugins(): Promise<PluginSummary[]> {
  const res = await fetch('/api/public/plugins', { cache: 'no-cache' })
  if (!res.ok) throw new Error(String(res.status))
  const data = await readJson<PluginSummary[]>(res)
  return Array.isArray(data) ? data : []
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
 * Lists a plugin's published downloads (newest first) from the unified resource service, via the
 * BFF proxy. The plugin `key` doubles as the resource `group`; the `app` is {@link PLUGIN_RES_APP}.
 *
 * @param key plugin key (also the resource group)
 * @returns the published downloads, newest first
 * @throws when the request fails
 */
export async function fetchPluginResources(key: string): Promise<PluginResource[]> {
  const res = await fetch(
    `/api/public/resources?app=${encodeURIComponent(PLUGIN_RES_APP)}&group=${encodeURIComponent(key)}`,
    { cache: 'no-cache' },
  )
  if (!res.ok) throw new Error(String(res.status))
  const data = await readJson<PluginResource[]>(res)
  return Array.isArray(data) ? data : []
}
