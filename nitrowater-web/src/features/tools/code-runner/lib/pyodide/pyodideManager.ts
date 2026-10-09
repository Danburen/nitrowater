/**
 * Pyodide runtime manager — IndexedDB-cached, CDN-loaded.
 *
 * Ported from wtools (MIT) `src/utils/pyodide/pyodideManager.ts` (IndexedDB cache, streaming
 * download, fetch interceptor, micropip auto-import). Simplified to Python-only (no CheerpJ).
 *
 * Flow:
 *   1. stream each core file from the CDN → DEFLATE → IndexedDB
 *   2. load the cached files into a memory map + install a fetch interceptor (cache-first, CDN
 *      fallback) so `loadPyodide()` never re-downloads
 *   3. load `pyodide.js` via <script>, then `loadPyodide({ indexURL })`
 */
import { CORE_CDN, LIBS_CDN, PRELOAD_FILES } from './config'
import { PACKAGE_NAME_MAP } from './packageNameMap'
import { compressUint8Array, decompressUint8Array } from '../zip'

// ──────────────────────────────────────────
//  Types
// ──────────────────────────────────────────

export interface PyodideInstance {
  runPython: (code: string) => unknown
  loadPackage: (name: string) => Promise<void>
  setStdin: (options: { stdin: () => string | null }) => void
  globals: {
    set: (key: string, value: unknown) => void
    get: (key: string) => unknown
  }
}

interface LoadPyodideWindow {
  loadPyodide: (options: { indexURL: string }) => Promise<PyodideInstance>
}

// ──────────────────────────────────────────
//  IndexedDB helpers
// ──────────────────────────────────────────

const DB_NAME = 'nitrowater-pyodide-cache-v1'
const STORE_NAME = 'files'

function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1)
    req.onupgradeneeded = () => {
      req.result.createObjectStore(STORE_NAME)
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

async function idbSet(key: string, data: Uint8Array): Promise<void> {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite')
    tx.objectStore(STORE_NAME).put(data, key)
    tx.oncomplete = () => {
      db.close()
      resolve()
    }
    tx.onerror = () => {
      db.close()
      reject(tx.error)
    }
  })
}

function idbGetAllEntries(): Promise<[string, Uint8Array][]> {
  return openDB().then(
    (db) =>
      new Promise<[string, Uint8Array][]>((resolve, reject) => {
        const tx = db.transaction(STORE_NAME, 'readonly')
        const req = tx.objectStore(STORE_NAME).openCursor()
        const entries: [string, Uint8Array][] = []
        req.onsuccess = () => {
          const cursor = req.result
          if (cursor) {
            entries.push([cursor.key as string, cursor.value as Uint8Array])
            cursor.continue()
          } else {
            db.close()
            resolve(entries)
          }
        }
        req.onerror = () => {
          db.close()
          reject(req.error)
        }
      }),
  )
}

/** Wipe the cached runtime files (next run re-downloads). */
export async function clearRuntimeCache(): Promise<void> {
  const db = await openDB()
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite')
    tx.objectStore(STORE_NAME).clear()
    tx.oncomplete = () => {
      db.close()
      resolve()
    }
    tx.onerror = () => {
      db.close()
      reject(tx.error)
    }
  })
  fileMap = null
}

// ──────────────────────────────────────────
//  Streaming download
// ──────────────────────────────────────────

export async function streamDownload(
  url: string,
  onProgress: (bytes: number) => void,
): Promise<Uint8Array> {
  const response = await fetch(url, { cache: 'no-cache' })
  if (!response.ok) throw new Error(`下载失败 (HTTP ${response.status})`)
  if (!response.body) throw new Error('下载失败：响应无流式内容')

  const reader = response.body.getReader()
  const chunks: Uint8Array[] = []
  let received = 0
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    chunks.push(value)
    received += value.length
    onProgress(received)
  }

  const combined = new Uint8Array(received)
  let pos = 0
  for (const chunk of chunks) {
    combined.set(chunk, pos)
    pos += chunk.length
  }
  return combined
}

/** Download every PRELOAD_FILES entry from the CDN and cache it (compressed) in IndexedDB. */
export async function downloadAndCacheFiles(
  onFileProgress: (name: string, bytes: number) => void,
  onFileDone: (name: string, index: number, total: number) => void,
): Promise<void> {
  for (let i = 0; i < PRELOAD_FILES.length; i++) {
    const name = PRELOAD_FILES[i]
    const data = await streamDownload(`${CORE_CDN}${name}`, (bytes) => onFileProgress(name, bytes))
    const compressed = await compressUint8Array(data)
    await idbSet(name, compressed)
    onFileDone(name, i + 1, PRELOAD_FILES.length)
  }
}

/** True when all preload files are already cached. */
export async function checkCache(): Promise<boolean> {
  try {
    const db = await openDB()
    return await new Promise<boolean>((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readonly')
      const req = tx.objectStore(STORE_NAME).count()
      req.onsuccess = () => {
        db.close()
        resolve(req.result >= PRELOAD_FILES.length)
      }
      req.onerror = () => {
        db.close()
        reject(req.error)
      }
    })
  } catch {
    return false
  }
}

// ──────────────────────────────────────────
//  Fetch interceptor — cache first, then CDN
// ──────────────────────────────────────────

let fileMap: Map<string, Uint8Array> | null = null

function isLibFile(name: string): boolean {
  return (
    name.endsWith('.zip') ||
    name.endsWith('.so') ||
    name.endsWith('.whl') ||
    name.endsWith('.data') ||
    /^[a-z_]+\.cpython-\d+-\w+\.so$/i.test(name)
  )
}

function getMime(name: string): string {
  if (name.endsWith('.wasm')) return 'application/wasm'
  if (name.endsWith('.zip')) return 'application/zip'
  if (name.endsWith('.js') || name.endsWith('.mjs')) return 'application/javascript'
  return 'application/octet-stream'
}

async function loadFileMap(): Promise<void> {
  if (fileMap) return
  fileMap = new Map()
  const entries = await idbGetAllEntries()
  for (const [key, value] of entries) {
    fileMap.set(key, await decompressUint8Array(value))
  }
}

function setFetchInterceptor(): () => void {
  const original = window.fetch.bind(window)

  window.fetch = async (input, init) => {
    const url =
      typeof input === 'string' ? input : input instanceof Request ? input.url : String(input)

    // Only intercept Pyodide's own CDN requests; leave everything else (e.g. /api, /bff) untouched.
    if (!url.startsWith(CORE_CDN) && !url.startsWith(LIBS_CDN)) {
      return original(input, init)
    }

    const filename = url.split('/').pop() || ''
    if (filename && fileMap?.has(filename)) {
      return new Response(fileMap.get(filename)! as BodyInit, {
        headers: { 'Content-Type': getMime(filename) },
      })
    }
    if (filename) {
      const base = isLibFile(filename) ? LIBS_CDN : CORE_CDN
      return original(`${base}${filename}`, init)
    }
    return original(input, init)
  }

  return () => {
    window.fetch = original
  }
}

// ──────────────────────────────────────────
//  Init
// ──────────────────────────────────────────

let pyodideInstance: PyodideInstance | null = null

export function getPyodide(): PyodideInstance | null {
  return pyodideInstance
}

export function isPyodideReady(): boolean {
  return pyodideInstance !== null
}

function loadScript(url: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = url
    script.crossOrigin = 'anonymous'
    script.onload = () => resolve()
    script.onerror = () => reject(new Error(`脚本加载失败: ${url}`))
    document.head.appendChild(script)
  })
}

export async function initPyodide(onStatus?: (message: string) => void): Promise<PyodideInstance> {
  if (pyodideInstance) return pyodideInstance

  onStatus?.('加载缓存文件索引...')
  await loadFileMap()

  onStatus?.('设置文件拦截器...')
  const cleanup = setFetchInterceptor()

  try {
    onStatus?.('加载 Pyodide 运行时...')
    await loadScript(`${CORE_CDN}pyodide.js`)

    onStatus?.('初始化 Python 解释器...')
    const { loadPyodide } = window as unknown as LoadPyodideWindow
    pyodideInstance = await loadPyodide({ indexURL: CORE_CDN })
    return pyodideInstance
  } catch (error) {
    cleanup()
    throw error
  }
}

/** Facade used by the UI: ensure the runtime is cached + initialized (once). */
export async function ensureRuntime(onStatus?: (message: string) => void): Promise<void> {
  if (pyodideInstance) return

  const cached = await checkCache()
  if (cached) {
    onStatus?.('使用已缓存的运行时...')
  } else {
    onStatus?.('下载 Pyodide 运行时（首次约 30MB）...')
    await downloadAndCacheFiles(
      (name, bytes) => onStatus?.(`下载 ${name} (${(bytes / 1048576).toFixed(1)}MB)`),
      (name, index, total) => onStatus?.(`已缓存 ${name}（${index}/${total}）`),
    )
  }

  await initPyodide(onStatus)
}

// ──────────────────────────────────────────
//  Run (with micropip auto-import)
// ──────────────────────────────────────────

const loadedPackages = new Set<string>(['micropip'])
const loadingPromises = new Map<string, Promise<void>>()

/** Extract top-level module names from `import x` / `from x import ...`. */
function extractImports(code: string): string[] {
  const imports = new Set<string>()
  const patterns = [/^\s*import\s+([a-zA-Z_][a-zA-Z0-9_]*)/gm, /^\s*from\s+([a-zA-Z_][a-zA-Z0-9_]*)/gm]
  for (const pattern of patterns) {
    let match: RegExpExecArray | null
    while ((match = pattern.exec(code)) !== null) imports.add(match[1])
  }
  return Array.from(imports)
}

async function loadPackage(pkg: string, onMessage?: (msg: string) => void): Promise<void> {
  const existing = loadingPromises.get(pkg)
  if (existing) return existing

  const promise = (async () => {
    onMessage?.(`正在加载 ${pkg}...`)
    try {
      await pyodideInstance!.loadPackage(pkg)
      loadedPackages.add(pkg)
      onMessage?.(`✓ ${pkg} 已完成`)
    } catch (error) {
      onMessage?.(`✗ ${pkg} 加载失败: ${error instanceof Error ? error.message : String(error)}`)
      throw error
    }
  })()

  loadingPromises.set(pkg, promise)
  return promise
}

async function ensurePackages(code: string, onMessage?: (msg: string) => void): Promise<void> {
  if (!pyodideInstance) return
  const missing: string[] = []
  for (const imp of extractImports(code)) {
    const mapped = PACKAGE_NAME_MAP[imp]
    if (mapped === null) continue // built-in module
    const pkg = mapped ?? imp
    if (!loadedPackages.has(pkg)) missing.push(pkg)
  }
  if (missing.length === 0) return

  onMessage?.(`自动加载依赖: ${missing.join(', ')}`)
  await Promise.all(missing.map((pkg) => loadPackage(pkg, onMessage)))
}

export interface PythonRunOptions {
  /** Standard input text; when omitted, stdin is empty (immediate EOF). */
  stdin?: string
  onMessage?: (msg: string) => void
}

/** Feed the whole stdin text at once; `input()` reads it, then hits EOF. */
function setStdin(text: string): void {
  if (!pyodideInstance) return
  const data = text.length === 0 ? '' : text.endsWith('\n') ? text : `${text}\n`
  let consumed = false
  pyodideInstance.setStdin({
    stdin: () => {
      if (consumed) return null
      consumed = true
      return data === '' ? null : data
    },
  })
}

// User code runs in the Pyodide global namespace (like `exec`), with stdout/stderr captured and
// exceptions turned into a traceback written to stderr.
const WRAPPER = `
import sys, json, io
_code = json.loads(__code_json__)
_stdout = io.StringIO()
_stderr = io.StringIO()
_old_out, _old_err = sys.stdout, sys.stderr
sys.stdout, sys.stderr = _stdout, _stderr
_exc = None
try:
    exec(_code)
except BaseException as _e:
    import traceback
    _stderr.write("Traceback (most recent call last):\\n")
    traceback.print_exc(file=_stderr)
finally:
    sys.stdout, sys.stderr = _old_out, _old_err
__py_out__ = _stdout.getvalue()
__py_err__ = _stderr.getvalue()
`

export async function runPythonCode(code: string, options: PythonRunOptions = {}): Promise<string> {
  if (!pyodideInstance) throw new Error('Pyodide 尚未初始化')

  setStdin(options.stdin ?? '')
  await ensurePackages(code, options.onMessage)
  pyodideInstance.globals.set('__code_json__', JSON.stringify(code))

  try {
    pyodideInstance.runPython(WRAPPER)
    const output = String(pyodideInstance.globals.get('__py_out__') ?? '')
    const error = String(pyodideInstance.globals.get('__py_err__') ?? '')
    return output + (error ? `\n\u001b[31m${error}\u001b[0m` : '')
  } catch (error) {
    return `\n[运行时错误] ${error instanceof Error ? error.message : String(error)}`
  }
}
