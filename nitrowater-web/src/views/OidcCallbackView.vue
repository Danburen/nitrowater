<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { userManager } from '../auth/oidc'

const router = useRouter()
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    await userManager.signinRedirectCallback()
    await router.replace('/')
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
})
</script>

<template>
  <div class="callback">
    <p
      v-if="!error"
      class="muted"
    >
      正在完成登录…
    </p>
    <template v-else>
      <h2>登录回调处理失败</h2>
      <pre class="mono">{{ error }}</pre>
      <router-link
        class="btn primary"
        to="/"
      >
        返回首页
      </router-link>
    </template>
  </div>
</template>

<style scoped>
.callback {
  min-height: 100%;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 1rem;
  padding: 2rem;
  text-align: center;
}

pre {
  max-width: 640px;
  overflow: auto;
  padding: 1rem;
  background: var(--surface-2);
  border-radius: 8px;
  font-size: 13px;
}
</style>
