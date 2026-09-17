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
    background: '#F1F5F9',
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
    // itemColor picks the color used for the active/selected item inside the
    // dropdown menu (and its hover tint) -- set to the orange "secondary" brand
    // color so selections read as orange rather than Vuetify's default grey.
    VSelect: { variant: 'outlined', density: 'comfortable', itemColor: 'secondary' },
    VAutocomplete: { variant: 'outlined', density: 'comfortable', itemColor: 'secondary' },
    VTextarea: { variant: 'outlined', density: 'comfortable' },
    VCombobox: { variant: 'outlined', density: 'comfortable', itemColor: 'secondary' },
    // VDateInput's own default puts the calendar icon in `prependIcon`, which VTextField
    // renders OUTSIDE the outlined box (a separate element to its left) -- move it to
    // `prependInnerIcon` so it sits inside the field like every other field's icon.
    VDateInput: {
      variant: 'outlined',
      density: 'comfortable',
      // Do not infer the browser's US-style date order. The same explicit format
      // controls both what the field displays and how typed dates are parsed.
      inputFormat: 'dd/mm/yyyy',
      pickerProps: { color: 'secondary' },
      prependIcon: '',
      prependInnerIcon: '$calendar',
    },
  },
})
