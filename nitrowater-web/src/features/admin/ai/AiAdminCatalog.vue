<script setup lang="ts">
/**
 * Index status, skills and tools, rendered with the same card shell as the other admin pages.
 */
import { computed } from 'vue'
import type { AiSkill, AiStatus, AiTool } from '../../../api/ai'

const props = defineProps<{
  /** Which block to render: the index health card or the tool/skill catalogue. */
  section: 'index' | 'capabilities'
  status: AiStatus | null
  skills: AiSkill[]
  tools: AiTool[]
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

const enabledTools = computed(() => props.tools.filter((tool) => tool.enabled).length)

function formatDate(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString()
}
</script>

<template>
  <el-card
    v-if="section === 'index'"
    shadow="never"
    class="admin-card"
  >
    <template #header>
      <div class="admin-toolbar">
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

  <div
    v-else
    class="grid gap-4 lg:grid-cols-2"
  >
    <el-card
      shadow="never"
      class="admin-card"
    >
      <template #header>
        <div class="admin-toolbar">
          <div class="flex items-baseline gap-2">
            <span class="admin-card-title">Skills</span>
            <span class="admin-card-meta">{{ skills.length }} 个</span>
          </div>
        </div>
      </template>
      <el-table
        v-loading="loading"
        :data="skills"
        size="small"
        max-height="288"
        stripe
      >
        <template #empty>
          <el-empty
            description="暂无技能"
            :image-size="60"
          />
        </template>
        <el-table-column
          label="名称"
          min-width="130"
        >
          <template #default="{ row }">
            <span class="font-mono">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="来源"
          width="88"
          prop="source"
        />
        <el-table-column
          label="适用场景"
          min-width="180"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.whenToUse || row.description || '—' }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card
      shadow="never"
      class="admin-card"
    >
      <template #header>
        <div class="admin-toolbar">
          <div class="flex items-baseline gap-2">
            <span class="admin-card-title">Agent 工具</span>
            <span class="admin-card-meta">{{ enabledTools }} / {{ tools.length }} 启用</span>
          </div>
        </div>
      </template>
      <el-table
        v-loading="loading"
        :data="tools"
        size="small"
        max-height="288"
        stripe
      >
        <template #empty>
          <el-empty
            description="暂无工具"
            :image-size="60"
          />
        </template>
        <el-table-column
          label="名称"
          min-width="130"
        >
          <template #default="{ row }">
            <span class="font-mono">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="来源"
          width="80"
          prop="source"
        />
        <el-table-column
          label="状态"
          width="80"
        >
          <template #default="{ row }">
            <el-tag
              size="small"
              effect="plain"
              :type="row.enabled ? 'success' : 'info'"
            >
              {{ row.enabled ? '启用' : '关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="描述"
          min-width="180"
          show-overflow-tooltip
          prop="description"
        />
      </el-table>
    </el-card>
  </div>
</template>
