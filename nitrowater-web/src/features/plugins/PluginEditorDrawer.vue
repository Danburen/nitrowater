<script setup lang="ts">
/**
 * Inline plugin editor (admin-only), opened from `/plugins` (create) or `/plugins/:key` (edit).
 *
 * Three tabs keep the editing surface close to the page it affects:
 * - 基本信息 — the card/links/enabled fields (same contract as the admin console);
 * - 入口简述 — a Markdown editor (`md-editor-v3`) with live preview;
 * - 文件 — publish/list/delete jars through the resource service (`.jar` only, server-enforced).
 *
 * The component is lazy-loaded by its parents so `md-editor-v3` never enters the public bundle.
 */
import { computed, reactive, ref, watch } from 'vue'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { useTheme } from '../../shared/theme/useTheme'
import { toast } from '../../shared/ui/toast'
import {
  createPlugin,
  deletePluginJar,
  listPluginJars,
  listPlugins,
  updatePlugin,
  uploadPluginJar,
  versionFromName,
  type AdminPluginJar,
  type PluginAdmin,
  type PluginUpsert,
} from '../../api/plugins'

const TABS = [
  { value: 'basic', label: '基本信息' },
  { value: 'intro', label: '入口简述' },
  { value: 'files', label: '文件' },
] as const
type TabValue = (typeof TABS)[number]['value']

const props = defineProps<{
  open: boolean
  /** `null` creates a new plugin; otherwise the plugin key being edited. */
  pluginKey: string | null
  initialTab?: TabValue
}>()

const emit = defineEmits<{
  close: []
  saved: [key: string]
}>()

const { theme } = useTheme()

const KEY_RE = /^[a-z0-9][a-z0-9-]*$/

const tab = ref<TabValue>('basic')
const saving = ref(false)
const loading = ref(false)
const loadError = ref('')
const keyError = ref('')
const adminPlugin = ref<PluginAdmin | null>(null)

const form = reactive({
  key: '',
  name: '',
  tagline: '',
  summary: '',
  icon: '',
  tagsText: '',
  docsUrl: '',
  homepageUrl: '',
  repoUrl: '',
  sponsorUrl: '',
  readme: '',
  sortOrder: 0,
  enabled: true,
})

const jars = ref<AdminPluginJar[]>([])
const jarsLoading = ref(false)
const jarsError = ref('')
const uploadFile = ref<File | null>(null)
const uploadVersion = ref('')
const uploading = ref(false)
const uploadRatio = ref(0)
const deletingUuid = ref<string | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const isCreate = computed(() => props.pluginKey == null)
const title = computed(() => (isCreate.value ? '新建插件' : `编辑插件 · ${adminPlugin.value?.name ?? props.pluginKey}`))

function reset(): void {
  const p = adminPlugin.value
  form.key = p?.key ?? ''
  form.name = p?.name ?? ''
  form.tagline = p?.tagline ?? ''
  form.summary = p?.summary ?? ''
  form.icon = p?.icon ?? ''
  form.tagsText = (p?.tags ?? []).join(', ')
  form.docsUrl = p?.docsUrl ?? ''
  form.homepageUrl = p?.homepageUrl ?? ''
  form.repoUrl = p?.repoUrl ?? ''
  form.sponsorUrl = p?.sponsorUrl ?? ''
  form.readme = p?.readme ?? ''
  form.sortOrder = p?.sortOrder ?? 0
  form.enabled = p?.enabled ?? true
  keyError.value = ''
  loadError.value = ''
  jars.value = []
  jarsError.value = ''
  uploadFile.value = null
  uploadVersion.value = ''
  uploadRatio.value = 0
  tab.value = props.initialTab ?? 'basic'
  if (fileInput.value) fileInput.value.value = ''
}

async function loadAdminPlugin(): Promise<void> {
  if (!props.pluginKey) return
  loading.value = true
  loadError.value = ''
  try {
    const all = await listPlugins()
    adminPlugin.value = all.find((p) => p.key === props.pluginKey) ?? null
    if (!adminPlugin.value) loadError.value = '未找到该插件的管理数据，可能已被删除。'
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function loadJars(): Promise<void> {
  if (!props.pluginKey) return
  jarsLoading.value = true
  jarsError.value = ''
  try {
    jars.value = await listPluginJars(props.pluginKey)
  } catch (e) {
    jarsError.value = e instanceof Error ? e.message : String(e)
  } finally {
    jarsLoading.value = false
  }
}

watch(
  () => [props.open, props.pluginKey] as const,
  async ([open, key]) => {
    if (!open) return
    adminPlugin.value = null
    await loadAdminPlugin()
    reset()
    if (key) void loadJars()
  },
  { immediate: true },
)

function splitTags(text: string): string[] {
  return text
    .split(/[,，\n]/)
    .map((t) => t.trim())
    .filter(Boolean)
}

function payload(): PluginUpsert {
  return {
    key: isCreate.value ? form.key.trim() : null,
    name: form.name.trim(),
    tagline: form.tagline.trim() || null,
    summary: form.summary.trim() || null,
    icon: form.icon.trim() || null,
    tags: splitTags(form.tagsText),
    docsUrl: form.docsUrl.trim() || null,
    homepageUrl: form.homepageUrl.trim() || null,
    repoUrl: form.repoUrl.trim() || null,
    sponsorUrl: form.sponsorUrl.trim() || null,
    readme: form.readme.trim() || null,
    sortOrder: form.sortOrder,
    enabled: form.enabled,
  }
}

async function save(): Promise<void> {
  if (saving.value) return
  if (!form.name.trim()) {
    tab.value = 'basic'
    toast.warning('请填写名称')
    return
  }
  if (isCreate.value && !KEY_RE.test(form.key.trim())) {
    tab.value = 'basic'
    keyError.value = 'key 只能用小写字母、数字与连字符，且以字母或数字开头'
    toast.warning('请检查 key')
    return
  }
  if (!isCreate.value && !adminPlugin.value) {
    toast.error('插件数据尚未加载，请关闭后重试')
    return
  }
  keyError.value = ''
  saving.value = true
  try {
    const saved = isCreate.value
      ? await createPlugin(payload())
      : await updatePlugin(adminPlugin.value!.id, payload())
    toast.success(isCreate.value ? '插件已创建' : '插件已保存')
    emit('saved', saved.key)
    emit('close')
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    saving.value = false
  }
}

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.jar')) {
    toast.warning('只允许上传 .jar 文件')
    input.value = ''
    return
  }
  uploadFile.value = file
  uploadVersion.value = versionFromName(file.name) ?? ''
}

async function doUpload(): Promise<void> {
  if (!props.pluginKey || !uploadFile.value || uploading.value) return
  uploading.value = true
  uploadRatio.value = 0
  try {
    const saved = await uploadPluginJar(
      props.pluginKey,
      uploadFile.value,
      uploadVersion.value.trim(),
      (ratio) => {
        uploadRatio.value = ratio
      },
    )
    toast.success(`已发布 ${saved.name ?? 'jar'}`)
    uploadFile.value = null
    uploadVersion.value = ''
    if (fileInput.value) fileInput.value.value = ''
    await loadJars()
    emit('saved', props.pluginKey)
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    uploading.value = false
    uploadRatio.value = 0
  }
}

async function removeJar(jar: AdminPluginJar): Promise<void> {
  if (!props.pluginKey || deletingUuid.value) return
  if (!window.confirm(`删除 ${jar.name ?? jar.uuid}？该操作不可撤销。`)) return
  deletingUuid.value = jar.uuid
  try {
    await deletePluginJar(props.pluginKey, jar.uuid)
    toast.success('已删除')
    await loadJars()
    emit('saved', props.pluginKey)
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    deletingUuid.value = null
  }
}

function fmtSize(bytes: number | null): string {
  if (bytes == null) return '—'
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
  return `${bytes} B`
}

function fmtDate(iso: string | null): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toISOString().slice(0, 10)
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-50 flex justify-end bg-black/40"
      @click.self="emit('close')"
    >
      <aside class="flex h-full w-full max-w-[760px] flex-col bg-bg shadow-2xl">
        <header class="flex h-[56px] shrink-0 items-center gap-3 border-b border-line px-4">
          <h2 class="min-w-0 flex-1 truncate text-[15px] font-semibold text-ink">
            {{ title }}
          </h2>
          <button
            type="button"
            class="btn"
            @click="emit('close')"
          >
            关闭
          </button>
        </header>

        <div
          v-if="loadError"
          class="mx-4 mt-3 rounded-[10px] border border-danger/30 bg-danger/10 px-3 py-2 text-[13px] text-danger"
        >
          {{ loadError }}
        </div>

        <div class="flex shrink-0 gap-1 border-b border-line px-4">
          <button
            v-for="t in TABS"
            :key="t.value"
            type="button"
            class="-mb-px border-b-2 px-3 py-2.5 text-[13.5px] transition"
            :class="tab === t.value
              ? 'border-accent font-medium text-accent'
              : 'border-transparent text-dim hover:text-ink'"
            @click="tab = t.value"
          >
            {{ t.label }}
            <span
              v-if="t.value === 'files' && jars.length"
              class="text-faint"
            >({{ jars.length }})</span>
          </button>
        </div>

        <div class="min-h-0 flex-1 overflow-y-auto p-4">
          <p
            v-if="loading"
            class="text-[13px] text-dim"
          >
            正在读取插件数据…
          </p>

          <!-- Tab: basic -->
          <div
            v-else-if="tab === 'basic'"
            class="grid gap-3 sm:grid-cols-2"
          >
            <label class="flex flex-col gap-1">
              <span class="text-[12px] text-dim">名称 *</span>
              <input
                v-model="form.name"
                class="input"
                placeholder="如：VeloChatX"
              >
            </label>
            <label class="flex flex-col gap-1">
              <span class="text-[12px] text-dim">Key（创建后不可改）</span>
              <input
                v-model="form.key"
                class="input"
                :disabled="!isCreate"
                placeholder="velochatx"
              >
              <span
                v-if="keyError"
                class="text-[12px] text-danger"
              >{{ keyError }}</span>
            </label>
            <label class="flex flex-col gap-1">
              <span class="text-[12px] text-dim">副标题</span>
              <input
                v-model="form.tagline"
                class="input"
                placeholder="Velocity 跨服聊天插件"
              >
            </label>
            <label class="flex flex-col gap-1">
              <span class="text-[12px] text-dim">排序（升序）</span>
              <input
                v-model.number="form.sortOrder"
                type="number"
                class="input"
              >
            </label>
            <label class="flex flex-col gap-1">
              <span class="text-[12px] text-dim">图标 SVG path（24x24 的 d 属性）</span>
              <input
                v-model="form.icon"
                class="input"
                placeholder="M4 5h16v11H8l-4 4z"
              >
            </label>
            <label class="flex flex-col gap-1">
              <span class="text-[12px] text-dim">标签（逗号分隔）</span>
              <input
                v-model="form.tagsText"
                class="input"
                placeholder="Velocity, 跨服聊天"
              >
            </label>
            <label class="flex flex-col gap-1 sm:col-span-2">
              <span class="text-[12px] text-dim">文档站 URL</span>
              <input
                v-model="form.docsUrl"
                class="input"
                placeholder="/plugin/velochatx/docs/"
              >
            </label>
            <label class="flex flex-col gap-1">
              <span class="text-[12px] text-dim">官网 URL（可空）</span>
              <input
                v-model="form.homepageUrl"
                class="input"
                placeholder="https://…"
              >
            </label>
            <label class="flex flex-col gap-1">
              <span class="text-[12px] text-dim">开源仓库 URL（可空）</span>
              <input
                v-model="form.repoUrl"
                class="input"
                placeholder="https://github.com/…"
              >
            </label>
            <label class="flex flex-col gap-1">
              <span class="text-[12px] text-dim">赞助 URL（可空）</span>
              <input
                v-model="form.sponsorUrl"
                class="input"
                placeholder="https://…"
              >
            </label>
            <label class="flex items-center gap-2 self-end pb-2">
              <input
                v-model="form.enabled"
                type="checkbox"
              >
              <span class="text-[13px] text-ink">启用（前台可见）</span>
            </label>
            <label class="flex flex-col gap-1 sm:col-span-2">
              <span class="text-[12px] text-dim">卡片简介</span>
              <textarea
                v-model="form.summary"
                class="input min-h-[64px]"
                placeholder="一句话介绍，用于列表卡片。"
              />
            </label>
          </div>

          <!-- Tab: intro -->
          <div
            v-else-if="tab === 'intro'"
            class="flex flex-col gap-2"
          >
            <p class="text-[12px] text-dim">
              Markdown 支持标题、列表、表格、代码块；保存后前台会用安全过滤后渲染。
            </p>
            <MdEditor
              v-model="form.readme"
              language="zh-CN"
              :theme="theme"
              :no-upload-img="true"
              :no-mermaid="true"
              :no-katex="true"
              style="height: 460px"
            />
          </div>

          <!-- Tab: files -->
          <div
            v-else
            class="flex flex-col gap-4"
          >
            <div
              v-if="isCreate"
              class="rounded-[10px] border border-line bg-soft/40 px-3 py-2 text-[13px] text-dim"
            >
              先保存基本信息创建插件，再回到这里上传 jar。
            </div>

            <template v-else>
              <div class="rounded-[12px] border border-line p-4">
                <div class="mb-2 text-[13px] font-medium text-ink">
                  发布新版本
                </div>
                <p class="mb-3 text-[12px] text-dim">
                  仅接受 <code class="rounded bg-soft px-1">.jar</code>（服务端校验扩展名 + ZIP 签名），单个文件上限 64 MB。
                  同名文件会原地覆盖，下载链接保持不变。
                </p>
                <div class="flex flex-wrap items-center gap-2">
                  <input
                    ref="fileInput"
                    type="file"
                    accept=".jar,application/java-archive"
                    class="block max-w-full text-[13px] text-dim file:mr-2 file:rounded-[8px] file:border file:border-line file:bg-soft file:px-2.5 file:py-1.5 file:text-[13px] file:text-ink"
                    :disabled="uploading"
                    @change="onFileChange"
                  >
                  <input
                    v-model="uploadVersion"
                    class="input max-w-[160px]"
                    placeholder="版本（可自动识别）"
                    :disabled="uploading"
                  >
                  <button
                    type="button"
                    class="btn btn-primary"
                    :disabled="!uploadFile || uploading"
                    @click="doUpload"
                  >
                    {{ uploading ? '上传中…' : '上传并发布' }}
                  </button>
                </div>
                <div
                  v-if="uploading"
                  class="mt-3 h-1.5 overflow-hidden rounded-full bg-soft"
                >
                  <div
                    class="h-full bg-accent transition-[width]"
                    :style="{ width: `${Math.round(uploadRatio * 100)}%` }"
                  />
                </div>
              </div>

              <p
                v-if="jarsLoading"
                class="text-[13px] text-dim"
              >
                正在读取文件…
              </p>
              <p
                v-else-if="jarsError"
                class="text-[13px] text-danger"
              >
                {{ jarsError }}
              </p>
              <div
                v-else-if="jars.length"
                class="overflow-hidden rounded-[12px] border border-line"
              >
                <div
                  v-for="(jar, i) in jars"
                  :key="jar.uuid"
                  class="flex items-center gap-3 border-b border-line px-4 py-2.5 last:border-b-0"
                  :class="i === 0 ? 'bg-accent/5' : ''"
                >
                  <span class="min-w-0 flex-1">
                    <span class="block truncate text-[13.5px] font-medium text-ink">{{ jar.name ?? jar.uuid }}</span>
                    <span class="block text-[12px] text-faint">
                      {{ fmtDate(jar.createdAt) }} · {{ fmtSize(jar.sizeBytes) }}
                      <template v-if="jar.version"> · v{{ jar.version }}</template>
                    </span>
                  </span>
                  <span
                    v-if="i === 0"
                    class="tag tag-lock shrink-0"
                  >最新</span>
                  <a
                    :href="jar.url"
                    download
                    class="shrink-0 text-[13px] text-accent transition hover:opacity-80"
                  >下载</a>
                  <button
                    type="button"
                    class="shrink-0 text-[13px] text-danger transition hover:opacity-80 disabled:opacity-50"
                    :disabled="deletingUuid === jar.uuid"
                    @click="removeJar(jar)"
                  >
                    {{ deletingUuid === jar.uuid ? '删除中…' : '删除' }}
                  </button>
                </div>
              </div>
              <p
                v-else
                class="text-[13px] text-dim"
              >
                还没有已发布文件。
              </p>
            </template>
          </div>
        </div>

        <footer class="flex shrink-0 items-center justify-end gap-2 border-t border-line px-4 py-3">
          <span
            v-if="!isCreate && adminPlugin"
            class="mr-auto text-[12px] text-faint"
          >
            key: {{ adminPlugin.key }}
          </span>
          <button
            type="button"
            class="btn"
            :disabled="saving"
            @click="emit('close')"
          >
            取消
          </button>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="saving || loading"
            @click="save"
          >
            {{ saving ? '保存中…' : (isCreate ? '创建插件' : '保存修改') }}
          </button>
        </footer>
      </aside>
    </div>
  </Teleport>
</template>
