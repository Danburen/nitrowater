<script setup lang="ts">
/**
 * One editable legal link row (title + absolute URL + remove button).
 *
 * Split from the list so the list can put `v-for` on a component: template type-checking keeps the
 * loop alias only on the element that carries `v-for`, so a row component keeps the bindings valid.
 */
const title = defineModel<string>('title', { required: true })
const url = defineModel<string>('url', { required: true })

defineEmits<{ remove: [] }>()
</script>

<template>
  <div class="flex items-center gap-2">
    <el-input
      v-model="title"
      class="w-[150px]!"
      placeholder="标题，如 用户协议"
    />
    <el-input
      v-model="url"
      class="flex-1"
      placeholder="https://www.example.com/legal/terms"
    />
    <el-button
      link
      type="danger"
      class="shrink-0"
      @click="$emit('remove')"
    >
      删除
    </el-button>
  </div>
</template>
