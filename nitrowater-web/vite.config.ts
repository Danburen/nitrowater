import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
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
