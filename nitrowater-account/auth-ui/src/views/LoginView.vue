<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElButton, ElInput } from 'element-plus'
import { Lock, Message, User } from '@element-plus/icons-vue'

import AuthLayout from '@/components/AuthLayout.vue'
import CaptchaDialog from '@/components/CaptchaDialog.vue'
import {
  ApiError,
  fetchLoginContext,
  loginByCode,
  sendLoginCode,
  type LoginContext,
} from '@/api/auth'
import { getDeviceFp } from '@/composables/useDeviceFp'
import { useCodeCooldown, type CooldownScope } from '@/composables/useCodeCooldown'

const route = useRoute()

const ERROR_MESSAGES: Record<string, string> = {
  captcha: '验证码不正确，请重新输入',
  credentials: '用户名或密码不正确',
  disabled: '账号已被禁用，请联系管理员',
  locked: '尝试次数过多，请稍后再试',
  daily: '今日尝试次数过多，请明天再试',
  server: '登录服务暂时不可用，请稍后再试',
}

const errorCode = computed(() => {
  const raw = route.query.error
  return typeof raw === 'string' ? raw : ''
})
const errorMessage = computed(() =>
  errorCode.value ? (ERROR_MESSAGES[errorCode.value] ?? '登录失败，请稍后再试') : '',
)
const registered = computed(() => route.query.registered === '1')
const redirect = computed(() => {
  const raw = route.query.redirect
  return typeof raw === 'string' ? raw : ''
})

/** The app that started this login (null for a direct visit to /login). */
const loginContext = ref<LoginContext | null>(null)

const formRef = ref<HTMLFormElement | null>(null)
const username = ref('')
const password = ref('')
const deviceFp = ref('')
const captchaCode = ref('')
const showCaptcha = ref(false)
const submitting = ref(false)

type LoginMode = 'password' | 'code'
const mode = ref<LoginMode>('password')

const codeIdentifier = ref('')
const loginCode = ref('')
const codeError = ref('')
const codeNotice = ref('')
const sendingCode = ref(false)
const codeSubmitting = ref(false)
const showCodeCaptcha = ref(false)
/** Target the last code was sent to; changing the identifier invalidates the input + cookie. */
const codeSentTo = ref('')
const {
  remaining: codeCooldown,
  start: startCodeCooldown,
  restore: restoreCodeCooldown,
  clear: clearCodeCooldown,
  sync: syncCodeCooldown,
} = useCodeCooldown()

function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return '请求失败，请稍后重试'
}

/** Mirrors the server's format-based channel detection. */
function channelOf(identifier: string): 'sms' | 'email' | null {
  const value = identifier.trim()
  if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return 'email'
  if (/^1[3-9]\d{9}$/.test(value)) return 'sms'
  return null
}

function switchMode(next: LoginMode) {
  mode.value = next
  codeError.value = ''
  codeNotice.value = ''
}

/** Cooldown scope: the backend keys its 1-minute window by channel + normalized target. */
function codeScope(): CooldownScope {
  const target = codeIdentifier.value.trim()
  return { scene: 'login', channel: channelOf(target) ?? 'sms', target }
}

watch(codeIdentifier, () => {
  const target = codeIdentifier.value.trim()
  if (codeSentTo.value && codeSentTo.value !== target) {
    // The previous *_CODE_KEY cookie belongs to the old target; clear the stale input so the user
    // does not submit a code that can never verify against the new identifier.
    codeSentTo.value = ''
    loginCode.value = ''
    codeNotice.value = ''
    codeError.value = ''
  }
  if (target && channelOf(target)) {
    restoreCodeCooldown(codeScope())
  } else {
    clearCodeCooldown()
  }
})

function openCodeCaptcha() {
  const target = codeIdentifier.value.trim()
  if (!target) {
    codeError.value = '请先填写手机号或邮箱'
    return
  }
  if (!channelOf(target)) {
    codeError.value = '请输入正确的手机号或邮箱'
    return
  }
  codeError.value = ''
  codeNotice.value = ''
  showCodeCaptcha.value = true
}

async function sendCodeWithCaptcha(captcha: string) {
  const target = codeIdentifier.value.trim()
  const channel = channelOf(target)
  if (!channel) {
    codeError.value = '请输入正确的手机号或邮箱'
    return
  }
  showCodeCaptcha.value = false
  sendingCode.value = true
  codeError.value = ''
  codeNotice.value = ''
  try {
    await sendLoginCode(target, channel, captcha)
    codeSentTo.value = target
    codeNotice.value = '验证码已发送，请留意短信或邮箱'
    startCodeCooldown(60, codeScope())
  } catch (error) {
    codeError.value = messageOf(error)
    syncCodeCooldown(error, codeScope())
  } finally {
    sendingCode.value = false
  }
}

async function submitCodeLogin() {
  const target = codeIdentifier.value.trim()
  const code = loginCode.value.trim()
  if (!target || !code) {
    codeError.value = '请填写手机号/邮箱和验证码'
    return
  }
  if (!channelOf(target)) {
    codeError.value = '请输入正确的手机号或邮箱'
    return
  }
  codeSubmitting.value = true
  codeError.value = ''
  try {
    const result = await loginByCode({
      identifier: target,
      code,
      deviceFp: deviceFp.value,
      redirect: redirect.value,
    })
    window.location.assign(result.redirectUrl)
  } catch (error) {
    codeError.value = messageOf(error)
    codeSubmitting.value = false
  }
}

/**
 * The captcha dialog owns its input; copy the confirmed code into the hidden field and let Vue
 * flush it before the native form POST (a same-tick submit would send the previous value).
 */
async function submitWithCaptcha(code: string) {
  captchaCode.value = code
  showCaptcha.value = false
  submitting.value = true
  await nextTick()
  formRef.value?.requestSubmit()
}

function onSubmit(event: Event) {
  if (captchaCode.value.trim()) {
    submitting.value = true
    return
  }
  event.preventDefault()
  if (formRef.value && !formRef.value.reportValidity()) return
  showCaptcha.value = true
}

onMounted(() => {
  deviceFp.value = getDeviceFp()
  void fetchLoginContext()
    .then((context) => {
      loginContext.value = context
    })
    .catch(() => {
      // Context is a nicety; a failure must never block the form.
    })
})
</script>

<template>
  <AuthLayout
    title="登录"
    subtitle="使用 NitroWater 账号继续。"
  >
    <div
      v-if="loginContext?.clientName"
      class="mb-5 rounded-lg border border-line bg-soft px-3 py-2.5 text-[13px] leading-5 text-dim"
      role="status"
    >
      您正在尝试登录「<strong class="font-medium text-ink">{{ loginContext.clientName }}</strong>」
    </div>

    <div
      v-if="registered"
      class="auth-alert-success mb-5"
      role="status"
    >
      账号创建成功，请使用新账号登录。
    </div>

    <div
      v-if="errorMessage"
      class="auth-alert-error mb-5"
      role="alert"
    >
      {{ errorMessage }}
    </div>

    <div class="mb-5 flex rounded-lg border border-line p-1 text-sm">
      <button
        type="button"
        class="flex-1 rounded-md px-3 py-2 font-medium transition"
        :class="mode === 'password' ? 'bg-accent text-white' : 'text-dim hover:text-ink'"
        @click="switchMode('password')"
      >
        密码登录
      </button>
      <button
        type="button"
        class="flex-1 rounded-md px-3 py-2 font-medium transition"
        :class="mode === 'code' ? 'bg-accent text-white' : 'text-dim hover:text-ink'"
        @click="switchMode('code')"
      >
        验证码登录
      </button>
    </div>

    <form
      v-if="mode === 'password'"
      ref="formRef"
      method="post"
      action="/login"
      class="space-y-4"
      @submit="onSubmit"
    >
      <input
        type="hidden"
        name="deviceFp"
        :value="deviceFp"
      >
      <input
        type="hidden"
        name="redirect"
        :value="redirect"
      >
      <input
        type="hidden"
        name="captcha"
        :value="captchaCode"
      >

      <div>
        <label
          class="auth-label"
          for="username"
        >用户名 / 邮箱 / 手机号</label>
        <ElInput
          id="username"
          v-model="username"
          name="username"
          size="large"
          :prefix-icon="User"
          autocomplete="username"
          placeholder="请输入账号"
          required
        />
      </div>

      <div>
        <label
          class="auth-label"
          for="password"
        >密码</label>
        <ElInput
          id="password"
          v-model="password"
          name="password"
          size="large"
          type="password"
          show-password
          :prefix-icon="Lock"
          autocomplete="current-password"
          placeholder="请输入密码"
          required
        />
      </div>

      <ElButton
        class="mt-2 w-full"
        size="large"
        type="primary"
        native-type="submit"
        :loading="submitting"
      >
        {{ submitting ? '正在登录…' : '登录' }}
      </ElButton>
    </form>

    <form
      v-else
      class="space-y-4"
      @submit.prevent="submitCodeLogin"
    >
      <div
        v-if="codeNotice"
        class="auth-alert-success"
        role="status"
      >
        {{ codeNotice }}
      </div>

      <div
        v-if="codeError"
        class="auth-alert-error"
        role="alert"
      >
        {{ codeError }}
      </div>

      <div>
        <label
          class="auth-label"
          for="code-identifier"
        >手机号 / 邮箱</label>
        <ElInput
          id="code-identifier"
          v-model="codeIdentifier"
          size="large"
          :prefix-icon="User"
          autocomplete="username"
          placeholder="输入手机号或邮箱"
        />
      </div>

      <div>
        <label
          class="auth-label"
          for="login-code"
        >验证码</label>
        <div class="flex items-center gap-2">
          <ElInput
            id="login-code"
            v-model="loginCode"
            class="min-w-0 flex-1"
            size="large"
            :prefix-icon="Message"
            autocomplete="one-time-code"
            inputmode="numeric"
            maxlength="6"
            placeholder="6 位验证码"
          />
          <ElButton
            class="shrink-0"
            size="large"
            :disabled="sendingCode || codeCooldown > 0"
            @click="openCodeCaptcha"
          >
            {{ codeCooldown > 0 ? `${codeCooldown}s 后重发` : '获取验证码' }}
          </ElButton>
        </div>
      </div>

      <ElButton
        class="mt-2 w-full"
        size="large"
        type="primary"
        native-type="submit"
        :loading="codeSubmitting"
      >
        {{ codeSubmitting ? '正在登录…' : '登录' }}
      </ElButton>
    </form>

    <div class="mt-5 flex items-center justify-between text-sm">
      <span class="text-faint">首次使用 NitroWater？</span>
      <RouterLink
        to="/register"
        class="auth-link"
      >
        创建账号
      </RouterLink>
    </div>

    <CaptchaDialog
      v-model="showCaptcha"
      title="安全验证"
      description="请输入图形验证码后继续登录。"
      confirm-text="继续登录"
      @submit="submitWithCaptcha"
    />

    <CaptchaDialog
      v-model="showCodeCaptcha"
      title="安全验证"
      description="输入图形验证码后发送登录验证码。"
      confirm-text="发送验证码"
      :loading="sendingCode"
      @submit="sendCodeWithCaptcha"
    />
  </AuthLayout>
</template>
