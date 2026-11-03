<script setup lang="ts">
/**
 * Admin — resources: filterable, pageable table over the resource service metadata.
 *
 * <p>Element Plus owns the table side: an inline `el-form` query bar (label left, control right,
 * actions pinned right), `el-table` and `el-pagination`. The detail panel is the shared Tailwind
 * {@link AppDrawer} and destructive actions go through the shared {@link confirm}, so popups look
 * the same here as on every other business page. Errors go to an inline `el-alert`.</p>
 */
import { onMounted, reactive, ref } from 'vue'
import AppDrawer from '../../shared/ui/AppDrawer.vue'
import { confirm } from '../../shared/ui/confirm'
import { toast } from '../../shared/ui/toast'
import AdminPage from './components/AdminPage.vue'
import { dayjs } from "element-plus";
import {
  adminDeleteResource,
  adminGetResource,
  adminGetResourceDictionaries,
  adminListResources,
} from '../../api/admin'
import type {
  AdminResourceDetail,
  AdminResourceItem,
  AdminResourcePage,
  DictionaryOption,
} from '../../api/admin'

/** Sentinel option value: the viewer typed an app name that is not in the dictionary. */
const OTHER_APP = '__other__'

const query = reactive({
  q: '',
  sourceType: '',
  status: '',
  /** Kept as text so an in-progress/blank value never turns into `0` or `NaN`. */
  ownerUid: '',
  group: '',
  page: 0,
  size: 20,
})

/** App filter: a select over the namespaces present in the data, with a "custom" escape hatch. */
const appChoice = ref('')
const appCustom = ref('')

const rows = ref<AdminResourceItem[]>([])
const loading = ref(false)
const error = ref('')
const page = reactive({ total: 0, page: 0, totalPages: 0 })

/** Filter options come from the server so labels/codes can never drift from the backend enums. */
const dict = reactive<{
  sourceType: DictionaryOption[]
  status: DictionaryOption[]
  app: DictionaryOption[]
}>({
  sourceType: [],
  status: [],
  app: [],
})

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<AdminResourceDetail | null>(null)

function errMsg(e: unknown, fallback: string): string {
  return e instanceof Error ? e.message : fallback
}

function formatSize(bytes: number | null | undefined): string {
  if (bytes == null) return '—'
  if (bytes < 1024) return bytes + ' B'
  const k = bytes / 1024
  if (k < 1024) return k.toFixed(1) + ' KB'
  const m = k / 1024
  if (m < 1024) return m.toFixed(1) + ' MB'
  const g = m / 1024
  return g.toFixed(1) + ' GB'
}

function statusTag(status: string): 'success' | 'warning' | 'info' | 'danger' {
  switch (status) {
    case 'ACTIVE':
      return 'success'
    case 'PENDING':
      return 'warning'
    case 'ORPHAN':
      return 'info'
    case 'DELETED':
      return 'danger'
    default:
      return 'info'
  }
}

function apiDownloadUrl(uuid: string): string {
  return `/api/admin/resources/${uuid}/download`
}

/** Opens a same-origin download URL in a new tab (the server sets the disposition). */
function openUrl(url: string): void {
  window.open(url, '_blank', 'noopener')
}

/** The app value actually sent: the picked namespace, or the hand-typed one behind "其他". */
function effectiveApp(): string {
  return appChoice.value === OTHER_APP ? appCustom.value.trim() : appChoice.value
}

/** Parses the owner uid filter; returns `undefined` when blank, or `null` when invalid. */
function parsedOwnerUid(): number | undefined | null {
  const text = query.ownerUid.trim()
  if (text === '') return undefined
  const value = Number(text)
  return Number.isInteger(value) && value >= 0 ? value : null
}

async function refresh() {
  const ownerUid = parsedOwnerUid()
  if (ownerUid === null) {
    error.value = 'ownerUid 必须是非负整数。'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const p: AdminResourcePage = await adminListResources({
      q: query.q || undefined,
      sourceType: query.sourceType || undefined,
      status: query.status || undefined,
      app: effectiveApp() || undefined,
      ownerUid,
      group: query.group || undefined,
      page: query.page,
      size: query.size,
    })
    Object.assign(page, p)
    rows.value = p.items
    // A filter change or a delete can shrink the result set below the requested page;
    // fall back to the last valid page instead of rendering an out-of-range empty page.
    if (p.items.length === 0 && p.page > 0 && p.page >= p.totalPages) {
      query.page = Math.max(p.totalPages - 1, 0)
      return refresh()
    }
  } catch (e) {
    error.value = errMsg(e, '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadDictionaries() {
  try {
    const d = await adminGetResourceDictionaries()
    dict.app = d.app
    dict.sourceType = d.sourceType
    dict.status = d.status
  } catch (e) {
    error.value = errMsg(e, '筛选选项加载失败')
  }
}

function search() {
  // Always restart from the first page so a filter change never lands on a stale page index.
  query.page = 0
  void refresh()
}

function resetQuery() {
  query.q = ''
  query.sourceType = ''
  query.status = ''
  query.ownerUid = ''
  query.group = ''
  appChoice.value = ''
  appCustom.value = ''
  query.page = 0
  query.size = 20
  void refresh()
}

/** `el-pagination` reports a 1-based page; the wire contract is 0-based. */
function pageTo(oneBased: number) {
  query.page = Math.max(0, oneBased - 1)
  void refresh()
}

function onSizeChange(size: number) {
  query.size = size
  query.page = 0
  void refresh()
}

async function openDetail(uuid: string) {
  detail.value = null
  detailLoading.value = true
  detailOpen.value = true
  try {
    detail.value = await adminGetResource(uuid)
  } catch (e) {
    toast.error(errMsg(e, '读取详情失败'))
    detailOpen.value = false
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  detailOpen.value = false
  detail.value = null
}

async function remove(uuid: string, name: string | null) {
  // The list is already scoped to what this viewer may delete (super-admin sees all rows,
  // any other admin only their own), so the button stays enabled; the server re-checks
  // ownership on DELETE and a 403 surfaces through the alert below.
  if (!(await confirm({
    title: '确认删除',
    message: `确认删除资源 ${uuid}（${name ?? uuid}）？`,
    confirmText: '删除',
    danger: true,
  }))) return
  try {
    await adminDeleteResource(uuid)
    toast.success('资源已删除')
    await refresh()
  } catch (e) {
    error.value = errMsg(e, '删除失败')
  }
}
const FILE_TYPE_MAP: Record<string, { label: string; icon: string }> = {
  'application/json': { label: 'JSON 文本', icon: '📄' },
  'text/plain': { label: '纯文本', icon: '📄' },
  'text/csv': { label: 'CSV 表格', icon: '📊' },
  'application/java-archive': { label: 'JAR 包', icon: '📦' },
  'application/zip': { label: 'ZIP 压缩包', icon: '📦' },
  'default': { label: '未知文件', icon: '📎' }
}

function getFileTypeInfo(row: AdminResourceItem) {
  const mime = row.mimeType || ''
  if (FILE_TYPE_MAP[mime]) return FILE_TYPE_MAP[mime]

  if (row.resourceType === 'ARCHIVE') return { label: '归档文件', icon: '📦' }
  if (row.resourceType === 'TEXT') return { label: '文本文件', icon: '📄' }

  return FILE_TYPE_MAP['default']
}
onMounted(() => {
  void loadDictionaries()
  void refresh()
})
</script>

<template>
  <AdminPage
    title="资源管理"
    description="统一资源服务的元数据、归属与可见性；字节存储与下载由 resource 服务负责。"
  >
    <template #actions>
      <el-button
        :disabled="loading"
        @click="refresh"
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

    <el-card
      shadow="never"
      class="admin-card admin-card-flush"
    >
      <el-form
        :inline="true"
        class="admin-filter-form border-b border-line px-2 py-2"
        @submit.prevent="search"
      >
        <el-form-item label="文件名">
          <el-input
            v-model.trim="query.q"
            placeholder="原始文件名"
            clearable
            class="w-[150px]!"
          />
        </el-form-item>
        <el-form-item label="来源">
          <el-select
            v-model="query.sourceType"
            placeholder="全部"
            clearable
            class="w-[120px]!"
          >
            <el-option
              v-for="o in dict.sourceType"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部"
            clearable
            class="w-[120px]!"
          >
            <el-option
              v-for="o in dict.status"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="app">
          <el-select
            v-model="appChoice"
            placeholder="全部"
            clearable
            class="w-[130px]!"
          >
            <el-option
              v-for="o in dict.app"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
            <el-option
              label="其他…"
              :value="OTHER_APP"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="appChoice === OTHER_APP"
          label="自定义"
        >
          <el-input
            v-model.trim="appCustom"
            placeholder="输入 app 名称"
            clearable
            class="w-[130px]!"
          />
        </el-form-item>
        <el-form-item label="ownerUid">
          <el-input
            v-model.trim="query.ownerUid"
            placeholder="输入 UID"
            inputmode="numeric"
            clearable
            class="w-[110px]!"
          />
        </el-form-item>
        <el-form-item label="group">
          <el-input
            v-model.trim="query.group"
            placeholder="输入 group"
            clearable
            class="w-[110px]!"
          />
        </el-form-item>
        <div class="admin-filter-actions">
          <el-button @click="resetQuery">
            重置
          </el-button>
          <el-button
            type="primary"
            native-type="submit"
            :loading="loading"
          >
            查询
          </el-button>
        </div>
      </el-form>

      <el-table
        v-loading="loading"
        :data="rows"
        row-key="uuid"
        size="small"
        stripe
      >
        <template #empty>
          <el-empty
            description="暂无资源"
            :image-size="60"
          />
        </template>
        <el-table-column
          label="UUID"
          width="150"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span class="font-mono text-[11px]">{{ row.uuid }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="原始文件名"
          min-width="140"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.name ?? '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="version"
          width="80"
        >
          <template #default="{ row }">
            {{ row.version ?? '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="类型"
          width="100"
        >
          <template #default="{ row }">
            <div class="flex items-center gap-1.5">
              <span>{{ getFileTypeInfo(row as AdminResourceItem).icon }}</span>
              <span class="text-[13px]">{{ getFileTypeInfo(row as AdminResourceItem).label }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="大小"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            {{ formatSize(row.sizeBytes) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="app"
          label="app"
          width="120"
          show-overflow-tooltip
        />
        <el-table-column
          label="group"
          width="120"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.groupKey ?? '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="status"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="statusTag(row.status)"
              size="small"
              effect="plain"
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="createdAt"
          width="190"
        >
          <template #default="{ row }">
            {{ row.createdAt ? dayjs(row.createdAt).format('YYYY-MM-DD HH:mm:ss') : '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="170"
          fixed="right"
          align="center"
        >
          <template #default="{ row }">
            <div class="flex items-center justify-center gap-1">
              <el-button
                link
                type="primary"
                size="small"
                @click="openDetail(row.uuid)"
              >
                详情
              </el-button>
              <el-button
                link
                type="primary"
                size="small"
                @click="openUrl(apiDownloadUrl(row.uuid))"
              >
                下载
              </el-button>
              <el-button
                link
                type="danger"
                size="small"
                @click="remove(row.uuid, row.name ?? null)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex flex-wrap items-center justify-between gap-3 border-t border-line px-4 py-3 text-[12.5px] text-dim">
        <span>共 {{ page.total }} 条 · 第 {{ page.page + 1 }} / {{ Math.max(page.totalPages, 1) }} 页</span>
        <el-pagination
          :current-page="page.page + 1"
          :page-size="query.size"
          :page-sizes="[20, 50, 100]"
          :total="page.total"
          background
          layout="sizes, prev, pager, next, jumper"
          @current-change="pageTo"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <AppDrawer
      v-model="detailOpen"
      title="资源详情"
      width="620px"
      @closed="closeDetail"
    >
      <el-skeleton
        v-if="detailLoading"
        :rows="6"
        animated
      />
      <el-descriptions
        v-else-if="detail"
        :column="2"
        border
        size="small"
      >
        <el-descriptions-item
          label="UUID"
          :span="2"
        >
          <span class="font-mono text-[11px]">{{ detail.uuid }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="原始文件名">
          {{ detail.name ?? '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="MIME">
          {{ detail.mimeType ?? '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="大小">
          {{ formatSize(detail.sizeBytes) }}
        </el-descriptions-item>
        <el-descriptions-item label="resourceType">
          {{ detail.resourceType }}
        </el-descriptions-item>
        <el-descriptions-item label="version">
          {{ detail.version ?? '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="visibility">
          {{ detail.visibility }}
        </el-descriptions-item>
        <el-descriptions-item label="status">
          <el-tag
            :type="statusTag(detail.status)"
            size="small"
            effect="plain"
          >
            {{ detail.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="app">
          {{ detail.app }}
        </el-descriptions-item>
        <el-descriptions-item label="group">
          {{ detail.groupKey ?? '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="createdAt">
          {{ detail.createdAt ? dayjs(detail.createdAt).format('YYYY-MM-DD HH:mm:ss') : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="下载">
          <el-button
            v-if="detail.url"
            link
            type="primary"
            size="small"
            @click="detail.url && openUrl(detail.url)"
          >
            下载
          </el-button>
          <span v-else>—</span>
        </el-descriptions-item>
      </el-descriptions>
    </AppDrawer>
  </AdminPage>
</template>
