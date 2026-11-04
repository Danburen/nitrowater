<script setup lang="ts">
/**
 * Step-up verification for account changes.
 *
 * <p>The server decides which masked targets are available; the browser only picks a channel and
 * sends the one-time code. No raw phone/email is ever rendered here.</p>
 */
import { computed, nextTick, ref, watch } from 'vue'
import {
  fetchReAuthInfo,
  sendReAuthCode,
  verifyReAuthCode,
  type ReAuthInfo,
  type ReAuthScene,
  type VerifyChannel,
} from '../../api/account'
import AppDialog from '../../shared/ui/AppDialog.vue'
import { toast } from '../../shared/ui/toast'
import { useCodeCooldown, type CooldownScope } from '../../shared/useCodeCooldown'

const props = defineProps<{
  visible: boolean
  scene: ReAuthScene
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success', token: string): void
}>()

const open = ref(props.visible)
const info = ref<ReAuthInfo | null>(null)
const channel = ref<VerifyChannel>('SMS')
const step = ref<1 | 2>(1)
const loading = ref(false)
const sending = ref(false)
const verifying = ref(false)
const code = ref('')
const error = ref('')
/** Key returned by the send call; the verify call must present it (cookies do not survive the BFF). */
const verifyKey = ref('')
/** The code field only exists from step 2 on; focus it as soon as it renders. */
const codeInput = ref<HTMLInputElement | null>(null)
const {
  remaining: cooldown,
  start: startCooldown,
  restore: restoreCooldown,
  sync: syncCooldown,
} = useCodeCooldown()

/** The server masks the target, so the channel is the stable cooldown identity here. */
function cooldownScope(): CooldownScope {
  return { scene: props.scene, channel: channel.value, target: 'account' }
}

const channels = computed<VerifyChannel[]>(() =>
  (info.value?.availableChannels ?? []).filter(
    (value): value is VerifyChannel => value === 'SMS' || value === 'EMAIL',
  ),
)
const target = computed(() =>
  channel.value === 'SMS' ? info.value?.maskedPhone : info.value?.maskedEmail,
)

watch(() => props.visible, (value) => {
  open.value = value
  if (value) void init()
})
watch(open, (value) => emit('update:visible', value))
watch(step, (value) => {
  if (value === 2) void nextTick(() => codeInput.value?.focus())
})
watch(channel, () => {
  // A code sent to the old channel cannot verify against the new one; send the user back to step 1.
  if (step.value === 2) {
    step.value = 1
    code.value = ''
  }
  verifyKey.value = ''
  error.value = ''
  restoreCooldown(cooldownScope())
})

async function init(): Promise<void> {
  reset()
  loading.value = true
  try {
    info.value = await fetchReAuthInfo()
    channel.value = channels.value[0] ?? 'SMS'
    if (!channels.value.length) {
      error.value = '当前账号没有可用于安全验证的手机号或邮箱'
    } else {
      restoreCooldown(cooldownScope())
    }
  } catch (cause) {
    error.value = message(cause)
  } finally {
    loading.value = false
  }
}

function reset(): void {
  step.value = 1
  code.value = ''
  verifyKey.value = ''
  error.value = ''
  info.value = null
}

async function sendCode(): Promise<void> {
  if (!channels.value.length) return
  sending.value = true
  error.value = ''
  try {
    verifyKey.value = await sendReAuthCode(props.scene, channel.value)
    step.value = 2
    startCooldown(60, cooldownScope())
    toast.success('验证码已发送')
  } catch (cause) {
    error.value = message(cause)
    syncCooldown(cause, cooldownScope())
  } finally {
    sending.value = false
  }
}

async function verifyCode(): Promise<void> {
  if (code.value.trim().length < 6 || !verifyKey.value) return
  verifying.value = true
  error.value = ''
  try {
    const token = await verifyReAuthCode(props.scene, channel.value, verifyKey.value, code.value.trim())
    emit('success', token)
    open.value = false
  } catch (cause) {
    error.value = message(cause)
  } finally {
    verifying.value = false
  }
}

function message(cause: unknown): string {
  return cause instanceof Error ? cause.message : '操作失败，请稍后重试'
}
</script>

<template>
  <AppDialog
    v-model="open"
    title="安全验证"
    width="400px"
    :close-on-overlay="false"
    :show-footer="false"
    @closed="reset"
  >
    <div class="space-y-4">
      <p
        v-if="loading"
        class="text-sm text-dim"
      >
        正在获取验证方式…
      </p>
      <template v-else>
        <label
          v-if="channels.length > 1"
          class="block text-sm text-dim"
        >
          验证方式
          <select
            v-model="channel"
            class="select mt-1.5 w-full"
          >
            <option
              v-for="item in channels"
              :key="item"
              :value="item"
            >
              {{ item === 'SMS' ? '短信验证' : '邮箱验证' }}
            </option>
          </select>
        </label>

        <div
          v-if="step === 1"
          class="space-y-3"
        >
          <p class="text-sm leading-6 text-dim">
            为保护账号安全，请先完成一次验证。验证码将发送到：
          </p>
          <p class="text-center text-lg font-semibold tracking-wide text-ink">
            {{ target || '未绑定' }}
          </p>
          <button
            type="button"
            class="btn btn-primary w-full justify-center"
            :disabled="sending || !target || loading || cooldown > 0"
            @click="sendCode"
          >
            <span
              v-if="sending"
              class="size-3.5 animate-spin rounded-full border-2 border-current border-t-transparent"
              aria-hidden="true"
            />
            {{ cooldown > 0 ? `${cooldown}s 后重发` : '发送验证码' }}
          </button>
        </div>

        <div
          v-else
          class="space-y-3"
        >
          <p class="text-sm text-dim">
            请输入发送到 {{ target }} 的 6 位验证码。
          </p>
          <label class="block text-sm text-dim">
            6 位验证码
            <input
              ref="codeInput"
              v-model="code"
              class="input mt-1.5 w-full text-center tracking-[0.3em]"
              maxlength="6"
              inputmode="numeric"
              placeholder="000000"
              @keyup.enter="verifyCode"
            >
          </label>
          <button
            type="button"
            class="btn btn-primary w-full justify-center"
            :disabled="verifying || code.trim().length < 6 || !verifyKey"
            @click="verifyCode"
          >
            <span
              v-if="verifying"
              class="size-3.5 animate-spin rounded-full border-2 border-current border-t-transparent"
              aria-hidden="true"
            />
            验证
          </button>
          <button
            type="button"
            class="btn btn-sm"
            :disabled="sending || cooldown > 0"
            @click="sendCode"
          >
            {{ cooldown > 0 ? `${cooldown}s 后重发` : '重新发送' }}
          </button>
        </div>

        <p
          v-if="error"
          class="text-sm text-danger"
        >
          {{ error }}
        </p>
      </template>
    </div>
  </AppDialog>
</template>
