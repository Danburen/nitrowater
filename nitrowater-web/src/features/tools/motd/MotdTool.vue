<script setup lang="ts">
/**
 * MOTD editor / preview.
 *
 * Parsing is shared with the rest of the Minecraft tools (`lib/mc/motd.ts`); this component only
 * owns the editor state, palette, preview and copy/download actions.
 */
import { computed, ref } from 'vue'
import PageShell from '../../../shared/ui/PageShell.vue'
import TextArea from '../../../shared/ui/TextArea.vue'
import TextEditor from '../../../shared/ui/TextEditor.vue'
import { toast } from '../../../shared/ui/toast'
import { copyText, downloadText } from '../lib/mc/download'
import {
  formatCodes,
  hasCustomHexColor,
  legacyColors,
  parseMotd,
  SERVER_LIST_LINE_CHARS,
  toJson,
  toLegacy,
  toPlain,
  toProperties,
} from '../lib/mc/motd'

const DEFAULT_MOTD = '&a&lNitroWater &7| &f为 Minecraft 腐竹提供一站式服务\n&e开服 · 配置 · 插件 · 排障'

const outputTabs = [
  { key: 'legacy', label: 'legacy' },
  { key: 'properties', label: 'server.properties' },
  { key: 'json', label: 'JSON text' },
] as const

interface TextEditorHandle {
  wrapSelection: (prefix: string, suffix?: string) => void
  insertText: (text: string) => void
  insertAfterSelection: (text: string) => void
}

const input = ref(DEFAULT_MOTD)
const escapeUnicode = ref(true)
const tab = ref<'legacy' | 'properties' | 'json'>('legacy')
const hexColor = ref('#55ff55')
const editor = ref<TextEditorHandle | null>(null)

const hexCodeLabel = computed(() => `&#${hexColor.value.replace('#', '').toUpperCase()}`)
const parsed = computed(() => parseMotd(input.value))
/** Hex outside the 16 vanilla colors: fine for JSON, not parseable by a vanilla server. */
const customHex = computed(() => hasCustomHexColor(parsed.value.lines))
/** Per-line character usage, so "too long for the server list" is visible while typing. */
const lineStats = computed(() =>
  parsed.value.lines.map((line, index) => {
    const length = line.reduce((total, segment) => total + segment.text.length, 0)
    return { line: index + 1, length, over: length > SERVER_LIST_LINE_CHARS }
  }),
)
const legacy = computed(() => toLegacy(parsed.value.lines))
const properties = computed(() => toProperties(parsed.value.lines, escapeUnicode.value))
const json = computed(() => toJson(parsed.value.lines))
const plain = computed(() => toPlain(parsed.value.lines))

const activeOutput = computed(() => {
  if (tab.value === 'properties') return properties.value
  if (tab.value === 'json') return json.value
  return legacy.value
})

const outputLabel = computed(() => {
  if (tab.value === 'properties') return 'server.properties 行'
  if (tab.value === 'json') return 'JSON text 组件'
  return 'legacy 字符串'
})

const outputFilename = computed(() => {
  if (tab.value === 'properties') return 'motd.properties.txt'
  if (tab.value === 'json') return 'motd.json'
  return 'motd.txt'
})

/** Wrap selected text with a color code; with no selection only insert the code. */
function insertColor(code: string): void {
  editor.value?.wrapSelection(code, '&r')
}

/** Formatting codes wrap a selection too; reset has no meaningful wrapper. */
function insertFormat(code: string): void {
  if (code === '&r') editor.value?.insertAfterSelection('&r')
  else editor.value?.wrapSelection(code, '&r')
}

function insertHex(): void {
  editor.value?.wrapSelection(`&#${hexColor.value.replace('#', '').toUpperCase()}`, '&r')
}

function insertNewline(): void {
  editor.value?.insertText('\n')
}

async function copy(value: string, label: string): Promise<void> {
  const ok = await copyText(value)
  if (ok) toast.success(`已复制${label}`)
  else toast.error('复制失败，请手动选择复制')
}

function download(): void {
  if (tab.value === 'json') {
    downloadText(outputFilename.value, json.value, 'application/json;charset=utf-8')
  } else if (tab.value === 'properties') {
    downloadText(outputFilename.value, `${properties.value}\n`)
  } else {
    downloadText(outputFilename.value, legacy.value)
  }
}

function reset(): void {
  input.value = DEFAULT_MOTD
  tab.value = 'legacy'
}
</script>

<template>
  <PageShell
    title="MOTD 格式化预览"
    desc="用 & / § 颜色代码编辑服务器 MOTD，实时预览两行效果，并导出 legacy / server.properties / JSON。全部本地处理。"
  >
    <div class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_360px]">
      <!-- Editor -->
      <section class="panel flex flex-col gap-3">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h2 class="text-sm font-semibold text-ink">
              MOTD 输入
            </h2>
            <p class="mt-0.5 text-[12.5px] text-dim">
              支持 &amp; / § 代码、16 色与十六进制色、换行
            </p>
          </div>
          <button
            type="button"
            class="btn"
            @click="reset"
          >
            恢复示例
          </button>
        </div>

        <TextEditor
          ref="editor"
          v-model="input"
          min-height="132px"
          placeholder="例如：&#10;&a&lNitroWater &7| &f为腐竹提供一站式服务&#10;&e开服 · 配置 · 插件 · 排障"
          aria-label="MOTD 输入"
        />

        <div class="space-y-2">
          <div class="flex flex-wrap items-center gap-1.5">
            <span class="mr-1 text-[12px] text-faint">颜色</span>
            <button
              v-for="color in legacyColors"
              :key="color.code"
              type="button"
              class="size-6 rounded-md border border-line-strong transition hover:scale-110"
              :style="{ backgroundColor: color.hex }"
              :title="`${color.label}（&${color.code}）`"
              :aria-label="`插入${color.label}颜色`"
              @click="insertColor(`&${color.code}`)"
            />
          </div>

          <div class="flex flex-wrap items-center gap-1.5">
            <span class="mr-1 text-[12px] text-faint">格式</span>
            <button
              v-for="format in formatCodes"
              :key="format.code"
              type="button"
              class="grid size-7 place-items-center rounded-md border border-line bg-soft text-[12px] font-semibold text-ink transition hover:border-line-strong hover:text-accent"
              :title="`${format.title}（&${format.code}）`"
              :aria-label="format.title"
              @click="insertFormat(`&${format.code}`)"
            >
              {{ format.label }}
            </button>
            <button
              type="button"
              class="btn px-2.5 py-1 text-[12px]"
              title="插入换行"
              @click="insertNewline"
            >
              换行
            </button>
          </div>

          <div class="flex flex-wrap items-center gap-2">
            <span class="text-[12px] text-faint">十六进制</span>
            <input
              v-model="hexColor"
              type="color"
              class="h-7 w-9 cursor-pointer rounded border border-line bg-bg"
              aria-label="选择十六进制颜色"
            >
            <code class="rounded bg-soft px-1.5 py-0.5 text-[11.5px] text-dim">{{ hexCodeLabel }}</code>
            <button
              type="button"
              class="btn px-2.5 py-1 text-[12px]"
              @click="insertHex"
            >
              插入颜色
            </button>
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-2 border-t border-line pt-3">
          <button
            type="button"
            class="btn"
            @click="copy(plain, '纯文本')"
          >
            复制纯文本
          </button>
          <button
            type="button"
            class="btn"
            @click="copy(legacy, 'legacy 字符串')"
          >
            复制 legacy
          </button>
        </div>

        <div
          v-if="parsed.usesAmpersand"
          class="rounded-[9px] border border-warn/30 bg-warn/10 px-3 py-2 text-[12.5px] text-warn"
        >
          原版 <code>server.properties</code> 只识别 <code>§</code>；<code>&amp;</code> 需要服务端/插件支持。
        </div>
        <div
          v-for="warning in parsed.warnings"
          :key="warning"
          class="rounded-[9px] border border-warn/30 bg-warn/10 px-3 py-2 text-[12.5px] text-warn"
        >
          {{ warning }}
        </div>
      </section>

      <!-- Preview -->
      <section class="panel flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <h2 class="text-sm font-semibold text-ink">
            服务器列表预览
          </h2>
          <span class="tag">近似效果</span>
        </div>

        <div class="rounded-[10px] border border-line bg-[#0d1014] p-3.5 text-[#e6e8ec]">
          <div class="flex items-center gap-3">
            <div class="grid size-9 shrink-0 place-items-center rounded-[5px] bg-gradient-to-br from-[#6bbf59] to-[#2f7a3d] text-[13px] font-bold text-white shadow-inner">
              N
            </div>
            <div class="min-w-0 flex-1">
              <div class="truncate text-[13px] font-semibold">
                NitroWater Server
              </div>
              <div class="mt-0.5 text-[11.5px] text-[#9aa1ad]">
                0/20 · 正版验证
              </div>
            </div>
            <div class="flex items-end gap-0.5">
              <span class="h-1.5 w-0.5 rounded-sm bg-[#6fcf8f]" />
              <span class="h-2.5 w-0.5 rounded-sm bg-[#6fcf8f]" />
              <span class="h-3.5 w-0.5 rounded-sm bg-[#6fcf8f]" />
              <span class="h-[18px] w-0.5 rounded-sm bg-[#6fcf8f]" />
              <span class="h-5 w-0.5 rounded-sm bg-[#3a4150]" />
            </div>
          </div>

          <div class="mt-3 space-y-0.5 border-t border-white/10 pt-3 font-mono text-[12.5px] leading-relaxed">
            <div
              v-for="(line, lineIndex) in parsed.lines"
              :key="lineIndex"
              class="min-h-[1.35em] break-words whitespace-pre-wrap"
            >
              <template v-if="line.length === 0">
                &nbsp;
              </template>
              <span
                v-for="(segment, segmentIndex) in line"
                :key="segmentIndex"
                :class="{ 'motd-obfuscated': segment.obfuscated }"
                :style="{
                  color: segment.color ?? undefined,
                  fontWeight: segment.bold ? 700 : 400,
                  fontStyle: segment.italic ? 'italic' : 'normal',
                  textDecoration: [segment.underline ? 'underline' : '', segment.strikethrough ? 'line-through' : ''].filter(Boolean).join(' ') || 'none',
                }"
                :title="segment.obfuscated ? '混淆文字：游戏内会随机变化' : undefined"
              >{{ segment.text }}</span>
            </div>
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-1.5">
          <span
            v-for="stat in lineStats"
            :key="stat.line"
            class="rounded-full border px-2 py-0.5 text-[11.5px] tabular-nums"
            :class="stat.over ? 'border-warn/50 bg-warn/10 text-warn' : 'border-line text-faint'"
          >
            第 {{ stat.line }} 行 {{ stat.length }}/{{ SERVER_LIST_LINE_CHARS }}
          </span>
        </div>

        <p class="text-[12px] leading-relaxed text-faint">
          预览使用常见客户端字体近似渲染；游戏内最终效果以服务端和客户端为准。服务器列表通常只显示前 2 行。
        </p>
      </section>
    </div>

    <!-- Output -->
    <section class="panel mt-4 flex flex-col gap-3">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div class="flex flex-wrap items-center gap-1.5">
          <button
            v-for="item in outputTabs"
            :key="item.key"
            type="button"
            class="rounded-[8px] border px-3 py-1.5 text-[12.5px] transition"
            :class="tab === item.key ? 'border-accent bg-accent/10 text-accent' : 'border-line bg-soft text-dim hover:text-ink'"
            @click="tab = item.key"
          >
            {{ item.label }}
          </button>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <label
            v-if="tab === 'properties'"
            class="inline-flex cursor-pointer items-center gap-2 text-[12.5px] text-dim"
          >
            <input
              v-model="escapeUnicode"
              type="checkbox"
              class="accent-accent"
            >
            转义非 ASCII（推荐）
          </label>
          <button
            type="button"
            class="btn"
            @click="copy(activeOutput, outputLabel)"
          >
            复制
          </button>
          <button
            type="button"
            class="btn btn-primary"
            @click="download"
          >
            下载 {{ outputFilename }}
          </button>
        </div>
      </div>

      <div
        v-if="customHex && tab !== 'json'"
        class="rounded-[9px] border border-warn/30 bg-warn/10 px-3 py-2 text-[12px] leading-relaxed text-warn"
      >
        含自定义十六进制颜色：原版服务端不解析 <code>§x</code> 语法，<code>server.properties</code> 里会变成乱码；
        请改用 16 色，或部署 Paper / Purpur 等使用 Adventure 的服务端。JSON 输出不受影响。
      </div>

      <TextArea
        :model-value="activeOutput"
        readonly
        min-height="180px"
        :aria-label="outputLabel"
      />

      <p class="text-[12px] leading-relaxed text-faint">
        <template v-if="tab === 'properties'">
          输出为 <code>motd=...</code> 行；已转义换行与 <code>§</code>。直接粘贴到 server.properties 或与现有文件合并。
          <br>
          现代 Java 版按 UTF-8 读取该文件，转义非 ASCII 只是为了兼容旧版或让文件保持纯 ASCII。
        </template>
        <template v-else-if="tab === 'json'">
          JSON text 组件适用于 <code>/tellraw</code>、书、告示牌等场景；十六进制颜色需要 1.16+，旧版请用 legacy 输出。
        </template>
        <template v-else>
          legacy 字符串使用 <code>§</code> 格式；可粘贴到支持该格式的服务端配置或插件文本中。
          原版只认 16 色，十六进制色（<code>§x…</code>）需要 Paper / Purpur 等 Adventure 服务端。
        </template>
      </p>
    </section>
  </PageShell>
</template>

<style scoped>
.motd-obfuscated {
  text-decoration: underline dotted;
  text-underline-offset: 2px;
}
</style>
