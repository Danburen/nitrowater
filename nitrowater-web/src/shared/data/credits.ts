/**
 * Site-level open-source crediting catalogue (portal "About" page).
 * Grouped by purpose; each entry links back to its repository.
 */
export interface CreditItem {
  name: string
  desc: string
  url: string
}

export interface CreditGroup {
  title: string
  items: CreditItem[]
}

export const creditGroups: CreditGroup[] = [
  {
    title: '前端框架与构建',
    items: [
      { name: 'Vue 3', desc: '渐进式前端框架，本站 UI 的响应式核心', url: 'https://github.com/vuejs/core' },
      { name: 'Vue Router', desc: '官方路由，驱动门户 / 工具 / 文档间的导航', url: 'https://github.com/vuejs/router' },
      { name: 'Vite', desc: '极速的开发服务器与构建工具', url: 'https://github.com/vitejs/vite' },
      { name: 'Tailwind CSS', desc: '原子化 CSS，本站样式与深浅色主题的基座', url: 'https://github.com/tailwindlabs/tailwindcss' },
    ],
  },
  {
    title: 'UI 组件',
    items: [
      { name: 'Element Plus', desc: '复杂控件（按钮 / 提示 / 选择器），按需加载', url: 'https://github.com/element-plus/element-plus' },
    ],
  },
  {
    title: '数据可视化',
    items: [
      { name: 'Apache ECharts', desc: '树形结构可视化（交互树图 / 多种布局）', url: 'https://github.com/apache/echarts' },
      { name: 'D3.js', desc: '列表树渲染与数据驱动布局', url: 'https://github.com/d3/d3' },
      { name: 'html2canvas', desc: '将 DOM 渲染为 PNG，用于导出图片', url: 'https://github.com/niklasvh/html2canvas' },
    ],
  },
  {
    title: '文件处理',
    items: [
      { name: 'JSZip', desc: '浏览器内 ZIP 打包与解压', url: 'https://github.com/Stuk/jszip' },
      { name: 'libarchive.js', desc: '基于 WebAssembly 解压 RAR / 7z / TAR 等格式', url: 'https://github.com/nika-begiashvili/libarchivejs' },
      { name: 'docx', desc: '在浏览器生成 .docx 文档', url: 'https://github.com/dolanmiu/docx' },
      { name: 'marked', desc: 'Markdown 解析器', url: 'https://github.com/markedjs/marked' },
      { name: 'FileSaver.js', desc: '触发浏览器文件下载', url: 'https://github.com/eligrey/FileSaver.js' },
    ],
  },
  {
    title: '编辑器与本地运行时',
    items: [
      { name: 'CodeMirror 6', desc: '代码编辑器核心（语法高亮 / 补全 / 主题）', url: 'https://github.com/codemirror/dev' },
      { name: 'Pyodide', desc: 'CPython 的 WebAssembly 运行时，浏览器内运行 Python', url: 'https://github.com/pyodide/pyodide' },
      { name: 'ffmpeg.wasm', desc: 'FFmpeg 的 WebAssembly 版本，浏览器内视频转码', url: 'https://github.com/ffmpegwasm/ffmpeg.wasm' },
    ],
  },
]
