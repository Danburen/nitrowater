/**
 * Public legal documents (`/api/public/legal/**`).
 *
 * Same-origin and anonymous: the BFF reads `privacy.txt` / `terms.txt` from the mounted content
 * directory on every request (`text/plain`, `no-store`), so a corrected clause appears on the next
 * load. The SSO account service serves the same two files at the same path on its own origin.
 */
export type LegalDocument = 'privacy' | 'terms'

/** Page titles; the files themselves carry their own heading. */
export const LEGAL_TITLES: Record<LegalDocument, string> = {
  privacy: '隐私政策',
  terms: '用户协议',
}

/** Narrows a route parameter (or any unknown value) to a supported document name. */
export function isLegalDocument(value: unknown): value is LegalDocument {
  return value === 'privacy' || value === 'terms'
}

/**
 * Fetches one document as plain text.
 *
 * @param document whitelisted document name
 * @returns the current text
 * @throws Error with `not-published` on 404, otherwise `http-<status>`
 */
export async function fetchLegalText(document: LegalDocument): Promise<string> {
  const response = await fetch(`/api/public/legal/${document}`, {
    headers: { Accept: 'text/plain' },
    cache: 'no-store',
  })
  if (!response.ok) {
    throw new Error(response.status === 404 ? 'not-published' : `http-${response.status}`)
  }
  return response.text()
}
