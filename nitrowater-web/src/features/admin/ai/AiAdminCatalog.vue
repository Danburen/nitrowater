<script setup lang="ts">
import { computed } from 'vue'
import type { AiSkill, AiStatus, AiTool } from '../../ai/api'

const props = defineProps<{
  status: AiStatus | null
  skills: readonly AiSkill[]
  tools: readonly AiTool[]
  loading: boolean
}>()

const indexLabel = computed(() => {
  if (!props.status) return '未知'
  if (props.status.indexing) return '索引中'
  return props.status.indexed ? '已索引' : '未索引'
})

const indexTone = computed(() => {
  if (!props.status) return 'text-faint'
  if (props.status.error) return 'text-danger'
  if (props.status.indexing || !props.status.indexed) return 'text-warn'
  return 'text-ok'
})

const enabledTools = computed(() => props.tools.filter((tool) => tool.enabled).length)

function formatDate(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString()
}
</script>

<template>
  <section class="panel">
    <h2 class="mb-4 text-sm font-semibold text-ink">
      索引与目录
    </h2>
    <div class="grid gap-4 border-b border-line pb-4 sm:grid-cols-3">
      <div>
        <p class="text-xs text-faint">
          索引状态
        </p>
        <p
          class="mt-1 text-sm font-medium"
          :class="indexTone"
        >
          {{ indexLabel }}
          <span
            v-if="status"
            class="ml-2 text-xs font-normal text-dim"
          >
            {{ status.chunks }} 块 / {{ status.files }} 文件
          </span>
        </p>
        <p class="mt-1 text-xs text-faint">
          最近重建：{{ formatDate(status?.indexedAt ?? null) }}
        </p>
      </div>
      <div>
        <p class="text-xs text-faint">
          Chat / Embedding
        </p>
        <p
          class="mt-1 truncate text-[13px] text-ink"
          :title="status?.chatModel"
        >
          {{ status?.chatModel || '—' }}
        </p>
        <p
          class="truncate text-xs text-dim"
          :title="status?.embeddingModel"
        >
          {{ status?.embeddingModel || '—' }}
        </p>
      </div>
      <div>
        <p class="text-xs text-faint">
          Qdrant 集合
        </p>
        <p
          class="mt-1 truncate text-[13px] text-ink"
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
      </div>
    </div>

    <div class="grid gap-5 pt-4 lg:grid-cols-2">
      <div class="min-w-0">
        <div class="mb-2 flex items-center justify-between gap-2">
          <h3 class="text-[13px] font-medium text-ink">
            Skills
          </h3>
          <span class="tag">{{ skills.length }}</span>
        </div>
        <p
          v-if="loading"
          class="py-3 text-sm text-dim"
        >
          加载中…
        </p>
        <p
          v-else-if="skills.length === 0"
          class="py-3 text-sm text-dim"
        >
          暂无技能。
        </p>
        <div
          v-else
          class="max-h-72 overflow-auto"
        >
          <table class="w-full border-collapse text-left text-xs">
            <thead>
              <tr class="border-b border-line text-faint">
                <th class="py-2 pr-2 font-medium">
                  名称
                </th>
                <th class="py-2 pr-2 font-medium">
                  来源
                </th>
                <th class="py-2 font-medium">
                  适用场景
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="skill in skills"
                :key="skill.name"
                class="border-b border-line/60 last:border-0"
              >
                <td class="py-2 pr-2 font-mono text-ink">
                  {{ skill.name }}
                </td>
                <td class="py-2 pr-2 text-dim">
                  {{ skill.source }}
                </td>
                <td class="py-2 text-dim">
                  {{ skill.whenToUse || skill.description || '—' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="min-w-0">
        <div class="mb-2 flex items-center justify-between gap-2">
          <h3 class="text-[13px] font-medium text-ink">
            Agent 工具
          </h3>
          <span class="tag">{{ enabledTools }} / {{ tools.length }}</span>
        </div>
        <p
          v-if="loading"
          class="py-3 text-sm text-dim"
        >
          加载中…
        </p>
        <p
          v-else-if="tools.length === 0"
          class="py-3 text-sm text-dim"
        >
          暂无工具。
        </p>
        <div
          v-else
          class="max-h-72 overflow-auto"
        >
          <table class="w-full border-collapse text-left text-xs">
            <thead>
              <tr class="border-b border-line text-faint">
                <th class="py-2 pr-2 font-medium">
                  名称
                </th>
                <th class="py-2 pr-2 font-medium">
                  来源
                </th>
                <th class="py-2 pr-2 font-medium">
                  状态
                </th>
                <th class="py-2 font-medium">
                  描述
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="tool in tools"
                :key="tool.name"
                class="border-b border-line/60 last:border-0"
              >
                <td class="py-2 pr-2 font-mono text-ink">
                  {{ tool.name }}
                </td>
                <td class="py-2 pr-2 text-dim">
                  {{ tool.source }}
                </td>
                <td
                  class="py-2 pr-2"
                  :class="tool.enabled ? 'text-ok' : 'text-faint'"
                >
                  {{ tool.enabled ? '启用' : '关闭' }}
                </td>
                <td class="py-2 text-dim">
                  {{ tool.description }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </section>
</template>
