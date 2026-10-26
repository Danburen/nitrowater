import { createHttpClient } from '@nitrowater/http'

export { ApiError } from '@nitrowater/http'
export type { ApiEnvelope } from '@nitrowater/http'

// Client for the account admin API (`/api/admin/**`). The SPA is same-origin with the
// account service, so the form-login session cookie travels automatically. Mutations must
// echo the `XSRF-TOKEN` cookie, and a 401 (session expired) bounces through SSO login.
export const http = createHttpClient({
  csrf: true,
  onUnauthorized: () => {
    const back = window.location.pathname + window.location.hash
    window.location.assign('/login?redirect=' + encodeURIComponent(back))
  },
})
