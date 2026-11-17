/**
 * Public legal documents (`/api/public/legal/**`).
 *
 * The index is the source of truth: the deployment decides how many documents exist and the UI
 * renders them in the order they arrive, instead of a hard-coded "privacy + terms" pair. Bodies stay
 * `text/plain` and are re-read per request (`no-store`). The version date is the file's modification
 * time, delivered with the index. The SSO host serves none of this.
 */
import { readJson } from './envelope'

/** One published document, as described by the index. */
export interface LegalDocument {
  /** Addressable key: lowercase letters, digits and hyphens (the file name without `.txt`). */
  key: string
  /** Short label for the footer link and the page heading. */
  title: string
  /** ISO-8601 instant: the document file's modification time, i.e. its version date. */
  updatedAt: string
}

/** Mirrors the server-side key whitelist; anything else is a 404, so don't request it. */
const KEY_PATTERN = /^[a-z0-9][a-z0-9-]{0,63}$/

/**
 * @param value route parameter or any unknown value
 * @returns whether it can be a document key
 */
export function isLegalKey(value: unknown): value is string {
  return typeof value === 'string' && KEY_PATTERN.test(value)
}

/**
 * Fetches the published documents.
 *
 * @returns the documents in display order (empty when none are uploaded)
 */
export async function fetchLegalIndex(): Promise<LegalDocument[]> {
  const response = await fetch('/api/public/legal', {
    headers: { Accept: 'application/json' },
    cache: 'no-store',
  })
  return readJson<LegalDocument[]>(response)
}

/**
 * Fetches one document as plain text.
 *
 * @param key document key from the index
 * @returns the current text
 * @throws Error with `not-published` on 404, otherwise `http-<status>`
 */
export async function fetchLegalText(key: string): Promise<string> {
  const response = await fetch(`/api/public/legal/${key}`, {
    headers: { Accept: 'text/plain' },
    cache: 'no-store',
  })
  if (!response.ok) {
    throw new Error(response.status === 404 ? 'not-published' : `http-${response.status}`)
  }
  return response.text()
}

/**
 * Formats the index timestamp as the version date shown under the heading.
 *
 * @param iso ISO-8601 instant from {@link LegalDocument.updatedAt}
 * @returns `YYYY-MM-DD` in the viewer's timezone, or an empty string when unparseable
 */
export function formatLegalDate(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}
