import { access, readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const fallbackSiteUrl = 'https://biopay.africa'
const privateRoutePrefixes = ['/app', '/login', '/signup', '/forgot-password', '/reset-password', '/verify-otp', '/approval', '/change-password']

async function readEnvValue(fileName, key) {
  try {
    const contents = await readFile(path.join(frontendRoot, fileName), 'utf8')
    const line = contents.split(/\r?\n/).find((entry) => entry.trim().startsWith(`${key}=`))
    return line?.split('=').slice(1).join('=').trim().replace(/^['"]|['"]$/g, '')
  } catch {
    return undefined
  }
}

async function siteOrigin() {
  const configured = process.env.VITE_PUBLIC_SITE_URL
    || await readEnvValue('.env.production', 'VITE_PUBLIC_SITE_URL')
    || await readEnvValue('.env', 'VITE_PUBLIC_SITE_URL')
    || fallbackSiteUrl
  const parsed = new URL(configured)
  if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('VITE_PUBLIC_SITE_URL must use http or https')
  return parsed.origin
}

function escapeXml(value) {
  return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&apos;')
}

const publicPages = [
  {
    path: '/',
    images: [
      'hero/cash-transfer.webp',
      'hero/voucher-redemption.webp',
      'hero/biometric-verification.webp',
      'hero/deduplication.webp',
      'hero/ai-agent.webp',
    ],
  },
]

const origin = await siteOrigin()
const configuredLastModified = process.env.VITE_SITEMAP_LASTMOD
  || await readEnvValue('.env.production', 'VITE_SITEMAP_LASTMOD')
  || await readEnvValue('.env', 'VITE_SITEMAP_LASTMOD')
if (configuredLastModified && !/^\d{4}-\d{2}-\d{2}$/.test(configuredLastModified)) {
  throw new Error('VITE_SITEMAP_LASTMOD must use YYYY-MM-DD')
}

const seenPaths = new Set()
for (const page of publicPages) {
  if (!page.path.startsWith('/') || privateRoutePrefixes.some((prefix) => page.path.startsWith(prefix))) {
    throw new Error(`Refusing to publish private or invalid sitemap path: ${page.path}`)
  }
  if (seenPaths.has(page.path)) throw new Error(`Duplicate sitemap path: ${page.path}`)
  seenPaths.add(page.path)
  await Promise.all(page.images.map((imagePath) => access(path.join(frontendRoot, 'public', imagePath))))
}

const sitemapEntries = publicPages.map((page) => {
  const pageUrl = new URL(page.path, `${origin}/`).toString()
  const lastModified = configuredLastModified ? `\n    <lastmod>${configuredLastModified}</lastmod>` : ''
  const images = page.images.map((imagePath) => `
    <image:image>
      <image:loc>${escapeXml(new URL(imagePath, `${origin}/`).toString())}</image:loc>
    </image:image>`).join('')
  return `  <url>\n    <loc>${escapeXml(pageUrl)}</loc>${lastModified}${images}\n  </url>`
}).join('\n')

const sitemap = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"
        xmlns:image="http://www.google.com/schemas/sitemap-image/1.1">
${sitemapEntries}
</urlset>
`

const robots = `User-agent: *
Allow: /
Disallow: /app
Disallow: /login
Disallow: /signup
Disallow: /forgot-password
Disallow: /reset-password
Disallow: /verify-otp
Disallow: /approval
Disallow: /change-password

Sitemap: ${origin}/sitemap.xml
`

await Promise.all([
  writeFile(path.join(frontendRoot, 'public', 'sitemap.xml'), sitemap, 'utf8'),
  writeFile(path.join(frontendRoot, 'public', 'robots.txt'), robots, 'utf8'),
])

console.log(`Generated sitemap.xml and robots.txt for ${origin}`)
