/**
 * Pyodide CDN + preload configuration.
 *
 * Ported from wtools (MIT) `src/utils/pyodide/config.ts`.
 *
 * Flow: stream each core file from the CDN, store it (DEFLATE-compressed) in IndexedDB, then let
 * `loadPyodide()` read it back through a fetch interceptor; library files (.so/.whl/.zip) fall
 * back to the CDN on demand.
 */
export const CDN_BASE = 'https://cdn.jsdelivr.net'

/** Pyodide version path (shared by core + libs). */
export const PYODIDE_VERSION_PATH = '/pyodide/v0.29.4/full'

/** Core file CDN directory (trailing slash required). */
export const CORE_CDN = `${CDN_BASE}${PYODIDE_VERSION_PATH}/`

/** Library file CDN directory (trailing slash required). */
export const LIBS_CDN = `${CDN_BASE}${PYODIDE_VERSION_PATH}/`

/**
 * Files preloaded + cached during init. `pyodide.js` is tiny (~18KB) and loaded via a <script>
 * tag, so it is not in this list; other files (.so/.whl) are fetched on demand via the interceptor.
 */
export const PRELOAD_FILES = [
  'pyodide.asm.js',
  'pyodide.asm.wasm',
  'pyodide-lock.json',
  'python_stdlib.zip',
]
