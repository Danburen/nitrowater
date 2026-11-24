<script setup lang="ts">
/**
 * Label / hint / error shell for form controls.
 *
 * The control itself stays native (input, textarea, select, checkbox...), while this component
 * keeps the surrounding typography and spacing consistent across the site.
 */
defineProps<{
  label?: string
  htmlFor?: string
  hint?: string
  error?: string
  required?: boolean
}>()
</script>

<template>
  <div class="flex flex-col gap-1.5">
    <div
      v-if="label || $slots.labelExtra"
      class="flex min-h-[18px] items-center justify-between gap-2"
    >
      <label
        v-if="label"
        :for="htmlFor"
        class="text-[12.5px] font-medium text-ink"
      >
        {{ label }}<span
          v-if="required"
          class="ml-0.5 text-danger"
        >*</span>
      </label>
      <slot name="labelExtra" />
    </div>

    <slot />

    <p
      v-if="error"
      role="alert"
      class="text-[11.5px] leading-relaxed text-danger"
    >
      {{ error }}
    </p>
    <p
      v-else-if="hint"
      class="text-[11.5px] leading-relaxed text-faint"
    >
      {{ hint }}
    </p>
  </div>
</template>
