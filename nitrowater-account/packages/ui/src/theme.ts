import { readonly, ref } from 'vue'

/** Resolved theme actually applied to the DOM. */
export type Theme = 'light' | 'dark'

export interface ThemeOptions {
  /**
   * localStorage key. Must match the FOUC-prevention inline script in the app's index.html,
   * so the first paint and the runtime controller agree.
   */
  storageKey: string
}

const lightQuery =
  typeof window !== 'undefined' ? window.matchMedia('(prefers-color-scheme: light)') : null

const systemTheme = (): Theme => (lightQuery?.matches ? 'light' : 'dark')

let storageKey = 'nitrowater-theme'
let explicit = false
let listening = false

// Module-level singleton: one reactive theme per app bundle. The admin console and the
// hosted auth pages are separate bundles, so each configures its own persistence key.
const theme = ref<Theme>(systemTheme())

function readStored(): Theme | null {
  try {
    const value = localStorage.getItem(storageKey)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    // Storage can be unavailable (private mode / disabled cookies); fall back to system.
    return null
  }
}

function apply(value: Theme): void {
  const root = document.documentElement
  root.dataset.theme = value
  root.style.colorScheme = value
}

/**
 * Bind the controller to an app-specific persistence key.
 * Call once at bootstrap, before {@link initTheme}.
 */
export function configureTheme(options: ThemeOptions): void {
  storageKey = options.storageKey
  const stored = readStored()
  explicit = stored !== null
  theme.value = stored ?? systemTheme()
}

/** Apply a theme and persist it as the user's explicit choice. */
export function setTheme(value: Theme): void {
  explicit = true
  theme.value = value
  try {
    localStorage.setItem(storageKey, value)
  } catch {
    // Persistence is best-effort; the in-memory value still applies.
  }
  apply(value)
}

/** Flip between light and dark. */
export function toggleTheme(): void {
  setTheme(theme.value === 'dark' ? 'light' : 'dark')
}

/**
 * Sync the DOM with the resolved theme and keep following the OS preference until the user
 * makes an explicit choice. Call once at bootstrap.
 */
export function initTheme(): void {
  apply(theme.value)
  if (listening || !lightQuery) return
  listening = true
  lightQuery.addEventListener('change', (event) => {
    if (explicit) return
    theme.value = event.matches ? 'light' : 'dark'
    apply(theme.value)
  })
}

export function useTheme() {
  return { theme: readonly(theme), setTheme, toggleTheme }
}
