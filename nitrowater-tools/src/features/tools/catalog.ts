/**
 * Catalog for the standalone (offline / browser-local) tools site.
 *
 * <p>These tools never call the BFF: all processing happens in the browser. The code runner is
 * intentionally absent — it stays on the main site as a login-gated online feature.</p>
 *
 * <p>`icon` is a basename under `public/icons/*.svg`; glyphs are real files, never path data.</p>
 */
export type ToolCategory = 'server' | 'general'

export interface ToolDef {
  key: string
  title: string
  desc: string
  /** Basename of the SVG in public/icons (without extension). */
  icon: string
  to: string
  tags: string[]
  status: 'ready' | 'soon'
  category: ToolCategory
}

/** Group metadata, in display order (server first). */
export const toolCategories: ReadonlyArray<{
  key: ToolCategory
  label: string
  desc: string
}> = [
  {
    key: 'server',
    label: '我的世界服务',
    desc: '围绕开服配置、服务器图标与启动脚本的日常工具。',
  },
  {
    key: 'general',
    label: '通用功能',
    desc: '开发、内容处理与格式转换工具，全部本地完成。',
  },
]

export const tools: ToolDef[] = [
  {
    key: 'motd',
    title: 'MOTD 格式化预览',
    desc: '用 & / § 颜色代码编辑服务器 MOTD，实时预览两行效果，导出 legacy / server.properties / JSON。',
    icon: 'motd',
    to: '/tools/motd',
    tags: ['MOTD', '颜色代码'],
    status: 'ready',
    category: 'server',
  },
  {
    key: 'server-properties',
    title: 'server.properties 生成 / 校验',
    desc: '按分组生成完整的 Java 版 server.properties，支持预设、导入现有文件、保留未知键与实时校验。',
    icon: 'server-properties',
    to: '/tools/server-properties',
    tags: ['server.properties', 'Java 版', '校验'],
    status: 'ready',
    category: 'server',
  },
  {
    key: 'server-icon',
    title: 'server-icon 生成',
    desc: '上传图片，拖动 / 缩放裁切成 64×64 PNG 服务器图标，支持透明或纯色背景。',
    icon: 'server-icon',
    to: '/tools/server-icon',
    tags: ['64×64', 'PNG'],
    status: 'ready',
    category: 'server',
  },
  {
    key: 'start-script',
    title: '启动脚本生成',
    desc: '生成 start.bat / start.sh：内存、nogui、Aikar flags、自动重启和 Java 路径都可配置。',
    icon: 'start-script',
    to: '/tools/start-script',
    tags: ['start.bat', 'start.sh', 'Aikar'],
    status: 'ready',
    category: 'server',
  },
  {
    key: 'code-runner',
    title: '本地代码运行器',
    desc: '支持 Python / JavaScript / TypeScript / Lua。',
    icon: 'code-runner',
    to: '/tools/code-runner',
    tags: ['Python', 'JavaScript', 'Lua'],
    status: 'ready',
    category: 'general',
  },
  {
    key: 'zip',
    title: '压缩 / 解压',
    desc: '打包插件 jar / 资源包 / 整合包；解压服务端压缩包。',
    icon: 'zip',
    to: '/tools/zip',
    tags: ['插件 jar', '资源包'],
    status: 'ready',
    category: 'general',
  },
  {
    key: 'tree',
    title: '树形结构可视化',
    desc: '把插件配置 JSON 渲染为可交互树图，支持字段映射与多种布局，导出 PNG。',
    icon: 'tree',
    to: '/tools/tree',
    tags: ['插件配置', 'ECharts'],
    status: 'ready',
    category: 'general',
  },
  {
    key: 'list-tree',
    title: '列表树可视化',
    desc: '粘贴服务端目录树，渲染为可折叠列表，自动识别文件图标。',
    icon: 'list-tree',
    to: '/tools/list-tree',
    tags: ['服务端目录', '文件图标'],
    status: 'ready',
    category: 'general',
  },
  {
    key: 'java-to-ts',
    title: 'Java DTO 转 TypeScript',
    desc: '解析 Java DTO / VO，生成 TypeScript 接口；可选默认值与表单校验规则。',
    icon: 'java-to-ts',
    to: '/tools/java-to-ts',
    tags: ['类型映射', '泛型', 'rules'],
    status: 'ready',
    category: 'general',
  },
  {
    key: 'video-to-gif',
    title: 'MP4 转 GIF',
    desc: '基于 ffmpeg.wasm 两遍调色板量化，可配帧率 / 分辨率 / 时间范围。',
    icon: 'video-to-gif',
    to: '/tools/video-to-gif',
    tags: ['ffmpeg.wasm'],
    status: 'ready',
    category: 'general',
  },
  {
    key: 'markdown-to-word',
    title: 'Markdown 转 Word',
    desc: '将 Markdown 转为 .docx，支持标题 / 列表 / 引用 / 代码块 / 表格。',
    icon: 'markdown-to-word',
    to: '/tools/markdown-to-word',
    tags: ['Markdown', 'docx'],
    status: 'ready',
    category: 'general',
  },
]

export const readyTools = tools.filter((tool) => tool.status === 'ready')

/** Ready tools of one category, preserving catalog order. */
export function readyToolsIn(category: ToolCategory): ToolDef[] {
  return readyTools.filter((tool) => tool.category === category)
}
