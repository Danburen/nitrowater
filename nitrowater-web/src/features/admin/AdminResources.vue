<template>
  <AdminPage
    title="资源管理"
    description="统一资源服务的元数据、归属与可见性；字节存储与下载由 resource 服务负责。"
  >
    <div class="flex flex-col gap-3">
      <form
        class="admin-actions"
        @submit.prevent="search"
      >
        <input
          v-model.trim="query.q"
          class="input w-[220px]"
          placeholder="原始文件名 / 存储路径"
        >
        <select
          v-model="query.sourceType"
          class="input w-[150px]"
        >
          <option value="">
            来源：全部
          </option>
          <option
            v-for="o in dict.sourceType"
            :key="o.value"
            :value="o.value"
          >
            {{ o.label }}
          </option>
        </select>
        <select
          v-model="query.status"
          class="input w-[120px]"
        >
          <option value="">
            状态：全部
          </option>
          <option
            v-for="o in dict.status"
            :key="o.value"
            :value="o.value"
          >
            {{ o.label }}
          </option>
        </select>
        <select
          v-model="appChoice"
          class="input w-[150px]"
        >
          <option value="">
            app：全部
          </option>
          <option
            v-for="o in dict.app"
            :key="o.value"
            :value="o.value"
          >
            {{ o.label }}
          </option>
          <option :value="OTHER_APP">
            其他…
          </option>
        </select>
        <input
          v-if="appChoice === OTHER_APP"
          v-model.trim="appCustom"
          class="input w-[160px]"
          placeholder="自定义 app"
        >
        <input
          v-model.number="query.ownerUid"
          type="number"
          class="input w-[140px]"
          placeholder="ownerUid"
        >
        <input
          v-model.trim="query.group"
          class="input w-[140px]"
          placeholder="group"
        >
        <button
          type="submit"
          class="btn btn-primary"
        >
          查询
        </button>
        <button
          type="button"
          class="btn"
          @click="resetQuery"
        >
          重置
        </button>
      </form>

      <div class="min-h-0 flex-1 rounded-lg border border-line bg-card">
        <!--
          Element Plus table: the action column is pinned to the right and the remaining columns keep
          explicit widths, so a narrow viewport scrolls horizontally under a permanently visible
          action column instead of pushing 详情/下载/删除 off screen.
        -->
        <el-table
          :data="rows"
          row-key="uuid"
          height="100%"
          border
          stripe
          :empty-text="loading ? '加载中…' : '暂无数据'"
        >
          <el-table-column
            label="UUID"
            width="300"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span class="font-mono text-[11px]">{{ row.uuid }}</span>
            </template>
          </el-table-column>
          <el-table-column
            label="原始文件名"
            min-width="220"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.name ?? '-' }}
            </template>
          </el-table-column>
          <el-table-column
            label="MIME"
            width="190"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.mimeType ?? '-' }}
            </template>
          </el-table-column>
          <el-table-column
            label="大小"
            width="100"
            align="center"
          >
            <template #default="{ row }">
              {{ formatSize(row.sizeBytes) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="app"
            label="app"
            width="130"
            show-overflow-tooltip
          />
          <el-table-column
            label="group"
            width="130"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.groupKey ?? '-' }}
            </template>
          </el-table-column>
          <el-table-column
            prop="resourceType"
            label="resourceType"
            width="140"
          />
          <el-table-column
            label="version"
            width="120"
          >
            <template #default="{ row }">
              {{ row.version ?? '-' }}
            </template>
          </el-table-column>
          <el-table-column
            label="status"
            width="120"
            align="center"
          >
            <template #default="{ row }">
              <el-tag
                :type="statusTag(row.status)"
                size="small"
                effect="light"
              >
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            label="createdAt"
            width="220"
          >
            <template #default="{ row }">
              {{ row.createdAt ?? '-' }}
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
                <button
                  type="button"
                  class="btn-xs"
                  @click="openDetail(row.uuid)"
                >
                  详情
                </button>
                <a
                  class="btn-xs"
                  :href="apiDownloadUrl(row.uuid)"
                  target="_blank"
                  rel="noopener"
                >下载</a>
                <button
                  type="button"
                  class="btn-xs btn-danger"
                  @click="remove(row.uuid, row.name ?? null)"
                >
                  删除
                </button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="flex items-center justify-between text-sm text-dim">
        <div>共 {{ page.total }} 条，{{ page.page + 1 }}/{{ Math.max(page.totalPages, 1) }} 页</div>
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
    </div>

    <div
      v-if="detailOpen"
      class="fixed inset-0 z-40 bg-black/30"
      @click.self="closeDetail"
    >
      <div class="absolute right-0 top-0 h-full w-full max-w-[720px] overflow-auto border-l border-line bg-card shadow-xl">
        <div class="flex items-center justify-between border-b border-line px-4 py-2">
          <h2 class="text-sm font-medium text-ink">
            资源详情
          </h2>
          <button
            type="button"
            class="btn"
            @click="closeDetail"
          >
            关闭
          </button>
        </div>
        <div
          v-if="detail"
          class="grid grid-cols-2 gap-3 p-4"
        >
          <div class="col-span-2">
            <div class="text-dim">
              UUID
            </div>
            <div class="font-mono text-[11px]">
              {{ detail.uuid }}
            </div>
          </div>
          <div>
            <div class="text-dim">
              原始文件名
            </div>
            <div>{{ detail.name ?? '-' }}</div>
          </div>
          <div>
            <div class="text-dim">
              MIME
            </div>
            <div>{{ detail.mimeType ?? '-' }}</div>
          </div>
          <div>
            <div class="text-dim">
              大小
            </div>
            <div>{{ formatSize(detail.sizeBytes) }}</div>
          </div>
          <div>
            <div class="text-dim">
              resourceType
            </div>
            <div>{{ detail.resourceType }}</div>
          </div>
          <div>
            <div class="text-dim">
              version
            </div>
            <div>{{ detail.version ?? '-' }}</div>
          </div>
          <div>
            <div class="text-dim">
              visibility
            </div>
            <div>{{ detail.visibility }}</div>
          </div>
          <div>
            <div class="text-dim">
              status
            </div>
            <div>
              <el-tag
                :type="statusTag(detail.status)"
                size="small"
                effect="light"
              >
                {{ detail.status }}
              </el-tag>
            </div>
          </div>
          <div>
            <div class="text-dim">
              app
            </div>
            <div>{{ detail.app }}</div>
          </div>
          <div>
            <div class="text-dim">
              group
            </div>
            <div>{{ detail.groupKey ?? '-' }}</div>
          </div>
          <div>
            <div class="text-dim">
              createdAt
            </div>
            <div>{{ detail.createdAt ?? '-' }}</div>
          </div>
          <div>
            <div class="text-dim">
              下载
            </div>
            <a
              v-if="detail.url"
              class="btn-xs"
              :href="detail.url"
              target="_blank"
              rel="noopener"
            >打开</a>
            <span v-else>-</span>
          </div>
        </div>
      </div>
    </div>
  </AdminPage>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import AdminPage from './AdminPage.vue'
import {
  adminListResources,
  adminGetResource,
  adminGetResourceDictionaries,
  adminDeleteResource,
} from '../../api/admin'
import type {
  AdminResourceItem,
  AdminResourceDetail,
  AdminResourcePage,
  DictionaryOption,
} from '../../api/admin'

/** Sentinel option value: the viewer typed an app name that is not in the dictionary. */
const OTHER_APP = '__other__'

const query = reactive({
  q: '',
  sourceType: '',
  status: '',
  ownerUid: null as number | null,
  group: '',
  page: 0,
  size: 20,
})

/** App filter: a select over the namespaces present in the data, with a "custom" escape hatch. */
const appChoice = ref('')
const appCustom = ref('')

const rows = ref<AdminResourceItem[]>([])
const loading = ref(false)
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
const detail = ref<AdminResourceDetail | null>(null)

function errMsg(e: unknown, fallback: string): string {
  return e instanceof Error ? e.message : fallback
}

function formatSize(bytes: number | null | undefined): string {
  if (bytes == null) return '-'
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

/** The app value actually sent: the picked namespace, or the hand-typed one behind "其他". */
function effectiveApp(): string {
  return appChoice.value === OTHER_APP ? appCustom.value : appChoice.value
}

async function refresh() {
  loading.value = true
  try {
    const p: AdminResourcePage = await adminListResources({
      q: query.q || undefined,
      sourceType: query.sourceType || undefined,
      status: query.status || undefined,
      app: effectiveApp() || undefined,
      ownerUid: query.ownerUid ?? undefined,
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
    alert(errMsg(e, '加载失败'))
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
    alert(errMsg(e, '筛选选项加载失败'))
  }
}

function search() {
  // Always restart from the first page so a filter change never lands on a stale page index.
  query.page = 0
  refresh()
}

function resetQuery() {
  query.q = ''
  query.sourceType = ''
  query.status = ''
  query.ownerUid = null
  query.group = ''
  appChoice.value = ''
  appCustom.value = ''
  query.page = 0
  query.size = 20
  refresh()
}

/** `el-pagination` reports a 1-based page; the wire contract is 0-based. */
function pageTo(oneBased: number) {
  query.page = Math.max(0, oneBased - 1)
  refresh()
}

function onSizeChange(size: number) {
  query.size = size
  query.page = 0
  refresh()
}

async function openDetail(uuid: string) {
  detail.value = null
  detailOpen.value = true
  try {
    detail.value = await adminGetResource(uuid)
  } catch (e) {
    alert(errMsg(e, '获取详情失败'))
  }
}

function closeDetail() {
  detailOpen.value = false
  detail.value = null
}

async function remove(uuid: string, name: string | null) {
  // The list is already scoped to what this viewer may delete (super-admin sees all rows,
  // any other admin only their own), so the button stays enabled; the server re-checks
  // ownership on DELETE and a 403 surfaces via the alert below.
  if (!confirm(`确认删除资源 ${uuid}（${name ?? uuid}）？`)) return
  try {
    await adminDeleteResource(uuid)
    await refresh()
  } catch (e) {
    alert(errMsg(e, '删除失败'))
  }
}

onMounted(() => {
  loadDictionaries()
  refresh()
})
</script>
