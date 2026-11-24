/**
 * Editor content cache — memory + batched localStorage persistence.
 *
 * Ported from wtools (MIT) `src/utils/contentCache.ts`.
 *
 * Strategy:
 *   - in-memory Map  → zero-latency restore when switching tools/routes
 *   - localStorage   → written on explicit save() or flushMemoryCache() (timer / before unload),
 *                      NOT on every keystroke, to avoid fighting IME autocompletion.
 */
import { ref, watch, type Ref } from 'vue'

const CACHE_PREFIX = 'nitrowater-editor-'

// in-process memory cache (survives route switches within the session)
const memoryCache = new Map<string, string>()

export function getCache(key: string): string | null {
  if (memoryCache.has(key)) return memoryCache.get(key)!

  const raw = localStorage.getItem(CACHE_PREFIX + key)
  if (raw !== null) {
    try {
      const parsed: unknown = JSON.parse(raw)
      if (typeof parsed === 'string') {
        memoryCache.set(key, parsed)
        return parsed
      }
    } catch {
      localStorage.removeItem(CACHE_PREFIX + key)
    }
  }
  return null
}

export function setCache(key: string, value: string): void {
  memoryCache.set(key, value)
  try {
    localStorage.setItem(CACHE_PREFIX + key, JSON.stringify(value))
  } catch {
    // Quota exceeded — the memory cache still works this session.
  }
}

export function clearCache(key: string): void {
  memoryCache.delete(key)
  localStorage.removeItem(CACHE_PREFIX + key)
}

/** Flush every in-memory entry to localStorage (called on a timer / before unload). */
export function flushMemoryCache(): void {
  for (const [key, value] of memoryCache.entries()) {
    try {
      localStorage.setItem(CACHE_PREFIX + key, JSON.stringify(value))
    } catch {
      // Quota exceeded — keep the memory cache.
    }
  }
}

export interface UseContentCacheReturn {
  /** The auto-persisting ref. Bind it with v-model or use directly. */
  content: Ref<string>
  /** True when cached content was restored (from memory or localStorage). */
  isRestored: boolean
  /** Reset to defaultValue and clear the cache. */
  clear: () => void
  /** Immediately persist current content (memory + localStorage). */
  save: () => void
}

/**
 * Create a `ref<string>` that preserves to memory on every change and persists to localStorage
 * on explicit save() or a global flush.
 */
export function useContentCache(key: string, defaultValue: string): UseContentCacheReturn {
  const cached = getCache(key)
  const isRestored = cached !== null
  const content = ref(cached ?? defaultValue)

  watch(content, (value) => {
    memoryCache.set(key, value)
  })

  function clear(): void {
    content.value = defaultValue
    clearCache(key)
  }

  function save(): void {
    setCache(key, content.value)
  }

  return { content, isRestored, clear, save }
}
