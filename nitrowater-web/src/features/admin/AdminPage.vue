<script setup lang="ts">
/**
 * Admin page chrome: title, description and an action slot above the page body.
 *
 * <p>Kept deliberately thin so every admin route shares the same spacing and header rhythm; the
 * body itself is free to use Element Plus controls or a bespoke widget.</p>
 *
 * <p>The container values are the single source of truth for admin page margins: routes must not
 * add their own {@code p-*}/{@code mx-*} wrappers, and the body slot already applies a uniform
 * vertical gap between blocks.</p>
 */
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <div class="mx-auto w-full max-w-[1120px] px-4 py-5 sm:px-6 sm:py-6">
    <div class="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div class="min-w-0">
        <h1 class="text-lg font-semibold text-ink">
          {{ title }}
        </h1>
        <p
          v-if="description"
          class="mt-1 text-[13px] text-dim"
        >
          {{ description }}
        </p>
      </div>
      <div class="admin-actions">
        <slot name="actions" />
      </div>
    </div>
    <div class="space-y-4">
      <slot />
    </div>
  </div>
</template>
