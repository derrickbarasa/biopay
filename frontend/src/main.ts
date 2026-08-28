import 'vuetify/styles'
import './style.css'
import './styles/ui-fixes.css'
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import vuetify from './plugins/vuetify'
import router from './router'
import App from './App.vue'
import DialogCloseButton from './components/DialogCloseButton.vue'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(vuetify)
app.component('DialogCloseButton', DialogCloseButton)

app.mount('#app')
