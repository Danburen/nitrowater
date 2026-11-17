<script setup lang="ts">
/**
 * Editor for the legal links of one OIDC application.
 *
 * The list is data-driven (title + absolute URL, any number of rows); the login page renders the
 * rows in this order. The document text itself stays on the application's host, so these are only
 * pointers the SSO stores in `client_legal_link`.
 */
import type { LegalLink } from '@/api/admin'
import LegalLinkRow from '@/components/LegalLinkRow.vue'

const links = defineModel<LegalLink[]>({ required: true })

function addLink() {
  links.value = [...links.value, { title: '', url: '' }]
}

function removeLink(index: number) {
  links.value = links.value.filter((_, i) => i !== index)
}
</script>

<template>
  <div class="w-full space-y-2">
    <LegalLinkRow
      v-for="(item, index) in links"
      :key="index"
      v-model:title="item.title"
      v-model:url="item.url"
      @remove="removeLink(index)"
    />
    <el-button
      link
      type="primary"
      @click="addLink"
    >
      添加链接
    </el-button>
    <div class="text-xs text-faint">
      登录页按顺序渲染这些链接（如《用户协议》《隐私政策》），正文由该应用自己托管；留空则不显示。
    </div>
  </div>
</template>
