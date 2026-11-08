<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'

import { adminApi, type RoleVO, type UserSummaryVO } from '@/api/admin'
import AdminFilterCard from '@/components/AdminFilterCard.vue'
import AdminPage from '@/components/AdminPage.vue'

const loading = ref(false)
const users = ref<UserSummaryVO[]>([])
const roles = ref<RoleVO[]>([])
const total = ref(0)
const query = reactive({ keyword: '', page: 0, size: 20 })

const dialogVisible = ref(false)
const saving = ref(false)
const current = ref<UserSummaryVO | null>(null)
const selected = ref<string[]>([])

async function load() {
  loading.value = true
  try {
    const res = await adminApi.listUsers(query.keyword, query.page, query.size)
    users.value = res.content
    total.value = res.page.totalElements
    // A page can come back empty after the last row of the last page is removed.
    if (res.content.length === 0 && res.page.number > 0) {
      query.page = Math.max(res.page.number - 1, 0)
      return load()
    }
  } catch (e) {
    ElMessage.error(errText(e))
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 0
  void load()
}

function resetQuery() {
  query.keyword = ''
  search()
}

function pageTo(oneBased: number) {
  query.page = Math.max(0, oneBased - 1)
  void load()
}

function onSizeChange(size: number) {
  query.size = size
  query.page = 0
  void load()
}

function openAssign(row: UserSummaryVO) {
  current.value = row
  selected.value = [...row.roles]
  dialogVisible.value = true
}

async function saveAssign() {
  if (!current.value) return
  saving.value = true
  try {
    await adminApi.assignRoles(current.value.uid, selected.value)
    ElMessage.success('角色已更新')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(errText(e))
  } finally {
    saving.value = false
  }
}

function errText(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}

onMounted(async () => {
  try {
    roles.value = await adminApi.listAllRoles()
  } catch {
    // 401 is handled by the http layer (redirect to the SSO login page); ignore here.
    return
  }
  await load()
})
</script>

<template>
  <AdminPage
    title="用户管理"
    description="查看账号并为用户分配角色。"
  >
    <AdminFilterCard
      :loading="loading"
      @submit="search"
      @reset="resetQuery"
    >
      <el-form-item label="关键字">
        <el-input
          v-model="query.keyword"
          placeholder="搜索用户名 / 昵称"
          clearable
          class="w-[220px]!"
          @keyup.enter="search"
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
            <span class="admin-card-title">用户列表</span>
            <span class="admin-card-meta">共 {{ total }} 个</span>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="users"
        row-key="uid"
        size="small"
        stripe
      >
        <template #empty>
          <el-empty
            description="暂无用户"
            :image-size="60"
          />
        </template>
        <el-table-column
          prop="uid"
          label="UID"
          width="150"
        />
        <el-table-column
          prop="username"
          label="用户名"
          min-width="140"
        />
        <el-table-column
          prop="nickname"
          label="昵称"
          min-width="120"
        >
          <template #default="{ row }">
            <span :class="row.nickname ? 'text-ink' : 'text-faint'">{{ row.nickname || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="userType"
          label="类型"
          width="110"
        />
        <el-table-column
          label="状态"
          width="120"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.accountStatus === 'ACTIVE' ? 'success' : 'info'"
              size="small"
            >
              {{ row.accountStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="角色"
          min-width="200"
        >
          <template #default="{ row }">
            <el-tag
              v-for="r in row.roles"
              :key="r"
              size="small"
              class="mr-1"
            >
              {{ r }}
            </el-tag>
            <span
              v-if="row.roles.length === 0"
              class="text-xs text-faint"
            >无</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="120"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="openAssign(row)"
            >
              分配角色
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="admin-pager">
        <el-pagination
          :current-page="query.page + 1"
          :page-size="query.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          background
          layout="sizes, prev, pager, next, jumper"
          @current-change="pageTo"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      title="分配角色"
      width="480px"
    >
      <div
        v-if="current"
        class="mb-3 text-sm text-dim"
      >
        {{ current.username }}<span v-if="current.nickname">（{{ current.nickname }}）</span>
      </div>
      <el-checkbox-group
        v-model="selected"
        class="flex flex-col gap-2"
      >
        <el-checkbox
          v-for="r in roles"
          :key="r.code"
          :value="r.code"
        >
          {{ r.name }} <span class="text-xs text-faint">{{ r.code }}</span>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="dialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="saveAssign"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </AdminPage>
</template>
