/**
 * Java `.properties` escaping helpers.
 *
 * `server.properties` is a Java properties file: backslashes, newlines and (optionally)
 * non-ASCII characters must be escaped so the server reads the intended value on every host.
 *
 * Modern Java Edition reads the file as UTF-8 first (ISO-8859-1 only as a fallback), so escaping
 * non-ASCII is optional. It is kept as an option for older servers and for files that must stay
 * pure ASCII; astral characters (emoji) are emitted as a proper surrogate pair, because a Java
 * `\uXXXX` escape is one UTF-16 unit and `\u1f600` would be read as `\u1f60` + `0`.
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
      out += unicodeEscape(char, code)
    } else {
      out += char
    }
  }
  // Java properties skips leading whitespace after the separator unless it is escaped.
  return out.replace(/^ +/, (spaces) => spaces.replace(/ /g, '\\ '))
}

/**
 * @param char one code point (may be a surrogate pair)
 * @param code its code point
 * @return `\uXXXX`, or two escapes when the code point is outside the BMP
 */
function unicodeEscape(char: string, code: number): string {
  if (code > 0xffff) {
    let out = ''
    for (let index = 0; index < char.length; index += 1) {
      out += `\\u${char.charCodeAt(index).toString(16).padStart(4, '0')}`
    }
    return out
  }
  return `\\u${code.toString(16).padStart(4, '0')}`
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
