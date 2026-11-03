/**
 * Wire types of the documentation assistant.
 *
 * This file holds declarations only (no runtime code), so it can be imported with `import type`
 * from every other module of the domain without creating a cycle.
 */

/** A citation returned with an answer (local doc chunk or web result). */
export interface AiSource {
  index: number
  kind: 'doc' | 'web'
  title: string
  section: string
  path: string
  url: string
  score: number
  snippet: string
}

/** Index/model status. */
export interface AiStatus {
  enabled: boolean
  indexing: boolean
  indexed: boolean
  files: number
  chunks: number
  indexedAt: string | null
  durationMs: number
  error: string | null
  collection: string
  chatModel: string
  embeddingModel: string
  topK: number
  similarityThreshold: number
  webSearchEnabled: boolean
  dailyTokenLimit: number
  chatEnabled: boolean
  agentEnabled: boolean
  documentSearchEnabled: boolean
  codeRunnerEnabled: boolean
  skillsEnabled: boolean
  /** Platform-wide daily budget: when tripped, chat is closed for everyone (history + slash keep working). */
  globalQuota: AiGlobalQuota
}

/** All-users daily budget snapshot, delivered with {@link AiStatus}. */
export interface AiGlobalQuota {
  enabled: boolean
  limit: number
  used: number
  tripped: boolean
  resetAt: string
}

/** One user's UTC-day token usage. */
export interface AiDailyUsage {
  date: string
  inputTokens: number
  outputTokens: number
  reservedTokens: number
  requestCount: number
  estimatedRequests: number
  dailyTokenLimit: number
  totalTokens: number
}

/** Content-moderation posture. In {@code LOG} hits are recorded but nothing is blocked. */
export type AiModerationMode = 'OFF' | 'LOG' | 'ENFORCE'

/** Runtime controls and per-user token ceiling edited by administrators. */
export interface AiAdminSettings {
  dailyTokenLimit: number
  aiEnabled: boolean
  chatEnabled: boolean
  agentEnabled: boolean
  documentSearchEnabled: boolean
  webSearchEnabled: boolean
  codeRunnerEnabled: boolean
  skillsEnabled: boolean
  moderationMode: AiModerationMode
  /** All-users daily ceiling in tokens; 0 = unlimited. */
  globalDailyTokenLimit: number
  /** Master switch for the platform-wide breaker. */
  globalQuotaEnabled: boolean
}

/** Platform-wide usage series for the quota console. */
export interface AiUsageSummary {
  globalEnabled: boolean
  globalDailyTokenLimit: number
  todayTokens: number
  todayRequests: number
  tripped: boolean
  today: string
  days: AiUsageDay[]
}

export interface AiUsageDay {
  date: string
  inputTokens: number
  outputTokens: number
  totalTokens: number
  requestCount: number
}

/** One recorded content-moderation hit. */
export interface AiModerationHit {
  id: number
  uid: number
  username: string | null
  nickname: string | null
  conversationUuid: string | null
  /** INPUT / OUTPUT / MEMORY */
  direction: string
  /** LOGGED / BLOCKED / MASKED */
  action: string
  words: string
  hitCount: number
  createdAt: string
}

/** Moderation posture, loaded dictionary size and the newest hits. */
export interface AiModerationReport {
  mode: AiModerationMode
  dictionarySize: number
  /** Hits recorded in the last 24 hours, all directions. */
  hitsLast24Hours: number
  /** Hits matching the active filters, ignoring the paging cursor. */
  total: number
  /** Whether older hits exist beyond this page. */
  hasMore: boolean
  hits: AiModerationHit[]
}

/** Filters for the moderation review panel; every field is optional. */
export interface AiModerationQuery {
  direction?: string
  action?: string
  /** Keyset cursor: return hits with an id lower than this. */
  beforeId?: number
  limit?: number
}

/** Administrator view of one user's usage for the current UTC day. */
export interface AiUserUsage {
  uid: number
  username: string | null
  nickname: string | null
  usage: AiDailyUsage
}

/** A skill shown in the picker / admin console. */
export interface AiSkill {
  name: string
  description: string
  whenToUse: string
  source: 'builtin' | 'external'
}

/** One tool invocation recorded by the agent. */
export interface AiToolCall {
  name: string
  argument: string
  resultCount: number
  /** Outcome code, e.g. tool.ok / tool.compile_error / tool.queued. */
  status?: string
  /** Short human-readable detail for the tool chip. */
  summary?: string | null
  /** Whether the call was handled; absent only on rows stored before the code vocabulary. */
  ok?: boolean
}

/** A tool in the admin catalog. */
export interface AiTool {
  name: string
  description: string
  source: 'core' | 'web'
  enabled: boolean
  inputSchema: string
}

/** One previous turn; the server keeps no session state. */
export interface ChatTurn {
  role: 'user' | 'assistant'
  content: string
}

/** One live progress marker of an agent turn (wire shape of the {@code step} SSE event). */
export interface AgentStep {
  phase: 'thinking' | 'tool_start' | 'tool_end' | string
  tool: string | null
  /** Per-invocation id, so a start can be paired with its end. */
  callId?: string | null
  /** Human-readable argument summary, sent on {@code tool_start}. */
  argument?: string | null
  /** Outcome code (tool.ok / tool.compile_error / tool.queued …), sent on {@code tool_end}. */
  status?: string | null
  /** Whether the call was handled; the badge reads this instead of comparing codes. */
  ok?: boolean | null
  /** Results produced, sent on {@code tool_end}. */
  resultCount?: number | null
  /** Wall-clock duration of the invocation, sent on {@code tool_end}. */
  durationMs?: number | null
}

/** How the model ended the turn. */
export interface AgentDoneMeta {
  finishReason: string | null
  /** True when the provider stopped because the token limit was reached. */
  truncated: boolean
}

/** Callbacks for one streamed agent turn. */
export interface AgentStreamHandlers {
  onConversation?: (uuid: string) => void
  onStart?: () => void
  onDelta?: (text: string) => void
  onSources?: (sources: AiSource[], grounded: boolean) => void
  onTools?: (tools: AiToolCall[]) => void
  onSkills?: (skills: string[]) => void
  onStep?: (step: AgentStep) => void
  /** The server masked the finished answer; replace the bubble with {@code text}. */
  onModeration?: (text: string) => void
  onDone?: (meta: AgentDoneMeta) => void
  onError?: (message: string, code?: string) => void
}

/** Result of a documentation re-index run. */
export interface AiReindexResult {
  files: number
  chunks: number
  indexedAt: string
  durationMs: number
}
