/**
 * Client for the deterministic slash-command endpoints.
 *
 * <p>These calls never touch the model: they return structured data that the page renders as a
 * system card. Nothing is written to the conversation, so the payload can never leak into a later
 * prompt.</p>
 */
import { apiFetch } from '../auth/bff'
import { readJson } from './envelope'
import { getJson, readErrorMessage } from './http'

/** Snapshot returned by {@code /quota}. */
export interface SlashQuota {
  dailyTokenLimit: number
  totalTokens: number
  inputTokens: number
  outputTokens: number
  requestCount: number
  /** UTC day the counters belong to ({@code yyyy-MM-dd}). */
  date: string
  /** Next UTC midnight, ISO instant. */
  resetAt: string
}

/** Snapshot returned by {@code /profile}. */
export interface SlashProfile {
  exists: boolean
  language: string
  replyStyle: string
  expertise: string
  timezone: string | null
  preferences: string[]
  inferenceOptIn: boolean
  updatedAt: string | null
}

/** Snapshot returned by {@code /status}. */
export interface SlashStatus {
  chatModel: string
  embeddingModel: string
  indexing: boolean
  indexed: boolean
  files: number
  chunks: number
  indexedAt: string | null
  indexError: string | null
  features: Record<string, boolean>
  tools: string[]
  skills: string[]
  dailyTokenLimit: number
}

/** Result of {@code /clear}. */
export interface SlashClear {
  cleared: boolean
  droppedMessages: number
  boundarySeq: number
}

export async function fetchSlashQuota(): Promise<SlashQuota> {
  return getJson<SlashQuota>('/api/ai/slash/quota')
}

export async function fetchSlashProfile(): Promise<SlashProfile> {
  return getJson<SlashProfile>('/api/ai/slash/profile')
}

export async function fetchSlashStatus(): Promise<SlashStatus> {
  return getJson<SlashStatus>('/api/ai/slash/status')
}

/** Drops the conversation from the model context while keeping the transcript. */
export async function clearSlashContext(conversationUuid: string): Promise<SlashClear> {
  const response = await apiFetch('/api/ai/slash/clear', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conversationUuid }),
  })
  if (!response.ok) throw new Error(await readErrorMessage(response))
  return readJson<SlashClear>(response)
}
