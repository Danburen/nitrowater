/**
 * Markdown → Word(.docx) 转换（纯浏览器，本地完成，文件不上传）。
 *
 * 分级策略 Tier B：`marked` / `docx` 均为 npm 依赖，函数内 `await import()` 惰性加载；
 * 组件再通过动态 `import()` 本模块实现整体懒加载。
 *
 * v1 支持：标题 h1–h6、段落（粗体/斜体/删除线/行内代码/链接/换行）、有序/无序/嵌套列表、
 * 引用块、代码块、分隔线、表格；图片降级为占位文本。
 */
import type { ExternalHyperlink, Paragraph, Table, TableCell, TextRun } from 'docx'

/** marked 的 token 结构在版本间不完全稳定，这里用宽松结构描述我们消费到的字段。 */
interface MdCell {
  text?: string
  tokens?: MdToken[]
}

interface MdToken {
  type: string
  text?: string
  tokens?: MdToken[]
  items?: MdToken[]
  depth?: number
  ordered?: boolean
  href?: string
  header?: MdCell[]
  rows?: MdCell[][]
}

export interface MarkdownToDocxOptions {
  /** 正文字号（docx 单位：半磅 half-points），默认 22（≈11pt）。 */
  fontSize?: number
  /** 正文字体，默认“微软雅黑”。 */
  font?: string
}

type InlineRun = TextRun | ExternalHyperlink

const ORDERED_REF = 'nw-ordered-list'

/** 将 Markdown 文本转换为 .docx 的 Blob。 */
export async function markdownToDocxBlob(
  markdown: string,
  options: MarkdownToDocxOptions = {},
): Promise<Blob> {
  const [{ marked }, docx] = await Promise.all([import('marked'), import('docx')])

  const {
    Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType,
    Table, TableRow, TableCell, WidthType, BorderStyle, ExternalHyperlink,
    LevelFormat, ShadingType, UnderlineType,
  } = docx

  const font = options.font ?? '微软雅黑'
  const fontSize = options.fontSize ?? 22
  const MONO = 'Consolas'
  const CODE_FILL = 'F5F5F5'

  const HEADINGS = [
    HeadingLevel.HEADING_1, HeadingLevel.HEADING_2, HeadingLevel.HEADING_3,
    HeadingLevel.HEADING_4, HeadingLevel.HEADING_5, HeadingLevel.HEADING_6,
  ]
  const headingLevel = (depth: number): (typeof HEADINGS)[number] =>
    HEADINGS[Math.min(Math.max(depth, 1), 6) - 1] ?? HeadingLevel.HEADING_6

  interface RunStyle {
    bold?: boolean
    italics?: boolean
    strike?: boolean
    color?: string
    underline?: boolean
  }

  /** 行内 token → 文本 run 序列（粗体/斜体/删除线/行内码/链接/换行）。 */
  function runs(tokens: MdToken[] | undefined, style: RunStyle = {}): InlineRun[] {
    const out: InlineRun[] = []
    const underline = style.underline ? { type: UnderlineType.SINGLE } : undefined
    for (const token of tokens ?? []) {
      switch (token.type) {
        case 'text':
          if (token.tokens?.length) out.push(...runs(token.tokens, style))
          else out.push(new TextRun({ text: token.text ?? '', ...style, underline, font }))
          break
        case 'escape':
          out.push(new TextRun({ text: token.text ?? '', ...style, underline, font }))
          break
        case 'strong':
          out.push(...runs(token.tokens, { ...style, bold: true }))
          break
        case 'em':
          out.push(...runs(token.tokens, { ...style, italics: true }))
          break
        case 'del':
          out.push(...runs(token.tokens, { ...style, strike: true }))
          break
        case 'codespan':
          out.push(new TextRun({
            text: token.text ?? '',
            font: MONO,
            shading: { type: ShadingType.CLEAR, fill: 'F0F0F0' },
          }))
          break
        case 'br':
          out.push(new TextRun({ text: '', break: 1 }))
          break
        case 'link':
          out.push(new ExternalHyperlink({
            link: token.href ?? '',
            children: runs(token.tokens, { ...style, color: '0563C1', underline: true })
              .filter((run): run is TextRun => run instanceof TextRun),
          }))
          break
        case 'image':
          out.push(new TextRun({
            text: `[图片: ${token.text || token.href || ''}]`,
            italics: true,
            color: '888888',
            font,
          }))
          break
        default:
          break
      }
    }
    return out
  }

  function codeBlock(code: string): Paragraph {
    const lines = code.replace(/\n$/, '').split('\n')
    const children: TextRun[] = []
    lines.forEach((line, index) => {
      if (index > 0) children.push(new TextRun({ text: '', break: 1 }))
      children.push(new TextRun({ text: line || ' ', font: MONO, size: 20 }))
    })
    return new Paragraph({
      shading: { type: ShadingType.CLEAR, fill: CODE_FILL },
      spacing: { before: 80, after: 160, line: 240 },
      children,
    })
  }

  function blockquote(tokens: MdToken[] | undefined): Paragraph[] {
    const out: Paragraph[] = []
    for (const token of tokens ?? []) {
      const inner = token.type === 'paragraph' || token.type === 'text'
        ? runs(token.tokens ?? [token], { italics: true })
        : runs([token], { italics: true })
      out.push(new Paragraph({
        indent: { left: 360 },
        border: { left: { color: 'CCCCCC', style: BorderStyle.SINGLE, size: 12, space: 8 } },
        spacing: { after: 120 },
        children: inner,
      }))
    }
    return out
  }

  function renderList(list: MdToken, level: number): Paragraph[] {
    const out: Paragraph[] = []
    const items = list.items ?? list.tokens ?? []
    for (const item of items) {
      const itemTokens = item.tokens ?? []
      const nested = itemTokens.filter((t) => t.type === 'list')
      const content = itemTokens.filter((t) => t.type !== 'list')
      const props = list.ordered
        ? { numbering: { reference: ORDERED_REF, level } }
        : { bullet: { level } }
      out.push(new Paragraph({ ...props, spacing: { after: 40 }, children: runs(content) }))
      for (const nestedList of nested) out.push(...renderList(nestedList, level + 1))
    }
    return out
  }

  function table(header: MdCell[], rows: MdCell[][]): Table {
    const cell = (value: MdCell, isHeader: boolean): TableCell => new TableCell({
      children: [new Paragraph({ children: runs(value.tokens, { bold: isHeader }) })],
      shading: isHeader ? { type: ShadingType.CLEAR, fill: 'F0F0F0' } : undefined,
    })
    return new Table({
      width: { size: 100, type: WidthType.PERCENTAGE },
      rows: [
        new TableRow({ tableHeader: true, children: header.map((c) => cell(c, true)) }),
        ...rows.map((row) => new TableRow({ children: row.map((c) => cell(c, false)) })),
      ],
    })
  }

  function blocks(tokens: MdToken[] | undefined): (Paragraph | Table)[] {
    const out: (Paragraph | Table)[] = []
    for (const token of tokens ?? []) {
      switch (token.type) {
        case 'heading':
          out.push(new Paragraph({
            heading: headingLevel(token.depth ?? 1),
            spacing: { before: 200, after: 100 },
            children: runs(token.tokens),
          }))
          break
        case 'paragraph':
        case 'text':
          out.push(new Paragraph({ spacing: { after: 120 }, children: runs(token.tokens ?? [token]) }))
          break
        case 'code':
          out.push(codeBlock(token.text ?? ''))
          break
        case 'blockquote':
          out.push(...blockquote(token.tokens))
          break
        case 'list':
          out.push(...renderList(token, 0))
          break
        case 'table':
          if (token.header && token.rows) out.push(table(token.header, token.rows))
          break
        case 'hr':
          out.push(new Paragraph({
            border: { bottom: { color: 'BBBBBB', style: BorderStyle.SINGLE, size: 6, space: 1 } },
            spacing: { after: 160 },
          }))
          break
        default:
          break
      }
    }
    return out
  }

  const tokens = marked.lexer(markdown) as unknown as MdToken[]

  const doc = new Document({
    styles: {
      default: {
        document: { run: { font, size: fontSize } },
      },
    },
    numbering: {
      config: [{
        reference: ORDERED_REF,
        levels: [
          { level: 0, format: LevelFormat.DECIMAL, text: '%1.', alignment: AlignmentType.START },
          { level: 1, format: LevelFormat.LOWER_LETTER, text: '%2.', alignment: AlignmentType.START },
          { level: 2, format: LevelFormat.LOWER_ROMAN, text: '%3.', alignment: AlignmentType.START },
        ],
      }],
    },
    sections: [{
      properties: {},
      children: blocks(tokens),
    }],
  })

  return Packer.toBlob(doc)
}
