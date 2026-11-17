<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ThemeToggle } from '@nitrowater/ui'

import { LEGAL_TITLES, fetchLegalText, isLegalDocument, type LegalDocument } from '@/api/legal'

/**
 * Privacy policy / terms rendered from the plain-text file served by this origin.
 *
 * The text is shown verbatim in a `pre-wrap` block: the documents are maintained as plain text
 * (so they can be corrected on the host without a build), and any markdown-ish emphasis would
 * only render as literal asterisks here.
 */
const route = useRoute()

const text = ref('')
const loading = ref(false)
const error = ref('')

const document = computed<LegalDocument | null>(() => {
  const raw = route.params.doc
  const value = Array.isArray(raw) ? raw[0] : raw
  return isLegalDocument(value) ? value : null
})

const title = computed(() => (document.value ? LEGAL_TITLES[document.value] : '法律声明'))

async function load(): Promise<void> {
  if (!document.value) {
    error.value = '文档不存在。'
    text.value = ''
    return
  }
  loading.value = true
  error.value = ''
  try {
    text.value = await fetchLegalText(document.value)
  } catch (cause) {
    error.value =
      cause instanceof Error && cause.message === 'not-published'
        ? '该文档尚未发布，请稍后再试。'
        : '文档加载失败，请稍后重试。'
    text.value = ''
  } finally {
    loading.value = false
  }
}

watch(document, load, { immediate: true })
</script>

<template>
  <div class="min-h-dvh bg-bg text-ink">
    <div class="mx-auto w-full max-w-3xl px-5 py-8 sm:px-6 sm:py-10">
      <div class="flex items-center justify-between gap-3">
        <RouterLink
          to="/login"
          class="auth-link text-[13px]"
        >
          ← 返回登录
        </RouterLink>
        <ThemeToggle variant="subtle" />
      </div>

      <h1 class="mt-6 text-xl font-semibold text-ink">
        {{ title }}
      </h1>
      <p class="mt-1 text-[12px] text-faint">
        NitroWater 统一身份中心 · 生效版本以本页内容为准
      </p>

      <div
        v-if="loading"
        class="mt-6 text-[13px] text-dim"
        role="status"
      >
        正在加载…
      </div>

      <div
        v-else-if="error"
        class="auth-alert-error mt-6"
        role="alert"
      >
        {{ error }}
      </div>

      <article
        v-else
        class="mt-6 whitespace-pre-wrap break-words text-[13.5px] leading-7 text-dim"
      >
        {{ text }}
      </article>
    </div>
  </div>
</template>
