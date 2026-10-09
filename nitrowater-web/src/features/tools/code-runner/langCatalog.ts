/**
 * Code-runner language catalog.
 *
 * Execution is split by engine:
 * - `local`  → runs entirely in the browser (WebAssembly / Web Worker); nothing is uploaded,
 *              so it stays anonymous-friendly.
 * - `server` → runs on the backend sandbox (Piston-style); requires login.
 *
 * Only `available: true` languages are wired up today. The rest are listed so the picker
 * communicates the roadmap and the "登录以随时随地运行" CTA makes sense.
 */
export type RunnerEngine = 'local' | 'server'

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

const PYTHON_SAMPLE = `# 在线运行演示：导入标准库 + 简单数据 + 输出结果
# （需要读入数据？展开下方「标准输入」即可用 input() 读取。）
import math
from collections import Counter


def main() -> None:
    nums = [3, 1, 4, 1, 5, 9, 2, 6]
    text = "the quick brown fox jumps over the lazy dog the fox"

    print("Hello from Python in the browser!")
    print("输入 nums =", nums)
    print("最大值 =", max(nums), "| 平均值 =", round(sum(nums) / len(nums), 2))
    print(f"sqrt(2) = {math.sqrt(2):.4f}  (来自 math 模块)")
    print("词频 Top3 =", Counter(text.split()).most_common(3))


if __name__ == "__main__":
    main()
`

export const RUNNER_LANGUAGES: RunnerLanguage[] = [
  {
    id: 'python',
    label: 'Python',
    badge: '本地 · Pyodide 0.29',
    engine: 'local',
    available: true,
    cacheKey: 'python',
    defaultCode: PYTHON_SAMPLE,
  },
  {
    id: 'javascript',
    label: 'JavaScript',
    badge: '本地 · 即将',
    engine: 'local',
    available: false,
    cacheKey: 'javascript',
    defaultCode: '',
  },
  {
    id: 'typescript',
    label: 'TypeScript',
    badge: '本地 · 即将',
    engine: 'local',
    available: false,
    cacheKey: 'typescript',
    defaultCode: '',
  },
  {
    id: 'lua',
    label: 'Lua',
    badge: '本地 · 即将',
    engine: 'local',
    available: false,
    cacheKey: 'lua',
    defaultCode: '',
  },
  {
    id: 'java',
    label: 'Java',
    badge: '服务器 · 需登录',
    engine: 'server',
    available: false,
    cacheKey: 'java',
    defaultCode: '',
  },
  {
    id: 'c',
    label: 'C',
    badge: '服务器 · 需登录',
    engine: 'server',
    available: false,
    cacheKey: 'c',
    defaultCode: '',
  },
  {
    id: 'cpp',
    label: 'C++',
    badge: '服务器 · 需登录',
    engine: 'server',
    available: false,
    cacheKey: 'cpp',
    defaultCode: '',
  },
]

export const DEFAULT_LANGUAGE_ID = 'python'

export function findLanguage(id: string): RunnerLanguage {
  return RUNNER_LANGUAGES.find((language) => language.id === id) ?? RUNNER_LANGUAGES[0]
}
