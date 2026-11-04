import {
  DataLine,
  Files,
  FolderOpened,
  Lock,
  MagicStick,
  Refresh,
  Setting,
  Cpu,
  Tools,
} from '@element-plus/icons-vue'
import type { Component } from 'vue'

/** One entry of the admin console sidebar (children render as a sub-menu). */
export interface AdminMenuItem {
  path: string
  label: string
  icon: Component
  children?: AdminMenuItem[]
}

/**
 * Admin navigation, shared by the sidebar and the router (single source of truth).
 *
 * <p>AI settings are grouped under one sub-menu so configuration, per-user usage and the tool
 * catalog are addressable URLs instead of sections stacked on one page.</p>
 */
export const ADMIN_MENU: readonly AdminMenuItem[] = [
  { path: '/admin/plugins', label: '插件管理', icon: Files },
  { path: '/admin/resources', label: '资源管理', icon: FolderOpened },
  {
    path: '/admin/ai',
    label: 'AI 助手',
    icon: MagicStick,
    children: [
      { path: '/admin/ai/settings', label: '运行设置', icon: Setting },
      { path: '/admin/ai/moderation', label: '内容安全', icon: Lock },
      { path: '/admin/ai/usage', label: '用户用量', icon: DataLine },
      { path: '/admin/ai/index', label: '索引状态', icon: Refresh },
      { path: '/admin/ai/skills', label: '技能', icon: Cpu },
      { path: '/admin/ai/tools', label: '工具', icon: Tools },
    ],
  },
]

/** Sub-menu indexes that should start expanded. */
export const ADMIN_OPENED_MENUS: readonly string[] = ['/admin/ai']
