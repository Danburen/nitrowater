<script setup lang="ts">
import AdminPage from '../components/AdminPage.vue'
import AiAdminUsage from '../components/AiAdminUsage.vue'
</script>

<template>
  <AdminPage
    title="用户用量"
    description="按 UTC 日期统计的每人 token 消耗；行内可重置单个用户，多选后可批量重置。"
  >
    <AiAdminUsage />
  </AdminPage>
</template>
