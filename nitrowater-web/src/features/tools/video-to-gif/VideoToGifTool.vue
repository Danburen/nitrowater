<script setup lang="ts">
/**
 * MP4 → GIF：基于 ffmpeg.wasm 两遍调色板量化（palettegen → paletteuse）。
 * 全部在浏览器本地完成，视频不会上传。
 * 移植自 wtools (MIT) `src/components/VideoToGif.vue`，UI 用 Tailwind + Element Plus 重写。
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { saveAs } from 'file-saver'
import type { FFmpeg, LogEvent, ProgressEvent } from '@ffmpeg/ffmpeg'
import { toast } from '../../../shared/ui/toast'
import ToolShell from '../ToolShell.vue'
import type { FfmpegState } from './lib/ffmpeg/ffmpegManager'
import { getFFmpeg, loadFFmpeg, resetFFmpeg } from './lib/ffmpeg/ffmpegManager'

const ACCEPT = '.mp4,.webm,.mov,.avi'

type ReadyState = 'idle' | 'loading' | 'ready' | 'error'

// ── 核心加载状态 ──
const readyState = ref<ReadyState>('idle')
const loadPercent = ref(0)
const loadMessage = ref('')

const readyTag = computed(() => {
  switch (readyState.value) {
    case 'ready':
      return { type: 'success' as const, text: '核心已就绪' }
    case 'loading':
      return { type: 'warning' as const, text: '加载核心中…' }
    case 'error':
      return { type: 'danger' as const, text: '加载失败' }
    default:
      return { type: 'info' as const, text: '待加载' }
  }
})

onMounted(() => {
  // 同会话中若其他入口已加载核心，直接就绪。
  if (getFFmpeg()) readyState.value = 'ready'
})

async function loadCore(): Promise<void> {
  readyState.value = 'loading'
  loadPercent.value = 0
  loadMessage.value = '准备加载…'
  try {
    await loadFFmpeg((state: FfmpegState) => {
      loadPercent.value = state.progress
      loadMessage.value = state.message
      if (state.step === 'ready') readyState.value = 'ready'
    })
    toast.success('FFmpeg 核心已就绪')
  } catch (error) {
    readyState.value = 'error'
    loadMessage.value = error instanceof Error ? error.message : String(error)
    toast.error('核心加载失败，请重试')
  }
}

async function retryLoad(): Promise<void> {
  resetFFmpeg()
  readyState.value = 'idle'
  loadPercent.value = 0
  loadMessage.value = ''
  await loadCore()
}

// ── 视频文件 ──
const videoFile = ref<File | null>(null)
const videoUrl = ref('')
const videoRef = ref<HTMLVideoElement>()
const fileInputRef = ref<HTMLInputElement>()
const videoDuration = ref(0)
const videoWidth = ref(0)
const videoHeight = ref(0)

function onPickVideo(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  clearResult()
  if (videoUrl.value) URL.revokeObjectURL(videoUrl.value)
  videoFile.value = file
  videoUrl.value = URL.createObjectURL(file)
}

function onVideoMeta(): void {
  const el = videoRef.value
  if (!el) return
  // WebM from some sources (e.g. MediaRecorder) has no finite duration until fully loaded.
  const total = Number.isFinite(el.duration) ? el.duration : 0
  videoDuration.value = total
  videoWidth.value = el.videoWidth
  videoHeight.value = el.videoHeight
  maxStartTime.value = Math.max(0, Math.floor(total))
  // 默认裁剪 0 ~ min(5s, 总长)；无时长元数据时区间为 [0,0]（时间轴禁用）。
  const end = Math.max(1, Math.min(5, maxStartTime.value))
  trimRange.value = maxStartTime.value > 0 ? [0, end] : [0, 0]
}

// ── 输出参数 ──
const fps = ref(10)
const scaleWidth = ref(480)
/** 自定义输出宽度，仅当 scaleWidth === -1 时生效。 */
const customWidth = ref(640)
const paletteColors = ref(192)
const loop = ref(true)
/** 视频总秒数（无时长元数据时为 0，时间轴禁用）。 */
const maxStartTime = ref(0)
/** 裁剪区间 [起点, 终点]，单位秒。 */
const trimRange = ref<[number, number]>([0, 0])

/** 实际生效的输出宽度：-1 表示使用自定义宽度，0 表示原始尺寸。 */
const effectiveWidth = computed(() =>
  scaleWidth.value === -1 ? Math.max(2, customWidth.value) : scaleWidth.value,
)

/** 无时长元数据时裁剪不可用。 */
const trimDisabled = computed(() => maxStartTime.value <= 0)

/** 裁剪区间起点（秒）。 */
const trimStart = computed(() => trimRange.value[0])

/** 裁剪区间终点（秒）。 */
const trimEnd = computed(() => trimRange.value[1])

/** 转换时长 = 终点 - 起点；无时长元数据时退化为整段（兜底 1s 防止 -t 0）。 */
const trimDuration = computed(() => Math.max(1, trimEnd.value - trimStart.value))

/** 保证两个把手最小间隔 1s，防止区间重叠。 */
watch(trimRange, ([start, end]) => {
  if (end - start >= 1) return
  if (end + 1 <= maxStartTime.value) trimRange.value = [start, end + 1]
  else trimRange.value = [end - 1, end]
})

// ── 转换状态 ──
const converting = ref(false)
const convertPercent = ref(0)
const convertMessage = ref('')
const convertDetail = ref('')
const gifUrl = ref('')
const gifSize = ref(0)

/** exec 期间临时监听 progress / log，返回清理函数。 */
function listenExec(ffmpeg: FFmpeg, rangeStart: number, rangeEnd: number): () => void {
  const onProgress = ({ progress }: ProgressEvent) => {
    const p = Math.min(progress, 1)
    convertPercent.value = Math.min(Math.round(rangeStart + p * (rangeEnd - rangeStart)), rangeEnd)
  }
  const onLog = ({ message }: LogEvent) => {
    if (!message.includes('frame=')) return
    const parts: string[] = []
    const frame = message.match(/frame=\s*(\d+)/)
    if (frame) parts.push(`${frame[1]} 帧`)
    const time = message.match(/time=\s*(\S+)/)
    if (time) parts.push(time[1])
    const speed = message.match(/speed=\s*(\S+)x/)
    if (speed) parts.push(`${speed[1]}x`)
    convertDetail.value = parts.join(' · ')
  }
  ffmpeg.on('progress', onProgress)
  ffmpeg.on('log', onLog)
  return () => {
    ffmpeg.off('progress', onProgress)
    ffmpeg.off('log', onLog)
  }
}

async function doConvert(): Promise<void> {
  if (!videoFile.value || converting.value) return
  const ffmpeg = getFFmpeg()
  if (!ffmpeg) {
    toast.error('FFmpeg 核心未加载')
    return
  }

  converting.value = true
  convertPercent.value = 0
  convertMessage.value = '准备转换…'
  convertDetail.value = ''
  if (gifUrl.value) URL.revokeObjectURL(gifUrl.value)
  gifUrl.value = ''
  gifSize.value = 0

  try {
    convertPercent.value = 5
    convertMessage.value = '读取视频文件…'
    const buffer = await videoFile.value.arrayBuffer()

    convertPercent.value = 10
    convertMessage.value = '写入虚拟文件系统…'
    await ffmpeg.writeFile('input.mp4', new Uint8Array(buffer))

    // 共享 filter 链：可选缩放 + 帧率。
    const filters: string[] = []
    if (effectiveWidth.value > 0) filters.push(`scale=${effectiveWidth.value}:-1:flags=lanczos`)
    filters.push(`fps=${fps.value}`)
    const paletteFilter = filters.join(',')

    // 裁剪参数：有可用时长时按区间截取，否则整段转换。
    const trimArgs = trimDisabled.value
      ? []
      : ['-ss', String(trimStart.value), '-t', String(trimDuration.value)]

    // 第一遍：生成调色板。
    convertPercent.value = 15
    convertMessage.value = '分析色彩生成调色板'
    const cleanup1 = listenExec(ffmpeg, 15, 50)
    await ffmpeg.exec([
      '-i', 'input.mp4',
      ...trimArgs,
      '-vf', `${paletteFilter},palettegen=stats_mode=diff:max_colors=${paletteColors.value}`,
      '-y', 'palette.png',
    ])
    cleanup1()

    // 第二遍：应用调色板输出 GIF。
    convertPercent.value = 55
    convertMessage.value = '应用调色板输出 GIF'
    const cleanup2 = listenExec(ffmpeg, 55, 88)
    await ffmpeg.exec([
      '-i', 'input.mp4',
      '-i', 'palette.png',
      ...trimArgs,
      '-lavfi', `${paletteFilter} [x]; [x][1:v] paletteuse=dither=bayer:bayer_scale=5`,
      ...(loop.value ? [] : ['-loop', '1']),
      '-y', 'output.gif',
    ])
    cleanup2()

    convertPercent.value = 90
    convertMessage.value = '读取输出数据…'
    const data = await ffmpeg.readFile('output.gif')
    const bytes = data instanceof Uint8Array ? data : new Uint8Array()

    convertPercent.value = 96
    convertMessage.value = '生成 GIF 预览…'
    const blob = new Blob([bytes.slice()], { type: 'image/gif' })
    gifSize.value = bytes.length
    await nextTick()
    gifUrl.value = URL.createObjectURL(blob)

    convertPercent.value = 100
    convertMessage.value = '转换完成'
  } catch (error) {
    toast.error(`转换失败：${error instanceof Error ? error.message : String(error)}`)
  } finally {
    // 无论成功或失败都清理虚拟文件系统，避免失败路径残留 input/palette/output。
    await Promise.all([
      ffmpeg.deleteFile('input.mp4').catch(() => undefined),
      ffmpeg.deleteFile('palette.png').catch(() => undefined),
      ffmpeg.deleteFile('output.gif').catch(() => undefined),
    ])
    converting.value = false
  }
}

function downloadGif(): void {
  if (!gifUrl.value) return
  saveAs(gifUrl.value, `output_${fps.value}fps_${effectiveWidth.value}w.gif`)
  toast.success('GIF 已下载')
}

function clearResult(): void {
  converting.value = false
  convertPercent.value = 0
  convertMessage.value = ''
  convertDetail.value = ''
  if (gifUrl.value) URL.revokeObjectURL(gifUrl.value)
  gifUrl.value = ''
  gifSize.value = 0
}

/** 清空视频与结果，允许重新选择。 */
function resetAll(): void {
  clearResult()
  if (videoUrl.value) URL.revokeObjectURL(videoUrl.value)
  videoUrl.value = ''
  videoFile.value = null
  videoDuration.value = 0
  videoWidth.value = 0
  videoHeight.value = 0
  maxStartTime.value = 0
  trimRange.value = [0, 0]
}

/** 更换视频：清空当前内容后直接打开文件选择框。 */
function replaceVideo(): void {
  resetAll()
  fileInputRef.value?.click()
}

function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}
</script>

<template>
  <ToolShell
    title="MP4 转 GIF"
    desc="基于 ffmpeg.wasm 两遍调色板量化，将 MP4 / WebM / MOV / AVI 转为 GIF。全部在浏览器本地完成，视频不会上传。首次需从 CDN 下载约 24MB 的 WASM 核心。"
  >
    <div class="flex flex-col gap-4">
      <!-- 核心状态 -->
      <section class="panel flex flex-col gap-3">
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-ink">运行核心</span>
          <el-tag
            :type="readyTag.type"
            size="small"
            effect="plain"
            round
          >
            {{ readyTag.text }}
          </el-tag>
        </div>

        <div
          v-if="readyState === 'idle'"
          class="flex flex-wrap items-center gap-3"
        >
          <el-button
            type="primary"
            @click="loadCore"
          >
            加载 ffmpeg 核心
          </el-button>
          <span class="text-xs text-faint">首次需下载约 24MB WASM，加载后可离线使用</span>
        </div>

        <div
          v-else-if="readyState === 'loading'"
          class="flex flex-col gap-2"
        >
          <span class="text-[13px] text-dim">{{ loadMessage }}</span>
          <el-progress
            :percentage="loadPercent"
            :text-inside="true"
            :stroke-width="18"
            :status="loadPercent === 100 ? 'success' : undefined"
          />
        </div>

        <div
          v-else-if="readyState === 'error'"
          class="flex flex-col gap-2"
        >
          <el-alert
            title="核心加载失败"
            type="error"
            :description="loadMessage"
            show-icon
            :closable="false"
          />
          <div>
            <el-button
              type="primary"
              @click="retryLoad"
            >
              重新加载 ffmpeg 核心
            </el-button>
          </div>
        </div>
      </section>

      <template v-if="readyState === 'ready'">
        <!-- 上传 -->
        <section class="panel flex flex-col gap-4">
          <label class="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-[12px] border border-dashed border-line bg-bg/40 px-6 py-8 text-center transition hover:border-accent/50">
            <svg
              class="size-7 text-accent"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM10 8.5l5 3.5-5 3.5z" />
            </svg>
            <span class="text-sm text-dim">拖拽或 <em class="not-italic text-accent">点击选择视频文件</em></span>
            <span class="font-mono text-xs text-faint">支持 MP4 / WebM / MOV / AVI</span>
            <input
              ref="fileInputRef"
              type="file"
              :accept="ACCEPT"
              class="hidden"
              @change="onPickVideo"
            >
          </label>

          <div
            v-if="videoUrl"
            class="flex flex-col gap-2"
          >
            <video
              ref="videoRef"
              :src="videoUrl"
              controls
              preload="metadata"
              class="max-h-[340px] w-full rounded-[10px] bg-black"
              @loadedmetadata="onVideoMeta"
            />
            <div class="flex flex-wrap items-center gap-2 text-xs text-faint">
              <span class="truncate">{{ videoFile?.name }}</span>
              <span v-if="videoDuration">· 时长 {{ formatDuration(videoDuration) }}</span>
              <span v-if="videoWidth">{{ videoWidth }}×{{ videoHeight }}</span>
              <el-popconfirm
                v-if="gifUrl"
                title="更换视频将清空当前结果"
                confirm-button-text="更换"
                cancel-button-text="取消"
                width="220"
                @confirm="replaceVideo"
              >
                <template #reference>
                  <el-button size="small">
                    更换视频
                  </el-button>
                </template>
              </el-popconfirm>
              <el-button
                v-else
                size="small"
                @click="replaceVideo"
              >
                更换视频
              </el-button>
            </div>
          </div>
        </section>

        <!-- 输出设置 -->
        <section
          v-if="videoUrl"
          class="panel flex flex-col gap-4"
        >
          <h2 class="text-sm font-semibold text-ink">
            输出设置
          </h2>

          <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <div class="flex flex-col gap-1">
              <span class="text-xs text-dim">帧率 (FPS)</span>
              <el-select
                v-model="fps"
                class="w-full"
              >
                <el-option
                  :value="5"
                  label="5（小文件）"
                />
                <el-option
                  :value="10"
                  label="10（适中）"
                />
                <el-option
                  :value="15"
                  label="15（流畅）"
                />
                <el-option
                  :value="24"
                  label="24（原帧率）"
                />
                <el-option
                  :value="30"
                  label="30（高帧率）"
                />
              </el-select>
            </div>
            <div class="flex flex-col gap-1">
              <span class="text-xs text-dim">输出宽度</span>
              <el-select
                v-model="scaleWidth"
                class="w-full"
              >
                <el-option
                  :value="0"
                  label="原始尺寸"
                />
                <el-option
                  :value="320"
                  label="320px"
                />
                <el-option
                  :value="480"
                  label="480px"
                />
                <el-option
                  :value="640"
                  label="640px"
                />
                <el-option
                  :value="800"
                  label="800px"
                />
                <el-option
                  :value="-1"
                  label="自定义…"
                />
              </el-select>
              <el-input-number
                v-if="scaleWidth === -1"
                v-model="customWidth"
                class="w-full"
                :min="16"
                :max="4096"
                :step="16"
                :precision="0"
                controls-position="right"
              />
            </div>
            <div class="flex flex-col gap-1">
              <span class="text-xs text-dim">调色板颜色数</span>
              <el-select
                v-model="paletteColors"
                class="w-full"
              >
                <el-option
                  :value="128"
                  label="128（小文件）"
                />
                <el-option
                  :value="192"
                  label="192（适中）"
                />
                <el-option
                  :value="256"
                  label="256（高质量）"
                />
              </el-select>
            </div>
            <div class="flex flex-col gap-1">
              <span class="text-xs text-dim">循环播放</span>
              <el-switch v-model="loop" />
            </div>
          </div>

          <!-- 时间轴裁剪：一条进度条、两个把手选定区间 -->
          <div class="flex flex-col gap-2">
            <div class="flex items-center justify-between">
              <span class="text-xs text-dim">裁剪区间</span>
              <span
                v-if="!trimDisabled"
                class="font-mono text-sm text-faint"
              >
                {{ formatDuration(trimStart) }} – {{ formatDuration(trimEnd) }} / {{ formatDuration(maxStartTime) }}
              </span>
            </div>
            <el-slider
              v-model="trimRange"
              class="trimmer-slider"
              range
              :min="0"
              :max="maxStartTime"
              :step="1"
              :disabled="trimDisabled"
              :show-tooltip="false"
            />
            <div class="flex items-center justify-between font-mono text-sm text-faint">
              <span>00:00</span>
              <template v-if="!trimDisabled">
                <span>时长 {{ formatDuration(trimDuration) }}</span>
                <span>{{ formatDuration(maxStartTime) }}</span>
              </template>
              <span v-else>缺少时长元数据，将转换全部内容</span>
            </div>
          </div>

          <div class="flex flex-wrap items-center gap-3">
            <el-button
              type="primary"
              :loading="converting"
              :disabled="!videoUrl"
              @click="doConvert"
            >
              {{ converting ? '转换中…' : '转换为 GIF' }}
            </el-button>
            <span
              v-if="videoDuration"
              class="text-xs text-faint"
            >
              视频 {{ formatDuration(videoDuration) }} · {{ videoWidth }}×{{ videoHeight }}
            </span>
          </div>
        </section>

        <!-- 进度 / 结果 -->
        <section
          v-if="converting || gifUrl"
          class="panel flex flex-col gap-3"
        >
          <template v-if="converting">
            <span class="text-sm font-medium text-ink">{{ convertMessage }}</span>
            <el-progress
              :percentage="convertPercent"
              :stroke-width="12"
            />
            <span
              v-if="convertDetail"
              class="font-mono text-xs text-faint"
            >{{ convertDetail }}</span>
          </template>
          <template v-else>
            <div class="flex flex-wrap items-center justify-between gap-2">
              <span class="text-sm font-medium text-ink">转换完成</span>
              <span class="text-xs text-faint">GIF 大小：{{ formatSize(gifSize) }}</span>
            </div>
            <img
              :src="gifUrl"
              alt="GIF 预览"
              class="max-h-[400px] max-w-full rounded-[10px] border border-line"
            >
            <div class="flex flex-wrap gap-2">
              <el-button
                type="primary"
                @click="downloadGif"
              >
                下载 GIF
              </el-button>
              <el-button @click="clearResult">
                重新转换
              </el-button>
            </div>
          </template>
        </section>
      </template>
    </div>
  </ToolShell>
</template>

<style scoped>
/* 时间轴裁剪：覆盖 EP slider 默认样式，做出裁剪工具感。 */
.trimmer-slider :deep(.el-slider__runway) {
  height: 6px;
  margin: 0;
  background-color: var(--color-soft);
}

.trimmer-slider :deep(.el-slider__bar) {
  height: 6px;
  background-color: var(--color-accent);
}

.trimmer-slider :deep(.el-slider__button-wrapper) {
  top: -18px;
}

.trimmer-slider :deep(.el-slider__button) {
  width: 16px;
  height: 16px;
  border: 2px solid #fff;
  border-radius: 9999px;
  background-color: var(--color-accent);
  box-shadow: 0 1px 4px rgb(0 0 0 / 0.4);
  transition: transform 0.12s;
}

.trimmer-slider :deep(.el-slider__button-wrapper:hover .el-slider__button),
.trimmer-slider :deep(.el-slider__button-wrapper.dragging .el-slider__button) {
  transform: scale(1.15);
}

.trimmer-slider.is-disabled :deep(.el-slider__bar),
.trimmer-slider.is-disabled :deep(.el-slider__runway),
.trimmer-slider.is-disabled :deep(.el-slider__button) {
  background-color: var(--color-soft);
  border-color: var(--color-line);
  box-shadow: none;
}
</style>
