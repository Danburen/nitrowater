const FP_KEY = 'nw_device_fp'

/** Returns the stable browser fingerprint used by the SSO login / register payloads. */
export function getDeviceFp(): string {
  let value: string | null
  try {
    value = localStorage.getItem(FP_KEY)
  } catch {
    value = null
  }

  if (!value) {
    value =
      typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID().replace(/-/g, '').slice(0, 16)
        : `nw${Date.now()}`
    try {
      localStorage.setItem(FP_KEY, value)
    } catch {
      // Private mode / storage disabled: keep the in-memory value for this page.
    }
  }

  return value
}
