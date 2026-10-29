/**
 * BFF session client.
 *
 * The browser holds no token — only an HttpOnly session cookie set by the BFF. Authentication
 * happens with full-page redirects into the BFF's OIDC login; the BFF keeps the access/refresh
 * tokens server-side and answers `/bff/me` with the identity.
 */

export interface BffAccount {
  uid: string
  username?: string | null
  name?: string | null
  roles?: string[]
  picture?: string | null
  active?: boolean
}

export interface BffSession {
  authenticated: boolean
  uid?: string | null
  username?: string | null
  name?: string | null
  picture?: string | null
  roles?: string[]
  did?: string | null
  issuer?: string | null
  expiresAt?: number | null
  accounts?: BffAccount[]
  activeUid?: string | null
}

/** How the BFF answered a session probe. */
export type SessionState = 'authenticated' | 'anonymous' | 'unavailable'

export interface SessionProbe {
  state: SessionState
  session: BffSession | null
  /** True when the server said the previous session is gone (stale cookie) rather than "never logged in". */
  expired?: boolean
  /** Diagnostic detail when {@code state} is {@code unavailable}. */
  reason?: string
}

/** Reads a cookie by name (used for the CSRF token the BFF exposes). */
function readCookie(name: string): string | null {
  const escaped = name.replace(/([.$?*|{}()[\]\\/+^])/g, '\\$1')
  const match = document.cookie.match(new RegExp('(?:^|; )' + escaped + '=([^;]*)'))
  return match ? decodeURIComponent(match[1]) : null
}

/** CSRF header for mutating requests (the BFF exposes XSRF-TOKEN as a readable cookie). */
export function csrfHeaders(): Record<string, string> {
  const token = readCookie('XSRF-TOKEN')
  return token ? { 'X-XSRF-TOKEN': token } : {}
}

/** sessionStorage key holding the in-app path to return to once login completes. */
const POST_LOGIN_KEY = 'nitrowater:post-login'

/**
 * Full-page redirect into the SSO authorization endpoint (the BFF is the OIDC client).
 *
 * The BFF always lands the user back on `/` after login, so the intended path is stashed in
 * sessionStorage and consumed by {@link consumePostLogin} once the SPA sees an authenticated
 * session — no BFF protocol change needed.
 *
 * <p>The key is ALWAYS (over)written: with no explicit {@code returnTo} we fall back to the page
 * the user is currently on. Leaving a previous value in place would resume a stale destination —
 * e.g. logging in from the code-runner after an earlier visit to {@code /admin} would land on the
 * (unauthorized) admin console instead of coming back to the tool.</p>
 *
 * @param returnTo in-app path to navigate to after login (e.g. {@code /tools/code-runner}); defaults to
 *                 the current in-app location
 */
export function login(returnTo?: string): void {
  const target = returnTo && returnTo.startsWith('/')
    ? returnTo
    : window.location.pathname + window.location.search
  sessionStorage.setItem(POST_LOGIN_KEY, target)
  window.location.assign('/oauth2/authorization/nitrowater')
}

/** Reads and clears the pending post-login path (if any) recorded by {@link login}. */
export function consumePostLogin(): string | null {
  const target = sessionStorage.getItem(POST_LOGIN_KEY)
  if (target) sessionStorage.removeItem(POST_LOGIN_KEY)
  return target
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

/**
 * Asks the BFF who the current user is. Never carries a token.
 *
 * <p>A failed probe is not the same as "signed out": a 5xx or a network error means the auth
 * service is unreachable, and reporting that as anonymous would silently log the user out of the
 * UI while their session may still be valid.</p>
 */
export async function fetchSession(): Promise<SessionProbe> {
  try {
    const res = await fetch('/bff/me', {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' },
    })
    if (res.ok) {
      const body = (await res.json()) as BffSession & { expired?: boolean }
      return body.authenticated
        ? { state: 'authenticated', session: body }
        : { state: 'anonymous', session: null, expired: body.expired === true }
    }
    if (res.status >= 500) {
      return { state: 'unavailable', session: null, reason: `HTTP ${res.status}` }
    }
    return { state: 'anonymous', session: null }
  } catch (error) {
    return { state: 'unavailable', session: null, reason: error instanceof Error ? error.message : '网络错误' }
  }
}

/**
 * Starts a fresh authorization with `prompt=login`, so the user can sign in as a second account
 * without disturbing the accounts already held by this session.
 */
export function addAccount(): void {
  // The BFF lands on `/` after the new login; resume the page the user was on.
  sessionStorage.setItem(POST_LOGIN_KEY, window.location.pathname + window.location.search)
  window.location.assign('/bff/accounts/add')
}

/**
 * Switches to an account already present in the server-side pool.
 *
 * The BFF rebuilds the security context in place and rotates the session id; a full reload then
 * discards any in-memory state (AI chat transcript, admin filters) that belonged to the previous
 * account.
 */
export async function activateAccount(uid: string): Promise<void> {
  const res = await apiFetch(`/bff/accounts/${encodeURIComponent(uid)}/activate`, { method: 'POST' })
  if (!res.ok) throw new Error(`activate account failed: ${res.status}`)
  window.location.reload()
}

export interface AccountRemoval {
  accounts: BffAccount[]
  activeUid: string | null
  needLogout: boolean
}

/**
 * Removes one account and revokes its refresh token.
 *
 * When the last account is removed the BFF keeps the security context so a top-level logout can
 * still perform RP-Initiated Logout; this helper does that automatically.
 */
export async function removeAccount(uid: string): Promise<AccountRemoval> {
  const res = await apiFetch(`/bff/accounts/${encodeURIComponent(uid)}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`remove account failed: ${res.status}`)
  const result = (await res.json()) as AccountRemoval
  if (result.needLogout) {
    logout()
    return result
  }
  window.location.reload()
  return result
}

/**
 * Global "session is gone" signal.
 *
 * <p>Any 401 from a protected endpoint means the BFF no longer has credentials — the usual causes
 * are an expired session, a Redis restart, or a new deployment invalidating the epoch. Listeners
 * (see {@code useAuth}) flip the UI into its expired state so the user is told once, instead of
 * seeing unrelated errors on each page.</p>
 */
type AuthExpiredListener = () => void
const authExpiredListeners = new Set<AuthExpiredListener>()
let lastExpiredAt = 0
const EXPIRED_DEBOUNCE_MS = 3000

/**
 * @param listener callback invoked when a session loss is detected
 * @return unsubscribe function
 */
export function onAuthExpired(listener: AuthExpiredListener): () => void {
  authExpiredListeners.add(listener)
  return () => authExpiredListeners.delete(listener)
}

/** Notifies listeners once per burst (parallel requests commonly fail together). */
export function notifyAuthExpired(): void {
  const now = Date.now()
  if (now - lastExpiredAt < EXPIRED_DEBOUNCE_MS) return
  lastExpiredAt = now
  for (const listener of authExpiredListeners) listener()
}

/**
 * fetch() against the BFF (same origin) that attaches the CSRF header on mutating requests.
 * Use this for business API calls (`/api/**`), which the BFF proxies to the resource server.
 *
 * <p>A 401 on a protected endpoint is reported to {@link onAuthExpired} subscribers. Anonymous
 * endpoints are excluded: the public sandbox runner legitimately answers without a session.</p>
 */
export async function apiFetch(input: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (method !== 'GET' && method !== 'HEAD') {
    for (const [key, value] of Object.entries(csrfHeaders())) {
      headers.set(key, value)
    }
  }
  const response = await fetch(input, { ...init, headers, credentials: 'same-origin' })
  if (response.status === 401 && !input.startsWith('/api/public/')) notifyAuthExpired()
  return response
}
