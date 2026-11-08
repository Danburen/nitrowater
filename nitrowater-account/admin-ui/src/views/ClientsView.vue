<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'

import { adminApi, type ClientForm, type ClientVO } from '@/api/admin'
import AdminFilterCard from '@/components/AdminFilterCard.vue'
import AdminPage from '@/components/AdminPage.vue'

const loading = ref(false)
const clients = ref<ClientVO[]>([])
const total = ref(0)
const query = reactive({ keyword: '', page: 0, size: 20 })

const dialogVisible = ref(false)
const saving = ref(false)
const editing = ref<ClientVO | null>(null)

/** URIs are edited as one-per-line text and converted to arrays on submit. */
interface FormState {
  clientId: string
  clientName: string
  clientAuthenticationMethods: string[]
  authorizationGrantTypes: string[]
  redirectUrisText: string
  postLogoutRedirectUrisText: string
  scopes: string[]
  requireProofKey: boolean
  requireAuthorizationConsent: boolean
  accessTokenTtlMinutes: number
  refreshTokenTtlDays: number
}

const form = ref<FormState>(blankForm())

/** One-time secret reveal. */
const secretVisible = ref(false)
const secretValue = ref('')
const secretClientId = ref('')

const AUTH_METHODS = [
  { value: 'client_secret_basic', label: 'client_secret_basic（Basic 头）' },
  { value: 'client_secret_post', label: 'client_secret_post（表单体）' },
  { value: 'none', label: 'none（公开客户端，无密钥）' },
]
const GRANT_TYPES = [
  { value: 'authorization_code', label: 'authorization_code（授权码）' },
  { value: 'refresh_token', label: 'refresh_token（刷新令牌）' },
  { value: 'client_credentials', label: 'client_credentials（客户端凭证）' },
]
const SCOPE_OPTIONS = ['openid', 'profile', 'email']

function blankForm(): FormState {
  return {
    clientId: '',
    clientName: '',
    clientAuthenticationMethods: ['client_secret_basic'],
    authorizationGrantTypes: ['authorization_code', 'refresh_token'],
    redirectUrisText: '',
    postLogoutRedirectUrisText: '',
    scopes: ['openid', 'profile'],
    requireProofKey: false,
    requireAuthorizationConsent: false,
    accessTokenTtlMinutes: 15,
    refreshTokenTtlDays: 30,
  }
}

function linesToArray(text: string): string[] {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
}

function arrayToLines(lines: string[]): string {
  return lines.join('\n')
}

function isPublicOnly(methods: string[]): boolean {
  return methods.length === 1 && methods[0] === 'none'
}

async function load() {
  loading.value = true
  try {
    const page = await adminApi.listClients(query.keyword, query.page, query.size)
    clients.value = page.content
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
  form.value = blankForm()
  dialogVisible.value = true
}

function openEdit(row: ClientVO) {
  editing.value = row
  form.value = {
    clientId: row.clientId,
    clientName: row.clientName,
    clientAuthenticationMethods: [...row.clientAuthenticationMethods],
    authorizationGrantTypes: [...row.authorizationGrantTypes],
    redirectUrisText: arrayToLines(row.redirectUris),
    postLogoutRedirectUrisText: arrayToLines(row.postLogoutRedirectUris),
    scopes: [...row.scopes],
    requireProofKey: row.requireProofKey,
    requireAuthorizationConsent: row.requireAuthorizationConsent,
    accessTokenTtlMinutes: row.accessTokenTtlMinutes,
    refreshTokenTtlDays: row.refreshTokenTtlDays,
  }
  dialogVisible.value = true
}

function buildPayload(): ClientForm {
  return {
    clientId: form.value.clientId.trim(),
    clientName: form.value.clientName.trim(),
    clientAuthenticationMethods: [...form.value.clientAuthenticationMethods],
    authorizationGrantTypes: [...form.value.authorizationGrantTypes],
    redirectUris: linesToArray(form.value.redirectUrisText),
    postLogoutRedirectUris: linesToArray(form.value.postLogoutRedirectUrisText),
    scopes: [...form.value.scopes],
    requireProofKey: form.value.requireProofKey,
    requireAuthorizationConsent: form.value.requireAuthorizationConsent,
    accessTokenTtlMinutes: form.value.accessTokenTtlMinutes,
    refreshTokenTtlDays: form.value.refreshTokenTtlDays,
  }
}

/** Update payload: client_id is immutable, so it is stripped from the create payload. */
function toUpdatePayload(full: ClientForm): Omit<ClientForm, 'clientId'> {
  return {
    clientName: full.clientName,
    clientAuthenticationMethods: full.clientAuthenticationMethods,
    authorizationGrantTypes: full.authorizationGrantTypes,
    redirectUris: full.redirectUris,
    postLogoutRedirectUris: full.postLogoutRedirectUris,
    scopes: full.scopes,
    requireProofKey: full.requireProofKey,
    requireAuthorizationConsent: full.requireAuthorizationConsent,
    accessTokenTtlMinutes: full.accessTokenTtlMinutes,
    refreshTokenTtlDays: full.refreshTokenTtlDays,
  }
}

function validate(): string | null {
  if (!editing.value && !form.value.clientId.trim()) return '请填写 client_id'
  if (!form.value.clientName.trim()) return '请填写应用名称'
  if (form.value.clientAuthenticationMethods.length === 0) return '至少选择一种认证方式'
  if (form.value.authorizationGrantTypes.length === 0) return '至少选择一种授权类型'
  if (linesToArray(form.value.redirectUrisText).length === 0) return '至少填写一个重定向 URI'
  if (form.value.scopes.length === 0) return '至少填写一个作用域'
  return null
}

async function save() {
  const error = validate()
  if (error) {
    ElMessage.warning(error)
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (editing.value) {
      const res = await adminApi.updateClient(editing.value.id, toUpdatePayload(payload))
      revealSecret(res.clientSecret, res.client.clientId)
      ElMessage.success('应用已更新')
    } else {
      const res = await adminApi.createClient(payload)
      revealSecret(res.clientSecret, res.client.clientId)
      ElMessage.success('应用已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(errText(e))
  } finally {
    saving.value = false
  }
}

async function resetSecret(row: ClientVO) {
  try {
    await ElMessageBox.confirm(
      `确认重置「${row.clientName}」的密钥？旧密钥将立即失效。`,
      '提示',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    const res = await adminApi.resetClientSecret(row.id)
    revealSecret(res.clientSecret, row.clientId)
    ElMessage.success('密钥已重置')
    await load()
  } catch (e) {
    ElMessage.error(errText(e))
  }
}

async function remove(row: ClientVO) {
  try {
    await ElMessageBox.confirm(`确认删除应用「${row.clientName}」？此操作不可恢复。`, '提示', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await adminApi.deleteClient(row.id)
    ElMessage.success('应用已删除')
    await load()
  } catch (e) {
    ElMessage.error(errText(e))
  }
}

function revealSecret(secret: string | null, clientId: string) {
  if (!secret) return
  secretValue.value = secret
  secretClientId.value = clientId
  secretVisible.value = true
}

async function copySecret() {
  try {
    await navigator.clipboard.writeText(secretValue.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

function fmtTime(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
}

function errText(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}

onMounted(load)
</script>

<template>
  <AdminPage
    title="应用管理"
    description="注册并管理接入 SSO 的 OIDC 应用（客户端）。"
  >
    <template #actions>
      <el-button
        type="primary"
        @click="openCreate"
      >
        新建应用
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
          placeholder="client_id / 名称"
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
            <span class="admin-card-title">应用列表</span>
            <span class="admin-card-meta">共 {{ total }} 个</span>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="clients"
        row-key="id"
        size="small"
        stripe
      >
        <template #empty>
          <el-empty
            description="暂无应用"
            :image-size="60"
          />
        </template>
        <el-table-column
          prop="clientName"
          label="名称"
          min-width="150"
        />
        <el-table-column
          prop="clientId"
          label="client_id"
          min-width="160"
        />
        <el-table-column
          label="类型"
          width="90"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.confidential ? 'warning' : 'info'"
              size="small"
            >
              {{ row.confidential ? '机密' : '公开' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="授权类型"
          min-width="220"
        >
          <template #default="{ row }">
            <el-tag
              v-for="g in row.authorizationGrantTypes"
              :key="g"
              size="small"
              class="mr-1"
            >
              {{ g }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="作用域"
          min-width="170"
        >
          <template #default="{ row }">
            <el-tag
              v-for="s in row.scopes"
              :key="s"
              size="small"
              type="success"
              class="mr-1"
            >
              {{ s }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="签发时间"
          width="170"
        >
          <template #default="{ row }">
            <span class="text-xs text-dim">{{ fmtTime(row.clientIdIssuedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="240"
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
              type="warning"
              :disabled="!row.confidential"
              @click="resetSecret(row)"
            >
              重置密钥
            </el-button>
            <el-button
              link
              type="danger"
              :disabled="row.protectedClient"
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
      :title="editing ? '编辑应用' : '新建应用'"
      width="640px"
      top="6vh"
    >
      <el-form label-width="120px">
        <el-form-item label="client_id">
          <el-input
            v-model="form.clientId"
            :disabled="!!editing"
            placeholder="如 my-app（唯一，创建后不可改）"
          />
        </el-form-item>
        <el-form-item label="名称">
          <el-input
            v-model="form.clientName"
            placeholder="应用显示名"
          />
        </el-form-item>
        <el-form-item label="认证方式">
          <el-select
            v-model="form.clientAuthenticationMethods"
            multiple
            class="w-full"
            placeholder="选择认证方式"
          >
            <el-option
              v-for="m in AUTH_METHODS"
              :key="m.value"
              :label="m.label"
              :value="m.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="授权类型">
          <el-select
            v-model="form.authorizationGrantTypes"
            multiple
            class="w-full"
            placeholder="选择授权类型"
          >
            <el-option
              v-for="g in GRANT_TYPES"
              :key="g.value"
              :label="g.label"
              :value="g.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="重定向 URI">
          <el-input
            v-model="form.redirectUrisText"
            type="textarea"
            :rows="3"
            placeholder="一行一个，如 https://example.com/login/oauth2/code/nitrowater"
          />
        </el-form-item>
        <el-form-item label="登出回跳 URI">
          <el-input
            v-model="form.postLogoutRedirectUrisText"
            type="textarea"
            :rows="2"
            placeholder="一行一个（RP-initiated logout）"
          />
        </el-form-item>
        <el-form-item label="作用域">
          <el-select
            v-model="form.scopes"
            multiple
            filterable
            allow-create
            default-first-option
            class="w-full"
            placeholder="如 openid / profile，可自定义"
          >
            <el-option
              v-for="s in SCOPE_OPTIONS"
              :key="s"
              :label="s"
              :value="s"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="PKCE">
          <el-switch v-model="form.requireProofKey" />
          <span class="ml-2 text-xs text-faint">{{ isPublicOnly(form.clientAuthenticationMethods) ? '公开客户端建议开启' : '机密客户端可选' }}</span>
        </el-form-item>
        <el-form-item label="授权同意页">
          <el-switch v-model="form.requireAuthorizationConsent" />
        </el-form-item>
        <el-form-item label="AT 有效期(分)">
          <el-input-number
            v-model="form.accessTokenTtlMinutes"
            :min="1"
            :max="1440"
          />
        </el-form-item>
        <el-form-item label="RT 有效期(天)">
          <el-input-number
            v-model="form.refreshTokenTtlDays"
            :min="1"
            :max="365"
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

    <el-dialog
      v-model="secretVisible"
      title="应用密钥（仅显示一次）"
      width="560px"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="请立即复制并妥善保存"
        description="出于安全考虑，明文密钥只在此处显示一次，关闭后无法再查看。若丢失，请重置密钥。"
      />
      <div class="mt-4">
        <div class="mb-1 text-xs text-dim">
          client_id：{{ secretClientId }}
        </div>
        <div class="flex items-center gap-2">
          <el-input
            :model-value="secretValue"
            readonly
          />
          <el-button
            type="primary"
            @click="copySecret"
          >
            复制
          </el-button>
        </div>
      </div>
      <template #footer>
        <el-button @click="secretVisible = false">
          我已保存
        </el-button>
      </template>
    </el-dialog>
  </AdminPage>
</template>
