/**
 * Slash-command catalogue and parser.
 *
 * <p>Commands are application shortcuts, not prompts: the page resolves them against dedicated
 * endpoints and never sends them to the model, so they cost zero tokens and cannot be
 * hallucinated. Only whitelisted single-word commands are intercepted — anything else starting
 * with {@code /} (a pasted path, for example) stays ordinary text — and {@code //} escapes a
 * literal leading slash.</p>
 */

export type SlashName = 'quota' | 'status' | 'profile' | 'clear'

export interface SlashCommand {
  name: SlashName
  /** Text shown in the menu, including the leading slash. */
  label: string
  description: string
}

export const SLASH_COMMANDS: readonly SlashCommand[] = [
  { name: 'quota', label: '/quota', description: '查看今日 token 额度' },
  { name: 'status', label: '/status', description: '系统自检：模型、索引、工具' },
  { name: 'profile', label: '/profile', description: '查看已保存的用户画像' },
  { name: 'clear', label: '/clear', description: '重置上下文（保留聊天记录）' },
]

export type ParsedSlash =
  | { kind: 'command'; command: SlashCommand }
  | { kind: 'unknown'; token: string }
  | { kind: 'escape'; text: string }

/**
 * Classifies a draft before it is sent.
 *
 * @param text raw composer value
 * @returns the parsed command, an unknown-command marker, an escape, or {@code null} when the
 *          text should be sent to the model unchanged
 */
export function parseSlash(text: string): ParsedSlash | null {
  const value = text.trim()
  if (!value.startsWith('/')) return null
  if (value.startsWith('//')) return { kind: 'escape', text: value.slice(1) }
  // Only "/word" is a candidate: "/opt/mc/server.jar" is a path, not a command.
  const single = /^\/([A-Za-z]+)$/.exec(value)
  if (!single) return null
  const name = single[1].toLowerCase()
  const command = SLASH_COMMANDS.find((item) => item.name === name)
  return command ? { kind: 'command', command } : { kind: 'unknown', token: single[1] }
}

/** Whether the composer should open the command menu for the current draft. */
export function opensSlashMenu(text: string): boolean {
  return text.startsWith('/') && !text.startsWith('//') && !/\s/.test(text)
}

/**
 * Commands matching the fraction typed so far.
 *
 * @param text composer value beginning with {@code /}
 */
export function matchSlashCommands(text: string): SlashCommand[] {
  const query = text.trim().slice(1).toLowerCase()
  return SLASH_COMMANDS.filter((item) => item.name.startsWith(query))
}
