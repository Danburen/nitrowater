/**
 * Playground judging labels — maps the backend enum names to display text/colors.
 *
 * Verdicts mirror `cn.nitrowater.server.infra.enums.Verdict`; statuses mirror `SubmissionStatus`.
 */

export interface VerdictMeta {
  label: string
  /** Tailwind text color (arbitrary hex — the theme does not expose the default palette). */
  color: string
}

const VERDICTS: Record<string, VerdictMeta> = {
  AC: { label: '通过', color: 'text-[#10b981]' },
  WA: { label: '答案错误', color: 'text-[#f43f5e]' },
  TLE: { label: '超时', color: 'text-[#f59e0b]' },
  MLE: { label: '内存超限', color: 'text-[#f59e0b]' },
  RE: { label: '运行错误', color: 'text-[#f43f5e]' },
  CE: { label: '编译错误', color: 'text-[#f43f5e]' },
  OLE: { label: '输出超限', color: 'text-[#f59e0b]' },
  SE: { label: '系统错误', color: 'text-[#a855f7]' },
}

const STATUSES: Record<string, string> = {
  QUEUED: '排队中',
  RUNNING: '评测中',
  DONE: '已完成',
  SE: '系统错误',
}

export function verdictMeta(verdict: string | null | undefined): VerdictMeta | null {
  if (!verdict) return null
  return VERDICTS[verdict] ?? { label: verdict, color: 'text-dim' }
}

export function statusLabel(status: string | null | undefined): string {
  if (!status) return ''
  return STATUSES[status] ?? status
}

const DIFFICULTIES: Record<string, { label: string; color: string }> = {
  EASY: { label: '简单', color: 'text-[#10b981]' },
  MEDIUM: { label: '中等', color: 'text-[#f59e0b]' },
  HARD: { label: '困难', color: 'text-[#f43f5e]' },
}

export function difficultyMeta(difficulty: string | null | undefined): { label: string; color: string } {
  if (!difficulty) return { label: '-', color: 'text-faint' }
  return DIFFICULTIES[difficulty] ?? { label: difficulty, color: 'text-dim' }
}
