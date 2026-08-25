import 'vuetify/styles'
import { createVuetify } from 'vuetify'
import { aliases, mdi } from 'vuetify/iconsets/mdi'
import '@/styles/mdi-subset.css'

const bioPayTheme = {
  dark: false,
  colors: {
    // Primary moved from emerald green to a teal / blue-green identity
    // ("less green, adapt the theme color"). Green is retained only for
    // success states below.
    primary: '#0D9488',
    'primary-darken-1': '#0F766E',
    secondary: '#F59E0B',
    'secondary-darken-1': '#EA580C',
    accent: '#CCFBF1',
    error: '#D64545',
    info: '#15803D',
    success: '#10B981',
    warning: '#F59E0B',
    background: '#F8FAFC',
    surface: '#FFFFFF',
  },
}

export default createVuetify({
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
    VCard: { rounded: 'lg' },
    VBtn: { rounded: 'md', elevation: 0 },
    VTextField: { variant: 'outlined', density: 'comfortable' },
    VSelect: { variant: 'outlined', density: 'comfortable' },
  },
})
