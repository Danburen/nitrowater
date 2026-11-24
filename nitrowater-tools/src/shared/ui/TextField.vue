<script setup lang="ts">
/**
 * Shared single-line input.
 *
 * Values stay strings even for `type="number"` so callers can keep form state string-based
 * (server.properties values are strings). Native input semantics are preserved.
 */
import { computed, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: string
    id?: string
    type?: 'text' | 'password' | 'email' | 'url' | 'search' | 'tel' | 'number'
    placeholder?: string
    name?: string
    disabled?: boolean
    readonly?: boolean
    required?: boolean
    clearable?: boolean
    invalid?: boolean
    mono?: boolean
    maxlength?: number
    min?: number | string
    max?: number | string
    step?: number | string
    autocomplete?: string
    inputmode?: 'none' | 'text' | 'decimal' | 'numeric' | 'tel' | 'search' | 'email' | 'url'
    list?: string
    ariaLabel?: string
    spellcheck?: boolean
  }>(),
  {
    id: undefined,
    type: 'text',
    placeholder: '',
    name: undefined,
    disabled: false,
    readonly: false,
    required: false,
    clearable: false,
    invalid: false,
    mono: false,
    maxlength: undefined,
    min: undefined,
    max: undefined,
    step: undefined,
    autocomplete: 'off',
    inputmode: undefined,
    list: undefined,
    ariaLabel: undefined,
    spellcheck: false,
  },
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
  (event: 'input', value: string, domEvent: Event): void
  (event: 'change', value: string, domEvent: Event): void
  (event: 'focus', domEvent: FocusEvent): void
  (event: 'blur', domEvent: FocusEvent): void
}>()

const inputRef = ref<HTMLInputElement | null>(null)
const showClear = computed(
  () => props.clearable && props.modelValue.length > 0 && !props.disabled && !props.readonly,
)

function onInput(event: Event): void {
  const target = event.target as HTMLInputElement
  emit('update:modelValue', target.value)
  emit('input', target.value, event)
}

function onChange(event: Event): void {
  emit('change', (event.target as HTMLInputElement).value, event)
}

function onFocus(event: FocusEvent): void {
  emit('focus', event)
}

function onBlur(event: FocusEvent): void {
  emit('blur', event)
}

function clear(): void {
  emit('update:modelValue', '')
  inputRef.value?.focus()
}
</script>

<template>
  <div class="relative">
    <input
      :id="id"
      ref="inputRef"
      :value="modelValue"
      :type="type"
      :placeholder="placeholder"
      :name="name"
      :disabled="disabled"
      :readonly="readonly"
      :required="required"
      :maxlength="maxlength"
      :min="min"
      :max="max"
      :step="step"
      :autocomplete="autocomplete"
      :inputmode="inputmode"
      :list="list"
      :aria-label="ariaLabel"
      :spellcheck="spellcheck"
      class="input"
      :class="[
        invalid ? 'border-danger/60' : '',
        mono ? 'font-mono' : '',
        showClear ? 'pr-8' : '',
        disabled ? 'cursor-not-allowed opacity-60' : '',
      ]"
      @input="onInput"
      @change="onChange"
      @focus="onFocus"
      @blur="onBlur"
    >
    <button
      v-if="showClear"
      type="button"
      class="absolute right-2 top-1/2 grid size-5 -translate-y-1/2 place-items-center rounded text-faint transition hover:bg-soft hover:text-ink"
      aria-label="清空"
      title="清空"
      @click="clear"
    >
      ×
    </button>
  </div>
</template>
