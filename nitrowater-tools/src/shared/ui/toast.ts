import { reactive } from 'vue'

/** Lightweight toast store (replaces Element Plus ElMessage in migrated tools). */
export type ToastType = 'success' | 'warning' | 'error' | 'info'

export interface ToastItem {
  id: number
  type: ToastType
  text: string
}

let seq = 0
export const toasts = reactive<ToastItem[]>([])

function push(type: ToastType, text: string, duration = 2600): void {
  const id = ++seq
  toasts.push({ id, type, text })
  window.setTimeout(() => {
    const index = toasts.findIndex((item) => item.id === id)
    if (index >= 0) {
      toasts.splice(index, 1)
    }
  }, duration)
}

export const toast = {
  success: (text: string) => push('success', text),
  warning: (text: string) => push('warning', text),
  error: (text: string) => push('error', text),
  info: (text: string) => push('info', text),
}
