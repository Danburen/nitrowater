<script setup lang="ts">
/**
 * One danger-zone action: the name on the left, the control on the right.
 *
 * <p>Same row geometry as {@code AdminSettingsRow} (`px-4 py-3`, hairlines drawn by the zone), so a
 * danger entry is the same height as any settings entry. A danger-zone entry is a single
 * self-contained action, so the only text is its name and the only explanation lives in the confirm
 * dialog. Stacks below {@code sm} so a wide button never squeezes the label to nothing.</p>
 */
defineProps<{
  label: string
}>()
</script>

<template>
  <div class="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:gap-8">
    <span class="min-w-0 text-[13px] text-ink">{{ label }}</span>
    <div class="flex shrink-0 items-center gap-2 sm:justify-end">
      <slot />
    </div>
  </div>
</template>
