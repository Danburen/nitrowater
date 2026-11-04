/**
 * Shared response-envelope helpers.
 *
 * Every business API answers with `ApiResponse<T>` = `{ success, code, message, data }`, and paged
 * listings carry a Spring Data `Page` inside `data` (`content` + `page{...}`). The BFF's own
 * endpoints (`/bff/me`, session probes) are NOT enveloped because the BFF only proxies upstream
 * JSON. `unwrap` therefore passes any non-enveloped payload straight through, so a call site works
 * against both shapes while the rollout is in progress.
 */

import { ResponseCode, resolveMessage } from './codes'
import type { ApiResponse } from './types'

/** A rejection carrying the backend's machine-readable code next to the HTTP status. */
export class ApiError extends Error {
  readonly code: string
  readonly status: number

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

function isEnvelope(value: unknown): value is ApiResponse<unknown> {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as { success?: unknown }).success === 'boolean' &&
    'code' in (value as object)
  )
}

/**
 * Unwraps an enveloped payload and passes anything else through unchanged.
 *
 * @param payload parsed JSON body
 * @returns the `data` field of an envelope, or the payload itself
 * @throws ApiError when an envelope reports `success: false`
 */
export function unwrap<T>(payload: unknown): T {
  if (!isEnvelope(payload)) return payload as T
  if (!payload.success) throw new ApiError(0, payload.code, payload.message)
  return payload.data as T
}

/**
 * Reads a JSON response, throwing {@link ApiError} for a failed status or a rejected envelope and
 * returning the unwrapped `data`.
 *
 * @param response fetch response
 * @returns the unwrapped payload
 */
export async function readJson<T>(response: Response): Promise<T> {
  const text = await response.text().catch(() => '')
  let body: unknown
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    body = null
  }
  if (isEnvelope(body) && !body.success) {
    throw new ApiError(response.status, body.code,
      resolveMessage(body.code, body.message) ?? `HTTP ${response.status}`)
  }
  if (!response.ok) {
    throw new ApiError(response.status, ResponseCode.HttpError, `HTTP ${response.status}${text ? `: ${text}` : ''}`)
  }
  return unwrap<T>(body)
}

/**
 * Awaits a mutating call that returns no meaningful body, still surfacing envelope rejections.
 *
 * @param response fetch response
 */
export async function readVoid(response: Response): Promise<void> {
  const text = await response.text().catch(() => '')
  let body: unknown
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    body = null
  }
  if (isEnvelope(body) && !body.success) {
    throw new ApiError(response.status, body.code,
      resolveMessage(body.code, body.message) ?? `HTTP ${response.status}`)
  }
  if (!response.ok) {
    throw new ApiError(response.status, ResponseCode.HttpError, `HTTP ${response.status}${text ? `: ${text}` : ''}`)
  }
}
