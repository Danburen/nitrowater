import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

// The SPA no longer handles OIDC callbacks: login/logout are full-page redirects through the
// BFF, so the only routes are the app pages themselves.
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

export default router
