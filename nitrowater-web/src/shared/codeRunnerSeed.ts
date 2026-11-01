/**
 * One-shot handoff from a chat code block to the code-runner tool.
 *
 * <p>The payload goes through {@code sessionStorage} instead of the URL: source can be several
 * kilobytes, and putting it in the query string would pollute history, logs and referrers. The
 * seed is consumed exactly once, so revisiting the runner never overwrites a newer draft.</p>
 */
export interface RunnerSeed {
  /** Runner language id ({@code python}, {@code java}, {@code cpp}, …). */
  language: string
  code: string
}

const KEY = 'nitrowater:code-runner:seed'
/** Guard against a hand-crafted entry bloating the sender's storage. */
const MAX_CODE_CHARS = 200_000

/**
 * @param seed language + source to preload
 * @return whether the seed was stored (false when storage is blocked, e.g. private mode)
 */
export function writeRunnerSeed(seed: RunnerSeed): boolean {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(seed))
    return true
  } catch {
    return false
  }
}

/** Reads and immediately removes the seed; malformed entries are discarded. */
export function consumeRunnerSeed(): RunnerSeed | null {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return null
    sessionStorage.removeItem(KEY)
    const parsed = JSON.parse(raw) as Partial<RunnerSeed>
    if (typeof parsed.language !== 'string' || typeof parsed.code !== 'string') return null
    if (!parsed.language.trim() || !parsed.code.trim() || parsed.code.length > MAX_CODE_CHARS) return null
    return { language: parsed.language, code: parsed.code }
  } catch {
    return null
  }
}
