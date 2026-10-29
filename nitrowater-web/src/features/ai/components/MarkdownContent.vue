<script setup lang="ts">
/**
 * Renders assistant markdown.
 *
 * <p>Prose is parsed by `marked` and sanitized with DOMPurify; fenced code blocks are split out and
 * rendered as {@link CodeBlockCard} components (they own their sandbox terminal state). Two
 * post-processing steps run on the sanitized prose:</p>
 * <ul>
 *   <li>external links become inline "citation" capsules carrying their domain;</li>
 *   <li>{@code [n]} markers resolve against the turn's sources into small numbered chips.</li>
 * </ul>
 *
 * <p>Rendered prose is memoized by text + source signature: while an answer streams, only the last
 * block changes, so the completed blocks are not re-parsed on every frame.</p>
 */
import { computed } from 'vue'
import DOMPurify from 'dompurify'
import { Marked } from 'marked'
import type { AiSource } from '../api'
import CodeBlockCard from './CodeBlockCard.vue'

type Segment =
  | { readonly kind: 'prose'; readonly html: string }
  | {
    readonly kind: 'code'
    readonly language: string
    readonly code: string
    readonly complete: boolean
  }

const props = withDefaults(defineProps<{
  text: string
  streaming?: boolean
  sources?: AiSource[]
}>(), { streaming: false, sources: () => [] })

const markdown = new Marked({ gfm: true, breaks: true })

/** Bounded cache: a long answer produces one entry per streamed frame. */
const HTML_CACHE = new Map<string, string>()
const CACHE_LIMIT = 80

const segments = computed<Segment[]>(() => splitSegments(props.text, props.sources))

function sourceSignature(sources: readonly AiSource[]): string {
  return sources.map((source) => source.index).join(',')
}

function renderProse(text: string, sources: readonly AiSource[]): string {
  const key = `${sourceSignature(sources)}|${text}`
  const cached = HTML_CACHE.get(key)
  if (cached !== undefined) return cached
  const raw = markdown.parse(text) as string
  const clean = DOMPurify.sanitize(raw, { USE_PROFILES: { html: true } })
  const html = decorate(clean, sources)
  if (HTML_CACHE.size >= CACHE_LIMIT) {
    // Evict the oldest entry instead of clearing: a full clear would re-parse every completed
    // block on the next frame and look like a stutter mid-answer.
    const oldest = HTML_CACHE.keys().next().value
    if (oldest !== undefined) HTML_CACHE.delete(oldest)
  }
  HTML_CACHE.set(key, html)
  return html
}

/**
 * Splits markdown into prose and fenced code. An unclosed fence is kept as an incomplete code
 * segment so a streaming answer renders it as code without exposing a run button.
 */
function splitSegments(source: string, sources: readonly AiSource[]): Segment[] {
  const out: Segment[] = []
  let prose: string[] = []
  let fence: { readonly marker: string; readonly language: string; body: string[] } | null = null

  const flushProse = (): void => {
    const text = prose.join('\n')
    prose = []
    if (!text.trim()) return
    out.push({ kind: 'prose', html: renderProse(text, sources) })
  }

  for (const line of source.split('\n')) {
    const trimmed = line.trimStart()
    if (fence) {
      if (trimmed.startsWith(fence.marker)) {
        out.push({
          kind: 'code',
          language: fence.language,
          code: fence.body.join('\n'),
          complete: true,
        })
        fence = null
      } else {
        fence.body.push(line)
      }
      continue
    }
    const opening = /^(`{3,}|~{3,})\s*([\w+#.-]*)\s*$/.exec(trimmed)
    if (opening) {
      flushProse()
      fence = { marker: opening[1], language: opening[2] ?? '', body: [] }
      continue
    }
    prose.push(line)
  }

  if (fence) {
    out.push({
      kind: 'code',
      language: fence.language,
      code: fence.body.join('\n'),
      complete: false,
    })
  }
  flushProse()
  return out
}

/**
 * Adds citation affordances to sanitized prose.
 *
 * <p>Works on a detached template so DOMPurify stays the single sanitation step; only attributes
 * and wrapper elements are added afterwards, never raw model text.</p>
 */
function decorate(html: string, sources: readonly AiSource[]): string {
  const template = document.createElement('template')
  template.innerHTML = html

  template.content.querySelectorAll('a[href]').forEach((anchor) => {
    const href = anchor.getAttribute('href') ?? ''
    if (!/^https?:/i.test(href)) return
    anchor.setAttribute('target', '_blank')
    anchor.setAttribute('rel', 'noreferrer noopener')
    anchor.classList.add('cite-link')
    anchor.setAttribute('data-domain', hostOf(href))
  })

  replaceCitationMarkers(template.content, sources)
  return template.innerHTML
}

/** Rewrites `[n]` in text nodes (never inside code) into chips, linking when a source exists. */
function replaceCitationMarkers(root: DocumentFragment, sources: readonly AiSource[]): void {
  if (sources.length === 0) return
  const targets: Text[] = []
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  for (let node = walker.nextNode(); node !== null; node = walker.nextNode()) {
    const text = node as Text
    const tag = text.parentElement?.tagName
    // Never rewrite inside code, and never nest a chip inside an existing link.
    if (tag === 'CODE' || tag === 'PRE' || tag === 'A') continue
    if (/\[\d{1,2}\]/.test(text.nodeValue ?? '')) targets.push(text)
  }

  for (const node of targets) {
    const text = node.nodeValue ?? ''
    const fragment = document.createDocumentFragment()
    let last = 0
    for (const match of text.matchAll(/\[(\d{1,2})\]/g)) {
      const start = match.index ?? 0
      if (start > last) fragment.append(text.slice(last, start))
      fragment.append(citationChip(Number(match[1]), sources))
      last = start + match[0].length
    }
    if (last < text.length) fragment.append(text.slice(last))
    node.replaceWith(fragment)
  }
}

function citationChip(index: number, sources: readonly AiSource[]): Node {
  const source = sources.find((item) => item.index === index)
  if (!source) return document.createTextNode(`[${index}]`)
  const chip = document.createElement('a')
  chip.className = 'cite-index'
  chip.textContent = `[${index}]`
  chip.title = source.section || source.title || ''
  if (source.url) {
    chip.setAttribute('href', source.url)
    chip.setAttribute('target', '_blank')
    chip.setAttribute('rel', 'noreferrer noopener')
  }
  return chip
}

function hostOf(href: string): string {
  try {
    return new URL(href).hostname.replace(/^www\./, '')
  } catch {
    return ''
  }
}
</script>

<template>
  <div class="md">
    <template
      v-for="(segment, index) in segments"
      :key="index"
    >
      <CodeBlockCard
        v-if="segment.kind === 'code'"
        :language="segment.language"
        :code="segment.code"
        :streaming="streaming || !segment.complete"
      />
      <!-- eslint-disable vue/no-v-html -- sanitized by DOMPurify in renderProse -->
      <div
        v-else
        class="md-prose"
        v-html="segment.html"
      />
      <!-- eslint-enable vue/no-v-html -->
    </template>
  </div>
</template>

<style scoped>
.md :deep(p),
.md-prose :deep(p) {
  margin: 0.5em 0;
}

.md :deep(p:first-child),
.md-prose :deep(p:first-child) {
  margin-top: 0;
}

.md :deep(p:last-child),
.md-prose :deep(p:last-child) {
  margin-bottom: 0;
}

.md :deep(ul),
.md :deep(ol),
.md-prose :deep(ul),
.md-prose :deep(ol) {
  margin: 0.5em 0;
  padding-left: 1.3em;
}

.md :deep(ul),
.md-prose :deep(ul) {
  list-style: disc;
}

.md :deep(ol),
.md-prose :deep(ol) {
  list-style: decimal;
}

.md :deep(li),
.md-prose :deep(li) {
  margin: 0.15em 0;
}

.md :deep(h1),
.md :deep(h2),
.md :deep(h3),
.md-prose :deep(h1),
.md-prose :deep(h2),
.md-prose :deep(h3) {
  margin: 0.9em 0 0.4em;
  font-size: 1em;
  font-weight: 700;
}

.md :deep(h1:first-child),
.md :deep(h2:first-child),
.md :deep(h3:first-child),
.md-prose :deep(h1:first-child),
.md-prose :deep(h2:first-child),
.md-prose :deep(h3:first-child) {
  margin-top: 0;
}

/* Inline code keeps only the monospace face: the reference sample has no pill background. */
.md :deep(code),
.md-prose :deep(code) {
  font-family: var(--font-mono);
  font-size: 0.9em;
}

.md :deep(a),
.md-prose :deep(a) {
  color: var(--color-accent);
  text-decoration: underline;
  text-underline-offset: 2px;
}

/* Inline citation capsule for external links: title + domain, never a bare URL. */
.md :deep(.cite-link),
.md-prose :deep(.cite-link) {
  display: inline-flex;
  align-items: baseline;
  gap: 3px;
  margin: 0 1px;
  padding: 0 7px;
  border-radius: 999px;
  background: var(--color-soft);
  color: var(--color-ink);
  font-size: 0.86em;
  text-decoration: none;
  vertical-align: baseline;
  white-space: nowrap;
}

.md :deep(.cite-link:hover),
.md-prose :deep(.cite-link:hover) {
  background: color-mix(in srgb, var(--color-accent) 16%, transparent);
  color: var(--color-accent);
}

.md :deep(.cite-link)::after,
.md-prose :deep(.cite-link)::after {
  content: attr(data-domain);
  color: var(--color-faint);
  font-size: 0.92em;
}

/* Numbered chip for [n] markers that resolve against sources. */
.md :deep(.cite-index),
.md-prose :deep(.cite-index) {
  margin: 0 1px;
  padding: 0 4px;
  border-radius: 5px;
  background: color-mix(in srgb, var(--color-accent) 12%, transparent);
  color: var(--color-accent);
  font-size: 0.8em;
  font-weight: 600;
  text-decoration: none;
  vertical-align: super;
  line-height: 1;
}

.md :deep(blockquote),
.md-prose :deep(blockquote) {
  margin: 0.6em 0;
  border-left: 3px solid var(--color-line-strong);
  padding-left: 0.8em;
  color: var(--color-dim);
}

.md :deep(table),
.md-prose :deep(table) {
  margin: 0.6em 0;
  border-collapse: collapse;
  font-size: 0.94em;
}

.md :deep(th),
.md :deep(td),
.md-prose :deep(th),
.md-prose :deep(td) {
  border: 1px solid var(--color-line);
  padding: 0.3em 0.55em;
}
</style>
