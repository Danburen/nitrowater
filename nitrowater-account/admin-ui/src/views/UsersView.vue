<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'

import { adminApi, type RoleVO, type UserSummaryVO } from '@/api/admin'

const loading = ref(false)
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const users = ref<UserSummaryVO[]>([])
const roles = ref<RoleVO[]>([])

const dialogVisible = ref(false)
const saving = ref(false)
const current = ref<UserSummaryVO | null>(null)
const selected = ref<string[]>([])

async function load() {
  loading.value = true
  try {
    const res = await adminApi.listUsers(keyword.value, page.value, size.value)
    users.value = res.content
    total.value = res.page.totalElements
  } catch (e) {
    ElMessage.error(errText(e))
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  void load()
}

function onSizeChange() {
  page.value = 1
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
    roles.value = await adminApi.listRoles()
  } catch {
    // 401 is handled by the http layer (redirect to the SSO login page); ignore here.
    return
  }
  await load()
})
</script>

<template>
  <div class="mx-auto w-full max-w-[1120px] p-4 sm:p-6">
    <div class="section-head">
      <div>
        <h1 class="text-lg font-semibold text-ink">
          用户管理
        </h1>
        <p class="mt-1 text-[13px] text-dim">
          查看账号并为用户分配角色。
        </p>
      </div>
    </div>

    <div class="panel overflow-hidden">
      <div class="flex flex-wrap items-center gap-2 border-b border-line p-3">
        <el-input
          v-model="keyword"
          placeholder="搜索用户名 / 昵称"
          clearable
          class="max-w-xs"
          @keyup.enter="search"
        />
        <el-button
          type="primary"
          @click="search"
        >
          搜索
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="users"
      >
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

      <div class="flex justify-end border-t border-line p-3">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="load"
          @size-change="onSizeChange"
        />
      </div>
    </div>

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
  </div>
</template>
