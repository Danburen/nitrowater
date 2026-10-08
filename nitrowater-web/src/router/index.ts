import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    {
      path: '/auth/callback',
      name: 'auth-callback',
      component: () => import('../views/OidcCallbackView.vue'),
    },
    {
      path: '/auth/silent-callback',
      name: 'silent-callback',
      component: () => import('../views/SilentCallbackView.vue'),
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

export default router
