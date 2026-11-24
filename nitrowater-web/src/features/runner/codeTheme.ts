/**
 * CodeMirror theme for the code runner, wired to the app's design tokens so the editor blends
 * with the surrounding panel in both themes. Dark mode layers CodeMirror's `oneDark` syntax
 * palette UNDER this chrome theme so backgrounds/gutters stay transparent.
 */
import { EditorView } from '@codemirror/view'
import type { Extension } from '@codemirror/state'
import { oneDark } from '@codemirror/theme-one-dark'

const chrome = EditorView.theme({
  '&': {
    height: '100%',
    fontSize: '13px',
    backgroundColor: 'transparent',
    color: 'var(--color-ink)',
  },
  '.cm-scroller': {
    fontFamily: 'var(--font-mono)',
    lineHeight: '1.7',
  },
  '.cm-content': {
    padding: '12px 0',
    caretColor: 'var(--color-accent)',
  },
  '.cm-gutters': {
    backgroundColor: 'transparent',
    color: 'var(--color-faint)',
    borderRight: '1px solid var(--color-line)',
  },
  '.cm-activeLine': {
    backgroundColor: 'color-mix(in srgb, var(--color-accent) 10%, transparent)',
  },
  '.cm-activeLineGutter': {
    backgroundColor: 'transparent',
    color: 'var(--color-accent)',
  },
  '.cm-selectionBackground, ::selection': {
    backgroundColor: 'color-mix(in srgb, var(--color-accent) 30%, transparent)',
  },
  '&.cm-focused .cm-selectionBackground, &.cm-focused ::selection': {
    backgroundColor: 'color-mix(in srgb, var(--color-accent) 38%, transparent)',
  },
  '.cm-cursor, .cm-dropCursor': {
    borderLeftColor: 'var(--color-accent)',
  },
  '&.cm-focused': {
    outline: 'none',
  },
})

/** Build the editor extensions for the resolved app theme. */
export function editorTheme(dark: boolean): Extension {
  return dark ? [oneDark, chrome] : [chrome]
}
