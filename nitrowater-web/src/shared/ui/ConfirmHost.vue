<script setup lang="ts">
/**
 * Single mount point for {@link confirm}: renders the head of the queue in the shared
 * `AppDialog`.
 *
 * <p>Mounted once by `App.vue`, so any page can call {@code confirm(...)} without owning a
 * dialog of its own. A confirm triggered from a page that already has a dialog open stacks on top
 * of it (z-index follows the open order in `overlay.ts`).</p>
 */
import { computed } from 'vue'
import AppDialog from './AppDialog.vue'
import { confirmQueue, settleConfirm } from './confirm'

const head = computed(() => confirmQueue[0])

/** AppDialog dismissals (backdrop, ESC, close button, cancel) answer "no". */
function onOpenChange(open: boolean): void {
  if (!open) settleConfirm(false)
}
</script>

<template>
  <AppDialog
    :model-value="!!head"
    :title="head?.options.title ?? '确认操作'"
    :confirm-text="head?.options.confirmText ?? '确定'"
    :cancel-text="head?.options.cancelText ?? '取消'"
    :danger="!!head?.options.danger"
    width="420px"
    @confirm="settleConfirm(true)"
    @update:model-value="onOpenChange"
  >
    <p class="whitespace-pre-line text-[13.5px] leading-6 text-dim">
      {{ head?.options.message }}
    </p>
  </AppDialog>
</template>
