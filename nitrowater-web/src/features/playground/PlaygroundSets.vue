<script setup lang="ts">
/**
 * Playground — 我的题单 (my problem sets).
 *
 * Login-gated: lists the caller's own sets and lets them be created / renamed / re-scoped
 * (private↔public) / deleted, plus problem membership editing via a picker. "开始刷题" opens the
 * set detail. Reads/writes go to the owner-scoped `/api/me/problem-sets` endpoints.
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../../auth/useAuth'
import { toast } from '../../shared/ui/toast'
import { listProblems, type ProblemSummary } from './api'
import { difficultyMeta } from './labels'
import {
  createMySet,
  deleteMySet,
  listMySets,
  listSetProblems,
  replaceMySetProblems,
  updateMySet,
  type MySetUpsert,
  type ProblemSetSummary,
  type SetVisibility,
} from './sets'
import PageShell from '../../shared/ui/PageShell.vue'

const router = useRouter()
const { login } = useAuth()

const sets = ref<ProblemSetSummary[]>([])
const loading = ref(false)
const error = ref('')
const needsLogin = ref(false)

const editingId = ref<number | null>(null)
const showForm = ref(false)
const saving = ref(false)
const form = reactive<{ name: string; description: string; visibility: SetVisibility }>({
  name: '',
  description: '',
  visibility: 'PRIVATE',
})

// ---- membership editor ----
const pickerOpen = ref(false)
const pickerSet = ref<ProblemSetSummary | null>(null)
const allProblems = ref<ProblemSummary[]>([])
const picked = ref<Set<number>>(new Set())
const pickerLoading = ref(false)
const pickerSaving = ref(false)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  needsLogin.value = false
  try {
    sets.value = await listMySets()
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e)
    if (message.includes('401')) needsLogin.value = true
    else error.value = message
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  form.name = ''
  form.description = ''
  form.visibility = 'PRIVATE'
  showForm.value = true
}

function openEdit(set: ProblemSetSummary): void {
  editingId.value = set.id
  form.name = set.name
  form.description = set.description ?? ''
  form.visibility = set.visibility
  showForm.value = true
}

async function save(): Promise<void> {
  if (saving.value) return
  if (!form.name.trim()) {
    toast.warning('请填写题单名称')
    return
  }
  saving.value = true
  try {
    const payload: MySetUpsert = {
      name: form.name.trim(),
      description: form.description.trim() || null,
      visibility: form.visibility,
    }
    if (editingId.value == null) {
      await createMySet(payload)
      toast.success('题单已创建')
    } else {
      await updateMySet(editingId.value, payload)
      toast.success('题单已更新')
    }
    showForm.value = false
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    saving.value = false
  }
}

async function remove(set: ProblemSetSummary): Promise<void> {
  if (!window.confirm(`删除题单「${set.name}」？其中题目本身会保留。`)) return
  try {
    await deleteMySet(set.id)
    toast.success('题单已删除')
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  }
}

function startSet(set: ProblemSetSummary): void {
  void router.push(`/playground/sets/${set.id}`)
}

async function openPicker(set: ProblemSetSummary): Promise<void> {
  pickerSet.value = set
  pickerOpen.value = true
  pickerLoading.value = true
  picked.value = new Set()
  allProblems.value = []
  try {
    const [available, current] = await Promise.all([listProblems(), listSetProblemsSafe(set.id)])
    allProblems.value = available
    picked.value = new Set(current.map((p) => p.id))
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    pickerLoading.value = false
  }
}

/** The set's current problems; falls back to empty when they cannot be read. */
async function listSetProblemsSafe(id: number): Promise<ProblemSummary[]> {
  try {
    return await listSetProblems(id)
  } catch {
    return []
  }
}

function togglePicked(id: number): void {
  const next = new Set(picked.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  picked.value = next
}

function closePicker(): void {
  if (pickerSaving.value) return
  pickerOpen.value = false
  pickerSet.value = null
}

async function savePicker(): Promise<void> {
  if (!pickerSet.value || pickerSaving.value) return
  pickerSaving.value = true
  try {
    // Preserve display order from the available list, then any ids not currently listed.
    const ordered = allProblems.value.map((p) => p.id).filter((id) => picked.value.has(id))
    await replaceMySetProblems(pickerSet.value.id, ordered)
    toast.success('题单内容已更新')
    pickerOpen.value = false
    pickerSet.value = null
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    pickerSaving.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <PageShell
    title="我的题单"
    desc="你自建的题单：勾选题目自由组卷，可设为私密或公开（公开后出现在题单广场）。"
  >
    <template #actions>
      <router-link
        to="/playground/square"
        class="inline-flex items-center gap-1 rounded-[9px] border border-line bg-card px-2.5 py-1 text-[13px] text-dim transition hover:border-line-strong hover:text-ink"
      >
        题单广场
      </router-link>
      <button
        class="btn btn-primary"
        @click="openCreate"
      >
        新建题单
      </button>
    </template>

    <div class="min-h-0 flex-1 overflow-y-auto">
      <div class="mx-auto w-full max-w-[1120px]">
        <!-- create / edit form -->
        <div
          v-if="showForm"
          class="panel mb-4"
        >
          <div class="mb-3 text-sm font-medium text-ink">
            {{ editingId == null ? '新建题单' : `编辑题单 #${editingId}` }}
          </div>
          <div class="grid gap-3 sm:grid-cols-2">
            <label class="flex flex-col gap-1">
              <span class="text-[12px] text-dim">名称 *</span>
              <input
                v-model="form.name"
                class="input"
                placeholder="如：面试高频"
              >
            </label>
            <label class="flex flex-col gap-1">
              <span class="text-[12px] text-dim">可见性</span>
              <select
                v-model="form.visibility"
                class="input"
              >
                <option value="PRIVATE">
                  私密（仅自己可见）
                </option>
                <option value="PUBLIC">
                  公开（题单广场可见）
                </option>
              </select>
            </label>
            <label class="flex flex-col gap-1 sm:col-span-2">
              <span class="text-[12px] text-dim">简介</span>
              <input
                v-model="form.description"
                class="input"
                placeholder="一句话描述该题单"
              >
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
              @click="showForm = false"
            >
              取消
            </button>
          </div>
        </div>

        <div
          v-if="loading"
          class="py-16 text-center text-sm text-faint"
        >
          加载题单…
        </div>

        <div
          v-else-if="needsLogin"
          class="mx-auto max-w-md py-16 text-center"
        >
          <p class="text-sm text-dim">
            需要登录后才能管理我的题单。
          </p>
          <button
            class="btn mt-4"
            @click="login('/playground/sets')"
          >
            登录
          </button>
        </div>

        <div
          v-else-if="error"
          class="py-16 text-center text-sm text-[#f43f5e]"
        >
          加载失败：{{ error }}
        </div>

        <div
          v-else-if="!sets.length"
          class="py-16 text-center text-sm text-faint"
        >
          还没有题单，点击右上角「新建题单」创建你的第一份题单。
        </div>

        <div
          v-else
          class="grid gap-3 grid-cols-[repeat(auto-fill,minmax(280px,1fr))]"
        >
          <div
            v-for="set in sets"
            :key="set.id"
            class="flex flex-col rounded-[14px] border border-line bg-card p-4"
          >
            <div class="flex items-center gap-2">
              <button
                type="button"
                class="truncate text-left font-medium text-ink transition hover:text-accent"
                @click="startSet(set)"
              >
                {{ set.name }}
              </button>
              <span
                class="ml-auto shrink-0 rounded-[5px] px-1.5 py-0.5 text-[11px]"
                :class="set.visibility === 'PUBLIC' ? 'text-ok' : 'text-faint'"
              >
                {{ set.visibility === 'PUBLIC' ? '公开' : '私密' }}
              </span>
            </div>
            <div class="mt-1 text-[12px] text-faint">
              {{ set.problemCount }} 题
            </div>
            <p
              v-if="set.description"
              class="mt-2 line-clamp-2 text-[12.5px] text-dim"
            >
              {{ set.description }}
            </p>
            <div class="mt-3 flex flex-wrap gap-2 border-t border-line pt-3">
              <button
                class="btn"
                @click="startSet(set)"
              >
                开始刷题
              </button>
              <button
                class="btn"
                @click="openPicker(set)"
              >
                管理题目
              </button>
              <button
                class="btn"
                @click="openEdit(set)"
              >
                编辑
              </button>
              <button
                class="btn text-danger"
                @click="remove(set)"
              >
                删除
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- membership picker -->
    <div
      v-if="pickerOpen"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      @click.self="closePicker"
    >
      <div class="flex max-h-[80vh] w-full max-w-[640px] flex-col rounded-[14px] border border-line bg-card">
        <div class="flex items-center justify-between border-b border-line px-4 py-3">
          <div class="text-sm font-medium text-ink">
            管理题目 · {{ pickerSet?.name }}
          </div>
          <button
            class="text-dim transition hover:text-ink"
            @click="closePicker"
          >
            ✕
          </button>
        </div>
        <div class="min-h-0 flex-1 overflow-y-auto p-3">
          <div
            v-if="pickerLoading"
            class="py-10 text-center text-sm text-faint"
          >
            加载题目…
          </div>
          <div
            v-else-if="!allProblems.length"
            class="py-10 text-center text-sm text-faint"
          >
            题库暂无可选题目。
          </div>
          <label
            v-for="p in allProblems"
            v-else
            :key="p.id"
            class="flex cursor-pointer items-center gap-3 rounded-[8px] px-2 py-1.5 hover:bg-soft"
          >
            <input
              type="checkbox"
              :checked="picked.has(p.id)"
              @change="togglePicked(p.id)"
            >
            <span class="text-[13px] text-ink">{{ p.title }}</span>
            <span
              class="text-[12px]"
              :class="difficultyMeta(p.difficulty).color"
            >{{ difficultyMeta(p.difficulty).label }}</span>
            <span class="ml-auto text-[12px] text-faint">#{{ p.id }}</span>
          </label>
        </div>
        <div class="flex items-center justify-end gap-2 border-t border-line px-4 py-3">
          <span class="mr-auto text-[12px] text-faint">已选 {{ picked.size }} 题</span>
          <button
            class="btn"
            :disabled="pickerSaving"
            @click="closePicker"
          >
            取消
          </button>
          <button
            class="btn btn-primary"
            :disabled="pickerSaving"
            @click="savePicker"
          >
            {{ pickerSaving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </PageShell>
</template>
