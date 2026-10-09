<script setup lang="ts">
/**
 * Reusable CodeMirror 6 wrapper for the code runner.
 * Language highlighting is a Compartment so it can be reconfigured without rebuilding the view;
 * the theme Compartment follows the app theme reactively. Ported/adapted from wtools (MIT)
 * `src/components/CodeEditor.vue`.
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  EditorView,
  drawSelection,
  highlightActiveLine,
  keymap,
  lineNumbers,
} from '@codemirror/view'
import { Compartment, EditorState } from '@codemirror/state'
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands'
import {
  bracketMatching,
  defaultHighlightStyle,
  indentOnInput,
  syntaxHighlighting,
} from '@codemirror/language'
import { autocompletion, closeBrackets, completionKeymap } from '@codemirror/autocomplete'
import { python } from '@codemirror/lang-python'
import { useTheme } from '../../../shared/theme/useTheme'
import { editorTheme } from './codeTheme'

const props = defineProps<{
  modelValue: string
  language?: 'python'
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const { theme } = useTheme()
const host = ref<HTMLDivElement>()

let view: EditorView | null = null
let resizeObserver: ResizeObserver | null = null

const languageCompartment = new Compartment()
const themeCompartment = new Compartment()

function languageExtension() {
  return python()
}

onMounted(() => {
  view = new EditorView({
    state: EditorState.create({
      doc: props.modelValue,
      extensions: [
        lineNumbers(),
        drawSelection(),
        highlightActiveLine(),
        EditorState.allowMultipleSelections.of(true),
        history(),
        indentOnInput(),
        bracketMatching(),
        closeBrackets(),
        autocompletion(),
        syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
        languageCompartment.of(languageExtension()),
        themeCompartment.of(editorTheme(theme.value === 'dark')),
        EditorView.lineWrapping,
        EditorView.updateListener.of((update) => {
          if (update.docChanged) emit('update:modelValue', update.state.doc.toString())
        }),
        keymap.of([...defaultKeymap, ...historyKeymap, ...completionKeymap]),
      ],
    }),
    parent: host.value!,
  })

  // Re-measure when the container resizes (responsive layout, window resize).
  resizeObserver = new ResizeObserver(() => view?.requestMeasure())
  resizeObserver.observe(host.value!)
})

// Re-theme without rebuilding the editor instance.
watch(
  () => theme.value,
  (value) => {
    view?.dispatch({ effects: themeCompartment.reconfigure(editorTheme(value === 'dark')) })
  },
)

// Sync external content changes (reset / clear / language switch) into the editor.
watch(
  () => props.modelValue,
  (value) => {
    if (!view) return
    if (view.state.doc.toString() !== value) {
      view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value } })
    }
  },
)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  view?.destroy()
  view = null
})
</script>

<template>
  <div
    ref="host"
    class="h-full min-h-0 overflow-auto"
  />
</template>
