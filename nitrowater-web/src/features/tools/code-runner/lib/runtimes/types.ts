/** Options accepted by every local runtime's `run` entry point. */
export interface RunOptions {
  /** Raw text exposed to the snippet via `input()` / `readline()`. */
  stdin?: string
  /** Per-execution timeout in milliseconds (defaults to `DEFAULT_RUN_TIMEOUT_MS`). */
  timeoutMs?: number
}
