<script setup lang="ts">
/**
 * Standalone 403 page: the session is valid but lacks ROLE_SUPER_ADMIN. Rendered outside
 * AdminLayout so it never triggers a protected API call (which would loop).
 */
function logout(): void {
  // Same-origin redirect to Spring Security's logout; the account service returns to login.
  window.location.assign('/logout')
}
</script>

<template>
  <div class="flex min-h-[100dvh] items-center justify-center bg-bg p-6 text-ink">
    <div class="max-w-[440px] rounded-[14px] border border-line bg-card p-8 text-center">
      <div class="mx-auto mb-4 flex size-11 items-center justify-center rounded-full bg-warn/10 text-warn">
        <svg
          class="size-5"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M12 9v4M12 17h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" />
        </svg>
      </div>
      <h1 class="text-lg font-semibold text-ink">
        无访问权限
      </h1>
      <p class="mt-2 text-[13px] text-dim">
        管理台仅限超级管理员（站长）访问。如需权限，请联系管理员开通。
      </p>
      <button
        class="btn btn-primary mt-6"
        @click="logout"
      >
        退出登录
      </button>
    </div>
  </div>
</template>
