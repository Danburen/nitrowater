<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { fetchLegalIndex, fetchLegalText, formatLegalDate, isLegalKey } from '../api/legal'

/**
 * One legal document rendered from the plain-text file served by the BFF.
 *
 * Plain text is intentional: the documents are host state edited on the deployment host, so
 * rendering Markdown would need a build step or show raw syntax. `whitespace-pre-wrap` keeps the
 * paragraphs exactly as written.
 *
 * Heading and version date come from the index (title + file modification time), so the operator
 * never maintains a date line by hand.
 */
const route = useRoute()
const router = useRouter()

const text = ref('')
const loading = ref(false)
const error = ref('')
const title = ref('法律声明')
const updatedAt = ref('')

const document = computed<string | null>(() => {
  const raw = route.params.doc
  const value = Array.isArray(raw) ? raw[0] : raw
  return isLegalKey(value) ? value : null
})

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
  const current = document.value
  if (!current) {
    error.value = '文档不存在。'
    text.value = ''
    title.value = '法律声明'
    updatedAt.value = ''
    return
  }
  loading.value = true
  error.value = ''
  // Heading and date come from the index; if it fails the body is still served under the key.
  const documents = await fetchLegalIndex().catch(() => [])
  const metadata = documents.find((entry) => entry.key === current)
  title.value = metadata?.title ?? current
  updatedAt.value = metadata ? formatLegalDate(metadata.updatedAt) : ''
  try {
    text.value = await fetchLegalText(current)
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
          <template v-if="updatedAt">
            NitroWater · 最近更新与生效日期：{{ updatedAt }}
          </template>
          <template v-else>
            NitroWater · 生效版本以本页内容为准
          </template>
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
