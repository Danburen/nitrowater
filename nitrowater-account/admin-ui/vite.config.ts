import { fileURLToPath, URL } from 'node:url'

import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// Admin console (admin-ui). Served same-origin by nitrowater-account under /admin/:
//  - base '/admin/' so emitted asset URLs resolve under the Spring static mapping;
//  - build straight into the account static resources so both bootRun (dev static-locations)
//    and bootJar pick the SPA up (that output dir is gitignored / dockerignored);
//  - dev server proxies /api -> account (:8090), so the SPA shares the SSO session cookie.
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  base: '/admin/',
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    outDir: '../src/main/resources/static/admin',
    emptyOutDir: true,
  },
  server: {
    port: 5174,
    proxy: {
      '/api': { target: 'http://localhost:8090', changeOrigin: true },
    },
  },
})
