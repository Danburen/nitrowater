/**
 * ffmpegManager — ffmpeg.wasm 懒加载管理器（单例）。
 *
 * 遵循本仓库 `lib/archive/libarchive.ts` 的模式：本模块被组件静态引入，但重依赖在调用时
 * `await import()` 惰性加载（独立 chunk，导航到工具时才会拉取）。
 *
 * - Tier B：`@ffmpeg/ffmpeg` 为 npm 依赖，`await import()` 惰性加载；
 * - Tier A：`@ffmpeg/core`（~24MB WASM）从 CDN 加载，见 ./cdn.ts。
 */
import type { FFmpeg } from '@ffmpeg/ffmpeg'
import { CORE_JS, CORE_WASM } from './cdn'

export type FfmpegStep = 'loading' | 'ready'

export interface FfmpegState {
  step: FfmpegStep
  progress: number
  message: string
}

let ffmpegInstance: FFmpeg | null = null
let loadPromise: Promise<FFmpeg> | null = null

/** 已加载的 ffmpeg 实例（未加载时为 null）。 */
export function getFFmpeg(): FFmpeg | null {
  return ffmpegInstance
}

/**
 * 懒加载并初始化 ffmpeg 核心；并发调用共享同一个 Promise（单例）。
 * 首次会从 CDN 下载约 24MB 的 WASM 核心。
 */
export async function loadFFmpeg(
  onProgress?: (state: FfmpegState) => void,
): Promise<FFmpeg> {
  if (ffmpegInstance) return ffmpegInstance
  if (loadPromise) return loadPromise

  loadPromise = (async () => {
    onProgress?.({ step: 'loading', progress: 10, message: '加载 ffmpeg.wasm 运行时代码…' })
    const { FFmpeg } = await import('@ffmpeg/ffmpeg')

    onProgress?.({ step: 'loading', progress: 30, message: '创建 FFmpeg 实例…' })
    const ffmpeg = new FFmpeg()

    // 下载核心期间的临时监听（加载完成后立即移除，避免干扰后续业务调用）。
    const onLog = ({ message }: { message: string }) => {
      if (message.includes('Download') || message.includes('load')) {
        onProgress?.({ step: 'loading', progress: 55, message: message.slice(0, 80) })
      }
    }
    ffmpeg.on('log', onLog)

    onProgress?.({ step: 'loading', progress: 45, message: '下载 WASM 核心（约 24MB）…' })
    // Worker 由 Vite 打包为本域文件；coreURL/wasmURL 为 CDN 地址，
    // Worker 内 import(coreURL) 走 CORS（jsDelivr 允许跨域）。
    await ffmpeg.load({ coreURL: CORE_JS, wasmURL: CORE_WASM })

    ffmpeg.off('log', onLog)

    ffmpegInstance = ffmpeg
    onProgress?.({ step: 'ready', progress: 100, message: 'FFmpeg 核心已就绪' })
    return ffmpeg
  })()

  try {
    return await loadPromise
  } catch (error) {
    // 失败时允许重试：清空缓存的 Promise。
    loadPromise = null
    throw error
  }
}

/** 重置实例与加载 Promise（用于失败重试）。 */
export function resetFFmpeg(): void {
  ffmpegInstance = null
  loadPromise = null
}
