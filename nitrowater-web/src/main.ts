import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'
import { initTheme } from './shared/theme/useTheme'

initTheme()

const app = createApp(App)

/**
 * Element Plus renders every table cell through `TableTdWrapper`, which calls the column's
 * `#default` slot from inside the table-body render pipeline. Vue 3.5 reports that as
 * "Slot invoked outside of the render function" even though the cell is being rendered; it is an
 * upstream false positive and otherwise floods the console on every table refresh. Drop exactly
 * that warning (and only when the trace points at the table wrapper) so real slot warnings from
 * our own components still surface.
 */
app.config.warnHandler = (message, _instance, trace) => {
  if (
    message.includes('Slot "default" invoked outside of the render function')
    && (trace.includes('TableTdWrapper') || trace.includes('ElTable'))
  ) {
    return
  }
  console.warn(`[Vue warn]: ${message}\n${trace}`)
}

app.use(router).mount('#app')
