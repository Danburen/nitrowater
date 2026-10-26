<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'

import AuthLayout from '@/components/AuthLayout.vue'
import { ApiError, postJson } from '@/api/auth'
import { getDeviceFp } from '@/composables/useDeviceFp'
import { newCaptchaUrl } from '@/composables/useCaptcha'

const username = ref('')
const phone = ref('')
const smsCode = ref('')
const password = ref('')
const email = ref('')

const showOptional = ref(false)
const showPassword = ref(false)
const errorMessage = ref('')
const notice = ref('')

const showCaptcha = ref(false)
const captchaCode = ref('')
const captchaUrl = ref(newCaptchaUrl())
const captchaInput = ref<HTMLInputElement | null>(null)
const sending = ref(false)
const submitting = ref(false)
const countdown = ref(0)

let countdownTimer: ReturnType<typeof setInterval> | null = null
let redirectTimer: ReturnType<typeof setTimeout> | null = null

function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return '请求失败，请稍后重试'
}

function clearMessages() {
  errorMessage.value = ''
  notice.value = ''
}

function refreshCaptcha() {
  captchaUrl.value = newCaptchaUrl()
}

function openCaptcha() {
  if (!phone.value.trim()) {
    errorMessage.value = '请先填写手机号'
    return
  }
  clearMessages()
  captchaCode.value = ''
  showCaptcha.value = true
  refreshCaptcha()
  void nextTick(() => captchaInput.value?.focus())
}

function closeCaptcha() {
  showCaptcha.value = false
}

function startCountdown() {
  countdown.value = 60
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0 && countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

async function sendCode() {
  const target = phone.value.trim()
  if (!target) {
    errorMessage.value = '请先填写手机号'
    return
  }
  if (!captchaCode.value.trim()) {
    errorMessage.value = '请输入图形验证码'
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
    closeCaptcha()
    notice.value = '验证码已发送，请留意手机短信'
    startCountdown()
  } catch (error) {
    errorMessage.value = messageOf(error)
    refreshCaptcha()
  } finally {
    sending.value = false
  }
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
  if (mail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(mail)) {
    errorMessage.value = '邮箱格式不正确'
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
    })
    notice.value = '注册成功，正在前往登录…'
    redirectTimer = setTimeout(() => window.location.assign('/login?registered=1'), 800)
  } catch (error) {
    errorMessage.value = messageOf(error)
    submitting.value = false
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && showCaptcha.value) closeCaptcha()
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  if (countdownTimer) clearInterval(countdownTimer)
  if (redirectTimer) clearTimeout(redirectTimer)
})
</script>

<template>
  <AuthLayout
    title="创建账号"
    subtitle="注册 NitroWater 账号，即可访问统一身份中心接入的全部应用。"
  >
    <div
      v-if="notice"
      class="auth-alert-success mb-5"
      role="status"
    >
      <svg
        viewBox="0 0 24 24"
        class="mt-0.5 h-4 w-4 shrink-0"
        fill="none"
        aria-hidden="true"
      >
        <path
          d="M20 6L9 17l-5-5"
          stroke="currentColor"
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="1.8"
        />
      </svg>
      {{ notice }}
    </div>

    <div
      v-if="errorMessage"
      class="auth-alert-error mb-5"
      role="alert"
    >
      <svg
        viewBox="0 0 24 24"
        class="mt-0.5 h-4 w-4 shrink-0"
        fill="none"
        aria-hidden="true"
      >
        <path
          d="M12 8v5M12 16.5h.01M10.3 4.6L2.8 17.4A1.5 1.5 0 004.1 19.7h15.8a1.5 1.5 0 001.3-2.3L13.7 4.6a1.9 1.9 0 00-3.4 0Z"
          stroke="currentColor"
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="1.6"
        />
      </svg>
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
        <div class="relative">
          <span class="pointer-events-none absolute inset-y-0 left-3.5 grid place-items-center text-faint">
            <svg
              viewBox="0 0 24 24"
              class="h-4 w-4"
              fill="none"
              aria-hidden="true"
            >
              <circle
                cx="12"
                cy="8"
                r="3.2"
                stroke="currentColor"
                stroke-width="1.6"
              />
              <path
                d="M5.5 19c.7-3 3.2-4.8 6.5-4.8s5.8 1.8 6.5 4.8"
                stroke="currentColor"
                stroke-linecap="round"
                stroke-width="1.6"
              />
            </svg>
          </span>
          <input
            id="reg-username"
            v-model="username"
            class="auth-input"
            autocomplete="username"
            placeholder="4-20 位字母、数字或下划线"
            maxlength="20"
            required
          >
        </div>
      </div>

      <div>
        <label
          class="auth-label"
          for="reg-phone"
        >手机号</label>
        <div class="relative">
          <span class="pointer-events-none absolute inset-y-0 left-3.5 grid place-items-center text-faint">
            <svg
              viewBox="0 0 24 24"
              class="h-4 w-4"
              fill="none"
              aria-hidden="true"
            >
              <rect
                x="7"
                y="2.5"
                width="10"
                height="19"
                rx="2"
                stroke="currentColor"
                stroke-width="1.6"
              />
              <path
                d="M10.5 18.5h3"
                stroke="currentColor"
                stroke-linecap="round"
                stroke-width="1.6"
              />
            </svg>
          </span>
          <input
            id="reg-phone"
            v-model="phone"
            class="auth-input"
            autocomplete="tel"
            inputmode="tel"
            placeholder="用于接收短信验证码"
            required
          >
        </div>
      </div>

      <div>
        <label
          class="auth-label"
          for="reg-sms"
        >短信验证码</label>
        <div class="flex items-stretch gap-2">
          <div class="relative min-w-0 flex-1">
            <span class="pointer-events-none absolute inset-y-0 left-3.5 grid place-items-center text-faint">
              <svg
                viewBox="0 0 24 24"
                class="h-4 w-4"
                fill="none"
                aria-hidden="true"
              >
                <path
                  d="M4 5.5h16a1 1 0 011 1v11a1 1 0 01-1 1H4a1 1 0 01-1-1v-11a1 1 0 011-1Z"
                  stroke="currentColor"
                  stroke-linejoin="round"
                  stroke-width="1.6"
                />
                <path
                  d="M3.5 7l8.5 6 8.5-6"
                  stroke="currentColor"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="1.6"
                />
              </svg>
            </span>
            <input
              id="reg-sms"
              v-model="smsCode"
              class="auth-input"
              autocomplete="one-time-code"
              inputmode="numeric"
              maxlength="6"
              placeholder="请输入短信验证码"
              required
            >
          </div>
          <button
            type="button"
            class="auth-button-ghost h-12 shrink-0 px-4"
            :disabled="sending || countdown > 0"
            @click="openCaptcha"
          >
            {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
          </button>
        </div>
      </div>

      <div class="rounded-xl border border-line bg-soft/35">
        <button
          type="button"
          class="flex w-full items-center justify-between gap-3 px-3.5 py-3 text-left text-[13px] text-dim transition hover:text-ink focus:outline-none"
          :aria-expanded="showOptional"
          @click="showOptional = !showOptional"
        >
          <span class="flex items-center gap-2">
            <svg
              viewBox="0 0 24 24"
              class="h-4 w-4"
              fill="none"
              aria-hidden="true"
            >
              <path
                d="M12 5v14M5 12h14"
                stroke="currentColor"
                stroke-linecap="round"
                stroke-width="1.6"
              />
            </svg>
            补充信息（选填）
          </span>
          <svg
            viewBox="0 0 24 24"
            class="h-4 w-4 transition-transform"
            :class="showOptional ? 'rotate-180' : ''"
            fill="none"
            aria-hidden="true"
          >
            <path
              d="M6 9l6 6 6-6"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="1.6"
            />
          </svg>
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
            <div class="relative">
              <span class="pointer-events-none absolute inset-y-0 left-3.5 grid place-items-center text-faint">
                <svg
                  viewBox="0 0 24 24"
                  class="h-4 w-4"
                  fill="none"
                  aria-hidden="true"
                >
                  <path
                    d="M8 11V8a4 4 0 118 0v3M7 11h10a1 1 0 011 1v7a1 1 0 01-1 1H7a1 1 0 01-1-1v-7a1 1 0 011-1Z"
                    stroke="currentColor"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="1.6"
                  />
                </svg>
              </span>
              <input
                id="reg-password"
                v-model="password"
                class="auth-input auth-input-plain"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="new-password"
                placeholder="8-20 位，含大小写字母和数字"
              >
              <button
                type="button"
                class="absolute inset-y-0 right-2 my-auto grid h-8 w-8 place-items-center rounded-lg text-faint transition hover:bg-soft hover:text-ink focus:outline-none focus:ring-4 focus:ring-accent/15"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                @click="showPassword = !showPassword"
              >
                <svg
                  viewBox="0 0 24 24"
                  class="h-4 w-4"
                  fill="none"
                  aria-hidden="true"
                >
                  <path
                    d="M2.2 12c1.1-2.6 4.8-6.8 9.8-6.8s8.7 4.2 9.8 6.8c-1.1 2.6-4.8 6.8-9.8 6.8S3.3 14.6 2.2 12Z"
                    stroke="currentColor"
                    stroke-linejoin="round"
                    stroke-width="1.6"
                  />
                  <circle
                    cx="12"
                    cy="12"
                    r="3"
                    stroke="currentColor"
                    stroke-width="1.6"
                  />
                </svg>
              </button>
            </div>
            <p class="mt-1.5 text-[11px] text-faint">
              设置后可使用用户名 + 密码登录
            </p>
          </div>

          <div>
            <label
              class="auth-label"
              for="reg-email"
            >邮箱</label>
            <div class="relative">
              <span class="pointer-events-none absolute inset-y-0 left-3.5 grid place-items-center text-faint">
                <svg
                  viewBox="0 0 24 24"
                  class="h-4 w-4"
                  fill="none"
                  aria-hidden="true"
                >
                  <path
                    d="M4 5.5h16a1 1 0 011 1v11a1 1 0 01-1 1H4a1 1 0 01-1-1v-11a1 1 0 011-1Z"
                    stroke="currentColor"
                    stroke-linejoin="round"
                    stroke-width="1.6"
                  />
                  <path
                    d="M3.5 7l8.5 6 8.5-6"
                    stroke="currentColor"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="1.6"
                  />
                </svg>
              </span>
              <input
                id="reg-email"
                v-model="email"
                class="auth-input"
                type="email"
                autocomplete="email"
                placeholder="用于接收通知（选填）"
              >
            </div>
          </div>
        </div>
      </div>

      <button
        type="submit"
        class="auth-button mt-2"
        :disabled="submitting"
      >
        <svg
          v-if="submitting"
          class="h-4 w-4 animate-spin"
          viewBox="0 0 24 24"
          fill="none"
          aria-hidden="true"
        >
          <circle
            cx="12"
            cy="12"
            r="9"
            stroke="currentColor"
            stroke-opacity=".25"
            stroke-width="3"
          />
          <path
            d="M21 12a9 9 0 00-9-9"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-width="3"
          />
        </svg>
        {{ submitting ? '正在创建…' : '创建账号' }}
      </button>
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

    <Teleport to="body">
      <div
        v-if="showCaptcha"
        class="auth-dialog-mask auth-fade-in"
        @click.self="closeCaptcha"
      >
        <div
          class="auth-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="captcha-title"
          @keydown.esc="closeCaptcha"
        >
          <div class="flex items-start justify-between gap-4">
            <div>
              <h3
                id="captcha-title"
                class="text-base font-semibold text-ink"
              >
                安全验证
              </h3>
              <p class="mt-1 text-[12px] text-dim">
                输入图形验证码后发送短信验证码。
              </p>
            </div>
            <button
              type="button"
              class="grid h-8 w-8 place-items-center rounded-lg text-faint transition hover:bg-soft hover:text-ink focus:outline-none focus:ring-4 focus:ring-accent/15"
              aria-label="关闭"
              @click="closeCaptcha"
            >
              <svg
                viewBox="0 0 24 24"
                class="h-4 w-4"
                fill="none"
                aria-hidden="true"
              >
                <path
                  d="M6 6l12 12M18 6L6 18"
                  stroke="currentColor"
                  stroke-linecap="round"
                  stroke-width="1.8"
                />
              </svg>
            </button>
          </div>

          <div class="mt-5 flex items-stretch gap-2">
            <input
              ref="captchaInput"
              v-model="captchaCode"
              class="w-full rounded-xl border border-line bg-bg/60 px-3.5 text-sm text-ink outline-none transition placeholder:text-faint focus:border-accent focus:ring-4 focus:ring-accent/10"
              maxlength="6"
              inputmode="numeric"
              autocomplete="off"
              placeholder="图形验证码"
              @keydown.enter.prevent="sendCode"
            >
            <button
              type="button"
              class="shrink-0 overflow-hidden rounded-xl border border-line bg-white transition hover:border-line-strong focus:outline-none focus:ring-4 focus:ring-accent/15"
              title="点击刷新验证码"
              aria-label="刷新图形验证码"
              @click="refreshCaptcha"
            >
              <img
                :src="captchaUrl"
                alt="图形验证码"
                class="h-12 w-[120px] object-cover"
                width="120"
                height="48"
              >
            </button>
          </div>

          <div class="mt-5 flex justify-end gap-2">
            <button
              type="button"
              class="auth-button-ghost"
              @click="closeCaptcha"
            >
              取消
            </button>
            <button
              type="button"
              class="auth-button w-auto px-5"
              :disabled="sending"
              @click="sendCode"
            >
              {{ sending ? '发送中…' : '发送验证码' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </AuthLayout>
</template>
