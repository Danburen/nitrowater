<script setup lang="ts">
import { ref } from 'vue'

import { ElButton, ElDialog, ElInput } from 'element-plus'

import { newCaptchaUrl } from '@/composables/useCaptcha'

/**
 * Captcha dialog shared by the login and register pages.
 *
 * Built on Element Plus `el-dialog` (so the close affordance / focus trap / overlay are the
 * framework's, not hand-rolled) and owns the captcha image, its refresh and the empty-input
 * check. The parent decides what the entered code is used for.
 */
const props = defineProps<{
  title: string
  description: string
  confirmText: string
  loading?: boolean
}>()

const emit = defineEmits<{
  submit: [code: string]
}>()

const open = defineModel<boolean>({ required: true })

const code = ref('')
const error = ref('')
/**
 * Empty until the dialog opens: `el-dialog` renders its body (taking `@open` a tick later) before
 * emitting `open`, so a pre-filled URL here would fire a request with a stale challenge and then a
 * second one from {@link onOpen} — two captchas per open, one of them orphaned in Redis.
 */
const captchaUrl = ref('')
const inputRef = ref<InstanceType<typeof ElInput> | null>(null)

/** Fresh state each time the dialog is opened (a stale code/image must never be reused). */
function onOpen() {
  code.value = ''
  error.value = ''
  captchaUrl.value = newCaptchaUrl()
}

/** Drop the URL once closed, so reopening cannot request the previous challenge before onOpen runs. */
function onClosed() {
  captchaUrl.value = ''
}

function onOpened() {
  inputRef.value?.focus()
}

function refreshCaptcha() {
  captchaUrl.value = newCaptchaUrl()
}

function confirm() {
  const value = code.value.trim()
  if (!value) {
    error.value = '请输入图形验证码'
    return
  }
  emit('submit', value)
}
</script>

<template>
  <ElDialog
    v-model="open"
    :title="props.title"
    width="min(360px, calc(100vw - 32px))"
    align-center
    append-to-body
    @open="onOpen"
    @opened="onOpened"
    @closed="onClosed"
  >
    <p class="text-[13px] leading-5 text-dim">
      {{ props.description }}
    </p>

    <div class="mt-4 flex items-center gap-2">
      <ElInput
        ref="inputRef"
        v-model="code"
        class="min-w-0 flex-1"
        maxlength="6"
        autocomplete="off"
        placeholder="图形验证码"
        @input="error = ''"
        @keyup.enter="confirm"
      />
      <button
        type="button"
        class="shrink-0 overflow-hidden rounded border border-line bg-white transition hover:border-line-strong focus:outline-none focus:ring-2 focus:ring-accent/15"
        title="点击刷新验证码"
        aria-label="刷新图形验证码"
        @click="refreshCaptcha"
      >
        <img
          v-if="captchaUrl"
          :src="captchaUrl"
          alt="图形验证码"
          width="140"
          height="44"
          class="block h-[44px] w-[140px]"
        >
        <span
          v-else
          class="block h-[44px] w-[140px] animate-pulse rounded bg-soft"
          aria-hidden="true"
        />
      </button>
    </div>

    <p
      v-if="error"
      class="mt-2 text-[12px] text-danger"
      role="alert"
    >
      {{ error }}
    </p>

    <template #footer>
      <ElButton @click="open = false">
        取消
      </ElButton>
      <ElButton
        type="primary"
        :loading="props.loading"
        @click="confirm"
      >
        {{ props.confirmText }}
      </ElButton>
    </template>
  </ElDialog>
</template>
