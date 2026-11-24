/**
 * Lua runtime.
 *
 * Lua 5.4 runs on WebAssembly via [wasmoon]. The VM (`glue.wasm`) is instantiated inside the
 * sandbox Worker on each run — `ensure` merely warms the browser HTTP cache by preloading the
 * wasmoon loader script from the CDN, so the worker's `importScripts` is effectively free.
 *
 * Output is captured by replacing Lua's global `print`; an infinite loop is killed by the
 * Worker timeout (`worker.terminate()`), which is far more reliable than cooperative
 * instruction-count interrupts.
 */
import { loadScriptOnce, hasGlobal } from './cdnLoader'
import { stepDone, stepStart, type ProgressReporter } from './progress'
import { runInSandbox } from './workerSandbox'
import type { RunOptions } from './types'

const WASMOON_URL = 'https://cdn.jsdelivr.net/npm/wasmoon@1.16.0/dist/index.js'

let ready = false

export function isLuaRuntimeReady(): boolean {
  return ready
}

export async function ensureLuaRuntime(onProgress?: ProgressReporter): Promise<void> {
  if (ready) return
  stepStart(onProgress, 'lua-vm', 'Lua 运行时（wasmoon / WebAssembly）')
  await loadScriptOnce(WASMOON_URL, () => hasGlobal('wasmoon'))
  stepDone(onProgress, 'lua-vm')
  ready = true
}

export function runLuaCode(code: string, options: RunOptions = {}): Promise<string> {
  return runInSandbox({
    mode: 'lua',
    code,
    stdin: options.stdin,
    timeoutMs: options.timeoutMs,
  })
}
