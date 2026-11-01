<script setup lang="ts">
/**
 * Admin sidebar menu (shared by the desktop aside and the mobile drawer).
 *
 * <p>Data comes from {@link ADMIN_MENU}; the active entry follows the current route, with
 * {@code meta.activeMenu} as an escape hatch for routes that render a different page.</p>
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ADMIN_MENU, ADMIN_OPENED_MENUS } from './menu'

withDefaults(defineProps<{ collapsed?: boolean }>(), { collapsed: false })

const route = useRoute()

const activeMenu = computed(() => {
  const meta = route.meta.activeMenu
  return typeof meta === 'string' ? meta : route.path
})
</script>

<template>
  <el-menu
    class="admin-menu"
    :default-active="activeMenu"
    :collapse="collapsed"
    :collapse-transition="false"
    :default-openeds="[...ADMIN_OPENED_MENUS]"
    router
  >
    <template
      v-for="item in ADMIN_MENU"
      :key="item.path"
    >
      <el-sub-menu
        v-if="item.children"
        :index="item.path"
      >
        <template #title>
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </template>
        <el-menu-item
          v-for="child in item.children"
          :key="child.path"
          :index="child.path"
        >
          <el-icon><component :is="child.icon" /></el-icon>
          <template #title>
            {{ child.label }}
          </template>
        </el-menu-item>
      </el-sub-menu>
      <el-menu-item
        v-else
        :index="item.path"
      >
        <el-icon><component :is="item.icon" /></el-icon>
        <template #title>
          {{ item.label }}
        </template>
      </el-menu-item>
    </template>
  </el-menu>
</template>

<style scoped>
.admin-menu {
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--color-dim);
  --el-menu-active-color: var(--color-accent);
  --el-menu-hover-bg-color: color-mix(in srgb, var(--color-soft) 80%, transparent);
  --el-menu-item-height: 40px;
  --el-menu-sub-item-height: 36px;
  border-right: none;
}

.admin-menu :deep(.el-menu-item.is-active) {
  background: color-mix(in srgb, var(--color-accent) 10%, transparent);
  border-radius: 9px;
  font-weight: 600;
}

.admin-menu :deep(.el-menu-item),
.admin-menu :deep(.el-sub-menu__title) {
  border-radius: 9px;
  margin: 2px 6px;
}
</style>
