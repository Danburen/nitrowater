<script setup lang="ts">
/**
 * Lightweight plain-text editor used by the Minecraft tools.
 *
 * It intentionally stays dependency-free: a textarea plus a small snapshot history gives undo /
 * redo, selection-aware wrapping and keyboard shortcuts without pulling in a full editor.
 */
import { computed, nextTick, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    ariaLabel?: string
    minHeight?: string
    readonly?: boolean
    showToolbar?: boolean
    mono?: boolean
    invalid?: boolean
  }>(),
  {
    placeholder: '',
    ariaLabel: '文本编辑器',
    minHeight: '140px',
    readonly: false,
    showToolbar: true,
    mono: true,
    invalid: false,
  },
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
}>()

interface HistoryEntry {
  value: string
  start: number
  end: number
}

const textareaRef = ref<HTMLTextAreaElement | null>(null)
const value = ref(props.modelValue)
const history = ref<HistoryEntry[]>([{ value: props.modelValue, start: 0, end: 0 }])
const index = ref(0)
const lastSelection = ref({ start: 0, end: 0 })

const canUndo = computed(() => index.value > 0)
const canRedo = computed(() => index.value < history.value.length - 1)

let typing = false
let lastInputAt = 0

function rememberSelection(): void {
  const element = textareaRef.value
  if (!element) return
  lastSelection.value = { start: element.selectionStart, end: element.selectionEnd }
}

function readSelection(): { start: number; end: number } {
  const element = textareaRef.value
  if (!element) return lastSelection.value
  if (document.activeElement === element) {
    return { start: element.selectionStart, end: element.selectionEnd }
  }
  return lastSelection.value
}

function setSelection(start: number, end: number): void {
  lastSelection.value = { start, end }
  void nextTick(() => {
    const element = textareaRef.value
    if (!element) return
    element.focus()
    element.setSelectionRange(start, end)
  })
}

function pushHistory(next: string, start: number, end: number): void {
  history.value = history.value.slice(0, index.value + 1)
  history.value.push({ value: next, start, end })
  index.value = history.value.length - 1
}

function emitValue(next: string): void {
  value.value = next
  emit('update:modelValue', next)
}

function applyChange(next: string, start: number, end: number): void {
  emitValue(next)
  pushHistory(next, start, end)
  typing = false
  lastInputAt = 0
  setSelection(start, end)
}

function onInput(event: Event): void {
  const element = event.target as HTMLTextAreaElement
  const next = element.value
  emitValue(next)
  lastSelection.value = { start: element.selectionStart, end: element.selectionEnd }

  const now = Date.now()
  if (typing && now - lastInputAt < 700 && index.value === history.value.length - 1 && history.value.length > 1) {
    const entries = history.value.slice()
    entries[index.value] = { value: next, start: element.selectionStart, end: element.selectionEnd }
    history.value = entries
  } else {
    pushHistory(next, element.selectionStart, element.selectionEnd)
    typing = true
  }
  lastInputAt = now
}

function restore(entry: HistoryEntry): void {
  emitValue(entry.value)
  typing = false
  lastInputAt = 0
  setSelection(entry.start, entry.end)
}

function undo(): void {
  if (!canUndo.value) return
  index.value -= 1
  restore(history.value[index.value])
}

function redo(): void {
  if (!canRedo.value) return
  index.value += 1
  restore(history.value[index.value])
}

function onKeydown(event: KeyboardEvent): void {
  if (props.readonly || !(event.ctrlKey || event.metaKey)) return
  const key = event.key.toLowerCase()
  if (key === 'z') {
    event.preventDefault()
    if (event.shiftKey) redo()
    else undo()
  } else if (key === 'y') {
    event.preventDefault()
    redo()
  }
}

/** Wrap the current selection (or insert the prefix at the cursor when nothing is selected). */
function wrapSelection(prefix: string, suffix = ''): void {
  if (props.readonly) return
  const { start, end } = readSelection()
  const selected = value.value.slice(start, end)
  if (selected) {
    const next = value.value.slice(0, start) + prefix + selected + suffix + value.value.slice(end)
    applyChange(next, start + prefix.length, end + prefix.length)
  } else {
    const next = value.value.slice(0, start) + prefix + value.value.slice(start)
    applyChange(next, start + prefix.length, start + prefix.length)
  }
}

function insertText(text: string): void {
  if (props.readonly) return
  const { start, end } = readSelection()
  const next = value.value.slice(0, start) + text + value.value.slice(end)
  applyChange(next, start + text.length, start + text.length)
}

/** Insert after the selection without replacing it (used by reset codes). */
function insertAfterSelection(text: string): void {
  if (props.readonly) return
  const { end } = readSelection()
  const next = value.value.slice(0, end) + text + value.value.slice(end)
  applyChange(next, end + text.length, end + text.length)
}

function clear(): void {
  if (props.readonly) return
  applyChange('', 0, 0)
}

function focus(): void {
  textareaRef.value?.focus()
}

watch(
  () => props.modelValue,
  (next) => {
    if (next === value.value) return
    value.value = next
    pushHistory(next, next.length, next.length)
    typing = false
    lastInputAt = 0
  },
)

defineExpose({ focus, undo, redo, wrapSelection, insertText, insertAfterSelection, clear, canUndo, canRedo })
</script>

<template>
  <div
    class="overflow-hidden rounded-[9px] border bg-bg/60 transition focus-within:border-accent"
    :class="invalid ? 'border-red-500/60' : 'border-line'"
  >
    <div
      v-if="!readonly && showToolbar"
      class="flex items-center gap-1 border-b border-line bg-soft/40 px-2 py-1"
    >
      <button
        type="button"
        class="rounded-[6px] px-2 py-0.5 text-[11.5px] text-dim transition hover:bg-soft hover:text-ink disabled:cursor-not-allowed disabled:opacity-40"
        :disabled="!canUndo"
        title="撤销（Ctrl+Z）"
        @click="undo"
      >
        撤销
      </button>
      <button
        type="button"
        class="rounded-[6px] px-2 py-0.5 text-[11.5px] text-dim transition hover:bg-soft hover:text-ink disabled:cursor-not-allowed disabled:opacity-40"
        :disabled="!canRedo"
        title="重做（Ctrl+Shift+Z / Ctrl+Y）"
        @click="redo"
      >
        重做
      </button>
    </div>

    <textarea
      ref="textareaRef"
      :value="value"
      :readonly="readonly"
      :placeholder="placeholder"
      :aria-label="ariaLabel"
      :style="{ minHeight }"
      class="block w-full resize-y border-0 bg-transparent px-3 py-2 text-sm leading-relaxed text-ink outline-none placeholder:text-faint"
      :class="mono ? 'font-mono' : ''"
      spellcheck="false"
      autocomplete="off"
      autocapitalize="off"
      @input="onInput"
      @keydown="onKeydown"
      @select="rememberSelection"
      @keyup="rememberSelection"
      @mouseup="rememberSelection"
      @blur="rememberSelection"
    />
  </div>
</template>

<style scoped>
textarea::placeholder {
  color: var(--color-faint);
}
</style>
