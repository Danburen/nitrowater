import { createRouter, createWebHashHistory } from 'vue-router'

import AdminLayout from '@/layouts/AdminLayout.vue'

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
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/users' },
  ],
})

export default router
