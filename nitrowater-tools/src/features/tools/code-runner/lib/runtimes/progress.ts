/**
 * Structured warm-up progress reported by the browser-local runtimes.
 *
 * Each step has a stable key, so the UI renders exactly one line per step: a spinning gear while it
 * runs, a check when it finishes. `detail` is live sub-text (downloaded bytes, package name, ...)
 * and never creates an extra line.
 */
export interface RuntimeStep {
  key: string
  label: string
}

export type RuntimeProgress =
  | { type: 'start'; step: RuntimeStep }
  | { type: 'detail'; key: string; detail: string }
  | { type: 'done'; key: string }
  | { type: 'error'; key: string; message: string }

export type ProgressReporter = (event: RuntimeProgress) => void

/** Small helpers so runtime modules stay focused on their own work. */
export function stepStart(reporter: ProgressReporter | undefined, key: string, label: string): void {
  reporter?.({ type: 'start', step: { key, label } })
}

export function stepDetail(
  reporter: ProgressReporter | undefined,
  key: string,
  detail: string,
): void {
  reporter?.({ type: 'detail', key, detail })
}

export function stepDone(reporter: ProgressReporter | undefined, key: string): void {
  reporter?.({ type: 'done', key })
}

export function stepError(
  reporter: ProgressReporter | undefined,
  key: string,
  message: string,
): void {
  reporter?.({ type: 'error', key, message })
}
