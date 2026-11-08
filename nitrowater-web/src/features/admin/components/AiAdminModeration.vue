<script setup lang="ts">
/**
 * Content-moderation review panel: the active posture, the loaded dictionary size and a filterable
 * page through the hits.
 *
 * <p>Read-only on purpose. The switch that changes behaviour lives in the settings card above; this
 * panel exists so an administrator can decide <em>whether</em> to switch, by seeing what the
 * dictionary actually catches. In the default {@code LOG} posture nothing is blocked yet, so a
 * false positive here is a word to add to the allow list, not an incident.</p>
 *
 * <p>Paging is keyset by hit id: rows arrive continuously, so an offset page would shift under the
 * reviewer between clicks.</p>
 */
import { computed, onMounted, ref } from 'vue'
import { toast } from '../../../shared/ui/toast'
import {
  fetchAdminModeration,
  reloadAdminModeration,
  type AiModerationHit,
  type AiModerationQuery,
  type AiModerationReport,
} from '../../../api/ai'
import AdminFilterCard from './AdminFilterCard.vue'

const PAGE_SIZE = 50

const report = ref<AiModerationReport | null>(null)
const hits = ref<AiModerationHit[]>([])
const loading = ref(false)
const reloading = ref(false)
const error = ref('')
/** False until the first load settles; prevents an empty table from flashing. */
const ready = ref(false)
const page = ref(0)

const keyword = ref('')
const direction = ref('')
const action = ref('')

const MODE_LABEL: Record<string, string> = {
  OFF: '已关闭',
  LOG: '仅记录',
  ENFORCE: '拦截 + 遮蔽',
}

const DIRECTION_LABEL: Record<string, string> = {
  INPUT: '用户输入',
  OUTPUT: '回答',
  MEMORY: '记忆',
  PROFILE: '画像偏好',
}

const ACTION_LABEL: Record<string, string> = {
  LOGGED: '仅记录',
  BLOCKED: '已拦截',
  MASKED: '已遮蔽',
}

const DIRECTION_OPTIONS = Object.entries(DIRECTION_LABEL).map(([value, label]) => ({ value, label }))
const ACTION_OPTIONS = Object.entries(ACTION_LABEL).map(([value, label]) => ({ value, label }))

const modeLabel = computed(() => MODE_LABEL[report.value?.mode ?? 'OFF'] ?? report.value?.mode ?? '—')
const filtering = computed(() => keyword.value.trim() !== '' || direction.value !== '' || action.value !== '')

onMounted(() => load())

function query(target = page.value): AiModerationQuery {
  return {
    keyword: keyword.value || undefined,
    direction: direction.value || undefined,
    action: action.value || undefined,
    page: target,
    size: PAGE_SIZE,
  }
}

/** Loads the selected page, replacing whatever was on screen. */
async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchAdminModeration(query())
    report.value = result
    hits.value = result.hits.content
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

/** `el-pagination` reports a 1-based page; the wire contract is 0-based. */
function pageTo(oneBased: number): void {
  page.value = Math.max(0, oneBased - 1)
  void load()
}

/**
 * Picks up an edited word list without a redeploy. This is the fast path for a false positive:
 * add the phrase to the allow list, reload, and the next request uses the new dictionary.
 */
async function reload(): Promise<void> {
  reloading.value = true
  error.value = ''
  try {
    const result = await reloadAdminModeration(query())
    report.value = result
    hits.value = result.hits.content
    toast.success(`词表已重载：${result.dictionarySize} 条`)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
    toast.error('词表重载失败')
  } finally {
    reloading.value = false
  }
}

function resetFilters(): void {
  keyword.value = ''
  direction.value = ''
  action.value = ''
  search()
}

/** Table slots hand back an untyped row; narrow it once. */
function asHit(row: unknown): AiModerationHit {
  return row as AiModerationHit
}

function displayName(row: unknown): string {
  const hit = asHit(row)
  return hit.nickname || hit.username || `UID ${hit.uid}`
}

function directionLabel(row: unknown): string {
  return DIRECTION_LABEL[asHit(row).direction] ?? asHit(row).direction
}

function actionLabel(row: unknown): string {
  return ACTION_LABEL[asHit(row).action] ?? asHit(row).action
}

function actionType(row: unknown): 'info' | 'warning' | 'danger' {
  const value = asHit(row).action
  if (value === 'BLOCKED') return 'danger'
  if (value === 'MASKED') return 'warning'
  return 'info'
}

function formatTime(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
}

// The page header owns the action cluster, so the reload button is rendered there and drives this
// panel through its exposed state.
defineExpose({ reload, reloading })
</script>

<template>
  <div class="space-y-4">
    <AdminFilterCard
      :loading="loading"
      @submit="search"
      @reset="resetFilters"
    >
      <el-form-item label="用户">
        <el-input
          v-model.trim="keyword"
          placeholder="用户名 / 昵称 / UID"
          clearable
          class="w-[200px]!"
        />
      </el-form-item>
      <el-form-item label="来源">
        <el-select
          v-model="direction"
          placeholder="全部"
          clearable
          class="w-[120px]!"
        >
          <el-option
            v-for="option in DIRECTION_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="处置">
        <el-select
          v-model="action"
          placeholder="全部"
          clearable
          class="w-[120px]!"
        >
          <el-option
            v-for="option in ACTION_OPTIONS"
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
            <span class="admin-card-title">内容安全审核</span>
            <el-tag
              :type="report?.mode === 'ENFORCE' ? 'danger' : report?.mode === 'LOG' ? 'warning' : 'info'"
              size="small"
              effect="plain"
            >
              {{ modeLabel }}
            </el-tag>
            <span class="admin-card-meta">
              词表 {{ report?.dictionarySize ?? 0 }} 条 · 近 24h 命中 {{ report?.hitsLast24Hours ?? 0 }} ·
              {{ filtering ? `筛选命中 ${report?.hits.page.totalElements ?? 0} 条` : `命中合计 ${report?.hits.page.totalElements ?? 0} 条` }}
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
        <p class="text-xs text-faint">
          只有「用户输入」的命中会计入冷却（连续命中会短暂停用 AI 接口）；回答、记忆与画像偏好的命中只用于
          调词——误判时把该词加到 <code>ai/moderation/allow.txt</code> 再点「重载词表」。
        </p>
      </div>

      <el-skeleton
        v-if="!ready"
        :rows="5"
        animated
        class="p-4"
      />
      <template v-else>
        <el-table
          v-loading="loading"
          :data="hits"
          size="small"
          stripe
          :empty-text="filtering ? '没有符合条件的命中记录' : '暂无命中记录'"
        >
          <el-table-column
            label="时间"
            width="180"
          >
            <template #default="{ row }">
              {{ formatTime(asHit(row).createdAt) }}
            </template>
          </el-table-column>
          <el-table-column
            label="用户"
            min-width="140"
          >
            <template #default="{ row }">
              {{ displayName(row) }}
            </template>
          </el-table-column>
          <el-table-column
            label="来源"
            width="110"
          >
            <template #default="{ row }">
              {{ directionLabel(row) }}
            </template>
          </el-table-column>
          <el-table-column
            label="处置"
            width="100"
          >
            <template #default="{ row }">
              <el-tag
                :type="actionType(row)"
                size="small"
              >
                {{ actionLabel(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            label="命中词"
            min-width="200"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ asHit(row).words }}
            </template>
          </el-table-column>
        </el-table>

        <div class="admin-pager">
          <el-pagination
            :current-page="page + 1"
            :page-size="PAGE_SIZE"
            :total="report?.hits.page.totalElements ?? 0"
            background
            layout="prev, pager, next"
            @current-change="pageTo"
          />
        </div>
      </template>
    </el-card>
  </div>
</template>
