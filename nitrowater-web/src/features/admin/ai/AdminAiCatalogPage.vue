<script setup lang="ts">
/**
 * Index status, skills and tools. Data loading lives here (not in the layout) so the settings and
 * usage pages do not fetch catalogs they never render.
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
// Explicit style import: the on-demand resolver does not inject styles for explicit API imports.
import 'element-plus/es/components/message/style/css'
import AdminPage from '../AdminPage.vue'
import {
  fetchAdminSkills,
  fetchAdminTools,
  fetchAiStatus,
  reindexDocs,
  type AiSkill,
  type AiStatus,
  type AiTool,
} from '../../../api/ai'
import AiAdminCatalog from './AiAdminCatalog.vue'

/** Which half of the catalogue this route renders (the index is its own page now). */
const props = defineProps<{ section: 'index' | 'capabilities' }>()

const isIndex = computed(() => props.section === 'index')

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
    ElMessage.success(`索引已重建：${result.files} 个文件 / ${result.chunks} 块，耗时 ${result.durationMs} ms`)
    await load()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
    ElMessage.error('重建索引失败')
  } finally {
    reindexing.value = false
  }
}
</script>

<template>
  <AdminPage
    :title="isIndex ? '索引状态' : '能力'"
    :description="isIndex
      ? '向量索引的健康状态、模型与集合信息；重建索引会整库重算。'
      : '已挂载的技能与 Agent 工具，以及各自的启用来源。'"
  >
    <template #actions>
      <el-button
        :loading="reindexing"
        :disabled="loading"
        @click="load"
      >
        刷新
      </el-button>
      <el-button
        v-if="isIndex"
        type="primary"
        :loading="reindexing"
        :disabled="loading"
        @click="reindex"
      >
        重建索引
      </el-button>
    </template>

    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
      class="mb-3"
    />

    <AiAdminCatalog
      :section="section"
      :status="status"
      :skills="skills"
      :tools="tools"
      :loading="loading"
    />
  </AdminPage>
</template>
