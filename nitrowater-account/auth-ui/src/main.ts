import { createApp } from 'vue'

import { configureTheme, initTheme } from '@nitrowater/ui'

import App from './App.vue'
import router from './router'
import './style.css'

configureTheme({ storageKey: 'nitrowater-auth-theme' })
initTheme()

createApp(App).use(router).mount('#app')
