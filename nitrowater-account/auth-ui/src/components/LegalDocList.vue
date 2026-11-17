<script setup lang="ts">
/**
 * All legal documents of the application that started this login, rendered inline as
 * `《用户协议》和《隐私政策》`.
 *
 * The rows come from the SSO's `client_legal_link` table; the documents themselves are hosted by
 * the application, so each title is a plain `<a>` to its own URL. The list is data-driven: an
 * application may register two documents or ten, and the page does not hard-code any title.
 */
import type { LegalLink } from '@/api/auth'

defineProps<{ links: LegalLink[] }>()
</script>

<template>
  <span
    v-for="(link, index) in links"
    :key="`${index}-${link.url}`"
  >
    <span v-if="index > 0">{{ index === links.length - 1 ? '和' : '、' }}</span>
    <a
      class="auth-link"
      :href="link.url"
      target="_blank"
      rel="noopener noreferrer"
    >《{{ link.title }}》</a>
  </span>
</template>
