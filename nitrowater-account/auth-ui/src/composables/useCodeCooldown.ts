import { onUnmounted, ref } from 'vue'

/**
 * Verification-code resend cooldown shared by every auth screen.
 *
 * <p>The backend enforces a per-target 1-minute window and answers a generic 429. This composable
 * keeps the UI on the same clock: the deadline is stored per `channel/target`, so a reload, a
 * second tab or the two-step re-auth dialog picks the countdown back up instead of resetting to
 * zero and hammering the server. The scene is kept in the scope for readability only; the backend
 * bucket does not include it.</p>
 */

export interface CooldownScope {
  /** `login`, `register`, `reset_password`, … */
  scene: string
  /** `sms` / `email` (case-insensitive). */
  channel: string
  /** Raw phone/email, or a stable placeholder when the server masks it. */
  target: string
}

const STORAGE_PREFIX = 'nitrowater:code-cooldown:'
const TICK_MS = 1000

function storageKey(scope: CooldownScope): string {
  const target = scope.target.trim().toLowerCase()
  // The backend buckets the 1-minute window by channel + target only (not by scene), so the UI
  // must share one deadline across login/register/forgot-password for the same target.
  return `${STORAGE_PREFIX}${scope.channel.toLowerCase()}:${target}`
}

function readDeadline(key: string): number {
  try {
    const raw = localStorage.getItem(key)
    const value = raw === null ? 0 : Number(raw)
    return Number.isFinite(value) ? value : 0
  } catch {
    return 0
  }
}

function remainingSeconds(key: string): number {
  const deadline = readDeadline(key)
  return deadline > 0 ? Math.max(0, Math.ceil((deadline - Date.now()) / TICK_MS)) : 0
}

/** Reads the generic 429 the server sends for every limiter bucket. */
function isRateLimited(error: unknown): boolean {
  if (typeof error !== 'object' || error === null) return false
  const candidate = error as { status?: unknown; code?: unknown }
  return candidate.status === 429
    || (typeof candidate.code === 'string' && candidate.code.includes('TOO_MANY_REQUEST'))
}

/**
 * @param defaultSeconds fallback used when a 429 carries no remaining-seconds metadata
 */
export function useCodeCooldown(defaultSeconds = 60) {
  const remaining = ref(0)
  let timer: ReturnType<typeof setTimeout> | null = null
  let activeKey = ''

  function stop(): void {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
  }

  function clearStore(key = activeKey): void {
    if (!key) return
    try {
      localStorage.removeItem(key)
    } catch {
      // Storage can be unavailable (private mode); the in-memory countdown still works.
    }
  }

  function tick(): void {
    const left = remainingSeconds(activeKey)
    remaining.value = left
    if (left <= 0) {
      stop()
      clearStore()
      return
    }
    timer = setTimeout(tick, TICK_MS)
  }

  function begin(key: string, seconds: number): void {
    const value = Math.max(1, Math.ceil(seconds))
    activeKey = key
    try {
      localStorage.setItem(key, String(Date.now() + value * TICK_MS))
    } catch {
      // Keep the visible countdown even if persistence is unavailable.
    }
    remaining.value = value
    stop()
    timer = setTimeout(tick, TICK_MS)
  }

  /** Starts (or restarts) the countdown for a scope. */
  function start(seconds: number, scope: CooldownScope): void {
    begin(storageKey(scope), seconds)
  }

  /** Restores a persisted deadline when the screen mounts or the target/channel changes. */
  function restore(scope: CooldownScope): void {
    const key = storageKey(scope)
    const left = remainingSeconds(key)
    stop()
    activeKey = key
    remaining.value = left
    if (left > 0) timer = setTimeout(tick, TICK_MS)
  }

  /** Clears the countdown for the active scope. */
  function clear(): void {
    stop()
    clearStore()
    remaining.value = 0
  }

  /** Synchronizes the countdown from a failed send (429) without hiding the error message. */
  function sync(error: unknown, scope: CooldownScope): void {
    if (isRateLimited(error)) {
      start(defaultSeconds, scope)
    }
  }

  onUnmounted(stop)

  return { remaining, start, restore, clear, sync }
}
