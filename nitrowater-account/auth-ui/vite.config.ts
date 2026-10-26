import { fileURLToPath, URL } from 'node:url'

import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// SSO hosted auth UI. Served same-origin by nitrowater-account:
//  - production build uses base '/auth/' and emits straight into the account static
//    resources, so bootRun (dev static-locations) and bootJar pick it up;
//  - the dev server keeps base '/' so /login and /register behave like real routes;
//  - /login GET must fall through to the SPA, while /login POST is the Spring Security
//    form-login endpoint and must reach account :8090.
export default defineConfig(({ command }) => ({
  plugins: [vue(), tailwindcss()],
  base: command === 'build' ? '/auth/' : '/',
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    outDir: '../src/main/resources/static/auth',
    emptyOutDir: true,
  },
  server: {
    port: 5175,
    strictPort: true,
    proxy: {
      '/api': { target: 'http://localhost:8090', changeOrigin: true },
      '/oauth2': { target: 'http://localhost:8090', changeOrigin: true },
      '/.well-known': { target: 'http://localhost:8090', changeOrigin: true },
      '/logout': { target: 'http://localhost:8090', changeOrigin: true },
      '/favicon.svg': { target: 'http://localhost:8090', changeOrigin: true },
      '/login': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        // Let the SPA own GET/HEAD so refresh on /login works; only the form POST goes
        // to Spring Security. Without this bypass the dev server would proxy the page
        // request too and the Vue app would never load.
        bypass(req) {
          if (req.method === 'GET' || req.method === 'HEAD') return '/index.html'
          return undefined
        },
        // Keep dev feedback on :5175 instead of following Spring's absolute
        // Location back to :8090 after a failed login.
        configure(proxy) {
          proxy.on('proxyRes', (proxyRes) => {
            const location = proxyRes.headers.location
            if (typeof location === 'string' && location.startsWith('http://localhost:8090/login')) {
              proxyRes.headers.location = location.slice('http://localhost:8090'.length)
            }
          })
        },
      },
    },
  },
}))
