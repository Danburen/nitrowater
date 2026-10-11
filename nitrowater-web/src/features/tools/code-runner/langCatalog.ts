/**
 * Code-runner language catalog.
 *
 * Execution is split by engine:
 * - `local`  → runs entirely in the browser (WebAssembly / Web Worker); nothing is uploaded,
 *              so it stays anonymous-friendly. Runtimes are loaded from a CDN on first run
 *              (lazy) — JavaScript needs no download (native engine), TypeScript pulls the
 *              compiler from a CDN, Lua pulls the wasmoon WebAssembly VM, Python pulls Pyodide.
 * - `server` → runs on the backend sandbox (Piston); anonymous is rate-limited, login relaxes it.
 *
 * Only `available: true` languages are wired up today. The rest are listed so the picker
 * communicates the roadmap and the "登录以随时随地运行" CTA makes sense.
 *
 * Starter snippets are NOT inlined here — they live in the dedicated structured file
 * `samples.json` (keyed by language id), so sample code stays separate from the logic.
 */
import samples from './samples.json'

export type RunnerEngine = 'local' | 'server'

/** Browser-only languages that have a runtime wired up in `lib/runtimes`. */
export type LocalLanguageId = 'python' | 'javascript' | 'typescript' | 'lua'

/** Languages executed on the backend sandbox (Piston) rather than in the browser. */
export type ServerLanguageId = 'java' | 'c' | 'cpp'

/** Every language id the runner can select. */
export type RunnerLanguageId = LocalLanguageId | ServerLanguageId

export interface RunnerLanguage {
  id: string
  label: string
  /** Short engine badge shown in the picker. */
  badge: string
  engine: RunnerEngine
  /** Local runtime implemented in the browser right now. */
  available: boolean
  /** localStorage draft key, namespaced by language. */
  cacheKey: string
  /** Starter snippet loaded on first visit / reset. */
  defaultCode: string
}

/** Starter snippets, keyed by language id. `Record` keeps this in sync with the language union. */
const SAMPLES: Record<RunnerLanguageId, string> = samples

export const RUNNER_LANGUAGES: RunnerLanguage[] = [
  {
    id: 'python',
    label: 'Python',
    badge: '本地 · Pyodide 0.29',
    engine: 'local',
    available: true,
    cacheKey: 'python',
    defaultCode: SAMPLES.python,
  },
  {
    id: 'javascript',
    label: 'JavaScript',
    badge: '本地 · 浏览器原生',
    engine: 'local',
    available: true,
    cacheKey: 'javascript',
    defaultCode: SAMPLES.javascript,
  },
  {
    id: 'typescript',
    label: 'TypeScript',
    badge: '本地 · TS 编译器',
    engine: 'local',
    available: true,
    cacheKey: 'typescript',
    defaultCode: SAMPLES.typescript,
  },
  {
    id: 'lua',
    label: 'Lua',
    badge: '本地 · wasmoon 5.4',
    engine: 'local',
    available: true,
    cacheKey: 'lua',
    defaultCode: SAMPLES.lua,
  },
  {
    id: 'java',
    label: 'Java',
    badge: '服务端 · Piston',
    engine: 'server',
    available: true,
    cacheKey: 'java',
    defaultCode: SAMPLES.java,
  },
  {
    id: 'c',
    label: 'C',
    badge: '服务端 · Piston',
    engine: 'server',
    available: true,
    cacheKey: 'c',
    defaultCode: SAMPLES.c,
  },
  {
    id: 'cpp',
    label: 'C++',
    badge: '服务端 · Piston',
    engine: 'server',
    available: true,
    cacheKey: 'cpp',
    defaultCode: SAMPLES.cpp,
  },
]

export const DEFAULT_LANGUAGE_ID = 'python'

/** The browser-only languages wired up in `lib/runtimes`. */
const LOCAL_LANGUAGE_IDS: ReadonlySet<string> = new Set([
  'python',
  'javascript',
  'typescript',
  'lua',
])

export function isLocalLanguage(id: string): id is LocalLanguageId {
  return LOCAL_LANGUAGE_IDS.has(id)
}

/** Every selectable language id, so the editor can narrow to a syntax-highlighting mode. */
const ALL_LANGUAGE_IDS: ReadonlySet<string> = new Set<RunnerLanguageId>([
  'python',
  'javascript',
  'typescript',
  'lua',
  'java',
  'c',
  'cpp',
])

export function isRunnerLanguage(id: string): id is RunnerLanguageId {
  return ALL_LANGUAGE_IDS.has(id)
}

export function findLanguage(id: string): RunnerLanguage {
  return RUNNER_LANGUAGES.find((language) => language.id === id) ?? RUNNER_LANGUAGES[0]
}
