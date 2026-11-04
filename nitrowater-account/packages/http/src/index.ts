/**
 * Shared fetch client for the account service UIs.
 *
 * Every endpoint returns the same {@link ApiEnvelope} (`{ success, code, message, data }`),
 * so the client unwraps `data` and surfaces failures as {@link ApiError}. Each app opts into
 * the behavior it needs: CSRF echo for cookie-authenticated mutations, and an optional 401
 * hook (the admin console bounces through SSO login; the public auth pages just throw).
 */

export interface ApiEnvelope<T> {
  success: boolean
  code: string
  message: string
  data: T
  /** Field-level validation messages, when the server provides them. */
  errors?: string[]
}

export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly errors: string[]

  constructor(code: string, message: string, status: number, errors: string[] = []) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.errors = errors
  }
}

export interface HttpClientOptions {
  /**
   * Echo the readable `XSRF-TOKEN` cookie back as `X-XSRF-TOKEN` on state-changing requests.
   * Required by Spring Security's CookieCsrfTokenRepository.
   */
  csrf?: boolean
  /** Invoked when a response is 401, before {@link ApiError} is thrown. */
  onUnauthorized?: () => void
  /** Separator used to join `message` + `errors[]`. Defaults to a full-width semicolon. */
  errorSeparator?: string
}

const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

/** Codes the server sends with a generic (or identical) message; the UI supplies the wording. */
const GENERIC_CODE_MESSAGES: Record<string, string> = {
  TOO_MANY_REQUEST: '操作太频繁，请稍后再试',
}

/** Reads a cookie value by name. */
function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'))
  return match ? decodeURIComponent(match[1]) : null
}

export function createHttpClient(options: HttpClientOptions = {}) {
  const errorSeparator = options.errorSeparator ?? '；'

  async function request<T>(method: string, url: string, body?: unknown): Promise<T> {
    const headers: Record<string, string> = {}
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json'
    }
    if (options.csrf && MUTATING_METHODS.has(method.toUpperCase())) {
      const csrf = readCookie('XSRF-TOKEN')
      if (csrf) {
        headers['X-XSRF-TOKEN'] = csrf
      }
    }

    const res = await fetch(url, {
      method,
      credentials: 'same-origin',
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })

    const text = await res.text()
    let payload: ApiEnvelope<T> | null = null
    if (text) {
      try {
        payload = JSON.parse(text) as ApiEnvelope<T>
      } catch {
        payload = null
      }
    }

    if (res.status === 401 && options.onUnauthorized) {
      options.onUnauthorized()
      throw new ApiError('http.unauthorized', '未登录或登录已过期', 401)
    }

    if (!res.ok || payload?.success === false) {
      const code = payload?.code ?? String(res.status)
      // The backend answers every limiter bucket with the bare code, which is not user-facing.
      const message = payload?.message && payload.message !== code
        ? payload.message
        : GENERIC_CODE_MESSAGES[code]
      const parts = [message, ...(payload?.errors ?? [])].filter(
        (part): part is string => Boolean(part),
      )
      throw new ApiError(
        code,
        parts.join(errorSeparator) || `请求失败 (${res.status})`,
        res.status,
        payload?.errors ?? [],
      )
    }

    return payload?.data as T
  }

  return {
    request,
    get: <T>(url: string) => request<T>('GET', url),
    post: <T>(url: string, body?: unknown) => request<T>('POST', url, body),
    put: <T>(url: string, body?: unknown) => request<T>('PUT', url, body),
    patch: <T>(url: string, body?: unknown) => request<T>('PATCH', url, body),
    del: <T>(url: string) => request<T>('DELETE', url),
  }
}
