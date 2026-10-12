<script setup lang="ts">
/**
 * Admin · editorials — pick a problem, then manage its editorials (create / edit / publish / pin
 * / delete). All statuses are visible here; the public site only sees PUBLISHED ones.
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { toast } from '../../shared/ui/toast'
import {
  createEditorial,
  deleteEditorial,
  listEditorials,
  listProblems,
  pinEditorial,
  publishEditorial,
  unpublishEditorial,
  unpinEditorial,
  updateEditorial,
  type EditorialSummary,
  type EditorialUpsert,
  type ProblemSummary,
} from './api'

const LANGUAGES = ['java', 'c', 'cpp', 'python', 'javascript', 'typescript', 'lua']
const STATUSES = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'HIDDEN', label: '隐藏' },
]

const problems = ref<ProblemSummary[]>([])
const selectedProblemId = ref<number | null>(null)

const rows = ref<EditorialSummary[]>([])
const loadingProblems = ref(false)
const loadingRows = ref(false)
const saving = ref(false)
const error = ref('')

const editingId = ref<number | null>(null)
const showForm = ref(false)

const form = reactive<EditorialUpsert>({
  title: '',
  content: '',
  language: '',
  status: 'DRAFT',
  pinned: false,
})

const canList = computed(() => selectedProblemId.value != null)

async function loadProblems(): Promise<void> {
  loadingProblems.value = true
  try {
    problems.value = await listProblems()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loadingProblems.value = false
  }
}

async function loadEditorials(): Promise<void> {
  if (selectedProblemId.value == null) {
    rows.value = []
    return
  }
  loadingRows.value = true
  error.value = ''
  try {
    rows.value = await listEditorials(selectedProblemId.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loadingRows.value = false
  }
}

function resetForm(): void {
  editingId.value = null
  form.title = ''
  form.content = ''
  form.language = ''
  form.status = 'DRAFT'
  form.pinned = false
}

function openCreate(): void {
  if (selectedProblemId.value == null) {
    toast.warning('请先选择题目')
    return
  }
  resetForm()
  showForm.value = true
}

function openEdit(row: EditorialSummary): void {
  editingId.value = row.id
  form.title = row.title
  form.content = ''
  form.language = row.language ?? ''
  form.status = row.status
  form.pinned = row.pinned
  showForm.value = true
  toast.info('正文未在列表返回，请在保存前补全内容')
}

async function save(): Promise<void> {
  if (saving.value) return
  if (selectedProblemId.value == null) return
  if (!form.title.trim()) {
    toast.warning('请填写标题')
    return
  }
  if (!form.content.trim()) {
    toast.warning('请填写正文')
    return
  }
  saving.value = true
  try {
    const payload: EditorialUpsert = {
      title: form.title.trim(),
      content: form.content,
      language: form.language?.trim() || null,
      status: form.status,
      pinned: !!form.pinned,
    }
    if (editingId.value == null) {
      await createEditorial(selectedProblemId.value, payload)
      toast.success('题解已创建')
    } else {
      await updateEditorial(editingId.value, payload)
      toast.success('题解已更新')
    }
    showForm.value = false
    resetForm()
    await loadEditorials()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    saving.value = false
  }
}

async function togglePublish(row: EditorialSummary): Promise<void> {
  try {
    if (row.status === 'PUBLISHED') await unpublishEditorial(row.id)
    else await publishEditorial(row.id)
    toast.success(row.status === 'PUBLISHED' ? '已下架' : '已发布')
    await loadEditorials()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  }
}

async function togglePin(row: EditorialSummary): Promise<void> {
  try {
    if (row.pinned) await unpinEditorial(row.id)
    else await pinEditorial(row.id)
    toast.success(row.pinned ? '已取消置顶' : '已置顶')
    await loadEditorials()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  }
}

async function remove(row: EditorialSummary): Promise<void> {
  if (!window.confirm(`删除题解「${row.title}」？`)) return
  try {
    await deleteEditorial(row.id)
    toast.success('题解已删除')
    await loadEditorials()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  }
}

function statusLabel(status: string): string {
  return STATUSES.find((s) => s.value === status)?.label ?? status
}

onMounted(() => {
  void loadProblems()
})
</script>

<template>
  <div class="mx-auto w-full max-w-[1120px] p-4 sm:p-6">
    <div class="section-head">
      <div>
        <h1 class="text-lg font-semibold text-ink">
          题解
        </h1>
        <p class="mt-1 text-[13px] text-dim">
          官方题解（中文正文）。仅「已发布」对外可见。
        </p>
      </div>
      <button
        class="btn btn-primary"
        :disabled="!canList"
        @click="openCreate"
      >
        新建题解
      </button>
    </div>

    <div class="panel mb-4">
      <label class="flex flex-col gap-1 sm:max-w-md">
        <span class="text-[12px] text-dim">选择题目</span>
        <select
          v-model.number="selectedProblemId"
          class="select"
          :disabled="loadingProblems"
          @change="loadEditorials"
        >
          <option :value="null">
            {{ loadingProblems ? '加载中…' : '请选择题目' }}
          </option>
          <option
            v-for="p in problems"
            :key="p.id"
            :value="p.id"
          >
            #{{ p.id }} · {{ p.title }}
          </option>
        </select>
      </label>
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
        {{ editingId == null ? '新建题解' : `编辑题解 #${editingId}` }}
      </div>
      <div class="grid gap-3 sm:grid-cols-3">
        <label class="flex flex-col gap-1 sm:col-span-2">
          <span class="text-[12px] text-dim">标题 *</span>
          <input
            v-model="form.title"
            class="input"
          >
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-[12px] text-dim">语言（可选）</span>
          <select
            v-model="form.language"
            class="select"
          >
            <option value="">无</option>
            <option
              v-for="l in LANGUAGES"
              :key="l"
              :value="l"
            >
              {{ l }}
            </option>
          </select>
        </label>
        <label class="flex flex-col gap-1 sm:col-span-3">
          <span class="text-[12px] text-dim">正文（Markdown）*</span>
          <textarea
            v-model="form.content"
            class="textarea min-h-[220px]"
          />
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-[12px] text-dim">状态</span>
          <select
            v-model="form.status"
            class="select"
          >
            <option
              v-for="s in STATUSES"
              :key="s.value"
              :value="s.value"
            >
              {{ s.label }}
            </option>
          </select>
        </label>
        <label class="flex items-center gap-2 self-end pb-2">
          <input
            v-model="form.pinned"
            type="checkbox"
          >
          <span class="text-[13px] text-ink">置顶</span>
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
              标题
            </th>
            <th class="px-3 py-2 font-normal">
              语言
            </th>
            <th class="px-3 py-2 font-normal">
              状态
            </th>
            <th class="px-3 py-2 font-normal">
              置顶
            </th>
            <th class="px-3 py-2 font-normal">
              赞 / 浏览
            </th>
            <th class="px-3 py-2 font-normal">
              操作
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!canList">
            <td
              colspan="7"
              class="px-3 py-8 text-center text-faint"
            >
              请先选择题目。
            </td>
          </tr>
          <tr v-else-if="loadingRows">
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
              该题目暂无题解。
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
              {{ row.title }}
            </td>
            <td class="px-3 py-2 text-dim">
              {{ row.language || '-' }}
            </td>
            <td class="px-3 py-2">
              <span
                :class="row.status === 'PUBLISHED' ? 'text-ok' : row.status === 'HIDDEN' ? 'text-danger' : 'text-faint'"
              >{{ statusLabel(row.status) }}</span>
            </td>
            <td class="px-3 py-2">
              <span
                v-if="row.pinned"
                class="text-warn"
              >置顶</span>
              <span
                v-else
                class="text-faint"
              >-</span>
            </td>
            <td class="px-3 py-2 text-dim">
              {{ row.likeCount }} / {{ row.viewCount }}
            </td>
            <td class="px-3 py-2">
              <div class="flex flex-wrap gap-2">
                <button
                  class="text-accent hover:underline"
                  @click="openEdit(row)"
                >
                  编辑
                </button>
                <button
                  class="text-accent hover:underline"
                  @click="togglePublish(row)"
                >
                  {{ row.status === 'PUBLISHED' ? '下架' : '发布' }}
                </button>
                <button
                  class="text-accent hover:underline"
                  @click="togglePin(row)"
                >
                  {{ row.pinned ? '取消置顶' : '置顶' }}
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
