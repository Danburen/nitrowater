import { ref } from 'vue'

import { adminApi, type AdminSessionVO } from '@/api/admin'
import { ApiError } from '@/api/http'

/**
 * Auth state for the admin console.
 *
 * Module-level singleton (same pattern as {@link useTheme}): the router guard resolves it once
 * per page load and the layout reads it for the header. Every route is private, so a single
 * resolved session is all the SPA needs — no store library required.
 */
export type AuthStatus = 'unknown' | 'authenticated' | 'forbidden'

const session = ref<AdminSessionVO | null>(null)
const status = ref<AuthStatus>('unknown')

/**
 * Resolve the current admin session. Idempotent: once authenticated it does not re-fetch
 * (in-page 401s after that are already handled by the http layer). A 403 means "logged in, but
 * not a super admin"; it is recorded so the guard can route to the forbidden page instead of
 * bouncing to login.
 *
 * @throws ApiError 401 (not authenticated) or 403 (not a super admin)
 */
async function load(): Promise<void> {
  if (status.value === 'authenticated') return
  try {
    session.value = await adminApi.session()
    status.value = 'authenticated'
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) {
      status.value = 'forbidden'
    }
    throw error
  }
}

/** Forget the cached session (e.g. after logout). */
function reset(): void {
  session.value = null
  status.value = 'unknown'
}

export function useSession() {
  return { session, status, load, reset }
}
