/** File-type recognition and category icons for tree list visualization (ported from wtools). */

import IMAGE_SVG from '../../../assets/icons/file-image.svg?raw'
import DOC_SVG from '../../../assets/icons/file-doc.svg?raw'
import CODE_SVG from '../../../assets/icons/file-code.svg?raw'
import DATA_SVG from '../../../assets/icons/file-data.svg?raw'
import ARCHIVE_SVG from '../../../assets/icons/file-archive.svg?raw'
import MEDIA_SVG from '../../../assets/icons/file-media.svg?raw'
import CONFIG_SVG from '../../../assets/icons/file-config.svg?raw'
import WEB_SVG from '../../../assets/icons/file-web.svg?raw'
import TEXT_SVG from '../../../assets/icons/file-text.svg?raw'

export type FileCategory =
  | 'image'
  | 'doc'
  | 'code'
  | 'data'
  | 'archive'
  | 'media'
  | 'config'
  | 'web'
  | 'text'

// ─── Extension → category mapping ────────────────────────────────
const EXT_MAP: Record<string, FileCategory> = {
  png: 'image', jpg: 'image', jpeg: 'image', gif: 'image',
  svg: 'image', webp: 'image', ico: 'image', bmp: 'image',
  pdf: 'doc', doc: 'doc', docx: 'doc', xls: 'doc', xlsx: 'doc',
  ppt: 'doc', pptx: 'doc',
  py: 'code', js: 'code', ts: 'code', jsx: 'code', tsx: 'code',
  java: 'code', kt: 'code', scala: 'code',
  cpp: 'code', c: 'code', h: 'code', hpp: 'code',
  go: 'code', rs: 'code', rb: 'code', php: 'code',
  swift: 'code', vue: 'code', cs: 'code',
  sh: 'code', bash: 'code', zsh: 'code',
  zip: 'archive', rar: 'archive', '7z': 'archive',
  tar: 'archive', gz: 'archive', bz2: 'archive', tgz: 'archive',
  csv: 'data', db: 'data', sqlite: 'data', sql: 'data',
  json: 'data', xml: 'data',
  yaml: 'config', yml: 'config', toml: 'config',
  ini: 'config', cfg: 'config', conf: 'config', env: 'config',
  mp4: 'media', avi: 'media', mkv: 'media', mov: 'media', webm: 'media',
  mp3: 'media', wav: 'media', flac: 'media', ogg: 'media',
  html: 'web', htm: 'web', css: 'web', scss: 'web', less: 'web',
  txt: 'text', md: 'text', rst: 'text', log: 'text',
}

export function getFileCategory(name: string): FileCategory {
  const dot = name.lastIndexOf('.')
  if (dot <= 0 || dot >= name.length - 1) return 'doc'
  return EXT_MAP[name.substring(dot + 1).toLowerCase()] ?? 'doc'
}

// ─── Category → managed SVG asset (imported with ?raw so it ships in the bundle) ─────────────

const SVG_MAP: Record<FileCategory, string> = {
  image: IMAGE_SVG,
  doc: DOC_SVG,
  code: CODE_SVG,
  data: DATA_SVG,
  archive: ARCHIVE_SVG,
  media: MEDIA_SVG,
  config: CONFIG_SVG,
  web: WEB_SVG,
  text: TEXT_SVG,
}

export function getFileIconSVG(name: string): string {
  return SVG_MAP[getFileCategory(name)]
}
