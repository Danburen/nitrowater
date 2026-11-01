/**
 * Result of one sandbox execution (`POST /api/sandbox/run`), shared by the chat code block and the
 * code-runner tool. `status` is the judge verdict (`OK`, `CE` for a compile error, `TO` on timeout,
 * `RE` on a runtime error, ...).
 */
export interface SandboxRunOutcome {
  readonly stdout: string | null
  readonly stderr: string | null
  readonly status: string
  readonly exitCode: number | null
  readonly wallTimeMs: number | null
  readonly memoryBytes: number | null
  readonly message: string | null
}

