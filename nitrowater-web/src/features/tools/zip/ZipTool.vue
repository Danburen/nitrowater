<script setup lang="ts">
/**
 * ZIP compress / multi-format extract.
 * Ported from wtools (MIT) `src/components/ZipTool.vue`; UI rewritten with Tailwind.
 */
import { Delete, Download, FolderOpened } from '@element-plus/icons-vue'
import { computed, ref } from 'vue'
import { saveAs } from 'file-saver'
import { toast } from '../../../shared/ui/toast'
import ToolShell from '../ToolShell.vue'
import { ARCHIVE_EXTENSIONS, decompressArchive } from '../lib/archive/libarchive'
import { compressZip, decompressZip } from '../lib/archive/zip'

interface PickedFile {
  file: File
  name: string
  size: number
}

interface ExtractedFile {
  name: string
  size: number
  data: Uint8Array
}

const tab = ref<'compress' | 'decompress'>('compress')

const compressFiles = ref<PickedFile[]>([])
const zipFileName = ref('')
const folderInput = ref<HTMLInputElement>()

function pickFolder(): void {
  folderInput.value?.click()
}
const isCompressing = ref(false)
const compressProgress = ref(0)

const singleArchive = ref<File | null>(null)
const isDecompressing = ref(false)
const decompressProgress = ref(0)
const extractedFiles = ref<ExtractedFile[]>([])

const accept = computed(() => ARCHIVE_EXTENSIONS.join(','))

function prettySize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

function onPickFiles(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  compressFiles.value = files.map((file) => ({ file, name: file.name, size: file.size }))
  input.value = ''
}

function onPickFolder(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  compressFiles.value = files.map((file) => ({
    file,
    name: file.webkitRelativePath || file.name,
    size: file.size,
  }))
  input.value = ''
}

function clearFiles(): void {
  compressFiles.value = []
  compressProgress.value = 0
}

async function doCompress(): Promise<void> {
  if (compressFiles.value.length === 0) return
  isCompressing.value = true
  compressProgress.value = 0
  try {
    const content = await compressZip(
      compressFiles.value.map((item) => ({ raw: item.file, path: item.name })),
      (percent) => { compressProgress.value = percent },
    )
    let name = zipFileName.value.trim() || 'archive'
    if (!name.endsWith('.zip')) name += '.zip'
    saveAs(content, name)
    toast.success('压缩完成并已下载')
  } catch (error) {
    console.error(error)
    toast.error('压缩过程中出现错误')
  } finally {
    isCompressing.value = false
    setTimeout(() => { compressProgress.value = 0 }, 3000)
  }
}

function onPickArchive(event: Event): void {
  const input = event.target as HTMLInputElement
  singleArchive.value = input.files?.[0] ?? null
  extractedFiles.value = []
  decompressProgress.value = 0
  input.value = ''
}

async function doDecompress(): Promise<void> {
  const file = singleArchive.value
  if (!file) return
  isDecompressing.value = true
  decompressProgress.value = 0
  extractedFiles.value = []
  try {
    const onProgress = (percent: number) => { decompressProgress.value = percent }
    const files = file.name.toLowerCase().endsWith('.zip')
      ? await decompressZip(file, onProgress)
      : await decompressArchive(file, onProgress)
    extractedFiles.value = files
    toast.success(`解压完成，共 ${files.length} 个文件`)
  } catch (error) {
    console.error(error)
    toast.error('解压失败，请确认是否为正确的压缩文件')
  } finally {
    isDecompressing.value = false
  }
}

function downloadExtracted(file: ExtractedFile): void {
  const blob = new Blob([new Uint8Array(file.data)])
  saveAs(blob, file.name.split('/').pop() || file.name)
}
</script>

<template>
  <ToolShell
    title="压缩 / 解压"
    desc="ZIP 打包下载；ZIP / RAR / 7z / TAR / GZ / XZ / BZ2 解压。全部在浏览器本地完成，文件不会上传。"
  >
    <div class="mb-4 inline-flex rounded-[10px] border border-line bg-card p-1">
      <button
        class="rounded-lg px-4 py-1.5 text-sm font-medium transition"
        :class="tab === 'compress' ? 'bg-accent text-white' : 'text-dim hover:text-ink'"
        @click="tab = 'compress'"
      >
        压缩文件
      </button>
      <button
        class="rounded-lg px-4 py-1.5 text-sm font-medium transition"
        :class="tab === 'decompress' ? 'bg-accent text-white' : 'text-dim hover:text-ink'"
        @click="tab = 'decompress'"
      >
        解压文件
      </button>
    </div>

    <!-- compress -->
    <section
      v-if="tab === 'compress'"
      class="panel flex flex-col gap-4"
    >
      <label class="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-[12px] border border-dashed border-line bg-bg/40 px-6 py-10 text-center transition hover:border-accent/50">
        <svg
          class="size-7 text-accent"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M12 16V4m0 0L8 8m4-4 4 4M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
        </svg>
        <span class="text-sm text-dim">拖拽或 <em class="not-italic text-accent">点击选择文件</em></span>
        <input
          type="file"
          multiple
          class="hidden"
          @change="onPickFiles"
        >
      </label>

      <div class="flex flex-wrap items-center gap-2">
        <input
          ref="folderInput"
          type="file"
          multiple
          class="hidden"
          webkitdirectory
          @change="onPickFolder"
        >
        <el-button
          :icon="FolderOpened"
          @click="pickFolder"
        >
          选择文件夹
        </el-button>
        <el-button
          v-if="compressFiles.length"
          :icon="Delete"
          @click="clearFiles"
        >
          清空 ({{ compressFiles.length }})
        </el-button>
      </div>

      <ul
        v-if="compressFiles.length"
        class="max-h-52 overflow-auto rounded-[10px] border border-line bg-bg/40 p-2 text-sm"
      >
        <li
          v-for="(item, index) in compressFiles"
          :key="index"
          class="flex items-center justify-between gap-3 px-2 py-1"
        >
          <span class="truncate text-dim">{{ item.name }}</span>
          <span class="shrink-0 font-mono text-xs text-faint">{{ prettySize(item.size) }}</span>
        </li>
      </ul>

      <div
        v-if="compressFiles.length"
        class="flex flex-wrap items-center gap-2"
      >
        <input
          v-model="zipFileName"
          class="input max-w-[260px]"
          placeholder="压缩包名（默认 archive.zip）"
        >
        <el-button
          type="primary"
          :icon="Download"
          :loading="isCompressing"
          @click="doCompress"
        >
          压缩并下载
        </el-button>
      </div>

      <div
        v-if="compressProgress > 0"
        class="h-2 overflow-hidden rounded-full bg-bg/60"
      >
        <div
          class="h-full bg-ok transition-all"
          :style="{ width: `${compressProgress}%` }"
        />
      </div>
    </section>

    <!-- decompress -->
    <section
      v-else
      class="panel flex flex-col gap-4"
    >
      <label class="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-[12px] border border-dashed border-line bg-bg/40 px-6 py-10 text-center transition hover:border-accent/50">
        <svg
          class="size-7 text-accent"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M12 4v12m0 0 4-4m-4 4-4-4M4 18h16" />
        </svg>
        <span class="text-sm text-dim">拖拽或 <em class="not-italic text-accent">点击选择压缩文件</em></span>
        <span class="font-mono text-xs text-faint">{{ accept }}</span>
        <input
          type="file"
          :accept="accept"
          class="hidden"
          @change="onPickArchive"
        >
      </label>

      <div
        v-if="singleArchive"
        class="flex flex-wrap items-center gap-3"
      >
        <span class="truncate text-sm text-dim">{{ singleArchive.name }}</span>
        <el-button
          type="primary"
          :icon="Download"
          :loading="isDecompressing"
          @click="doDecompress"
        >
          提取内容
        </el-button>
      </div>

      <div
        v-if="decompressProgress > 0"
        class="h-2 overflow-hidden rounded-full bg-bg/60"
      >
        <div
          class="h-full bg-warn transition-all"
          :style="{ width: `${decompressProgress}%` }"
        />
      </div>

      <div
        v-if="extractedFiles.length"
        class="overflow-hidden rounded-[10px] border border-line"
      >
        <table class="w-full text-left text-sm">
          <thead class="bg-bg/40 text-xs uppercase tracking-wide text-faint">
            <tr>
              <th class="px-3 py-2 font-medium">
                文件名
              </th>
              <th class="px-3 py-2 font-medium">
                大小
              </th>
              <th class="px-3 py-2" />
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(file, index) in extractedFiles"
              :key="index"
              class="border-t border-line"
            >
              <td class="max-w-0 truncate px-3 py-2 text-dim">
                {{ file.name }}
              </td>
              <td class="whitespace-nowrap px-3 py-2 font-mono text-xs text-faint">
                {{ prettySize(file.size) }}
              </td>
              <td class="px-3 py-2 text-right">
                <el-button
                  size="small"
                  @click="downloadExtracted(file)"
                >
                  下载
                </el-button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </ToolShell>
</template>
