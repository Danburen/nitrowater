<script setup lang="ts">
/**
 * One settings entry: the name (and optional hint) on the left, the control on the right.
 *
 * <p>Carries its own horizontal padding so a group card can run full-bleed hairline separators
 * between rows. Stacks below {@code sm} so a wide control never squeezes the label to nothing. The
 * hint doubles as a slot for rows whose explanation is dynamic.</p>
 */
defineProps<{
  label: string
  hint?: string
}>()
</script>

<template>
  <div class="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:gap-8">
    <div class="min-w-0">
      <div class="text-[13px] leading-snug text-ink">
        {{ label }}
      </div>
      <p
        v-if="hint || $slots.hint"
        class="mt-1 text-[12px] leading-relaxed text-faint"
      >
        <slot name="hint">
          {{ hint }}
        </slot>
      </p>
    </div>
    <div class="flex shrink-0 items-center gap-2 sm:justify-end">
      <slot />
    </div>
  </div>
</template>
