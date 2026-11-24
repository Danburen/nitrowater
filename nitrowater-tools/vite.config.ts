import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { defineConfig } from 'vite'

// The tools site is fully static: no BFF, no OIDC, no server-side code.
export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
    // Element Plus is used by a few heavyweight tool views (tree / list-tree / GIF).
    AutoImport({ resolvers: [ElementPlusResolver()], dts: 'src/auto-imports.d.ts' }),
    Components({ resolvers: [ElementPlusResolver()], dts: 'src/components.d.ts' }),
  ],
  optimizeDeps: {
    include: [
      'echarts',
      'd3',
      'html2canvas',
      'jszip',
      'libarchive.js',
      'file-saver',
      'element-plus',
      '@element-plus/icons-vue',
      'docx',
      'marked',
    ],
    // @ffmpeg/ffmpeg creates its Worker via `new Worker(new URL('./worker.js', import.meta.url))`.
    // Pre-bundling rewrites that URL and breaks worker resolution in dev, so keep it as-is.
    exclude: ['@ffmpeg/ffmpeg'],
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          const path = id.replace(/\\/g, '/')
          if (!path.includes('/node_modules/')) return undefined
          if (/\/node_modules\/(echarts|zrender)\//.test(path)) return 'vendor-echarts'
          if (/\/node_modules\/(d3|d3-[\w-]+)\//.test(path)) return 'vendor-d3'
          if (/\/node_modules\/html2canvas\//.test(path)) return 'vendor-html2canvas'
          return undefined
        },
      },
    },
  },
  server: {
    port: 5174,
    strictPort: true,
  },
})
