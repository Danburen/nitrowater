<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, ref } from 'vue'

import { adminApi, type RoleForm, type RoleVO } from '@/api/admin'

const loading = ref(false)
const roles = ref<RoleVO[]>([])

const dialogVisible = ref(false)
const saving = ref(false)
const editing = ref<RoleVO | null>(null)
const form = ref<RoleForm>({ code: '', name: '', description: '' })

async function load() {
  loading.value = true
  try {
    roles.value = await adminApi.listRoles()
  } catch (e) {
    ElMessage.error(errText(e))
  } finally {
    loading.value = false
  }
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
  <div class="mx-auto w-full max-w-[1120px] p-4 sm:p-6">
    <div class="section-head">
      <div>
        <h1 class="text-lg font-semibold text-ink">
          角色管理
        </h1>
        <p class="mt-1 text-[13px] text-dim">
          共 {{ roles.length }} 个角色 · 内置角色不可删除。
        </p>
      </div>
      <button
        class="btn btn-primary"
        @click="openCreate"
      >
        新建角色
      </button>
    </div>

    <div class="panel overflow-hidden">
      <el-table
        v-loading="loading"
        :data="roles"
      >
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
    </div>

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
  </div>
</template>
