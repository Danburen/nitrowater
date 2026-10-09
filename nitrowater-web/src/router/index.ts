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
