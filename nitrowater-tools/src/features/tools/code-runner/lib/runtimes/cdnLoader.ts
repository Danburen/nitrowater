/**
 * Lazy CDN loader for classic scripts.
 *
 * Each URL is fetched at most once (results are memoised as promises), so multiple callers
 * that race to run the first snippet share a single `<script>` download. Failed loads are
 * evicted from the cache so the user can retry.
 */
const scriptPromises = new Map<string, Promise<void>>()

/** Snapshot of what the CDN scripts expose on `window`, keyed by global name. */
export function hasGlobal(name: string): boolean {
  return typeof (window as unknown as Record<string, unknown>)[name] !== 'undefined'
}

export function loadScriptOnce(url: string, isLoaded?: () => boolean): Promise<void> {
  if (isLoaded?.()) return Promise.resolve()

  const existing = scriptPromises.get(url)
  if (existing) return existing

  const promise = new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = url
    script.async = true
    script.crossOrigin = 'anonymous'
    script.onload = () => resolve()
    script.onerror = () => {
      scriptPromises.delete(url)
      script.remove()
      reject(new Error(`CDN 脚本加载失败：${url}`))
    }
    document.head.appendChild(script)
  })

  scriptPromises.set(url, promise)
  return promise
}
