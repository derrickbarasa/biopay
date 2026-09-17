/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_CONTACT_EMAIL?: string
  readonly VITE_DEMO_EMAIL?: string
  readonly VITE_PUBLIC_SITE_URL?: string
  readonly VITE_SITEMAP_LASTMOD?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
