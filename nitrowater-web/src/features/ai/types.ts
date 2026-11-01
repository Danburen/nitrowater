import type { AiSource, AiToolCall } from '../../api/ai'
import type { SlashProfile, SlashQuota, SlashStatus } from '../../api/slash'

/** Transient progress of a streaming agent turn (rendered as the live activity card). */
export interface ChatActivity {
  phase: 'thinking' | 'tool_start' | 'tool_end'
  tool: string | null
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
  grounded: boolean | null
  /** Fatal stream error, rendered under the bubble. */
  error: string | null
  /** The user aborted the stream; the partial answer is kept. */
  stopped?: boolean
  /** The provider stopped on a token limit; the answer can be continued. */
  truncated?: boolean
  /** The content gate masked part of this answer before it was stored. */
  moderated?: boolean
  /** Live tool/thinking state while streaming. */
  activity?: ChatActivity | null
  /** Slash-command result; present only on {@code system} messages. */
  card?: SystemCard
}