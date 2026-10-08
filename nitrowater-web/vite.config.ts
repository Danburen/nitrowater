import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    // Fixed dev port so the OIDC redirect_uri (http://localhost:5173/auth/callback) matches.
    port: 5173,
    strictPort: true,
  },
})
