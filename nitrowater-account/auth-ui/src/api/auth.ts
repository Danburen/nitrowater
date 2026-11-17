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

/** One legal document link registered by the application that started this login. */
export interface LegalLink {
  /** Label shown on the page, e.g. `用户协议`. */
  title: string
  /** Absolute URL on the application's own host. */
  url: string
}

/** Context of the hosted auth pages, including the application's legal links. */
export interface LoginContext {
  /** Pending OIDC `client_id`, null when the page was opened directly. */
  clientId: string | null
  /** Display name of that application, null when nothing is pending. */
  clientName: string | null
  /** Documents that application registered, in display order; empty when it registered none. */
  legalLinks: LegalLink[]
}

/**
 * Reads the client of the pending OIDC authorization request, plus its legal links.
 *
 * The account service keeps that request in the session, so this answers "which app is the user
 * logging in to". The client fields are null when the page was opened directly; `legalLinks` is
 * whatever that application registered in `client_legal_link` (empty when it registered none).
 */
export function fetchLoginContext(): Promise<LoginContext> {
  return http.get<LoginContext>('/api/auth/login-context')
}

/** Where to send the browser after a successful JSON (code) login. */
export interface LoginRedirect {
  redirectUrl: string
}

/** Sends a login code; the server sets the channel-specific *_CODE_KEY cookie. */
export function sendLoginCode(
  identifier: string,
  channel: 'sms' | 'email',
  captcha: string,
): Promise<void> {
  return postJson<void>('/api/auth/send-code', {
    target: identifier,
    channel,
    scene: 'login',
    captcha,
  })
}

/** Completes code login and establishes the SSO session server-side. */
export function loginByCode(body: {
  identifier: string
  code: string
  deviceFp?: string
  redirect?: string
}): Promise<LoginRedirect> {
  const { redirect, ...payload } = body
  const query = redirect ? `?redirect=${encodeURIComponent(redirect)}` : ''
  return postJson<LoginRedirect>(`/api/auth/login-by-code${query}`, payload)
}
