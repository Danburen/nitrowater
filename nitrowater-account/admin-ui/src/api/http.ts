// Thin fetch wrapper for the account admin API (`/api/admin/**`).
// The SPA is same-origin with the account service, so the form-login session cookie
// travels automatically (credentials: 'same-origin'). Responses use the shared
// ApiResponse envelope ({ success, code, message, data }); we unwrap `data` and
// surface failures as ApiError. 401 (session expired) bounces to the SSO login page.

export interface ApiEnvelope<T> {
  success: boolean
  code: string
  message: string
  data: T
}

export class ApiError extends Error {
  code: string
  status: number

  constructor(code: string, message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

async function request<T>(method: string, url: string, body?: unknown): Promise<T> {
  const res = await fetch(url, {
    method,
    credentials: 'same-origin',
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (res.status === 401) {
    // Session expired / not logged in: send the user through SSO, then back here.
    const back = window.location.pathname + window.location.hash
    window.location.assign('/login?redirect=' + encodeURIComponent(back))
    throw new ApiError('http.unauthorized', '未登录或登录已过期', 401)
  }

  const text = await res.text()
  let payload: ApiEnvelope<T> | null = null
  if (text) {
    try {
      payload = JSON.parse(text) as ApiEnvelope<T>
    } catch {
      payload = null
    }
  }

  if (!res.ok) {
    throw new ApiError(
      payload?.code ?? String(res.status),
      payload?.message ?? `请求失败 (${res.status})`,
      res.status,
    )
  }
  return payload?.data as T
}

export const http = {
  get: <T>(url: string) => request<T>('GET', url),
  post: <T>(url: string, body?: unknown) => request<T>('POST', url, body),
  put: <T>(url: string, body?: unknown) => request<T>('PUT', url, body),
  del: <T>(url: string) => request<T>('DELETE', url),
}
