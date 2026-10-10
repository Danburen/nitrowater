/**
 * Unified facade over every browser-only runtime.
 *
 * The code-runner UI talks to this module exclusively, so adding a language is a matter of
 * wiring one more case here — the component never needs to know whether the engine is Pyodide,
 * a Worker, or a CDN compiler.
 */
import {
  ensureRuntime as ensurePyodide,
  isPyodideReady,
  runPythonCode,
} from '../pyodide/pyodideManager'
import { ensureJsRuntime, isJsRuntimeReady, runJavaScriptCode } from './jsRuntime'
import { ensureTsRuntime, isTsRuntimeReady, runTypeScriptCode } from './tsRuntime'
import { ensureLuaRuntime, isLuaRuntimeReady, runLuaCode } from './luaRuntime'
import type { RunOptions } from './types'
import type { LocalLanguageId } from '../../langCatalog'

export type { LocalLanguageId } from '../../langCatalog'
export type { RunOptions } from './types'

/** Whether the runtime for `language` has already been initialised this session. */
export function isLanguageReady(language: LocalLanguageId): boolean {
  switch (language) {
    case 'python':
      return isPyodideReady()
    case 'javascript':
      return isJsRuntimeReady()
    case 'typescript':
      return isTsRuntimeReady()
    case 'lua':
      return isLuaRuntimeReady()
  }
}

/** Lazily initialise (download + warm up) the runtime for `language`. */
export async function ensureLanguage(
  language: LocalLanguageId,
  onStatus?: (message: string) => void,
): Promise<void> {
  switch (language) {
    case 'python':
      return ensurePyodide(onStatus)
    case 'javascript':
      return ensureJsRuntime(onStatus)
    case 'typescript':
      return ensureTsRuntime(onStatus)
    case 'lua':
      return ensureLuaRuntime(onStatus)
  }
}

/** Execute `code` with the runtime for `language` and resolve with combined stdout/stderr. */
export function runLocalCode(
  language: LocalLanguageId,
  code: string,
  options: RunOptions = {},
): Promise<string> {
  switch (language) {
    case 'python':
      return runPythonCode(code, options)
    case 'javascript':
      return runJavaScriptCode(code, options)
    case 'typescript':
      return runTypeScriptCode(code, options)
    case 'lua':
      return runLuaCode(code, options)
  }
}
