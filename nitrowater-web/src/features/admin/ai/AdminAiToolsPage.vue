<script setup lang="ts">
/**
 * Admin: the tools advertised to the agent.
 *
 * <p>Tools are registered in code, so this page is deliberately read-mostly: it shows the catalog
 * and the input schema (what the model is actually told) and allows hiding one for subsequent
 * turns. Nothing here can add or edit a tool.</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import AdminPage from '../components/AdminPage.vue'
import AiAdminTextCell from '../components/AiAdminTextCell.vue'
import { toast } from '../../../shared/ui/toast'
import { fetchAdminTools, setAdminToolEnabled, type AiTool } from '../../../api/ai'

/**
 * Display names of the catalog buckets. A bucket the backend adds later still renders (the raw
 * value is the fallback), so a server-side feature never requires a frontend release to be useful.
 */
const SOURCE_LABELS: Readonly<Record<string, string>> = {
  core: '内置',
  docs: '文档',
  skills: '技能',
  code: '代码',
  web: '联网',
}

const tools = ref<AiTool[]>([])
const loading = ref(false)
const error = ref('')
const drawer = reactive({ visible: false, tool: null as AiTool | null })

const enabledCount = computed(() => tools.value.filter((tool) => tool.enabled).length)

/** @param tool catalog row @return the bucket label, or the raw bucket when it is not mapped yet */
function sourceLabel(tool: AiTool): string {
  return SOURCE_LABELS[tool.source] ?? tool.source
}

/** Drawer subtitle: bucket, switch state, and the gates that explain why a tool may not run. */
const detailLine = computed(() => {
  const tool = drawer.tool
  if (!tool) return ''
  const state = tool.enabled ? '启用' : '停用'
  const consent = tool.optIn ? ' · 需用户同意' : ''
  const usable = tool.available ? '' : ' · 运行时不可用'
  return `来源：${sourceLabel(tool)} · 状态：${state}${consent}${usable}`
})

/** Pretty-printed schema; the wire value is a JSON string produced by Spring AI. */
const schema = computed(() => {
  const raw = drawer.tool?.inputSchema ?? ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
})

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    tools.value = await fetchAdminTools()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    loading.value = false
  }
}

async function toggle(tool: AiTool, enabled: boolean): Promise<void> {
  try {
    tools.value = await setAdminToolEnabled(tool.name, enabled)
    toast.success(`${tool.name} 已${enabled ? '启用' : '停用'}`)
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '切换失败')
    await load()
  }
}

function openDetail(tool: AiTool): void {
  drawer.tool = tool
  drawer.visible = true
}

async function copySchema(): Promise<void> {
  try {
    await navigator.clipboard.writeText(schema.value)
    toast.success('已复制')
  } catch {
    toast.error('复制失败，请手动选择')
  }
}
</script>

<template>
  <AdminPage
    title="工具"
    description="工具由代码注册，只能在后续对话中启用或停用；此处展示模型实际看到的输入结构。"
  >
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    />

    <el-card
      shadow="never"
      class="admin-card admin-card-flush"
    >
      <template #header>
        <div class="admin-card-header">
          <div class="flex items-baseline gap-2">
            <span class="admin-card-title">工具列表</span>
            <span class="admin-card-meta">{{ enabledCount }} / {{ tools.length }} 启用</span>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="tools"
        size="small"
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
          min-width="150"
        >
          <template #default="{ row }">
            <span class="font-mono">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="来源"
          width="90"
        >
          <template #default="{ row }">
            <span>{{ sourceLabel(row as AiTool) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="描述"
          min-width="200"
        >
          <template #default="{ row }">
            <AiAdminTextCell :text="row.description" />
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="150"
        >
          <template #default="{ row }">
            <div class="flex items-center gap-2">
              <el-switch
                :model-value="row.enabled"
                size="small"
                :disabled="loading"
                @change="(value: unknown) => toggle(row as AiTool, value === true)"
              />
              <el-tag
                v-if="!(row as AiTool).available"
                size="small"
                type="warning"
              >
                不可用
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="90"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              size="small"
              @click="openDetail(row as AiTool)"
            >
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer
      v-model="drawer.visible"
      title="工具详情"
      size="560px"
    >
      <template v-if="drawer.tool">
        <div class="space-y-4">
          <div class="flex items-center justify-between gap-3">
            <div class="min-w-0">
              <p class="truncate font-mono text-sm text-ink">
                {{ drawer.tool.name }}
              </p>
              <p class="mt-1 text-[12.5px] text-dim">
                {{ detailLine }}
              </p>
            </div>
            <el-switch
              :model-value="drawer.tool.enabled"
              :disabled="loading"
              @change="(value: unknown) => toggle(drawer.tool!, value === true)"
            />
          </div>
          <p class="whitespace-pre-wrap break-words text-[13px] leading-relaxed text-dim">
            {{ drawer.tool.description }}
          </p>
          <div>
            <div class="mb-1.5 flex items-center justify-between">
              <span class="text-[13px] text-dim">输入结构（JSON Schema）</span>
              <el-button
                link
                size="small"
                @click="copySchema"
              >
                复制
              </el-button>
            </div>
            <pre class="max-h-[55vh] overflow-auto rounded-md border border-line bg-soft p-3 font-mono text-[12px] leading-relaxed text-ink">{{ schema }}</pre>
          </div>
          <p class="text-[12px] text-faint">
            工具由后端代码注册，无法在控制台新增或修改；这里的开关只影响模型在后续对话中是否看到它。
          </p>
        </div>
      </template>
    </el-drawer>
  </AdminPage>
</template>
