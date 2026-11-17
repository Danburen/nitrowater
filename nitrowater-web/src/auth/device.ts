/**
 * Stable per-browser device id.
 *
 * The BFF binds a session to the fingerprint it is given at login and compares it on every data
 * call (`X-Device-Fp`), so a cookie copied to another browser is rejected even while the session id
 * is still valid. The value is a random id, not a browser fingerprint: it is cheap, needs no
 * permissions, and the threat model already accepts that a script can read/forge it.
 *
 * <p>The id is mirrored into a first-party cookie for two reasons:</p>
 *
 * <ul>
 *   <li>top-level navigations (resource downloads opened with {@code window.open}, {@code <a
 *       download>}) cannot set a custom header, and the BFF accepts the cookie as a fallback;</li>
 *   <li>{@code localStorage} can be unavailable (private mode, enterprise policy). The cookie
 *       survives the full-page login redirect, so the browser does not bind the session to one id
 *       and then present another one on the very next API call (which would loop on
 *       {@code device_changed}).</li>
 * </ul>
 *
 * Keep this id shape and the cookie name in sync with the BFF's `[A-Za-z0-9-]{8,128}` check and
 * `SessionGuardFilter.DEVICE_COOKIE`.
 */
const FP_KEY = 'nitrowater:deviceFp'
const FP_COOKIE = 'nitrowater_device_fp'

/** Last-resort id for when both localStorage and cookies are blocked (kept per page). */
let memoryFp: string | null = null

function newId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `nw${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'))
  return match ? decodeURIComponent(match[1]) : null
}

function writeCookie(name: string, value: string): void {
  // `Secure` only on TLS: plain-http dev would silently drop the cookie otherwise.
  const secure = window.location.protocol === 'https:' ? '; Secure' : ''
  document.cookie = `${name}=${encodeURIComponent(value)}; Path=/; Max-Age=31536000; SameSite=Lax${secure}`
}

/** @returns the device id, creating it on first use */
export function deviceFp(): string {
  try {
    const existing = window.localStorage.getItem(FP_KEY)
    if (existing) {
      // Sessions created before the cookie existed only have localStorage; mirror it once so
      // header-less top-level navigations can present the same value.
      if (readCookie(FP_COOKIE) !== existing) writeCookie(FP_COOKIE, existing)
      return existing
    }
  } catch {
    // localStorage unavailable: the cookie fallback below is stable across the login redirect.
  }

  const fromCookie = readCookie(FP_COOKIE)
  if (fromCookie) {
    try {
      window.localStorage.setItem(FP_KEY, fromCookie)
    } catch {
      // Storage still blocked; the cookie alone is enough.
    }
    return fromCookie
  }

  const value = memoryFp ?? newId()
  memoryFp = value
  try {
    window.localStorage.setItem(FP_KEY, value)
  } catch {
    // Keep the in-memory value; a completely storage-less browser cannot keep a session anyway.
  }
  writeCookie(FP_COOKIE, value)
  return value
}
