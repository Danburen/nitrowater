<script setup lang="ts">
/**
 * AI runtime settings: the per-user daily token ceiling and the feature switches.
 *
 * <p>Element Plus form controls; the switches write straight into a local copy that is sent as a
 * complete snapshot (the API replaces the whole settings row).</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
// Explicit style import: an explicit API import bypasses the on-demand resolver's CSS injection.
import 'element-plus/es/components/message/style/css'
import { fetchAdminAiSettings, updateAdminAiSettings } from '../../../api/ai'
import type { AiModerationMode } from '../../../api/ai'

interface SettingsForm {
  dailyTokenLimit: number
  aiEnabled: boolean
  chatEnabled: boolean
  agentEnabled: boolean
  documentSearchEnabled: boolean
  webSearchEnabled: boolean
  codeRunnerEnabled: boolean
  skillsEnabled: boolean
  moderationMode: AiModerationMode
  globalDailyTokenLimit: number
  globalQuotaEnabled: boolean
}

type FeatureKey = Exclude<
  keyof SettingsForm,
  'dailyTokenLimit' | 'moderationMode' | 'globalDailyTokenLimit' | 'globalQuotaEnabled'
>

const FEATURES: readonly { key: FeatureKey; label: string; hint: string }[] = [
  { key: 'aiEnabled', label: 'NitroBot', hint: '总开关，关闭后所有 AI 能力不可用' },
  { key: 'chatEnabled', label: 'Chat 模式', hint: '一次性问答接口' },
  { key: 'agentEnabled', label: 'Agent 模式', hint: '工具调用与多轮执行（聊天页使用）' },
  { key: 'documentSearchEnabled', label: '文档检索', hint: '站内文档 RAG' },
  { key: 'webSearchEnabled', label: '联网搜索', hint: '外部网页搜索（需服务端已配置供应商）' },
  { key: 'codeRunnerEnabled', label: 'Agent 代码运行', hint: '允许 Agent 调用沙箱执行代码' },
  { key: 'skillsEnabled', label: 'Skills', hint: '允许加载技能工作流' },
]

const MODERATION_MODES: readonly { value: AiModerationMode; label: string; hint: string }[] = [
  { value: 'OFF', label: '关闭', hint: '不做任何检查。' },
  { value: 'LOG', label: '仅记录', hint: '命中违禁词只记入审计列表，不拦截、不遮蔽。建议先用这个模式跑一段时间，确认误伤情况。' },
  { value: 'ENFORCE', label: '拦截 + 遮蔽', hint: '违规提问直接拒绝；回答中的违规片段替换为 *；短时间多次触发的用户暂停使用 AI 15 分钟。' },
]

const form = reactive<SettingsForm>({
  dailyTokenLimit: 50000,
  aiEnabled: true,
  chatEnabled: true,
  agentEnabled: true,
  documentSearchEnabled: true,
  webSearchEnabled: false,
  codeRunnerEnabled: true,
  skillsEnabled: true,
  moderationMode: 'LOG',
  globalDailyTokenLimit: 0,
  globalQuotaEnabled: false,
})
const loading = ref(false)
const saving = ref(false)
const error = ref('')
/** False until the first read settles: rendering defaults first causes a visible flash. */
const ready = ref(false)

const moderationHint = computed(
  () => MODERATION_MODES.find(mode => mode.value === form.moderationMode)?.hint ?? '',
)

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchAdminAiSettings()
    Object.assign(form, result)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    loading.value = false
    ready.value = true
  }
}

async function save(): Promise<void> {
  if (!Number.isSafeInteger(form.dailyTokenLimit) || form.dailyTokenLimit < 1) {
    error.value = '每日 token 限额必须是正整数。'
    return
  }
  if (!Number.isSafeInteger(form.globalDailyTokenLimit) || form.globalDailyTokenLimit < 0) {
    error.value = '全站 token 额度必须是 ≥ 0 的整数（0 表示不限制）'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const saved = await updateAdminAiSettings({ ...form })
    Object.assign(form, saved)
    ElMessage.success('AI 设置已保存')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
    ElMessage.error('AI 设置保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-card
    shadow="never"
    class="admin-card"
  >
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
      class="mb-4"
    />

    <el-skeleton
      v-if="!ready"
      :rows="7"
      animated
      class="py-2"
    />
    <el-form
      v-else
      label-position="left"
      label-width="190px"
      :disabled="loading || saving"
      @submit.prevent
    >
      <el-form-item label="每日 token 限额">
        <el-input-number
          v-model="form.dailyTokenLimit"
          :min="1"
          :step="1000"
          :precision="0"
          controls-position="right"
          class="!w-[200px]"
        />
        <span class="ml-3 text-xs text-dim">每位用户每天可用上限（UTC 日重置）</span>
      </el-form-item>

      <el-form-item label="全站 token 额度（每日）">
        <el-input-number
          v-model="form.globalDailyTokenLimit"
          :min="0"
          :step="100000"
          :precision="0"
          controls-position="right"
          class="!w-[200px]"
        />
        <span class="ml-3 text-xs text-dim">所有用户合计；0 = 不限制</span>
      </el-form-item>

      <el-form-item label="全站额度熔断">
        <el-switch v-model="form.globalQuotaEnabled" />
        <span class="ml-3 text-xs text-faint">
          开启后当天累计用量达到上限时自动关闭提问入口（历史会话与 / 指令仍可用）
        </span>
      </el-form-item>

      <el-form-item
        v-for="feature in FEATURES"
        :key="feature.key"
        :label="feature.label"
      >
        <el-switch v-model="form[feature.key]" />
        <span class="ml-3 text-xs text-faint">{{ feature.hint }}</span>
      </el-form-item>

      <el-form-item label="内容安全">
        <el-radio-group v-model="form.moderationMode">
          <el-radio-button
            v-for="mode in MODERATION_MODES"
            :key="mode.value"
            :value="mode.value"
          >
            {{ mode.label }}
          </el-radio-button>
        </el-radio-group>
        <span class="ml-3 text-xs text-faint">{{ moderationHint }}</span>
      </el-form-item>

      <el-form-item>
        <el-button
          type="primary"
          :loading="saving"
          @click="save"
        >
          保存设置
        </el-button>
        <el-button
          :disabled="saving"
          @click="load"
        >
          重新读取
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>
