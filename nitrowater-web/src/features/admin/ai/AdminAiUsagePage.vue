<script setup lang="ts">
import AdminPage from '../AdminPage.vue'
import AiAdminUsage from './AiAdminUsage.vue'
</script>

<template>
  <AdminPage
    title="用户用量"
    description="按 UTC 日期统计的每人 token 消耗，可选中用户或全部重置。"
  >
    <AiAdminUsage />
  </AdminPage>
</template>
