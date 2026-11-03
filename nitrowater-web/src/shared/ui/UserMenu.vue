<script setup lang="ts">
/**
 * Account menu: the signed-in identity plus the account pool, the user centre and the exits.
 *
 * <p>Native {@code <details>} rather than {@code el-dropdown}: the popover is pure Tailwind, so it
 * inherits the {@code --color-*} tokens in both themes without any Element Plus bridging. Shared by
 * the portal header and the admin console top bar, which keeps one account vocabulary across both
 * shells.</p>
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../../auth/useAuth'

const router = useRouter()
const { session, accounts, addAccount, switchAccount, removeAccount, logout } = useAuth()

const root = ref<HTMLDetailsElement | null>(null)
const displayName = computed(() => session.value?.name || session.value?.username || '已登录')
const initial = computed(() => displayName.value.slice(0, 1).toUpperCase())
/** `null` = idle; a string is the inline error shown inside the popover. */
const error = ref<string | null>(null)
const busy = ref(false)

const triggerClass
  = 'flex cursor-pointer list-none items-center gap-2 rounded-[9px] border border-line bg-card px-3 py-1.5 text-[13px] text-ink transition hover:border-line-strong [&::-webkit-details-marker]:hidden'

/** `<details>` closes on its own trigger only: Esc and an outside click have to be wired up. */
function close(): void {
  if (root.value) root.value.open = false
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Escape' || !root.value?.open) return
  event.preventDefault()
  close()
  root.value.querySelector('summary')?.focus()
}

function onDocumentPointerDown(event: PointerEvent): void {
  if (!root.value?.open) return
  if (event.target instanceof Node && !root.value.contains(event.target)) close()
}

onMounted(() => document.addEventListener('pointerdown', onDocumentPointerDown))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onDocumentPointerDown))

/** Switches to another account held by the BFF session (the helper reloads the SPA). */
async function onSwitchAccount(uid: string): Promise<void> {
  if (busy.value || !uid || session.value?.activeUid === uid) return
  busy.value = true
  error.value = null
  try {
    await switchAccount(uid)
  } catch {
    error.value = '切换账号失败，请重试'
    busy.value = false
  }
}

/** Removes one account; removing the last one triggers a full SSO logout. */
async function onRemoveAccount(uid: string, active: boolean): Promise<void> {
  if (busy.value || !uid) return
  if (!window.confirm(active ? '退出当前账号？' : '移除该账号？')) return
  busy.value = true
  error.value = null
  try {
    await removeAccount(uid)
  } catch {
    error.value = '操作失败，请重试'
    busy.value = false
  }
}
</script>

<template>
  <details
    ref="root"
    class="relative"
    @keydown="onKeydown"
  >
    <summary :class="triggerClass">
      <img
        v-if="session?.picture"
        :src="session.picture"
        alt=""
        class="size-6 shrink-0 rounded-full object-cover"
      >
      <span
        v-else
        class="grid size-6 shrink-0 place-items-center rounded-full bg-line text-[11px] text-dim"
      >
        {{ initial }}
      </span>
      <span class="max-w-[8rem] truncate">{{ displayName }}</span>
      <span class="text-[10px] text-dim">▼</span>
    </summary>

    <div class="absolute right-0 top-full z-30 mt-2 w-64 overflow-hidden rounded-xl border border-line bg-card shadow-xl">
      <p class="border-b border-line px-3 py-2 text-[11px] uppercase tracking-wide text-faint">
        账号
      </p>
      <button
        v-for="account in accounts"
        :key="account.uid"
        type="button"
        class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-ink transition hover:bg-soft disabled:cursor-default disabled:opacity-70"
        :disabled="busy || account.active"
        @click="onSwitchAccount(account.uid)"
      >
        <img
          v-if="account.picture"
          :src="account.picture"
          alt=""
          class="size-6 rounded-full object-cover"
        >
        <span
          v-else
          class="grid size-6 shrink-0 place-items-center rounded-full bg-soft text-[11px] text-dim"
        >
          {{ (account.name || account.username || '?').slice(0, 1) }}
        </span>
        <span class="min-w-0 flex-1 truncate">{{ account.name || account.username || account.uid }}</span>
        <span
          v-if="account.active"
          class="text-[11px] text-ok"
        >
          当前
        </span>
      </button>
      <div
        class="border-t border-line"
        @click="close()"
      >
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-ink transition hover:bg-soft"
          @click="router.push('/account')"
        >
          <span class="text-dim">◎</span> 用户中心
        </button>
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-ink transition hover:bg-soft"
          @click="addAccount"
        >
          <span class="text-dim">+</span> 添加账号
        </button>
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-ink transition hover:bg-soft disabled:opacity-60"
          :disabled="busy"
          @click="onRemoveAccount(session?.activeUid ?? '', true)"
        >
          退出当前账号
        </button>
        <button
          type="button"
          class="flex w-full items-center gap-2 px-3 py-2 text-left text-[13px] text-danger transition hover:bg-soft"
          @click="logout"
        >
          退出全部
        </button>
      </div>
      <p
        v-if="error"
        class="px-3 py-2 text-xs text-danger"
      >
        {{ error }}
      </p>
    </div>
  </details>
</template>
