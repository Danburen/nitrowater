/**
 * Shared wire contract of every business API.
 *
 * Mirrors the backend: `cn.nitrowater.lib.api.ApiResponse` wraps every JSON response (the services
 * do it through `ApiResponseAdvice`), and Spring Data's `Page` is serialized with
 * `pageSerializationMode = VIA_DTO`, i.e. `content` plus a `page` metadata object — never the
 * `PageImpl` internals (`pageable`/`sort`/`first`/`last`).
 *
 * Declarations only: runtime helpers live in `./envelope` (unwrap/readJson) and `./http`.
 */

/** Envelope every business endpoint answers with. */
export interface ApiResponse<T> {
  success: boolean
  code: string
  message: string
  data: T
}

/** Pagination metadata of a Spring Data page. */
export interface PageMeta {
  size: number
  /** Zero-based page index. */
  number: number
  totalElements: number
  totalPages: number
}

/** One page of items as delivered inside {@code ApiResponse.data}. */
export interface Page<T> {
  content: T[]
  page: PageMeta
}
