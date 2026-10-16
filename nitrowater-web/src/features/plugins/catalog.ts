/**
 * Plugin catalog — the plugin center's data client.
 *
 * A plugin is published **independently** of the web build:
 * - its metadata (name / tagline / links / 单页说明) lives in the business service, read anonymously
 *   from `/api/public/plugins`, so it can be maintained from the admin console without a redeploy;
 * - its downloads are jars on the unified resource service
 *   (`/api/public/resources?app=…&group=<key>`, see {@link fetchPluginResources});
 * - its documentation is a VitePress sub-site under the BFF's deploy-owned dir
 *   (`deploy/plugin-docs/<key>/`) served at `/plugin/<key>/**`, i.e. a plain same-origin URL.
 */

/** App namespace under which plugin downloads are published on the unified resource service. */
export const PLUGIN_RES_APP = 'nitrowater'

/** A plugin card (the public listing) — one entry of the plugin center. */
export interface PluginSummary {
  /** Stable key: the route segment (`/plugins/<key>`), the resource group and the docs dir name. */
  key: string
  name: string
  tagline: string | null
  /** One-line blurb for cards / search. */
  summary: string | null
  /** 24x24 stroke SVG path data (sidebar / card icon). */
  icon: string | null
  tags: string[]
  /** Absolute URL of the plugin's docs sub-site (a separate site → full-page navigation). */
  docsUrl: string | null
  /** Plugin homepage, or `null` when it has none (the link is then hidden). */
  homepageUrl: string | null
  /** Source repository, or `null` when not public (the link is then hidden). */
  repoUrl: string | null
  /** Sponsor link, or `null` when the plugin has none (the link is then hidden). */
  sponsorUrl: string | null
}

/** A plugin detail — a card plus its bundled single-page 说明 (rendered by `marked`). */
export interface PluginDetail extends PluginSummary {
  /** Intro markdown, or `null` when the plugin has no single-page 说明 yet. */
  readme: string | null
}

/** One published download of a plugin, as returned by the resource service list endpoint. */
export interface PluginResource {
  uuid: string
  /** Original file name, or `null` when unknown. */
  name: string | null
  /** Version recorded in the resource's file info, or `null`. */
  version: string | null
  /** Size in bytes, or `null` when unknown. */
  sizeBytes: number | null
  /** Creation instant (ISO-8601), or `null`. */
  createdAt: string | null
  /** Same-origin URL that streams the file as a download attachment. */
  url: string
}

/**
 * Lists the enabled plugins (cards) for the sidebar / overview / home teaser.
 *
 * @returns the enabled plugins, server-ordered
 * @throws when the request fails
 */
export async function fetchPlugins(): Promise<PluginSummary[]> {
  const res = await fetch('/api/public/plugins', { cache: 'no-cache' })
  if (!res.ok) throw new Error(String(res.status))
  const data = (await res.json()) as PluginSummary[]
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
  return (await res.json()) as PluginDetail
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
  const data = (await res.json()) as PluginResource[]
  return Array.isArray(data) ? data : []
}

/** Where users propose / request more plugins (GitHub Issues, no mailbox exposed). */
export const SUBMIT_PLUGIN_URL = 'https://github.com/danburen/nitrowater/issues/new'
