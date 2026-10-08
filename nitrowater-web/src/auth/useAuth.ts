import { ref } from 'vue'
import type { User } from 'oidc-client-ts'
import { userManager } from './oidc'

/**
 * Shared reactive SSO session state.
 *
 * A single module-level store is exposed so every component observes the same user;
 * event hooks keep it in sync across tabs/renewals.
 */
const user = ref<User | null>(null)
const loading = ref(true)
let initialized = false

async function refresh(): Promise<void> {
  user.value = await userManager.getUser()
}

function init(): void {
  if (initialized) return
  initialized = true
  userManager.events.addUserLoaded((loaded) => {
    user.value = loaded
  })
  userManager.events.addUserUnloaded(() => {
    user.value = null
  })
  userManager.events.addAccessTokenExpired(() => {
    user.value = null
  })
  void refresh().finally(() => {
    loading.value = false
  })
}

export function useAuth() {
  init()
  return {
    user,
    loading,
    login: (): Promise<void> => userManager.signinRedirect(),
    logout: (): Promise<void> => userManager.signoutRedirect(),
    renew: async (): Promise<void> => {
      await userManager.signinSilent()
    },
    refresh,
  }
}
