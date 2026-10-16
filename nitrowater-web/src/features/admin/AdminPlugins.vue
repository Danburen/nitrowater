<script setup lang="ts">
/** Admin · plugins — list + inline create/edit form + delete (plugin-center metadata). */
import { onMounted, reactive, ref } from 'vue'
import { toast } from '../../shared/ui/toast'
import {
  createPlugin,
  deletePlugin,
  listPlugins,
  updatePlugin,
  type PluginAdmin,
  type PluginUpsert,
} from './api'

/** Server-side key rule (`@Pattern` on PluginUpsertReq). */
const KEY_RE = /^[a-z0-9][a-z0-9-]*$/

const rows = ref<PluginAdmin[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')

const editingId = ref<number | null>(null)
const showForm = ref(false)

/** Form state — tags are edited as a comma-separated string. */
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

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    rows.value = await listPlugins()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  editingId.value = null
  form.key = ''
  form.name = ''
  form.tagline = ''
  form.summary = ''
  form.icon = ''
  form.tagsText = ''
  form.docsUrl = ''
  form.homepageUrl = ''
  form.repoUrl = ''
  form.sponsorUrl = ''
  form.readme = ''
  form.sortOrder = 0
  form.enabled = true
}

function openCreate(): void {
  resetForm()
  showForm.value = true
}

function openEdit(row: PluginAdmin): void {
  editingId.value = row.id
  form.key = row.key
  form.name = row.name
  form.tagline = row.tagline ?? ''
  form.summary = row.summary ?? ''
  form.icon = row.icon ?? ''
  form.tagsText = row.tags.join(', ')
  form.docsUrl = row.docsUrl ?? ''
  form.homepageUrl = row.homepageUrl ?? ''
  form.repoUrl = row.repoUrl ?? ''
  form.sponsorUrl = row.sponsorUrl ?? ''
  form.readme = row.readme ?? ''
  form.sortOrder = row.sortOrder
  form.enabled = row.enabled
  showForm.value = true
}

/** Splits a comma / full-width-comma / newline separated tag string. */
function splitTags(text: string): string[] {
  return text
    .split(/[,，\n]/)
    .map((t) => t.trim())
    .filter(Boolean)
}

async function save(): Promise<void> {
  if (saving.value) return
  if (!form.name.trim()) {
    toast.warning('请填写名称')
    return
  }
  if (editingId.value == null) {
    const key = form.key.trim()
    if (!key) {
      toast.warning('请填写 key（小写字母 / 数字 / 连字符）')
      return
    }
    if (!KEY_RE.test(key)) {
      toast.warning('key 只能用小写字母、数字与连字符，且以字母或数字开头')
      return
    }
  }
  saving.value = true
  try {
    const payload: PluginUpsert = {
      key: form.key.trim() || null,
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
    if (editingId.value == null) {
      await createPlugin(payload)
      toast.success('插件已创建')
    } else {
      await updatePlugin(editingId.value, payload)
      toast.success('插件已更新')
    }
    showForm.value = false
    resetForm()
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    saving.value = false
  }
}

async function remove(row: PluginAdmin): Promise<void> {
  if (!window.confirm(`删除插件「${row.name}」？该操作不可撤销。`)) return
  try {
    await deletePlugin(row.id)
    toast.success('插件已删除')
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="mx-auto w-full max-w-[1120px] p-4 sm:p-6">
    <div class="section-head">
      <div>
        <h1 class="text-lg font-semibold text-ink">
          插件
        </h1>
        <p class="mt-1 text-[13px] text-dim">
          插件中心元数据：卡片信息、外链与单页说明（key 创建后不可改）。
        </p>
      </div>
      <button
        class="btn btn-primary"
        @click="openCreate"
      >
        新建插件
      </button>
    </div>

    <div
      v-if="error"
      class="mb-4 rounded-[10px] border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger"
    >
      {{ error }}
    </div>

    <!-- Form -->
    <div
      v-if="showForm"
      class="panel mb-4"
    >
      <div class="mb-3 text-sm font-medium text-ink">
        {{ editingId == null ? '新建插件' : `编辑插件 #${editingId}` }}
      </div>
      <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <label class="flex flex-col gap-1">
          <span class="text-[12px] text-dim">名称 *</span>
          <input
            v-model="form.name"
            class="input"
            placeholder="如：VeloChatX"
          >
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-[12px] text-dim">Key（小写字母 / 数字 / 连字符；创建后不可改）</span>
          <input
            v-model="form.key"
            class="input"
            :disabled="editingId != null"
            placeholder="velochatx"
          >
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
        <label class="flex flex-col gap-1 lg:col-span-2">
          <span class="text-[12px] text-dim">文档站 URL</span>
          <input
            v-model="form.docsUrl"
            class="input"
            placeholder="/plugin/velochatx/docs/"
          >
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-[12px] text-dim">官网 URL（可空，空则不展示）</span>
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
        <label class="flex flex-col gap-1 sm:col-span-2 lg:col-span-3">
          <span class="text-[12px] text-dim">卡片简介</span>
          <textarea
            v-model="form.summary"
            class="input min-h-[64px]"
            placeholder="一句话介绍，用于列表卡片。"
          />
        </label>
        <label class="flex flex-col gap-1 sm:col-span-2 lg:col-span-3">
          <span class="text-[12px] text-dim">单页说明 Markdown（详情页「介绍」tab）</span>
          <textarea
            v-model="form.readme"
            class="input min-h-[180px] font-mono text-[12.5px]"
            placeholder="# 插件名&#10;&#10;说明正文…"
          />
        </label>
      </div>
      <div class="mt-4 flex items-center gap-2">
        <button
          class="btn btn-primary"
          :disabled="saving"
          @click="save"
        >
          {{ saving ? '保存中…' : '保存' }}
        </button>
        <button
          class="btn"
          :disabled="saving"
          @click="showForm = false; resetForm()"
        >
          取消
        </button>
      </div>
    </div>

    <!-- Table -->
    <div class="panel overflow-x-auto p-0">
      <table class="w-full text-left text-sm">
        <thead>
          <tr class="border-b border-line text-[12px] text-faint">
            <th class="px-3 py-2 font-normal">
              ID
            </th>
            <th class="px-3 py-2 font-normal">
              Key
            </th>
            <th class="px-3 py-2 font-normal">
              名称
            </th>
            <th class="px-3 py-2 font-normal">
              标签
            </th>
            <th class="px-3 py-2 font-normal">
              排序
            </th>
            <th class="px-3 py-2 font-normal">
              状态
            </th>
            <th class="px-3 py-2 font-normal">
              操作
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td
              colspan="7"
              class="px-3 py-8 text-center text-faint"
            >
              加载中…
            </td>
          </tr>
          <tr v-else-if="!rows.length">
            <td
              colspan="7"
              class="px-3 py-8 text-center text-faint"
            >
              暂无插件。
            </td>
          </tr>
          <tr
            v-for="row in rows"
            :key="row.id"
            class="border-b border-line/60 last:border-0"
          >
            <td class="px-3 py-2 text-dim">
              {{ row.id }}
            </td>
            <td class="px-3 py-2 font-mono text-[12px] text-dim">
              {{ row.key }}
            </td>
            <td class="px-3 py-2">
              <span class="block text-ink">{{ row.name }}</span>
              <span
                v-if="row.tagline"
                class="block text-[12px] text-faint"
              >{{ row.tagline }}</span>
            </td>
            <td class="px-3 py-2">
              <span
                v-if="row.tags.length"
                class="flex flex-wrap gap-1"
              >
                <span
                  v-for="t in row.tags"
                  :key="t"
                  class="tag"
                >{{ t }}</span>
              </span>
              <span
                v-else
                class="text-faint"
              >-</span>
            </td>
            <td class="px-3 py-2 text-dim">
              {{ row.sortOrder }}
            </td>
            <td class="px-3 py-2">
              <span :class="row.enabled ? 'text-ok' : 'text-faint'">{{ row.enabled ? '启用' : '停用' }}</span>
            </td>
            <td class="px-3 py-2">
              <div class="flex gap-2">
                <button
                  class="text-accent hover:underline"
                  @click="openEdit(row)"
                >
                  编辑
                </button>
                <button
                  class="text-danger hover:underline"
                  @click="remove(row)"
                >
                  删除
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
