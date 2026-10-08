import { ref } from 'vue'
import { fetchSession, login as bffLogin, logout as bffLogout } from './bff'
import type { BffSession } from './bff'

/**
 * Shared reactive SSO session state, backed by the BFF (`/bff/me`).
 *
 * A single module-level store is exposed so every component observes the same session.
 */
const session = ref<BffSession | null>(null)
const loading = ref(true)
let initialized = false

async function refresh(): Promise<void> {
  session.value = await fetchSession()
}

function init(): void {
  if (initialized) return
  initialized = true
  void refresh().finally(() => {
    loading.value = false
  })
}

export function useAuth() {
  init()
  return {
    session,
    loading,
    /** Full-page redirect into the SSO login. */
    login: (): void => bffLogin(),
    /** Ends the BFF session then returns home. */
    logout: async (): Promise<void> => {
      await bffLogout()
    },
    refresh,
  }
}
