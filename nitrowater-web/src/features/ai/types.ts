import type { AiSource, AiToolCall } from '../../api/ai'
import type { SlashProfile, SlashQuota, SlashStatus } from '../../api/slash'

/** One rendered live step: the wire marker plus the client-side identity and timing. */
export interface LiveStep {
  /** Server call id when available, otherwise a client-generated one. */
  id: string
  phase: 'thinking' | 'tool_start' | 'tool_end'
  tool: string | null
  argument: string | null
  /** Outcome code from the server; only used for debugging text, never for the badge. */
  status: string | null
  /** Whether the call was handled; {@code null} while it is still running. */
  ok: boolean | null
  resultCount: number | null
  /** Client-side start, used for the elapsed time when the server reported none. */
  startedAt: number
  durationMs: number | null
}




/**
 * Payload of a system card inserted by a slash command.
 *
 * <p>Cards are display-only: they are never persisted and never sent to the model, so their data
 * cannot leak into a later prompt.</p>
 */
export type SystemCard =
  | { type: 'quota'; data: SlashQuota }
  | { type: 'profile'; data: SlashProfile }
  | { type: 'status'; data: SlashStatus }
  | { type: 'notice'; text: string; tone: 'info' | 'warn' }

/** One dot of the conversation timeline (one per user question). */
export interface TimelineItem {
  id: string
  label: string
}

/**
 * One message in the NitroBot transcript.
 *
 * <p>Signed-in transcripts are stored server-side (see {@code useConversations}); transient fields
 * such as {@code streaming} and every {@code system} card stay in memory only.</p>
 */
export interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  sources: AiSource[]
  tools: AiToolCall[]
  skills: string[]
  /**
   * Process cards of a finished turn, kept in the local cache only.
   *
   * <p>The server stores the tool calls themselves ({@code tools}), which is enough to rebuild the
   * timeline after a reload; keeping the live cards here as well preserves the durations and the
   * exact list the user watched, so finishing a turn does not visibly rewrite it. Absent on
   * messages restored from another device.</p>
   */
  steps?: LiveStep[]
  grounded: boolean | null
  /** Fatal stream error, rendered under the bubble. */
  error: string | null
  /** The user aborted the stream; the partial answer is kept. */
  stopped?: boolean
  /** The provider stopped on a token limit; the answer can be continued. */
  truncated?: boolean
  /** The content gate masked part of this answer before it was stored. */
  moderated?: boolean
  /** Slash-command result; present only on {@code system} messages. */
  card?: SystemCard
}