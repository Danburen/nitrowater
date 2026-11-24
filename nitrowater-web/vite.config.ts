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
    // Pre-bundle the runner's heavy deps at server start. Otherwise the dev optimizer may re-run
    // mid-navigation and invalidate an in-flight lazy import on the first visit to /runner.
    include: [
      '@codemirror/state',
      '@codemirror/view',
      '@codemirror/commands',
      '@codemirror/language',
      '@codemirror/autocomplete',
      '@codemirror/lang-python',
      '@codemirror/lang-javascript',
      // Only the Lua / C-like modes are used; these packages have no root export.
      '@codemirror/legacy-modes/mode/lua',
      '@codemirror/legacy-modes/mode/clike',
      '@codemirror/theme-one-dark',
      'element-plus',
      '@element-plus/icons-vue',
      'marked',
    ],
  },
  build: {
    rollupOptions: {
      output: {
        // The runner is lazy-loaded, so keep CodeMirror out of the entry chunk.
        manualChunks(id) {
          const path = id.replace(/\\/g, '/')
          if (!path.includes('/node_modules/')) return undefined
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
