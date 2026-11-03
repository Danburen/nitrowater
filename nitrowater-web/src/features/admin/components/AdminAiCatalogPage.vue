<script setup lang="ts">
/**
 * Index status, skills and tools. Data loading lives here (not in the layout) so the settings and
 * usage pages do not fetch catalogs they never render.
 */
import { computed, onMounted, ref } from 'vue'
import { confirm } from '../../../shared/ui/confirm.ts'
import { toast } from '../../../shared/ui/toast.ts'
import AdminDangerRow from '../AdminDangerRow.vue'
import AdminDangerZone from '../AdminDangerZone.vue'
import AdminPage from './AdminPage.vue'
import {
  fetchAdminSkills,
  fetchAdminTools,
  fetchAiStatus,
  reindexDocs,
  type AiSkill,
  type AiStatus,
  type AiTool,
} from '../../../api/ai'
import AiAdminCatalog from '../ai/AiAdminCatalog.vue'

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

/** Danger zone: the rebuild wipes and re-embeds the whole collection, so it asks first. */
async function reindex(): Promise<void> {
  if (reindexing.value) return
  if (!(await confirm({
    title: '重建索引',
    message: '将清空当前向量集合，按 docs 目录重新切片并重新嵌入全部文档。重建期间检索结果不可靠，且无法回滚，继续？',
    confirmText: '重建',
    danger: true,
  }))) return
  reindexing.value = true
  error.value = ''
  try {
    const result = await reindexDocs()
    toast.success(`索引已重建：${result.files} 个文件 / ${result.chunks} 块，耗时 ${result.durationMs} ms`)
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
  <AdminPage
    :title="isIndex ? '索引状态' : '能力'"
    :description="isIndex
      ? '向量索引的健康状态、模型与集合信息；重建索引会整库重算。'
      : '已挂载的技能与 Agent 工具，以及各自的启用来源。'"
  >
    <template #actions>
      <el-button
        :disabled="loading || reindexing"
        @click="load"
      >
        刷新
      </el-button>
    </template>

    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    />

    <AiAdminCatalog
      :section="section"
      :status="status"
      :skills="skills"
      :tools="tools"
      :loading="loading"
    />

    <AdminDangerZone v-if="isIndex">
      <AdminDangerRow label="重建向量索引">
        <el-button
          type="danger"
          plain
          :loading="reindexing"
          :disabled="loading"
          @click="reindex"
        >
          重建索引
        </el-button>
      </AdminDangerRow>
    </AdminDangerZone>
  </AdminPage>
</template>
