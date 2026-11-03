<script setup lang="ts">
import AdminPage from '../components/AdminPage.vue'
import AiAdminModeration from './AiAdminModeration.vue'
</script>

<template>
  <AdminPage
    title="内容安全"
    description="违禁词策略、命中审计与词表重载；策略在运行设置里切换。"
  >
    <AiAdminModeration />
  </AdminPage>
</template>
