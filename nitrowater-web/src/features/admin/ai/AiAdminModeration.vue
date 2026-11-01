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
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
// Explicit style import: an explicit API import bypasses the on-demand resolver's CSS injection.
import 'element-plus/es/components/message/style/css'
import {
  fetchAdminModeration,
  reloadAdminModeration,
  type AiModerationHit,
  type AiModerationQuery,
  type AiModerationReport,
} from '../../../api/ai'

const PAGE_SIZE = 50

const report = ref<AiModerationReport | null>(null)
const hits = ref<AiModerationHit[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const reloading = ref(false)
const error = ref('')
/** False until the first load settles; prevents an empty table from flashing. */
const ready = ref(false)

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
const filtering = computed(() => direction.value !== '' || action.value !== '')

onMounted(() => load())

// Re-query from the newest row whenever a filter changes; the accumulated page is discarded.
watch([direction, action], () => load())

function query(beforeId?: number): AiModerationQuery {
  return {
    direction: direction.value || undefined,
    action: action.value || undefined,
    beforeId,
    limit: PAGE_SIZE,
  }
}

/** Loads the newest page, replacing whatever was on screen. */
async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const page = await fetchAdminModeration(query())
    report.value = page
    hits.value = page.hits
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    loading.value = false
    ready.value = true
  }
}

/** Appends the next page, using the oldest loaded id as the cursor. */
async function loadMore(): Promise<void> {
  const last = hits.value[hits.value.length - 1]
  if (!last) return
  loadingMore.value = true
  error.value = ''
  try {
    const page = await fetchAdminModeration(query(last.id))
    report.value = page
    hits.value = [...hits.value, ...page.hits]
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    loadingMore.value = false
  }
}

/**
 * Picks up an edited word list without a redeploy. This is the fast path for a false positive:
 * add the phrase to the allow list, reload, and the next request uses the new dictionary.
 */
async function reload(): Promise<void> {
  reloading.value = true
  error.value = ''
  try {
    const page = await reloadAdminModeration(query())
    report.value = page
    hits.value = page.hits
    ElMessage.success(`词表已重载：${page.dictionarySize} 条`)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
    ElMessage.error('词表重载失败')
  } finally {
    reloading.value = false
  }
}

function resetFilters(): void {
  direction.value = ''
  action.value = ''
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
</script>

<template>
  <el-card
    shadow="never"
    class="admin-card"
  >
    <template #header>
      <div class="flex flex-wrap items-center gap-3">
        <span class="text-sm font-semibold">内容安全审核</span>
        <el-tag
          :type="report?.mode === 'ENFORCE' ? 'danger' : report?.mode === 'LOG' ? 'warning' : 'info'"
          size="small"
        >
          {{ modeLabel }}
        </el-tag>
        <span class="text-xs text-faint">
          词表 {{ report?.dictionarySize ?? 0 }} 条 · 近 24h 命中 {{ report?.hitsLast24Hours ?? 0 }} ·
          {{ filtering ? `筛选命中 ${report?.total ?? 0} 条` : `命中合计 ${report?.total ?? 0} 条` }}
        </span>
        <span class="grow" />
        <el-button
          size="small"
          :loading="loading"
          @click="load"
        >
          刷新
        </el-button>
        <el-button
          size="small"
          :loading="reloading"
          @click="reload"
        >
          重载词表
        </el-button>
      </div>
    </template>

    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
      class="mb-4"
    />

    <div class="mb-3 flex flex-wrap items-center gap-2">
      <el-select
        v-model="direction"
        placeholder="全部来源"
        clearable
        size="small"
        class="w-36"
      >
        <el-option
          v-for="option in DIRECTION_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-select
        v-model="action"
        placeholder="全部处置"
        clearable
        size="small"
        class="w-36"
      >
        <el-option
          v-for="option in ACTION_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-button
        v-if="filtering"
        size="small"
        text
        @click="resetFilters"
      >
        清除筛选
      </el-button>
    </div>

    <p class="mb-3 text-xs text-faint">
      只有「用户输入」的命中会计入冷却（连续命中会短暂停用 AI 接口）；回答、记忆与画像偏好的命中只用于
      调词——误判时把该词加到 <code>ai/moderation/allow.txt</code> 再点「重载词表」。
    </p>

    <el-skeleton
      v-if="!ready"
      :rows="5"
      animated
    />
    <template v-else>
      <el-table
        :data="hits"
        size="small"
        empty-text="暂无命中记录"
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

      <div class="mt-3 flex items-center gap-3">
        <el-button
          v-if="report?.hasMore"
          size="small"
          :loading="loadingMore"
          @click="loadMore"
        >
          加载更多
        </el-button>
        <span
          v-else-if="hits.length > 0"
          class="text-xs text-faint"
        >已加载全部 {{ hits.length }} 条</span>
      </div>
    </template>
  </el-card>
</template>
