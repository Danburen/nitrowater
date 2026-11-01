import { computed, ref } from 'vue'
import {
  activateAccount,
  addAccount as bffAddAccount,
  consumeLogoutPending,
  consumePostLogin,
  fetchSession,
  login as bffLogin,
  logout as bffLogout,
  onAuthExpired,
  removeAccount as bffRemoveAccount,
} from './bff'
import type { BffSession, SessionState } from './bff'

/**
 * Shared reactive SSO session state, backed by the BFF (`/bff/me`).
 *
 * <p>The cached snapshot is never treated as authorization: a 401 from any protected endpoint (or a
 * periodic probe) flips {@link expired}, so a session lost to a Redis restart or a new deployment
 * becomes a single global notice instead of scattered "login required" errors.</p>
 */
export type AuthState = 'booting' | SessionState

const session = ref<BffSession | null>(null)
const state = ref<AuthState>('booting')
const expired = ref(false)
/** True when a previous logout navigation failed and the user is still signed in. */
const logoutStuck = ref(false)
const loading = computed(() => state.value === 'booting')

let initialized = false
let lastCheckedAt = 0
/** Idle tabs re-verify at this interval; a tab returning to the foreground checks sooner. */
const RECHECK_MS = 10 * 60 * 1000
const RECHECK_ON_FOCUS_MS = 5 * 60 * 1000

/** Whether the current session carries the admin role (accepts both claim spellings). */
const isAdmin = computed(() => {
  const roles = session.value?.roles ?? []
  return roles.includes('ADMIN') || roles.includes('ROLE_ADMIN')
})

/** Accounts held by the server-side pool, in login order. */
const accounts = computed(() => session.value?.accounts ?? [])

onAuthExpired(() => {
  session.value = null
  state.value = 'anonymous'
  expired.value = true
})

async function refresh(): Promise<void> {
  const probe = await fetchSession()
  lastCheckedAt = Date.now()
  if (probe.state === 'unavailable') {
    // Keep the last identity so the UI can keep rendering, but make the outage visible: the auth
    // service being unreachable is NOT the same as being signed out.
    state.value = 'unavailable'
    return
  }
  session.value = probe.session
  state.value = probe.state
  if (probe.state === 'authenticated') {
    // A logout marker with a live session means the logout round trip never finished (AS rejected
    // the redirect, CSRF missing, network): surface it instead of pretending nothing happened.
    if (consumeLogoutPending()) logoutStuck.value = true
  } else {
    consumeLogoutPending()
    logoutStuck.value = false
  }
  // A stale cookie means the server dropped the session (deploy / Redis restart): surface it as an
  // expiry rather than as "this visitor never signed in".
  expired.value = probe.state === 'anonymous' && probe.expired === true
}

function init(): void {
  if (initialized) return
  initialized = true
  void refresh().finally(() => {
    if (state.value === 'booting') state.value = 'anonymous'
  })
  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible' && Date.now() - lastCheckedAt > RECHECK_ON_FOCUS_MS) {
        void refresh()
      }
    })
    window.setInterval(() => {
      if (document.visibilityState === 'visible') void refresh()
    }, RECHECK_MS)
  }
}

export function useAuth() {
  init()
  return {
    session,
    state,
    loading,
    expired,
    logoutStuck,
    isAdmin,
    accounts,
    /** Full-page redirect into the SSO login; `returnTo` is resumed after authentication. */
    login: (returnTo?: string): void => bffLogin(returnTo),
    /** Ends the BFF session and the upstream SSO session (top-level navigation). */
    logout: (): void => {
      bffLogout()
    },
    /** Starts a fresh authorization to add another account. */
    addAccount: (): void => bffAddAccount(),
    /** Instantly activates an account already held by the session (reloads the SPA). */
    switchAccount: (uid: string): Promise<void> => activateAccount(uid),
    /** Removes one account; logs out when it was the last one (reloads or redirects). */
    removeAccount: (uid: string) => bffRemoveAccount(uid),
    refresh,
    consumePostLogin,
  }
}
