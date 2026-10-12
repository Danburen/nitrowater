<script setup lang="ts">
/** Admin · tags — list + inline create/edit form + delete. */
import { onMounted, reactive, ref } from 'vue'
import { toast } from '../../shared/ui/toast'
import {
  createTag,
  deleteTag,
  listTags,
  updateTag,
  type Tag,
  type TagUpsert,
} from './api'

const KINDS = [
  { value: 'TOPIC', label: '知识点' },
  { value: 'COMPANY', label: '公司' },
]

const rows = ref<Tag[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')

const editingId = ref<number | null>(null)
const showForm = ref(false)

const form = reactive<TagUpsert>({
  slug: '',
  name: '',
  nameEn: '',
  kind: 'TOPIC',
  color: '',
  enabled: true,
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    rows.value = await listTags()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  editingId.value = null
  form.slug = ''
  form.name = ''
  form.nameEn = ''
  form.kind = 'TOPIC'
  form.color = ''
  form.enabled = true
}

function openCreate(): void {
  resetForm()
  showForm.value = true
}

function openEdit(row: Tag): void {
  editingId.value = row.id
  form.slug = row.slug
  form.name = row.name
  form.nameEn = row.nameEn ?? ''
  form.kind = row.kind
  form.color = row.color ?? ''
  form.enabled = row.enabled
  showForm.value = true
}

async function save(): Promise<void> {
  if (saving.value) return
  if (!form.name.trim()) {
    toast.warning('请填写中文名')
    return
  }
  saving.value = true
  try {
    const payload: TagUpsert = {
      slug: form.slug?.trim() || null,
      name: form.name.trim(),
      nameEn: form.nameEn?.trim() || null,
      kind: form.kind,
      color: form.color?.trim() || null,
      enabled: form.enabled,
    }
    if (editingId.value == null) {
      await createTag(payload)
      toast.success('标签已创建')
    } else {
      await updateTag(editingId.value, payload)
      toast.success('标签已更新')
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

async function remove(row: Tag): Promise<void> {
  if (!window.confirm(`删除标签「${row.name}」？该操作不可撤销。`)) return
  try {
    await deleteTag(row.id)
    toast.success('标签已删除')
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  }
}

function kindLabel(kind: string): string {
  return KINDS.find((k) => k.value === kind)?.label ?? kind
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
          标签
        </h1>
        <p class="mt-1 text-[13px] text-dim">
          知识点 / 公司标签，供题目关联。
        </p>
      </div>
      <button
        class="btn btn-primary"
        @click="openCreate"
      >
        新建标签
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
        {{ editingId == null ? '新建标签' : `编辑标签 #${editingId}` }}
      </div>
      <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <label class="flex flex-col gap-1">
          <span class="text-[12px] text-dim">中文名 *</span>
          <input
            v-model="form.name"
            class="input"
            placeholder="如：动态规划"
          >
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-[12px] text-dim">英文名</span>
          <input
            v-model="form.nameEn"
            class="input"
            placeholder="Dynamic Programming"
          >
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-[12px] text-dim">Slug（留空自动生成，编辑时忽略）</span>
          <input
            v-model="form.slug"
            class="input"
            :disabled="editingId != null"
            placeholder="dynamic-programming"
          >
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-[12px] text-dim">类型</span>
          <select
            v-model="form.kind"
            class="select"
          >
            <option
              v-for="k in KINDS"
              :key="k.value"
              :value="k.value"
            >
              {{ k.label }}
            </option>
          </select>
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-[12px] text-dim">颜色</span>
          <input
            v-model="form.color"
            class="input"
            placeholder="#5b8cff"
          >
        </label>
        <label class="flex items-center gap-2 self-end pb-2">
          <input
            v-model="form.enabled"
            type="checkbox"
          >
          <span class="text-[13px] text-ink">启用</span>
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
              中文名
            </th>
            <th class="px-3 py-2 font-normal">
              英文名
            </th>
            <th class="px-3 py-2 font-normal">
              Slug
            </th>
            <th class="px-3 py-2 font-normal">
              类型
            </th>
            <th class="px-3 py-2 font-normal">
              颜色
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
              colspan="8"
              class="px-3 py-8 text-center text-faint"
            >
              加载中…
            </td>
          </tr>
          <tr v-else-if="!rows.length">
            <td
              colspan="8"
              class="px-3 py-8 text-center text-faint"
            >
              暂无标签。
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
            <td class="px-3 py-2 text-ink">
              {{ row.name }}
            </td>
            <td class="px-3 py-2 text-dim">
              {{ row.nameEn || '-' }}
            </td>
            <td class="px-3 py-2 font-mono text-[12px] text-dim">
              {{ row.slug }}
            </td>
            <td class="px-3 py-2 text-dim">
              {{ kindLabel(row.kind) }}
            </td>
            <td class="px-3 py-2">
              <span
                v-if="row.color"
                class="inline-flex items-center gap-1.5 text-[12px] text-dim"
              >
                <span
                  class="size-3 rounded-full border border-line"
                  :style="{ background: row.color }"
                />
                {{ row.color }}
              </span>
              <span
                v-else
                class="text-faint"
              >-</span>
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
