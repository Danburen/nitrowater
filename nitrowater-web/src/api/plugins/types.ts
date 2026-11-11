/**
 * Wire types of the plugin center.
 *
 * Declarations only (no runtime code), so every other module of the domain can `import type` from
 * here without creating a cycle.
 */

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

/** One artifact as returned by the admin plugin-resource list (a superset of the public view). */
export interface AdminPluginJar extends PluginResource {
  /** Lifecycle status (`ACTIVE` for published rows). */
  status?: string
  /** Access scope (`PUBLIC` for plugin jars). */
  visibility?: string
}

/** Plugin metadata as stored by the business service (admin view, all fields materialised). */
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

/** Create/update payload for plugin metadata. */
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

/** Ordering accepted by the admin plugin list. */
export type PluginAdminSort = 'default' | 'updated' | 'name'

/** Filters for the admin plugin list (`GET /api/admin/plugins`). */
export interface PluginAdminQuery {
  /** Case-insensitive key / name / tagline / summary substring. */
  q?: string
  /** Enabled-state filter; omitted means both. */
  enabled?: boolean
  /** Defaults to server-side `default` (sort order). */
  sort?: PluginAdminSort
  /** Zero-based page index. */
  page?: number
  /** Page size. */
  size?: number
}

/** Filters for the public plugin list (`GET /api/public/plugins`). */
export interface PluginListQuery {
  /** Case-insensitive key / name / tagline / summary substring. */
  q?: string
  /** Zero-based page index. */
  page?: number
  /** Page size. */
  size?: number
}
