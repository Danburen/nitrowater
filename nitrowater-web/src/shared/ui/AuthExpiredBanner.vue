<script setup lang="ts">
/**
 * Global session notice.
 *
 * <p>Shown when a protected request reported the session gone, or when the auth service itself is
 * unreachable. It never redirects on its own: a mid-answer redirect would discard the transcript,
 * and an outage would cause a login redirect loop. The user decides when to re-authenticate.</p>
 */
import { useRoute } from 'vue-router'
import { useAuth } from '../../auth/useAuth'

const { state, expired, refresh, login } = useAuth()
const route = useRoute()

/** Re-authenticate and come back to the current page. */
function reLogin(): void {
  login(route.fullPath)
}

/** Re-probe the auth service after an outage. */
function retry(): void {
  void refresh()
}
</script>

<template>
  <div
    v-if="expired || state === 'unavailable'"
    class="auth-banner"
    role="alert"
  >
    <span
      class="auth-dot"
      :class="expired ? 'bg-warn' : 'bg-danger'"
    />
    <span class="auth-text">
      {{ expired ? '登录已失效，请重新登录' : '认证服务暂时不可用，请稍后重试' }}
    </span>
    <button
      type="button"
      class="auth-action"
      @click="expired ? reLogin() : retry()"
    >
      {{ expired ? '重新登录' : '重试' }}
    </button>
  </div>
</template>

<style scoped>
.auth-banner {
  position: fixed;
  top: 10px;
  left: 50%;
  z-index: 60;
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: min(92vw, 520px);
  padding: 7px 10px 7px 12px;
  transform: translateX(-50%);
  border: 1px solid var(--color-line-strong);
  border-radius: 999px;
  background: var(--color-card);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.14);
  font-size: 12.5px;
  color: var(--color-ink);
}

.auth-dot {
  width: 7px;
  height: 7px;
  flex-shrink: 0;
  border-radius: 999px;
}

.auth-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.auth-action {
  flex-shrink: 0;
  padding: 2px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-accent) 14%, transparent);
  color: var(--color-accent);
  font-weight: 600;
  font-size: 12px;
  transition: background 0.12s;
}

.auth-action:hover {
  background: color-mix(in srgb, var(--color-accent) 24%, transparent);
}
</style>
