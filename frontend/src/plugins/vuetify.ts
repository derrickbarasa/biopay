import 'vuetify/styles'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import { aliases, mdi } from 'vuetify/iconsets/mdi'
import '@mdi/font/css/materialdesignicons.css'

const bioPayTheme = {
  dark: false,
  colors: {
    primary: '#059669',
    'primary-darken-1': '#047857',
    secondary: '#F59E0B',
    'secondary-darken-1': '#EA580C',
    accent: '#CCFBF1',
    error: '#D64545',
    info: '#2196F3',
    success: '#10B981',
    warning: '#F59E0B',
    background: '#F8FAFC',
    surface: '#FFFFFF',
  },
}

export default createVuetify({
  components,
  directives,
  icons: {
    defaultSet: 'mdi',
    aliases,
    sets: { mdi },
  },
  theme: {
    defaultTheme: 'bioPayTheme',
    themes: { bioPayTheme },
  },
  defaults: {
    VCard: { rounded: 'xl' },
    VBtn: { rounded: 'lg', elevation: 0 },
    VTextField: { variant: 'outlined', density: 'comfortable' },
    VSelect: { variant: 'outlined', density: 'comfortable' },
  },
})
