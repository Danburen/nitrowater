<script setup lang="ts">
/**
 * Hosted auth shell (login / register).
 *
 * <p>Element Plus card as the frame so the SSO pages share the admin-family look; the form bodies
 * stay in their own views (they carry the captcha, device fingerprint and OIDC redirect logic).</p>
 */
import { ElCard } from 'element-plus'
import { ThemeToggle } from '@nitrowater/ui'

defineProps<{
  title: string
  subtitle: string
}>()
</script>

<template>
  <div class="grid min-h-dvh place-items-center bg-bg p-6">
    <div class="w-full max-w-[400px]">
      <ElCard
        shadow="never"
        class="auth-panel"
      >
        <template #header>
          <div class="flex items-center gap-2.5">
            <div class="min-w-0 flex-1">
              <p class="text-[15px] font-semibold leading-tight text-ink">
                NitroWater
              </p>
              <p class="text-[11px] text-faint">
                统一身份中心
              </p>
            </div>
            <ThemeToggle variant="subtle" />
          </div>
        </template>

        <h1 class="text-lg font-semibold text-ink">
          {{ title }}
        </h1>
        <p class="mt-1 text-[13px] leading-5 text-dim">
          {{ subtitle }}
        </p>

        <div class="mt-6">
          <slot />
        </div>

        <template #footer>
          <p class="text-center text-[11px] text-faint">
            NitroWater SSO · OIDC
          </p>
        </template>
      </ElCard>
    </div>
  </div>
</template>

<style scoped>
/* Element Plus pads its card tighter than the previous hand-rolled shell. */
.auth-panel :deep(.el-card__header) {
  padding: 14px 22px;
  border-bottom-color: var(--color-line);
}

.auth-panel :deep(.el-card__body) {
  padding: 22px 22px 18px;
}

.auth-panel :deep(.el-card__footer) {
  padding: 12px 22px 16px;
  border-top-color: var(--color-line);
}
</style>
