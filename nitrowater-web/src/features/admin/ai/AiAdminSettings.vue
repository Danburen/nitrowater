<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { toast } from '../../../shared/ui/toast'
import {
  fetchAdminAiSettings,
  updateAdminAiSettings,
  type AiAdminSettings,
} from '../../ai/api'

type FeatureKey = Exclude<keyof AiAdminSettings, 'dailyTokenLimit'>

const featureFlags = [
  { key: 'aiEnabled', label: 'NitroBot' },
  { key: 'chatEnabled', label: 'Chat 模式' },
  { key: 'agentEnabled', label: 'Agent 模式' },
  { key: 'documentSearchEnabled', label: '文档检索' },
  { key: 'webSearchEnabled', label: '联网搜索' },
  { key: 'codeRunnerEnabled', label: 'Agent 代码运行' },
  { key: 'skillsEnabled', label: 'Skills' },
] as const satisfies readonly { key: FeatureKey; label: string }[]

const settings = ref<AiAdminSettings | null>(null)
const dailyTokenLimit = ref('')
const loading = ref(false)
const saving = ref(false)
const error = ref('')

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchAdminAiSettings()
    settings.value = result
    dailyTokenLimit.value = String(result.dailyTokenLimit)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    loading.value = false
  }
}

function setFeature(key: FeatureKey, event: Event): void {
  const target = event.currentTarget
  if (settings.value && target instanceof HTMLInputElement) {
    settings.value[key] = target.checked
  }
}

async function save(): Promise<void> {
  const current = settings.value
  const limit = Number(dailyTokenLimit.value)
  if (!current || !Number.isSafeInteger(limit) || limit < 1) {
    error.value = '每日 token 限额必须是正整数。'
    return
  }
  saving.value = true
  error.value = ''
  try {
    settings.value = await updateAdminAiSettings({ ...current, dailyTokenLimit: limit })
    dailyTokenLimit.value = String(limit)
    toast.success('AI 设置已保存')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
    toast.error('AI 设置保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section
    class="panel"
    aria-labelledby="ai-settings-title"
  >
    <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
      <h2
        id="ai-settings-title"
        class="text-sm font-semibold text-ink"
      >
        每日额度与功能
      </h2>
      <button
        class="btn btn-primary"
        type="button"
        :disabled="loading || saving || !settings"
        @click="save"
      >
        {{ saving ? '保存中…' : '保存设置' }}
      </button>
    </div>

    <p
      v-if="error"
      role="alert"
      class="mb-3 text-sm text-danger"
    >
      {{ error }}
    </p>
    <p
      v-if="loading"
      class="py-3 text-sm text-dim"
    >
      正在读取设置…
    </p>
    <div
      v-else-if="settings"
      class="grid gap-5 lg:grid-cols-[minmax(220px,300px)_1fr]"
    >
      <label
        for="ai-daily-token-limit"
        class="grid content-start gap-2 text-[13px] text-dim"
      >
        每位用户每日 token 限额
        <input
          id="ai-daily-token-limit"
          v-model="dailyTokenLimit"
          class="input font-mono"
          type="number"
          min="1"
          step="1000"
          inputmode="numeric"
          required
        >
      </label>

      <div class="grid gap-x-5 sm:grid-cols-2 lg:grid-cols-3">
        <label
          v-for="flag in featureFlags"
          :key="flag.key"
          class="flex min-h-10 items-center gap-2 border-b border-line/60 py-2 text-[13px] text-ink"
        >
          <input
            class="size-4 accent-accent"
            type="checkbox"
            :checked="settings[flag.key]"
            @change="setFeature(flag.key, $event)"
          >
          <span>{{ flag.label }}</span>
        </label>
      </div>
    </div>
  </section>
</template>
