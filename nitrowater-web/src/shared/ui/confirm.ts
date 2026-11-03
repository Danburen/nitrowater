/**
 * Promise-based confirmation, rendered by {@link ConfirmHost} through the shared `AppDialog`.
 *
 * <p>Replaces `ElMessageBox.confirm` so every confirmation on the business side — portal, tools
 * and the admin console — has the same chrome as the dialogs next to it. Cancelling is a normal
 * answer, so the promise resolves `false` instead of rejecting: call sites read
 * {@code if (!(await confirm({...}))) return} rather than using exceptions for control flow.</p>
 */
import { reactive } from 'vue'

export interface ConfirmOptions {
  /** Heading; defaults to 确认操作. */
  title?: string
  /** Body text; the only required field. */
  message: string
  confirmText?: string
  cancelText?: string
  /** Paints the confirm button red. Use it for anything that destroys data. */
  danger?: boolean
}

export interface ConfirmRequest {
  options: ConfirmOptions
  resolve: (confirmed: boolean) => void
}

/** FIFO of pending confirmations: a second one queues instead of clobbering the first. */
export const confirmQueue = reactive<ConfirmRequest[]>([])

/**
 * Asks the user to confirm an action.
 *
 * @param options dialog content, or just the message for the common case
 * @returns `true` when the user confirmed, `false` when they cancelled or dismissed
 */
export function confirm(options: ConfirmOptions | string): Promise<boolean> {
  const normalized: ConfirmOptions = typeof options === 'string' ? { message: options } : options
  return new Promise<boolean>((resolve) => {
    confirmQueue.push({ options: normalized, resolve })
  })
}

/**
 * Settles the head of the queue and lets the next request take its place.
 *
 * <p>Called by {@link ConfirmHost} only; the queue is drained in order.</p>
 */
export function settleConfirm(confirmed: boolean): void {
  const head = confirmQueue.shift()
  head?.resolve(confirmed)
}
