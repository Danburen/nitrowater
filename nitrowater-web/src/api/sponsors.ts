/**
 * Public sponsor list for the About page (`/api/public/sponsors`).
 *
 * The BFF serves this endpoint directly and the business service caches each page in Redis, so an
 * anonymous visitor can page through sponsors without a session. The wire shape is the shared
 * Spring Data `Page`, zero-based on the request side.
 */
import { getJson } from './http'
import type { Page } from './types'

/** One sponsor, projected server-side from the ifdian response. */
export interface Sponsor {
  userId: string
  nickname: string
  avatarUrl: string
  lastSponsorTime: string
}

/**
 * @param page zero-based page number
 * @returns one page of sponsors
 */
export function fetchSponsors(page: number): Promise<Page<Sponsor>> {
  return getJson<Page<Sponsor>>(`/api/public/sponsors?page=${page}`)
}
