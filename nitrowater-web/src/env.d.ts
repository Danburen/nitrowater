/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** OIDC issuer base URL (the SSO Authorization Server). Default: http://localhost:8090 */
  readonly VITE_OIDC_AUTHORITY?: string
  /** Registered OIDC client id. Default: nitrowater-web */
  readonly VITE_OIDC_CLIENT_ID?: string
  /** Authorization-code redirect URI. Default: <origin>/auth/callback */
  readonly VITE_OIDC_REDIRECT_URI?: string
  /** post_logout_redirect_uri. Default: <origin>/ */
  readonly VITE_OIDC_POST_LOGOUT_REDIRECT_URI?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
