<script setup lang="ts">
/**
 * Conversation sidebar: brand, new chat, recent conversations, theme / account footer.
 *
 * <p>The transcript itself lives in the browser (see {@code useConversations}); this panel is the
 * only place that offers navigation, so the chat column can stay a clean single-purpose surface.</p>
 */
import SvgIcon from '../../../shared/ui/SvgIcon.vue'
import ThemeToggle from '../../../shared/ui/ThemeToggle.vue'
import type { Conversation } from '../composables/useConversations'

defineProps<{
  conversations: Conversation[]
  activeId: string | null
  open: boolean
  collapsed: boolean
  authenticated: boolean
  userLabel: string
  userInitial: string
}>()

const emit = defineEmits<{
  (e: 'new-chat'): void
  (e: 'select', id: string): void
  (e: 'remove', id: string): void
  (e: 'toggle'): void
  (e: 'close'): void
  (e: 'logout'): void
}>()
</script>

<template>
  <aside
    class="fixed inset-y-0 left-0 z-40 flex w-[264px] shrink-0 flex-col border-r border-line bg-soft transition-[transform,width,opacity] duration-200 lg:static lg:z-auto lg:translate-x-0"
    :class="[
      open ? 'translate-x-0 shadow-2xl' : '-translate-x-full lg:translate-x-0',
      collapsed ? 'lg:w-0 lg:overflow-hidden lg:border-r-0 lg:opacity-0' : '',
    ]"
    aria-label="对话列表"
  >
    <div class="flex h-14 shrink-0 items-center gap-1 px-2.5">
      <RouterLink
        to="/"
        class="flex min-w-0 flex-1 items-center gap-2 rounded-lg px-1.5 py-1 transition hover:bg-card/60"
        title="返回站点首页"
      >
        <span class="grid size-7 shrink-0 place-items-center rounded-[8px] bg-gradient-to-br from-accent to-accent-2 text-[12.5px] font-extrabold text-white">N</span>
        <span class="truncate text-[15px] font-bold tracking-tight text-ink">NitroBot</span>
      </RouterLink>
      <button
        type="button"
        class="hidden size-8 shrink-0 place-items-center rounded-lg text-dim transition hover:bg-card/70 hover:text-ink lg:grid"
        title="收起侧边栏"
        aria-label="收起侧边栏"
        @click="emit('toggle')"
      >
        <SvgIcon
          name="sidebar"
          :size="16"
        />
      </button>
      <button
        type="button"
        class="grid size-8 shrink-0 place-items-center rounded-lg text-dim transition hover:bg-card/70 hover:text-ink lg:hidden"
        title="关闭"
        aria-label="关闭侧边栏"
        @click="emit('close')"
      >
        <SvgIcon
          name="close"
          :size="16"
        />
      </button>
    </div>

    <button
      type="button"
      class="mx-3 mb-2 flex h-9 shrink-0 items-center gap-2 rounded-[10px] border border-dashed border-line px-3 text-[13px] text-dim transition hover:border-accent/50 hover:bg-accent/10 hover:text-accent"
      @click="emit('new-chat')"
    >
      <SvgIcon
        name="plus"
        :size="16"
      />
      新建对话
    </button>

    <p class="shrink-0 px-4 pb-1 pt-2 text-[10.5px] font-semibold uppercase tracking-[0.06em] text-faint">
      最近对话
    </p>
    <div class="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
      <p
        v-if="conversations.length === 0"
        class="px-2 py-3 text-[12px] leading-relaxed text-faint"
      >
        还没有对话，先问点什么吧。
      </p>
      <div
        v-for="conversation in conversations"
        :key="conversation.id"
        class="group/item relative"
      >
        <button
          type="button"
          class="w-full truncate rounded-[8px] py-2 pl-2.5 pr-7 text-left text-[13px] transition"
          :class="conversation.id === activeId
            ? 'bg-accent/10 font-medium text-accent'
            : 'text-dim hover:bg-card/70 hover:text-ink'"
          :title="conversation.title"
          @click="emit('select', conversation.id)"
        >
          {{ conversation.title }}
        </button>
        <button
          type="button"
          class="absolute right-1 top-1/2 hidden -translate-y-1/2 rounded p-1 text-faint transition hover:bg-card hover:text-danger group-hover/item:block"
          title="删除对话"
          aria-label="删除对话"
          @click.stop="emit('remove', conversation.id)"
        >
          <SvgIcon
            name="close"
            :size="14"
          />
        </button>
      </div>
    </div>

    <div class="shrink-0 border-t border-line p-2">
      <div class="flex items-center gap-1.5">
        <ThemeToggle />
        <RouterLink
          to="/"
          class="flex h-9 flex-1 items-center justify-center gap-1.5 rounded-[9px] border border-line bg-soft text-[12px] text-dim transition hover:border-line-strong hover:text-ink"
        >
          <SvgIcon
            name="arrow-left"
            :size="14"
          />
          返回站点
        </RouterLink>
      </div>
      <div
        v-if="authenticated"
        class="mt-1.5 flex items-center gap-2 px-1 pb-0.5"
      >
        <span class="grid size-7 shrink-0 place-items-center rounded-full bg-gradient-to-br from-warn to-danger text-[11px] font-bold text-white">{{ userInitial }}</span>
        <span class="min-w-0 flex-1 truncate text-[12px] text-dim">{{ userLabel }}</span>
        <button
          type="button"
          class="rounded p-1 text-faint transition hover:text-ink"
          title="退出登录"
          aria-label="退出登录"
          @click="emit('logout')"
        >
          <SvgIcon
            name="logout"
            :size="16"
          />
        </button>
      </div>
      <p
        v-else
        class="px-1 pb-0.5 pt-1.5 text-[12px] text-faint"
      >
        未登录 · 登录后才能对话
      </p>
    </div>
  </aside>
</template>
