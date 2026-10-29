/**
 * `server.properties` field definitions, parsing, validation and serialization.
 *
 * The editor keeps a complete default map, so importing a partial file still produces a valid
 * server.properties. Unknown keys from an imported file are preserved verbatim.
 */
import { escapePropertyValue, unescapePropertyValue } from './escape'

export const propertyGroups = ['基础', '玩家与规则', '性能', '网络与查询', 'RCON', '资源包'] as const
export type PropertyGroup = (typeof propertyGroups)[number]

export type PropertyType = 'string' | 'int' | 'boolean' | 'enum'

export interface PropertyOption {
  value: string
  label: string
}

export interface PropertyField {
  key: string
  label: string
  type: PropertyType
  defaultValue: string
  group: PropertyGroup
  hint?: string
  min?: number
  max?: number
  options?: ReadonlyArray<PropertyOption>
  multiline?: boolean
  secret?: boolean
  placeholder?: string
}

const gamemodeOptions: ReadonlyArray<PropertyOption> = [
  { value: 'survival', label: '生存' },
  { value: 'creative', label: '创造' },
  { value: 'adventure', label: '冒险' },
  { value: 'spectator', label: '旁观' },
]

const difficultyOptions: ReadonlyArray<PropertyOption> = [
  { value: 'peaceful', label: '和平' },
  { value: 'easy', label: '简单' },
  { value: 'normal', label: '普通' },
  { value: 'hard', label: '困难' },
]

const levelTypeOptions: ReadonlyArray<PropertyOption> = [
  { value: 'minecraft:normal', label: '默认' },
  { value: 'minecraft:flat', label: '超平坦' },
  { value: 'minecraft:large_biomes', label: '大型生物群系' },
  { value: 'minecraft:amplified', label: '放大化' },
  { value: 'minecraft:single_biome_surface', label: '单一生物群系' },
  { value: 'minecraft:debug_all_block_states', label: '调试世界' },
]

export const propertyFields: ReadonlyArray<PropertyField> = [
  // Basics
  {
    key: 'motd',
    label: 'MOTD',
    type: 'string',
    defaultValue: 'A Minecraft Server',
    group: '基础',
    multiline: true,
    hint: '服务器列表显示的两行文字；支持 § 颜色代码。',
  },
  { key: 'server-port', label: '服务器端口', type: 'int', defaultValue: '25565', group: '基础', min: 1, max: 65535 },
  { key: 'server-ip', label: '监听地址', type: 'string', defaultValue: '', group: '基础', placeholder: '留空监听所有网卡' },
  { key: 'max-players', label: '最大玩家数', type: 'int', defaultValue: '20', group: '基础', min: 1, max: 10000 },
  { key: 'level-name', label: '世界文件夹', type: 'string', defaultValue: 'world', group: '基础', hint: '不要包含 / 或 \\。' },
  { key: 'level-seed', label: '世界种子', type: 'string', defaultValue: '', group: '基础', placeholder: '留空则随机' },
  {
    key: 'level-type',
    label: '世界类型',
    type: 'string',
    defaultValue: 'minecraft:normal',
    group: '基础',
    options: levelTypeOptions,
    hint: '自定义数据包可填写命名空间 ID。',
  },
  { key: 'generator-settings', label: '生成器设置', type: 'string', defaultValue: '{}', group: '基础', placeholder: '{"layers":...}', hint: '仅超平坦 / 自定义世界使用；普通世界保留 {}。' },
  { key: 'gamemode', label: '默认游戏模式', type: 'enum', defaultValue: 'survival', group: '基础', options: gamemodeOptions },
  { key: 'difficulty', label: '难度', type: 'enum', defaultValue: 'easy', group: '基础', options: difficultyOptions },
  { key: 'hardcore', label: '极限模式', type: 'boolean', defaultValue: 'false', group: '基础' },
  { key: 'pvp', label: '允许 PVP', type: 'boolean', defaultValue: 'true', group: '基础' },
  { key: 'force-gamemode', label: '强制游戏模式', type: 'boolean', defaultValue: 'false', group: '基础' },
  { key: 'initial-enabled-packs', label: '初始启用数据包', type: 'string', defaultValue: 'vanilla', group: '基础', hint: '1.19+；多个用逗号分隔。' },
  { key: 'initial-disabled-packs', label: '初始禁用数据包', type: 'string', defaultValue: '', group: '基础' },

  // Players and rules
  { key: 'online-mode', label: '正版验证', type: 'boolean', defaultValue: 'true', group: '玩家与规则' },
  { key: 'enforce-secure-profile', label: '强制安全聊天签名', type: 'boolean', defaultValue: 'true', group: '玩家与规则', hint: '1.19+；关闭后部分客户端可绕过聊天签名。' },
  { key: 'white-list', label: '启用白名单', type: 'boolean', defaultValue: 'false', group: '玩家与规则' },
  { key: 'enforce-whitelist', label: '强制白名单', type: 'boolean', defaultValue: 'false', group: '玩家与规则' },
  { key: 'allow-flight', label: '允许飞行', type: 'boolean', defaultValue: 'false', group: '玩家与规则' },
  { key: 'allow-nether', label: '允许下界', type: 'boolean', defaultValue: 'true', group: '玩家与规则' },
  { key: 'spawn-protection', label: '出生点保护半径', type: 'int', defaultValue: '16', group: '玩家与规则', min: 0, max: 10000 },
  { key: 'enable-command-block', label: '启用命令方块', type: 'boolean', defaultValue: 'false', group: '玩家与规则' },
  { key: 'op-permission-level', label: 'OP 权限等级', type: 'int', defaultValue: '4', group: '玩家与规则', min: 0, max: 4 },
  { key: 'function-permission-level', label: '函数权限等级', type: 'int', defaultValue: '2', group: '玩家与规则', min: 1, max: 4 },
  { key: 'player-idle-timeout', label: '挂机踢出（分钟）', type: 'int', defaultValue: '0', group: '玩家与规则', min: 0, max: 1440, hint: '0 表示不踢出。' },
  { key: 'hide-online-players', label: '隐藏在线玩家', type: 'boolean', defaultValue: 'false', group: '玩家与规则' },

  // Performance
  { key: 'view-distance', label: '视距', type: 'int', defaultValue: '10', group: '性能', min: 3, max: 32 },
  { key: 'simulation-distance', label: '模拟距离', type: 'int', defaultValue: '10', group: '性能', min: 3, max: 32 },
  { key: 'max-tick-time', label: '单 tick 上限（ms）', type: 'int', defaultValue: '60000', group: '性能', min: -1, max: 600000, hint: '-1 表示禁用看门狗。' },
  { key: 'network-compression-threshold', label: '网络压缩阈值', type: 'int', defaultValue: '256', group: '性能', min: -1, max: 65536, hint: '-1 表示关闭压缩。' },
  { key: 'sync-chunk-writes', label: '同步区块写入', type: 'boolean', defaultValue: 'true', group: '性能' },
  { key: 'max-world-size', label: '最大世界半径', type: 'int', defaultValue: '29999984', group: '性能', min: 1, max: 29999984 },
  { key: 'pause-when-empty-seconds', label: '空服暂停（秒）', type: 'int', defaultValue: '60', group: '性能', min: 0, max: 3600, hint: '1.21.2+；0 表示不暂停。' },

  // Network and query
  { key: 'enable-status', label: '响应状态查询', type: 'boolean', defaultValue: 'true', group: '网络与查询' },
  { key: 'enable-query', label: '启用 Query', type: 'boolean', defaultValue: 'false', group: '网络与查询' },
  { key: 'query.port', label: 'Query 端口', type: 'int', defaultValue: '25565', group: '网络与查询', min: 1, max: 65535 },
  { key: 'prevent-proxy-connections', label: '阻止代理连接', type: 'boolean', defaultValue: 'false', group: '网络与查询' },
  { key: 'use-native-transport', label: '使用原生传输', type: 'boolean', defaultValue: 'true', group: '网络与查询', hint: 'Linux 上可提升网络性能。' },
  { key: 'accepts-transfers', label: '接受跨服转移', type: 'boolean', defaultValue: 'false', group: '网络与查询', hint: '1.20.5+。' },

  // RCON
  { key: 'enable-rcon', label: '启用 RCON', type: 'boolean', defaultValue: 'false', group: 'RCON' },
  { key: 'rcon.port', label: 'RCON 端口', type: 'int', defaultValue: '25575', group: 'RCON', min: 1, max: 65535 },
  { key: 'rcon.password', label: 'RCON 密码', type: 'string', defaultValue: '', group: 'RCON', secret: true, hint: '只在本地处理；不要分享导出的文件。' },

  // Resource pack
  { key: 'resource-pack', label: '资源包地址', type: 'string', defaultValue: '', group: '资源包', placeholder: 'https://example.com/pack.zip' },
  { key: 'resource-pack-sha1', label: '资源包 SHA-1', type: 'string', defaultValue: '', group: '资源包', placeholder: '40 位十六进制' },
  { key: 'require-resource-pack', label: '强制资源包', type: 'boolean', defaultValue: 'false', group: '资源包' },
  { key: 'resource-pack-prompt', label: '资源包提示', type: 'string', defaultValue: '', group: '资源包' },
]

export const propertyPresets: ReadonlyArray<{ key: PropertyPreset; label: string; desc: string }> = [
  { key: 'survival', label: '生存服', desc: '生存 / 普通难度 / 保留出生点保护' },
  { key: 'creative', label: '创造服', desc: '创造 / 和平 / 允许飞行' },
  { key: 'minigame', label: '小游戏服', desc: '冒险 / 强制模式 / 允许飞行与命令方块' },
]

export type PropertyPreset = 'survival' | 'creative' | 'minigame'

export interface ParsedProperties {
  values: Record<string, string>
  unknown: Array<{ key: string; value: string }>
  warnings: string[]
}

/** A complete map with every known key set to its default. */
export function createDefaultProperties(): Record<string, string> {
  return Object.fromEntries(propertyFields.map((field) => [field.key, field.defaultValue]))
}

export function applyPropertyPreset(preset: PropertyPreset): Record<string, string> {
  const values = createDefaultProperties()
  if (preset === 'survival') {
    Object.assign(values, {
      motd: 'A Minecraft Survival Server',
      gamemode: 'survival',
      difficulty: 'normal',
      pvp: 'true',
      'spawn-protection': '16',
    })
  } else if (preset === 'creative') {
    Object.assign(values, {
      motd: 'A Minecraft Creative Server',
      gamemode: 'creative',
      difficulty: 'peaceful',
      pvp: 'false',
      'allow-flight': 'true',
      'spawn-protection': '0',
      'enable-command-block': 'true',
    })
  } else {
    Object.assign(values, {
      motd: 'A Minecraft Minigame Server',
      gamemode: 'adventure',
      difficulty: 'peaceful',
      pvp: 'true',
      'allow-flight': 'true',
      'spawn-protection': '0',
      'enable-command-block': 'true',
      'force-gamemode': 'true',
    })
  }
  return values
}

function hasContinuation(line: string): boolean {
  let count = 0
  for (let index = line.length - 1; index >= 0 && line[index] === '\\'; index -= 1) count += 1
  return count % 2 === 1
}

function findSeparator(line: string): number {
  let escaped = false
  for (let index = 0; index < line.length; index += 1) {
    const char = line[index]
    if (escaped) {
      escaped = false
      continue
    }
    if (char === '\\') {
      escaped = true
      continue
    }
    if (char === '=' || char === ':') return index
  }
  return -1
}

/** Parse a server.properties file into known values + preserved unknown keys. */
export function parseProperties(text: string): ParsedProperties {
  const values = createDefaultProperties()
  const unknown = new Map<string, string>()
  const warnings: string[] = []
  const seen = new Set<string>()

  const rawLines = text.replace(/^\uFEFF/, '').split(/\r?\n/)
  const lines: string[] = []
  let pending = ''
  for (const raw of rawLines) {
    // Java properties ignores leading whitespace on continuation lines.
    const combined = pending ? pending + raw.replace(/^\s+/, '') : raw
    if (hasContinuation(combined)) {
      pending = combined.slice(0, -1)
      continue
    }
    lines.push(combined)
    pending = ''
  }
  if (pending) lines.push(pending)

  const knownKeys = new Set(propertyFields.map((field) => field.key))
  lines.forEach((line, lineIndex) => {
    const trimmed = line.replace(/^\s+/, '')
    if (!trimmed || trimmed.startsWith('#') || trimmed.startsWith('!')) return
    const separator = findSeparator(trimmed)
    const key = unescapePropertyValue((separator >= 0 ? trimmed.slice(0, separator) : trimmed).trim())
    const value = unescapePropertyValue(separator >= 0 ? trimmed.slice(separator + 1).trimStart() : '')
    if (!key) {
      warnings.push(`第 ${lineIndex + 1} 行缺少键名，已跳过。`)
      return
    }
    if (seen.has(key)) warnings.push(`键 ${key} 重复，后者覆盖前者。`)
    seen.add(key)
    if (knownKeys.has(key)) values[key] = value
    else unknown.set(key, value)
  })

  return {
    values,
    unknown: Array.from(unknown, ([key, value]) => ({ key, value })),
    warnings,
  }
}

/** Serialize a complete server.properties file. */
export function serializeProperties(
  values: Record<string, string>,
  unknown: ReadonlyArray<{ key: string; value: string }> = [],
  escapeUnicode = false,
): string {
  const lines: string[] = [
    '#Minecraft server properties',
    '#Generated by NitroWater - https://nitrowater.cn',
    '#Java Edition only; Bedrock Edition uses a different configuration format.',
    '',
  ]

  for (const group of propertyGroups) {
    lines.push(`# --- ${group} ---`)
    for (const field of propertyFields.filter((item) => item.group === group)) {
      const value = values[field.key] ?? field.defaultValue
      lines.push(`${field.key}=${escapePropertyValue(value, escapeUnicode)}`)
    }
    lines.push('')
  }

  if (unknown.length > 0) {
    lines.push('# --- Preserved unknown keys ---')
    for (const entry of unknown) {
      lines.push(`${entry.key}=${escapePropertyValue(entry.value, escapeUnicode)}`)
    }
    lines.push('')
  }

  return `${lines.join('\n').trimEnd()}\n`
}

/** Validate known values; returns a map of `key -> error message`. */
export function validateProperties(values: Record<string, string>): Record<string, string> {
  const errors: Record<string, string> = {}

  for (const field of propertyFields) {
    const value = String(values[field.key] ?? '')
    if (field.type === 'int') {
      const number = Number(value)
      if (!value.trim() || !Number.isInteger(number)) {
        errors[field.key] = '请输入整数'
        continue
      }
      if (field.min !== undefined && number < field.min) errors[field.key] = `不能小于 ${field.min}`
      else if (field.max !== undefined && number > field.max) errors[field.key] = `不能大于 ${field.max}`
    } else if (field.type === 'boolean') {
      if (value !== 'true' && value !== 'false') errors[field.key] = '只能为 true 或 false'
    } else if (field.type === 'enum') {
      if (!field.options?.some((option) => option.value === value)) errors[field.key] = '请选择有效值'
    }
  }

  const levelName = values['level-name'] ?? ''
  if (!levelName.trim()) errors['level-name'] = '不能为空'
  else if (levelName.includes('/') || levelName.includes('\\')) errors['level-name'] = '不能包含 / 或 \\'

  const generatorSettings = values['generator-settings'] ?? ''
  if (generatorSettings.trim()) {
    try {
      JSON.parse(generatorSettings)
    } catch {
      errors['generator-settings'] = '应为合法 JSON'
    }
  }

  const resourcePack = values['resource-pack'] ?? ''
  if (resourcePack && !/^https?:\/\//i.test(resourcePack)) {
    errors['resource-pack'] = '资源包地址应以 http:// 或 https:// 开头'
  }
  const sha1 = values['resource-pack-sha1'] ?? ''
  if (sha1 && !/^[0-9a-f]{40}$/i.test(sha1)) errors['resource-pack-sha1'] = 'SHA-1 应为 40 位十六进制'

  if (values['enable-rcon'] === 'true') {
    if (!values['rcon.password']) errors['rcon.password'] = '启用 RCON 时必须设置密码'
    if (values['rcon.port'] === values['server-port']) errors['rcon.port'] = 'RCON 端口不能与服务器端口相同'
  }

  return errors
}
