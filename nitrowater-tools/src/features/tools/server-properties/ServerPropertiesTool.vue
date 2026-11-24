<script setup lang="ts">
/**
 * server.properties generator / validator.
 *
 * The component keeps a full default map so the output is always a complete file. Importing an
 * existing file preserves unknown keys instead of silently dropping them.
 */
import { computed, ref } from 'vue'
import FieldShell from '../../../shared/ui/FieldShell.vue'
import PageShell from '../../../shared/ui/PageShell.vue'
import TextArea from '../../../shared/ui/TextArea.vue'
import TextEditor from '../../../shared/ui/TextEditor.vue'
import TextField from '../../../shared/ui/TextField.vue'
import { toast } from '../../../shared/ui/toast'
import { copyText, downloadText } from '../lib/mc/download'
import {
  applyPropertyPreset,
  createDefaultProperties,
  parseProperties,
  propertyFields,
  propertyGroups,
  propertyPresets,
  serializeProperties,
  validateProperties,
  type PropertyGroup,
  type PropertyPreset,
} from '../lib/mc/properties'

const values = ref<Record<string, string>>(createDefaultProperties())
const unknown = ref<Array<{ key: string; value: string }>>([])
const warnings = ref<string[]>([])
const escapeUnicode = ref(true)
const activeGroup = ref<PropertyGroup>('基础')
const showImport = ref(false)
const importDraft = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

const errors = computed(() => validateProperties(values.value))
const errorCount = computed(() => Object.keys(errors.value).length)
const fieldsInGroup = computed(() => propertyFields.filter((field) => field.group === activeGroup.value))
const output = computed(() => serializeProperties(values.value, unknown.value, escapeUnicode.value))
const lineCount = computed(() => output.value.trimEnd().split('\n').length)
const unknownPreview = computed(() => {
  const names = unknown.value.slice(0, 6).map((entry) => entry.key).join('、')
  return unknown.value.length > 6 ? `${names} 等` : names
})

function groupErrors(group: PropertyGroup): number {
  return propertyFields.filter((field) => field.group === group && errors.value[field.key]).length
}

function datalistId(key: string): string {
  return `prop-list-${key.replace(/[^a-z0-9-]/gi, '-')}`
}

function applyPreset(preset: PropertyPreset): void {
  values.value = applyPropertyPreset(preset)
  unknown.value = []
  warnings.value = []
  toast.success(`已应用${propertyPresets.find((item) => item.key === preset)?.label ?? ''}预设`)
}

function reset(): void {
  values.value = createDefaultProperties()
  unknown.value = []
  warnings.value = []
  toast.success('已恢复默认配置')
}

function doImport(): void {
  if (!importDraft.value.trim()) {
    toast.error('请先粘贴 server.properties 内容')
    return
  }
  const result = parseProperties(importDraft.value)
  values.value = result.values
  unknown.value = result.unknown
  warnings.value = result.warnings
  importDraft.value = ''
  showImport.value = false
  toast.success(`已导入配置${result.unknown.length ? `，保留 ${result.unknown.length} 个未知键` : ''}`)
}

function pickFile(): void {
  fileInput.value?.click()
}

async function handleFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 1024 * 1024) {
    toast.error('文件超过 1 MB，请确认这是 server.properties')
    input.value = ''
    return
  }
  importDraft.value = await file.text()
  input.value = ''
  doImport()
}

async function copy(): Promise<void> {
  if (errorCount.value > 0) {
    toast.error('请先修正校验错误')
    return
  }
  const ok = await copyText(output.value)
  if (ok) toast.success('已复制 server.properties')
  else toast.error('复制失败，请手动选择复制')
}

function download(): void {
  if (errorCount.value > 0) {
    toast.error('请先修正校验错误')
    return
  }
  downloadText('server.properties', output.value)
}
</script>

<template>
  <PageShell
    title="server.properties 生成 / 校验"
    desc="按分组生成完整的 Java 版 server.properties，支持预设、导入现有文件、保留未知键与实时校验。全部本地处理。"
  >
    <div class="flex flex-col gap-3">
      <!-- Toolbar -->
      <div class="panel flex flex-wrap items-center justify-between gap-3">
        <div class="flex flex-wrap items-center gap-2">
          <span class="text-[12px] text-faint">预设</span>
          <button
            v-for="preset in propertyPresets"
            :key="preset.key"
            type="button"
            class="btn"
            :title="preset.desc"
            @click="applyPreset(preset.key)"
          >
            {{ preset.label }}
          </button>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <input
            ref="fileInput"
            type="file"
            accept=".properties,.txt,text/plain"
            class="hidden"
            @change="handleFile"
          >
          <button
            type="button"
            class="btn"
            @click="pickFile"
          >
            选择文件
          </button>
          <button
            type="button"
            class="btn"
            @click="showImport = !showImport"
          >
            {{ showImport ? '收起导入' : '粘贴导入' }}
          </button>
          <button
            type="button"
            class="btn"
            @click="reset"
          >
            恢复默认
          </button>
        </div>
      </div>

      <!-- Import -->
      <section
        v-if="showImport"
        class="panel flex flex-col gap-3"
      >
        <div>
          <h2 class="text-sm font-semibold text-ink">
            导入现有 server.properties
          </h2>
          <p class="mt-0.5 text-[12.5px] text-dim">
            已知键会覆盖当前表单；未知键会保留并写入导出文件。
          </p>
        </div>
        <TextEditor
          v-model="importDraft"
          min-height="120px"
          placeholder="粘贴 server.properties 内容，例如：&#10;server-port=25565&#10;motd=A Minecraft Server"
          aria-label="导入 server.properties 内容"
        />
        <div class="flex justify-end gap-2">
          <button
            type="button"
            class="btn"
            @click="importDraft = ''"
          >
            清空
          </button>
          <button
            type="button"
            class="btn btn-primary"
            @click="doImport"
          >
            导入配置
          </button>
        </div>
      </section>

      <!-- Warnings -->
      <div
        v-if="warnings.length"
        class="rounded-[10px] border border-warn/30 bg-warn/10 px-3.5 py-3 text-[12.5px] text-warn"
      >
        <p class="font-medium">
          导入提示
        </p>
        <ul class="mt-1 list-disc space-y-0.5 pl-4">
          <li
            v-for="warning in warnings"
            :key="warning"
          >
            {{ warning }}
          </li>
        </ul>
      </div>

      <!-- Whitelist reminder: the vanilla default is on, and an empty whitelist locks everyone out -->
      <div
        v-if="values['white-list'] === 'true'"
        class="rounded-[10px] border border-warn/30 bg-warn/10 px-3.5 py-3 text-[12.5px] leading-relaxed text-warn"
      >
        白名单已开启：开服前先执行 <code>/whitelist add &lt;玩家&gt;</code>（OP 会自动加入），
        否则其他玩家会被拒绝进入。需要公开服就关掉这一项。
      </div>

      <div class="grid gap-4 lg:grid-cols-[190px_minmax(0,1fr)]">
        <!-- Group nav -->
        <nav class="panel flex h-fit flex-row flex-wrap gap-1 lg:flex-col">
          <button
            v-for="group in propertyGroups"
            :key="group"
            type="button"
            class="flex shrink-0 items-center justify-between gap-2 rounded-[8px] px-2.5 py-2 text-left text-[12.5px] transition lg:w-full"
            :class="activeGroup === group ? 'bg-accent/10 text-accent' : 'text-dim hover:bg-soft hover:text-ink'"
            @click="activeGroup = group"
          >
            <span>{{ group }}</span>
            <span
              v-if="groupErrors(group)"
              class="rounded-full bg-red-500/15 px-1.5 py-0.5 text-[10.5px] text-red-400"
            >
              {{ groupErrors(group) }}
            </span>
          </button>
        </nav>

        <!-- Fields -->
        <section class="panel flex flex-col gap-4">
          <div class="flex items-center justify-between">
            <div>
              <h2 class="text-sm font-semibold text-ink">
                {{ activeGroup }}
              </h2>
              <p class="mt-0.5 text-[12px] text-faint">
                {{ fieldsInGroup.length }} 个字段
              </p>
            </div>
            <span
              v-if="groupErrors(activeGroup)"
              class="rounded-full bg-red-500/15 px-2 py-0.5 text-[11px] text-red-400"
            >
              {{ groupErrors(activeGroup) }} 个错误
            </span>
          </div>

          <div class="grid gap-4 md:grid-cols-2">
            <FieldShell
              v-for="field in fieldsInGroup"
              :key="field.key"
              :label="field.label"
              :html-for="`prop-${field.key}`"
              :hint="field.hint"
              :error="errors[field.key]"
              :class="field.multiline ? 'md:col-span-2' : ''"
            >
              <template #labelExtra>
                <code class="text-[10.5px] text-faint">{{ field.key }}</code>
              </template>

              <select
                v-if="field.type === 'boolean'"
                :id="`prop-${field.key}`"
                v-model="values[field.key]"
                class="select"
                :class="{ 'border-danger/60': errors[field.key] }"
              >
                <option value="true">
                  true
                </option>
                <option value="false">
                  false
                </option>
              </select>

              <select
                v-else-if="field.type === 'enum'"
                :id="`prop-${field.key}`"
                v-model="values[field.key]"
                class="select"
                :class="{ 'border-danger/60': errors[field.key] }"
              >
                <option
                  v-for="option in field.options"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}（{{ option.value }}）
                </option>
              </select>

              <TextArea
                v-else-if="field.multiline"
                :id="`prop-${field.key}`"
                v-model="values[field.key]"
                min-height="64px"
                :invalid="Boolean(errors[field.key])"
                :placeholder="field.placeholder"
              />

              <TextField
                v-else-if="field.type === 'int'"
                :id="`prop-${field.key}`"
                v-model="values[field.key]"
                type="number"
                step="1"
                :min="field.min"
                :max="field.max"
                :invalid="Boolean(errors[field.key])"
                :placeholder="field.placeholder"
              />

              <template v-else-if="field.options">
                <TextField
                  :id="`prop-${field.key}`"
                  v-model="values[field.key]"
                  :list="datalistId(field.key)"
                  :invalid="Boolean(errors[field.key])"
                  :placeholder="field.placeholder"
                />
                <datalist :id="datalistId(field.key)">
                  <option
                    v-for="option in field.options"
                    :key="option.value"
                    :value="option.value"
                  >
                    {{ option.label }}
                  </option>
                </datalist>
              </template>

              <TextField
                v-else
                :id="`prop-${field.key}`"
                v-model="values[field.key]"
                :type="field.secret ? 'password' : 'text'"
                :invalid="Boolean(errors[field.key])"
                :placeholder="field.placeholder"
              />
            </FieldShell>
          </div>
        </section>
      </div>

      <!-- Output -->
      <section class="panel flex flex-col gap-3">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h2 class="text-sm font-semibold text-ink">
              生成的 server.properties
            </h2>
            <p class="mt-0.5 text-[12px] text-faint">
              {{ errorCount ? `${errorCount} 个字段需要修正` : '校验通过' }} · {{ lineCount }} 行
              <span v-if="unknown.length"> · 保留 {{ unknown.length }} 个未知键</span>
            </p>
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <label class="inline-flex cursor-pointer items-center gap-2 text-[12.5px] text-dim">
              <input
                v-model="escapeUnicode"
                type="checkbox"
                class="accent-accent"
              >
              转义非 ASCII
            </label>
            <button
              type="button"
              class="btn"
              :disabled="errorCount > 0"
              @click="copy"
            >
              复制
            </button>
            <button
              type="button"
              class="btn btn-primary"
              :disabled="errorCount > 0"
              @click="download"
            >
              下载 server.properties
            </button>
          </div>
        </div>

        <TextArea
          :model-value="output"
          readonly
          min-height="320px"
          class="text-[12px]"
          aria-label="生成的 server.properties"
        />

        <p
          v-if="unknown.length"
          class="text-[12px] leading-relaxed text-dim"
        >
          已保留未知键：<code class="text-faint">{{ unknownPreview }}</code>
        </p>
        <p class="text-[12px] leading-relaxed text-faint">
          server.properties 为 Java Edition 专用；Bedrock Edition 的配置字段不同，请勿混用。不同 MC 版本的字段略有差异，旧版本可能忽略新字段；首次使用前请备份原文件。MOTD 可用「MOTD 格式化预览」生成后粘贴。
        </p>
      </section>
    </div>
  </PageShell>
</template>
