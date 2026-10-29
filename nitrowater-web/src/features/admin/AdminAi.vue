<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { toast } from '../../shared/ui/toast'
import {
  fetchAdminSkills,
  fetchAdminTools,
  fetchAiStatus,
  reindexDocs,
  type AiSkill,
  type AiStatus,
  type AiTool,
} from '../ai/api'
import AiAdminCatalog from './ai/AiAdminCatalog.vue'
import AiAdminSettings from './ai/AiAdminSettings.vue'
import AiAdminUsage from './ai/AiAdminUsage.vue'

const status = ref<AiStatus | null>(null)
const skills = ref<AiSkill[]>([])
const tools = ref<AiTool[]>([])
const loading = ref(false)
const reindexing = ref(false)
const error = ref('')

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [statusResult, skillsResult, toolsResult] = await Promise.all([
      fetchAiStatus(),
      fetchAdminSkills(),
      fetchAdminTools(),
    ])
    status.value = statusResult
    skills.value = skillsResult
    tools.value = toolsResult
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    loading.value = false
  }
}

async function reindex(): Promise<void> {
  if (reindexing.value) return
  reindexing.value = true
  error.value = ''
  try {
    const result = await reindexDocs()
    toast.success(`索引已重建：${result.files} 个文件 / ${result.chunks} 块，用时 ${result.durationMs} ms`)
    await load()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
    toast.error('重建索引失败')
  } finally {
    reindexing.value = false
  }
}
</script>

<template>
  <div class="mx-auto w-full max-w-[1120px] p-4 sm:p-6">
    <div class="section-head">
      <div>
        <h1 class="text-lg font-semibold text-ink">
          AI 助手
        </h1>
        <p class="mt-1 text-[13px] text-dim">
          每日 token 配额、功能开关、用户用量与文档索引。
        </p>
      </div>
      <button
        class="btn btn-primary"
        type="button"
        :disabled="reindexing || loading"
        @click="reindex"
      >
        {{ reindexing ? '重建中…' : '重建索引' }}
      </button>
    </div>

    <p
      v-if="error"
      role="alert"
      class="mb-4 border-l-2 border-danger px-3 py-2 text-sm text-danger"
    >
      {{ error }}
    </p>

    <AiAdminSettings />
    <AiAdminUsage class="mt-5" />
    <AiAdminCatalog
      class="mt-5"
      :status="status"
      :skills="skills"
      :tools="tools"
      :loading="loading"
    />
  </div>
</template>
