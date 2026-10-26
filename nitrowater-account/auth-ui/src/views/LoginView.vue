<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import AuthLayout from '@/components/AuthLayout.vue'
import { getDeviceFp } from '@/composables/useDeviceFp'
import { newCaptchaUrl } from '@/composables/useCaptcha'

const route = useRoute()

const ERROR_MESSAGES: Record<string, string> = {
  captcha: '验证码不正确，请重新输入',
  credentials: '用户名或密码不正确',
  locked: '尝试次数过多，请稍后再试',
  daily: '今日尝试次数过多，请明天再试',
  server: '登录服务暂时不可用，请稍后重试',
}

const errorCode = computed(() => {
  const raw = route.query.error
  return typeof raw === 'string' ? raw : ''
})
const errorMessage = computed(() =>
  errorCode.value ? (ERROR_MESSAGES[errorCode.value] ?? '登录失败，请稍后重试') : '',
)
const registered = computed(() => route.query.registered === '1')
const redirect = computed(() => {
  const raw = route.query.redirect
  return typeof raw === 'string' ? raw : ''
})

const formRef = ref<HTMLFormElement | null>(null)
const captchaInput = ref<HTMLInputElement | null>(null)
const deviceFp = ref('')
const captchaUrl = ref(newCaptchaUrl())
const captchaCode = ref('')
const captchaError = ref('')
const showCaptcha = ref(false)
const showPassword = ref(false)
const submitting = ref(false)

function refreshCaptcha() {
  captchaUrl.value = newCaptchaUrl()
}

function openCaptcha() {
  captchaCode.value = ''
  captchaError.value = ''
  showCaptcha.value = true
  refreshCaptcha()
  void nextTick(() => captchaInput.value?.focus())
}

function closeCaptcha() {
  showCaptcha.value = false
  captchaCode.value = ''
  captchaError.value = ''
}

function submitWithCaptcha() {
  const code = captchaCode.value.trim()
  if (!code) {
    captchaError.value = '请输入图形验证码'
    return
  }
  captchaCode.value = code
  showCaptcha.value = false
  submitting.value = true
  formRef.value?.requestSubmit()
}

function onSubmit(event: Event) {
  if (captchaCode.value.trim()) {
    submitting.value = true
    return
  }
  event.preventDefault()
  if (formRef.value && !formRef.value.reportValidity()) return
  openCaptcha()
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && showCaptcha.value) closeCaptcha()
}

onMounted(() => {
  deviceFp.value = getDeviceFp()
  window.addEventListener('keydown', onKeydown)
})
onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <AuthLayout
    title="欢迎回来"
    subtitle="登录后继续访问 NitroWater 门户、管理台与业务应用。"
  >
    <div
      v-if="registered"
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
      账号创建成功，请使用新账号登录。
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
            id="username"
            name="username"
            class="auth-input"
            autocomplete="username"
            placeholder="请输入账号"
            required
          >
        </div>
      </div>

      <div>
        <label
          class="auth-label"
          for="password"
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
            id="password"
            name="password"
            class="auth-input auth-input-plain"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="current-password"
            placeholder="请输入密码"
            required
          >
          <button
            type="button"
            class="absolute inset-y-0 right-2 my-auto grid h-8 w-8 place-items-center rounded-lg text-faint transition hover:bg-soft hover:text-ink focus:outline-none focus:ring-4 focus:ring-accent/15"
            :aria-label="showPassword ? '隐藏密码' : '显示密码'"
            @click="showPassword = !showPassword"
          >
            <svg
              v-if="showPassword"
              viewBox="0 0 24 24"
              class="h-4 w-4"
              fill="none"
              aria-hidden="true"
            >
              <path
                d="M3 3l18 18M10.6 10.7A3 3 0 0014 14.2M9.9 5.4A10.8 10.8 0 0112 5.2c5 0 8.7 4.2 9.8 6.8a12.7 12.7 0 01-2.7 3.7M6.7 6.8A13.7 13.7 0 002.2 12c1.1 2.6 4.8 6.8 9.8 6.8 1.2 0 2.3-.2 3.3-.7"
                stroke="currentColor"
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="1.6"
              />
            </svg>
            <svg
              v-else
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
        {{ submitting ? '正在登录…' : '登录' }}
      </button>
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
          aria-labelledby="login-captcha-title"
          aria-describedby="login-captcha-desc"
        >
          <div class="flex items-start justify-between gap-4">
            <div>
              <h3
                id="login-captcha-title"
                class="text-base font-semibold text-ink"
              >
                安全验证
              </h3>
              <p
                id="login-captcha-desc"
                class="mt-1 text-[12px] text-dim"
              >
                请输入图形验证码后继续登录。
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
              :class="captchaError ? 'border-danger/60' : ''"
              maxlength="6"
              autocomplete="off"
              autocapitalize="none"
              spellcheck="false"
              placeholder="图形验证码"
              :aria-invalid="Boolean(captchaError)"
              @input="captchaError = ''"
              @keydown.enter.prevent="submitWithCaptcha"
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
          <p
            v-if="captchaError"
            class="mt-2 text-[12px] text-danger"
            role="alert"
          >
            {{ captchaError }}
          </p>

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
              @click="submitWithCaptcha"
            >
              继续登录
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </AuthLayout>
</template>
