import { createApp } from 'vue'

import { configureTheme, initTheme } from '@nitrowater/ui'

// Only the Element Plus components used by the hosted pages are imported (in the SFCs), so
// only their styles are pulled in here — the login page is public and must stay light.
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/card/style/css'

import App from './App.vue'
import router from './router'
import './style.css'

configureTheme({ storageKey: 'nitrowater-auth-theme' })
initTheme()

createApp(App).use(router).mount('#app')
