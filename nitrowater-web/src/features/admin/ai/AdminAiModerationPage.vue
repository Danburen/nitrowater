<script setup lang="ts">
import { ref } from 'vue'
import AdminPage from '../components/AdminPage.vue'
import AiAdminModeration from '../components/AiAdminModeration.vue'

/** The panel owns the audit request and the dictionary; the header renders its reload action. */
const panel = ref<InstanceType<typeof AiAdminModeration> | null>(null)
</script>

<template>
  <AdminPage
    title="内容安全"
    description="违禁词策略、命中审计与词表重载；策略在运行设置里切换。"
  >
    <template #actions>
      <el-button
        :loading="panel?.reloading ?? false"
        @click="panel?.reload()"
      >
        重载词表
      </el-button>
    </template>

    <AiAdminModeration ref="panel" />
  </AdminPage>
</template>
