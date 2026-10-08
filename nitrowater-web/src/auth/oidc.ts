import { UserManager, WebStorageStateStore } from 'oidc-client-ts'
import type { User, UserManagerSettings } from 'oidc-client-ts'

/**
 * OIDC / SSO client configuration.
 *
 * The Authorization Server (nitrowater-account) is a separate origin (:8090); the SPA
 * authenticates with the authorization-code flow + PKCE as a public client. Override any
 * value via Vite env vars (VITE_OIDC_*) for non-local deployments.
 */
const ORIGIN = window.location.origin

export const oidcSettings: UserManagerSettings = {
  authority: import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8090',
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'nitrowater-web',
  redirect_uri: import.meta.env.VITE_OIDC_REDIRECT_URI ?? `${ORIGIN}/auth/callback`,
  post_logout_redirect_uri: import.meta.env.VITE_OIDC_POST_LOGOUT_REDIRECT_URI ?? `${ORIGIN}/`,
  silent_redirect_uri: `${ORIGIN}/auth/silent-callback`,
  response_type: 'code', // authorization code + PKCE (public client)
  scope: 'openid profile',
  loadUserInfo: true,
  automaticSilentRenew: true,
  userStore: new WebStorageStateStore({ store: window.localStorage }),
}

export const userManager = new UserManager(oidcSettings)

/**
 * Decodes a JWT payload without verifying the signature.
 * Display-only helper: never trust these claims for authorization decisions on the client.
 */
export function decodeJwtPayload(token?: string | null): Record<string, unknown> | null {
  if (!token) return null
  const segment = token.split('.')[1]
  if (!segment) return null
  try {
    const base64 = segment.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
    const bytes = Uint8Array.from(atob(padded), (char) => char.charCodeAt(0))
    return JSON.parse(new TextDecoder().decode(bytes)) as Record<string, unknown>
  } catch {
    return null
  }
}

export type { User }
