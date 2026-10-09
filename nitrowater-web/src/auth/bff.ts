/**
 * BFF session client.
 *
 * The browser holds no token — only an HttpOnly session cookie set by the BFF. Authentication
 * happens with full-page redirects into the BFF's OIDC login; the BFF keeps the access/refresh
 * tokens server-side and answers `/bff/me` with the identity.
 */

export interface BffSession {
  authenticated: boolean
  uid?: string | null
  username?: string | null
  name?: string | null
  roles?: string[]
  did?: string | null
  issuer?: string | null
  expiresAt?: number | null
}

/** Reads a cookie by name (used for the CSRF token the BFF exposes). */
function readCookie(name: string): string | null {
  const escaped = name.replace(/([.$?*|{}()[\]\\/+^])/g, '\\$1')
  const match = document.cookie.match(new RegExp('(?:^|; )' + escaped + '=([^;]*)'))
  return match ? decodeURIComponent(match[1]) : null
}

/** CSRF header for mutating requests (the BFF exposes XSRF-TOKEN as a readable cookie). */
function csrfHeaders(): Record<string, string> {
  const token = readCookie('XSRF-TOKEN')
  return token ? { 'X-XSRF-TOKEN': token } : {}
}

/** Full-page redirect into the SSO authorization endpoint (the BFF is the OIDC client). */
export function login(): void {
  window.location.assign('/oauth2/authorization/nitrowater')
}

/**
 * Ends the BFF session and the upstream SSO session.
 *
 * A top-level form POST (not `fetch`) is used on purpose: the BFF answers with a 302 chain to the
 * Authorization Server's end-session endpoint, and only a top-level navigation carries the SSO
 * session cookie there (a `fetch` would drop it cross-origin and the SSO session would survive).
 * The CSRF token is submitted as the `_csrf` form field the BFF expects.
 */
export function logout(): void {
  const form = document.createElement('form')
  form.method = 'POST'
  form.action = '/logout'
  const csrf = document.createElement('input')
  csrf.type = 'hidden'
  csrf.name = '_csrf'
  csrf.value = readCookie('XSRF-TOKEN') ?? ''
  form.appendChild(csrf)
  document.body.appendChild(form)
  form.submit()
}

/** Asks the BFF who the current user is. Never carries a token. */
export async function fetchSession(): Promise<BffSession> {
  const res = await fetch('/bff/me', {
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })
  if (!res.ok) return { authenticated: false }
  return (await res.json()) as BffSession
}

/**
 * fetch() against the BFF (same origin) that attaches the CSRF header on mutating requests.
 * Use this for business API calls (`/api/**`), which the BFF proxies to the resource server.
 */
export async function apiFetch(input: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (method !== 'GET' && method !== 'HEAD') {
    for (const [key, value] of Object.entries(csrfHeaders())) {
      headers.set(key, value)
    }
  }
  return fetch(input, { ...init, headers, credentials: 'same-origin' })
}
