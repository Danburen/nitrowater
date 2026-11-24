/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Standalone offline tools site origin (build-time placeholder in `.env.example`). */
  readonly VITE_OFFLINE_TOOLS_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

// The SPA talks to the same-origin BFF; no OIDC/token settings are needed in the browser.
