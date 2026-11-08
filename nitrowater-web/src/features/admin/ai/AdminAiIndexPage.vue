<script setup lang="ts">
/**
 * Documentation index status page.
 *
 * <p>Owns everything that is specific to this page: the health request, the error banner and the
 * rebuild action. Nothing here knows about skills or tools, which is what lets the two catalogue
 * routes load independently (the old shared component fetched all three resources for both).</p>
 */
import { onMounted, ref } from 'vue'
import { confirm } from '../../../shared/ui/confirm'
import { toast } from '../../../shared/ui/toast'
import { fetchAiStatus, reindexDocs, type AiStatus } from '../../../api/ai'
import AdminDangerRow from '../AdminDangerRow.vue'
import AdminDangerZone from '../AdminDangerZone.vue'
import AdminPage from '../components/AdminPage.vue'
import AiAdminIndexOverview from '../components/AiAdminIndexOverview.vue'

const status = ref<AiStatus | null>(null)
const loading = ref(false)
const reindexing = ref(false)
const error = ref('')

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    status.value = await fetchAiStatus()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    loading.value = false
  }
}

/** The rebuild wipes and re-embeds the whole collection, so it asks first. */
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
    title="索引状态"
    description="向量索引的健康状态、模型与集合信息；重建索引会整库重算。"
  >
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    />

    <AiAdminIndexOverview
      :status="status"
      :loading="loading"
    />

    <AdminDangerZone>
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
