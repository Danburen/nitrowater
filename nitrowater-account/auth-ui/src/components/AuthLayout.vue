<script setup lang="ts">
import { ThemeToggle } from '@nitrowater/ui'

defineProps<{
  title: string
  subtitle: string
}>()
</script>

<template>
  <div class="auth-page">
    <div class="auth-shell">
      <header class="auth-brand">
        <div class="min-w-0 flex-1">
          <p class="text-[15px] font-semibold leading-tight text-ink">
            NitroWater
          </p>
          <p class="text-[11px] text-faint">
            统一身份中心
          </p>
        </div>
        <ThemeToggle variant="subtle" />
      </header>

      <main class="auth-card">
        <h1 class="text-lg font-semibold text-ink">
          {{ title }}
        </h1>
        <p class="mt-1 text-[13px] leading-5 text-dim">
          {{ subtitle }}
        </p>

        <div class="mt-6">
          <slot />
        </div>
      </main>

      <p class="auth-footer">
        NitroWater SSO · OIDC
      </p>
    </div>
  </div>
</template>
