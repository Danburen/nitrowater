<script setup lang="ts">
/**
 * Step-up verification for account changes.
 *
 * <p>The server decides which masked targets are available; the browser only picks a channel and
 * sends the one-time code. No raw phone/email is ever rendered here.</p>
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchReAuthInfo,
  sendReAuthCode,
  verifyReAuthCode,
  type ReAuthInfo,
  type ReAuthScene,
  type VerifyChannel,
} from '../../api/account'

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

async function init(): Promise<void> {
  reset()
  loading.value = true
  try {
    info.value = await fetchReAuthInfo()
    channel.value = channels.value[0] ?? 'SMS'
    if (!channels.value.length) {
      error.value = '当前账号没有可用于安全验证的手机号或邮箱'
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
  error.value = ''
  info.value = null
}

async function sendCode(): Promise<void> {
  if (!channels.value.length) return
  sending.value = true
  error.value = ''
  try {
    await sendReAuthCode(props.scene, channel.value)
    step.value = 2
    ElMessage.success('验证码已发送')
  } catch (cause) {
    error.value = message(cause)
  } finally {
    sending.value = false
  }
}

async function verifyCode(): Promise<void> {
  if (code.value.trim().length < 6) return
  verifying.value = true
  error.value = ''
  try {
    const token = await verifyReAuthCode(props.scene, channel.value, code.value.trim())
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
  <el-dialog
    v-model="open"
    title="安全验证"
    width="400px"
    :close-on-click-modal="false"
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
        <el-select
          v-if="channels.length > 1"
          v-model="channel"
          size="small"
          class="w-full"
        >
          <el-option
            v-for="item in channels"
            :key="item"
            :label="item === 'SMS' ? '短信验证' : '邮箱验证'"
            :value="item"
          />
        </el-select>

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
          <el-button
            type="primary"
            class="w-full"
            :loading="sending"
            :disabled="!target || loading"
            @click="sendCode"
          >
            发送验证码
          </el-button>
        </div>

        <div
          v-else
          class="space-y-3"
        >
          <p class="text-sm text-dim">
            请输入发送到 {{ target }} 的 6 位验证码。
          </p>
          <el-input
            v-model="code"
            maxlength="6"
            placeholder="6 位验证码"
            class="text-center"
            @keyup.enter="verifyCode"
          />
          <el-button
            type="primary"
            class="w-full"
            :loading="verifying"
            :disabled="code.trim().length < 6"
            @click="verifyCode"
          >
            验证
          </el-button>
          <el-button
            link
            size="small"
            :loading="sending"
            @click="sendCode"
          >
            重新发送
          </el-button>
        </div>

        <p
          v-if="error"
          class="text-sm text-danger"
        >
          {{ error }}
        </p>
      </template>
    </div>
  </el-dialog>
</template>
