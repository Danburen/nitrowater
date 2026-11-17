<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { LEGAL_TITLES, fetchLegalText, isLegalDocument, type LegalDocument } from '../api/legal'

/**
 * Privacy policy / terms rendered from the plain-text file served by the BFF.
 *
 * Plain text is intentional: the documents are host state edited on the deployment host, so
 * rendering Markdown would either need a build step or show raw syntax. `whitespace-pre-wrap`
 * keeps the paragraphs and line breaks exactly as written.
 */
const route = useRoute()
const router = useRouter()

const text = ref('')
const loading = ref(false)
const error = ref('')

const document = computed<LegalDocument | null>(() => {
  const raw = route.params.doc
  const value = Array.isArray(raw) ? raw[0] : raw
  return isLegalDocument(value) ? value : null
})

const title = computed(() => (document.value ? LEGAL_TITLES[document.value] : '法律声明'))

/** Return to where we came from; fall back to the portal when there is no history. */
function goBack(): void {
  const state = window.history.state as { back?: string } | null
  if (state?.back) {
    router.back()
  } else {
    void router.push('/')
  }
}

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
  <div class="min-h-screen">
    <main class="mx-auto max-w-[1120px] px-4 sm:px-6">
      <div class="pt-8">
        <button
          type="button"
          class="btn"
          @click="goBack"
        >
          ← 返回
        </button>
      </div>

      <article class="max-w-[860px] py-10">
        <h1 class="text-[28px] font-bold tracking-tight">
          {{ title }}
        </h1>
        <p class="mt-2 text-[12.5px] text-faint">
          NitroWater · 生效版本以本页内容为准
        </p>

        <div
          v-if="loading"
          class="mt-8 text-sm text-dim"
          role="status"
        >
          正在加载…
        </div>

        <div
          v-else-if="error"
          class="mt-8 rounded-lg border border-danger/25 bg-danger/10 px-3 py-2.5 text-[13px] text-danger"
          role="alert"
        >
          {{ error }}
        </div>

        <div
          v-else
          class="mt-8 whitespace-pre-wrap break-words text-[14px] leading-7 text-dim"
        >
          {{ text }}
        </div>
      </article>
    </main>
  </div>
</template>
