<script setup lang="ts">
/**
 * Admin · problem editor — create / update a problem, its tag & set links, and its test cases.
 *
 * The route param is either `new` (create) or a numeric id (edit). Tags and problem sets are
 * loaded once for the multi-selects; test cases have their own save (they hit a separate
 * endpoint and only make sense once the problem exists).
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from '../../shared/ui/toast'
import {
  createProblem,
  getProblem,
  listSets,
  listTags,
  replaceTestCases,
  updateProblem,
  type ProblemSet,
  type ProblemUpsert,
  type Tag,
  type TestCaseUpsert,
} from './api'

const route = useRoute()
const router = useRouter()

const problemId = computed(() => String(route.params.id))
const isNew = computed(() => problemId.value === 'new')

const DIFFICULTIES = [
  { value: 'EASY', label: '简单' },
  { value: 'MEDIUM', label: '中等' },
  { value: 'HARD', label: '困难' },
]
const IO_MODES = ['ACM', 'LEETCODE']

const tags = ref<Tag[]>([])
const sets = ref<ProblemSet[]>([])

const loading = ref(false)
const saving = ref(false)
const casesSaving = ref(false)
const error = ref('')

const form = reactive<ProblemUpsert>({
  slug: '',
  title: '',
  titleEn: '',
  difficulty: 'EASY',
  description: '',
  descriptionEn: '',
  ioMode: 'ACM',
  timeLimitMs: 5000,
  memoryLimitKb: 262144,
  rewardPoints: 0,
  enabled: true,
})
const selectedTagIds = ref<number[]>([])
const selectedSetIds = ref<number[]>([])
const templatesText = ref('')
const judgeConfigText = ref('')
const cases = ref<TestCaseUpsert[]>([])

function resetForm(): void {
  form.slug = ''
  form.title = ''
  form.titleEn = ''
  form.difficulty = 'EASY'
  form.description = ''
  form.descriptionEn = ''
  form.ioMode = 'ACM'
  form.timeLimitMs = 5000
  form.memoryLimitKb = 262144
  form.rewardPoints = 0
  form.enabled = true
  selectedTagIds.value = []
  selectedSetIds.value = []
  templatesText.value = ''
  judgeConfigText.value = ''
  cases.value = []
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [tagList, setList] = await Promise.all([listTags(), listSets()])
    tags.value = tagList
    sets.value = setList
    if (isNew.value) {
      resetForm()
    } else {
      const detail = await getProblem(problemId.value)
      form.slug = detail.slug
      form.title = detail.title
      form.titleEn = detail.titleEn ?? ''
      form.difficulty = detail.difficulty
      form.description = detail.description
      form.descriptionEn = detail.descriptionEn ?? ''
      form.ioMode = detail.ioMode
      form.timeLimitMs = detail.timeLimitMs
      form.memoryLimitKb = detail.memoryLimitKb
      form.rewardPoints = detail.rewardPoints
      form.enabled = detail.enabled
      selectedTagIds.value = detail.tags.map((t) => t.id)
      selectedSetIds.value = detail.sets.map((s) => s.id)
      templatesText.value = detail.templates ? JSON.stringify(detail.templates, null, 2) : ''
      judgeConfigText.value = detail.judgeConfig ? JSON.stringify(detail.judgeConfig, null, 2) : ''
      cases.value = detail.testCases.map((c) => ({
        idx: c.idx,
        stdin: c.stdin ?? '',
        expectedOutput: c.expectedOutput ?? '',
        isSample: c.isSample,
        score: c.score,
      }))
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function toggle(list: number[], id: number): void {
  const index = list.indexOf(id)
  if (index >= 0) list.splice(index, 1)
  else list.push(id)
}

function parseJsonObject<T extends object>(text: string, what: string): T | null {
  const trimmed = text.trim()
  if (!trimmed) return null
  let parsed: unknown
  try {
    parsed = JSON.parse(trimmed)
  } catch {
    throw new Error(`${what} 不是合法 JSON`)
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`${what} 必须是一个 JSON 对象`)
  }
  return parsed as T
}

async function save(): Promise<void> {
  if (saving.value) return
  if (!form.title.trim()) {
    toast.warning('请填写中文标题')
    return
  }
  if (!form.description.trim()) {
    toast.warning('请填写中文题面')
    return
  }
  saving.value = true
  try {
    const templates = parseJsonObject<Record<string, string>>(templatesText.value, '模板')
    const judgeConfig = parseJsonObject<Record<string, unknown>>(judgeConfigText.value, '评测配置')
    const payload: ProblemUpsert = {
      slug: isNew.value ? form.slug?.trim() || null : null,
      title: form.title.trim(),
      titleEn: form.titleEn?.trim() || null,
      difficulty: form.difficulty,
      tagIds: [...selectedTagIds.value],
      setIds: [...selectedSetIds.value],
      description: form.description,
      descriptionEn: form.descriptionEn?.trim() || null,
      ioMode: form.ioMode,
      templates,
      judgeConfig,
      timeLimitMs: Number(form.timeLimitMs) || null,
      memoryLimitKb: Number(form.memoryLimitKb) || null,
      rewardPoints: Number(form.rewardPoints) || 0,
      enabled: form.enabled,
    }
    if (isNew.value) {
      const created = await createProblem(payload)
      toast.success('题目已创建')
      await router.replace(`/admin/problems/${created.id}`)
    } else {
      await updateProblem(Number(problemId.value), payload)
      toast.success('题目已更新')
    }
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    saving.value = false
  }
}

function addCase(): void {
  cases.value.push({ idx: cases.value.length, stdin: '', expectedOutput: '', isSample: false, score: 0 })
}

function removeCase(index: number): void {
  cases.value.splice(index, 1)
  cases.value.forEach((c, i) => {
    c.idx = i
  })
}

async function saveCases(): Promise<void> {
  if (isNew.value) {
    toast.warning('请先保存题目，再编辑测试用例')
    return
  }
  casesSaving.value = true
  try {
    const payload: TestCaseUpsert[] = cases.value.map((c, i) => ({
      idx: i,
      stdin: c.stdin ?? '',
      expectedOutput: c.expectedOutput ?? '',
      isSample: !!c.isSample,
      score: Number(c.score) || 0,
    }))
    await replaceTestCases(Number(problemId.value), payload)
    toast.success('测试用例已保存')
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    casesSaving.value = false
  }
}

watch(problemId, () => {
  void load()
})

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="mx-auto w-full max-w-[1120px] p-4 sm:p-6">
    <div class="section-head">
      <div>
        <h1 class="text-lg font-semibold text-ink">
          {{ isNew ? '新建题目' : `编辑题目 #${problemId}` }}
        </h1>
        <p class="mt-1 text-[13px] text-dim">
          双语字段留空时展示回退到中文；Slug 仅在新建生效。
        </p>
      </div>
      <router-link
        to="/admin/problems"
        class="btn"
      >
        返回列表
      </router-link>
    </div>

    <div
      v-if="error"
      class="mb-4 rounded-[10px] border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger"
    >
      {{ error }}
    </div>

    <div
      v-if="loading"
      class="py-16 text-center text-sm text-faint"
    >
      加载中…
    </div>

    <template v-else>
      <!-- Base fields -->
      <div class="panel mb-4">
        <div class="mb-3 text-sm font-medium text-ink">
          基本信息
        </div>
        <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <label class="flex flex-col gap-1">
            <span class="text-[12px] text-dim">中文标题 *</span>
            <input
              v-model="form.title"
              class="input"
            >
          </label>
          <label class="flex flex-col gap-1">
            <span class="text-[12px] text-dim">英文标题</span>
            <input
              v-model="form.titleEn"
              class="input"
            >
          </label>
          <label class="flex flex-col gap-1">
            <span class="text-[12px] text-dim">Slug（留空自动生成，编辑时忽略）</span>
            <input
              v-model="form.slug"
              class="input"
              :disabled="!isNew"
            >
          </label>
          <label class="flex flex-col gap-1">
            <span class="text-[12px] text-dim">难度</span>
            <select
              v-model="form.difficulty"
              class="select"
            >
              <option
                v-for="d in DIFFICULTIES"
                :key="d.value"
                :value="d.value"
              >
                {{ d.label }}
              </option>
            </select>
          </label>
          <label class="flex flex-col gap-1">
            <span class="text-[12px] text-dim">评测模式</span>
            <select
              v-model="form.ioMode"
              class="select"
            >
              <option
                v-for="m in IO_MODES"
                :key="m"
                :value="m"
              >
                {{ m }}
              </option>
            </select>
          </label>
          <label class="flex items-center gap-2 self-end pb-2">
            <input
              v-model="form.enabled"
              type="checkbox"
            >
            <span class="text-[13px] text-ink">启用</span>
          </label>
          <label class="flex flex-col gap-1">
            <span class="text-[12px] text-dim">时限 (ms)</span>
            <input
              v-model.number="form.timeLimitMs"
              class="input"
              type="number"
            >
          </label>
          <label class="flex flex-col gap-1">
            <span class="text-[12px] text-dim">内存 (KB)</span>
            <input
              v-model.number="form.memoryLimitKb"
              class="input"
              type="number"
            >
          </label>
          <label class="flex flex-col gap-1">
            <span class="text-[12px] text-dim">奖励积分</span>
            <input
              v-model.number="form.rewardPoints"
              class="input"
              type="number"
            >
          </label>
        </div>
      </div>

      <!-- Statement -->
      <div class="panel mb-4">
        <div class="mb-3 text-sm font-medium text-ink">
          题面（Markdown）
        </div>
        <div class="grid gap-3 lg:grid-cols-2">
          <label class="flex flex-col gap-1">
            <span class="text-[12px] text-dim">中文题面 *</span>
            <textarea
              v-model="form.description"
              class="textarea min-h-[220px]"
            />
          </label>
          <label class="flex flex-col gap-1">
            <span class="text-[12px] text-dim">英文题面</span>
            <textarea
              v-model="form.descriptionEn"
              class="textarea min-h-[220px]"
            />
          </label>
        </div>
      </div>

      <!-- Tags & sets -->
      <div class="panel mb-4">
        <div class="mb-3 text-sm font-medium text-ink">
          标签 / 题单
        </div>
        <div class="grid gap-4 lg:grid-cols-2">
          <div>
            <div class="mb-2 text-[12px] text-dim">
              标签
            </div>
            <div class="flex flex-wrap gap-2">
              <label
                v-for="t in tags"
                :key="t.id"
                class="flex cursor-pointer items-center gap-1.5 rounded-md border border-line bg-soft px-2 py-1 text-[12.5px]"
                :class="selectedTagIds.includes(t.id) ? 'border-accent text-accent' : 'text-dim'"
              >
                <input
                  type="checkbox"
                  class="hidden"
                  :checked="selectedTagIds.includes(t.id)"
                  @change="toggle(selectedTagIds, t.id)"
                >
                {{ t.name }}
              </label>
              <span
                v-if="!tags.length"
                class="text-[12px] text-faint"
              >暂无标签，请先在「标签」中创建。</span>
            </div>
          </div>
          <div>
            <div class="mb-2 text-[12px] text-dim">
              题单
            </div>
            <div class="flex flex-wrap gap-2">
              <label
                v-for="s in sets"
                :key="s.id"
                class="flex cursor-pointer items-center gap-1.5 rounded-md border border-line bg-soft px-2 py-1 text-[12.5px]"
                :class="selectedSetIds.includes(s.id) ? 'border-accent text-accent' : 'text-dim'"
              >
                <input
                  type="checkbox"
                  class="hidden"
                  :checked="selectedSetIds.includes(s.id)"
                  @change="toggle(selectedSetIds, s.id)"
                >
                {{ s.name }}
              </label>
              <span
                v-if="!sets.length"
                class="text-[12px] text-faint"
              >暂无题单，请先在「题单」中创建。</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Advanced JSON -->
      <div class="panel mb-4">
        <div class="mb-3 text-sm font-medium text-ink">
          高级（JSON）
        </div>
        <div class="grid gap-3 lg:grid-cols-2">
          <label class="flex flex-col gap-1">
            <span class="text-[12px] text-dim">语言模板 templates（{ "cpp": "…" }）</span>
            <textarea
              v-model="templatesText"
              class="textarea min-h-[140px]"
              placeholder="{&quot;cpp&quot;: &quot;// your code&quot;}"
            />
          </label>
          <label class="flex flex-col gap-1">
            <span class="text-[12px] text-dim">评测配置 judgeConfig</span>
            <textarea
              v-model="judgeConfigText"
              class="textarea min-h-[140px]"
              placeholder="{&quot;compare&quot;: &quot;exact&quot;}"
            />
          </label>
        </div>
      </div>

      <div class="mb-6 flex items-center gap-2">
        <button
          class="btn btn-primary"
          :disabled="saving"
          @click="save"
        >
          {{ saving ? '保存中…' : '保存题目' }}
        </button>
      </div>

      <!-- Test cases -->
      <div class="panel">
        <div class="mb-3 flex items-center justify-between">
          <div class="text-sm font-medium text-ink">
            测试用例
            <span
              v-if="isNew"
              class="ml-2 text-[12px] text-faint"
            >保存题目后可编辑</span>
          </div>
          <div class="flex gap-2">
            <button
              class="btn"
              :disabled="isNew || casesSaving"
              @click="addCase"
            >
              添加用例
            </button>
            <button
              class="btn btn-primary"
              :disabled="isNew || casesSaving"
              @click="saveCases"
            >
              {{ casesSaving ? '保存中…' : '保存用例' }}
            </button>
          </div>
        </div>

        <div class="overflow-x-auto">
          <table class="w-full text-left text-sm">
            <thead>
              <tr class="border-b border-line text-[12px] text-faint">
                <th class="px-2 py-2 font-normal">
                  #
                </th>
                <th class="px-2 py-2 font-normal">
                  输入
                </th>
                <th class="px-2 py-2 font-normal">
                  期望输出
                </th>
                <th class="px-2 py-2 font-normal">
                  样例
                </th>
                <th class="px-2 py-2 font-normal">
                  分值
                </th>
                <th class="px-2 py-2 font-normal">
                  操作
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!cases.length">
                <td
                  colspan="6"
                  class="px-2 py-6 text-center text-faint"
                >
                  暂无用例。
                </td>
              </tr>
              <tr
                v-for="(c, i) in cases"
                :key="i"
                class="border-b border-line/60 last:border-0 align-top"
              >
                <td class="px-2 py-2 text-dim">
                  {{ i + 1 }}
                </td>
                <td class="px-2 py-2">
                  <textarea
                    v-model="c.stdin"
                    class="textarea min-h-[60px]"
                  />
                </td>
                <td class="px-2 py-2">
                  <textarea
                    v-model="c.expectedOutput"
                    class="textarea min-h-[60px]"
                  />
                </td>
                <td class="px-2 py-2 text-center">
                  <input
                    v-model="c.isSample"
                    type="checkbox"
                  >
                </td>
                <td class="px-2 py-2">
                  <input
                    v-model.number="c.score"
                    class="input !w-[72px]"
                    type="number"
                  >
                </td>
                <td class="px-2 py-2">
                  <button
                    class="text-danger hover:underline"
                    @click="removeCase(i)"
                  >
                    移除
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>
