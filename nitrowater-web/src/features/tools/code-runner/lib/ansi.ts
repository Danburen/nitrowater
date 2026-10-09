/**
 * ansiToHtml — convert ANSI SGR escape sequences to HTML spans with inline styles.
 *
 * Ported from wtools (MIT) `src/utils/ansiToHtml.ts`. Handles the common SGR codes emitted by
 * Python / CLIs: reset, bold, italic, underline, 30–37 / 90–97 foreground, 40–47 / 100–107
 * background. The palette targets a dark terminal background, which is what the output pane
 * uses in both light and dark app themes.
 */

// Map ANSI color code → CSS color (dark terminal theme)
const FG: Record<number, string> = {
  30: '#9ca3af', // black → gray (visible on dark bg)
  31: '#f87171', // red
  32: '#4ade80', // green
  33: '#facc15', // yellow
  34: '#60a5fa', // blue
  35: '#c084fc', // magenta
  36: '#22d3ee', // cyan
  37: '#f3f4f6', // white
  90: '#6b7280', // bright black (dim gray)
  91: '#ef4444', // bright red
  92: '#22c55e', // bright green
  93: '#eab308', // bright yellow
  94: '#3b82f6', // bright blue
  95: '#a855f7', // bright magenta
  96: '#06b6d4', // bright cyan
  97: '#ffffff', // bright white
}

const BG: Record<number, string> = {
  40: '#374151',
  41: '#7f1d1d',
  42: '#14532d',
  43: '#713f12',
  44: '#1e3a5f',
  45: '#4c1d95',
  46: '#164e63',
  47: '#1f2937',
  100: '#4b5563',
  101: '#991b1b',
  102: '#166534',
  103: '#854d0e',
  104: '#1e40af',
  105: '#581c87',
  106: '#155e75',
  107: '#374151',
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

export function ansiToHtml(text: string): string {
  // Match ANSI escape sequences: ESC[<params>m
  // eslint-disable-next-line no-control-regex -- matching a literal ESC is the whole point
  const ansiRe = /\u001b\[(\d+(?:;\d+)*)m/g

  const parts: string[] = []
  let lastIdx = 0
  let match: RegExpExecArray | null

  // Style stack
  let bold = false
  let italic = false
  let underline = false
  let fg: string | undefined
  let bg: string | undefined

  function flushStyle(): string {
    const styles: string[] = []
    if (bold) styles.push('font-weight:700')
    if (italic) styles.push('font-style:italic')
    if (underline) styles.push('text-decoration:underline')
    if (fg) styles.push(`color:${fg}`)
    if (bg) styles.push(`background:${bg}`)
    return styles.length > 0 ? ` style="${styles.join(';')}"` : ''
  }

  function resetAll(): void {
    bold = false
    italic = false
    underline = false
    fg = undefined
    bg = undefined
  }

  while ((match = ansiRe.exec(text)) !== null) {
    // Push plain text before this match
    if (match.index > lastIdx) {
      const raw = text.slice(lastIdx, match.index)
      const cls = flushStyle()
      parts.push(cls ? `<span${cls}>${escapeHtml(raw)}</span>` : escapeHtml(raw))
    }
    lastIdx = ansiRe.lastIndex

    // Process codes
    const codes = match[1].split(';').map(Number)
    for (const code of codes) {
      if (code === 0) resetAll()
      else if (code === 1) bold = true
      else if (code === 3) italic = true
      else if (code === 4) underline = true
      else if (code === 22) bold = false
      else if (code === 23) italic = false
      else if (code === 24) underline = false
      else if ((code >= 30 && code <= 37) || (code >= 90 && code <= 97)) fg = FG[code]
      else if ((code >= 40 && code <= 47) || (code >= 100 && code <= 107)) bg = BG[code]
      else if (code === 39) fg = undefined
      else if (code === 49) bg = undefined
    }
  }

  // Remaining text after the last ANSI code
  if (lastIdx < text.length) {
    const raw = text.slice(lastIdx)
    const cls = flushStyle()
    parts.push(cls ? `<span${cls}>${escapeHtml(raw)}</span>` : escapeHtml(raw))
  }

  return parts.join('')
}
