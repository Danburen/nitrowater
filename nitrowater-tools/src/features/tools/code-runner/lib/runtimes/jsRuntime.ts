/**
 * JavaScript runtime.
 *
 * Runs on the browser's native engine inside the sandbox Worker — no download is required,
 * so "loading" is instantaneous. `ensure` exists only to keep a uniform shape across all
 * local runtimes (and to surface a one-line status to the UI).
 */
import { stepDone, stepStart, type ProgressReporter } from './progress'
import type { RunOptions } from './types'
import { runInSandbox } from './workerSandbox'

let ready = false

export function isJsRuntimeReady(): boolean {
  return ready
}

export async function ensureJsRuntime(onProgress?: ProgressReporter): Promise<void> {
  stepStart(onProgress, 'js-engine', 'JavaScript 引擎（浏览器原生）')
  ready = true
  stepDone(onProgress, 'js-engine')
}

export function runJavaScriptCode(code: string, options: RunOptions = {}): Promise<string> {
  return runInSandbox({
    mode: 'js',
    code,
    stdin: options.stdin,
    timeoutMs: options.timeoutMs,
  })
}
