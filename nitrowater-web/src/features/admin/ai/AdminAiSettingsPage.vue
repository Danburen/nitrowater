<script setup lang="ts">
import AdminPage from '../AdminPage.vue'
import AiAdminSettings from './AiAdminSettings.vue'
</script>

<template>
  <AdminPage
    title="运行设置"
    description="每位用户的每日 token 限额、全站额度与熔断，以及各 AI 能力的总开关。"
  >
    <AiAdminSettings />
  </AdminPage>
</template>
