<script setup lang="ts">
/**
 * User center: nickname, password, email and phone management.
 *
 * <p>Every sensitive mutation asks the server for a short-lived re-auth token first. The token is
 * scene-bound and single-use; the new email/phone is only persisted after the code sent to that
 * target is verified.</p>
 */
import { reactive, ref, watch } from 'vue'
import { toast } from '../../shared/ui/toast'
import { useAuth } from '../../auth/useAuth'
import {
  activateEmail,
  activatePhone,
  changeEmail,
  changePhone,
  fetchAccountMe,
  resetPassword,
  setPassword,
  unbindEmail,
  updateNickname,
  verifyChangeEmail,
  verifyChangePhone,
  type AccountMe,
  type ReAuthScene,
} from '../../api/account'
import ReAuthDialog from './ReAuthDialog.vue'
import AppDialog from '../../shared/ui/AppDialog.vue'

const { session, loading: authLoading, login, logout } = useAuth()

const me = ref<AccountMe | null>(null)
const loading = ref(false)
const error = ref('')
const nickname = ref('')
const savingNickname = ref(false)

const reAuthVisible = ref(false)
const reAuthScene = ref<ReAuthScene>('reset_password')
let reAuthAction: ((token: string) => Promise<void> | void) | null = null

const passwordDialog = reactive({
  visible: false,
  mode: 'reset' as 'reset' | 'set',
  token: '',
  newPwd: '',
  confirmPwd: '',
  submitting: false,
})

const targetDialog = reactive({
  visible: false,
  kind: 'change-email' as 'bind-email' | 'change-email' | 'bind-phone' | 'change-phone',
  title: '',
  label: '',
  value: '',
  submitting: false,
})

const codeDialog = reactive({
  visible: false,
  kind: 'email' as 'email' | 'phone',
  title: '',
  verifyKey: '',
  code: '',
  submitting: false,
})

const PASSWORD_RE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,20}$/
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PHONE_RE = /^1[3-9]\d{9}$/

const displayName = () => me.value?.nickname || me.value?.username || session.value?.name || '用户'

watch(() => session.value?.authenticated, (authenticated) => {
  if (authenticated) void load()
  else me.value = null
}, { immediate: true })

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    me.value = await fetchAccountMe()
    nickname.value = me.value.nickname ?? ''
  } catch (cause) {
    error.value = message(cause)
  } finally {
    loading.value = false
  }
}

async function saveNickname(): Promise<void> {
  const value = nickname.value.trim()
  if (!value || value.length > 12) {
    toast.warning('昵称长度需为 1-12 个字符')
    return
  }
  savingNickname.value = true
  try {
    await updateNickname(value)
    if (me.value) me.value = { ...me.value, nickname: value }
    if (session.value) session.value = { ...session.value, name: value }
    toast.success('昵称已更新')
  } catch (cause) {
    toast.error(message(cause))
  } finally {
    savingNickname.value = false
  }
}

function askReAuth(scene: ReAuthScene, action: (token: string) => Promise<void> | void): void {
  reAuthScene.value = scene
  reAuthAction = action
  reAuthVisible.value = true
}

async function onReAuthSuccess(token: string): Promise<void> {
  reAuthVisible.value = false
  const action = reAuthAction
  reAuthAction = null
  if (!action) return
  try {
    await action(token)
  } catch (cause) {
    toast.error(message(cause))
  }
}

function openPassword(): void {
  const mode = me.value?.passwordSet ? 'reset' : 'set'
  askReAuth(mode === 'reset' ? 'reset_password' : 'set_password', (token) => {
    passwordDialog.mode = mode
    passwordDialog.token = token
    passwordDialog.newPwd = ''
    passwordDialog.confirmPwd = ''
    passwordDialog.visible = true
  })
}

async function savePassword(): Promise<void> {
  if (passwordDialog.newPwd !== passwordDialog.confirmPwd) {
    toast.warning('两次输入的密码不一致')
    return
  }
  if (!PASSWORD_RE.test(passwordDialog.newPwd)) {
    toast.warning('密码需 8-20 位，且包含大小写字母和数字')
    return
  }
  passwordDialog.submitting = true
  try {
    if (passwordDialog.mode === 'set') {
      await setPassword(passwordDialog.newPwd, passwordDialog.confirmPwd, passwordDialog.token)
    } else {
      await resetPassword(passwordDialog.newPwd, passwordDialog.confirmPwd, passwordDialog.token)
    }
    passwordDialog.visible = false
    toast.success(passwordDialog.mode === 'set' ? '密码设置成功，请重新登录' : '密码修改成功，请重新登录')
    window.setTimeout(() => logout(), 700)
  } catch (cause) {
    toast.error(message(cause))
  } finally {
    passwordDialog.submitting = false
  }
}

function openEmailTarget(kind: 'bind-email' | 'change-email'): void {
  targetDialog.kind = kind
  targetDialog.title = kind === 'bind-email' ? '绑定邮箱' : '修改邮箱'
  targetDialog.label = '新邮箱地址'
  targetDialog.value = ''
  targetDialog.visible = true
}

function openPhoneTarget(kind: 'bind-phone' | 'change-phone'): void {
  targetDialog.kind = kind
  targetDialog.title = kind === 'bind-phone' ? '绑定手机号' : '修改手机号'
  targetDialog.label = '新手机号'
  targetDialog.value = ''
  targetDialog.visible = true
}

function openEmailActivate(): void {
  askReAuth('activate', async (token) => {
    const result = await activateEmail(token)
    openCodeDialog('邮箱激活验证码', 'email', result.verifyKey)
  })
}

function openPhoneActivate(): void {
  askReAuth('activate', async (token) => {
    const result = await activatePhone(token)
    openCodeDialog('手机号激活验证码', 'phone', result.verifyKey)
  })
}

function openEmailUnbind(): void {
  askReAuth('unbind', async (token) => {
    await unbindEmail(token)
    toast.success('邮箱已解绑')
    await load()
  })
}

async function submitTarget(): Promise<void> {
  const value = targetDialog.value.trim()
  const isEmail = targetDialog.kind.endsWith('email')
  if (isEmail ? !EMAIL_RE.test(value) : !PHONE_RE.test(value)) {
    toast.warning(isEmail ? '请输入有效邮箱地址' : '请输入有效手机号')
    return
  }
  targetDialog.visible = false
  targetDialog.submitting = true
  try {
    askReAuth(isEmail ? 'change_email' : 'change_phone', async (token) => {
      const result = isEmail
        ? await changeEmail(value, token)
        : await changePhone(value, token)
      openCodeDialog(isEmail ? '新邮箱验证码' : '新手机号验证码',
        isEmail ? 'email' : 'phone', result.verifyKey)
    })
  } finally {
    targetDialog.submitting = false
  }
}

function openCodeDialog(title: string, kind: 'email' | 'phone', verifyKey: string): void {
  codeDialog.title = title
  codeDialog.kind = kind
  codeDialog.verifyKey = verifyKey
  codeDialog.code = ''
  codeDialog.visible = true
}

async function submitCode(): Promise<void> {
  const code = codeDialog.code.trim()
  if (code.length < 6) {
    toast.warning('请输入 6 位验证码')
    return
  }
  codeDialog.submitting = true
  try {
    if (codeDialog.kind === 'email') {
      await verifyChangeEmail(codeDialog.verifyKey, code)
    } else {
      await verifyChangePhone(codeDialog.verifyKey, code)
    }
    codeDialog.visible = false
    toast.success(codeDialog.kind === 'email' ? '邮箱已更新' : '手机号已更新')
    await load()
  } catch (cause) {
    toast.error(message(cause))
  } finally {
    codeDialog.submitting = false
  }
}

function message(cause: unknown): string {
  return cause instanceof Error ? cause.message : '操作失败，请稍后重试'
}
</script>

<template>
  <main
    v-if="authLoading"
    class="mx-auto flex min-h-[50vh] max-w-[1120px] items-center justify-center px-4 text-sm text-faint"
  >
    正在检查登录状态…
  </main>

  <main
    v-else-if="!session?.authenticated"
    class="mx-auto flex min-h-[50vh] max-w-[1120px] flex-col items-center justify-center gap-4 px-4 text-center"
  >
    <h1 class="text-xl font-semibold text-ink">
      登录后管理个人账号
    </h1>
    <p class="text-sm text-dim">
      修改昵称、密码、邮箱和手机号都需要登录。
    </p>
    <button
      class="btn btn-primary"
      @click="login('/account')"
    >
      去登录
    </button>
  </main>

  <main
    v-else
    class="mx-auto max-w-[1120px] px-4 py-6 sm:px-6"
  >
    <div class="mb-5">
      <h1 class="text-xl font-semibold tracking-tight text-ink">
        用户中心
      </h1>
      <p class="mt-1 text-sm text-dim">
        管理展示信息与账号安全。敏感操作会先进行短信或邮箱验证。
      </p>
    </div>

    <div class="grid gap-5 md:grid-cols-[230px_minmax(0,1fr)]">
      <aside class="h-fit rounded-[14px] border border-line bg-card p-5">
        <div class="mb-5 flex items-center gap-3">
          <span class="grid size-12 shrink-0 place-items-center rounded-full bg-soft text-lg font-semibold text-dim">
            {{ displayName().slice(0, 1) }}
          </span>
          <div class="min-w-0">
            <p class="truncate text-sm font-semibold text-ink">
              {{ displayName() }}
            </p>
            <p class="truncate text-xs text-faint">
              {{ me?.username || session?.username || '' }}
            </p>
          </div>
        </div>
        <nav class="space-y-1 text-sm text-dim">
          <a
            href="#profile"
            class="block rounded-lg px-2.5 py-2 transition hover:bg-soft hover:text-ink"
          >个人资料</a>
          <a
            href="#security"
            class="block rounded-lg px-2.5 py-2 transition hover:bg-soft hover:text-ink"
          >账号安全</a>
          <a
            href="#contact"
            class="block rounded-lg px-2.5 py-2 transition hover:bg-soft hover:text-ink"
          >邮箱与手机</a>
        </nav>
      </aside>

      <div class="min-w-0 space-y-5">
        <p
          v-if="error"
          class="rounded-lg border border-danger/30 bg-danger/5 px-4 py-3 text-sm text-danger"
        >
          {{ error }}
        </p>
        <p
          v-if="loading"
          class="text-sm text-faint"
        >
          正在加载账号信息…
        </p>

        <template v-if="me">
          <section
            id="profile"
            class="rounded-[14px] border border-line bg-card p-5"
          >
            <div class="mb-4">
              <h2 class="text-base font-semibold text-ink">
                个人资料
              </h2>
              <p class="mt-1 text-xs text-faint">
                昵称会显示在门户和 NitroBot 中。
              </p>
            </div>
            <label class="block text-sm text-dim">
              昵称
              <input
                v-model="nickname"
                class="input mt-1.5 w-full"
                maxlength="12"
                placeholder="请输入昵称"
              >
            </label>
            <div class="mt-4 flex items-center gap-2">
              <button
                class="btn btn-primary btn-sm"
                :disabled="savingNickname"
                @click="saveNickname"
              >
                {{ savingNickname ? '保存中…' : '保存昵称' }}
              </button>
              <span class="text-xs text-faint">1-12 个字符</span>
            </div>
          </section>

          <section
            id="security"
            class="rounded-[14px] border border-line bg-card p-5"
          >
            <div class="mb-4">
              <h2 class="text-base font-semibold text-ink">
                账号安全
              </h2>
              <p class="mt-1 text-xs text-faint">
                修改密码前需要通过已绑定的手机号或邮箱完成一次安全验证。
              </p>
            </div>
            <div class="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-soft/60 px-4 py-3">
              <div>
                <p class="text-sm text-ink">
                  登录密码
                </p>
                <p class="mt-0.5 text-xs text-faint">
                  {{ me.passwordSet ? '已设置密码' : '尚未设置密码' }}
                </p>
              </div>
              <button
                class="btn btn-sm"
                @click="openPassword"
              >
                {{ me.passwordSet ? '修改密码' : '设置密码' }}
              </button>
            </div>
          </section>

          <section
            id="contact"
            class="rounded-[14px] border border-line bg-card p-5"
          >
            <div class="mb-4">
              <h2 class="text-base font-semibold text-ink">
                邮箱与手机
              </h2>
              <p class="mt-1 text-xs text-faint">
                修改绑定需要验证新地址的所有权；未验证的绑定不会被当作有效联系方式。
              </p>
            </div>

            <div class="space-y-3">
              <div class="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-soft/60 px-4 py-3">
                <div class="min-w-0">
                  <p class="text-sm text-ink">
                    邮箱
                  </p>
                  <p class="mt-0.5 flex flex-wrap items-center gap-2 text-xs text-faint">
                    <span>{{ me.emailMasked || '未绑定' }}</span>
                    <span
                      v-if="me.emailMasked"
                      class="rounded-full px-1.5 py-0.5 text-[10px]"
                      :class="me.emailVerified ? 'bg-ok/10 text-ok' : 'bg-warn/10 text-warn'"
                    >
                      {{ me.emailVerified ? '已验证' : '未验证' }}
                    </span>
                  </p>
                </div>
                <div class="flex flex-wrap gap-2">
                  <button
                    v-if="!me.emailMasked"
                    class="btn btn-primary btn-sm"
                    @click="openEmailTarget('bind-email')"
                  >
                    绑定邮箱
                  </button>
                  <button
                    v-else-if="!me.emailVerified"
                    class="btn btn-primary btn-sm"
                    @click="openEmailActivate"
                  >
                    激活邮箱
                  </button>
                  <button
                    v-if="me.emailMasked"
                    class="btn btn-sm"
                    @click="openEmailTarget('change-email')"
                  >
                    修改邮箱
                  </button>
                  <button
                    v-if="me.emailMasked && me.emailVerified"
                    class="btn btn-sm"
                    @click="openEmailUnbind"
                  >
                    解绑邮箱
                  </button>
                </div>
              </div>

              <div class="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-soft/60 px-4 py-3">
                <div class="min-w-0">
                  <p class="text-sm text-ink">
                    手机号
                  </p>
                  <p class="mt-0.5 flex flex-wrap items-center gap-2 text-xs text-faint">
                    <span>{{ me.phoneMasked || '未绑定' }}</span>
                    <span
                      v-if="me.phoneMasked"
                      class="rounded-full px-1.5 py-0.5 text-[10px]"
                      :class="me.phoneVerified ? 'bg-ok/10 text-ok' : 'bg-warn/10 text-warn'"
                    >
                      {{ me.phoneVerified ? '已验证' : '未验证' }}
                    </span>
                  </p>
                </div>
                <div class="flex flex-wrap gap-2">
                  <button
                    v-if="!me.phoneMasked"
                    class="btn btn-primary btn-sm"
                    @click="openPhoneTarget('bind-phone')"
                  >
                    绑定手机号
                  </button>
                  <button
                    v-else-if="!me.phoneVerified"
                    class="btn btn-primary btn-sm"
                    @click="openPhoneActivate"
                  >
                    激活手机号
                  </button>
                  <button
                    v-if="me.phoneMasked"
                    class="btn btn-sm"
                    @click="openPhoneTarget('change-phone')"
                  >
                    修改手机号
                  </button>
                </div>
              </div>
            </div>
          </section>
        </template>
      </div>
    </div>

    <ReAuthDialog
      :visible="reAuthVisible"
      :scene="reAuthScene"
      @update:visible="reAuthVisible = $event"
      @success="onReAuthSuccess"
    />

    <AppDialog
      v-model="targetDialog.visible"
      :title="targetDialog.title"
      width="420px"
      confirm-text="下一步"
      :confirm-loading="targetDialog.submitting"
      :close-on-overlay="false"
      @confirm="submitTarget"
    >
      <label class="block text-sm text-dim">
        {{ targetDialog.label }}
        <input
          v-model="targetDialog.value"
          class="input mt-1.5 w-full"
          :type="targetDialog.kind.endsWith('email') ? 'email' : 'tel'"
          :placeholder="targetDialog.kind.endsWith('email') ? 'name@example.com' : '11 位手机号'"
          @keyup.enter="submitTarget"
        >
      </label>
    </AppDialog>

    <AppDialog
      v-model="codeDialog.visible"
      :title="codeDialog.title"
      width="400px"
      :close-on-overlay="false"
      :confirm-loading="codeDialog.submitting"
      @confirm="submitCode"
    >
      <label class="block text-sm text-dim">
        6 位验证码
        <input
          v-model="codeDialog.code"
          class="input mt-1.5 w-full text-center tracking-[0.3em]"
          maxlength="6"
          inputmode="numeric"
          placeholder="000000"
          @keyup.enter="submitCode"
        >
      </label>
    </AppDialog>

    <AppDialog
      v-model="passwordDialog.visible"
      :title="passwordDialog.mode === 'set' ? '设置密码' : '修改密码'"
      width="420px"
      :close-on-overlay="false"
      :confirm-loading="passwordDialog.submitting"
      @confirm="savePassword"
    >
      <div class="space-y-3">
        <label class="block text-sm text-dim">
          新密码
          <input
            v-model="passwordDialog.newPwd"
            class="input mt-1.5 w-full"
            type="password"
            autocomplete="new-password"
            placeholder="8-20 位，含大小写字母和数字"
          >
        </label>
        <label class="block text-sm text-dim">
          确认新密码
          <input
            v-model="passwordDialog.confirmPwd"
            class="input mt-1.5 w-full"
            type="password"
            autocomplete="new-password"
            placeholder="再次输入新密码"
            @keyup.enter="savePassword"
          >
        </label>
        <p class="text-xs text-faint">
          修改成功后当前所有会话将失效，需要重新登录。
        </p>
      </div>
    </AppDialog>
  </main>
</template>
