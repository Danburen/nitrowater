<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElButton, ElCheckbox, ElIcon, ElInput } from 'element-plus'
import { ArrowDown, Iphone, Lock, Message, User } from '@element-plus/icons-vue'

import AuthLayout from '@/components/AuthLayout.vue'
import CaptchaDialog from '@/components/CaptchaDialog.vue'
import LegalDocList from '@/components/LegalDocList.vue'
import { ApiError, fetchLoginContext, postJson, type LoginContext } from '@/api/auth'
import { getDeviceFp } from '@/composables/useDeviceFp'
import { useCodeCooldown, type CooldownScope } from '@/composables/useCodeCooldown'

type RegisterChannel = 'sms' | 'email'

const channelOptions = [
  { value: 'sms', label: '手机号注册' },
  { value: 'email', label: '邮箱注册' },
] as const satisfies ReadonlyArray<{ value: RegisterChannel; label: string }>

const PHONE_RE = /^1[3-9]\d{9}$/
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** The app that started this registration (null for a direct visit): its legal links apply. */
const loginContext = ref<LoginContext | null>(null)

/** Documents registered by that app; empty means the consent line stays plain text. */
const legalLinks = computed(() => loginContext.value?.legalLinks ?? [])

const channel = ref<RegisterChannel>('sms')
const username = ref('')
const phone = ref('')
const email = ref('')
const verifyCode = ref('')
const password = ref('')

const isEmail = computed(() => channel.value === 'email')
const target = computed(() => (isEmail.value ? email.value.trim() : phone.value.trim()))
const targetLabel = computed(() => (isEmail.value ? '邮箱' : '手机号'))
const codeLabel = computed(() => (isEmail.value ? '邮箱验证码' : '短信验证码'))

// Handoff from the login page ("no account for this target"): carry the value over so it does not
// have to be retyped. Anything that is not a valid phone/email is ignored rather than pre-filled.
const route = useRoute()
const handoffPhone = route.query.phone
const initialPhone = typeof handoffPhone === 'string' ? handoffPhone.trim() : ''
const handoffEmail = route.query.email
const initialEmail = typeof handoffEmail === 'string' ? handoffEmail.trim() : ''
if (PHONE_RE.test(initialPhone)) {
  phone.value = initialPhone
} else if (EMAIL_RE.test(initialEmail)) {
  email.value = initialEmail
  channel.value = 'email'
}

const showOptional = ref(false)
const errorMessage = ref('')
const notice = ref('')
/** Consent is mandatory and mirrored server-side (RegisterRequest#agreed). */
const agreed = ref(false)

const showCaptcha = ref(false)
const captchaCode = ref('')
const sending = ref(false)
const submitting = ref(false)

/** Target the last code was sent to; editing the target or channel invalidates the stale input. */
const codeSentTo = ref('')

let redirectTimer: ReturnType<typeof setTimeout> | null = null

const {
  remaining: codeCooldown,
  start: startCodeCooldown,
  restore: restoreCodeCooldown,
  clear: clearCodeCooldown,
  sync: syncCodeCooldown,
} = useCodeCooldown()

function codeScope(): CooldownScope {
  return { scene: 'register', channel: channel.value, target: target.value }
}

function validTarget(): boolean {
  return isEmail.value ? EMAIL_RE.test(target.value) : PHONE_RE.test(target.value)
}

watch([channel, phone, email], () => {
  const current = target.value
  if (codeSentTo.value && codeSentTo.value !== current) {
    codeSentTo.value = ''
    verifyCode.value = ''
  }
  if (current) restoreCodeCooldown(codeScope())
  else clearCodeCooldown()
})

onMounted(() => {
  if (target.value) restoreCodeCooldown(codeScope())
  void fetchLoginContext()
    .then((context) => {
      loginContext.value = context
    })
    .catch(() => {
      // Links are a nicety; a failure must never block the form.
    })
})

function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return '请求失败，请稍后重试'
}

function clearMessages() {
  errorMessage.value = ''
  notice.value = ''
}

function openCaptcha() {
  if (!target.value) {
    errorMessage.value = `请先填写${targetLabel.value}`
    return
  }
  if (!validTarget()) {
    errorMessage.value = isEmail.value ? '请输入有效邮箱地址' : '请输入正确手机号'
    return
  }
  clearMessages()
  showCaptcha.value = true
}

async function sendCode() {
  const value = target.value
  if (!value || !validTarget()) {
    errorMessage.value = isEmail.value ? '请输入有效邮箱地址' : '请输入正确手机号'
    return
  }

  sending.value = true
  clearMessages()
  try {
    await postJson<void>('/api/auth/send-code', {
      target: value,
      channel: channel.value,
      scene: 'register',
      captcha: captchaCode.value.trim(),
    })
    notice.value = isEmail.value ? '验证码已发送，请留意邮箱' : '验证码已发送，请留意手机短信'
    codeSentTo.value = value
    startCodeCooldown(60, codeScope())
  } catch (error) {
    errorMessage.value = messageOf(error)
    syncCodeCooldown(error, codeScope())
  } finally {
    sending.value = false
  }
}

/** The dialog owns the captcha input; take the confirmed value and send the code in one go. */
function sendCodeWithCaptcha(code: string) {
  captchaCode.value = code
  showCaptcha.value = false
  void sendCode()
}

async function register() {
  clearMessages()

  const name = username.value.trim()
  const value = target.value
  const code = verifyCode.value.trim()
  const pwd = password.value

  if (!name || !value || !code) {
    errorMessage.value = `请填写用户名、${targetLabel.value}和${codeLabel.value}`
    return
  }
  if (!/^[0-9a-zA-Z_]{4,20}$/.test(name)) {
    errorMessage.value = '用户名需为 4-20 位字母、数字或下划线'
    return
  }
  if (PHONE_RE.test(name)) {
    errorMessage.value = '用户名不能是手机号'
    return
  }
  if (!validTarget()) {
    errorMessage.value = isEmail.value ? '请输入有效邮箱地址' : '请输入正确手机号'
    return
  }
  if (pwd && !/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,20}$/.test(pwd)) {
    errorMessage.value = '密码需为 8-20 位，且包含大小写字母和数字'
    return
  }
  if (!agreed.value) {
    errorMessage.value = '请先阅读并同意相关条款'
    return
  }

  submitting.value = true
  try {
    await postJson<void>('/api/auth/register', {
      username: name,
      // Exactly one identifier is sent; the server cross-checks it against the proof below.
      ...(isEmail.value ? { email: value } : { phone: value }),
      password: pwd || null,
      // Server-side @AssertTrue: the checkbox is not decoration, the request must carry it.
      agreed: agreed.value,
      verify: {
        channel: channel.value,
        target: value,
        code,
        scene: 'register',
        deviceInfo: { deviceFp: getDeviceFp() },
      },
    })
    notice.value = '注册成功，正在前往登录…'
    redirectTimer = setTimeout(() => window.location.assign('/login?registered=1'), 800)
  } catch (error) {
    errorMessage.value = messageOf(error)
    submitting.value = false
  }
}

onUnmounted(() => {
  if (redirectTimer) clearTimeout(redirectTimer)
})
</script>

<template>
  <AuthLayout
    title="创建账号"
    subtitle="注册后即可登录 NitroWater 及其接入应用。"
  >
    <div
      v-if="notice"
      class="auth-alert-success mb-5"
      role="status"
    >
      {{ notice }}
    </div>

    <div
      v-if="errorMessage"
      class="auth-alert-error mb-5"
      role="alert"
    >
      {{ errorMessage }}
    </div>

    <form
      class="space-y-4"
      @submit.prevent="register"
    >
      <div
        class="flex gap-1 rounded-lg bg-soft/70 p-1"
        role="tablist"
        aria-label="注册方式"
      >
        <button
          v-for="option in channelOptions"
          :key="option.value"
          type="button"
          role="tab"
          class="flex-1 rounded-md px-3 py-1.5 text-sm transition"
          :class="channel === option.value ? 'bg-card text-ink shadow-sm' : 'text-dim hover:text-ink'"
          :aria-selected="channel === option.value"
          @click="channel = option.value"
        >
          {{ option.label }}
        </button>
      </div>

      <div>
        <label
          class="auth-label"
          for="reg-username"
        >用户名</label>
        <ElInput
          id="reg-username"
          v-model="username"
          size="large"
          :prefix-icon="User"
          autocomplete="username"
          placeholder="4-20 位字母、数字或下划线"
          maxlength="20"
          required
        />
      </div>

      <div>
        <label
          class="auth-label"
          :for="isEmail ? 'reg-email' : 'reg-phone'"
        >{{ targetLabel }}</label>
        <ElInput
          v-if="isEmail"
          id="reg-email"
          v-model="email"
          size="large"
          :prefix-icon="Message"
          autocomplete="email"
          inputmode="email"
          placeholder="用于接收邮箱验证码"
          maxlength="254"
          required
        />
        <ElInput
          v-else
          id="reg-phone"
          v-model="phone"
          size="large"
          :prefix-icon="Iphone"
          autocomplete="tel"
          inputmode="tel"
          placeholder="用于接收短信验证码"
          required
        />
      </div>

      <div>
        <label
          class="auth-label"
          for="reg-code"
        >{{ codeLabel }}</label>
        <div class="flex items-center gap-2">
          <ElInput
            id="reg-code"
            v-model="verifyCode"
            class="min-w-0 flex-1"
            size="large"
            :prefix-icon="Message"
            autocomplete="one-time-code"
            inputmode="numeric"
            maxlength="6"
            :placeholder="`请输入${codeLabel}`"
            required
          />
          <ElButton
            class="shrink-0"
            size="large"
            :disabled="sending || codeCooldown > 0"
            @click="openCaptcha"
          >
            {{ codeCooldown > 0 ? `${codeCooldown}s 后重发` : '获取验证码' }}
          </ElButton>
        </div>
      </div>

      <div class="overflow-hidden rounded-lg border border-line">
        <button
          type="button"
          class="flex w-full items-center justify-between gap-3 px-3.5 py-2.5 text-left text-[13px] text-dim transition hover:text-ink focus:outline-none"
          :aria-expanded="showOptional"
          @click="showOptional = !showOptional"
        >
          <span>补充信息（选填）</span>
          <ElIcon
            class="text-xs text-faint transition-transform"
            :class="showOptional ? 'rotate-180' : ''"
          >
            <ArrowDown />
          </ElIcon>
        </button>

        <div
          v-if="showOptional"
          class="space-y-4 border-t border-line px-3.5 py-4"
        >
          <div>
            <label
              class="auth-label"
              for="reg-password"
            >密码</label>
            <ElInput
              id="reg-password"
              v-model="password"
              size="large"
              type="password"
              show-password
              :prefix-icon="Lock"
              autocomplete="new-password"
              placeholder="8-20 位，含大小写字母和数字"
            />
            <p class="mt-1.5 text-[11px] text-faint">
              设置后可使用用户名 + 密码登录
            </p>
          </div>
        </div>
      </div>

      <p class="text-[11px] leading-relaxed text-faint">
        手机号或邮箱任选一项注册并验证。另一项可在登录后的「用户中心 → 邮箱与手机」绑定，验证通过后即可用于对应的验证码登录。
      </p>

      <div class="flex items-start gap-2 text-[12px] leading-5 text-dim">
        <ElCheckbox
          v-model="agreed"
          class="mt-[1px] shrink-0"
          aria-label="我已阅读并同意相关条款"
        />
        <div class="min-w-0">
          <p v-if="legalLinks.length">
            我已阅读并同意<LegalDocList :links="legalLinks" />，并同意本站按该政策处理我的个人信息。
          </p>
          <p v-else>
            我已阅读并同意本服务的服务条款与隐私政策，并同意本站按该政策处理我的个人信息。
          </p>
        </div>
      </div>

      <ElButton
        class="mt-2 w-full"
        size="large"
        type="primary"
        native-type="submit"
        :loading="submitting"
      >
        {{ submitting ? '正在创建…' : '创建账号' }}
      </ElButton>
    </form>

    <div class="mt-5 flex items-center justify-between text-sm">
      <span class="text-faint">已经有账号？</span>
      <RouterLink
        to="/login"
        class="auth-link"
      >
        返回登录
      </RouterLink>
    </div>

    <CaptchaDialog
      v-model="showCaptcha"
      title="安全验证"
      :description="`输入图形验证码后发送${codeLabel}。`"
      confirm-text="发送验证码"
      :loading="sending"
      @submit="sendCodeWithCaptcha"
    />
  </AuthLayout>
</template>
