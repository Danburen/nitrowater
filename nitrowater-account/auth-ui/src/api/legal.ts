/**
 * Public legal documents (`/api/public/legal/**`).
 *
 * Same-origin and anonymous: the account service reads the texts from the mounted content
 * directory on every request (`text/plain`, `no-store`), so a corrected clause appears on the next
 * load — no rebuild, no restart, no cache to bust.
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
 * <p>A 404 is reported as `not-published` rather than a generic failure: the file is host state,
 * so "missing" means the operator has not uploaded it on this deployment yet — which is worth
 * saying differently from a network error.</p>
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
