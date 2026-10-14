/**
 * Plugin catalog — drives the plugin center (左侧插件列表 + 右侧单页说明).
 *
 * Each plugin is published **independently** of the web build: its documentation is a VitePress
 * sub-site and its downloads are mirrored jars, both living under the BFF's deploy-owned dir
 * (`deploy/plugin-docs/<key>/`, see deploy/plugin-docs/README.md). The BFF serves that tree at
 * `/plugin/<key>/**`, so `docs` / `res` below are plain same-origin absolute URLs.
 *
 * A plugin's single-page 说明 is bundled markdown (raw-imported) rendered by `marked`.
 */
import velochatxMd from './descriptions/velochatx.md?raw'

export interface PluginDef {
  key: string
  name: string
  tagline: string
  /** One-line blurb for cards / search. */
  desc: string
  /** 24x24 stroke SVG path data (sidebar icon). */
  icon: string
  tags: string[]
  /** Absolute URL of the plugin's docs sub-site (a separate site → full-page navigation). */
  docs: string
  /** Base URL of the plugin's mirrored downloads; holds `manifest.json` + the jars. */
  res: string
  /** Source repository, or `null` when not public. */
  repo: string | null
  /** Bundled single-page markdown (the plugin's 说明). */
  markdown: string
}

export const plugins: PluginDef[] = [
  {
    key: 'velochatx',
    name: 'VeloChatX',
    tagline: 'Velocity 跨服聊天插件',
    desc: '为 Velocity 代理提供跨服聊天：自定义聊天格式、跨服 / 群组广播、定时广播、@提及、TabList 显示替换、违禁词过滤等；内置 i18n 与自动更新。',
    icon: 'M4 5h16v11H8l-4 4zM8 9h8M8 12h5',
    tags: ['Velocity', '跨服聊天', 'MiniMessage', 'LuckPerms'],
    docs: '/plugin/velochatx/docs/',
    res: '/plugin/velochatx/res/',
    repo: 'https://github.com/Danburen/VeloChatX',
    markdown: velochatxMd,
  },
]

export function findPlugin(key: string): PluginDef | undefined {
  return plugins.find((p) => p.key === key)
}

/** Where users propose / request more plugins (GitHub Issues, no mailbox exposed). */
export const SUBMIT_PLUGIN_URL = 'https://github.com/danburen/nitrowater/issues/new'
