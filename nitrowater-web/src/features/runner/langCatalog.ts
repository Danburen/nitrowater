/**
 * Code-runner language catalog for the signed-in runner.
 *
 * Every language runs on the backend sandbox (Piston): this page is login-only, so there is no
 * browser-local / WASM engine and no anonymous tier. Starter snippets live in `samples.json`
 * so sample code stays separate from the logic.
 */
import samples from './samples.json'

/** Every language id the runner can select. */
export type RunnerLanguageId = 'python' | 'javascript' | 'typescript' | 'lua' | 'java' | 'c' | 'cpp'

export interface RunnerLanguage {
  id: RunnerLanguageId
  label: string
  /** localStorage draft key, namespaced by language. */
  cacheKey: string
  /** Starter snippet loaded on first visit / reset. */
  defaultCode: string
}

/** Starter snippets, keyed by language id. `Record` keeps this in sync with the language union. */
const SAMPLES: Record<RunnerLanguageId, string> = samples

export const RUNNER_LANGUAGES: RunnerLanguage[] = [
  { id: 'python', label: 'Python', cacheKey: 'python', defaultCode: SAMPLES.python },
  { id: 'javascript', label: 'JavaScript', cacheKey: 'javascript', defaultCode: SAMPLES.javascript },
  { id: 'typescript', label: 'TypeScript', cacheKey: 'typescript', defaultCode: SAMPLES.typescript },
  { id: 'lua', label: 'Lua', cacheKey: 'lua', defaultCode: SAMPLES.lua },
  { id: 'java', label: 'Java', cacheKey: 'java', defaultCode: SAMPLES.java },
  { id: 'c', label: 'C', cacheKey: 'c', defaultCode: SAMPLES.c },
  { id: 'cpp', label: 'C++', cacheKey: 'cpp', defaultCode: SAMPLES.cpp },
]

export const DEFAULT_LANGUAGE_ID: RunnerLanguageId = 'python'

const ALL_LANGUAGE_IDS: ReadonlySet<string> = new Set<RunnerLanguageId>(RUNNER_LANGUAGES.map((item) => item.id))

export function isRunnerLanguage(id: string): id is RunnerLanguageId {
  return ALL_LANGUAGE_IDS.has(id)
}

export function findLanguage(id: string): RunnerLanguage {
  return RUNNER_LANGUAGES.find((language) => language.id === id) ?? RUNNER_LANGUAGES[0]
}
