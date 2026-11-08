<script setup lang="ts">
/**
 * Index health card: state, models, collection and the last rebuild.
 *
 * <p>Presentational only — the page owns the request, the error banner and the rebuild action, so
 * this card can also be dropped into a future overview page that shows several services side by
 * side.</p>
 */
import { computed } from 'vue'
import type { AiStatus } from '../../../api/ai'

const props = defineProps<{
  status: AiStatus | null
  loading: boolean
}>()

const indexLabel = computed(() => {
  if (!props.status) return '未知'
  if (props.status.indexing) return '索引中'
  return props.status.indexed ? '已索引' : '未索引'
})

const indexTagType = computed<'success' | 'warning' | 'danger' | 'info'>(() => {
  if (!props.status) return 'info'
  if (props.status.error) return 'danger'
  if (props.status.indexing || !props.status.indexed) return 'warning'
  return 'success'
})

function formatDate(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString()
}
</script>

<template>
  <el-card
    shadow="never"
    class="admin-card"
  >
    <template #header>
      <div class="admin-card-header">
        <div class="flex items-baseline gap-2">
          <span class="admin-card-title">索引概览</span>
          <span class="admin-card-meta">
            {{ status?.chunks ?? 0 }} 块 · {{ status?.files ?? 0 }} 文件
          </span>
        </div>
      </div>
    </template>

    <el-skeleton
      v-if="loading && !status"
      :rows="6"
      animated
      class="py-2"
    />
    <el-descriptions
      v-else
      :column="3"
      border
      size="small"
    >
      <el-descriptions-item label="索引状态">
        <el-tag
          :type="indexTagType"
          size="small"
          effect="plain"
        >
          {{ indexLabel }}
        </el-tag>
        <p class="mt-1 text-xs text-faint">
          最近重建：{{ formatDate(status?.indexedAt ?? null) }}
        </p>
      </el-descriptions-item>

      <el-descriptions-item label="Chat / Embedding">
        <p
          class="truncate text-[13px]"
          :title="status?.chatModel"
        >
          {{ status?.chatModel || '—' }}
        </p>
        <p
          class="truncate text-xs text-faint"
          :title="status?.embeddingModel"
        >
          {{ status?.embeddingModel || '—' }}
        </p>
      </el-descriptions-item>

      <el-descriptions-item label="Qdrant 集合">
        <p
          class="truncate text-[13px]"
          :title="status?.collection"
        >
          {{ status?.collection || '—' }}
        </p>
        <p
          v-if="status?.error"
          class="mt-1 text-xs text-danger"
        >
          {{ status.error }}
        </p>
      </el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>
