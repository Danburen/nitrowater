/**
 * Language catalog for the browser-local code runner.
 *
 * Every entry runs entirely in the user's browser (native JS engine or WebAssembly): no code or
 * input is uploaded and no login is involved. Runtimes load lazily from a CDN on first run —
 * JavaScript needs no download, TypeScript pulls the compiler, Lua pulls the wasmoon VM and
 * Python pulls Pyodide.
 *
 * Server-executed languages (Java / C / C++ and the rest of the Piston catalogue) are
 * intentionally absent: they belong to the login-gated cloud runner on the main site, not to
 * this static tools site.
 *
 * Starter snippets live in `samples.json`, keyed by language id, so sample code stays separate
 * from the logic.
 */
import samples from './samples.json'

/** Languages with a browser-only runtime wired up in `lib/runtimes`. */
export type LocalLanguageId = 'python' | 'javascript' | 'typescript' | 'lua'

/** The editor and the UI only ever deal with local languages here. */
export type RunnerLanguageId = LocalLanguageId

export interface RunnerLanguage {
  id: LocalLanguageId
  label: string
  /** Short engine badge shown in the picker. */
  badge: string
  /** localStorage draft key, namespaced by language. */
  cacheKey: string
  /** Starter snippet loaded on first visit / reset. */
  defaultCode: string
}

/** Starter snippets, keyed by language id. `Record` keeps this in sync with the language union. */
const SAMPLES: Record<LocalLanguageId, string> = samples

export const RUNNER_LANGUAGES: RunnerLanguage[] = [
  {
    id: 'python',
    label: 'Python',
    badge: '本地 · Pyodide 0.29',
    cacheKey: 'python',
    defaultCode: SAMPLES.python,
  },
  {
    id: 'javascript',
    label: 'JavaScript',
    badge: '本地 · 浏览器原生',
    cacheKey: 'javascript',
    defaultCode: SAMPLES.javascript,
  },
  {
    id: 'typescript',
    label: 'TypeScript',
    badge: '本地 · TS 编译器',
    cacheKey: 'typescript',
    defaultCode: SAMPLES.typescript,
  },
  {
    id: 'lua',
    label: 'Lua',
    badge: '本地 · wasmoon 5.4',
    cacheKey: 'lua',
    defaultCode: SAMPLES.lua,
  },
]

export const DEFAULT_LANGUAGE_ID: LocalLanguageId = 'python'

const LOCAL_LANGUAGE_IDS: ReadonlySet<string> = new Set<LocalLanguageId>([
  'python',
  'javascript',
  'typescript',
  'lua',
])

export function isLocalLanguage(id: string): id is LocalLanguageId {
  return LOCAL_LANGUAGE_IDS.has(id)
}

/** Kept as the shared type guard name so the editor and cache logic read uniformly. */
export function isRunnerLanguage(id: string): id is LocalLanguageId {
  return isLocalLanguage(id)
}

export function findLanguage(id: string): RunnerLanguage {
  return RUNNER_LANGUAGES.find((language) => language.id === id) ?? RUNNER_LANGUAGES[0]
}
