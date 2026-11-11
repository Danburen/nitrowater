<script setup lang="ts">
/**
 * Message composer: auto-growing textarea, Enter to send, stop while streaming, and the slash-menu
 * for deterministic shortcuts.
 *
 * <p>It sits in the flex column (not absolutely positioned) so a mobile keyboard and the on-screen
 * chrome cannot overlap it. The slash menu only lists whitelisted commands; selecting one fills the
 * draft and the actual interception happens in the page when the message is sent.</p>
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { SLASH_COMMANDS, matchSlashCommands, opensSlashMenu, type SlashCommand } from '../slash'
import UiTooltip from '../../../shared/ui/UiTooltip.vue'

const props = withDefaults(defineProps<{
  modelValue: string
  busy: boolean
  disabled: boolean
  placeholder: string
  /** Muted footline that replaces the send hints, e.g. an exhausted daily budget. */
  hint?: string
  /** Slash commands need a session; they stay available whenever the composer itself is usable. */
  commandsEnabled: boolean
  /** Whether the server exposes a web-search provider for this turn. */
  webSearchAvailable?: boolean
  /** User intent for the next turn; only effective when the server allows it. */
  webSearch?: boolean
}>(), { webSearchAvailable: false, webSearch: false })

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'submit'): void
  (e: 'stop'): void
  (e: 'login'): void
  (e: 'update:webSearch', value: boolean): void
}>()

const MAX_HEIGHT = 180
const inputEl = ref<HTMLTextAreaElement | null>(null)
/** Only enable native overflow scrolling once the auto-grown field hits its cap. */
const overflowing = ref(false)
const menuIndex = ref(0)
/** Draft for which the user pressed Escape; the menu stays closed for that exact text. */
const escapedDraft = ref<string | null>(null)

const draft = computed(() => props.modelValue.trim())
const exactCommand = computed(() => SLASH_COMMANDS.some((item) => item.label === draft.value))
const suggestions = computed<SlashCommand[]>(() => {
  if (!props.commandsEnabled || props.disabled) return []
  if (!opensSlashMenu(props.modelValue) || escapedDraft.value === props.modelValue) return []
  return matchSlashCommands(props.modelValue)
})
const menuOpen = computed(() => suggestions.value.length > 0 && !exactCommand.value)

watch(() => props.modelValue, () => {
  menuIndex.value = 0
  void nextTick(resize)
})

onMounted(() => {
  resize()
  focus()
})

function resize(): void {
  const element = inputEl.value
  if (!element) return
  element.style.height = 'auto'
  const contentHeight = element.scrollHeight
  element.style.height = `${Math.min(contentHeight, MAX_HEIGHT)}px`
  // Keep overflow hidden while the field fits. Browsers otherwise reserve/show a scrollbar
  // during the auto-grow measurement, including for the empty state and one or two blank lines.
  overflowing.value = contentHeight > MAX_HEIGHT
}

function onInput(event: Event): void {
  escapedDraft.value = null
  emit('update:modelValue', (event.target as HTMLTextAreaElement).value)
  resize()
}

/** Fills the draft with a complete command; the user confirms with Enter. */
function apply(command: SlashCommand): void {
  emit('update:modelValue', command.label)
  escapedDraft.value = command.label
  void nextTick(() => {
    resize()
    focus()
  })
}

function onKeydown(event: KeyboardEvent): void {
  // Enter confirms an IME candidate while composing: never treat it as navigation or send.
  if (event.isComposing || event.keyCode === 229) return

  if (menuOpen.value) {
    const count = suggestions.value.length
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      menuIndex.value = (menuIndex.value + 1) % count
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      menuIndex.value = (menuIndex.value - 1 + count) % count
      return
    }
    if (event.key === 'Escape') {
      event.preventDefault()
      escapedDraft.value = props.modelValue
      return
    }
    if (event.key === 'Enter' || event.key === 'Tab') {
      event.preventDefault()
      apply(suggestions.value[menuIndex.value])
      return
    }
  }

  if (event.key !== 'Enter' || event.shiftKey) return
  event.preventDefault()
  if (!props.busy && !props.disabled && props.modelValue.trim()) emit('submit')
}

function focus(): void {
  inputEl.value?.focus()
}

defineExpose({ focus })
</script>

<template>
  <div class="shrink-0 px-3 pb-2 pt-1 sm:px-4 sm:pb-4">
    <form
      class="relative mx-auto w-full max-w-[780px]"
      @submit.prevent="emit('submit')"
    >
      <div
        v-if="menuOpen"
        id="slash-commands"
        class="absolute bottom-full left-0 right-0 z-20 mb-2 overflow-hidden rounded-[14px] border border-line bg-card shadow-[0_10px_30px_rgba(0,0,0,0.12)]"
        role="listbox"
        aria-label="可用指令"
      >
        <button
          v-for="(command, index) in suggestions"
          :id="`slash-${command.name}`"
          :key="command.name"
          type="button"
          role="option"
          :aria-selected="index === menuIndex"
          class="flex w-full items-baseline gap-2 px-3.5 py-2 text-left transition"
          :class="index === menuIndex ? 'bg-soft' : 'hover:bg-soft'"
          @mousedown.prevent="apply(command)"
        >
          <span class="font-mono text-[12.5px] font-medium text-ink">{{ command.label }}</span>
          <span class="text-[11.5px] text-dim">{{ command.description }}</span>
        </button>
      </div>

      <div class="rounded-[20px] border border-line bg-card shadow-[0_6px_24px_rgba(0,0,0,0.07)] transition focus-within:border-accent/60 focus-within:ring-2 focus-within:ring-accent/15">
        <textarea
          ref="inputEl"
          :value="modelValue"
          rows="1"
          class="scroll-thin block max-h-[180px] min-h-[26px] w-full resize-none bg-transparent px-3.5 pt-2.5 text-[14.5px] leading-[1.6] text-ink outline-none placeholder:text-faint sm:px-4"
          :class="overflowing ? 'overflow-y-auto' : 'overflow-y-hidden'"
          :placeholder="placeholder"
          :disabled="disabled"
          aria-label="消息输入框"
          aria-autocomplete="list"
          :aria-expanded="menuOpen"
          :aria-controls="menuOpen ? 'slash-commands' : undefined"
          :aria-activedescendant="menuOpen ? `slash-${suggestions[menuIndex]?.name}` : undefined"
          @input="onInput"
          @keydown="onKeydown"
        />
        <div class="flex items-center gap-1.5 px-2 pb-1.5 pt-1">
          <UiTooltip
            v-if="webSearchAvailable && !disabled"
            text="本轮允许联网搜索（关闭后不发起任何网络请求）"
            placement="top"
          >
            <button
              type="button"
              class="chip-toggle inline-flex items-center gap-1 rounded-full border border-line px-2.5 py-1 text-[11.5px] text-dim transition hover:border-line-strong hover:text-ink"
              :class="{ 'is-on': webSearch }"
              :aria-pressed="webSearch"
              :title="webSearch ? '本轮联网：开' : '本轮联网：关'"
              @click="emit('update:webSearch', !webSearch)"
            >
              <svg
                class="size-3.5"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <circle cx="12" cy="12" r="9" />
                <path d="M3 12h18M12 3a15 15 0 0 1 0 18M12 3a15 15 0 0 0 0 18" />
              </svg>
              联网
            </button>
          </UiTooltip>
          <span class="ml-auto" />
          <button
            v-if="busy"
            type="button"
            class="grid size-9 shrink-0 place-items-center rounded-[11px] bg-danger text-white transition hover:opacity-90"
            title="停止生成"
            aria-label="停止生成"
            @click="emit('stop')"
          >
            <svg
              class="size-4"
              viewBox="0 0 24 24"
              fill="currentColor"
            >
              <rect x="6" y="6" width="12" height="12" rx="2" />
            </svg>
          </button>
          <button
            v-else
            type="submit"
            class="grid size-9 shrink-0 place-items-center rounded-[11px] bg-accent text-white transition hover:bg-accent-strong disabled:cursor-not-allowed disabled:bg-line disabled:text-faint"
            :disabled="!modelValue.trim() || disabled"
            title="发送"
            aria-label="发送消息"
          >
            <svg
              class="size-4"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <line x1="12" y1="19" x2="12" y2="5" />
              <polyline points="5 12 12 5 19 12" />
            </svg>
          </button>
        </div>
      </div>      <p class="mt-1.5 text-center text-[11px] text-faint">
        <template v-if="disabled">
          <button
            type="button"
            class="text-accent transition hover:text-accent-strong hover:underline"
            @click="emit('login')"
          >
            立即登录 →
          </button>
        </template>
        <span
          v-else-if="hint"
          class="text-warn"
        >{{ hint }}</span>
        <template v-else>
          <template v-if="commandsEnabled && !modelValue.trim()">
            输入 <span class="font-mono">/</span> 查看指令 ·
          </template>
          Enter 发送 · Shift + Enter 换行 · 回答由 AI 生成，请核对关键信息
        </template>
      </p>
    </form>
  </div>
</template>

<style scoped>
/* On-state only: the base pill is utilities in the template, and swapping border/text colours from
   a state class keeps them from fighting the base utilities over CSS order. */
.chip-toggle.is-on {
  border-color: color-mix(in oklab, var(--color-accent) 40%, transparent);
  background: color-mix(in oklab, var(--color-accent) 10%, transparent);
  color: var(--color-accent);
}
</style>
