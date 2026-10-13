<script setup lang="ts">
/**
 * In-app 404: an unknown console route. Rendered inside AdminLayout (so the sidebar stays
 * available) — the router guard has already resolved the session before this view shows.
 */
</script>

<template>
  <div class="mx-auto flex w-full max-w-[560px] flex-col items-center justify-center p-10 text-center text-ink">
    <div class="text-5xl font-semibold leading-none text-accent/70">
      404
    </div>
    <h1 class="mt-3 text-lg font-semibold text-ink">
      页面不存在
    </h1>
    <p class="mt-2 text-[13px] text-dim">
      你访问的页面不存在或已被移除。请检查地址，或返回首页。
    </p>
    <router-link
      class="btn btn-primary mt-6"
      to="/users"
    >
      返回首页
    </router-link>
  </div>
</template>
