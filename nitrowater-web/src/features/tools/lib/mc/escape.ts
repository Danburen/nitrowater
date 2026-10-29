/**
 * Java `.properties` escaping helpers.
 *
 * `server.properties` is a Java properties file: backslashes, newlines and (optionally)
 * non-ASCII characters must be escaped so the server reads the intended value on every host.
 */

/** Escape a value for the right-hand side of `key=value`. */
export function escapePropertyValue(value: string, escapeUnicode: boolean): string {
  let out = ''
  for (const char of String(value)) {
    const code = char.codePointAt(0) ?? 0
    if (char === '\\') {
      out += '\\\\'
    } else if (char === '\n') {
      out += '\\n'
    } else if (char === '\r') {
      out += '\\r'
    } else if (char === '\t') {
      out += '\\t'
    } else if (escapeUnicode && (code < 0x20 || code > 0x7e)) {
      out += `\\u${code.toString(16).padStart(4, '0')}`
    } else {
      out += char
    }
  }
  // Java properties skips leading whitespace after the separator unless it is escaped.
  return out.replace(/^ +/, (spaces) => spaces.replace(/ /g, '\\ '))
}

/** Decode a Java properties value (the inverse of {@link escapePropertyValue}). */
export function unescapePropertyValue(value: string): string {
  return value.replace(/\\u([0-9a-fA-F]{4})|\\(.)/g, (_match, hex: string | undefined, char: string | undefined) => {
    if (hex) return String.fromCharCode(Number.parseInt(hex, 16))
    switch (char) {
      case 'n':
        return '\n'
      case 'r':
        return '\r'
      case 't':
        return '\t'
      case 'f':
        return '\f'
      default:
        return char ?? ''
    }
  })
}
