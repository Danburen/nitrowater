<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'

import { adminApi, type RoleForm, type RoleVO } from '@/api/admin'
import AdminFilterCard from '@/components/AdminFilterCard.vue'
import AdminPage from '@/components/AdminPage.vue'

const loading = ref(false)
const roles = ref<RoleVO[]>([])
const total = ref(0)
/** Tri-state: 'all' (no filter), 'true' / 'false' map to the server's optional boolean. */
const query = reactive({ keyword: '', builtin: 'all', page: 0, size: 20 })

const dialogVisible = ref(false)
const saving = ref(false)
const editing = ref<RoleVO | null>(null)
const form = ref<RoleForm>({ code: '', name: '', description: '' })

async function load() {
  loading.value = true
  try {
    const page = await adminApi.listRoles(
      query.keyword,
      query.builtin === 'all' ? '' : query.builtin,
      query.page,
      query.size,
    )
    roles.value = page.content
    total.value = page.page.totalElements
    // Deleting the last row of a page would otherwise leave an empty table behind.
    if (page.content.length === 0 && page.page.number > 0) {
      query.page = Math.max(page.page.number - 1, 0)
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
  query.builtin = 'all'
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

function openCreate() {
  editing.value = null
  form.value = { code: '', name: '', description: '' }
  dialogVisible.value = true
}

function openEdit(row: RoleVO) {
  editing.value = row
  form.value = { code: row.code, name: row.name, description: row.description ?? '' }
  dialogVisible.value = true
}

async function save() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请填写角色名称')
    return
  }
  if (!editing.value && !form.value.code.trim()) {
    ElMessage.warning('请填写角色编码')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await adminApi.updateRole(editing.value.id, {
        name: form.value.name,
        description: form.value.description,
      })
      ElMessage.success('角色已更新')
    } else {
      await adminApi.createRole(form.value)
      ElMessage.success('角色已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(errText(e))
  } finally {
    saving.value = false
  }
}

async function remove(row: RoleVO) {
  try {
    await ElMessageBox.confirm(`确认删除角色「${row.name}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await adminApi.deleteRole(row.id)
    ElMessage.success('角色已删除')
    await load()
  } catch (e) {
    ElMessage.error(errText(e))
  }
}

function errText(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}

onMounted(load)
</script>

<template>
  <AdminPage
    title="角色管理"
    description="维护角色定义；内置角色不可删除。"
  >
    <template #actions>
      <el-button
        type="primary"
        @click="openCreate"
      >
        新建角色
      </el-button>
    </template>

    <AdminFilterCard
      :loading="loading"
      @submit="search"
      @reset="resetQuery"
    >
      <el-form-item label="关键字">
        <el-input
          v-model="query.keyword"
          placeholder="编码 / 名称 / 描述"
          clearable
          class="w-[220px]!"
          @keyup.enter="search"
        />
      </el-form-item>
      <el-form-item label="范围">
        <el-select
          v-model="query.builtin"
          class="w-[130px]!"
        >
          <el-option
            value="all"
            label="全部"
          />
          <el-option
            value="true"
            label="内置"
          />
          <el-option
            value="false"
            label="自定义"
          />
        </el-select>
      </el-form-item>
    </AdminFilterCard>

    <el-card
      shadow="never"
      class="admin-card admin-card-flush"
    >
      <template #header>
        <div class="admin-card-header">
          <div class="flex items-baseline gap-2">
            <span class="admin-card-title">角色列表</span>
            <span class="admin-card-meta">共 {{ total }} 个</span>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="roles"
        row-key="id"
        size="small"
        stripe
      >
        <template #empty>
          <el-empty
            description="暂无角色"
            :image-size="60"
          />
        </template>
        <el-table-column
          prop="code"
          label="编码"
          width="180"
        />
        <el-table-column
          prop="name"
          label="名称"
          min-width="140"
        />
        <el-table-column
          prop="description"
          label="描述"
          min-width="220"
        >
          <template #default="{ row }">
            <span :class="row.description ? 'text-dim' : 'text-faint'">{{ row.description || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="内置"
          width="90"
        >
          <template #default="{ row }">
            <el-tag
              v-if="row.builtin"
              type="warning"
              size="small"
            >
              内置
            </el-tag>
            <span
              v-else
              class="text-xs text-faint"
            >—</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="userCount"
          label="账号数"
          width="90"
        />
        <el-table-column
          label="操作"
          width="160"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              link
              type="danger"
              :disabled="row.builtin"
              @click="remove(row)"
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
      :title="editing ? '编辑角色' : '新建角色'"
      width="480px"
    >
      <el-form label-width="80px">
        <el-form-item label="编码">
          <el-input
            v-model="form.code"
            :disabled="!!editing"
            placeholder="大写字母 / 下划线，如 EDITOR"
          />
        </el-form-item>
        <el-form-item label="名称">
          <el-input
            v-model="form.name"
            placeholder="角色显示名"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="可选"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="save"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </AdminPage>
</template>
