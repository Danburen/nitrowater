<script setup lang="ts">
/**
 * Admin: the agent's skill catalog.
 *
 * <p>Built-ins ship with the code and are read-only; managed rows live in the database and are
 * created/imported/edited here. Saving is the activation step — the agent reads the table on every
 * turn — so there is no reload button and no "not loaded yet" state.</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import AdminPage from '../components/AdminPage.vue'
import AdminFilterCard from '../components/AdminFilterCard.vue'
import AiAdminTextCell from '../components/AiAdminTextCell.vue'
import { confirm } from '../../../shared/ui/confirm'
import { toast } from '../../../shared/ui/toast'
import {
  createAdminSkill,
  deleteAdminSkill,
  exportAdminSkill,
  fetchAdminSkill,
  fetchAdminSkills,
  importAdminSkill,
  updateAdminSkill,
  type AiSkill,
  type AiSkillDetail,
} from '../../../api/ai'

type DrawerMode = 'create' | 'edit' | 'view'

const rows = ref<AiSkill[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ keyword: '', page: 0, size: 20 })

const drawer = reactive({
  visible: false,
  mode: 'create' as DrawerMode,
  saving: false,
  loading: false,
  origin: null as string | null,
})
const form = reactive({ name: '', description: '', whenToUse: '', body: '' })
const fileInput = ref<HTMLInputElement | null>(null)
const importing = ref(false)
const exporting = ref<string | null>(null)

const drawerTitle = computed(() => {
  if (drawer.mode === 'create') return '新增技能'
  if (drawer.mode === 'edit') return '编辑技能'
  return '技能详情'
})
const readOnly = computed(() => drawer.mode === 'view' || drawer.loading)

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const page = await fetchAdminSkills(query)
    rows.value = page.content
    total.value = page.page.totalElements
    // A delete or filter change can empty the current page.
    if (page.content.length === 0 && page.page.number > 0) {
      query.page = Math.max(page.page.number - 1, 0)
      return load()
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    loading.value = false
  }
}

function search(): void {
  query.page = 0
  void load()
}

function onPage(page: number): void {
  query.page = Math.max(0, page - 1)
  void load()
}

function onSizeChange(size: number): void {
  query.size = size
  query.page = 0
  void load()
}

function openCreate(): void {
  drawer.mode = 'create'
  drawer.origin = null
  form.name = ''
  form.description = ''
  form.whenToUse = ''
  form.body = ''
  drawer.visible = true
}

function fillFrom(detail: AiSkillDetail, mode: DrawerMode): void {
  drawer.mode = mode
  drawer.origin = detail.origin
  form.name = detail.name
  form.description = detail.description
  form.whenToUse = detail.whenToUse
  form.body = detail.body
  drawer.visible = true
}

async function openDetail(row: AiSkill, mode: 'view' | 'edit'): Promise<void> {
  drawer.mode = mode
  drawer.visible = true
  drawer.loading = true
  try {
    fillFrom(await fetchAdminSkill(row.name), mode)
  } catch (cause) {
    drawer.visible = false
    toast.error(cause instanceof Error ? cause.message : '读取技能失败')
  } finally {
    drawer.loading = false
  }
}

async function exportSkill(name: string): Promise<void> {
  if (!name || exporting.value) return
  exporting.value = name
  try {
    const blob = await exportAdminSkill(name)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${name}.md`
    document.body.appendChild(link)
    link.click()
    link.remove()
    // Revoking immediately can cancel the download in some browsers.
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '导出失败')
  } finally {
    exporting.value = null
  }
}

async function save(): Promise<void> {
  drawer.saving = true
  try {
    const payload = {
      name: form.name.trim(),
      description: form.description.trim(),
      whenToUse: form.whenToUse.trim(),
      body: form.body,
    }
    if (drawer.mode === 'create') {
      await createAdminSkill(payload)
      toast.success('技能已创建')
    } else {
      await updateAdminSkill(form.name, payload)
      toast.success('技能已更新')
    }
    drawer.visible = false
    await load()
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '保存失败')
  } finally {
    drawer.saving = false
  }
}

async function remove(row: AiSkill): Promise<void> {
  const hint = row.overridesBuiltin
    ? `“${row.name}” 将恢复为内置版本，确定删除这份覆盖吗？`
    : `“${row.name}” 将从技能目录中移除，确定删除吗？`
  if (!(await confirm({ title: '删除技能', message: hint, confirmText: '删除', danger: true }))) return
  try {
    await deleteAdminSkill(row.name)
    toast.success('技能已删除')
    await load()
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '删除失败')
  }
}

function pickFile(): void {
  fileInput.value?.click()
}

async function onFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  importing.value = true
  try {
    const saved = await importAdminSkill(file)
    toast.success(`已导入 ${saved.name}`)
    await load()
    // Show the imported row right away so its one-line description can be reviewed or adjusted.
    fillFrom(saved, 'edit')
  } catch (cause) {
    toast.error(cause instanceof Error ? cause.message : '导入失败')
  } finally {
    importing.value = false
  }
}
</script>

<template>
  <AdminPage
    title="技能"
    description="内置技能随代码发布、只读；新增与导入的技能存库，保存即对下一轮对话生效（热加载，无需重启）。可导出为 SKILL.md 再修改或迁移。"
  >
    <template #actions>
      <el-button
        :disabled="importing"
        @click="pickFile"
      >
        {{ importing ? '导入中…' : '导入 SKILL.md' }}
      </el-button>
      <el-button
        type="primary"
        @click="openCreate"
      >
        新增技能
      </el-button>
      <input
        ref="fileInput"
        type="file"
        accept=".md,text/markdown"
        class="hidden"
        @change="onFile"
      >
    </template>

    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    />

    <AdminFilterCard
      :loading="loading"
      @submit="search"
      @reset="query.keyword = ''; search()"
    >
      <el-form-item label="关键词">
        <el-input
          v-model="query.keyword"
          placeholder="名称 / 描述 / 适用场景"
          clearable
          class="w-[240px]!"
        />
      </el-form-item>
    </AdminFilterCard>

    <el-card
      shadow="never"
      class="admin-card admin-card-flush"
    >
      <template #header>
        <div class="admin-card-header">
          <div class="flex items-baseline gap-2">
            <span class="admin-card-title">技能列表</span>
            <span class="admin-card-meta">共 {{ total }} 条</span>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="rows"
        size="small"
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
          min-width="150"
        >
          <template #default="{ row }">
            <span class="font-mono">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="来源"
          width="110"
        >
          <template #default="{ row }">
            <el-tag
              v-if="row.overridesBuiltin"
              size="small"
              type="warning"
            >
              覆盖内置
            </el-tag>
            <el-tag
              v-else-if="row.builtin"
              size="small"
              type="info"
            >
              内置
            </el-tag>
            <el-tag
              v-else
              size="small"
            >
              管理
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="描述"
          min-width="160"
        >
          <template #default="{ row }">
            <AiAdminTextCell :text="row.description" />
          </template>
        </el-table-column>
        <el-table-column
          label="更新时间"
          width="160"
        >
          <template #default="{ row }">
            <span class="text-[12.5px] text-dim">{{ row.updatedAt ? row.updatedAt.replace('T', ' ').slice(0, 19) : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="200"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              size="small"
              @click="openDetail(row as AiSkill, 'view')"
            >
              查看
            </el-button>
            <el-button
              v-if="row.id !== null"
              link
              type="primary"
              size="small"
              @click="openDetail(row as AiSkill, 'edit')"
            >
              编辑
            </el-button>
            <el-button
              link
              size="small"
              :loading="exporting === row.name"
              @click="exportSkill(row.name)"
            >
              导出
            </el-button>
            <el-button
              v-if="row.id !== null"
              link
              size="small"
              type="danger"
              @click="remove(row as AiSkill)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="admin-pager">
        <el-pagination
          :current-page="query.page + 1"
          :page-size="query.size"
          :page-sizes="[20, 50, 100]"
          :total="total"
          background
          layout="sizes, prev, pager, next, jumper"
          @current-change="onPage"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <el-drawer
      v-model="drawer.visible"
      :title="drawerTitle"
      size="560px"
    >
      <div
        v-loading="drawer.loading"
        class="space-y-4"
      >
        <el-tag
          v-if="drawer.origin === 'IMPORT'"
          size="small"
          type="success"
        >
          由导入创建
        </el-tag>
        <el-form label-position="top">
          <el-form-item label="名称">
            <el-input
              v-model="form.name"
              :disabled="readOnly || drawer.mode === 'edit'"
              placeholder="小写字母、数字和连字符，例如 config-walkthrough"
              maxlength="64"
            />
          </el-form-item>
          <el-form-item label="描述">
            <el-input
              v-model="form.description"
              :disabled="readOnly"
              maxlength="255"
              placeholder="一行说明，展示在技能目录里"
            />
          </el-form-item>
          <el-form-item label="适用场景">
            <el-input
              v-model="form.whenToUse"
              :disabled="readOnly"
              maxlength="255"
              placeholder="模型据此判断何时加载，例如 “排查插件冲突时”"
            />
          </el-form-item>
          <el-form-item label="指令正文（Markdown）">
            <el-input
              v-model="form.body"
              :disabled="readOnly"
              type="textarea"
              :rows="16"
              class="font-mono"
              placeholder="技能被 loadSkill 加载后注入 system prompt 的完整工作流"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button
          v-if="drawer.mode !== 'create'"
          :loading="exporting === form.name"
          @click="exportSkill(form.name)"
        >
          导出 SKILL.md
        </el-button>
        <template v-if="!readOnly">
          <el-button @click="drawer.visible = false">
            取消
          </el-button>
          <el-button
            type="primary"
            :loading="drawer.saving"
            @click="save"
          >
            保存
          </el-button>
        </template>
      </template>
    </el-drawer>
  </AdminPage>
</template>
