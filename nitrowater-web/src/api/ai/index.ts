/**
 * AI domain barrel.
 *
 * Split by concern: `types` holds the wire shapes, `chat` the user-facing calls and the SSE
 * stream, `admin` the `/api/admin/ai/**` endpoints. Call sites keep using the domain path:
 * `import { streamAgent, type AiStatus } from '../../api/ai'`.
 */
export * from './types'
export * from './chat'
export * from './admin'
