/**
 * Tool catalog — shared by the portal home (工具箱 section) and the tools index.
 * Icons are 24x24 stroke SVG path data.
 */
export interface ToolDef {
  key: string
  title: string
  desc: string
  icon: string
  to?: string
  tags: string[]
  status: 'ready' | 'soon'
}

export const tools: ToolDef[] = [
  {
    key: 'java-to-ts',
    title: 'Java DTO 转 TypeScript',
    desc: '解析 Java DTO / VO，生成 TypeScript 接口；可选默认值与表单校验规则。',
    icon: 'M4 8h13M14 5l3 3-3 3M20 16H7M10 13l-3 3 3 3',
    to: '/tools/java-to-ts',
    tags: ['类型映射', '泛型', 'rules'],
    status: 'ready',
  },
  {
    key: 'zip',
    title: '压缩 / 解压',
    desc: 'ZIP 打包下载；ZIP / RAR / 7z / TAR / GZ / XZ / BZ2 解压，全部本地完成。',
    icon: 'M12 3l8 4.5v9L12 21l-8-4.5v-9zM12 12v9M12 12L4 7.5M12 12l8-4.5',
    to: '/tools/zip',
    tags: ['ZIP', 'RAR/7z', '本地'],
    status: 'ready',
  },
  {
    key: 'tree',
    title: '树形结构可视化',
    desc: 'JSON 渲染为可交互树图，支持字段映射与多种布局，导出 PNG。',
    icon: 'M9 4h6v4H9zM12 8v4M6 12h12M6 12v4M18 12v4M3 16h6v4H3zM15 16h6v4h-6z',
    to: '/tools/tree',
    tags: ['ECharts', '布局'],
    status: 'ready',
  },
  {
    key: 'list-tree',
    title: '列表树可视化',
    desc: '缩进树文本渲染为可折叠列表，自动识别文件图标。',
    icon: 'M4 4v16M8 7h12M8 12h12M8 17h12',
    to: '/tools/list-tree',
    tags: ['D3', '文件图标'],
    status: 'ready',
  },
  {
    key: 'code-runner',
    title: '在线代码运行',
    desc: '浏览器内本地运行 Python（WebAssembly），无需上传、匿名可用；更多语言陆续接入。',
    icon: 'M4 5h16v14H4zM7 9l3 3-3 3M13 15h4',
    to: '/tools/code-runner',
    tags: ['Python', 'Pyodide', '本地'],
    status: 'ready',
  },
  {
    key: 'video-to-gif',
    title: 'MP4 转 GIF',
    desc: 'ffmpeg.wasm 两遍调色板量化，可配帧率 / 分辨率 / 时间范围。',
    icon: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM10 8.5l5 3.5-5 3.5z',
    tags: ['ffmpeg.wasm'],
    status: 'soon',
  },
  {
    key: 'playground',
    title: '编程演练场',
    desc: 'ACM 模式在线判题，题目 / 提交 / 评测（需登录）。',
    icon: 'M6 3h9l5 5v13H6zM15 3v5h5M9 15l2 2 4-4',
    tags: ['ACM', '判题'],
    status: 'soon',
  },
]

export const readyTools = tools.filter(
  (tool): tool is ToolDef & { to: string } => tool.status === 'ready' && !!tool.to,
)
