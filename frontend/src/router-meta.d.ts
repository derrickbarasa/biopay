import 'vue-router'

export {}

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    seoTitle?: string
    description?: string
    indexable?: boolean
    canonicalPath?: string
    socialImage?: string
  }
}
