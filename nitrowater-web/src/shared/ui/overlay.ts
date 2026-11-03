/**
 * Overlay plumbing shared by the business-side popup layer (AppDialog / AppDrawer / ConfirmHost).
 *
 * <p>Two things have to be global rather than per component: the body scroll lock (stacked
 * overlays would otherwise restore each other's state and leave the page frozen or scrollable)
 * and ESC ownership (only the topmost overlay may close — otherwise ESC over a confirm would
 * close the dialog underneath it).</p>
 */

let lockCount = 0
let savedOverflow = ''
let savedPaddingRight = ''

/**
 * Freezes background scrolling while an overlay is open.
 *
 * <p>The scrollbar width is compensated with padding so the page behind does not shift.</p>
 *
 * @returns an idempotent release function; the last release restores the previous state
 */
export function lockBodyScroll(): () => void {
  if (lockCount === 0) {
    const { body } = document
    const gap = window.innerWidth - document.documentElement.clientWidth
    savedOverflow = body.style.overflow
    savedPaddingRight = body.style.paddingRight
    body.style.overflow = 'hidden'
    if (gap > 0) body.style.paddingRight = `${gap}px`
  }
  lockCount += 1

  let released = false
  return () => {
    if (released) return
    released = true
    lockCount -= 1
    if (lockCount === 0) {
      document.body.style.overflow = savedOverflow
      document.body.style.paddingRight = savedPaddingRight
    }
  }
}

const stack: symbol[] = []

/**
 * Registers an open overlay.
 *
 * @returns its 1-based depth, used to stack the z-index in mounting order
 */
export function pushOverlay(token: symbol): number {
  stack.push(token)
  return stack.length
}

/** Removes an overlay that just closed. */
export function popOverlay(token: symbol): void {
  const index = stack.lastIndexOf(token)
  if (index >= 0) stack.splice(index, 1)
}

/** True while {@code token} is the overlay a key press should address. */
export function isTopOverlay(token: symbol | null): boolean {
  return token !== null && stack[stack.length - 1] === token
}

const FOCUSABLE = [
  'a[href]',
  'area[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  'iframe',
  '[tabindex]:not([tabindex="-1"])',
  '[contenteditable="true"]',
].join(',')

const FORM_CONTROL = [
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
].join(',')

function isVisible(element: HTMLElement): boolean {
  return element.getClientRects().length > 0
}

function collect(root: HTMLElement, selector: string): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>(selector)).filter(isVisible)
}

/**
 * Moves focus into an overlay.
 *
 * <p>The first text field wins over the close button, so a single-input dialog (verification
 * code, nickname, ...) is usable without an extra Tab; anything else focuses the panel itself and
 * lets the screen reader announce the title.</p>
 */
export function focusInitial(root: HTMLElement | null): void {
  if (!root) return
  const [control] = collect(root, FORM_CONTROL)
  const target = control ?? root
  target.focus({ preventScroll: true })
}

/** Keeps Tab / Shift+Tab cycling inside an overlay. */
export function trapTab(root: HTMLElement, event: KeyboardEvent): void {
  const items = collect(root, FOCUSABLE)
  const first = items[0]
  const last = items[items.length - 1]
  if (!first || !last) {
    event.preventDefault()
    root.focus({ preventScroll: true })
    return
  }

  const active = document.activeElement
  const outside = !(active instanceof Node) || !root.contains(active)
  if (event.shiftKey && (outside || active === first)) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && (outside || active === last)) {
    event.preventDefault()
    first.focus()
  }
}
