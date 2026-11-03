import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

// The SPA no longer handles OIDC callbacks: login/logout are full-page redirects through the
// BFF, so the only routes are the app pages themselves.
//
// The `/tools` area is a nested layout (ToolsLayout) that renders its own wtools-style sidebar
// shell; `meta.layout = 'tools'` tells App.vue to hide the global header there.
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    // NitroBot is a product-level assistant, not a per-plugin tab: it has its own header entry and
    // searches every indexed corpus (VeloChatX + NitroWater). It renders a full-bleed chat shell
    // (own sidebar + top bar), so the site header is hidden there.
    { path: '/chat', name: 'chat', component: () => import('../features/ai/NitroBotView.vue'), meta: { layout: 'chat' } },
    // Plugin center: nested layout with its own sidebar (plugin list + search box); the selected
    // plugin's single-page 说明 renders in the content area (global header hidden there).
    {
      path: '/plugins',
      component: () => import('../features/plugins/PluginsLayout.vue'),
      meta: { layout: 'plugins' },
      children: [
        { path: '', name: 'plugins', component: () => import('../features/plugins/PluginsOverview.vue') },
        // Legacy per-plugin chat URL: keep old links working after NitroBot moved to /chat.
        { path: ':key/chat', redirect: '/chat' },
        { path: ':key', name: 'plugin-detail', component: () => import('../features/plugins/PluginDetailView.vue') },
      ],
    },
    { path: '/about', name: 'about', component: () => import('../views/AboutView.vue') },
    { path: '/account', name: 'account', component: () => import('../features/account/AccountView.vue') },
    // Admin console: nested layout with its own sidebar (global header hidden). Access is
    // gated by role inside AdminLayout; the resource server enforces ROLE_ADMIN anyway.
    {
      path: '/admin',
      component: () => import('../features/admin/AdminLayout.vue'),
      meta: { layout: 'admin' },
      children: [
      { path: '', redirect: '/admin/plugins' },
      { path: 'plugins', name: 'admin-plugins', component: () => import('../features/admin/AdminPlugins.vue') },
      { path: 'resources', name: 'admin-resources', component: () => import('../features/admin/AdminResources.vue') },
      { path: 'ai', redirect: '/admin/ai/settings' },
      { path: 'ai/settings', name: 'admin-ai-settings', component: () => import('../features/admin/ai/AdminAiSettingsPage.vue') },
      { path: 'ai/usage', name: 'admin-ai-usage', component: () => import('../features/admin/ai/AdminAiUsagePage.vue') },
      {
      path: 'ai/moderation',
      name: 'admin-ai-moderation',
      component: () => import('../features/admin/ai/AdminAiModerationPage.vue'),
    },
    {
      path: 'ai/index',
      name: 'admin-ai-index',
      component: () => import('../features/admin/components/AdminAiCatalogPage.vue'),
      props: { section: 'index' },
    },
    {
      path: 'ai/catalog',
      name: 'admin-ai-catalog',
      component: () => import('../features/admin/components/AdminAiCatalogPage.vue'),
      props: { section: 'capabilities' },
    },
      ],
    },
    {
      path: '/tools',
      component: () => import('../features/tools/ToolsLayout.vue'),
      meta: { layout: 'tools' },
      children: [
        { path: '', name: 'tools', component: () => import('../features/tools/ToolsView.vue') },
        { path: 'motd', name: 'tool-motd', component: () => import('../features/tools/motd/MotdTool.vue') },
        { path: 'server-properties', name: 'tool-server-properties', component: () => import('../features/tools/server-properties/ServerPropertiesTool.vue') },
        { path: 'server-icon', name: 'tool-server-icon', component: () => import('../features/tools/server-icon/ServerIconTool.vue') },
        { path: 'start-script', name: 'tool-start-script', component: () => import('../features/tools/start-script/StartScriptTool.vue') },
        { path: 'java-to-ts', name: 'tool-java-to-ts', component: () => import('../features/tools/java-to-ts/JavaToTsTool.vue') },
        { path: 'zip', name: 'tool-zip', component: () => import('../features/tools/zip/ZipTool.vue') },
        { path: 'tree', name: 'tool-tree', component: () => import('../features/tools/tree/TreeTool.vue') },
        { path: 'list-tree', name: 'tool-list-tree', component: () => import('../features/tools/list-tree/ListTreeTool.vue') },
        { path: 'code-runner', name: 'tool-code-runner', component: () => import('../features/tools/code-runner/CodeRunnerTool.vue') },
        { path: 'video-to-gif', name: 'tool-video-to-gif', component: () => import('../features/tools/video-to-gif/VideoToGifTool.vue') },
        { path: 'markdown-to-word', name: 'tool-markdown-to-word', component: () => import('../features/tools/markdown-to-word/MarkdownToWordTool.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

// Recover from stale-chunk / dev dep-optimizer races: if a lazy route's module fails to fetch,
// reload once (guarded) so the browser loads the current module graph instead of dead-ending on
// VUE_ROUTER_R0010.
const CHUNK_ERROR =
  /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module/i
const RELOAD_FLAG = 'nitrowater:chunk-reload'

router.onError((error, to) => {
  const message = error instanceof Error ? error.message : String(error)
  if (!CHUNK_ERROR.test(message)) return
  const target = to.fullPath || window.location.pathname + window.location.search
  if (sessionStorage.getItem(RELOAD_FLAG) === target) return
  sessionStorage.setItem(RELOAD_FLAG, target)
  window.location.assign(target)
})

router.afterEach(() => {
  sessionStorage.removeItem(RELOAD_FLAG)
})

export default router
