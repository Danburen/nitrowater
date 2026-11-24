import type { ProgressReporter } from './progress'

/** Options accepted by every local runtime's `run` entry point. */
export interface RunOptions {
  /** Raw text exposed to the snippet via `input()` / `readline()`. */
  stdin?: string
  /** Per-execution timeout in milliseconds (defaults to `DEFAULT_RUN_TIMEOUT_MS`). */
  timeoutMs?: number
  /** Receives lazy dependency-loading steps (e.g. Python packages) during the run. */
  onProgress?: ProgressReporter
}
