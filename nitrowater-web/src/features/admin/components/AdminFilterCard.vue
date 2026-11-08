<script setup lang="ts">
/**
 * Standalone query card shown above a list card.
 *
 * <p>Owns the shared filter shell so every admin list reads the same: one inline row, label on the
 * left, control on the right and the reset/query pair pinned to the right edge. Pages only declare
 * their fields in the default slot; the actions are always the same two buttons.</p>
 *
 * <p>Kept out of the list card on purpose — filtering is a page-level concern, so it gets its own
 * card instead of a bar glued to the table header.</p>
 */
withDefaults(defineProps<{
  /** Disables both actions while a query is in flight. */
  loading?: boolean
}>(), {
  loading: false,
})

const emit = defineEmits<{
  submit: []
  reset: []
}>()
</script>

<template>
  <el-card
    shadow="never"
    class="admin-filter-card"
  >
    <el-form
      :inline="true"
      class="admin-filter-form"
      @submit.prevent="emit('submit')"
    >
      <slot />
      <div class="admin-filter-actions">
        <el-button
          :disabled="loading"
          @click="emit('reset')"
        >
          重置
        </el-button>
        <el-button
          type="primary"
          native-type="submit"
          :loading="loading"
        >
          查询
        </el-button>
      </div>
    </el-form>
  </el-card>
</template>

<style scoped>
/*
 * One query card per list page: the card is the shell and the form is its only child. Kept in this
 * SFC because nothing else reuses the shell; scoped selectors also outrank Element Plus' unlayered
 * card defaults without needing `!important`.
 */
.admin-filter-card {
  overflow: hidden;
  border: 1px solid var(--color-line);
  border-radius: 14px;
}

.admin-filter-card :deep(.el-card__body) {
  padding: 0;
}

.admin-filter-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 12px;
  padding: 8px 12px;
}

.admin-filter-form :deep(.el-form-item) {
  margin: 0 !important;
}

.admin-filter-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

/* Narrow screens: one field per row, controls take the full width. `!important` beats the fixed
   control widths the pages set on their fields. */
@media (max-width: 991px) {
  .admin-filter-form :deep(.el-form-item),
  .admin-filter-actions {
    width: 100%;
  }

  .admin-filter-actions {
    margin-left: 0;
    justify-content: flex-end;
  }

  .admin-filter-form :deep(.el-form-item .el-input),
  .admin-filter-form :deep(.el-form-item .el-select) {
    width: 100% !important;
  }
}
</style>
