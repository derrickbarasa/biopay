import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vuetify from 'vite-plugin-vuetify'

export default defineConfig({
  plugins: [vue(), vuetify({ autoImport: true })],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // Use one explicit IPv4 origin. On this workstation another app owns
    // 127.0.0.1:5173 while an older BioPay process owned [::1]:5173, causing
    // a single browser session to load lazy modules from two different apps.
    host: '127.0.0.1',
    port: 5175,
    strictPort: true,
  },
})
