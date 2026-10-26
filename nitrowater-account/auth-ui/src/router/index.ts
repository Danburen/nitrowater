import { createRouter, createWebHistory } from 'vue-router'

import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
  }
}

// Both /login and /register are forwarded by Spring to /auth/index.html. History mode
// lets the SPA keep the real URLs (and therefore the `error` / `redirect` query
// parameters) while the server only needs two explicit mappings.
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { title: '登录 · NitroWater 统一身份中心' },
    },
    {
      path: '/register',
      name: 'register',
      component: RegisterView,
      meta: { title: '注册 · NitroWater 统一身份中心' },
    },
    { path: '/:pathMatch(.*)*', redirect: '/login' },
  ],
})

router.afterEach((to) => {
  document.title = to.meta.title ?? 'NitroWater 统一身份中心'
})

export default router
