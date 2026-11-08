/**
 * Machine-readable `code` values carried by the response envelope.
 *
 * A const object (not a TypeScript `enum`) so a value works as a value *and* a type, stays
 * tree-shakeable and plays well with `verbatimModuleSyntax`.
 *
 * Only the **string** codes are mirrored from the backend (`BaseResponseCode` and the domain
 * exceptions); HTTP status codes are intentionally not mirrored — the status is for transport
 * concerns (retry, login redirect), the code is for UI branching.
 */
export const ResponseCode = {
  /** Success envelope. */
  Success: 'success',
  /** No/expired session. */
  Unauthorized: 'unauthorized',
  /** Authenticated but not allowed. */
  Forbidden: 'forbidden',
  /** Unknown route. */
  NotFound: 'not_found',
  /** Bean validation or unparsable body. */
  InvalidRequest: 'invalid_request',
  /** Unhandled server error. */
  InternalError: 'internal_error',
  /** A downstream service is unreachable (BFF answered 502 instead of a generic 500). */
  UpstreamUnavailable: 'upstream_unavailable',
  /** The uploaded file exceeds the server's multipart size limit (413). */
  UploadTooLarge: 'upload_too_large',
  /** The server could not parse the multipart body (truncated / bad boundary). */
  InvalidMultipart: 'invalid_multipart',
  /** Non-envelope failure answered with a non-2xx status (client-side synthetic code). */
  HttpError: 'http_error',
  /** The caller's daily AI budget is exhausted. */
  TokenQuota: 'token_quota',
  /** Legacy alias of {@link ResponseCode.TokenQuota}. */
  DailyQuota: 'daily_quota',
  /** The platform-wide AI budget is exhausted. */
  GlobalQuotaExceeded: 'global_quota_exceeded',
  /** Per-minute rate limit hit. */
  RateLimited: 'rate_limited',
  /**
   * Generic rate-limit rejection. Every limiter bucket (resend cooldown, IP window, per-target
   * hourly cap) answers this one code; the UI only has to restart its local countdown.
   */
  TooManyRequests: 'TOO_MANY_REQUEST',
  /** The AI feature is switched off. */
  AiDisabled: 'ai_disabled',
  /** The requested AI mode is switched off. */
  AiModeDisabled: 'ai_mode_disabled',
  /** The content gate blocked the request. */
  ContentBlocked: 'content_blocked',
  /** The content gate put the caller on cooldown. */
  ContentCooldown: 'content_cooldown',
  /** One AI turn exceeded its wall-clock budget (504). */
  TurnTimeout: 'turn_timeout',
  /** User uploads are disabled on this deployment. */
  UploadsDisabled: 'uploads_disabled',
  /** The internal endpoint is not reachable from this network. */
  InternalForbidden: 'internal_forbidden',
  /** The storage backend failed. */
  StorageError: 'storage_error',
} as const

/** Union of every known response code. */
export type ResponseCode = (typeof ResponseCode)[keyof typeof ResponseCode]

/**
 * Codes whose wire `message` is just the code itself (or empty), mapped to human wording.
 *
 * Only add entries where the backend deliberately sends a generic message — a real server message
 * always wins.
 */
const CODE_MESSAGES: Record<string, string> = {
  [ResponseCode.TooManyRequests]: '操作太频繁，请稍后再试',
}

/**
 * Picks the message to show for a rejected envelope.
 *
 * @param code machine code from the envelope
 * @param message server message, which may equal the code
 * @returns the server message, the mapped wording, or `null` when neither applies
 */
export function resolveMessage(code?: string | null, message?: string | null): string | null {
  if (message && message !== code) return message
  if (code && CODE_MESSAGES[code]) return CODE_MESSAGES[code]
  return null
}
