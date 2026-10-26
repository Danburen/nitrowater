import { createHttpClient } from '@nitrowater/http'

export { ApiError } from '@nitrowater/http'

/**
 * Anonymous auth API client (`/api/auth/**`).
 *
 * These endpoints are same-origin and CSRF-exempt in AuthorizationServerConfig, so
 * credentials travel with the request and no X-XSRF-TOKEN header is needed. A 401 is surfaced
 * as an error (the login form owns the flow); it must not bounce through SSO login.
 */
const http = createHttpClient()

/** POST JSON to the anonymous auth API. */
export function postJson<T>(url: string, body: unknown): Promise<T> {
  return http.post<T>(url, body)
}
