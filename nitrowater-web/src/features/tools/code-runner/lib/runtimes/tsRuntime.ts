/**
 * TypeScript runtime.
 *
 * TypeScript has no browser engine of its own, so we load the official `typescript` package
 * from a CDN (lazy, on first run), transpile the snippet with `ts.transpileModule` (types are
 * erased, not type-checked), then hand the emitted JavaScript to the JS sandbox.
 *
 * Emit diagnostics ARE surfaced, so genuine syntax problems still surface as `TSxxxx` errors —
 * but semantic type errors (the ones a real `tsc` would report) are intentionally ignored,
 * matching the behaviour of an online playground.
 */
import { loadScriptOnce } from './cdnLoader'
import { runJavaScriptCode, ensureJsRuntime, isJsRuntimeReady } from './jsRuntime'
import type { RunOptions } from './types'

const TS_CDN_URL = 'https://cdn.jsdelivr.net/npm/typescript@5.9.2/lib/typescript.js'
const ANSI_RED = '\u001b[31m'
const ANSI_RESET = '\u001b[0m'

interface TsPosition {
  line: number
  character: number
}

interface TsDiagnostic {
  category: number
  code: number
  messageText: string | object
  file?: { getLineAndCharacterOfPosition(position: number): TsPosition }
  start?: number
}

interface TsTranspileResult {
  outputText: string
  diagnostics?: TsDiagnostic[]
}

interface TypeScriptGlobal {
  ScriptTarget: Record<string, number>
  ModuleKind: Record<string, number>
  DiagnosticCategory: { Error: number }
  transpileModule(
    source: string,
    options: {
      compilerOptions: Record<string, unknown>
      reportDiagnostics: boolean
      fileName: string
    },
  ): TsTranspileResult
  flattenDiagnosticMessageText(text: string | object, newLine: string): string
}

let ready = false

export function isTsRuntimeReady(): boolean {
  return ready
}

function getTypeScript(): TypeScriptGlobal | undefined {
  return (window as unknown as { ts?: TypeScriptGlobal }).ts
}

export async function ensureTsRuntime(onStatus?: (message: string) => void): Promise<void> {
  if (ready) {
    onStatus?.('TypeScript 编译器已就绪')
    return
  }
  if (!isJsRuntimeReady()) await ensureJsRuntime()

  onStatus?.('正在从 CDN 下载 TypeScript 编译器（约 9 MB）…')
  await loadScriptOnce(TS_CDN_URL, () => getTypeScript() !== undefined)
  if (!getTypeScript()) throw new Error('TypeScript 编译器加载失败')

  onStatus?.('TypeScript 编译器就绪')
  ready = true
}

function describeDiagnostic(ts: TypeScriptGlobal, diagnostic: TsDiagnostic): string {
  let location = ''
  if (diagnostic.file && typeof diagnostic.start === 'number') {
    try {
      const position = diagnostic.file.getLineAndCharacterOfPosition(diagnostic.start)
      location = ` (${position.line + 1}:${position.character + 1})`
    } catch {
      location = ''
    }
  }
  const message = ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n')
  return `TS${diagnostic.code}${location}: ${message}`
}

export async function runTypeScriptCode(code: string, options: RunOptions = {}): Promise<string> {
  const ts = getTypeScript()
  if (!ts) throw new Error('TypeScript 编译器尚未加载，请先运行一次以初始化')

  const transpiled = ts.transpileModule(code, {
    compilerOptions: {
      target: ts.ScriptTarget.ES2020,
      module: ts.ModuleKind.None,
      removeComments: false,
      inlineSourceMap: false,
    },
    reportDiagnostics: true,
    fileName: 'snippet.ts',
  })

  const errors = (transpiled.diagnostics ?? []).filter(
    (diagnostic) => diagnostic.category === ts.DiagnosticCategory.Error,
  )
  if (errors.length > 0) {
    const details = errors.map((diagnostic) => describeDiagnostic(ts, diagnostic)).join('\n')
    return `${ANSI_RED}[编译错误]\n${details}${ANSI_RESET}`
  }

  return runJavaScriptCode(transpiled.outputText, options)
}
