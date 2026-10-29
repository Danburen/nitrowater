<script setup lang="ts">
/**
 * server-icon.png generator.
 *
 * The preview canvas is already 64x64 (Minecraft's fixed icon size); CSS only scales it up with
 * pixelated rendering. Dragging pans the source image, the zoom slider scales it, and the
 * background can be transparent or a solid color.
 */
import { computed, ref, watch } from 'vue'
import PageShell from '../../../shared/ui/PageShell.vue'
import { toast } from '../../../shared/ui/toast'
import { downloadBlob } from '../lib/mc/download'

const backgrounds = [
  { value: 'transparent', label: '透明' },
  { value: '#ffffff', label: '白色' },
  { value: '#000000', label: '黑色' },
  { value: '#1f2937', label: '深灰' },
  { value: '#14532d', label: '深绿' },
] as const

const preview = ref<HTMLCanvasElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const source = ref<HTMLImageElement | null>(null)
const fileName = ref('')
const zoom = ref(1)
const offsetX = ref(0)
const offsetY = ref(0)
const background = ref<string>('transparent')

const hasImage = computed(() => source.value !== null)
const sourceInfo = computed(() => {
  const image = source.value
  return image ? `${image.naturalWidth} × ${image.naturalHeight}` : '未选择图片'
})

let dragging = false
let lastX = 0
let lastY = 0

function draw(): void {
  const canvas = preview.value
  if (!canvas) return
  const context = canvas.getContext('2d')
  if (!context) return

  context.clearRect(0, 0, 64, 64)
  if (background.value !== 'transparent') {
    context.fillStyle = background.value
    context.fillRect(0, 0, 64, 64)
  }

  const image = source.value
  if (!image) return
  const scale = Math.max(64 / image.naturalWidth, 64 / image.naturalHeight) * zoom.value
  const width = image.naturalWidth * scale
  const height = image.naturalHeight * scale
  const x = (64 - width) / 2 + offsetX.value
  const y = (64 - height) / 2 + offsetY.value

  context.imageSmoothingEnabled = true
  context.imageSmoothingQuality = 'high'
  context.drawImage(image, x, y, width, height)
}

function clampOffsets(): void {
  const image = source.value
  if (!image) {
    offsetX.value = 0
    offsetY.value = 0
    return
  }
  const scale = Math.max(64 / image.naturalWidth, 64 / image.naturalHeight) * zoom.value
  const maxX = Math.max(0, (image.naturalWidth * scale - 64) / 2)
  const maxY = Math.max(0, (image.naturalHeight * scale - 64) / 2)
  offsetX.value = Math.min(maxX, Math.max(-maxX, offsetX.value))
  offsetY.value = Math.min(maxY, Math.max(-maxY, offsetY.value))
}

function center(): void {
  offsetX.value = 0
  offsetY.value = 0
  draw()
}

function resetView(): void {
  zoom.value = 1
  offsetX.value = 0
  offsetY.value = 0
  draw()
}

function onPointerDown(event: PointerEvent): void {
  if (!hasImage.value) return
  event.preventDefault()
  dragging = true
  lastX = event.clientX
  lastY = event.clientY
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

function onPointerMove(event: PointerEvent): void {
  if (!dragging) return
  event.preventDefault()
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const scale = 64 / rect.width
  offsetX.value += (event.clientX - lastX) * scale
  offsetY.value += (event.clientY - lastY) * scale
  lastX = event.clientX
  lastY = event.clientY
  clampOffsets()
  draw()
}

function onPointerUp(): void {
  dragging = false
}

function pickFile(): void {
  fileInput.value?.click()
}

async function handleFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    toast.error('请选择图片文件')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    toast.error('图片不能超过 10 MB')
    return
  }

  const url = URL.createObjectURL(file)
  const image = new Image()
  image.onload = () => {
    source.value = image
    fileName.value = file.name
    zoom.value = 1
    offsetX.value = 0
    offsetY.value = 0
    URL.revokeObjectURL(url)
    draw()
    toast.success('图片已载入，拖动预览可调整位置')
  }
  image.onerror = () => {
    URL.revokeObjectURL(url)
    toast.error('无法读取这张图片')
  }
  image.src = url
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  return `${(bytes / 1024).toFixed(1)} KB`
}

function exportIcon(): void {
  const canvas = preview.value
  if (!canvas || !hasImage.value) return
  canvas.toBlob((blob) => {
    if (!blob) {
      toast.error('生成 PNG 失败，请重试')
      return
    }
    downloadBlob('server-icon.png', blob)
    toast.success(`已生成 server-icon.png（${formatBytes(blob.size)}）`)
  }, 'image/png')
}

watch(zoom, () => {
  clampOffsets()
  draw()
})

watch(background, () => {
  draw()
})
</script>

<template>
  <PageShell
    title="server-icon 生成"
    desc="上传图片，拖动 / 缩放裁切成 Minecraft 服务器图标（64×64 PNG），支持透明或纯色背景。全部在浏览器本地处理。"
  >
    <div class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_380px]">
      <!-- Preview -->
      <section class="panel flex flex-col gap-4">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h2 class="text-sm font-semibold text-ink">
              64 × 64 预览
            </h2>
            <p class="mt-0.5 text-[12px] text-faint">
              拖动平移 · 滚轮/滑块缩放 · 双击居中
            </p>
          </div>
          <span class="tag">{{ hasImage ? sourceInfo : '等待图片' }}</span>
        </div>

        <div class="flex justify-center">
          <div
            class="relative aspect-square w-full max-w-[320px] overflow-hidden rounded-[14px] border border-line-strong"
            :class="background === 'transparent' ? 'icon-checker' : ''"
            :style="background === 'transparent' ? undefined : { backgroundColor: background }"
          >
            <canvas
              ref="preview"
              width="64"
              height="64"
              class="h-full w-full touch-none select-none"
              :class="hasImage ? 'cursor-grab active:cursor-grabbing' : 'cursor-default'"
              style="image-rendering: pixelated"
              aria-label="server-icon 预览"
              @pointerdown="onPointerDown"
              @pointermove="onPointerMove"
              @pointerup="onPointerUp"
              @pointercancel="onPointerUp"
              @dblclick="center"
            />
            <div
              v-if="!hasImage"
              class="pointer-events-none absolute inset-0 grid place-items-center"
            >
              <div class="text-center">
                <div class="mx-auto grid size-10 place-items-center rounded-full bg-soft text-dim">
                  +
                </div>
                <p class="mt-2 text-[12.5px] text-dim">
                  选择一张图片开始
                </p>
              </div>
            </div>
          </div>
        </div>

        <div class="flex flex-wrap items-center justify-center gap-2">
          <button
            type="button"
            class="btn"
            @click="pickFile"
          >
            {{ hasImage ? '更换图片' : '选择图片' }}
          </button>
          <button
            type="button"
            class="btn"
            :disabled="!hasImage"
            @click="center"
          >
            居中
          </button>
          <button
            type="button"
            class="btn"
            :disabled="!hasImage"
            @click="resetView"
          >
            重置缩放
          </button>
        </div>

        <p class="text-center text-[12px] leading-relaxed text-faint">
          非正方形图片会按短边裁切；建议使用正方形原图，避免主体被裁掉。
        </p>
      </section>

      <!-- Controls -->
      <section class="panel flex flex-col gap-4">
        <div>
          <h2 class="text-sm font-semibold text-ink">
            图标设置
          </h2>
          <p class="mt-0.5 text-[12.5px] text-dim">
            输出固定为 64×64 PNG
          </p>
        </div>

        <input
          ref="fileInput"
          type="file"
          accept="image/*"
          class="hidden"
          @change="handleFile"
        >

        <div
          v-if="hasImage"
          class="rounded-[9px] border border-line bg-soft/60 px-3 py-2 text-[12px] text-dim"
        >
          <p class="truncate text-ink">
            {{ fileName }}
          </p>
          <p class="mt-0.5">
            原图 {{ sourceInfo }} · 输出 64 × 64
          </p>
        </div>

        <div class="space-y-2">
          <div class="flex items-center justify-between text-[12.5px]">
            <label
              for="icon-zoom"
              class="text-dim"
            >缩放</label>
            <span class="text-ink">{{ zoom.toFixed(2) }}×</span>
          </div>
          <input
            id="icon-zoom"
            v-model.number="zoom"
            type="range"
            min="1"
            max="4"
            step="0.05"
            class="w-full accent-accent"
            :disabled="!hasImage"
          >
          <p class="text-[11.5px] text-faint">
            1× 表示刚好铺满画布；放大后可拖动选择显示区域。
          </p>
        </div>

        <div class="space-y-2">
          <span class="text-[12.5px] text-dim">背景</span>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="item in backgrounds"
              :key="item.value"
              type="button"
              class="flex items-center gap-1.5 rounded-[8px] border px-2 py-1.5 text-[12px] transition"
              :class="background === item.value ? 'border-accent text-accent' : 'border-line text-dim hover:text-ink'"
              @click="background = item.value"
            >
              <span
                class="size-3.5 rounded-[3px] border border-line-strong"
                :class="item.value === 'transparent' ? 'icon-checker' : ''"
                :style="item.value === 'transparent' ? undefined : { backgroundColor: item.value }"
              />
              {{ item.label }}
            </button>
          </div>
        </div>

        <div class="border-t border-line pt-4">
          <button
            type="button"
            class="btn btn-primary w-full"
            :disabled="!hasImage"
            @click="exportIcon"
          >
            下载 server-icon.png
          </button>
          <p class="mt-2 text-[11.5px] leading-relaxed text-faint">
            把生成的 PNG 放到服务器根目录（与 server.properties 同级），重启后生效。
          </p>
        </div>
      </section>
    </div>
  </PageShell>
</template>

<style scoped>
.icon-checker {
  background-image:
    linear-gradient(45deg, #2a2f38 25%, transparent 25%),
    linear-gradient(-45deg, #2a2f38 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #2a2f38 75%),
    linear-gradient(-45deg, transparent 75%, #2a2f38 75%);
  background-size: 16px 16px;
  background-position: 0 0, 0 8px, 8px -8px, -8px 0;
}
</style>
