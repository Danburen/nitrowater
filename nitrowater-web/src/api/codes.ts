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
  /** The AI feature is switched off. */
  AiDisabled: 'ai_disabled',
  /** The requested AI mode is switched off. */
  AiModeDisabled: 'ai_mode_disabled',
  /** The content gate blocked the request. */
  ContentBlocked: 'content_blocked',
  /** The content gate put the caller on cooldown. */
  ContentCooldown: 'content_cooldown',
  /** User uploads are disabled on this deployment. */
  UploadsDisabled: 'uploads_disabled',
  /** The internal endpoint is not reachable from this network. */
  InternalForbidden: 'internal_forbidden',
  /** The storage backend failed. */
  StorageError: 'storage_error',
} as const

/** Union of every known response code. */
export type ResponseCode = (typeof ResponseCode)[keyof typeof ResponseCode]
