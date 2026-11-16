<script setup lang="ts">
/**
 * Index inventory: which corpora and markdown files are actually embedded in the vector store.
 *
 * <p>The overview card above answers «is the index healthy»; this panel answers «which documents is
 * it built from». That question only became answerable when the corpora moved out of the jar into a
 * mounted directory: a content-only deploy adds/edits/deletes markdown without touching the image,
 * so the operator needs to see whether the mount and the index still agree.</p>
 *
 * <p>Rows come from the server's join of the filesystem with `ai_doc_index`, so the four states are
 * authoritative — no client-side diffing:</p>
 * <ul>
 *   <li>{@code INDEXED} — disk and index agree, nothing to do;</li>
 *   <li>{@code STALE} — file edited after the last run;</li>
 *   <li>{@code MISSING} — file on disk, never embedded (new page);</li>
 *   <li>{@code ORPHAN} — embedded, file deleted/renamed (its vectors go away on re-index).</li>
 * </ul>
 */
import { computed, onMounted, ref } from 'vue'
import {
  fetchAdminAiIndexDocuments,
  fetchAdminAiIndexReport,
  type AiCorpusIndex,
  type AiDocIndexStatus,
  type AiIndexDoc,
  type AiIndexReport,
} from '../../../api/ai'
import AdminFilterCard from './AdminFilterCard.vue'

const PAGE_SIZE = 50

const STATUS_LABEL: Record<AiDocIndexStatus, string> = {
  INDEXED: '已索引',
  STALE: '内容已变',
  MISSING: '未索引',
  ORPHAN: '文件已删',
}

const STATUS_TAG: Record<AiDocIndexStatus, 'success' | 'warning' | 'info' | 'danger'> = {
  INDEXED: 'success',
  STALE: 'warning',
  MISSING: 'info',
  ORPHAN: 'danger',
}

const STATUS_OPTIONS = (Object.keys(STATUS_LABEL) as AiDocIndexStatus[]).map((value) => ({
  value,
  label: STATUS_LABEL[value],
}))

const report = ref<AiIndexReport | null>(null)
const docs = ref<AiIndexDoc[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
/** False until the first load settles; prevents an empty table from flashing. */
const ready = ref(false)
const page = ref(0)

const corpus = ref('')
const status = ref<AiDocIndexStatus | ''>('')

const corpusOptions = computed(() => (report.value?.corpora ?? []).map((item) => ({
  value: item.corpus,
  label: item.corpus,
})))

/** Any file whose state is not «indexed», i.e. a rebuild would change the vector store. */
const drifted = computed(() => (report.value?.corpora ?? [])
  .some((item) => item.stale + item.missing + item.orphan > 0))

const filtering = computed(() => corpus.value !== '' || status.value !== '')

onMounted(() => load())

function query(target = page.value) {
  return {
    corpus: corpus.value || undefined,
    status: status.value || undefined,
    page: target,
    size: PAGE_SIZE,
  }
}

/** Loads the corpus roll-up and the selected document page together: they share the same truth. */
async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [summary, result] = await Promise.all([
      fetchAdminAiIndexReport(),
      fetchAdminAiIndexDocuments(query()),
    ])
    report.value = summary
    docs.value = result.content
    total.value = result.page.totalElements
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    loading.value = false
    ready.value = true
  }
}

/** Restarts from the first page whenever a filter changes. */
function search(): void {
  page.value = 0
  void load()
}

function resetFilters(): void {
  corpus.value = ''
  status.value = ''
  search()
}

/** `el-pagination` reports a 1-based page; the wire contract is 0-based. */
function pageTo(oneBased: number): void {
  page.value = Math.max(0, oneBased - 1)
  void load()
}

/** Clicking a corpus row narrows the document list to it — the usual drill-down. */
function filterCorpus(row: AiCorpusIndex): void {
  corpus.value = row.corpus
  search()
}

function asCorpus(row: unknown): AiCorpusIndex {
  return row as AiCorpusIndex
}

function asDoc(row: unknown): AiIndexDoc {
  return row as AiIndexDoc
}

function driftOf(row: unknown): number {
  const item = asCorpus(row)
  return item.stale + item.missing + item.orphan
}

function corpusMeta(row: unknown): string {
  const item = asCorpus(row)
  return `${item.documents} 文档 · ${item.chunks} 块`
}

function statusLabel(value: AiDocIndexStatus): string {
  return STATUS_LABEL[value] ?? value
}

function statusTag(value: AiDocIndexStatus): 'success' | 'warning' | 'info' | 'danger' {
  return STATUS_TAG[value] ?? 'info'
}

function formatTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
}

function languageLabel(value: string): string {
  return value === 'en' ? 'English' : '中文'
}

defineExpose({ reload: load })
</script>

<template>
  <div class="space-y-4">
    <el-card
      shadow="never"
      class="admin-card admin-card-flush"
    >
      <template #header>
        <div class="admin-card-header">
          <div class="flex flex-wrap items-baseline gap-2">
            <span class="admin-card-title">语料与索引</span>
            <span class="admin-card-meta">
              {{ report?.documents ?? 0 }} 个文档 · {{ report?.chunks ?? 0 }} 块 ·
              最近重建 {{ formatTime(report?.indexedAt) }}
            </span>
          </div>
        </div>
      </template>

      <div class="space-y-3 px-4 py-3">
        <el-alert
          v-if="error"
          :title="error"
          type="error"
          show-icon
          :closable="false"
        />
        <el-alert
          v-else-if="drifted"
          title="有文档与索引不一致"
          type="warning"
          show-icon
          :closable="false"
        >
          <template #default>
            重建索引会按当前 docs 目录重新切片：`内容已变` / `未索引` 会被更新，`文件已删` 的向量会被删除。
          </template>
        </el-alert>
        <p
          v-else
          class="text-xs text-faint"
        >
          语料来自挂载目录（每个子目录一个语料），以下是磁盘与向量索引的比对结果。点语料行可只看它的文档。
        </p>
      </div>

      <el-skeleton
        v-if="!ready"
        :rows="4"
        animated
        class="p-4"
      />
      <el-table
        v-else-if="report && report.corpora.length > 0"
        v-loading="loading"
        :data="report.corpora"
        size="small"
        stripe
        :row-style="{ cursor: 'pointer' }"
        @row-click="filterCorpus"
      >
        <el-table-column
          label="语料"
          min-width="240"
        >
          <template #default="{ row }">
            <p class="text-[13px] font-medium text-ink">
              {{ asCorpus(row).corpus }}
            </p>
            <p
              v-if="asCorpus(row).description"
              class="truncate text-xs text-faint"
              :title="asCorpus(row).description"
            >
              {{ asCorpus(row).description }}
            </p>
            <p
              v-else-if="!asCorpus(row).sourcePath"
              class="text-xs text-faint"
            >
              目录已移除，仅剩索引记录
            </p>
          </template>
        </el-table-column>
        <el-table-column
          label="文档 / 切片"
          width="150"
        >
          <template #default="{ row }">
            {{ corpusMeta(row) }}
          </template>
        </el-table-column>
        <el-table-column
          label="索引状态"
          min-width="200"
        >
          <template #default="{ row }">
            <el-tag
              v-if="driftOf(row) === 0"
              type="success"
              size="small"
              effect="plain"
            >
              一致
            </el-tag>
            <span
              v-else
              class="inline-flex flex-wrap gap-1"
            >
              <el-tag
                v-if="asCorpus(row).stale"
                type="warning"
                size="small"
                effect="plain"
              >
                内容已变 {{ asCorpus(row).stale }}
              </el-tag>
              <el-tag
                v-if="asCorpus(row).missing"
                type="info"
                size="small"
                effect="plain"
              >
                未索引 {{ asCorpus(row).missing }}
              </el-tag>
              <el-tag
                v-if="asCorpus(row).orphan"
                type="danger"
                size="small"
                effect="plain"
              >
                文件已删 {{ asCorpus(row).orphan }}
              </el-tag>
            </span>
          </template>
        </el-table-column>
        <el-table-column
          label="最近索引"
          width="180"
        >
          <template #default="{ row }">
            {{ formatTime(asCorpus(row).indexedAt) }}
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-else
        description="没有发现任何语料目录（检查 AI_DOCS_ROOT 挂载）"
        :image-size="60"
      />
    </el-card>

    <AdminFilterCard
      :loading="loading"
      @submit="search"
      @reset="resetFilters"
    >
      <el-form-item label="语料">
        <el-select
          v-model="corpus"
          placeholder="全部"
          clearable
          class="w-[160px]!"
        >
          <el-option
            v-for="option in corpusOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select
          v-model="status"
          placeholder="全部"
          clearable
          class="w-[140px]!"
        >
          <el-option
            v-for="option in STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
    </AdminFilterCard>

    <el-card
      shadow="never"
      class="admin-card admin-card-flush"
    >
      <template #header>
        <div class="admin-card-header">
          <div class="flex flex-wrap items-baseline gap-2">
            <span class="admin-card-title">文档清单</span>
            <span class="admin-card-meta">
              {{ filtering ? `筛选 ${total} 个` : `合计 ${total} 个` }}
            </span>
          </div>
        </div>
      </template>

      <el-skeleton
        v-if="!ready"
        :rows="5"
        animated
        class="p-4"
      />
      <template v-else>
        <el-table
          v-loading="loading"
          :data="docs"
          size="small"
          stripe
          :empty-text="filtering ? '没有符合条件的文档' : '索引里还没有文档'"
        >
          <el-table-column
            label="状态"
            width="110"
          >
            <template #default="{ row }">
              <el-tag
                :type="statusTag(asDoc(row).status)"
                size="small"
                effect="plain"
              >
                {{ statusLabel(asDoc(row).status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            label="语料"
            width="120"
          >
            <template #default="{ row }">
              {{ asDoc(row).corpus }}
            </template>
          </el-table-column>
          <el-table-column
            label="文档"
            min-width="260"
          >
            <template #default="{ row }">
              <p class="truncate text-[13px] text-ink">
                {{ asDoc(row).title }}
              </p>
              <p
                class="truncate font-mono text-xs text-faint"
                :title="asDoc(row).docPath"
              >
                {{ asDoc(row).docPath }}
              </p>
            </template>
          </el-table-column>
          <el-table-column
            label="语言"
            width="90"
          >
            <template #default="{ row }">
              {{ languageLabel(asDoc(row).lang) }}
            </template>
          </el-table-column>
          <el-table-column
            label="切片"
            width="80"
          >
            <template #default="{ row }">
              {{ asDoc(row).chunks }}
            </template>
          </el-table-column>
          <el-table-column
            label="最近索引"
            width="180"
          >
            <template #default="{ row }">
              {{ formatTime(asDoc(row).indexedAt) }}
            </template>
          </el-table-column>
        </el-table>

        <div class="admin-pager">
          <el-pagination
            :current-page="page + 1"
            :page-size="PAGE_SIZE"
            :total="total"
            background
            layout="prev, pager, next"
            @current-change="pageTo"
          />
        </div>
      </template>
    </el-card>
  </div>
</template>
