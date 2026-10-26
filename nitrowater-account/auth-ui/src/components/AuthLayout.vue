<script setup lang="ts">
import { ThemeToggle } from '@nitrowater/ui'

defineProps<{
  title: string
  subtitle: string
}>()
</script>

<template>
  <div class="auth-page">
    <main class="auth-card auth-fade-up">
      <aside class="auth-brand-panel">
        <div class="relative z-10 flex items-center gap-3">
          <span
            class="grid h-11 w-11 place-items-center rounded-2xl bg-white/10 ring-1 ring-white/15 backdrop-blur"
          >
            <svg
              viewBox="0 0 64 64"
              class="h-7 w-7"
              aria-hidden="true"
            >
              <defs>
                <linearGradient
                  id="nw-auth-logo"
                  x1="10%"
                  y1="0%"
                  x2="90%"
                  y2="100%"
                >
                  <stop
                    offset="0%"
                    stop-color="#7fb5ff"
                  />
                  <stop
                    offset="100%"
                    stop-color="#7c4dff"
                  />
                </linearGradient>
              </defs>
              <path
                d="M32 5C32 5 9 25.4 9 38.2 9 50.4 19.3 59 32 59s23-8.6 23-20.8C55 25.4 32 5 32 5Z"
                fill="url(#nw-auth-logo)"
              />
              <path
                d="M23 40.5c.7 6 5.1 10.5 11 11.5"
                fill="none"
                stroke="rgba(255,255,255,.85)"
                stroke-linecap="round"
                stroke-width="3"
              />
            </svg>
          </span>
          <div>
            <p class="text-[15px] font-semibold tracking-tight text-white">
              NitroWater
            </p>
            <p class="text-[11px] tracking-[0.18em] text-white/55">
              IDENTITY PLATFORM
            </p>
          </div>
        </div>

        <div class="relative z-10 mt-10 max-w-sm lg:mt-0">
          <p class="text-[11px] font-semibold tracking-[0.22em] text-white/50">
            NITROWATER IDENTITY
          </p>
          <h1 class="mt-3 text-3xl font-semibold leading-tight tracking-tight text-white sm:text-4xl">
            一个账号，<br class="hidden sm:block">连接所有应用
          </h1>
          <p class="mt-4 text-sm leading-6 text-white/65">
            统一身份中心为 NitroWater 门户、管理台与业务应用提供一致、安全的登录体验。
          </p>

          <ul class="mt-8 hidden space-y-3.5 lg:block">
            <li class="flex items-center gap-3 text-sm text-white/70">
              <span class="grid h-7 w-7 place-items-center rounded-lg bg-white/10 text-white/80">
                <svg
                  viewBox="0 0 24 24"
                  class="h-4 w-4"
                  fill="none"
                  aria-hidden="true"
                >
                  <path
                    d="M12 3l7 3v5c0 4.4-2.9 8.2-7 9.5-4.1-1.3-7-5.1-7-9.5V6l7-3Z"
                    stroke="currentColor"
                    stroke-linejoin="round"
                    stroke-width="1.6"
                  />
                  <path
                    d="M9 12l2 2 4-4"
                    stroke="currentColor"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="1.6"
                  />
                </svg>
              </span>
              单点登录，一次认证通行全部应用
            </li>
            <li class="flex items-center gap-3 text-sm text-white/70">
              <span class="grid h-7 w-7 place-items-center rounded-lg bg-white/10 text-white/80">
                <svg
                  viewBox="0 0 24 24"
                  class="h-4 w-4"
                  fill="none"
                  aria-hidden="true"
                >
                  <path
                    d="M8 11V8a4 4 0 118 0v3M7 11h10a1 1 0 011 1v7a1 1 0 01-1 1H7a1 1 0 01-1-1v-7a1 1 0 011-1Z"
                    stroke="currentColor"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="1.6"
                  />
                </svg>
              </span>
              设备指纹与会话安全
            </li>
            <li class="flex items-center gap-3 text-sm text-white/70">
              <span class="grid h-7 w-7 place-items-center rounded-lg bg-white/10 text-white/80">
                <svg
                  viewBox="0 0 24 24"
                  class="h-4 w-4"
                  fill="none"
                  aria-hidden="true"
                >
                  <path
                    d="M8 7h8M8 12h8M8 17h5M5 4h14a1 1 0 011 1v14a1 1 0 01-1 1H5a1 1 0 01-1-1V5a1 1 0 011-1Z"
                    stroke="currentColor"
                    stroke-linecap="round"
                    stroke-width="1.6"
                  />
                </svg>
              </span>
              标准化 OIDC，对接现有与未来系统
            </li>
          </ul>
        </div>

        <div class="relative z-10 mt-9 hidden items-center gap-3 text-[11px] text-white/45 lg:flex">
          <span class="rounded-full border border-white/10 bg-white/5 px-2.5 py-1">OIDC</span>
          <span class="rounded-full border border-white/10 bg-white/5 px-2.5 py-1">SSO</span>
          <span class="rounded-full border border-white/10 bg-white/5 px-2.5 py-1">Session</span>
        </div>

        <span class="auth-ring -right-24 -top-24 h-80 w-80" />
        <span class="auth-ring -bottom-28 -left-20 h-64 w-64" />
        <span class="auth-ring bottom-16 right-12 h-24 w-24" />
      </aside>

      <section class="relative flex flex-col bg-card p-7 sm:p-10 lg:p-12">
        <div class="flex items-center justify-between gap-4">
          <span class="text-[11px] font-semibold tracking-[0.16em] text-faint">
            NITROWATER SSO
          </span>
          <ThemeToggle variant="subtle" />
        </div>

        <div class="my-auto py-10 lg:py-12">
          <div class="mx-auto w-full max-w-sm">
            <h2 class="text-2xl font-semibold tracking-tight text-ink sm:text-[28px]">
              {{ title }}
            </h2>
            <p class="mt-2 text-sm leading-6 text-dim">
              {{ subtitle }}
            </p>
            <div class="mt-7">
              <slot />
            </div>
          </div>
        </div>

        <div class="auth-footer">
          NitroWater SSO · OIDC · 安全会话
        </div>
      </section>
    </main>
  </div>
</template>
