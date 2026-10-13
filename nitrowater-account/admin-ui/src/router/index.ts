import { createRouter, createWebHashHistory } from 'vue-router'

import { ApiError } from '@/api/http'
import { useSession } from '@/auth/session'
import AdminLayout from '@/layouts/AdminLayout.vue'

declare module 'vue-router' {
  interface RouteMeta {
    /** Document/tab title (reserved for a future title hook). */
    title?: string
    /**
     * Opt out of the auth guard. This is the "whitelist": it lives on the route record itself,
     * so it can never drift from the routes. No route is public today — the login page is
     * served by the account service, not the SPA.
     */
    public?: boolean
  }
}

// Hash history avoids any server-side SPA fallback: the whole app lives under /admin/#/...
// and the Spring static mapping only ever serves /admin/index.html + /admin/assets/*.
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      component: AdminLayout,
      children: [
        { path: '', redirect: '/users' },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/UsersView.vue'),
          meta: { title: '用户管理' },
        },
        {
          path: 'roles',
          name: 'roles',
          component: () => import('@/views/RolesView.vue'),
          meta: { title: '角色管理' },
        },
        {
          path: 'clients',
          name: 'clients',
          component: () => import('@/views/ClientsView.vue'),
          meta: { title: '应用管理' },
        },
        {
          // Unknown console route: render the in-app 404 inside the shell (the session is
          // already resolved by the guard) instead of silently redirecting to a real page.
          path: ':pathMatch(.*)*',
          name: 'notFound',
          component: () => import('@/views/NotFoundView.vue'),
          meta: { title: '页面不存在' },
        },
      ],
    },
    {
      path: '/forbidden',
      name: 'forbidden',
      component: () => import('@/views/ForbiddenView.vue'),
      meta: { title: '无访问权限' },
    },
  ],
})

/**
 * Auth guard. Every route is private by default; a route opts out with `meta.public = true`.
 *
 * A 401 has already been turned into a full redirect to /login by the http layer, so here we
 * just abort the in-app navigation. A 403 means the session is valid but lacks ROLE_SUPER_ADMIN,
 * which is a distinct outcome: route to the forbidden page rather than the login page.
 */
router.beforeEach(async (to) => {
  if (to.meta.public) return true

  const { status, load } = useSession()
  // Never loop: when we are already heading to `forbidden`, let it through.
  const toForbidden = () => (to.name === 'forbidden' ? true : { name: 'forbidden' })

  if (status.value === 'forbidden') return toForbidden()
  if (status.value === 'authenticated') return true

  try {
    await load()
    return true
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) return toForbidden()
    // 401 (or a transient failure): the http layer is redirecting to /login; stop here.
    return false
  }
})

export default router
