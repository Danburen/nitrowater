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
    // Plugin center: nested layout with its own sidebar (plugin list + search box); the selected
    // plugin's single-page 说明 renders in the content area (global header hidden there).
    {
      path: '/plugins',
      component: () => import('../features/plugins/PluginsLayout.vue'),
      meta: { layout: 'plugins' },
      children: [
        { path: '', name: 'plugins', component: () => import('../features/plugins/PluginsOverview.vue') },
        { path: ':key', name: 'plugin-detail', component: () => import('../features/plugins/PluginDetailView.vue') },
      ],
    },
    { path: '/about', name: 'about', component: () => import('../views/AboutView.vue') },
    // The coding playground is a standalone top-level page (global header, no tools sidebar):
    // browse problems and submit for judging, gated by login on the backend.
    {
      path: '/playground',
      component: () => import('../features/playground/PlaygroundLayout.vue'),
      meta: { layout: 'playground' },
      children: [
        { path: '', name: 'playground', component: () => import('../features/playground/PlaygroundProblems.vue') },
        // Literal paths must precede `:id` so they are not captured as a problem id.
        { path: 'sets', name: 'playground-sets', component: () => import('../features/playground/PlaygroundSets.vue') },
        { path: 'sets/:id', name: 'playground-set-detail', component: () => import('../features/playground/PlaygroundSetDetail.vue') },
        { path: 'square', name: 'playground-square', component: () => import('../features/playground/PlaygroundSquare.vue') },
        { path: 'submissions', name: 'playground-submissions', component: () => import('../features/playground/PlaygroundSubmissions.vue') },
        { path: ':id', name: 'playground-problem', component: () => import('../features/playground/PlaygroundProblem.vue') },
      ],
    },
    // Admin console: nested layout with its own sidebar (global header hidden). Access is
    // gated by role inside AdminLayout; the resource server enforces ROLE_ADMIN anyway.
    {
      path: '/admin',
      component: () => import('../features/admin/AdminLayout.vue'),
      meta: { layout: 'admin' },
      children: [
        { path: '', redirect: '/admin/problems' },
        { path: 'problems', name: 'admin-problems', component: () => import('../features/admin/AdminProblems.vue') },
        { path: 'problems/:id', name: 'admin-problem-edit', component: () => import('../features/admin/AdminProblemEdit.vue') },
        { path: 'tags', name: 'admin-tags', component: () => import('../features/admin/AdminTags.vue') },
        { path: 'sets', name: 'admin-sets', component: () => import('../features/admin/AdminSets.vue') },
        { path: 'plugins', name: 'admin-plugins', component: () => import('../features/admin/AdminPlugins.vue') },
        { path: 'editorials', name: 'admin-editorials', component: () => import('../features/admin/AdminEditorials.vue') },
        { path: 'resources', name: 'admin-resources', component: () => import('../features/admin/AdminResources.vue') },
      ],
    },
    {
      path: '/tools',
      component: () => import('../features/tools/ToolsLayout.vue'),
      meta: { layout: 'tools' },
      children: [
        { path: '', name: 'tools', component: () => import('../features/tools/ToolsView.vue') },
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
