/**
 * Browser download / clipboard helpers shared by the local Minecraft tools.
 *
 * Everything here runs entirely in the browser: no network, no upload, no server round-trip.
 */

/** Trigger a download for a text payload (UTF-8, no BOM). */
export function downloadText(filename: string, text: string, mime = 'text/plain;charset=utf-8'): void {
  downloadBlob(filename, new Blob([text], { type: mime }))
}

/** Trigger a download for a binary payload. */
export function downloadBlob(filename: string, blob: Blob): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** Copy text to the clipboard; returns false when the browser denies clipboard access. */
export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}
