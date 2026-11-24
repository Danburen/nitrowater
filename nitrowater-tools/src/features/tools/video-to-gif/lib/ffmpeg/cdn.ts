/**
 * ffmpeg.wasm CDN 配置。
 *
 * 分级策略：
 * - `@ffmpeg/ffmpeg`（含 Worker 创建代码）是 **npm 依赖**（Tier B），由 Vite 本地构建，
 *   Worker 同域创建，无 CORS 问题；
 * - `@ffmpeg/core`（~24MB WASM）是 **CDN 懒加载**（Tier A），Worker 内
 *   `import(coreURL)` 同源加载 ESM 核心，仅点击“加载核心”时才下载。
 *
 * 版本锁定在 URL 中：0.12.10（与 @ffmpeg/ffmpeg 0.12.x 配套）。
 */
const CORE_BASE = 'https://cdn.jsdelivr.net/npm/@ffmpeg/core@0.12.10/dist/esm'

/** 核心 JS（ESM）CDN 地址。 */
export const CORE_JS = `${CORE_BASE}/ffmpeg-core.js`

/** 核心 WASM（~24MB）CDN 地址。 */
export const CORE_WASM = `${CORE_BASE}/ffmpeg-core.wasm`
