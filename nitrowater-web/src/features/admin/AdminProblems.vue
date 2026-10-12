<script setup lang="ts">
/** Admin · problems — list (disabled ones included) with a link into the editor. */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { difficultyMeta } from '../playground/labels'
import { listProblems, type ProblemSummary } from './api'

const router = useRouter()
const rows = ref<ProblemSummary[]>([])
const loading = ref(false)
const error = ref('')

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    rows.value = await listProblems()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function openNew(): void {
  void router.push('/admin/problems/new')
}

function openEdit(row: ProblemSummary): void {
  void router.push(`/admin/problems/${row.id}`)
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="mx-auto w-full max-w-[1280px] p-4 sm:p-6">
    <div class="section-head">
      <div>
        <h1 class="text-lg font-semibold text-ink">
          题目
        </h1>
        <p class="mt-1 text-[13px] text-dim">
          支持中英双语、标签与题单关联、测试用例管理。
        </p>
      </div>
      <button
        class="btn btn-primary"
        @click="openNew"
      >
        新建题目
      </button>
    </div>

    <div
      v-if="error"
      class="mb-4 rounded-[10px] border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger"
    >
      {{ error }}
    </div>

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
              Slug
            </th>
            <th class="px-3 py-2 font-normal">
              难度
            </th>
            <th class="px-3 py-2 font-normal">
              标签
            </th>
            <th class="px-3 py-2 font-normal">
              题单
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
              暂无题目。
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
                {{ row.title }}
              </div>
              <div
                v-if="row.titleEn"
                class="text-[12px] text-faint"
              >
                {{ row.titleEn }}
              </div>
            </td>
            <td class="px-3 py-2 font-mono text-[12px] text-dim">
              {{ row.slug }}
            </td>
            <td
              class="px-3 py-2"
              :class="difficultyMeta(row.difficulty).color"
            >
              {{ difficultyMeta(row.difficulty).label }}
            </td>
            <td class="px-3 py-2">
              <div class="flex flex-wrap gap-1">
                <span
                  v-for="t in row.tags"
                  :key="t.id"
                  class="tag"
                >{{ t.name }}</span>
                <span
                  v-if="!row.tags.length"
                  class="text-faint"
                >-</span>
              </div>
            </td>
            <td class="px-3 py-2">
              <div class="flex flex-wrap gap-1">
                <span
                  v-for="s in row.sets"
                  :key="s.id"
                  class="tag"
                >{{ s.name }}</span>
                <span
                  v-if="!row.sets.length"
                  class="text-faint"
                >-</span>
              </div>
            </td>
            <td class="px-3 py-2">
              <span :class="row.enabled ? 'text-ok' : 'text-faint'">{{ row.enabled ? '启用' : '停用' }}</span>
            </td>
            <td class="px-3 py-2">
              <button
                class="text-accent hover:underline"
                @click="openEdit(row)"
              >
                编辑
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
