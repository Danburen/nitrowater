import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
    // Element Plus on-demand: <el-*> in templates auto-resolve + auto-import their CSS.
    // AutoImport additionally resolves the programmatic APIs (ElMessage/ElMessageBox/…) with
    // their styles. We intentionally do NOT auto-import Vue APIs — `import { ref } from 'vue'`
    // stays explicit to keep ESLint's no-unused-vars meaningful.
    AutoImport({ resolvers: [ElementPlusResolver()], dts: 'src/auto-imports.d.ts' }),
    Components({ resolvers: [ElementPlusResolver()], dts: 'src/components.d.ts' }),
  ],
  optimizeDeps: {
    // Pre-bundle the tools' heavy deps at server start. Otherwise the dev optimizer may re-run
    // mid-navigation and invalidate an in-flight lazy import → "Failed to fetch dynamically
    // imported module" on the first visit to a tool (e.g. /tools/code-runner).
    include: [
      '@codemirror/state',
      '@codemirror/view',
      '@codemirror/commands',
      '@codemirror/language',
      '@codemirror/autocomplete',
      '@codemirror/lang-python',
      '@codemirror/theme-one-dark',
      'echarts',
      'd3',
      'html2canvas',
      'jszip',
      'libarchive.js',
      'file-saver',
      'element-plus',
      '@element-plus/icons-vue',
    ],
  },
  build: {
    rollupOptions: {
      output: {
        // Split the heavy tool-only deps into their own vendor chunks. They are reached
        // through interaction-level dynamic imports (L2), so userland stays out of the entry.
        manualChunks(id) {
          const path = id.replace(/\\/g, '/')
          if (!path.includes('/node_modules/')) return undefined
          if (/\/node_modules\/(echarts|zrender)\//.test(path)) return 'vendor-echarts'
          if (/\/node_modules\/(d3|d3-[\w-]+)\//.test(path)) return 'vendor-d3'
          if (/\/node_modules\/html2canvas\//.test(path)) return 'vendor-html2canvas'
          if (/\/node_modules\/(@codemirror|@lezer|codemirror)\//.test(path)) return 'vendor-codemirror'
          return undefined
        },
      },
    },
  },
  server: {
    // Fixed dev port. Same-origin model: the SPA talks only to the BFF (proxy below),
    // so no CORS and no tokens in the browser.
    port: 5173,
    strictPort: true,
    proxy: {
      // changeOrigin stays false so the BFF sees Host: localhost:5173 and builds the OIDC
      // redirect_uri as http://localhost:5173/login/oauth2/code/nitrowater.
      '/api': { target: 'http://localhost:8080' },
      '/bff': { target: 'http://localhost:8080' },
      '/oauth2': { target: 'http://localhost:8080' },
      '/login': { target: 'http://localhost:8080' },
      '/logout': { target: 'http://localhost:8080' },
    },
  },
})
