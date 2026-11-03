<script setup lang="ts">
import { ref } from 'vue'
import AdminPage from '../components/AdminPage.vue'
import AiAdminSettings from '../components/AiAdminSettings.vue'

/** The panel owns the settings state (and its dirty flag); the header only renders the actions. */
const panel = ref<InstanceType<typeof AiAdminSettings> | null>(null)
</script>

<template>
  <AdminPage
    title="运行设置"
    description="每位用户的每日 token 限额、全站额度与熔断，以及各 AI 能力的开关。"
  >
    <template #actions>
      <span
        v-if="panel?.dirty"
        class="text-[12px] text-faint"
      >有未保存的更改</span>
      <el-button
        :disabled="panel?.busy ?? false"
        @click="panel?.load()"
      >
        重新读取
      </el-button>
      <el-button
        type="primary"
        :loading="panel?.saving ?? false"
        :disabled="panel?.busy ?? true"
        @click="panel?.save()"
      >
        保存设置
      </el-button>
    </template>

    <AiAdminSettings ref="panel" />
  </AdminPage>
</template>
