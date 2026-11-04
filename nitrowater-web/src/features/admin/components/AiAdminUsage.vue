<script setup lang="ts">
/**
 * Per-user UTC-day usage with selection and reset.
 *
 * <p>Element Plus table: the selection column replaces the hand-rolled checkboxes; resets go
 * through the shared {@link confirm} instead of {@code window.confirm}, so the dialog matches the
 * rest of the business pages.</p>
 *
 * <p>Reset sits on the object it acts on: one user is reset from their row, a selection from the
 * list header. Only the platform-wide breaker lives in the danger zone — a page-level block is for
 * standalone destructive actions (resets that do not target a row), not for per-row maintenance.</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { confirm } from '../../../shared/ui/confirm'
import { toast } from '../../../shared/ui/toast'
import {
  fetchAdminAiUsage,
  fetchAdminAiUsageSummary,
  resetAdminAiUsage,
  resetAdminGlobalUsage,
  type AiUsageSort,
  type AiUsageSummary,
  type AiUserUsage,
} from '../../../api/ai'
import AdminDangerRow from '../AdminDangerRow.vue'
import AdminDangerZone from '../AdminDangerZone.vue'

const rows = ref<AiUserUsage[]>([])
const summary = ref<AiUsageSummary | null>(null)
const selected = ref<number[]>([])
const loading = ref(false)
const resetting = ref(false)
/** Row whose own reset is in flight, so only that button spins. */
const resettingUid = ref<number | null>(null)
const globalResetting = ref(false)
const error = ref('')
/** False until the first load settles; prevents an empty table / empty state from flashing. */
const ready = ref(false)
const hasSelection = computed(() => selected.value.length > 0)
const usageDate = computed(() => rows.value[0]?.usage.date ?? summary.value?.today ?? '—')
const total = ref(0)
const totalPages = ref(1)

/** Server-side filters for the per-user list. */
const query = reactive({
  keyword: '',
  onlyExceeded: false,
  sort: 'tokens' as AiUsageSort,
  page: 0,
  size: 20,
})
const filtering = computed(() => query.keyword.trim() !== '' || query.onlyExceeded)

const SORT_OPTIONS: Array<{ value: AiUsageSort; label: string }> = [
  { value: 'tokens', label: '用量从高到低' },
  { value: 'tokens_asc', label: '用量从低到高' },
  { value: 'requests', label: '请求数从多到少' },
  { value: 'uid', label: 'UID 升序' },
]

/** Today's platform-wide share of the configured ceiling, clamped to 0–100. */
const globalPercent = computed(() => {
  const snapshot = summary.value
  if (!snapshot || snapshot.globalDailyTokenLimit <= 0) return 0
  return Math.min(100, Math.round((snapshot.todayTokens / snapshot.globalDailyTokenLimit) * 100))
})

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [users, global] = await Promise.all([
      fetchAdminAiUsage({
        keyword: query.keyword || undefined,
        onlyExceeded: query.onlyExceeded,
        sort: query.sort,
        page: query.page,
        size: query.size,
      }),
      fetchAdminAiUsageSummary(31),
    ])
    rows.value = users.content
    summary.value = global
    total.value = users.page.totalElements
    totalPages.value = Math.max(1, users.page.totalPages)
    const visible = new Set(rows.value.map((row) => row.uid))
    selected.value = selected.value.filter((uid) => visible.has(uid))
    // A filter change or a reset can shrink the set below the current page.
    if (users.content.length === 0 && users.page.number > 0
      && users.page.number >= users.page.totalPages) {
      query.page = Math.max(users.page.totalPages - 1, 0)
      return load()
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    loading.value = false
    ready.value = true
  }
}

/** Re-queries with the current filters; selections are pruned to the visible rows. */
function search(): void {
  query.page = 0
  void load()
}

/** Restores the default filters (all users, usage descending) and reloads. */
function resetFilters(): void {
  query.keyword = ''
  query.onlyExceeded = false
  query.sort = 'tokens'
  query.page = 0
  query.size = 20
  void load()
}

/** `el-pagination` reports a 1-based page; the wire contract is 0-based. */
function pageTo(oneBased: number): void {
  query.page = Math.max(0, oneBased - 1)
  void load()
}

function onSizeChange(size: number): void {
  query.size = size
  query.page = 0
  void load()
}

/** Clears today's platform-wide counter, which also lifts the breaker for the rest of the day. */
async function resetGlobal(): Promise<void> {
  if (!(await confirm({
    title: '确认解除',
    message: '将清空今日全站用量并立即解除熔断，继续？',
    confirmText: '解除',
    danger: true,
  }))) return
  globalResetting.value = true
  error.value = ''
  try {
    await resetAdminGlobalUsage()
    toast.success('已解除今日全站熔断')
    await load()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
    toast.error('解除失败')
  } finally {
    globalResetting.value = false
  }
}

/** Table slots hand back an untyped row; every helper narrows it once. */
function asUsage(row: unknown): AiUserUsage {
  return row as AiUserUsage
}

function usagePercent(row: unknown): number {
  const usage = asUsage(row).usage
  if (usage.dailyTokenLimit <= 0) return 0
  return Math.min(100, Math.round((usage.totalTokens / usage.dailyTokenLimit) * 100))
}

function displayName(row: unknown): string {
  const user = asUsage(row)
  return user.nickname || user.username || `UID ${user.uid}`
}

function formatTokens(value: number): string {
  return value.toLocaleString('zh-CN')
}

function onSelectionChange(picked: AiUserUsage[]): void {
  selected.value = picked.map((row) => row.uid)
}

/**
 * Clears today's tokens for the given users.
 *
 * @param uids  users to reset
 * @param label user-facing target description for the confirm dialog
 * @param uid   single-user mode: the row that owns the spinner, {@code null} for a bulk reset
 */
async function reset(uids: readonly number[], label: string, uid: number | null = null): Promise<void> {
  if (resetting.value || resettingUid.value !== null) return
  if (!(await confirm({
    title: '确认重置',
    message: `重置${label}今日 token 用量？`,
    confirmText: '重置',
    danger: true,
  }))) return
  if (uid === null) {
    resetting.value = true
  } else {
    resettingUid.value = uid
  }
  error.value = ''
  try {
    const count = await resetAdminAiUsage(uids)
    selected.value = []
    toast.success(`已重置 ${count} 位用户的今日用量`)
    await load()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
    toast.error('重置用量失败')
  } finally {
    resetting.value = false
    resettingUid.value = null
  }
}
</script>

<template>
  <div class="space-y-4">
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    />

    <el-card
      shadow="never"
      class="admin-card"
    >
      <template #header>
        <div class="admin-toolbar">
          <div class="flex items-baseline gap-2">
            <span class="admin-card-title">全站用量</span>
            <span class="admin-card-meta">UTC {{ summary?.today ?? '—' }} · 近 31 天</span>
          </div>
          <div class="admin-actions">
            <el-tag
              v-if="summary && !summary.globalEnabled"
              type="info"
              effect="plain"
            >
              未启用熔断
            </el-tag>
            <el-tag
              v-else-if="summary?.tripped"
              type="danger"
              effect="plain"
            >
              已熔断
            </el-tag>
            <el-tag
              v-else
              type="success"
              effect="plain"
            >
              正常
            </el-tag>
          </div>
        </div>
      </template>

      <div class="mb-4 grid gap-3 sm:grid-cols-3">
        <div class="rounded-[12px] border border-line bg-card p-3">
          <div class="text-[12px] text-faint">今日用量</div>
          <div class="mt-1 font-mono text-[17px] leading-tight text-ink">{{ formatTokens(summary?.todayTokens ?? 0) }}</div>
        </div>
        <div class="rounded-[12px] border border-line bg-card p-3">
          <div class="text-[12px] text-faint">每日上限</div>
          <div class="mt-1 font-mono text-[17px] leading-tight text-ink">
            {{ summary && summary.globalDailyTokenLimit > 0 ? formatTokens(summary.globalDailyTokenLimit) : '不限制' }}
          </div>
        </div>
        <div class="rounded-[12px] border border-line bg-card p-3">
          <div class="text-[12px] text-faint">今日请求数 · 占用</div>
          <div class="mt-1 font-mono text-[17px] leading-tight text-ink">
            {{ formatTokens(summary?.todayRequests ?? 0) }}
            <span class="ml-2 text-xs text-faint">{{ globalPercent }}%</span>
          </div>
        </div>
      </div>

      <el-table
        :data="summary?.days ?? []"
        size="small"
        max-height="240"
        stripe
        empty-text="暂无用量记录"
      >
        <el-table-column
          prop="date"
          label="UTC 日期"
          width="120"
        />
        <el-table-column
          prop="inputTokens"
          label="输入 tokens"
          align="right"
        >
          <template #default="{ row }">{{ formatTokens(row.inputTokens) }}</template>
        </el-table-column>
        <el-table-column
          prop="outputTokens"
          label="输出 tokens"
          align="right"
        >
          <template #default="{ row }">{{ formatTokens(row.outputTokens) }}</template>
        </el-table-column>
        <el-table-column
          prop="totalTokens"
          label="合计"
          align="right"
        >
          <template #default="{ row }">{{ formatTokens(row.totalTokens) }}</template>
        </el-table-column>
        <el-table-column
          prop="requestCount"
          label="请求数"
          align="right"
          width="100"
        />
      </el-table>
    </el-card>

    <el-card
      shadow="never"
      class="admin-card"
    >
      <template #header>
        <div class="admin-toolbar">
          <div class="flex items-baseline gap-2">
            <span class="admin-card-title">用户今日用量</span>
            <span class="admin-card-meta">UTC · {{ usageDate }} · 共 {{ total }} 位用户</span>
          </div>
          <div class="admin-actions">
            <el-button
              type="danger"
              plain
              :disabled="!hasSelection || loading || resetting || resettingUid !== null"
              @click="reset(selected, '所选用户')"
            >
              重置所选{{ hasSelection ? `（${selected.length}）` : '' }}
            </el-button>
            <el-button
              :disabled="loading || resetting"
              @click="load"
            >
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-form
        :inline="true"
        class="admin-filter-form border-b border-line px-2 py-2"
        @submit.prevent="search"
      >
        <el-form-item label="用户">
          <el-input
            v-model.trim="query.keyword"
            placeholder="用户名 / 昵称 / UID"
            clearable
            class="w-[220px]!"
          />
        </el-form-item>
        <el-form-item label="范围">
          <el-select
            v-model="query.onlyExceeded"
            class="w-[130px]!"
          >
            <el-option
              :value="false"
              label="全部用户"
            />
            <el-option
              :value="true"
              label="仅看超额"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-select
            v-model="query.sort"
            class="w-[150px]!"
          >
            <el-option
              v-for="option in SORT_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <div class="admin-filter-actions">
          <el-button
            type="primary"
            native-type="submit"
            :loading="loading"
          >
            查询
          </el-button>
          <el-button
            :disabled="loading"
            @click="resetFilters"
          >
            重置
          </el-button>
        </div>
      </el-form>

      <el-skeleton
        v-if="!ready"
        :rows="5"
        animated
        class="py-2"
      />
      <el-table
        v-else
        v-loading="loading"
        :data="rows"
        row-key="uid"
        size="small"
        stripe
        @selection-change="onSelectionChange"
      >
        <template #empty>
          <el-empty
            :description="filtering ? '没有符合条件的用户' : '今日暂无 AI 用量记录'"
            :image-size="72"
          />
        </template>
        <el-table-column
          type="selection"
          width="44"
        />
        <el-table-column
          label="用户"
          min-width="160"
        >
          <template #default="{ row }">
            <div class="text-ink">{{ displayName(row) }}</div>
            <div class="font-mono text-[11px] text-faint">UID {{ row.uid }}</div>
          </template>
        </el-table-column>
        <el-table-column
          label="用量 / 限额"
          min-width="220"
        >
          <template #default="{ row }">
            <div class="flex items-center justify-between font-mono text-xs text-ink">
              <span>{{ formatTokens(row.usage.totalTokens) }}</span>
              <span class="text-faint">{{ formatTokens(row.usage.dailyTokenLimit) }}</span>
            </div>
            <el-progress
              :percentage="usagePercent(row)"
              :show-text="false"
              :stroke-width="6"
              class="mt-1"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="输入"
          width="110"
          align="right"
        >
          <template #default="{ row }">
            <span class="font-mono">{{ formatTokens(row.usage.inputTokens) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="输出"
          width="110"
          align="right"
        >
          <template #default="{ row }">
            <span class="font-mono">{{ formatTokens(row.usage.outputTokens) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="请求"
          width="80"
          align="right"
          prop="usage.requestCount"
        />
        <el-table-column
          label="估算"
          width="80"
          align="right"
          prop="usage.estimatedRequests"
        />
        <el-table-column
          label="操作"
          width="80"
          align="right"
        >
          <template #default="{ row }">
            <el-button
              link
              type="danger"
              :disabled="resetting || resettingUid !== null"
              :loading="resettingUid === row.uid"
              @click="reset([row.uid], displayName(row), row.uid)"
            >
              重置
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex flex-wrap items-center justify-between gap-3 border-t border-line px-4 py-3 text-[12.5px] text-dim">
        <span>共 {{ total }} 条 · 第 {{ query.page + 1 }} / {{ totalPages }} 页</span>
        <el-pagination
          :current-page="query.page + 1"
          :page-size="query.size"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="sizes, prev, pager, next"
          @current-change="pageTo"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <AdminDangerZone>
      <AdminDangerRow label="解除今日熔断">
        <el-button
          type="danger"
          plain
          :disabled="globalResetting"
          :loading="globalResetting"
          @click="resetGlobal"
        >
          解除熔断
        </el-button>
      </AdminDangerRow>
    </AdminDangerZone>
  </div>
</template>
