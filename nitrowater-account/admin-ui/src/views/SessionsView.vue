<script setup lang="ts">
/**
 * Global session policy + the emergency "sign everybody out" switch.
 *
 * <p>There is no environment knob for this any more: the BFF compares each session against a
 * shared epoch in Redis, and this page rotates that epoch on demand. The same action also clears
 * the OAuth2 authorizations (refresh tokens) and the SSO sessions, which is what the old
 * `APP_SESSION_EPOCH` variable could never do on its own.</p>
 */
import { ElMessage, ElMessageBox } from 'element-plus'
import { ref } from 'vue'

import { adminApi, type RevokedSessionsVO } from '@/api/admin'
import { ApiError } from '@/api/http'
import AdminPage from '@/components/AdminPage.vue'

const revoking = ref(false)

async function revokeAll() {
  try {
    await ElMessageBox.confirm(
      '这会立即结束所有人的登录态：\n' +
        '· 门户（BFF）会话按新的会话代次全部失效\n' +
        '· 账号中心 SSO 会话全部删除\n' +
        '· 所有 OAuth2 授权（刷新令牌）全部清除\n\n' +
        '你当前的会话也会失效，确认后需要重新登录。\n' +
        '该操作要求你在最近 10 分钟内验证过密码；否则会先要求重新登录。',
      '注销全部登录会话',
      {
        type: 'warning',
        confirmButtonText: '全部注销',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    return
  }

  revoking.value = true
  try {
    const result: RevokedSessionsVO = await adminApi.revokeAllSessions()
    ElMessage.success(
      `已注销：${result.ssoSessions} 个 SSO 会话、${result.authorizations} 条 OAuth2 授权`,
    )
    // This session is gone too; leave for the login page instead of rendering a wall of 401s.
    window.setTimeout(() => window.location.assign('/login?expired=1'), 1200)
  } catch (e) {
    revoking.value = false
    if (e instanceof ApiError && e.code === 'reauth_required') {
      // Sudo mode: the session is valid but the password entry is too old. A fresh login is the
      // cheapest step-up; `/logout` clears the SSO session and lands on the login page.
      void ElMessageBox.alert(e.message, '需要重新验证', {
        type: 'warning',
        confirmButtonText: '重新登录',
      })
        .then(() => window.location.assign('/logout'))
        .catch(() => {})
      return
    }
    ElMessage.error(e instanceof Error ? e.message : String(e))
  }
}
</script>

<template>
  <AdminPage
    title="会话安全"
    description="全站登录态策略，以及让所有人立即下线的应急开关。"
  >
    <el-card
      shadow="never"
      class="admin-card"
    >
      <template #header>
        <div class="admin-card-header">
          <span class="admin-card-title">当前策略</span>
        </div>
      </template>

      <dl class="policy">
        <div class="policy-row">
          <dt>空闲超时</dt>
          <dd>7 天未活动自动退出</dd>
        </div>
        <div class="policy-row">
          <dt>绝对上限</dt>
          <dd>距最后一次输入密码 30 天，必须重新登录；静默续期不会延长它</dd>
        </div>
        <div class="policy-row">
          <dt>设备绑定</dt>
          <dd>每个会话绑定浏览器指纹；换设备或清空站点数据需要重新登录</dd>
        </div>
        <div class="policy-row">
          <dt>会话存储</dt>
          <dd>Redis 持久化，服务重启不掉线</dd>
        </div>
      </dl>
    </el-card>

    <section class="danger-zone">
      <h2 class="danger-zone-title">
        Danger zone
      </h2>
      <div class="danger-zone-row">
        <span class="danger-zone-label">注销全部登录会话</span>
        <el-button
          type="danger"
          plain
          :loading="revoking"
          @click="revokeAll"
        >
          注销全部会话
        </el-button>
      </div>
    </section>
  </AdminPage>
</template>

<style scoped>
/* Left name / right control, hairline between rows (settings-page convention). */
.policy {
  margin: 0;
}

.policy-row {
  display: flex;
  align-items: baseline;
  gap: 16px;
  padding: 10px 0;
  font-size: 13.5px;
}

.policy-row + .policy-row {
  border-top: 1px solid var(--color-line);
}

.policy-row dt {
  flex: 0 0 96px;
  color: var(--color-dim);
}

.policy-row dd {
  margin: 0;
  color: var(--color-ink);
}

/* Page-bottom destructive area: one independent action, no grouping, details in the dialog. */
.danger-zone {
  border: 1px solid color-mix(in srgb, var(--color-danger) 40%, transparent);
  border-radius: 12px;
  background: color-mix(in srgb, var(--color-danger) 5%, transparent);
  overflow: hidden;
}

.danger-zone-title {
  margin: 0;
  padding: 9px 16px;
  border-bottom: 1px solid color-mix(in srgb, var(--color-danger) 30%, transparent);
  background: color-mix(in srgb, var(--color-danger) 12%, transparent);
  color: var(--color-danger);
  font-size: 12.5px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.danger-zone-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
}

.danger-zone-label {
  font-size: 13.5px;
  font-weight: 500;
  color: var(--color-ink);
}
</style>
