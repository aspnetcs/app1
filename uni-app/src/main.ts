import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { useAuthStore } from './stores/auth'
import { useGroupStore } from './stores/group'
import { config } from './config'
import { setAuthStoreGetter } from './api/http'

export function createApp() {
  const app = createSSRApp(App)
  const pinia = createPinia()
  app.use(pinia)

  setAuthStoreGetter(() => useAuthStore())

  const authStore = useAuthStore()
  authStore.loadFromStorage()

  if (config.features.userGroups) {
    const groupStore = useGroupStore()
    groupStore.loadFromStorage()
    if (authStore.isLoggedIn) {
      groupStore.fetchProfile({ force: true }).catch(() => undefined)
    } else {
      groupStore.clearProfile()
    }
  }

  return { app }
}
