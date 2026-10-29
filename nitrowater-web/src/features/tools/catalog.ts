/**
 * Tool catalog — shared by the portal home (工具箱 section) and the tools index.
 * Icons are 24x24 stroke SVG path data.
 *
 * Tools are split into two groups: `server` (Minecraft plugin / server operations) and
 * `general` (everything else). Server tools are always listed first; both groups stay available.
 */
export type ToolCategory = 'server' | 'general'

export interface ToolDef {
  key: string
  title: string
  desc: string
  icon: string
  to?: string
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
    desc: '开发、内容处理与服务器辅助工具。',
  },
]

export const tools: ToolDef[] = [
  {
    key: 'motd',
    title: 'MOTD 格式化预览',
    desc: '用 & / § 颜色代码编辑服务器 MOTD，实时预览两行效果，导出 legacy / server.properties / JSON。',
    icon: 'M4 5h16v11H9l-5 4zM7 9h10M7 12h7',
    to: '/tools/motd',
    tags: ['MOTD', '颜色代码', '本地'],
    status: 'ready',
    category: 'server',
  },
  {
    key: 'server-properties',
    title: 'server.properties 生成 / 校验',
    desc: '按分组生成完整的 Java 版 server.properties，支持预设、导入现有文件、保留未知键与实时校验。',
    icon: 'M6 3h9l5 5v13H6zM15 3v5h5M9 12h6M9 16h6',
    to: '/tools/server-properties',
    tags: ['server.properties', 'Java 版', '校验'],
    status: 'ready',
    category: 'server',
  },
  {
    key: 'server-icon',
    title: 'server-icon 生成',
    desc: '上传图片，拖动 / 缩放裁切成 64×64 PNG 服务器图标，支持透明或纯色背景。',
    icon: 'M4 5h16v14H4zM8.5 9.5a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3M4 16l5-4 4 3 3-2 4 3',
    to: '/tools/server-icon',
    tags: ['64×64', 'PNG', '本地'],
    status: 'ready',
    category: 'server',
  },
  {
    key: 'start-script',
    title: '启动脚本生成',
    desc: '生成 start.bat / start.sh：内存、nogui、Aikar flags、自动重启和 Java 路径都可配置。',
    icon: 'M4 5h16v14H4zM7 9l3 2.5L7 14M12 14h5',
    to: '/tools/start-script',
    tags: ['start.bat', 'start.sh', 'Aikar'],
    status: 'ready',
    category: 'server',
  },
  {
    key: 'code-runner',
    title: '在线代码运行',
    desc: '验证插件逻辑片段：登录后可在服务端沙箱运行 Java / C / C++ / Python / JS / TS / Lua；匿名仅浏览器本地运行 Python / JS / TS / Lua。',
    icon: 'M4 5h16v14H4zM7 9l3 3-3 3M13 15h4',
    to: '/tools/code-runner',
    tags: ['Java', 'Python', 'JS/TS', 'Lua'],
    status: 'ready',
    category: 'general',
  },
  {
    key: 'zip',
    title: '压缩 / 解压',
    desc: '打包插件 jar / 资源包 / 整合包；解压服务端压缩包，全部本地完成。',
    icon: 'M12 3l8 4.5v9L12 21l-8-4.5v-9zM12 12v9M12 12L4 7.5M12 12l8-4.5',
    to: '/tools/zip',
    tags: ['插件 jar', '资源包', '本地'],
    status: 'ready',
    category: 'general',
  },
  {
    key: 'tree',
    title: '树形结构可视化',
    desc: '把插件配置 JSON 渲染为可交互树图，支持字段映射与多种布局，导出 PNG。',
    icon: 'M9 4h6v4H9zM12 8v4M6 12h12M6 12v4M18 12v4M3 16h6v4H3zM15 16h6v4h-6z',
    to: '/tools/tree',
    tags: ['插件配置', 'ECharts'],
    status: 'ready',
    category: 'general',
  },
  {
    key: 'list-tree',
    title: '列表树可视化',
    desc: '粘贴服务端目录树，渲染为可折叠列表，自动识别文件图标。',
    icon: 'M4 4v16M8 7h12M8 12h12M8 17h12',
    to: '/tools/list-tree',
    tags: ['服务端目录', '文件图标'],
    status: 'ready',
    category: 'general',
  },
  {
    key: 'java-to-ts',
    title: 'Java DTO 转 TypeScript',
    desc: '解析 Java DTO / VO，生成 TypeScript 接口；可选默认值与表单校验规则。',
    icon: 'M4 8h13M14 5l3 3-3 3M20 16H7M10 13l-3 3 3 3',
    to: '/tools/java-to-ts',
    tags: ['类型映射', '泛型', 'rules'],
    status: 'ready',
    category: 'general',
  },
  {
    key: 'video-to-gif',
    title: 'MP4 转 GIF',
    desc: '基于 ffmpeg.wasm 两遍调色板量化，可配帧率 / 分辨率 / 时间范围，本地完成。',
    icon: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM10 8.5l5 3.5-5 3.5z',
    to: '/tools/video-to-gif',
    tags: ['ffmpeg.wasm', '本地'],
    status: 'ready',
    category: 'general',
  },
  {
    key: 'markdown-to-word',
    title: 'Markdown 转 Word',
    desc: '将 Markdown 转为 .docx，支持标题 / 列表 / 引用 / 代码块 / 表格，本地完成。',
    icon: 'M6 3h9l5 5v13H6zM15 3v5h5M9 12h6M9 16h6',
    to: '/tools/markdown-to-word',
    tags: ['Markdown', 'docx', '本地'],
    status: 'ready',
    category: 'general',
  },
]

export const readyTools = tools.filter(
  (tool): tool is ToolDef & { to: string } => tool.status === 'ready' && !!tool.to,
)

/** Ready tools of one category, preserving catalog order. */
export function readyToolsIn(category: ToolCategory): Array<ToolDef & { to: string }> {
  return readyTools.filter((tool) => tool.category === category)
}
