<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { ElButton, ElIcon, ElInput } from 'element-plus'
import { ArrowDown, Iphone, Lock, Message, User } from '@element-plus/icons-vue'

import AuthLayout from '@/components/AuthLayout.vue'
import CaptchaDialog from '@/components/CaptchaDialog.vue'
import { ApiError, postJson } from '@/api/auth'
import { getDeviceFp } from '@/composables/useDeviceFp'
import { useCodeCooldown, type CooldownScope } from '@/composables/useCodeCooldown'

const username = ref('')
const phone = ref('')
const smsCode = ref('')
const password = ref('')
const email = ref('')
const emailCode = ref('')

const showOptional = ref(false)
const errorMessage = ref('')
const notice = ref('')

const showCaptcha = ref(false)
const captchaCode = ref('')
const sending = ref(false)
const submitting = ref(false)
const emailSending = ref(false)
const showEmailCaptcha = ref(false)

/** Targets the last SMS/email code was sent to; editing the target invalidates the stale input. */
const smsSentTo = ref('')
const emailSentTo = ref('')

let redirectTimer: ReturnType<typeof setTimeout> | null = null

const {
  remaining: smsCooldown,
  start: startSmsCooldown,
  restore: restoreSmsCooldown,
  clear: clearSmsCooldown,
  sync: syncSmsCooldown,
} = useCodeCooldown()
const {
  remaining: emailCooldown,
  start: startEmailCooldown,
  restore: restoreEmailCooldown,
  clear: clearEmailCooldown,
  sync: syncEmailCooldown,
} = useCodeCooldown()

function smsScope(): CooldownScope {
  return { scene: 'register', channel: 'sms', target: phone.value.trim() }
}

function emailScope(): CooldownScope {
  return { scene: 'register', channel: 'email', target: email.value.trim() }
}

watch(phone, () => {
  const target = phone.value.trim()
  if (smsSentTo.value && smsSentTo.value !== target) {
    smsSentTo.value = ''
    smsCode.value = ''
  }
  if (target) restoreSmsCooldown(smsScope())
  else clearSmsCooldown()
})
watch(email, () => {
  const target = email.value.trim()
  if (emailSentTo.value && emailSentTo.value !== target) {
    emailSentTo.value = ''
    emailCode.value = ''
  }
  if (target) restoreEmailCooldown(emailScope())
  else clearEmailCooldown()
})

onMounted(() => {
  if (phone.value.trim()) restoreSmsCooldown(smsScope())
  if (email.value.trim()) restoreEmailCooldown(emailScope())
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
  if (!phone.value.trim()) {
    errorMessage.value = '请先填写手机号'
    return
  }
  clearMessages()
  showCaptcha.value = true
}

async function sendCode() {
  const target = phone.value.trim()
  if (!target) {
    errorMessage.value = '请先填写手机号'
    return
  }

  sending.value = true
  clearMessages()
  try {
    await postJson<void>('/api/auth/send-code', {
      target,
      channel: 'sms',
      scene: 'register',
      captcha: captchaCode.value.trim(),
    })
    notice.value = '验证码已发送，请留意手机短信'
    smsSentTo.value = target
    startSmsCooldown(60, smsScope())
  } catch (error) {
    errorMessage.value = messageOf(error)
    syncSmsCooldown(error, smsScope())
  } finally {
    sending.value = false
  }
}

/** The dialog owns the code input; take the confirmed value and send the SMS in one go. */
function sendCodeWithCaptcha(code: string) {
  captchaCode.value = code
  showCaptcha.value = false
  void sendCode()
}

function openEmailCaptcha() {
  const mail = email.value.trim()
  if (!mail) {
    errorMessage.value = '请先填写邮箱'
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(mail)) {
    errorMessage.value = '邮箱格式不正确'
    return
  }
  clearMessages()
  showEmailCaptcha.value = true
}

async function sendEmailCode(captcha: string) {
  const mail = email.value.trim()
  if (!mail) {
    errorMessage.value = '请先填写邮箱'
    return
  }

  emailSending.value = true
  clearMessages()
  try {
    await postJson<void>('/api/auth/send-code', {
      target: mail,
      channel: 'email',
      scene: 'register',
      captcha,
    })
    notice.value = '邮箱验证码已发送，请查收'
    emailSentTo.value = mail
    startEmailCooldown(60, emailScope())
  } catch (error) {
    errorMessage.value = messageOf(error)
    syncEmailCooldown(error, emailScope())
  } finally {
    emailSending.value = false
  }
}

function sendEmailCodeWithCaptcha(code: string) {
  showEmailCaptcha.value = false
  void sendEmailCode(code)
}

async function register() {
  clearMessages()

  const name = username.value.trim()
  const target = phone.value.trim()
  const code = smsCode.value.trim()
  const pwd = password.value
  const mail = email.value.trim()

  if (!name || !target || !code) {
    errorMessage.value = '请填写用户名、手机号和短信验证码'
    return
  }
  if (!/^[0-9a-zA-Z_]{4,20}$/.test(name)) {
    errorMessage.value = '用户名需为 4-20 位字母、数字或下划线'
    return
  }
  if (/^1[3-9]\d{9}$/.test(name)) {
    errorMessage.value = '用户名不能是手机号'
    return
  }
  if (mail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(mail)) {
    errorMessage.value = '邮箱格式不正确'
    return
  }
  if (mail && !emailCode.value.trim()) {
    errorMessage.value = '请填写邮箱验证码'
    return
  }
  if (pwd && !/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,20}$/.test(pwd)) {
    errorMessage.value = '密码需为 8-20 位，且包含大小写字母和数字'
    return
  }

  submitting.value = true
  try {
    await postJson<void>('/api/auth/register', {
      username: name,
      phone: target,
      password: pwd || null,
      email: mail || null,
      verify: {
        channel: 'sms',
        target,
        code,
        scene: 'register',
        deviceInfo: { deviceFp: getDeviceFp() },
      },
      emailVerify: mail
        ? {
            channel: 'email',
            target: mail,
            code: emailCode.value.trim(),
            scene: 'register',
            deviceInfo: { deviceFp: getDeviceFp() },
          }
        : null,
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
          for="reg-phone"
        >手机号</label>
        <ElInput
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
          for="reg-sms"
        >短信验证码</label>
        <div class="flex items-center gap-2">
          <ElInput
            id="reg-sms"
            v-model="smsCode"
            class="min-w-0 flex-1"
            size="large"
            :prefix-icon="Message"
            autocomplete="one-time-code"
            inputmode="numeric"
            maxlength="6"
            placeholder="请输入短信验证码"
            required
          />
          <ElButton
            class="shrink-0"
            size="large"
            :disabled="sending || smsCooldown > 0"
            @click="openCaptcha"
          >
            {{ smsCooldown > 0 ? `${smsCooldown}s 后重发` : '获取验证码' }}
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

          <div>
            <label
              class="auth-label"
              for="reg-email"
            >邮箱</label>
            <ElInput
              id="reg-email"
              v-model="email"
              size="large"
              type="email"
              :prefix-icon="Message"
              autocomplete="email"
              placeholder="填写后需验证，可用于快捷登录"
            />
          </div>

          <div v-if="email.trim()">
            <label
              class="auth-label"
              for="reg-email-code"
            >邮箱验证码</label>
            <div class="flex items-center gap-2">
              <ElInput
                id="reg-email-code"
                v-model="emailCode"
                class="min-w-0 flex-1"
                size="large"
                :prefix-icon="Message"
                autocomplete="one-time-code"
                inputmode="numeric"
                maxlength="6"
                placeholder="请输入邮箱验证码"
              />
              <ElButton
                class="shrink-0"
                size="large"
                :disabled="emailSending || emailCooldown > 0"
                @click="openEmailCaptcha"
              >
                {{ emailCooldown > 0 ? `${emailCooldown}s 后重发` : '获取验证码' }}
              </ElButton>
            </div>
            <p class="mt-1.5 text-[11px] text-faint">
              邮箱验证通过后可用于验证码快捷登录
            </p>
          </div>
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
      description="输入图形验证码后发送短信验证码。"
      confirm-text="发送验证码"
      :loading="sending"
      @submit="sendCodeWithCaptcha"
    />

    <CaptchaDialog
      v-model="showEmailCaptcha"
      title="安全验证"
      description="输入图形验证码后发送邮箱验证码。"
      confirm-text="发送验证码"
      :loading="emailSending"
      @submit="sendEmailCodeWithCaptcha"
    />
  </AuthLayout>
</template>
