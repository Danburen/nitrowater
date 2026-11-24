<script setup lang="ts">
/**
 * Markdown → Word(.docx)：纯浏览器本地转换，文件不上传。
 * 依赖（marked / docx）通过动态 import 惰性加载。
 */
import { computed, ref } from 'vue'
import { saveAs } from 'file-saver'
import { toast } from '../../../shared/ui/toast'
import PageShell from '../../../shared/ui/PageShell.vue'
import TextArea from '../../../shared/ui/TextArea.vue'
import TextField from '../../../shared/ui/TextField.vue'
import { SAMPLE_MARKDOWN } from './lib/sample'

const markdown = ref('')
const fileName = ref('document')
const converting = ref(false)
const fileInput = ref<HTMLInputElement>()

const charCount = computed(() => markdown.value.length)
const canConvert = computed(() => markdown.value.trim().length > 0)

function loadSample(): void {
  markdown.value = SAMPLE_MARKDOWN
}

function clearAll(): void {
  markdown.value = ''
}

function pickFile(): void {
  fileInput.value?.click()
}

async function onPickFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  markdown.value = await file.text()
  const base = file.name.replace(/\.(md|markdown|txt)$/i, '')
  if (base) fileName.value = base
}

async function doConvert(): Promise<void> {
  if (!canConvert.value || converting.value) return
  converting.value = true
  try {
    const { markdownToDocxBlob } = await import('./lib/markdownToDocx')
    const blob = await markdownToDocxBlob(markdown.value)
    let name = fileName.value.trim() || 'document'
    if (!name.toLowerCase().endsWith('.docx')) name += '.docx'
    saveAs(blob, name)
    toast.success('已生成 Word 文档')
  } catch (error) {
    console.error(error)
    toast.error(`转换失败：${error instanceof Error ? error.message : String(error)}`)
  } finally {
    converting.value = false
  }
}
</script>

<template>
  <PageShell
    title="Markdown 转 Word"
    desc="将 Markdown 转换为 .docx Word 文档（标题 / 列表 / 引用 / 代码块 / 表格 / 链接）。全部在浏览器本地完成，内容不会上传。"
  >
    <div class="flex flex-col gap-4">
      <section class="panel flex flex-col gap-3">
        <div class="flex flex-wrap items-center gap-2">
          <input
            ref="fileInput"
            type="file"
            accept=".md,.markdown,.txt,text/markdown,text/plain"
            class="hidden"
            @change="onPickFile"
          >
          <button
            type="button"
            class="btn"
            @click="pickFile"
          >
            选择 .md 文件
          </button>
          <button
            type="button"
            class="btn"
            @click="loadSample"
          >
            载入示例
          </button>
          <button
            type="button"
            class="btn"
            :disabled="!markdown"
            @click="clearAll"
          >
            清空
          </button>
          <span class="ml-auto text-xs text-faint">{{ charCount }} 字符</span>
        </div>

        <TextArea
          v-model="markdown"
          class="resize-y"
          min-height="288px"
          placeholder="# 在此输入 Markdown，或点击“载入示例”"
          aria-label="Markdown 内容"
        />
      </section>

      <section class="panel flex flex-wrap items-center gap-3">
        <span class="text-sm text-dim">文件名</span>
        <TextField
          v-model="fileName"
          class="max-w-[240px]"
          placeholder="document"
        />
        <button
          type="button"
          class="btn btn-primary"
          :disabled="converting || !canConvert"
          @click="doConvert"
        >
          {{ converting ? '转换中…' : '转换并下载 .docx' }}
        </button>
        <span class="text-xs text-faint">支持标题、列表、引用、代码块、表格、粗体/斜体、链接</span>
      </section>
    </div>
  </PageShell>
</template>
