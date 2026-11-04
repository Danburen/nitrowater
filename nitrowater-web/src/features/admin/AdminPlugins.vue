<script setup lang="ts">
/**
 * Admin — plugins: metadata list plus a create/edit drawer.
 *
 * <p>Uses the same chrome as every other admin page ({@link AdminPage} + {@code el-card.admin-card-flush}):
 * action strip on top, table in the middle, then the shared {@link AppDrawer} for the editor — the
 * popup chrome is Tailwind so it matches the portal, while the form controls stay Element Plus.</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import AppDrawer from '../../shared/ui/AppDrawer.vue'
import { confirm } from '../../shared/ui/confirm'
import { toast } from '../../shared/ui/toast'
import AdminPage from './components/AdminPage.vue'
import {
  createPlugin,
  deletePlugin,
  listPlugins,
  updatePlugin,
  type PluginAdmin,
  type PluginAdminSort,
  type PluginUpsert,
} from '../../api/plugins'
import {dayjs} from "element-plus";

/** Server-side key rule (`@Pattern` on PluginUpsertReq). */
const KEY_RE = /^[a-z0-9][a-z0-9-]*$/

const rows = ref<PluginAdmin[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const total = ref(0)
const totalPages = ref(1)

/** Filters mirror `GET /api/plugins`; `enabled` is a tri-state select (`all` = both). */
const query = reactive({
  q: '',
  enabled: 'all' as 'all' | 'true' | 'false',
  sort: 'default' as PluginAdminSort,
  page: 0,
  size: 20,
})

const editingId = ref<number | null>(null)
const drawerOpen = ref(false)
const editing = computed(() => editingId.value != null)
const drawerTitle = computed(() => (editing.value ? `编辑插件 #${editingId.value}` : '新建插件'))

/** Form state — tags are edited as a comma-separated string. */
const form = reactive({
  key: '',
  name: '',
  tagline: '',
  summary: '',
  icon: '',
  tagsText: '',
  docsUrl: '',
  homepageUrl: '',
  repoUrl: '',
  sponsorUrl: '',
  readme: '',
  sortOrder: 0,
  enabled: true,
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await listPlugins({
      q: query.q || undefined,
      enabled: query.enabled === 'all' ? undefined : query.enabled === 'true',
      sort: query.sort,
      page: query.page,
      size: query.size,
    })
    rows.value = result.content
    total.value = result.page.totalElements
    totalPages.value = Math.max(1, result.page.totalPages)
    // A filter change or a delete can shrink the set below the current page; fall back to the last
    // valid page instead of rendering an out-of-range empty page.
    if (result.content.length === 0 && result.page.number > 0
      && result.page.number >= result.page.totalPages) {
      query.page = Math.max(result.page.totalPages - 1, 0)
      return load()
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function search(): void {
  // Always restart from the first page so a filter change never lands on a stale page index.
  query.page = 0
  void load()
}

function resetQuery(): void {
  query.q = ''
  query.enabled = 'all'
  query.sort = 'default'
  query.page = 0
  query.size = 20
  void load()
}

/** `el-pagination` reports a 1-based page; the wire contract is 0-based. */
function pageTo(oneBased: number): void {
  query.page = Math.max(0, oneBased - 1)
  void load()
}

function onSizeChange(size: number): void {
  query.size = size
  query.page = 0
  void load()
}

function resetForm(): void {
  editingId.value = null
  form.key = ''
  form.name = ''
  form.tagline = ''
  form.summary = ''
  form.icon = ''
  form.tagsText = ''
  form.docsUrl = ''
  form.homepageUrl = ''
  form.repoUrl = ''
  form.sponsorUrl = ''
  form.readme = ''
  form.sortOrder = 0
  form.enabled = true
}

function openCreate(): void {
  resetForm()
  drawerOpen.value = true
}

function openEdit(row: PluginAdmin): void {
  editingId.value = row.id
  form.key = row.key
  form.name = row.name
  form.tagline = row.tagline ?? ''
  form.summary = row.summary ?? ''
  form.icon = row.icon ?? ''
  form.tagsText = row.tags.join(', ')
  form.docsUrl = row.docsUrl ?? ''
  form.homepageUrl = row.homepageUrl ?? ''
  form.repoUrl = row.repoUrl ?? ''
  form.sponsorUrl = row.sponsorUrl ?? ''
  form.readme = row.readme ?? ''
  form.sortOrder = row.sortOrder
  form.enabled = row.enabled
  drawerOpen.value = true
}

/** Splits a comma / full-width-comma / newline separated tag string. */
function splitTags(text: string): string[] {
  return text
    .split(/[,，\n]/)
    .map((t) => t.trim())
    .filter(Boolean)
}

async function save(): Promise<void> {
  if (saving.value) return
  if (!form.name.trim()) {
    toast.warning('请填写名称')
    return
  }
  const id = editingId.value
  if (id == null) {
    const key = form.key.trim()
    if (!key) {
      toast.warning('请填写 key（小写字母 / 数字 / 连字符）')
      return
    }
    if (!KEY_RE.test(key)) {
      toast.warning('key 只能由小写字母、数字、连字符组成，且不能以连字符或数字开头')
      return
    }
  }
  saving.value = true
  try {
    const payload: PluginUpsert = {
      key: form.key.trim() || null,
      name: form.name.trim(),
      tagline: form.tagline.trim() || null,
      summary: form.summary.trim() || null,
      icon: form.icon.trim() || null,
      tags: splitTags(form.tagsText),
      docsUrl: form.docsUrl.trim() || null,
      homepageUrl: form.homepageUrl.trim() || null,
      repoUrl: form.repoUrl.trim() || null,
      sponsorUrl: form.sponsorUrl.trim() || null,
      readme: form.readme.trim() || null,
      sortOrder: form.sortOrder,
      enabled: form.enabled,
    }
    if (id == null) {
      await createPlugin(payload)
      toast.success('插件已创建')
    } else {
      await updatePlugin(id, payload)
      toast.success('插件已更新')
    }
    drawerOpen.value = false
    resetForm()
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  } finally {
    saving.value = false
  }
}

/** Table slots hand back an untyped row; narrow it once. */
function asPlugin(row: unknown): PluginAdmin {
  return row as PluginAdmin
}

async function remove(row: PluginAdmin): Promise<void> {
  if (!(await confirm({
    title: '确认删除',
    message: `删除插件「${row.name}」？该操作不可撤销。`,
    confirmText: '删除',
    danger: true,
  }))) return
  try {
    await deletePlugin(row.id)
    toast.success('插件已删除')
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : String(e))
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <AdminPage
    title="插件管理"
    description="插件中心的元数据：卡片信息、标签与单页说明（key 创建后不可改）。"
  >
    <template #actions>
      <el-button
        :disabled="loading"
        @click="load"
      >
        刷新
      </el-button>
      <el-button
        type="primary"
        @click="openCreate"
      >
        新建插件
      </el-button>
    </template>

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
        <div class="admin-toolbar">
          <div class="flex items-baseline gap-2">
            <span class="admin-card-title">插件列表</span>
            <span class="admin-card-meta">共 {{ total }} 个</span>
          </div>
        </div>
      </template>

      <el-form
        :inline="true"
        class="admin-filter-form border-b border-line px-2 py-2"
        @submit.prevent="search"
      >
        <el-form-item label="关键词">
          <el-input
            v-model.trim="query.q"
            placeholder="key / 名称 / 简介"
            clearable
            class="w-[220px]!"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.enabled"
            class="w-[120px]!"
          >
            <el-option
              value="all"
              label="全部"
            />
            <el-option
              value="true"
              label="启用"
            />
            <el-option
              value="false"
              label="停用"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-select
            v-model="query.sort"
            class="w-[150px]!"
          >
            <el-option
              value="default"
              label="默认排序"
            />
            <el-option
              value="updated"
              label="最近更新"
            />
            <el-option
              value="name"
              label="名称"
            />
          </el-select>
        </el-form-item>
        <div class="admin-filter-actions">
          <el-button
            type="primary"
            native-type="submit"
            :loading="loading"
          >
            查询
          </el-button>
          <el-button
            :disabled="loading"
            @click="resetQuery"
          >
            重置
          </el-button>
        </div>
      </el-form>

      <el-table
        v-loading="loading"
        :data="rows"
        row-key="id"
        size="small"
        stripe
      >
        <template #empty>
          <el-empty
            description="暂无插件，点击右上角「新建插件」"
            :image-size="60"
          />
        </template>
        <el-table-column
          prop="id"
          label="ID"
          width="70"
        />
        <el-table-column
          label="名称"
          min-width="160"
        >
          <template #default="{ row }">
            <div class="text-ink">
              {{ row.name }}
            </div>
            <div
              v-if="row.tagline"
              class="text-[12px] text-faint"
            >
              {{ row.tagline }}
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="Key"
          width="100"
        >
          <template #default="{ row }">
            <span class="font-mono text-[12px] text-dim">{{ row.key }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="标签"
          min-width="200"
        >
          <template #default="{ row }">
            <div
              v-if="row.tags.length"
              class="flex flex-wrap gap-1"
            >
              <el-tag
                v-for="tag in row.tags"
                :key="tag"
                size="small"
                type="info"
                effect="plain"
              >
                {{ tag }}
              </el-tag>
            </div>
            <span
              v-else
              class="text-faint"
            >—</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="sortOrder"
          label="排序"
          width="50"
          align="center"
        />
        <el-table-column
          label="状态"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.enabled ? 'success' : 'info'"
              size="small"
              effect="plain"
            >
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="更新时间"
          width="180"
        >
          <template #default="{ row }">
            {{ row.updatedAt ? dayjs(row.updatedAt).format('YYYY-MM-DD HH:mm:ss') : '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="130"
          fixed="right"
          align="center"
        >
          <template #default="{ row }">
            <div class="flex items-center justify-center gap-1">
              <el-button
                link
                type="primary"
                size="small"
                @click="openEdit(asPlugin(row))"
              >
                编辑
              </el-button>
              <el-button
                link
                type="danger"
                size="small"
                @click="remove(asPlugin(row))"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex flex-wrap items-center justify-between gap-3 border-t border-line px-4 py-3 text-[12.5px] text-dim">
        <span>共 {{ total }} 条 · 第 {{ query.page + 1 }} / {{ totalPages }} 页</span>
        <el-pagination
          :current-page="query.page + 1"
          :page-size="query.size"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="sizes, prev, pager, next"
          @current-change="pageTo"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <AppDrawer
      v-model="drawerOpen"
      :title="drawerTitle"
      width="560px"
      @closed="resetForm"
    >
      <el-form
        label-position="top"
        :disabled="saving"
        @submit.prevent
      >
        <div class="grid gap-4 sm:grid-cols-2">
          <el-form-item
            label="名称"
            required
          >
            <el-input
              v-model="form.name"
              placeholder="例如：VeloChatX"
            />
          </el-form-item>
          <el-form-item
            label="Key（小写字母 / 数字 / 连字符）"
            :required="!editing"
          >
            <el-input
              v-model="form.key"
              :disabled="editing"
              placeholder="velochatx"
            />
          </el-form-item>
          <el-form-item
            label="一句话简介"
            class="sm:col-span-2"
          >
            <el-input
              v-model="form.tagline"
              placeholder="Velocity 聊天增强插件"
            />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number
              v-model="form.sortOrder"
              :precision="0"
              controls-position="right"
              class="!w-full"
            />
          </el-form-item>
          <el-form-item label="启用（前台可见）">
            <el-switch v-model="form.enabled" />
          </el-form-item>
          <el-form-item
            label="标签（逗号分隔）"
            class="sm:col-span-2"
          >
            <el-input
              v-model="form.tagsText"
              placeholder="Velocity, 聊天增强"
            />
          </el-form-item>
          <el-form-item
            label="图标（SVG path，24×24 的 d 属性）"
            class="sm:col-span-2"
          >
            <el-input
              v-model="form.icon"
              placeholder="M4 5h16v11H8l-4 4z"
            />
          </el-form-item>
          <el-form-item
            label="文档站 URL"
            class="sm:col-span-2"
          >
            <el-input
              v-model="form.docsUrl"
              placeholder="/plugin/velochatx/docs/"
            />
          </el-form-item>
          <el-form-item label="首页 URL（可空）">
            <el-input
              v-model="form.homepageUrl"
              placeholder="https://…"
            />
          </el-form-item>
          <el-form-item label="源码仓库 URL（可空）">
            <el-input
              v-model="form.repoUrl"
              placeholder="https://github.com/…"
            />
          </el-form-item>
          <el-form-item
            label="赞助 URL（可空）"
            class="sm:col-span-2"
          >
            <el-input
              v-model="form.sponsorUrl"
              placeholder="https://…"
            />
          </el-form-item>
          <el-form-item
            label="卡片简介"
            class="sm:col-span-2"
          >
            <el-input
              v-model="form.summary"
              type="textarea"
              :rows="2"
              placeholder="一句话总结，用于列表卡片。"
            />
          </el-form-item>
          <el-form-item
            label="单页说明 Markdown"
            class="sm:col-span-2"
          >
            <el-input
              v-model="form.readme"
              type="textarea"
              :rows="10"
              placeholder="# 插件名&#10;&#10;说明正文。"
            />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <div class="admin-page-actions justify-end">
          <el-button
            :disabled="saving"
            @click="drawerOpen = false"
          >
            取消
          </el-button>
          <el-button
            type="primary"
            :loading="saving"
            @click="save"
          >
            保存
          </el-button>
        </div>
      </template>
    </AppDrawer>
  </AdminPage>
</template>

<style scoped>
/* Element Plus paints an unlayered `margin-bottom: 18px` on every form item, so the reset has to
   stay outside Tailwind's utilities layer instead of becoming a plain `mb-0` utility. */
.el-form-item {
  margin-bottom: 0;
}
</style>