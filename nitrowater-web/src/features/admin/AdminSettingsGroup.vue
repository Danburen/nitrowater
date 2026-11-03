<script setup lang="ts">
/**
 * One settings group, rendered as its own card.
 *
 * <p>The settings page itself is card-less: groups are the only card-level chrome, so each concern
 * (quota, feature switches, moderation) reads as a self-contained block with its own heading. Rows
 * inside a group are separated by hairlines, never by gaps.</p>
 */
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <section class="overflow-hidden rounded-[14px] border border-line bg-card">
    <header class="border-b border-line px-4 py-3">
      <h2 class="text-[13.5px] font-semibold text-ink">
        {{ title }}
      </h2>
      <p
        v-if="description"
        class="mt-0.5 text-[12px] leading-relaxed text-faint"
      >
        {{ description }}
      </p>
    </header>
    <!-- Hairlines between rows only: divide-y never paints a border above the first one. -->
    <div class="divide-y divide-line">
      <slot />
    </div>
  </section>
</template>
