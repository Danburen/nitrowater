/**
 * Blob-based Web Worker sandbox used by the JavaScript, TypeScript and Lua runtimes.
 *
 * Running generated code in a dedicated Worker (built from an in-page Blob, no extra file)
 * gives us three things a main-thread `eval` cannot:
 *   1. a hard timeout — an infinite loop is killed with `worker.terminate()`;
 *   2. output capture without polluting the page (`console.*` is shadowed, Lua's `print`
 *      is replaced);
 *   3. a clean slate on every run (a fresh Worker per execution).
 *
 * The worker speaks a tiny protocol back to the host:
 *   { type: 'out', text }  → stdout chunk
 *   { type: 'err', text }  → stderr / runtime error chunk
 *   { type: 'done' }       → execution finished
 */
import { DEFAULT_RUN_TIMEOUT_MS } from './constants'

export type SandboxMode = 'js' | 'lua'

export interface SandboxRunOptions {
  mode: SandboxMode
  code: string
  /** Raw text made available to the snippet through `input()` / `readline()`. */
  stdin?: string
  timeoutMs?: number
}

const ANSI_RED = '\u001b[31m'
const ANSI_RESET = '\u001b[0m'

const WORKER_SOURCE = String.raw`
'use strict'

// ---- CDN endpoints (kept in sync with the host-side runtime modules) ----
var WASMOON_URL = 'https://cdn.jsdelivr.net/npm/wasmoon@1.16.0/dist/index.js'
var WASMOON_WASM_URL = 'https://cdn.jsdelivr.net/npm/wasmoon@1.16.0/dist/glue.wasm'

function post(type, text) {
  self.postMessage({ type: type, text: text })
}

// Pretty-print a single value the way a console would (strings stay bare at the top level).
function formatValue(value, depth) {
  depth = depth || 0
  if (value === null) return 'null'
  if (value === undefined) return 'undefined'
  var type = typeof value
  if (type === 'string') return depth === 0 ? value : JSON.stringify(value)
  if (type === 'number' || type === 'boolean' || type === 'bigint') return String(value)
  if (type === 'function') return value.name ? '[Function: ' + value.name + ']' : '[Function]'
  if (type === 'symbol') return value.toString()
  if (value instanceof Error) return value.stack || value.name + ': ' + value.message
  if (depth > 3) return Array.isArray(value) ? '[Array]' : '[Object]'
  try {
    return JSON.stringify(value, function (key, item) {
      if (typeof item === 'bigint') return item.toString() + 'n'
      if (typeof item === 'function') return '[Function]'
      if (item === undefined) return '[undefined]'
      return item
    })
  } catch (error) {
    return String(value)
  }
}

function joinArgs(args) {
  var parts = []
  for (var i = 0; i < args.length; i++) parts.push(formatValue(args[i], 0))
  return parts.join(' ')
}

function formatError(error) {
  if (error && error.stack) return String(error.stack)
  return String(error)
}

// Console replacement: stdout for log/info/debug, stderr for warn/error.
var sandboxConsole = {
  log: function () { post('out', joinArgs(arguments) + '\n') },
  info: function () { post('out', joinArgs(arguments) + '\n') },
  debug: function () { post('out', joinArgs(arguments) + '\n') },
  warn: function () { post('err', joinArgs(arguments) + '\n') },
  error: function () { post('err', joinArgs(arguments) + '\n') },
}

async function runJs(code, makeInput) {
  // AsyncFunction tolerates top-level await and top-level return.
  var AsyncFunction = Object.getPrototypeOf(async function () {}).constructor
  var runner = new AsyncFunction('console', 'print', 'input', 'readline', code)
  var print = function () { post('out', joinArgs(arguments) + '\n') }
  var result = await runner(sandboxConsole, print, makeInput, makeInput)
  if (result !== undefined) post('out', formatValue(result, 0) + '\n')
}

async function runLua(code, makeInput) {
  importScripts(WASMOON_URL)
  var wasmoon = self.wasmoon
  if (!wasmoon || !wasmoon.LuaFactory) throw new Error('wasmoon 未能正确加载')
  var factory = new wasmoon.LuaFactory(WASMOON_WASM_URL)
  var engine = await factory.createEngine()
  try {
    engine.global.set('print', function () { post('out', joinArgs(arguments) + '\n') })
    engine.global.set('io_read', function () { return makeInput() })
    await engine.doString(code)
  } finally {
    try { engine.global.close() } catch (error) { /* engine already torn down */ }
  }
}

self.onmessage = async function (event) {
  var data = event.data || {}
  var stdinText = typeof data.stdin === 'string' ? data.stdin : ''
  var lines = stdinText.length > 0 ? stdinText.split(/\r?\n/) : []
  var cursor = 0
  function makeInput() {
    return cursor < lines.length ? lines[cursor++] : null
  }

  try {
    if (data.mode === 'lua') {
      await runLua(data.code || '', makeInput)
    } else {
      await runJs(data.code || '', makeInput)
    }
    post('done', '')
  } catch (error) {
    post('err', formatError(error))
    post('done', '')
  }
}
`

let workerUrl: string | null = null

function getWorkerUrl(): string {
  if (workerUrl === null) {
    const blob = new Blob([WORKER_SOURCE], { type: 'application/javascript' })
    workerUrl = URL.createObjectURL(blob)
  }
  return workerUrl
}

/**
 * Execute a snippet in an isolated Worker and resolve with its combined stdout/stderr.
 * Never rejects: engine errors and timeouts are folded into the returned text.
 */
export function runInSandbox(options: SandboxRunOptions): Promise<string> {
  const { mode, code, stdin = '', timeoutMs = DEFAULT_RUN_TIMEOUT_MS } = options

  return new Promise<string>((resolve) => {
    let worker: Worker
    try {
      worker = new Worker(getWorkerUrl())
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      resolve(`${ANSI_RED}[运行错误] 无法创建运行沙箱：${message}${ANSI_RESET}`)
      return
    }

    let output = ''
    let settled = false

    const finish = (suffix = '') => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      worker.terminate()
      resolve(output + suffix)
    }

    const timer = setTimeout(() => {
      finish(`\n${ANSI_RED}[超时] 运行超过 ${Math.round(timeoutMs / 1000)} 秒，已终止（可能存在死循环）${ANSI_RESET}`)
    }, timeoutMs)

    worker.onmessage = (event: MessageEvent<{ type: string; text?: string }>) => {
      const message = event.data
      switch (message.type) {
        case 'out':
          output += message.text ?? ''
          break
        case 'err':
          output += `${ANSI_RED}${message.text ?? ''}${ANSI_RESET}`
          break
        case 'done':
          finish()
          break
        default:
          break
      }
    }

    worker.onerror = (event) => {
      finish(`\n${ANSI_RED}[运行错误] ${event.message || 'Worker 执行异常'}${ANSI_RESET}`)
    }

    worker.postMessage({ mode, code, stdin })
  })
}
