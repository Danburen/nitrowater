import { createRouter, createWebHistory } from 'vue-router'

import ToolsView from '../features/tools/ToolsView.vue'

/**
 * Routes intentionally keep the historical `/tools/*` paths so bookmarks and cross-site links
 * keep working when the tools move to their own origin.
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/tools' },
    { path: '/tools', name: 'tools', component: ToolsView },
    { path: '/tools/motd', name: 'motd', component: () => import('../features/tools/motd/MotdTool.vue') },
    {
      path: '/tools/server-properties',
      name: 'server-properties',
      component: () => import('../features/tools/server-properties/ServerPropertiesTool.vue'),
    },
    {
      path: '/tools/server-icon',
      name: 'server-icon',
      component: () => import('../features/tools/server-icon/ServerIconTool.vue'),
    },
    {
      path: '/tools/start-script',
      name: 'start-script',
      component: () => import('../features/tools/start-script/StartScriptTool.vue'),
    },
    { path: '/tools/zip', name: 'zip', component: () => import('../features/tools/zip/ZipTool.vue') },
    { path: '/tools/tree', name: 'tree', component: () => import('../features/tools/tree/TreeTool.vue') },
    {
      path: '/tools/list-tree',
      name: 'list-tree',
      component: () => import('../features/tools/list-tree/ListTreeTool.vue'),
    },
    {
      path: '/tools/java-to-ts',
      name: 'java-to-ts',
      component: () => import('../features/tools/java-to-ts/JavaToTsTool.vue'),
    },
    {
      path: '/tools/code-runner',
      name: 'code-runner',
      component: () => import('../features/tools/code-runner/LocalCodeRunnerTool.vue'),
    },
    {
      path: '/tools/video-to-gif',
      name: 'video-to-gif',
      component: () => import('../features/tools/video-to-gif/VideoToGifTool.vue'),
    },
    {
      path: '/tools/markdown-to-word',
      name: 'markdown-to-word',
      component: () => import('../features/tools/markdown-to-word/MarkdownToWordTool.vue'),
    },
    { path: '/:pathMatch(.*)*', redirect: '/tools' },
  ],
})

router.afterEach((to) => {
  document.title = `${String(to.name ?? 'tools')} · NitroWater 工具箱`
})

export default router
