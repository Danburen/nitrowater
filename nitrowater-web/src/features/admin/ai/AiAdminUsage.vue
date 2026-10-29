<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { toast } from '../../../shared/ui/toast'
import { fetchAdminAiUsage, resetAdminAiUsage, type AiUserUsage } from '../../ai/api'

const rows = ref<AiUserUsage[]>([])
const selected = ref<number[]>([])
const loading = ref(false)
const resetting = ref(false)
const error = ref('')
const hasSelection = computed(() => selected.value.length > 0)
const usageDate = computed(() => rows.value[0]?.usage.date ?? '—')

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    rows.value = await fetchAdminAiUsage()
    const visibleUids = new Set(rows.value.map((row) => row.uid))
    selected.value = selected.value.filter((uid) => visibleUids.has(uid))
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    loading.value = false
  }
}

function usagePercent(row: AiUserUsage): number {
  const limit = row.usage.dailyTokenLimit
  if (limit <= 0) return 0
  return Math.min(100, Math.round((row.usage.totalTokens / limit) * 100))
}

function displayName(row: AiUserUsage): string {
  return row.nickname || row.username || `UID ${row.uid}`
}

function formatTokens(value: number): string {
  return value.toLocaleString()
}

async function reset(uids: readonly number[], label: string): Promise<void> {
  if (!window.confirm(`重置${label}今日 token 用量？`)) return
  resetting.value = true
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
  }
}
</script>

<template>
  <section
    class="panel"
    aria-labelledby="ai-usage-title"
  >
    <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
      <div class="flex items-baseline gap-2">
        <h2
          id="ai-usage-title"
          class="text-sm font-semibold text-ink"
        >
          用户今日用量
        </h2>
        <span class="text-xs text-faint">UTC · {{ usageDate }}</span>
      </div>
      <div class="flex flex-wrap gap-2">
        <button
          class="btn"
          type="button"
          :disabled="loading || resetting"
          @click="load"
        >
          刷新
        </button>
        <button
          class="btn"
          type="button"
          :disabled="!hasSelection || resetting"
          @click="reset(selected, '所选用户')"
        >
          重置所选（{{ selected.length }}）
        </button>
        <button
          class="btn"
          type="button"
          :disabled="rows.length === 0 || resetting"
          @click="reset([], '所有用户')"
        >
          重置全部
        </button>
      </div>
    </div>

    <p
      v-if="error"
      role="alert"
      class="mb-3 text-sm text-danger"
    >
      {{ error }}
    </p>
    <p
      v-if="loading"
      class="py-4 text-sm text-dim"
    >
      正在读取用量…
    </p>
    <p
      v-else-if="rows.length === 0"
      class="py-4 text-sm text-dim"
    >
      今日暂无 AI 用量记录。
    </p>
    <div
      v-else
      class="overflow-x-auto"
    >
      <table class="w-full min-w-[820px] border-collapse text-left text-[13px]">
        <thead>
          <tr class="border-b border-line text-xs text-faint">
            <th class="w-9 py-2 pr-2 font-medium">
              <span class="sr-only">选择</span>
            </th>
            <th class="py-2 pr-3 font-medium">
              用户
            </th>
            <th class="min-w-48 py-2 pr-3 font-medium">
              总用量 / 限额
            </th>
            <th class="py-2 pr-3 font-medium">
              输入
            </th>
            <th class="py-2 pr-3 font-medium">
              输出
            </th>
            <th class="py-2 pr-3 font-medium">
              请求
            </th>
            <th class="py-2 font-medium">
              估算
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in rows"
            :key="row.uid"
            class="border-b border-line/60 last:border-0"
          >
            <td class="py-2 pr-2">
              <input
                v-model="selected"
                class="size-4 accent-accent"
                type="checkbox"
                :value="row.uid"
                :aria-label="`选择用户 ${displayName(row)}`"
              >
            </td>
            <td class="py-2 pr-3">
              <p class="text-ink">
                {{ displayName(row) }}
              </p>
              <p class="font-mono text-[11px] text-faint">
                UID {{ row.uid }}
              </p>
            </td>
            <td class="py-2 pr-3">
              <div class="mb-1 flex justify-between gap-3 font-mono text-xs text-ink">
                <span>{{ formatTokens(row.usage.totalTokens) }}</span>
                <span class="text-faint">{{ formatTokens(row.usage.dailyTokenLimit) }}</span>
              </div>
              <div
                class="h-1.5 overflow-hidden bg-soft"
                role="progressbar"
                :aria-valuenow="usagePercent(row)"
                aria-valuemin="0"
                aria-valuemax="100"
                :aria-label="`${displayName(row)} 的每日 token 使用率`"
              >
                <div
                  class="h-full bg-accent"
                  :style="{ width: `${usagePercent(row)}%` }"
                />
              </div>
            </td>
            <td class="py-2 pr-3 font-mono text-dim">
              {{ formatTokens(row.usage.inputTokens) }}
            </td>
            <td class="py-2 pr-3 font-mono text-dim">
              {{ formatTokens(row.usage.outputTokens) }}
            </td>
            <td class="py-2 pr-3 font-mono text-dim">
              {{ row.usage.requestCount }}
            </td>
            <td class="py-2 font-mono text-dim">
              {{ row.usage.estimatedRequests }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
