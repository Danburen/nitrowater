<script setup lang="ts">
/**
 * Side panel for business pages — the drawer counterpart of {@link AppDialog}.
 *
 * <p>Same reasons for being Tailwind-native: it inherits the `--color-*` tokens, so the panel is
 * correct in both themes without any Element Plus bridge, and it carries the same modal behaviour
 * (teleport, scroll lock, ESC for the topmost overlay, focus trap / restore). Like the dialog it
 * mounts and unmounts on `v-if` alone, so a throttled animation clock can never leave the page
 * blocked behind a half-open panel.</p>
 */
import { computed, nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'
import { focusInitial, isTopOverlay, lockBodyScroll, popOverlay, pushOverlay, trapTab } from './overlay'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
    side?: 'left' | 'right'
    /** CSS width of the panel, always capped to the viewport. */
    width?: string
    /** Adds the standard body padding; turn it off when the slot owns its own layout. */
    pad?: boolean
    /** Clicking the dimmed backdrop dismisses the panel. */
    closeOnOverlay?: boolean
    closeOnEsc?: boolean
    hideClose?: boolean
    ariaLabel?: string
  }>(),
  {
    title: '',
    side: 'right',
    width: '560px',
    pad: true,
    closeOnOverlay: true,
    closeOnEsc: true,
    hideClose: false,
    ariaLabel: '侧边面板',
  },
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'closed'): void
}>()

const panel = ref<HTMLElement | null>(null)
const titleId = useId()
const zIndex = ref(1000)

const hasHeader = computed(() => props.title !== '' || !props.hideClose)

let token: symbol | null = null
let releaseScroll: (() => void) | null = null
let restoreFocus: HTMLElement | null = null

function activate(): void {
  if (token) return
  restoreFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  token = Symbol('app-drawer')
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
  releaseScroll?.()
  releaseScroll = null
  if (token) popOverlay(token)
  token = null
})

function dismiss(): void {
  emit('update:modelValue', false)
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
      class="fixed inset-0"
      :style="{ zIndex }"
    >
      <div
        class="absolute inset-0 bg-black/50"
        @click="onOverlay"
      />

      <aside
        ref="panel"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="title ? titleId : undefined"
        :aria-label="title ? undefined : ariaLabel"
        :data-side="side"
        tabindex="-1"
        class="app-drawer-panel absolute inset-y-0 flex w-full max-w-[100vw] flex-col bg-bg text-ink shadow-2xl outline-none"
        :class="side === 'right' ? 'right-0 border-l border-line' : 'left-0 border-r border-line'"
        :style="{ width }"
        @keydown="onKeydown"
      >
        <header
          v-if="hasHeader"
          class="flex h-[56px] shrink-0 items-center gap-3 border-b border-line px-4"
        >
          <slot name="header">
            <h2
              v-if="title"
              :id="titleId"
              class="min-w-0 flex-1 truncate text-[15px] font-semibold text-ink"
            >
              {{ title }}
            </h2>
            <span
              v-else
              class="flex-1"
            />
          </slot>
          <button
            v-if="!hideClose"
            type="button"
            class="icon-btn"
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

        <div
          class="min-h-0 flex-1 overflow-y-auto"
          :class="pad ? 'p-4' : ''"
        >
          <slot />
        </div>

        <footer
          v-if="$slots.footer"
          class="flex shrink-0 items-center justify-end gap-2 border-t border-line px-4 py-3"
        >
          <slot name="footer" />
        </footer>
      </aside>
    </div>
  </Teleport>
</template>

<style scoped>
.app-drawer-panel {
  animation: app-drawer-slide 0.18s ease-out;
}

@keyframes app-drawer-slide {
  from {
    transform: translateX(24px);
  }
}

.app-drawer-panel[data-side='left'] {
  animation-name: app-drawer-slide-left;
}

@keyframes app-drawer-slide-left {
  from {
    transform: translateX(-24px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .app-drawer-panel,
  .app-drawer-panel[data-side='left'] {
    animation: none;
  }
}
</style>
