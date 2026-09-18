import type { RouteLocationNormalized } from 'vue-router'

const PRODUCT_NAME = 'BioPay'
const DEFAULT_TITLE = 'BioPay | Biometric Payment Infrastructure'
const DEFAULT_DESCRIPTION = 'BioPay provides offline-first biometric registration, payment and voucher infrastructure for humanitarian programmes.'
const DEFAULT_IMAGE_PATH = '/og-image.jpg'
const PRODUCTION_SITE_URL = 'https://biopay.africa'

function productionOrigin() {
  const configured = import.meta.env.VITE_PUBLIC_SITE_URL?.trim()

  try {
    const url = new URL(configured || PRODUCTION_SITE_URL)
    if (url.protocol === 'https:' || url.protocol === 'http:') return url.origin
  } catch {
    // A bad optional override must not prevent the application from starting.
  }

  return PRODUCTION_SITE_URL
}

function upsertMeta(selector: string, attribute: 'name' | 'property', key: string, content: string) {
  let element = document.head.querySelector<HTMLMetaElement>(selector)
  if (!element) {
    element = document.createElement('meta')
    element.setAttribute(attribute, key)
    document.head.appendChild(element)
  }
  element.content = content
}

function setCanonical(url: string | null) {
  const existing = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]')
  if (!url) {
    existing?.remove()
    return
  }

  const element = existing ?? document.createElement('link')
  element.rel = 'canonical'
  element.href = url
  if (!existing) document.head.appendChild(element)
}

export function applyRouteSeo(route: RouteLocationNormalized) {
  const title = typeof route.meta.title === 'string' ? route.meta.title : undefined
  const seoTitle = typeof route.meta.seoTitle === 'string' ? route.meta.seoTitle : undefined
  const description = typeof route.meta.description === 'string' ? route.meta.description : DEFAULT_DESCRIPTION
  const indexable = route.meta.indexable === true
  const pageTitle = seoTitle ?? (title ? `${title} | ${PRODUCT_NAME}` : DEFAULT_TITLE)
  const origin = productionOrigin()
  const canonicalPath = typeof route.meta.canonicalPath === 'string' ? route.meta.canonicalPath : route.path
  const canonical = indexable ? new URL(canonicalPath, `${origin}/`).toString() : null
  const imagePath = typeof route.meta.socialImage === 'string' ? route.meta.socialImage : DEFAULT_IMAGE_PATH
  const socialImage = new URL(imagePath, `${origin}/`).toString()

  document.title = pageTitle
  document.documentElement.lang = 'en'

  upsertMeta('meta[name="description"]', 'name', 'description', description)
  upsertMeta(
    'meta[name="robots"]',
    'name',
    'robots',
    indexable
      ? 'index, follow, max-image-preview:large, max-snippet:-1, max-video-preview:-1'
      : 'noindex, nofollow, noarchive',
  )
  upsertMeta('meta[property="og:site_name"]', 'property', 'og:site_name', PRODUCT_NAME)
  upsertMeta('meta[property="og:title"]', 'property', 'og:title', pageTitle)
  upsertMeta('meta[property="og:description"]', 'property', 'og:description', description)
  upsertMeta('meta[property="og:type"]', 'property', 'og:type', 'website')
  upsertMeta('meta[property="og:url"]', 'property', 'og:url', canonical ?? `${origin}/`)
  upsertMeta('meta[property="og:image"]', 'property', 'og:image', socialImage)
  upsertMeta('meta[property="og:image:secure_url"]', 'property', 'og:image:secure_url', socialImage)
  upsertMeta('meta[name="twitter:card"]', 'name', 'twitter:card', 'summary_large_image')
  upsertMeta('meta[name="twitter:title"]', 'name', 'twitter:title', pageTitle)
  upsertMeta('meta[name="twitter:description"]', 'name', 'twitter:description', description)
  upsertMeta('meta[name="twitter:image"]', 'name', 'twitter:image', socialImage)
  upsertMeta('meta[name="twitter:image:alt"]', 'name', 'twitter:image:alt', 'BioPay biometric verification and cash transfer workflow')
  setCanonical(canonical)
}
