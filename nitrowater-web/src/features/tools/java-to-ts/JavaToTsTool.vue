<script setup lang="ts">
/**
 * Java DTO/VO -> TypeScript converter.
 * Ported from wtools (MIT) `src/components/JavaToTs.vue`; UI rewritten with Tailwind.
 */
import { CopyDocument, Delete } from '@element-plus/icons-vue'
import { ref } from 'vue'
import { toast } from '../../../shared/ui/toast'
import ToolShell from '../ToolShell.vue'

const javaCode = ref('')
const tsCode = ref('')
const generateDefaults = ref(true)
const generateRules = ref(true)

const escapeJSStr = (s: string): string =>
  s.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\n/g, '\\n').replace(/\r/g, '\\r')

const unescapeJavaRegex = (s: string): string => s.replace(/\\(.)/g, '$1')

const parseConstraintAttrs = (attrStr: string): Record<string, string> => {
  const attrs: Record<string, string> = {}
  if (!attrStr) return attrs
  const attrRegex = /(\w+)\s*=\s*(?:(\d+(?:\.\d+)?)|"((?:[^"\\]|\\.)*)")/g
  let match
  while ((match = attrRegex.exec(attrStr)) !== null) {
    attrs[match[1]] = match[2] ?? match[3] ?? ''
  }
  if (Object.keys(attrs).length === 0) {
    const simpleVal = attrStr.match(/^\s*(\d+(?:\.\d+)?)\s*$/)
    if (simpleVal) attrs['value'] = simpleVal[1]
  }
  return attrs
}

const buildRuleContent = (annotation: string, attrs: Record<string, string>): string | null => {
  const msg = (key = 'message') => (attrs[key] ? `message: '${escapeJSStr(attrs[key])}'` : null)
  switch (annotation) {
    case 'NotNull':
    case 'NotBlank':
    case 'NotEmpty':
      return `{ required: true, ${msg() ?? "message: '该字段不能为空'"}, trigger: 'blur' }`
    case 'Size':
    case 'Length': {
      const parts: string[] = []
      if (attrs.min) parts.push(`min: ${attrs.min}`)
      if (attrs.max) parts.push(`max: ${attrs.max}`)
      const m = msg()
      if (m) parts.push(m)
      if (parts.length === 0) return null
      parts.push(`trigger: 'blur'`)
      return `{ ${parts.join(', ')} }`
    }
    case 'Min':
    case 'DecimalMin': {
      const val = attrs.value
      if (!val) return null
      return `{ type: 'number', min: ${val}, ${msg() ?? "message: '不能小于${val}'"}, trigger: 'blur' }`
    }
    case 'Max':
    case 'DecimalMax': {
      const val = attrs.value
      if (!val) return null
      return `{ type: 'number', max: ${val}, ${msg() ?? "message: '不能大于${val}'"}, trigger: 'blur' }`
    }
    case 'Email':
      return `{ type: 'email', ${msg() ?? "message: '邮箱格式不正确'"}, trigger: 'blur' }`
    case 'Pattern': {
      if (!attrs.regexp) return null
      const pattern = unescapeJavaRegex(attrs.regexp).replace(/\//g, '\\/')
      const parts: string[] = [`pattern: /${pattern}/`]
      const m = msg()
      if (m) parts.push(m)
      parts.push(`trigger: 'blur'`)
      return `{ ${parts.join(', ')} }`
    }
    case 'Range': {
      const parts: string[] = [`type: 'number'`]
      if (attrs.min) parts.push(`min: ${attrs.min}`)
      if (attrs.max) parts.push(`max: ${attrs.max}`)
      const m = msg()
      if (m) parts.push(m)
      if (parts.length === 1) return null
      parts.push(`trigger: 'blur'`)
      return `{ ${parts.join(', ')} }`
    }
    case 'Positive':
      return `{ type: 'number', min: 1, ${msg() ?? "message: '必须大于0'"}, trigger: 'blur' }`
    case 'PositiveOrZero':
      return `{ type: 'number', min: 0, ${msg() ?? "message: '必须大于等于0'"}, trigger: 'blur' }`
    case 'Negative':
      return `{ type: 'number', max: -1, ${msg() ?? "message: '必须小于0'"}, trigger: 'blur' }`
    case 'NegativeOrZero':
      return `{ type: 'number', max: 0, ${msg() ?? "message: '必须小于等于0'"}, trigger: 'blur' }`
    default:
      return null
  }
}

const getDefaultValue = (tsType: string): string => {
  if (tsType === 'string') return 'undefined'
  if (tsType === 'number') return '0'
  if (tsType === 'boolean') return 'false'
  if (tsType.endsWith('[]') || tsType.startsWith('Array<')) return '[]'
  if (tsType.startsWith('Record<')) return '{}'
  return 'undefined'
}

const typeMapping: Record<string, string> = {
  String: 'string', char: 'string', Character: 'string',
  Integer: 'number', int: 'number', Long: 'number', long: 'number',
  Short: 'number', short: 'number', Byte: 'number', byte: 'number',
  Double: 'number', double: 'number', Float: 'number', float: 'number',
  BigDecimal: 'number', BigInteger: 'number',
  Boolean: 'boolean', boolean: 'boolean',
  Date: 'string', Instant: 'string', LocalDate: 'string', LocalTime: 'string', LocalDateTime: 'string',
  Object: 'any',
}

const parseJavaType = (javaType: string): { tsType: string, isUnknown: boolean } => {
  javaType = javaType.trim()
  const collectionMatch = javaType.match(/^(?:List|Set|Collection)<(.+)>$/)
  if (collectionMatch) {
    const innerType = parseJavaType(collectionMatch[1])
    return {
      tsType: innerType.tsType.includes(' ') || innerType.tsType.includes('|')
        ? `Array<${innerType.tsType}>`
        : `${innerType.tsType}[]`,
      isUnknown: innerType.isUnknown,
    }
  }
  const mapMatch = javaType.match(/^Map<(.+?),\s*(.+)>$/)
  if (mapMatch) {
    const keyType = parseJavaType(mapMatch[1])
    const valueType = parseJavaType(mapMatch[2])
    const validKey = keyType.tsType === 'number' || keyType.tsType === 'string' ? keyType.tsType : 'string'
    return { tsType: `Record<${validKey}, ${valueType.tsType}>`, isUnknown: valueType.isUnknown }
  }
  if (javaType.endsWith('[]')) {
    const parsed = parseJavaType(javaType.slice(0, -2))
    return { tsType: `${parsed.tsType}[]`, isUnknown: parsed.isUnknown }
  }
  if (typeMapping[javaType]) {
    return { tsType: typeMapping[javaType], isUnknown: false }
  }
  return { tsType: javaType, isUnknown: true }
}

function convertToTs(): void {
  if (!javaCode.value.trim()) {
    toast.warning('请输入 Java 代码')
    return
  }
  const code = javaCode.value
  const classMatch = code.match(/class\s+(\w+)/)
  const className = classMatch ? classMatch[1] : 'UnknownType'

  const fields: { type: string, name: string, isUnknown: boolean, comment?: string, rules: string[] }[] = []
  let pendingComment: string | null = null
  const pendingRules: string[] = []

  for (let line of code.split('\n')) {
    line = line.trim()
    if (line.startsWith('//')) continue
    const apiModelMatch = line.match(/@ApiModelProperty\(\s*(?:(?:value\s*=\s*)?"([^"]*)")\s*(?:[,)]|$)/)
    if (apiModelMatch) {
      pendingComment = apiModelMatch[1] || null
      continue
    }
    const constraintMatch = line.match(/@(NotNull|NotBlank|NotEmpty|Size|Length|Min|Max|DecimalMin|DecimalMax|Email|Pattern|Range|Positive(?:OrZero)?|Negative(?:OrZero)?)\s*(?:\(([^)]*)\))?/)
    if (constraintMatch) {
      const ruleStr = buildRuleContent(constraintMatch[1], parseConstraintAttrs(constraintMatch[2] ?? ''))
      if (ruleStr) pendingRules.push(ruleStr)
      continue
    }
    if (line.startsWith('@')) continue
    const fieldMatch = line.match(/(?:(?:private|protected|public)\s+)?([\w<>,?[\] ]+)\s+(\w+)\s*;/)
    if (fieldMatch) {
      const parsedType = parseJavaType(fieldMatch[1].trim())
      fields.push({
        name: fieldMatch[2].trim(),
        type: parsedType.tsType,
        isUnknown: parsedType.isUnknown,
        comment: pendingComment ?? undefined,
        rules: pendingRules.length > 0 ? [...pendingRules] : [],
      })
      pendingComment = null
      pendingRules.length = 0
    }
  }

  let tsOutput = `export interface ${className} {\n`
  let hasUnknowns = false
  for (const field of fields) {
    const commentStr = field.comment ? ` // ${field.comment}` : ''
    if (field.isUnknown) {
      hasUnknowns = true
      tsOutput += `  ${field.name}: ${field.type}; // FIXME: 未知类型/自定义DTO，需提供 ${field.type} 的定义${commentStr}\n`
    } else {
      tsOutput += `  ${field.name}: ${field.type};${commentStr}\n`
    }
  }
  tsOutput += `}\n`

  if (generateDefaults.value) {
    tsOutput += `\nexport const default${className}: ${className} = {\n`
    for (const field of fields) tsOutput += `  ${field.name}: ${getDefaultValue(field.type)},\n`
    tsOutput += `};\n`
  }

  const fieldsWithRules = fields.filter((f) => f.rules.length > 0)
  if (generateRules.value && fieldsWithRules.length > 0) {
    tsOutput += `\n// Element Plus form rules\n`
    tsOutput += `import type { FormRules } from 'element-plus'\n\n`
    tsOutput += `export const rules: FormRules = {\n`
    for (const field of fieldsWithRules) {
      tsOutput += `  ${field.name}: [\n`
      for (const rule of field.rules) tsOutput += `    ${rule},\n`
      tsOutput += `  ],\n`
    }
    tsOutput += `}\n`
  }

  tsCode.value = tsOutput
  if (hasUnknowns) toast.warning('检测到未知类型，请检查 FIXME 注释')
  else toast.success('转换成功')
}

async function copy(text: string, okMsg: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text)
    toast.success(okMsg)
  } catch {
    toast.error('复制失败，请手动选择复制')
  }
}
</script>

<template>
  <ToolShell
    title="Java DTO 转 TypeScript"
    desc="解析 Java DTO / VO，生成 TypeScript 接口；可选默认值与 Element Plus 表单校验规则。"
  >
    <div class="grid gap-4 lg:grid-cols-2">
      <section class="panel flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <h3 class="text-sm font-semibold text-ink">
            Java 输入
          </h3>
          <div class="flex gap-2">
            <el-tooltip
              content="复制 Java 代码"
              placement="top"
            >
              <el-button
                size="small"
                :icon="CopyDocument"
                circle
                :disabled="!javaCode"
                @click="copy(javaCode, '已复制 Java 代码')"
              />
            </el-tooltip>
            <el-tooltip
              content="清空输入"
              placement="top"
            >
              <el-button
                size="small"
                :icon="Delete"
                circle
                :disabled="!javaCode"
                @click="javaCode = ''"
              />
            </el-tooltip>
          </div>
        </div>
        <textarea
          v-model="javaCode"
          class="textarea min-h-[320px]"
          placeholder="粘贴 Java 类代码，例如：&#10;public class RoleResp {&#10;    private Integer id;&#10;    private String name;&#10;}"
        />
        <div class="flex flex-wrap items-center gap-4 text-sm text-dim">
          <label class="inline-flex cursor-pointer items-center gap-2">
            <input
              v-model="generateDefaults"
              type="checkbox"
              class="accent-accent"
            >
            生成默认值
          </label>
          <label class="inline-flex cursor-pointer items-center gap-2">
            <input
              v-model="generateRules"
              type="checkbox"
              class="accent-accent"
            >
            生成校验规则
          </label>
        </div>
        <el-button
          type="primary"
          class="self-start"
          @click="convertToTs"
        >
          转换为 TypeScript
        </el-button>
      </section>

      <section class="panel flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <h3 class="text-sm font-semibold text-ink">
            TypeScript 输出
          </h3>
          <el-button
            size="small"
            :icon="CopyDocument"
            :disabled="!tsCode"
            @click="copy(tsCode, '已复制结果')"
          >
            复制结果
          </el-button>
        </div>
        <textarea
          v-model="tsCode"
          readonly
          class="textarea min-h-[320px]"
          placeholder="生成的 TypeScript 代码将显示在这里"
        />
      </section>
    </div>
  </ToolShell>
</template>
