/**
 * Minecraft MOTD parsing and serialization.
 *
 * Supports the legacy `&` / `§` formatting codes, `§x§R§R§G§G§B§B` hex colors, the common
 * `&#RRGGBB` shorthand, styles (bold / italic / underline / strike / obfuscated) and newlines.
 * Outputs are pure strings so the same parsed model can feed the preview, `server.properties`
 * escaping and a JSON text component for `/tellraw`-style use.
 */
import { escapePropertyValue } from './escape'

const SECTION = '\u00A7'

export interface MotdStyle {
  /** Lower-case `#rrggbb`, or `null` for the default color. */
  color: string | null
  bold: boolean
  italic: boolean
  underline: boolean
  strikethrough: boolean
  obfuscated: boolean
}

export interface MotdSegment extends MotdStyle {
  text: string
}

export interface MotdParseResult {
  lines: MotdSegment[][]
  warnings: string[]
  /** True when the input used `&` codes (vanilla `server.properties` only understands `§`). */
  usesAmpersand: boolean
}

export interface LegacyColor {
  code: string
  label: string
  hex: string
  /** JSON text component color name (kept for pre-1.16 compatibility). */
  json: string
}

export const legacyColors: ReadonlyArray<LegacyColor> = [
  { code: '0', label: '黑', hex: '#000000', json: 'black' },
  { code: '1', label: '深蓝', hex: '#0000aa', json: 'dark_blue' },
  { code: '2', label: '深绿', hex: '#00aa00', json: 'dark_green' },
  { code: '3', label: '深青', hex: '#00aaaa', json: 'dark_aqua' },
  { code: '4', label: '深红', hex: '#aa0000', json: 'dark_red' },
  { code: '5', label: '深紫', hex: '#aa00aa', json: 'dark_purple' },
  { code: '6', label: '金', hex: '#ffaa00', json: 'gold' },
  { code: '7', label: '灰', hex: '#aaaaaa', json: 'gray' },
  { code: '8', label: '深灰', hex: '#555555', json: 'dark_gray' },
  { code: '9', label: '蓝', hex: '#5555ff', json: 'blue' },
  { code: 'a', label: '绿', hex: '#55ff55', json: 'green' },
  { code: 'b', label: '青', hex: '#55ffff', json: 'aqua' },
  { code: 'c', label: '红', hex: '#ff5555', json: 'red' },
  { code: 'd', label: '浅紫', hex: '#ff55ff', json: 'light_purple' },
  { code: 'e', label: '黄', hex: '#ffff55', json: 'yellow' },
  { code: 'f', label: '白', hex: '#ffffff', json: 'white' },
]

export const formatCodes: ReadonlyArray<{ code: string; label: string; title: string }> = [
  { code: 'l', label: 'B', title: '粗体' },
  { code: 'o', label: 'I', title: '斜体' },
  { code: 'n', label: 'U', title: '下划线' },
  { code: 'm', label: 'S', title: '删除线' },
  { code: 'k', label: '?', title: '混淆文字' },
  { code: 'r', label: 'R', title: '重置格式' },
]

const colorByCode = new Map(legacyColors.map((color) => [color.code, color.hex]))
const colorByHex = new Map(legacyColors.map((color) => [color.hex, color]))
const jsonColorByCode = new Map(legacyColors.map((color) => [color.code, color.json]))

function createStyle(): MotdStyle {
  return {
    color: null,
    bold: false,
    italic: false,
    underline: false,
    strikethrough: false,
    obfuscated: false,
  }
}

function sameStyle(a: MotdStyle, b: MotdStyle): boolean {
  return (
    a.color === b.color
    && a.bold === b.bold
    && a.italic === b.italic
    && a.underline === b.underline
    && a.strikethrough === b.strikethrough
    && a.obfuscated === b.obfuscated
  )
}

function pushSegment(line: MotdSegment[], style: MotdStyle, text: string): void {
  if (!text) return
  const last = line[line.length - 1]
  if (last && sameStyle(last, style)) {
    last.text += text
    return
  }
  line.push({ ...style, text })
}

/** Push text into the current line, starting a new line for every `\n`. */
function pushText(lines: MotdSegment[][], text: string, style: MotdStyle): void {
  if (!text) return
  const parts = text.split('\n')
  parts.forEach((part, index) => {
    if (index > 0) lines.push([])
    pushSegment(lines[lines.length - 1], style, part)
  })
}

/** Read the six `§R§R§G§G§B§B` pairs after `§x`; returns `#rrggbb` or `null`. */
function parseHexSequence(text: string, start: number): string | null {
  let hex = ''
  for (let index = 0; index < 6; index += 1) {
    const marker = text[start + index * 2]
    const digit = text[start + index * 2 + 1]
    if ((marker !== '&' && marker !== SECTION) || !digit || !/^[0-9a-fA-F]$/.test(digit)) {
      return null
    }
    hex += digit.toLowerCase()
  }
  return `#${hex}`
}

/**
 * Parse a MOTD string into styled lines.
 *
 * Literal `\n` sequences are treated as line breaks as a convenience for values copied from
 * `server.properties`; actual newlines work too.
 */
export function parseMotd(input: string): MotdParseResult {
  const warnings: string[] = []
  let usesAmpersand = false
  const normalized = input.replace(/\r\n?/g, '\n').replace(/\\n/g, '\n')
  const lines: MotdSegment[][] = [[]]
  let style = createStyle()
  let buffer = ''

  const flush = (): void => {
    if (!buffer) return
    pushText(lines, buffer, style)
    buffer = ''
  }

  const applyColor = (hex: string): void => {
    style = { ...createStyle(), color: hex }
  }

  let index = 0
  while (index < normalized.length) {
    const char = normalized[index]
    if (char !== '&' && char !== SECTION) {
      buffer += char
      index += 1
      continue
    }

    const next = normalized[index + 1]

    if (next === '#') {
      const hex = normalized.slice(index + 2, index + 8)
      if (/^[0-9a-fA-F]{6}$/.test(hex)) {
        if (char === '&') usesAmpersand = true
        flush()
        applyColor(`#${hex.toLowerCase()}`)
        index += 8
        continue
      }
      warnings.push('检测到不完整的十六进制颜色，已按普通文本处理。')
      buffer += char
      index += 1
      continue
    }

    if (next === 'x' || next === 'X') {
      const hex = parseHexSequence(normalized, index + 2)
      if (hex) {
        if (char === '&') usesAmpersand = true
        flush()
        applyColor(hex)
        index += 14
        continue
      }
      warnings.push('检测到不完整的 §x 十六进制颜色，已按普通文本处理。')
      buffer += char
      index += 1
      continue
    }

    const code = next?.toLowerCase()
    if (!code) {
      buffer += char
      index += 1
      continue
    }

    const color = colorByCode.get(code)
    if (color) {
      if (char === '&') usesAmpersand = true
      flush()
      applyColor(color)
      index += 2
      continue
    }

    if (code === 'k' || code === 'l' || code === 'm' || code === 'n' || code === 'o') {
      if (char === '&') usesAmpersand = true
      flush()
      if (code === 'k') style.obfuscated = true
      else if (code === 'l') style.bold = true
      else if (code === 'm') style.strikethrough = true
      else if (code === 'n') style.underline = true
      else style.italic = true
      index += 2
      continue
    }

    if (code === 'r') {
      if (char === '&') usesAmpersand = true
      flush()
      style = createStyle()
      index += 2
      continue
    }

    buffer += char
    index += 1
  }

  flush()
  if (lines.length > 2) warnings.push('服务器列表通常只显示 MOTD 的前 2 行。')
  return { lines, warnings, usesAmpersand }
}

function colorCode(hex: string): string {
  const legacy = colorByHex.get(hex.toLowerCase())
  if (legacy) return SECTION + legacy.code
  const digits = hex.replace('#', '').toUpperCase()
  return SECTION + 'x' + digits.split('').map((digit) => SECTION + digit).join('')
}

/** Serialize styled lines back to a legacy `§` string. */
export function toLegacy(lines: MotdSegment[][]): string {
  return lines.map((line) => {
    let out = ''
    let current = createStyle()
    for (const segment of line) {
      if (!segment.text) continue
      const needsReset = current.color !== null
        || current.bold
        || current.italic
        || current.underline
        || current.strikethrough
        || current.obfuscated
      if (needsReset) {
        out += SECTION + 'r'
        current = createStyle()
      }
      if (segment.color) {
        out += colorCode(segment.color)
        current.color = segment.color
      }
      if (segment.bold) {
        out += SECTION + 'l'
        current.bold = true
      }
      if (segment.italic) {
        out += SECTION + 'o'
        current.italic = true
      }
      if (segment.underline) {
        out += SECTION + 'n'
        current.underline = true
      }
      if (segment.strikethrough) {
        out += SECTION + 'm'
        current.strikethrough = true
      }
      if (segment.obfuscated) {
        out += SECTION + 'k'
        current.obfuscated = true
      }
      out += segment.text
      current = { ...segment }
    }
    return out
  }).join('\n')
}

/** Serialize as the `motd=...` line for `server.properties`. */
export function toProperties(lines: MotdSegment[][], escapeUnicode = true): string {
  return `motd=${escapePropertyValue(toLegacy(lines), escapeUnicode)}`
}

interface JsonComponent {
  text: string
  color?: string
  bold?: boolean
  italic?: boolean
  underlined?: boolean
  strikethrough?: boolean
  obfuscated?: boolean
}

function jsonColor(hex: string): string {
  const legacy = colorByHex.get(hex.toLowerCase())
  if (!legacy) return hex
  return jsonColorByCode.get(legacy.code) ?? hex
}

/** Serialize as a JSON text component (for `/tellraw`, books, signs, etc.). */
export function toJson(lines: MotdSegment[][]): string {
  const extra: JsonComponent[] = []
  lines.forEach((line, lineIndex) => {
    if (lineIndex > 0) extra.push({ text: '\n' })
    for (const segment of line) {
      if (!segment.text) continue
      const component: JsonComponent = { text: segment.text }
      if (segment.color) component.color = jsonColor(segment.color)
      if (segment.bold) component.bold = true
      if (segment.italic) component.italic = true
      if (segment.underline) component.underlined = true
      if (segment.strikethrough) component.strikethrough = true
      if (segment.obfuscated) component.obfuscated = true
      extra.push(component)
    }
  })
  const root = extra.length > 0 ? { text: '', extra } : { text: '' }
  return JSON.stringify(root, null, 2)
}

/** Strip all formatting codes (useful for a plain-text copy). */
export function toPlain(lines: MotdSegment[][]): string {
  return lines.map((line) => line.map((segment) => segment.text).join('')).join('\n')
}
