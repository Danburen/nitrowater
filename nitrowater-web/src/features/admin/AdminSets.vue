<script setup lang="ts">
/** Admin · problem sets ("题单") — list + inline create/edit form + delete. */
import { onMounted, reactive, ref } from 'vue'
import { toast } from '../../shared/ui/toast'
import {
  createSet,
  deleteSet,
  listSets,
  updateSet,
  type ProblemSet,
  type ProblemSetUpsert,
} from './api'

const rows = ref<ProblemSet[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')

const editingId = ref<number | null>(null)
const showForm = ref(false)

const form = reactive<ProblemSetUpsert>({
  slug: '',
  name: '',
  nameEn: '',
  description: '',
  descriptionEn: '',
  color: '',
  sortOrder: 0,
  enabled: true,
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    rows.value = await listSets()
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
  form.description = ''
  form.descriptionEn = ''
  form.color = ''
  form.sortOrder = 0
  form.enabled = true
}

function openCreate(): void {
  resetForm()
  showForm.value = true
}

function openEdit(row: ProblemSet): void {
  editingId.value = row.id
  form.slug = row.slug
  form.name = row.name
  form.nameEn = row.nameEn ?? ''
  form.description = row.description ?? ''
  form.descriptionEn = row.descriptionEn ?? ''
  form.color = row.color ?? ''
  form.sortOrder = row.sortOrder
  form.enabled = row.enabled
  showForm.value = true
}

async function save(): Promise<void> {
  if (saving.value) return
  if (!form.name.trim()) {
    toast.warning('请填写名称')
    return
  }
  saving.value = true
  try {
    const payload: ProblemSetUpsert = {
      slug: form.slug?.trim() || null,
      name: form.name.trim(),
      nameEn: form.nameEn?.trim() || null,
      description: form.description?.trim() || null,
      descriptionEn: form.descriptionEn?.trim() || null,
      color: form.color?.trim() || null,
      sortOrder: Number(form.sortOrder) || 0,
      enabled: form.enabled,
    }
    if (editingId.value == null) {
      await createSet(payload)
      toast.success('题单已创建')
    } else {
      await updateSet(editingId.value, payload)
      toast.success('题单已更新')
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

async function remove(row: ProblemSet): Promise<void> {
  if (!window.confirm(`删除题单「${row.name}」？其成员关系会一并移除（题目本身保留）。`)) return
  try {
    await deleteSet(row.id)
    toast.success('题单已删除')
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
          题单
        </h1>
        <p class="mt-1 text-[13px] text-dim">
          题目集（如 热门题目 / 面试经典）；题目可归属多个题单。
        </p>
      </div>
      <button
        class="btn btn-primary"
        @click="openCreate"
      >
        新建题单
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
        {{ editingId == null ? '新建题单' : `编辑题单 #${editingId}` }}
      </div>
      <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <label class="flex flex-col gap-1">
          <span class="text-[12px] text-dim">名称 *</span>
          <input
            v-model="form.name"
            class="input"
            placeholder="如：面试经典 150"
          >
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-[12px] text-dim">英文名</span>
          <input
            v-model="form.nameEn"
            class="input"
            placeholder="Top Interview 150"
          >
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-[12px] text-dim">Slug（留空自动生成，编辑时忽略）</span>
          <input
            v-model="form.slug"
            class="input"
            :disabled="editingId != null"
            placeholder="top-interview-150"
          >
        </label>
        <label class="flex flex-col gap-1 sm:col-span-2 lg:col-span-3">
          <span class="text-[12px] text-dim">中文简介</span>
          <input
            v-model="form.description"
            class="input"
            placeholder="一句话描述该题单"
          >
        </label>
        <label class="flex flex-col gap-1 sm:col-span-2 lg:col-span-3">
          <span class="text-[12px] text-dim">英文简介</span>
          <input
            v-model="form.descriptionEn"
            class="input"
            placeholder="One-line English description"
          >
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-[12px] text-dim">颜色</span>
          <input
            v-model="form.color"
            class="input"
            placeholder="#5b8cff"
          >
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-[12px] text-dim">排序（升序）</span>
          <input
            v-model.number="form.sortOrder"
            class="input"
            type="number"
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
              名称
            </th>
            <th class="px-3 py-2 font-normal">
              Slug
            </th>
            <th class="px-3 py-2 font-normal">
              题数
            </th>
            <th class="px-3 py-2 font-normal">
              排序
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
              暂无题单。
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
            <td class="px-3 py-2">
              <div class="text-ink">
                {{ row.name }}
              </div>
              <div
                v-if="row.nameEn"
                class="text-[12px] text-faint"
              >
                {{ row.nameEn }}
              </div>
            </td>
            <td class="px-3 py-2 font-mono text-[12px] text-dim">
              {{ row.slug }}
            </td>
            <td class="px-3 py-2 text-dim">
              {{ row.problemCount }}
            </td>
            <td class="px-3 py-2 text-dim">
              {{ row.sortOrder }}
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
