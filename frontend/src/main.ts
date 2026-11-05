import { createApp } from 'vue'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import { definePreset } from '@primeuix/themes'
import Aura from '@primeuix/themes/aura'
import ToastService from 'primevue/toastservice'
import ConfirmationService from 'primevue/confirmationservice'
import 'primeicons/primeicons.css'
import '@/assets/main.css'

import App from './App.vue'
import router from './router'
import { i18n } from './i18n'

const AtriaBluePreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '{blue.50}',
      100: '{blue.100}',
      200: '{blue.200}',
      300: '{blue.300}',
      400: '{blue.400}',
      500: '{blue.600}',
      600: '{blue.700}',
      700: '{blue.800}',
      800: '{blue.900}',
      900: '{blue.950}',
      950: '{blue.950}'
    }
  }
})

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(i18n)
app.use(PrimeVue, {
  theme: {
    preset: AtriaBluePreset,
    options: {
      darkModeSelector: '.atria-dark'
    }
  }
})
app.use(ToastService)
app.use(ConfirmationService)

app.mount('#app')
