<script setup lang="ts">
/**
 * The single modal used by business pages (portal, tools and the admin console).
 *
 * <p>Hand-rolled on Tailwind instead of `el-dialog` on purpose: the portal is themed through the
 * `--color-*` tokens, while Element Plus paints from its own variables and only looks right when
 * the resolver injected its CSS. Tailwind inherits the page tokens by construction, so the dialog
 * cannot drift between themes and there is no CSS-injection failure mode left.</p>
 *
 * <p>It still behaves like a real modal: teleported to `body` (never clipped by a page's
 * `overflow`), background scroll locked, ESC closes the topmost overlay only, focus is trapped and
 * restored, and `role="dialog"` + `aria-modal` are exposed. The default footer is the house
 * standard (取消 / 确定); pass `#footer` when a dialog needs its own actions.</p>
 *
 * <p>Deliberately free of CSS transitions and of Vue's {@code <Transition>}: mounting and
 * unmounting are driven by {@code v-if} alone. A dialog must never be able to strand itself
 * half-way through a transition (an invisible overlay that still swallows clicks is the worst
 * possible failure here), and the entrance motion is a few pixels of transform — it degrades to
 * "no motion" instead of "invisible".</p>
 */
import { computed, nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'
import { focusInitial, isTopOverlay, lockBodyScroll, popOverlay, pushOverlay, trapTab } from './overlay'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
    /** Muted line under the title; optional. */
    description?: string
    /** CSS width of the panel, always capped to the viewport. */
    width?: string
    confirmText?: string
    cancelText?: string
    confirmLoading?: boolean
    confirmDisabled?: boolean
    /** Paints the confirm button in the destructive palette. */
    danger?: boolean
    showFooter?: boolean
    showCancel?: boolean
    /** Clicking the dimmed backdrop dismisses the dialog. */
    closeOnOverlay?: boolean
    closeOnEsc?: boolean
    /** Hides the header close button (for flows that must end through an action). */
    hideClose?: boolean
    /** Accessible name used when the dialog has no visible title. */
    ariaLabel?: string
  }>(),
  {
    title: '',
    description: '',
    width: '440px',
    confirmText: '确定',
    cancelText: '取消',
    confirmLoading: false,
    confirmDisabled: false,
    danger: false,
    showFooter: true,
    showCancel: true,
    closeOnOverlay: true,
    closeOnEsc: true,
    hideClose: false,
    ariaLabel: '对话框',
  },
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'confirm'): void
  (event: 'cancel'): void
  (event: 'closed'): void
}>()

const panel = ref<HTMLElement | null>(null)
const titleId = useId()
const descriptionId = useId()
const zIndex = ref(1000)

const hasHeader = computed(() => props.title !== '' || !props.hideClose)
const hasDescription = computed(() => props.description !== '')

let token: symbol | null = null
let releaseScroll: (() => void) | null = null
let restoreFocus: HTMLElement | null = null

function activate(): void {
  if (token) return
  restoreFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  token = Symbol('app-dialog')
  zIndex.value = 1000 + pushOverlay(token)
  releaseScroll = lockBodyScroll()
  void nextTick(() => focusInitial(panel.value))
}

function deactivate(): void {
  releaseScroll?.()
  releaseScroll = null
  if (token) popOverlay(token)
  token = null
  restoreFocus?.focus({ preventScroll: true })
  restoreFocus = null
  emit('closed')
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) activate()
    else if (token) deactivate()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  // Route change while open: release everything silently (no `closed` event on teardown).
  releaseScroll?.()
  releaseScroll = null
  if (token) popOverlay(token)
  token = null
})

function dismiss(): void {
  emit('update:modelValue', false)
}

function onCancel(): void {
  emit('cancel')
  dismiss()
}

function onOverlay(): void {
  if (props.closeOnOverlay) dismiss()
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    if (!props.closeOnEsc || !isTopOverlay(token)) return
    event.preventDefault()
    dismiss()
    return
  }
  if (event.key === 'Tab' && panel.value) {
    trapTab(panel.value, event)
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="modelValue"
      class="fixed inset-0 overflow-y-auto"
      :style="{ zIndex }"
    >
      <div
        class="fixed inset-0 bg-black/50"
        @click="onOverlay"
      />

      <div class="relative flex min-h-full items-center justify-center p-4">
        <div
          ref="panel"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="title ? titleId : undefined"
          :aria-describedby="hasDescription ? descriptionId : undefined"
          :aria-label="title ? undefined : ariaLabel"
          tabindex="-1"
          class="app-dialog-panel relative flex max-h-[calc(100dvh-2rem)] w-full flex-col overflow-hidden rounded-[14px] border border-line bg-card text-ink shadow-2xl outline-none"
          :style="{ width, maxWidth: 'calc(100vw - 2rem)' }"
          @keydown="onKeydown"
        >
          <header
            v-if="hasHeader"
            class="flex shrink-0 items-start gap-3 px-4 pb-3 pt-4"
          >
            <div class="min-w-0 flex-1">
              <h2
                v-if="title"
                :id="titleId"
                class="truncate text-[15px] font-semibold text-ink"
              >
                {{ title }}
              </h2>
              <p
                v-if="hasDescription"
                :id="descriptionId"
                class="mt-1 text-[12.5px] leading-5 text-dim"
              >
                {{ description }}
              </p>
            </div>
            <button
              v-if="!hideClose"
              type="button"
              class="icon-btn -mr-1 -mt-1"
              title="关闭"
              aria-label="关闭"
              @click="dismiss"
            >
              <svg
                class="size-4"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
              >
                <path d="M6 6l12 12M18 6L6 18" />
              </svg>
            </button>
          </header>

          <div class="min-h-0 flex-1 overflow-y-auto px-4 pb-4">
            <slot />
          </div>

          <footer
            v-if="showFooter"
            class="shrink-0 border-t border-line px-4 py-3"
          >
            <slot name="footer">
              <div class="flex items-center justify-end gap-2">
                <button
                  v-if="showCancel"
                  type="button"
                  class="btn"
                  @click="onCancel"
                >
                  {{ cancelText }}
                </button>
                <button
                  type="button"
                  class="btn"
                  :class="danger ? 'border-danger bg-danger text-white hover:opacity-90' : 'btn-primary'"
                  :disabled="confirmLoading || confirmDisabled"
                  @click="emit('confirm')"
                >
                  <span
                    v-if="confirmLoading"
                    class="size-3.5 animate-spin rounded-full border-2 border-current border-t-transparent"
                    aria-hidden="true"
                  />
                  {{ confirmText }}
                </button>
              </div>
            </slot>
          </footer>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
/* Entrance motion is transform-only: if the animation clock never advances (background tab,
   throttled renderer) the panel is a few pixels low but fully visible — never stranded at
   opacity 0 with an invisible overlay still swallowing clicks. */
.app-dialog-panel {
  animation: app-dialog-rise 0.16s ease-out;
}

@keyframes app-dialog-rise {
  from {
    transform: translateY(6px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .app-dialog-panel {
    animation: none;
  }
}
</style>
