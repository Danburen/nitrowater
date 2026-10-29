<script setup lang="ts">
/**
 * Shared multi-line text input without undo history.
 *
 * Use TextEditor when undo/redo or selection wrapping is required; use TextArea for ordinary
 * multi-line form fields.
 */
withDefaults(
  defineProps<{
    modelValue: string
    id?: string
    placeholder?: string
    rows?: number
    maxlength?: number
    disabled?: boolean
    readonly?: boolean
    required?: boolean
    invalid?: boolean
    mono?: boolean
    minHeight?: string
    ariaLabel?: string
    spellcheck?: boolean
  }>(),
  {
    id: undefined,
    placeholder: '',
    rows: 4,
    maxlength: undefined,
    disabled: false,
    readonly: false,
    required: false,
    invalid: false,
    mono: true,
    minHeight: '120px',
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

function onInput(event: Event): void {
  const target = event.target as HTMLTextAreaElement
  emit('update:modelValue', target.value)
  emit('input', target.value, event)
}

function onChange(event: Event): void {
  emit('change', (event.target as HTMLTextAreaElement).value, event)
}

function onFocus(event: FocusEvent): void {
  emit('focus', event)
}

function onBlur(event: FocusEvent): void {
  emit('blur', event)
}
</script>

<template>
  <textarea
    :id="id"
    :value="modelValue"
    :placeholder="placeholder"
    :rows="rows"
    :maxlength="maxlength"
    :disabled="disabled"
    :readonly="readonly"
    :required="required"
    :aria-label="ariaLabel"
    :spellcheck="spellcheck"
    :style="{ minHeight }"
    class="textarea"
    :class="[
      invalid ? 'border-danger/60' : '',
      mono ? '' : 'font-sans',
      disabled ? 'cursor-not-allowed opacity-60' : '',
    ]"
    @input="onInput"
    @change="onChange"
    @focus="onFocus"
    @blur="onBlur"
  />
</template>
