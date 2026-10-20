import { apiFetch } from '../../auth/bff'

/**
 * Playground (ACM judging) API client.
 *
 * Reads go through the BFF proxy (`/api/**`); the resource server exposes the problem bank under
 * `/api/public/**` (anonymous read) and submissions under `/api/**` (login required). Mutating
 * calls attach the CSRF header via {@link apiFetch}.
 */

/** A tag attached to a problem (bilingual via direct `_en` columns; `nameEn` is optional). */
export interface Tag {
  id: number
  slug: string
  name: string
  nameEn: string | null
  kind: string
  color: string | null
  enabled: boolean
}

/** A lightweight problem set ("题单") a problem belongs to. */
export interface ProblemSetBrief {
  id: number
  slug: string
  name: string
  nameEn: string | null
  color: string | null
}

export interface ProblemSummary {
  id: number
  slug: string
  title: string
  titleEn: string | null
  difficulty: string
  tags: Tag[]
  sets: ProblemSetBrief[]
  rewardPoints: number
  timeLimitMs: number
  memoryLimitKb: number
  enabled: boolean
}

export interface TestCase {
  idx: number
  stdin: string | null
  expectedOutput: string | null
  isSample: boolean
  score: number
}

export interface ProblemDetail {
  id: number
  slug: string
  title: string
  titleEn: string | null
  difficulty: string
  tags: Tag[]
  sets: ProblemSetBrief[]
  description: string
  descriptionEn: string | null
  ioMode: string
  templates: Record<string, string> | null
  judgeConfig: Record<string, unknown> | null
  timeLimitMs: number
  memoryLimitKb: number
  rewardPoints: number
  enabled: boolean
  sampleCases: TestCase[]
}

export interface SubmissionCase {
  index: number
  verdict: string
  timeMs: number
  memoryKb: number
  message: string | null
}

export interface Submission {
  id: number
  problemId: number
  language: string
  status: string
  verdict: string | null
  passed: number
  total: number
  score: number
  timeMs: number | null
  memoryKb: number | null
  cases: SubmissionCase[]
  compileMessage: string | null
  createdAt: string
  finishedAt: string | null
}

/** Unwrap a JSON response, turning non-2xx into a thrown Error with the server body. */
async function toJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.text().catch(() => '')
    throw new Error(`HTTP ${response.status}${body ? `: ${body}` : ''}`)
  }
  return (await response.json()) as T
}

export async function listProblems(): Promise<ProblemSummary[]> {
  return toJson<ProblemSummary[]>(await apiFetch('/api/public/problems'))
}

export async function getProblem(id: number | string): Promise<ProblemDetail> {
  return toJson<ProblemDetail>(await apiFetch(`/api/public/problems/${id}`))
}

export async function createSubmission(problemId: number, language: string, code: string): Promise<Submission> {
  return toJson<Submission>(
    await apiFetch('/api/submissions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ problemId, language, code }),
    }),
  )
}

export async function getSubmission(id: number): Promise<Submission> {
  return toJson<Submission>(await apiFetch(`/api/submissions/${id}`))
}

export async function listSubmissions(): Promise<Submission[]> {
  return toJson<Submission[]>(await apiFetch('/api/submissions'))
}

/** Result of a one-off self-test run (`POST /api/sandbox/run`). */
export interface RunResult {
  stdout: string | null
  stderr: string | null
  /** {@code OK} on success, otherwise {@code CE}/{@code TO}/{@code OL}/{@code RE}/{@code SG}/{@code SE}. */
  status: string
  exitCode: number | null
  wallTimeMs: number | null
  memoryBytes: number | null
  message: string | null
}

/** Runs code once on the backend sandbox (login required) — the "self-test before submit" path. */
export async function runCode(language: string, code: string, stdin: string): Promise<RunResult> {
  return toJson<RunResult>(
    await apiFetch('/api/sandbox/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ language, code, stdin }),
    }),
  )
}

/** Result of a problem-aware self-test run (`POST /api/sandbox/run-problem`). */
export interface ProblemRunResult {
  /** Run state: `OK`, otherwise `CE`/`TO`/`OL`/`RE`/`SG`/`SE`. */
  status: string
  stdout: string | null
  stderr: string | null
  timeMs: number | null
  memoryKb: number | null
  message: string | null
}

/**
 * Runs code once with a free-form input for a problem (login required) — a plain "stdin in,
 * stdout out" self-test: nothing is judged and nothing is persisted. LeetCode-style problems go
 * through the same harness as a submission (the input is the JSON parameter object); ACM problems
 * run raw.
 */
export async function runProblem(
  problemId: number,
  language: string,
  code: string,
  stdin: string,
): Promise<ProblemRunResult> {
  return toJson<ProblemRunResult>(
    await apiFetch('/api/sandbox/run-problem', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ problemId, language, code, stdin }),
    }),
  )
}
