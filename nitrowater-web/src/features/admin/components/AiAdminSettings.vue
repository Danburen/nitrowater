<script setup lang="ts">
/**
 * AI runtime settings: quota ceilings, feature switches and the moderation posture.
 *
 * <p>Layout rules for settings pages: one entry per row, name on the left and control on the right,
 * grouped by concern with a heading per group and hairlines inside it. No card wrapper - the groups
 * float on the page background. Destructive actions live alone in the danger zone at the bottom
 * (GitHub-style), never in the page action cluster.</p>
 *
 * <p>The controls write into a local copy that is sent as a complete snapshot (the API replaces the
 * whole settings row), so <em>save</em> is the only thing that changes the running configuration.</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { confirm } from '../../../shared/ui/confirm.ts'
import { toast } from '../../../shared/ui/toast.ts'
import { fetchAdminAiSettings, updateAdminAiSettings } from '../../../api/ai'
import type { AiAdminSettings, AiModerationMode } from '../../../api/ai'
import AdminDangerRow from '../AdminDangerRow.vue'
import AdminDangerZone from '../AdminDangerZone.vue'
import AdminSettingsGroup from '../AdminSettingsGroup.vue'
import AdminSettingsRow from '../AdminSettingsRow.vue'

type FeatureKey = Exclude<
  keyof AiAdminSettings,
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

/** Mirror of the server-side factory defaults; the danger zone restores this exact snapshot. */
const DEFAULT_SETTINGS: AiAdminSettings = {
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
}

const form = reactive<AiAdminSettings>({ ...DEFAULT_SETTINGS })
/** Last snapshot confirmed by the server; {@code null} until the first read settles. */
const saved = ref<AiAdminSettings | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
/** False until the first read settles: rendering defaults first causes a visible flash. */
const ready = ref(false)

const busy = computed(() => loading.value || saving.value)
const dirty = computed(
  () => saved.value !== null && JSON.stringify({ ...form }) !== JSON.stringify(saved.value),
)
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
    saved.value = { ...result }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    loading.value = false
    ready.value = true
  }
}

async function save(): Promise<boolean> {
  if (!Number.isSafeInteger(form.dailyTokenLimit) || form.dailyTokenLimit < 1) {
    error.value = '每日 token 限额必须是正整数。'
    return false
  }
  if (!Number.isSafeInteger(form.globalDailyTokenLimit) || form.globalDailyTokenLimit < 0) {
    error.value = '全站 token 额度必须是 ≥ 0 的整数（0 表示不限制）'
    return false
  }
  saving.value = true
  error.value = ''
  try {
    const persisted = await updateAdminAiSettings({ ...form })
    Object.assign(form, persisted)
    saved.value = { ...persisted }
    toast.success('AI 设置已保存')
    return true
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
    toast.error('AI 设置保存失败')
    return false
  } finally {
    saving.value = false
  }
}

/** Danger zone: overwrite the live configuration with the factory snapshot and persist it. */
async function resetToDefaults(): Promise<void> {
  if (!(await confirm({
    title: '恢复默认设置',
    message: '将额度、功能开关与内容安全策略恢复为出厂默认值，并立即保存到线上配置。此操作会覆盖当前设置，继续？',
    confirmText: '恢复并保存',
    danger: true,
  }))) return
  Object.assign(form, DEFAULT_SETTINGS)
  await save()
}

// Page-level action cluster (AdminPage header) drives these; the panel owns the state.
defineExpose({ busy, dirty, loading, saving, ready, load, save })
</script>

<template>
  <div class="space-y-4">
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    />

    <el-skeleton
      v-if="!ready"
      :rows="7"
      animated
      class="py-2"
    />
    <template v-else>
      <AdminSettingsGroup
        title="额度限制"
        description="计量以 UTC 日为单位，保存后对后续请求生效。"
      >
        <AdminSettingsRow
          label="每日 token 限额"
          hint="每位用户每天可用上限（UTC 日重置）"
        >
          <el-input-number
            v-model="form.dailyTokenLimit"
            :min="1"
            :step="1000"
            :precision="0"
            controls-position="right"
            :disabled="busy"
            class="w-[200px]!"
          />
        </AdminSettingsRow>

        <AdminSettingsRow
          label="全站 token 额度（每日）"
          hint="所有用户合计；0 = 不限制"
        >
          <el-input-number
            v-model="form.globalDailyTokenLimit"
            :min="0"
            :step="100000"
            :precision="0"
            controls-position="right"
            :disabled="busy"
            class="w-[200px]!"
          />
        </AdminSettingsRow>

        <AdminSettingsRow
          label="全站额度熔断"
          hint="开启后当天累计用量达到上限时自动关闭提问入口（历史会话与 / 指令仍可用）"
        >
          <el-switch
            v-model="form.globalQuotaEnabled"
            :disabled="busy"
          />
        </AdminSettingsRow>
      </AdminSettingsGroup>

      <AdminSettingsGroup
        title="功能开关"
        description="关闭后立即对后续请求生效，已打开的会话不受影响。"
      >
        <AdminSettingsRow
          v-for="feature in FEATURES"
          :key="feature.key"
          :label="feature.label"
          :hint="feature.hint"
        >
          <el-switch
            v-model="form[feature.key]"
            :disabled="busy"
          />
        </AdminSettingsRow>
      </AdminSettingsGroup>

      <AdminSettingsGroup
        title="内容安全"
        description="决定命中违禁词时是记录、拦截还是遮蔽。"
      >
        <AdminSettingsRow label="处置方式">
          <template #hint>
            {{ moderationHint }}
          </template>
          <el-radio-group
            v-model="form.moderationMode"
            :disabled="busy"
          >
            <el-radio-button
              v-for="mode in MODERATION_MODES"
              :key="mode.value"
              :value="mode.value"
            >
              {{ mode.label }}
            </el-radio-button>
          </el-radio-group>
        </AdminSettingsRow>
      </AdminSettingsGroup>

      <AdminDangerZone>
        <AdminDangerRow label="恢复默认设置">
          <el-button
            type="danger"
            plain
            :disabled="busy"
            @click="resetToDefaults"
          >
            恢复默认
          </el-button>
        </AdminDangerRow>
      </AdminDangerZone>
    </template>
  </div>
</template>
