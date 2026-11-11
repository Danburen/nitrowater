<script setup lang="ts">
/**
 * Live step timeline of the running agent turn.
 *
 * <p>Independent of the answer bubble on purpose: process and answer are two channels of the same
 * SSE stream, so the timeline can render before (and keep rendering while) the answer bubble is
 * still empty. Each marker the server sends becomes one card — an argument-bearing tool start, a
 * result-bearing tool end, or a round boundary.</p>
 *
 * <p>The list is live-only: it is never persisted, and the collapsed {@code ToolTrace} row takes
 * over once the turn completes.</p>
 */
import { computed } from 'vue'
import type { LiveStep } from '../types'

const props = defineProps<{
  steps: readonly LiveStep[]
  /** Whether the turn is still waiting for its first server marker. */
  awaiting?: boolean
}>()

/**
 * Placeholder row shown between the submit and the turn's first marker.
 *
 * <p>It mirrors the shape of the server's {@code thinking} row on purpose: the real marker simply
 * replaces it a few milliseconds later, so the user never sees the timeline appear from nothing.</p>
 */
const PENDING: LiveStep = {
  id: 'pending',
  phase: 'thinking',
  tool: null,
  argument: null,
  status: null,
  ok: null,
  resultCount: null,
  startedAt: 0,
  durationMs: null,
}

/** Whether the turn is still running, i.e. whether a screen reader should announce changes. */
const live = computed(() =>
  props.awaiting === true
  || props.steps.some((step) => step.phase === 'thinking' || step.phase === 'tool_start'))

/** @return the rows to render: real markers, or the pending card while none has arrived */
const shown = computed<readonly LiveStep[]>(() =>
  props.steps.length > 0 || props.awaiting !== true ? props.steps : [PENDING])

/** Display names of the agent's tools; unknown tools fall back to their raw id. */
const TOOL_LABELS: Readonly<Record<string, string>> = {
  searchDocs: '查阅文档',
  webSearch: '联网搜索',
  runCode: '运行代码',
  loadSkill: '加载技能',
  saveMemory: '写入记忆',
}

/** One or more SVG path commands, drawn with stroke only. */
const TOOL_ICONS: Readonly<Record<string, readonly string[]>> = {
  thinking: ['M12 3.5l1.7 4.3 4.3 1.7-4.3 1.7L12 15.5l-1.7-4.3L6 9.5l4.3-1.7z', 'M19 15l.9 2.1 2.1.9-2.1.9-.9 2.1-.9-2.1-2.1-.9 2.1-.9z'],
  searchDocs: ['M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z', 'M14 3v5h5', 'M9 13h6', 'M9 17h4'],
  webSearch: ['M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18', 'M3.5 12h17', 'M12 3a14 14 0 0 1 0 18', 'M12 3a14 14 0 0 0 0 18'],
  runCode: ['M4 17l6-5-6-5', 'M12 19h8'],
  loadSkill: ['M12 3l9 5-9 5-9-5 9-5z', 'M3 13l9 5 9-5'],
  saveMemory: ['M6 3h9l5 5v13H6z', 'M9 3v5h6'],
  fallback: ['M14.7 6.3a5 5 0 0 0 6.9 6.9L14 21h-4v-4z', 'M3 3l6 6'],
}

function label(step: LiveStep): string {
  if (step.phase === 'thinking') return '思考中'
  const name = TOOL_LABELS[step.tool ?? ''] ?? step.tool ?? '工具'
  return step.phase === 'tool_start' ? `正在${name}` : name
}

/** Short second line: argument first, then result count, outcome and duration. */
function detail(step: LiveStep): string {
  const parts: string[] = []
  if (step.argument) parts.push(step.argument)
  if (step.phase === 'tool_end') {
    if (step.resultCount) parts.push(`${step.resultCount} 条结果`)
    if (step.durationMs != null) parts.push(`${(step.durationMs / 1000).toFixed(1)}s`)
  }
  return parts.join(' · ')
}

function icon(step: LiveStep): readonly string[] {
  if (step.phase === 'thinking') return TOOL_ICONS.thinking
  return TOOL_ICONS[step.tool ?? ''] ?? TOOL_ICONS.fallback
}

/** @return running / failed / done, driving the colour and the status glyph */
function state(step: LiveStep): 'running' | 'failed' | 'done' {
  if (step.phase === 'tool_start' || step.phase === 'thinking') return 'running'
  return step.ok === false ? 'failed' : 'done'
}
</script>

<template>
  <ol
    class="live-steps"
    role="status"
    :aria-live="live ? 'polite' : 'off'"
  >
    <li
      v-for="step in shown"
      :key="step.id"
      class="live-step"
      :class="`is-${state(step)}`"
    >
      <span
        class="live-step-icon"
        aria-hidden="true"
      >
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.7"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path
            v-for="(d, index) in icon(step)"
            :key="index"
            :d="d"
          />
        </svg>
      </span>

      <span class="live-step-body">
        <span class="live-step-title">{{ label(step) }}</span>
        <span
          v-if="detail(step)"
          class="live-step-detail"
        >{{ detail(step) }}</span>
      </span>

      <span
        class="live-step-state"
        aria-hidden="true"
      >
        <svg
          v-if="state(step) === 'running'"
          class="live-spin"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2.2"
          stroke-linecap="round"
        >
          <path d="M21 12a9 9 0 1 1-6.2-8.6" />
        </svg>
        <svg
          v-else-if="state(step) === 'failed'"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
        >
          <circle
            cx="12"
            cy="12"
            r="9"
          />
          <path d="M12 7.5v5.5" />
          <path d="M12 16.5h.01" />
        </svg>
        <svg
          v-else
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2.2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <polyline points="20 6 9 17 4 12" />
        </svg>
      </span>
    </li>
  </ol>
</template>

<style scoped>
.live-steps {
  display: flex;
  flex-direction: column;
  gap: 6px;
  justify-content: flex-start;
  max-width: 460px;
  margin-bottom: 8px;
  list-style: none;
}

.live-step {
  position: relative;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 7px 10px;
  overflow: hidden;
  border: 1px solid var(--color-line);
  border-radius: 10px;
  background: var(--color-card);
  font-size: 12.5px;
  line-height: 1.4;
}

.live-step.is-running {
  border-color: color-mix(in srgb, var(--color-accent) 32%, transparent);
  background: color-mix(in srgb, var(--color-accent) 6%, var(--color-card));
}

/* Sweeping underline: the only "loading" affordance on the card itself. */
.live-step.is-running::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--color-accent), transparent);
  animation: live-sweep 1.4s ease-in-out infinite;
}

.live-step-icon {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 7px;
  background: color-mix(in srgb, var(--color-accent) 10%, transparent);
  color: var(--color-accent);
}

.live-step-icon svg {
  width: 13px;
  height: 13px;
}

.live-step.is-done .live-step-icon {
  background: color-mix(in srgb, var(--color-line) 60%, transparent);
  color: var(--color-dim);
}

.live-step.is-failed .live-step-icon {
  background: color-mix(in srgb, var(--color-warn) 14%, transparent);
  color: var(--color-warn);
}

.live-step-body {
  display: flex;
  flex: 1;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}

.live-step-title {
  flex-shrink: 0;
  color: var(--color-ink);
  font-weight: 500;
}

.live-step-detail {
  overflow: hidden;
  color: var(--color-faint);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.live-step-state {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 14px;
  height: 14px;
  color: var(--color-dim);
}

.live-step.is-failed .live-step-state {
  color: var(--color-warn);
}

.live-step-state svg {
  width: 14px;
  height: 14px;
}

.live-spin {
  color: var(--color-accent);
  animation: live-spin 1.2s linear infinite;
}

@keyframes live-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes live-sweep {
  0% {
    transform: translateX(-100%);
  }

  100% {
    transform: translateX(100%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .live-spin,
  .live-step.is-running::after {
    animation: none;
  }
}
</style>
