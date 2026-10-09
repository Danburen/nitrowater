import { readonly, ref } from 'vue'

/** Resolved theme actually applied to the DOM. */
export type Theme = 'light' | 'dark'

/** Must match the key used by the FOUC-prevention script in index.html. */
const STORAGE_KEY = 'nitrowater-theme'
const lightQuery =
  typeof window !== 'undefined' ? window.matchMedia('(prefers-color-scheme: light)') : null

const systemTheme = (): Theme => (lightQuery?.matches ? 'light' : 'dark')

function readStored(): Theme | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    // Storage can be unavailable (private mode / disabled cookies); fall back to system.
    return null
  }
}

// Module-level singleton: one shared reactive theme for the whole app.
// Initial value mirrors the inline head script so hydration stays consistent.
const theme = ref<Theme>(readStored() ?? systemTheme())
let explicit = readStored() !== null

function apply(value: Theme): void {
  const root = document.documentElement
  root.dataset.theme = value
  root.style.colorScheme = value
}

/** Apply a theme and persist it as the user's explicit choice. */
export function setTheme(value: Theme): void {
  explicit = true
  theme.value = value
  try {
    localStorage.setItem(STORAGE_KEY, value)
  } catch {
    // Ignore persistence failures; the in-memory value still applies.
  }
  apply(value)
}

/** Flip between light and dark. */
export function toggleTheme(): void {
  setTheme(theme.value === 'dark' ? 'light' : 'dark')
}

/**
 * Sync the DOM with the resolved theme. Call once at bootstrap.
 * While the user has not made an explicit choice, keep following the OS preference.
 */
export function initTheme(): void {
  apply(theme.value)
  lightQuery?.addEventListener('change', (event) => {
    if (explicit) return
    theme.value = event.matches ? 'light' : 'dark'
    apply(theme.value)
  })
}

export function useTheme() {
  return { theme: readonly(theme), setTheme, toggleTheme }
}
