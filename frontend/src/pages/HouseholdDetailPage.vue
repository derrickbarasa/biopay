<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiClient, dispatch } from '@/api/client'
import { apiRelativeFilePath } from '@/api/paths'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { householdReviewStatus } from '@/utils/householdReview'
import HouseholdReviewActions from '@/components/HouseholdReviewActions.vue'
import {
  LEGAL_STATUS_OPTIONS,
  MARITAL_STATUS_OPTIONS,
  VULNERABILITY_OPTIONS,
  legalStatusLabel,
  vulnerabilityLabel,
} from '@/constants/householdClassifications'
import { ALTERNATE_RELATIONSHIP_OPTIONS, inferGenderFromRelationship } from '@/constants/alternateRelationship'
import { ageToDateOfBirth, dateOfBirthToAge } from '@/utils/dateOfBirth'
import { formatCurrency } from '@/utils/currency'

const maritalStatusItems: readonly string[] = MARITAL_STATUS_OPTIONS
const alternateRelationshipItems: readonly string[] = ALTERNATE_RELATIONSHIP_OPTIONS

interface Alternate {
  alternateNumber?: string
  alternateName?: string
  relationship?: string
  phoneNumber?: string
  age?: number
  gender?: string
  images?: string[]
  createdAt?: string
  status?: number
  fingerprintStatus?: string
  fingerprintNumbers?: number[]
  faceStatus?: string
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToast()
const { confirmAction } = useConfirm()

interface GeoNode { code: string; name: string; stateCode?: string; countyCode?: string; locationCode?: string }

interface PaymentEvent {
  id?: number
  amount?: number
  status?: number
  cycle?: string
  createdAt?: string
}
interface VoucherEvent {
  voucherCode?: string
  amount?: number
  status?: string
  purpose?: string
  expiresAt?: string
  redeemedAt?: string
  createdAt?: string
}

const loading = ref(true)
const detail = ref<Record<string, any> | null>(null)
const alternates = ref<Alternate[]>([])
const payments = ref<PaymentEvent[]>([])
const vouchers = ref<VoucherEvent[]>([])
// Object URLs for photos fetched (with the auth header) through apiClient as blobs --
// a plain image URL can't reach the JWT-protected /files route.
const photoUrls = ref<string[]>([])
const photoLoading = ref(false)
const photoLoadError = ref('')
const exportingAlternates = ref(false)
// Same blob-fetch pattern, keyed by alternateNumber, for each alternate's own gallery.
const alternatePhotoUrls = ref<Record<string, string[]>>({})
// Currently expanded photo, shown large in the lightbox dialog below -- every small photo
// thumbnail on this page (header avatar, household gallery, alternate avatar/gallery) opens it.
const lightboxSrc = ref<string | null>(null)
// Which person the open lightbox photo belongs to -- so the downloaded filename can
// tell photos apart across households and alternates that happen to share a name.
const lightboxContext = ref<{ name: string; alternateNumber?: string } | null>(null)
const photoDownloading = ref(false)

function openLightbox(src: string, name: string, alternateNumber?: string) {
  lightboxSrc.value = src
  lightboxContext.value = { name, alternateNumber }
}
const fingerprintDialog = ref(false)

const fingerPositions = [
  { number: 1, hand: 'Right hand', label: 'Thumb' },
  { number: 2, hand: 'Right hand', label: 'Index' },
  { number: 3, hand: 'Right hand', label: 'Middle' },
  { number: 4, hand: 'Right hand', label: 'Ring' },
  { number: 5, hand: 'Right hand', label: 'Little' },
  { number: 6, hand: 'Left hand', label: 'Thumb' },
  { number: 7, hand: 'Left hand', label: 'Index' },
  { number: 8, hand: 'Left hand', label: 'Middle' },
  { number: 9, hand: 'Left hand', label: 'Ring' },
  { number: 10, hand: 'Left hand', label: 'Little' },
]
const capturedFingerNumbers = computed(() => new Set<number>(detail.value?.fingerprintNumbers ?? []))
const capturedFingerprintCount = computed(() => capturedFingerNumbers.value.size)

// Same fingerprint-coverage display as the household head above, reused for whichever
// alternate is currently open in the view dialog.
const altFingerprintDialog = ref(false)
const altCapturedFingerNumbers = computed(() => new Set<number>(viewAltTarget.value?.fingerprintNumbers ?? []))
const altCapturedFingerprintCount = computed(() => altCapturedFingerNumbers.value.size)

// Name-not-code lookups, matching the pattern used on the Households list page.
const organizations = ref<{ organisationCode: string; name: string }[]>([])
const states = ref<GeoNode[]>([])
const counties = ref<GeoNode[]>([])
const locations = ref<GeoNode[]>([])
const villages = ref<GeoNode[]>([])
const orgNameByCode = computed(() => new Map(organizations.value.map((o) => [o.organisationCode, o.name])))
const stateNameByCode = computed(() => new Map(states.value.map((s) => [s.code, s.name])))
const countyNameByCode = computed(() => new Map(counties.value.map((c) => [c.code, c.name])))
const locationNameByCode = computed(() => new Map(locations.value.map((l) => [l.code, l.name])))
const villageNameByCode = computed(() => new Map(villages.value.map((v) => [v.code, v.name])))
function orgName(code?: string) { return (code && orgNameByCode.value.get(code)) || code || '—' }
const countiesForState = (stateCode: string) => stateCode ? counties.value.filter((c) => c.stateCode === stateCode) : counties.value
const locationsForCounty = (countyCode: string) => countyCode ? locations.value.filter((l) => l.countyCode === countyCode) : locations.value
const villagesForLocation = (locationCode: string) => locationCode ? villages.value.filter((v) => v.locationCode === locationCode) : villages.value

const householdNumber = computed(() => String(route.params.householdNumber ?? ''))
const reviewStatus = computed(() => householdReviewStatus(detail.value?.reviewStatus))
const reviewStatusColor: Record<string, string> = { PENDING: 'default', APPROVED: 'success', REJECTED: 'error' }

const genderLabel = (g?: string) => (g === 'M' ? 'Male' : g === 'F' ? 'Female' : (g || '—'))

const infoFields = computed(() => {
  const d = detail.value
  if (!d) return []
  const fields = [
    { label: 'Household code', value: d.householdNumber },
    { label: 'Head of household', value: d.householdName },
    { label: 'Organization', value: orgName(d.organisationCode) },
    { label: 'Age', value: d.age ?? '—' },
    { label: 'Gender', value: genderLabel(d.gender) },
    { label: 'Marital status', value: d.maritalStatus || '—' },
    { label: 'Spouse name', value: d.spouseName || '—' },
    { label: 'ID / document number', value: d.idNumber || '—' },
    { label: 'Phone number', value: d.phoneNumber || '—' },
    { label: 'Household size', value: d.householdSize ?? '—' },
    { label: 'Female dependants', value: d.femaleDependants ?? '—' },
    { label: 'Male dependants', value: d.maleDependants ?? '—' },
    { label: 'Vulnerability categories', value: (d.vulnerabilityStatuses ?? []).map(vulnerabilityLabel).join(', ') || 'Not recorded' },
    { label: 'Legal status', value: legalStatusLabel(d.legalStatus) },
    { label: 'Review status', value: householdReviewStatus(d.reviewStatus) },
    { label: 'State', value: stateNameByCode.value.get(d.stateCode) || d.stateCode || '—' },
    { label: 'County', value: countyNameByCode.value.get(d.countyCode) || d.countyCode || '—' },
    { label: 'Location', value: locationNameByCode.value.get(d.payamCode) || d.payamCode || '—' },
    { label: 'Village', value: villageNameByCode.value.get(d.bomaCode) || d.bomaCode || '—' },
    { label: 'Coordinates', value: d.latitude && d.longitude ? `${d.latitude}, ${d.longitude}` : '—' },
    { label: 'Registered', value: d.createdAt || '—' },
    { label: 'Last updated', value: d.updatedAt || '—' },
  ]
  if (householdReviewStatus(d.reviewStatus) === 'REJECTED' && d.rejectionReason) {
    fields.splice(15, 0, { label: 'Rejection reason', value: d.rejectionReason })
  }
  return fields
})

// Captured by the Android field app at registration (device GPS/network last-known-fix,
// requested once at login -- see LocationHelper/HomeActivity in mobile/agent), synced up as
// part of the household record. Households registered before that permission was ever
// granted, or via the web dashboard, have no coordinates -- the map is omitted for those.
const coordinates = computed(() => {
  const lat = Number(detail.value?.latitude)
  const lon = Number(detail.value?.longitude)
  return Number.isFinite(lat) && Number.isFinite(lon) && (lat !== 0 || lon !== 0) ? { lat, lon } : null
})
const mapEmbedUrl = computed(() => {
  const c = coordinates.value
  if (!c) return ''
  const delta = 0.003
  const bbox = [c.lon - delta, c.lat - delta, c.lon + delta, c.lat + delta].join('%2C')
  return `https://www.openstreetmap.org/export/embed.html?bbox=${bbox}&layer=mapnik&marker=${c.lat}%2C${c.lon}`
})
const mapLinkUrl = computed(() => {
  const c = coordinates.value
  return c ? `https://www.openstreetmap.org/?mlat=${c.lat}&mlon=${c.lon}#map=17/${c.lat}/${c.lon}` : ''
})

function revokePhotos() {
  for (const u of photoUrls.value) URL.revokeObjectURL(u)
  photoUrls.value = []
  lightboxSrc.value = null
  lightboxContext.value = null
}

async function downloadLightboxPhoto() {
  if (!lightboxSrc.value || photoDownloading.value) return
  photoDownloading.value = true
  try {
    const response = await fetch(lightboxSrc.value)
    if (!response.ok) throw new Error('Photo download failed')
    const blob = await response.blob()
    const extensionByType: Record<string, string> = {
      'image/jpeg': 'jpg',
      'image/png': 'png',
      'image/webp': 'webp',
      'image/gif': 'gif',
    }
    const extension = extensionByType[blob.type] ?? 'jpg'
    const context = lightboxContext.value
    // Include the household code and, for an alternate, their own alternate code -- a
    // person's name alone collides across households (and across alternates within one
    // household), overwriting or shuffling earlier downloads when several are saved.
    const nameParts = [
      context?.name || detail.value?.householdName,
      householdNumber.value,
      context?.alternateNumber,
    ].filter(Boolean) as string[]
    const baseName = nameParts.join('-')
      .trim()
      .replace(/[^a-z0-9]+/gi, '-')
      .replace(/^-|-$/g, '')
      .toLowerCase() || 'household-photo'
    const downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = `${baseName}-photo.${extension}`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(downloadUrl)
  } catch {
    toast.error('The photo could not be downloaded. Please try again.')
  } finally {
    photoDownloading.value = false
  }
}

function revokeAlternatePhotos() {
  for (const urls of Object.values(alternatePhotoUrls.value)) {
    for (const u of urls) URL.revokeObjectURL(u)
  }
  alternatePhotoUrls.value = {}
  lightboxSrc.value = null
}

// Fetches each JWT-protected photo through apiClient (which attaches the bearer
// token) and turns the blob into a displayable object URL. Shared by the household
// head's own gallery and every alternate's gallery below.
// Returns the last error too (not just the successful urls) so a total failure can show
// *why* -- e.g. 404 (the file isn't on the server the dashboard is talking to, or the
// image row's stored filename doesn't match a real file) vs 403 (viewer's org/anchor
// doesn't match who the photo was uploaded under) vs a network failure -- instead of one
// generic "could not be loaded" that looks identical for every different root cause.
async function fetchPhotoBlobs(paths: string[]): Promise<{ urls: string[]; lastError: unknown }> {
  const urls: string[] = []
  let lastError: unknown = null
  for (const p of paths) {
    try {
      const rel = apiRelativeFilePath(p)
      if (!rel) continue
      const res = await apiClient.get(rel, { responseType: 'blob' })
      urls.push(URL.createObjectURL(res.data as Blob))
    } catch (err) {
      lastError = err
      // Skip an image that fails to load rather than failing the whole page.
    }
  }
  return { urls, lastError }
}

function photoErrorReason(err: unknown): string {
  // apiClient's response interceptor already unwraps AxiosError into a plain Error
  // carrying `.status` (see api/client.ts) -- by the time it reaches here there is no
  // `.response` left to read, so checking `.response?.status` always missed and this
  // reported "a network error" for every failure, 404/403 included.
  const status = (err as { status?: number } | undefined)?.status
  if (status === 404) return 'the file is missing on the server'
  if (status === 401 || status === 403) return "you don't have access to this photo"
  if (status != null) return `server returned ${status}`
  return 'a network error'
}

async function loadPhotos(paths: string[]) {
  revokePhotos()
  photoLoading.value = true
  photoLoadError.value = ''
  try {
    const { urls, lastError } = await fetchPhotoBlobs(paths)
    photoUrls.value = urls
    if (paths.length && !photoUrls.value.length) {
      photoLoadError.value = `The captured photo could not be loaded (${photoErrorReason(lastError)}).`
    }
  } finally {
    photoLoading.value = false
  }
}

function retryPhotos() {
  const images: string[] = detail.value?.images ?? []
  if (images.length) loadPhotos(images)
}

async function loadAlternatePhotos() {
  revokeAlternatePhotos()
  const withPhotos = alternates.value.filter((a) => a.alternateNumber && a.images?.length)
  const entries = await Promise.all(
    withPhotos.map(async (a) => [a.alternateNumber as string, (await fetchPhotoBlobs(a.images ?? [])).urls] as const),
  )
  alternatePhotoUrls.value = Object.fromEntries(entries)
}

async function load() {
  loading.value = true
  try {
    const [h, alts, hist] = await Promise.all([
      dispatch<{ results: any[] }>('GET_HOUSEHOLD', { householdNumber: householdNumber.value }),
      auth.can('ACCESS_ALTERNATES')
        ? dispatch<{ results: Alternate[] }>('GET_ALTERNATES', { householdNumber: householdNumber.value, includeInactive: true })
        : Promise.resolve({ results: [] as Alternate[] }),
      dispatch<{ results: { payments: PaymentEvent[]; vouchers: VoucherEvent[] } }>(
        'GET_HOUSEHOLD_HISTORY', { householdNumber: householdNumber.value },
      ),
    ])
    detail.value = h.results?.[0] ?? null
    alternates.value = alts.results ?? []
    payments.value = hist.results?.payments ?? []
    vouchers.value = hist.results?.vouchers ?? []
    const images: string[] = detail.value?.images ?? []
    await loadPhotos(images)
    loadAlternatePhotos()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load household')
  } finally {
    loading.value = false
  }
}

async function loadNameLookups() {
  try {
    const requests: Promise<any>[] = [
      dispatch<{ results: GeoNode[] }>('GET_STATES'),
      dispatch<{ results: GeoNode[] }>('GET_COUNTIES'),
      dispatch<{ results: GeoNode[] }>('GET_LOCATIONS'),
      dispatch<{ results: GeoNode[] }>('GET_VILLAGES'),
    ]
    if (auth.isAnchor) requests.push(dispatch<{ results: typeof organizations.value }>('GET_ORGANIZATIONS'))
    const [s, c, l, v, o] = await Promise.all(requests)
    states.value = s.results
    counties.value = c.results
    locations.value = l.results
    villages.value = v.results
    if (o) organizations.value = o.results
  } catch {
    // Fields just fall back to showing the raw code; the rest of the page still works.
  }
}

onUnmounted(() => {
  revokePhotos()
  revokeAlternatePhotos()
})

function goBack() {
  router.push({ name: 'households' })
}

const printing = ref(false)

function escapeHtml(s: string): string {
  return String(s ?? '').replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c] as string
  ))
}

// Fetches the self-contained voucher (name + photo + QR, all inlined as data URIs)
// and opens a print-ready window.
async function printVoucher() {
  printing.value = true
  try {
    const res = await dispatch<{ results: { householdNumber: string; householdName: string; organisationCode: string; photo?: string; qr?: string } }>(
      'GET_HOUSEHOLD_VOUCHER', { householdNumber: householdNumber.value },
    )
    const v = res.results
    const w = window.open('', '_blank', 'width=760,height=920')
    if (!w) {
      toast.error('Allow pop-ups to print the voucher')
      return
    }
    const photo = v.photo
      ? `<img class="photo" src="${v.photo}" alt="Beneficiary photo" />`
      : `<div class="photo placeholder">No photo</div>`
    const qr = v.qr ? `<img class="qr" src="${v.qr}" alt="Household QR code" />` : ''
    w.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>Payment Voucher - ${escapeHtml(v.householdNumber)}</title>
      <style>
        @import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;700;800&display=swap');
        * { box-sizing: border-box; font-family: "Outfit", sans-serif; }
        body { margin: 0; padding: 32px; color: #0f172a; }
        .voucher { max-width: 620px; margin: 0 auto; border: 2px solid #0d9488; border-radius: 14px; padding: 28px; }
        .head { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 1px solid #e2e8f0; padding-bottom: 14px; margin-bottom: 20px; }
        .title { font-size: 22px; font-weight: 800; color: #0f766e; letter-spacing: -.01em; }
        .sub { color: #64748b; font-size: 13px; margin-top: 2px; }
        .body { display: flex; gap: 24px; align-items: center; }
        .photo { width: 150px; height: 150px; object-fit: cover; border-radius: 10px; border: 1px solid #e2e8f0; }
        .placeholder { display: flex; align-items: center; justify-content: center; color: #94a3b8; background: #f1f5f9; font-size: 13px; }
        .info { flex: 1; }
        .name { font-size: 24px; font-weight: 700; margin: 0 0 6px; }
        .row { font-size: 14px; color: #334155; margin: 3px 0; }
        .label { color: #64748b; }
        .qrwrap { text-align: center; }
        .qr { width: 150px; height: 150px; }
        .qrcap { font-size: 11px; color: #94a3b8; margin-top: 4px; }
        .foot { margin-top: 24px; border-top: 1px dashed #cbd5e1; padding-top: 12px; font-size: 12px; color: #94a3b8; display: flex; justify-content: space-between; }
        @media print { body { padding: 0; } .voucher { border-color: #0d9488; } }
      </style></head>
      <body onload="window.focus()">
        <div class="voucher">
          <div class="head">
            <div><div class="title">Payment Voucher</div><div class="sub">${escapeHtml(orgName(v.organisationCode))}</div></div>
            <div class="qrwrap">${qr}<div class="qrcap">${escapeHtml(v.householdNumber)}</div></div>
          </div>
          <div class="body">
            ${photo}
            <div class="info">
              <p class="name">${escapeHtml(v.householdName ?? '')}</p>
              <div class="row"><span class="label">Household code:</span> ${escapeHtml(v.householdNumber)}</div>
              <div class="row"><span class="label">Organization:</span> ${escapeHtml(orgName(v.organisationCode))}</div>
              <div class="row"><span class="label">Issued:</span> ${escapeHtml(new Date().toLocaleDateString())}</div>
            </div>
          </div>
          <div class="foot"><span>BioPay</span><span>Signature: ____________________</span></div>
        </div>
      </body></html>`)
    w.document.close()
    w.focus()
    setTimeout(() => w.print(), 300)
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to generate voucher')
  } finally {
    printing.value = false
  }
}

const editDialog = ref(false)
const editing = ref(false)
const editForm = ref({
  householdName: '', dateOfBirth: null as Date | null, gender: '', maritalStatus: null as string | null, spouseName: '', phoneNumber: '',
  householdSize: null as number | null, stateCode: '', countyCode: '', locationCode: '', villageCode: '',
  vulnerabilityStatuses: [] as string[], legalStatus: null as string | null,
  photo: null as File | null,
})
const uploadingEditPhoto = ref(false)

function onEditPhotoFile(event: Event) {
  editForm.value.photo = (event.target as HTMLInputElement).files?.[0] ?? null
}

// Bound to each select's own change event, not a form-wide watcher, so dependent
// fields reset only on an active pick -- never when openEdit() populates the form.
function onEditStateChange() { editForm.value.countyCode = ''; editForm.value.locationCode = ''; editForm.value.villageCode = '' }
function onEditCountyChange() { editForm.value.locationCode = ''; editForm.value.villageCode = '' }
function onEditLocationChange() { editForm.value.villageCode = '' }

function openEdit() {
  const d = detail.value
  if (!d) return
  editForm.value = {
    householdName: d.householdName ?? '',
    dateOfBirth: ageToDateOfBirth(d.age),
    gender: d.gender ?? '',
    maritalStatus: d.maritalStatus ?? null,
    spouseName: d.spouseName ?? '',
    phoneNumber: d.phoneNumber ?? '',
    householdSize: d.householdSize ?? null,
    stateCode: d.stateCode ?? '',
    countyCode: d.countyCode ?? '',
    locationCode: d.payamCode ?? '',
    villageCode: d.bomaCode ?? '',
    vulnerabilityStatuses: d.vulnerabilityStatuses ?? [],
    legalStatus: d.legalStatus ?? null,
    photo: null,
  }
  editDialog.value = true
}

async function saveEdit() {
  if (!editForm.value.householdName.trim()) {
    toast.error('Head of household name is required')
    return
  }
  editing.value = true
  try {
    await dispatch('UPDATE_HOUSEHOLD', {
      householdNumber: householdNumber.value,
      householdName: editForm.value.householdName.trim(),
      age: dateOfBirthToAge(editForm.value.dateOfBirth) ?? undefined,
      gender: editForm.value.gender || undefined,
      maritalStatus: editForm.value.maritalStatus || undefined,
      spouseName: editForm.value.spouseName || undefined,
      phoneNumber: editForm.value.phoneNumber || undefined,
      householdSize: editForm.value.householdSize ?? undefined,
      bomaCode: editForm.value.villageCode || undefined,
      vulnerabilityStatuses: editForm.value.vulnerabilityStatuses,
      legalStatus: editForm.value.legalStatus || undefined,
    })
    if (editForm.value.photo) {
      uploadingEditPhoto.value = true
      try {
        const dataUrl = await fileToDataUrl(editForm.value.photo)
        const extension = (editForm.value.photo.name.split('.').pop() || 'jpg').toLowerCase()
        await dispatch('UPLOAD_IMAGE', {
          beneficiaryId: householdNumber.value,
          beneficiaryType: 1,
          imageBase64: dataUrl,
          extension,
        })
      } catch (err) {
        toast.error(err instanceof Error
          ? `Household updated, but the photo failed to upload: ${err.message}`
          : 'Household updated, but the photo failed to upload')
      } finally {
        uploadingEditPhoto.value = false
      }
    }
    toast.success('Household updated')
    editDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to update household')
  } finally {
    editing.value = false
  }
}

async function exportAlternates() {
  if (!alternates.value.length) {
    toast.error('No alternates to export')
    return
  }
  exportingAlternates.value = true
  try {
    const XLSX = await import('xlsx')
    const rows = alternates.value.map((alternate) => {
      const parsedTimestamp = alternate.createdAt ? new Date(alternate.createdAt) : null
      return [
        String(detail.value?.householdNumber ?? householdNumber.value),
        String(detail.value?.householdName ?? ''),
        alternate.alternateNumber ?? '',
        alternate.alternateName ?? '',
        alternate.relationship ?? '',
        genderLabel(alternate.gender),
        alternate.phoneNumber ?? '',
        parsedTimestamp && !Number.isNaN(parsedTimestamp.getTime()) ? parsedTimestamp : '',
      ]
    })
    const worksheet = XLSX.utils.aoa_to_sheet([
      ['Household Code', 'Household Name', 'Alternate Code', 'Alternate Name', 'Relationship to Household', 'Gender', 'Phone', 'Created At'],
      ...rows,
    ], { cellDates: true })
    worksheet['!cols'] = [
      { wch: 24 }, { wch: 32 }, { wch: 22 }, { wch: 28 }, { wch: 28 }, { wch: 16 }, { wch: 20 }, { wch: 23 },
    ]
    for (let row = 2; row <= rows.length + 1; row += 1) {
      const timestampCell = worksheet[`H${row}`]
      if (timestampCell?.t === 'd') timestampCell.z = 'yyyy-mm-dd hh:mm:ss'
    }
    worksheet['!autofilter'] = { ref: `A1:H${rows.length + 1}` }

    const workbook = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(workbook, worksheet, 'Alternates')
    XLSX.writeFile(workbook, `alternates-${householdNumber.value}.xlsx`, { cellDates: true })
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to export alternates')
  } finally {
    exportingAlternates.value = false
  }
}

// A picked photo uploads via UPLOAD_IMAGE with beneficiaryType 2, matching the
// app-wide 1=head/2=alternate convention.
const addAltDialog = ref(false)
const addingAlt = ref(false)
const altForm = ref({ alternateName: '', relationship: '', phoneNumber: '', gender: '', dateOfBirth: null as Date | null })
const altPhotoFile = ref<File | null>(null)
// null while adding; the alternate being edited otherwise -- the same dialog and altForm
// serve both flows, only the dispatch code and dialog title differ.
const editingAlternateNumber = ref<string | null>(null)

const altTableHeaders = [
  { title: 'Photo', key: 'photo', sortable: false, width: '6%', minWidth: 72 },
  { title: 'Alternate Code', key: 'alternateNumber', width: '16%', minWidth: 164, nowrap: true },
  { title: 'Name', key: 'alternateName', width: '17%', minWidth: 116 },
  { title: 'Relationship', key: 'relationship', width: '17%', minWidth: 132 },
  { title: 'Gender', key: 'gender', width: '9%', minWidth: 92 },
  { title: 'Added', key: 'createdAt', width: '15%', minWidth: 128 },
  { title: 'Status', key: 'status', width: '9%', minWidth: 100 },
  { title: 'Actions', key: 'actions', sortable: false, align: 'start' as const, width: '11%', minWidth: 132, fixed: true, nowrap: true },
]

const paymentTableHeaders = [
  { title: 'Payment ID', key: 'id', width: '14%', minWidth: 112, nowrap: true },
  { title: 'Cycle Code', key: 'cycle', width: '27%', minWidth: 240, nowrap: true },
  { title: 'Amount', key: 'amount', width: '18%', minWidth: 128 },
  { title: 'Status', key: 'status', width: '16%', minWidth: 112 },
  { title: 'Payment Date', key: 'createdAt', width: '25%', minWidth: 176, nowrap: true },
]

const voucherTableHeaders = [
  { title: 'Voucher Code', key: 'voucherCode', width: '17%', minWidth: 180, nowrap: true },
  { title: 'Purpose', key: 'purpose', width: '15%', minWidth: 144 },
  { title: 'Amount', key: 'amount', width: '13%', minWidth: 120 },
  { title: 'Status', key: 'status', width: '11%', minWidth: 104 },
  { title: 'Issued', key: 'createdAt', width: '15%', minWidth: 160, nowrap: true },
  { title: 'Expires', key: 'expiresAt', width: '15%', minWidth: 160, nowrap: true },
  { title: 'Redeem', key: 'redemptionStatus', width: '14%', minWidth: 160, nowrap: true },
]

function formatTimestamp(value?: string) {
  if (!value) return '—'
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

function paymentStatusLabel(status?: number) {
  return status === 1 ? 'Paid' : status === 2 ? 'Failed' : 'Pending'
}

function paymentStatusColor(status?: number) {
  return status === 1 ? 'success' : status === 2 ? 'error' : 'warning'
}

function voucherStatusColor(status?: string) {
  return status === 'REDEEMED' ? 'success' : status === 'VOID' ? 'error' : 'warning'
}

function voucherRedemptionState(voucher: VoucherEvent) {
  if (voucher.status === 'REDEEMED' || voucher.redeemedAt) return { label: 'Success', color: 'success' }
  if (voucher.status === 'VOID') return { label: 'Failed', color: 'error' }
  return { label: 'Pending', color: 'warning' }
}

function openAddAlternate() {
  editingAlternateNumber.value = null
  altForm.value = { alternateName: '', relationship: '', phoneNumber: '', gender: '', dateOfBirth: null }
  altPhotoFile.value = null
  addAltDialog.value = true
}

function openEditAlternate(a: Alternate) {
  editingAlternateNumber.value = a.alternateNumber ?? null
  altForm.value = {
    alternateName: a.alternateName ?? '',
    relationship: a.relationship ?? '',
    phoneNumber: a.phoneNumber ?? '',
    gender: a.gender ?? '',
    dateOfBirth: ageToDateOfBirth(a.age),
  }
  altPhotoFile.value = null
  addAltDialog.value = true
}

const viewAltDialog = ref(false)
const viewAltTarget = ref<Alternate | null>(null)
function openViewAlternate(a: Alternate) {
  viewAltTarget.value = a
  viewAltDialog.value = true
}

async function deactivateAlternate(a: Alternate) {
  if (!await confirmAction({
    title: 'Deactivate alternate?',
    message: `${a.alternateName} will no longer appear as an alternate for this household. Their record is kept, but re-adding them would need a new entry.`,
    confirmLabel: 'Deactivate',
    color: 'error',
  })) return
  try {
    await dispatch('DELETE_ALTERNATE', { alternateNumber: a.alternateNumber })
    toast.success('Alternate deactivated')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to deactivate alternate')
  }
}

async function activateAlternate(a: Alternate) {
  try {
    await dispatch('ACTIVATE_ALTERNATE', { alternateNumber: a.alternateNumber })
    toast.success('Alternate activated')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to activate alternate')
  }
}

// Auto-picks gender for relationships with an unambiguous gender (e.g. "Husband" -> M);
// left alone for exceptions like Cousin/In-law/Ward/Other so the officer can choose it.
function onAltRelationshipChange(relationship: string | null) {
  const inferred = inferGenderFromRelationship(relationship ?? undefined)
  if (inferred) altForm.value.gender = inferred
}

function onAltPhotoFile(event: Event) {
  altPhotoFile.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(reader.error ?? new Error('Failed to read the selected file'))
    reader.readAsDataURL(file)
  })
}

async function saveAlternate() {
  if (!altForm.value.alternateName.trim()) {
    toast.error('Name is required')
    return
  }
  addingAlt.value = true
  try {
    const organisationCode = auth.isAnchor ? detail.value?.organisationCode : undefined
    const editing = editingAlternateNumber.value
    const age = dateOfBirthToAge(altForm.value.dateOfBirth) ?? undefined
    const alternateNumber = editing ?? (await dispatch<{ alternateNumber: string }>('CREATE_ALTERNATE', {
      householdNumber: householdNumber.value,
      organisationCode,
      alternateName: altForm.value.alternateName.trim(),
      relationship: altForm.value.relationship || undefined,
      phoneNumber: altForm.value.phoneNumber || undefined,
      gender: altForm.value.gender || undefined,
      age,
    })).alternateNumber
    if (editing) {
      await dispatch('UPDATE_ALTERNATE', {
        alternateNumber,
        alternateName: altForm.value.alternateName.trim(),
        relationship: altForm.value.relationship || undefined,
        phoneNumber: altForm.value.phoneNumber || undefined,
        gender: altForm.value.gender || undefined,
        age,
      })
    }
    if (altPhotoFile.value) {
      try {
        const dataUrl = await fileToDataUrl(altPhotoFile.value)
        const extension = (altPhotoFile.value.name.split('.').pop() || 'jpg').toLowerCase()
        await dispatch('UPLOAD_IMAGE', {
          beneficiaryId: alternateNumber,
          beneficiaryType: 2,
          imageBase64: dataUrl,
          extension,
          organisationCode,
        })
      } catch (err) {
        toast.error(err instanceof Error
          ? `Alternate saved, but the photo failed to upload: ${err.message}`
          : 'Alternate saved, but the photo failed to upload')
        addAltDialog.value = false
        await load()
        return
      }
    }
    toast.success(editing ? 'Alternate updated' : 'Alternate added')
    addAltDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to save alternate')
  } finally {
    addingAlt.value = false
  }
}

onMounted(() => { load(); loadNameLookups() })
</script>

<template>
  <div>
    <div class="household-detail-header mb-4">
      <div class="household-detail-heading">
        <v-btn icon="mdi-arrow-left" variant="text" aria-label="Back to households" @click="goBack" />
        <v-avatar
          v-if="photoUrls.length"
          size="56"
          class="household-detail-avatar"
          role="button"
          tabindex="0"
          :aria-label="`View ${detail?.householdName ?? 'household head'}'s photo full size`"
          @click="openLightbox(photoUrls[0], detail?.householdName ?? 'household-head')"
          @keyup.enter="openLightbox(photoUrls[0], detail?.householdName ?? 'household-head')"
        >
          <v-img :src="photoUrls[0]" :alt="`${detail?.householdName ?? 'Household head'} photo`" cover />
        </v-avatar>
        <v-avatar v-else size="56" color="surface-variant">
          <v-icon icon="mdi-account-outline" size="28" />
        </v-avatar>
        <div>
          <h1 class="page-title">
            {{ detail?.householdName ?? 'Household' }}
          </h1>
          <div class="text-body-2 text-medium-emphasis">Household code: {{ householdNumber }}</div>
        </div>
      </div>
      <div v-if="detail" class="household-detail-actions">
        <v-chip :color="reviewStatusColor[reviewStatus]" variant="tonal">
          {{ reviewStatus }}
        </v-chip>
        <HouseholdReviewActions
          v-if="auth.can('ACCESS_HOUSEHOLDS')"
          :household-number="householdNumber"
          :household-name="detail.householdName ?? 'Household'"
          :review-status="detail.reviewStatus"
          @updated="load"
        />
        <v-btn
          v-if="auth.can('ACCESS_HOUSEHOLDS')"
          variant="tonal"
          prepend-icon="mdi-pencil-outline"
          @click="openEdit"
        >
          Edit
        </v-btn>
        <v-btn
          v-if="auth.can('ACCESS_VOUCHERS')"
          color="primary"
          variant="tonal"
          prepend-icon="mdi-printer"
          :loading="printing"
          @click="printVoucher"
        >
          Print Voucher
        </v-btn>
        <v-chip :color="detail.status === 1 ? 'success' : 'error'" variant="tonal">
          {{ detail.status === 1 ? 'Active' : 'Inactive' }}
        </v-chip>
      </div>
    </div>

    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <v-alert
      v-if="!loading && !detail"
      type="warning"
      variant="tonal"
      text="Household not found or not in your organisation."
    />

    <v-row v-if="detail">
      <v-col cols="12" md="8">
        <v-card variant="flat" border class="mb-4">
          <v-card-title class="text-subtitle-1 font-weight-bold">Household details</v-card-title>
          <v-divider />
          <v-card-text>
            <v-row dense>
              <v-col v-for="f in infoFields" :key="f.label" cols="12" sm="6">
                <div class="text-caption text-medium-emphasis">{{ f.label }}</div>
                <div class="text-body-1">{{ f.value }}</div>
              </v-col>
            </v-row>
          </v-card-text>
        </v-card>

      </v-col>

      <v-col cols="12" md="4">
        <v-card variant="flat" border class="mb-4">
          <v-card-title class="text-subtitle-1 font-weight-bold">Household head photo</v-card-title>
          <v-divider />
          <v-card-text>
            <div v-if="photoLoading" class="photo-loading" role="status">
              <v-progress-circular indeterminate color="primary" size="24" width="2" />
              <span>Loading captured photo...</span>
            </div>
            <v-row v-else-if="photoUrls.length" dense>
              <v-col v-for="(src, i) in photoUrls" :key="i" :cols="photoUrls.length === 1 ? 12 : 6">
                <v-img
                  :src="src"
                  :alt="`${detail?.householdName ?? 'Household head'} photo ${i + 1}`"
                  :aspect-ratio="photoUrls.length === 1 ? 4 / 3 : 1"
                  cover
                  class="rounded-lg clickable-photo"
                  role="button"
                  tabindex="0"
                  aria-label="View photo full size"
                  @click="openLightbox(src, detail?.householdName ?? 'household-head')"
                  @keyup.enter="openLightbox(src, detail?.householdName ?? 'household-head')"
                />
              </v-col>
            </v-row>
            <v-alert v-else-if="photoLoadError" type="error" variant="tonal" density="compact">
              <div class="d-flex align-center flex-wrap ga-2">
                <span>{{ photoLoadError }}</span>
                <v-btn size="small" variant="text" color="error" @click="retryPhotos">Try again</v-btn>
              </div>
            </v-alert>
            <div v-else class="text-medium-emphasis">No captured household head photo has synced yet.</div>
          </v-card-text>
        </v-card>

        <v-card variant="flat" border class="mb-4">
          <v-card-title class="text-subtitle-1 font-weight-bold">Biometrics</v-card-title>
          <v-divider />
          <v-card-text class="d-flex flex-column ga-3">
            <div class="biometric-row">
              <div>
                <div>Fingerprints</div>
                <div class="text-caption text-medium-emphasis">
                  {{ capturedFingerprintCount }} of 10 captured
                </div>
              </div>
              <div class="d-flex align-center ga-1">
                <v-chip size="small" :color="detail.fingerprintStatus === 'ENROLLED' ? 'success' : 'warning'" variant="tonal">
                  {{ detail.fingerprintStatus ?? 'PENDING' }}
                </v-chip>
                <v-btn size="small" variant="text" prepend-icon="mdi-eye-outline" @click="fingerprintDialog = true">
                  View
                </v-btn>
              </div>
            </div>
            <div class="biometric-row">
              <div>
                <div>Face</div>
                <div class="text-caption text-medium-emphasis">Secure face template</div>
              </div>
              <v-chip size="small" :color="detail.faceStatus === 'ENROLLED' ? 'success' : 'warning'" variant="tonal">
                {{ detail.faceStatus ?? 'PENDING' }}
              </v-chip>
            </div>
            <v-alert type="info" variant="tonal" density="compact" class="mt-1">
              Fingerprints and face templates are captured through the BioPay Android field app. The household head photo is displayed above when it has synced.
            </v-alert>
          </v-card-text>
        </v-card>

        <v-card variant="flat" border class="mb-4">
          <v-card-title class="text-subtitle-1 font-weight-bold d-flex align-center">
            Registration location
            <v-spacer />
            <v-btn v-if="coordinates" variant="text" size="small" prepend-icon="mdi-open-in-new" :href="mapLinkUrl" target="_blank" rel="noopener">
              Open map
            </v-btn>
          </v-card-title>
          <v-divider />
          <v-card-text>
            <template v-if="coordinates">
              <iframe
                class="household-map" :src="mapEmbedUrl" title="Household registration location with a marker at the captured device coordinates" loading="lazy"
                referrerpolicy="no-referrer-when-downgrade"
              />
              <div class="location-coordinates mt-3">
                <v-icon icon="mdi-map-marker-outline" color="primary" size="22" aria-hidden="true" />
                <div>
                  <div class="location-coordinate-values">
                    <span><strong>Latitude</strong> {{ coordinates.lat.toFixed(6) }}</span>
                    <span><strong>Longitude</strong> {{ coordinates.lon.toFixed(6) }}</span>
                  </div>
                  <div class="text-caption text-medium-emphasis">Captured from the field officer's device when this household was recorded.</div>
                </div>
              </div>
            </template>
            <div v-else class="location-empty">
              <v-icon icon="mdi-map-marker-outline" size="26" aria-hidden="true" />
              <span>No device coordinates were captured when this household was recorded.</span>
            </div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-row v-if="detail" class="mt-0">
      <v-col cols="12">
        <v-card v-if="auth.can('ACCESS_ALTERNATES')" variant="flat" border class="mb-4">
          <v-card-title class="alternates-card-title text-subtitle-1 font-weight-bold d-flex align-center">
            <span>Alternates ({{ alternates.length }})</span>
            <v-spacer />
            <div class="alternates-toolbar">
              <v-btn
                v-if="alternates.length && auth.can('DOWNLOAD_REPORTS')"
                size="small"
                variant="text"
                prepend-icon="mdi-download"
                :loading="exportingAlternates"
                @click="exportAlternates"
              >
                Export Excel
              </v-btn>
              <v-btn
                size="small"
                color="secondary"
                prepend-icon="mdi-account-plus-outline"
                @click="openAddAlternate"
              >
                Add alternate
              </v-btn>
            </div>
          </v-card-title>
          <v-divider />
          <v-data-table
            v-if="alternates.length"
            :headers="altTableHeaders"
            :items="alternates"
            item-value="alternateNumber"
            density="comfortable"
            class="alt-table"
          >
            <template #item.photo="{ item }">
              <v-avatar
                v-if="alternatePhotoUrls[item.alternateNumber ?? '']?.length"
                size="36"
                role="button"
                tabindex="0"
                :aria-label="`View ${item.alternateName ?? 'alternate'}'s photo full size`"
                @click="openLightbox(alternatePhotoUrls[item.alternateNumber ?? '']![0], item.alternateName ?? 'alternate', item.alternateNumber)"
                @keyup.enter="openLightbox(alternatePhotoUrls[item.alternateNumber ?? '']![0], item.alternateName ?? 'alternate', item.alternateNumber)"
              >
                <v-img :src="alternatePhotoUrls[item.alternateNumber ?? '']![0]" cover />
              </v-avatar>
              <v-avatar v-else size="36" color="surface-variant">
                <v-icon icon="mdi-account-child-outline" size="18" />
              </v-avatar>
            </template>
            <template #item.relationship="{ item }">{{ item.relationship || '—' }}</template>
            <template #item.gender="{ item }">{{ genderLabel(item.gender) }}</template>
            <template #item.createdAt="{ item }">{{ formatTimestamp(item.createdAt) }}</template>
            <template #item.status="{ item }">
              <v-chip size="small" :color="item.status === 1 ? 'success' : 'default'" variant="tonal">
                {{ item.status === 1 ? 'Active' : 'Inactive' }}
              </v-chip>
            </template>
            <template #item.actions="{ item }">
              <v-btn icon="mdi-eye-outline" variant="text" size="small" density="comfortable" aria-label="View alternate" @click="openViewAlternate(item)" />
              <v-btn v-if="item.status === 1" icon="mdi-pencil-outline" variant="text" size="small" density="comfortable" aria-label="Edit alternate" @click="openEditAlternate(item)" />
              <v-btn v-if="item.status === 1" icon="mdi-account-cancel-outline" variant="text" size="small" density="comfortable" color="error" aria-label="Deactivate alternate" @click="deactivateAlternate(item)" />
              <v-btn v-else icon="mdi-account-check-outline" variant="text" size="small" density="comfortable" color="success" aria-label="Activate alternate" @click="activateAlternate(item)" />
            </template>
          </v-data-table>
          <v-card-text v-else class="text-medium-emphasis">
            No alternates registered for this household.
          </v-card-text>
        </v-card>

        <v-card variant="flat" border>
          <v-card-title class="text-subtitle-1 font-weight-bold">Payment history</v-card-title>
          <v-divider />
          <v-data-table
            :headers="paymentTableHeaders"
            :items="payments"
            item-value="id"
            :items-per-page="5"
            :items-per-page-options="[5, 10, 25]"
            density="comfortable"
            class="history-table"
            no-data-text="No payments recorded for this household."
          >
            <template #item.id="{ item }">{{ item.id ?? '—' }}</template>
            <template #item.cycle="{ item }">{{ item.cycle || '—' }}</template>
            <template #item.amount="{ item }"><span class="history-amount">{{ formatCurrency(item.amount) }}</span></template>
            <template #item.status="{ item }">
              <v-chip size="small" :color="paymentStatusColor(item.status)" variant="tonal">
                {{ paymentStatusLabel(item.status) }}
              </v-chip>
            </template>
            <template #item.createdAt="{ item }">{{ formatTimestamp(item.createdAt) }}</template>
          </v-data-table>
        </v-card>

        <v-card variant="flat" border class="mt-4">
          <v-card-title class="text-subtitle-1 font-weight-bold">Voucher history</v-card-title>
          <v-divider />
          <v-data-table
            :headers="voucherTableHeaders"
            :items="vouchers"
            item-value="voucherCode"
            :items-per-page="5"
            :items-per-page-options="[5, 10, 25]"
            density="comfortable"
            class="history-table"
            no-data-text="No vouchers issued to this household."
          >
            <template #item.voucherCode="{ item }">{{ item.voucherCode || '—' }}</template>
            <template #item.purpose="{ item }">{{ item.purpose || '—' }}</template>
            <template #item.amount="{ item }"><span class="history-amount">{{ formatCurrency(item.amount) }}</span></template>
            <template #item.status="{ item }">
              <v-chip size="small" :color="voucherStatusColor(item.status)" variant="tonal">
                {{ item.status ?? 'ISSUED' }}
              </v-chip>
            </template>
            <template #item.createdAt="{ item }">{{ formatTimestamp(item.createdAt) }}</template>
            <template #item.expiresAt="{ item }">{{ formatTimestamp(item.expiresAt) }}</template>
            <template #item.redemptionStatus="{ item }">
              <div class="redemption-state">
                <v-chip size="small" :color="voucherRedemptionState(item).color" variant="tonal">
                  {{ voucherRedemptionState(item).label }}
                </v-chip>
                <span v-if="item.redeemedAt" class="text-caption text-medium-emphasis">
                  {{ formatTimestamp(item.redeemedAt) }}
                </span>
              </div>
            </template>
          </v-data-table>
        </v-card>
      </v-col>
    </v-row>

    <v-dialog :model-value="!!lightboxSrc" max-width="720" @update:model-value="lightboxSrc = null; lightboxContext = null">
      <v-card v-if="lightboxSrc">
        <dialog-close-button @close="lightboxSrc = null; lightboxContext = null" />
        <v-card-title class="photo-lightbox-header d-flex align-center">
          Photo
          <v-spacer />
          <v-btn
            class="photo-lightbox-download"
            color="secondary"
            variant="flat"
            size="small"
            prepend-icon="mdi-download"
            :loading="photoDownloading"
            @click="downloadLightboxPhoto"
          >
            Download
          </v-btn>
        </v-card-title>
        <v-img :src="lightboxSrc" :alt="`${detail?.householdName ?? 'Household head'} photo`" max-height="80vh" contain />
      </v-card>
    </v-dialog>

    <v-dialog v-model="fingerprintDialog" max-width="680">
      <v-card>
        <v-card-title class="d-flex align-center">
          Captured fingerprints
          <v-spacer />
        </v-card-title>
        <dialog-close-button @close="fingerprintDialog = false" />
        <v-divider />
        <v-card-text class="pt-4">
          <p class="text-body-2 text-medium-emphasis mb-4">
            {{ capturedFingerprintCount }} of 10 finger positions have been captured for {{ detail?.householdName }}.
          </p>
          <section v-for="hand in ['Right hand', 'Left hand']" :key="hand" class="fingerprint-hand mb-4" :aria-label="hand">
            <h3 class="text-subtitle-2 mb-2">{{ hand }}</h3>
            <div class="fingerprint-grid">
              <div
                v-for="finger in fingerPositions.filter((item) => item.hand === hand)"
                :key="finger.number"
                class="fingerprint-slot"
                :class="{ 'fingerprint-slot--captured': capturedFingerNumbers.has(finger.number) }"
              >
                <v-icon icon="mdi-fingerprint" size="30" aria-hidden="true" />
                <span>{{ finger.label }}</span>
                <small>{{ capturedFingerNumbers.has(finger.number) ? 'Captured' : 'Not captured' }}</small>
              </div>
            </div>
          </section>
          <v-alert type="info" variant="tonal" density="compact">
            Biometric templates stay protected. This view shows capture coverage only, not the stored fingerprint data.
          </v-alert>
        </v-card-text>
      </v-card>
    </v-dialog>

    <v-dialog v-model="editDialog" max-width="760">
      <v-card>
        <v-card-title class="d-flex align-center">
          Edit household
          <v-spacer />
        </v-card-title>
        <dialog-close-button @close="editDialog = false" />
        <v-divider />
        <v-card-text class="pt-4">
          <v-text-field v-model="editForm.householdName" label="Head of household name" />
          <v-row>
            <v-col cols="6" sm="4"><v-date-input v-model="editForm.dateOfBirth" label="Date of birth" :max="new Date()" clearable /></v-col>
            <v-col cols="6" sm="4">
              <v-select v-model="editForm.gender" label="Gender" :items="['M', 'F']" />
            </v-col>
            <v-col cols="12" sm="4"><v-text-field v-model.number="editForm.householdSize" label="Household size" type="number" /></v-col>
          </v-row>
          <v-row>
            <v-col cols="6" sm="4">
              <v-select v-model="editForm.maritalStatus" label="Marital status" :items="maritalStatusItems" clearable />
            </v-col>
            <v-col cols="6" sm="4"><v-text-field v-model="editForm.spouseName" label="Spouse name" /></v-col>
            <v-col cols="12" sm="4"><v-text-field v-model="editForm.phoneNumber" label="Phone number" /></v-col>
          </v-row>
          <v-row>
            <v-col cols="12" sm="4">
              <v-select
                v-model="editForm.legalStatus"
                :items="LEGAL_STATUS_OPTIONS"
                item-title="title"
                item-value="value"
                label="Legal status"
                clearable
              />
            </v-col>
            <v-col cols="12" sm="4">
              <v-select
                v-model="editForm.vulnerabilityStatuses"
                :items="VULNERABILITY_OPTIONS"
                item-title="title"
                item-value="value"
                label="Vulnerability categories"
                hint="Select every support need that applies"
                persistent-hint
                multiple
                chips
                closable-chips
              />
            </v-col>
            <v-col cols="12" sm="4">
              <v-file-input
                label="Household head photo"
                :hint="detail?.images?.length ? 'Choose a new photo to replace the current one' : 'Attach a photo of the household head'"
                persistent-hint
                accept="image/*"
                prepend-icon="mdi-camera-outline"
                show-size
                density="compact"
                clearable
                :loading="uploadingEditPhoto"
                @change="onEditPhotoFile"
              />
            </v-col>
          </v-row>
          <div class="text-caption text-medium-emphasis mt-2 mb-1">Location</div>
          <v-row dense>
            <v-col cols="6" sm="3"><v-select v-model="editForm.stateCode" :items="states" item-title="name" item-value="code" label="State" density="compact" @update:model-value="onEditStateChange" /></v-col>
            <v-col cols="6" sm="3"><v-select v-model="editForm.countyCode" :items="countiesForState(editForm.stateCode)" item-title="name" item-value="code" label="County" density="compact" @update:model-value="onEditCountyChange" /></v-col>
            <v-col cols="6" sm="3"><v-select v-model="editForm.locationCode" :items="locationsForCounty(editForm.countyCode)" item-title="name" item-value="code" label="Location" density="compact" @update:model-value="onEditLocationChange" /></v-col>
            <v-col cols="6" sm="3"><v-select v-model="editForm.villageCode" :items="villagesForLocation(editForm.locationCode)" item-title="name" item-value="code" label="Village" density="compact" /></v-col>
          </v-row>
        </v-card-text>
        <v-card-actions class="pa-4 pt-0">
          <v-spacer />
          <v-btn variant="flat" color="error" @click="editDialog = false">Cancel</v-btn>
          <v-btn variant="flat" color="secondary" :loading="editing" @click="saveEdit">Save</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="addAltDialog" max-width="520">
      <v-card>
        <v-card-title class="d-flex align-center">
          {{ editingAlternateNumber ? 'Edit alternate' : 'Add alternate' }}
          <v-spacer />
        </v-card-title>
        <dialog-close-button @close="addAltDialog = false" />
        <v-divider />
        <v-card-text class="d-flex flex-column ga-3 pt-4">
          <v-text-field v-model="altForm.alternateName" label="Full name" hide-details density="compact" />
          <v-select
            v-model="altForm.relationship"
            :items="alternateRelationshipItems"
            label="Relationship to household head"
            hide-details
            density="compact"
            @update:model-value="onAltRelationshipChange"
          />
          <div class="d-flex ga-3 alt-form-row">
            <v-date-input v-model="altForm.dateOfBirth" label="Date of birth" :max="new Date()" hide-details density="compact" clearable />
            <v-select
              v-model="altForm.gender"
              :items="[{ title: 'Male', value: 'M' }, { title: 'Female', value: 'F' }]"
              label="Gender"
              clearable
              hide-details
              density="compact"
            />
          </div>
          <v-text-field v-model="altForm.phoneNumber" label="Phone number" hide-details density="compact" />
          <v-file-input
            label="Attach photo (optional)"
            accept="image/*"
            prepend-icon="mdi-camera-outline"
            hide-details
            density="compact"
            @change="onAltPhotoFile"
          />
        </v-card-text>
        <v-card-actions class="pa-4 pt-0">
          <v-spacer />
          <v-btn variant="flat" color="error" @click="addAltDialog = false">Cancel</v-btn>
          <v-btn variant="flat" color="secondary" :loading="addingAlt" @click="saveAlternate">Save</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="altFingerprintDialog" max-width="680">
      <v-card>
        <v-card-title class="d-flex align-center">
          Captured fingerprints
          <v-spacer />
        </v-card-title>
        <dialog-close-button @close="altFingerprintDialog = false" />
        <v-divider />
        <v-card-text class="pt-4">
          <p class="text-body-2 text-medium-emphasis mb-4">
            {{ altCapturedFingerprintCount }} of 10 finger positions have been captured for {{ viewAltTarget?.alternateName }}.
          </p>
          <section v-for="hand in ['Right hand', 'Left hand']" :key="hand" class="fingerprint-hand mb-4" :aria-label="hand">
            <h3 class="text-subtitle-2 mb-2">{{ hand }}</h3>
            <div class="fingerprint-grid">
              <div
                v-for="finger in fingerPositions.filter((item) => item.hand === hand)"
                :key="finger.number"
                class="fingerprint-slot"
                :class="{ 'fingerprint-slot--captured': altCapturedFingerNumbers.has(finger.number) }"
              >
                <v-icon icon="mdi-fingerprint" size="30" aria-hidden="true" />
                <span>{{ finger.label }}</span>
                <small>{{ altCapturedFingerNumbers.has(finger.number) ? 'Captured' : 'Not captured' }}</small>
              </div>
            </div>
          </section>
          <v-alert type="info" variant="tonal" density="compact">
            Biometric templates stay protected. This view shows capture coverage only, not the stored fingerprint data.
          </v-alert>
        </v-card-text>
      </v-card>
    </v-dialog>

    <v-dialog v-model="viewAltDialog" max-width="480">
      <v-card v-if="viewAltTarget">
        <v-card-title class="d-flex align-center">
          {{ viewAltTarget.alternateName }}
          <v-spacer />
        </v-card-title>
        <dialog-close-button @close="viewAltDialog = false" />
        <v-divider />
        <v-card-text class="d-flex flex-column ga-3 pt-4">
          <div v-if="(alternatePhotoUrls[viewAltTarget.alternateNumber ?? ''] ?? []).length" class="alternate-photo-grid">
            <v-img
              v-for="(src, i) in alternatePhotoUrls[viewAltTarget.alternateNumber ?? '']"
              :key="i"
              :src="src"
              width="112"
              height="112"
              cover
              class="alternate-view-photo rounded-lg clickable-photo"
              role="button"
              tabindex="0"
              aria-label="View photo full size"
              @click="openLightbox(src, viewAltTarget?.alternateName ?? 'alternate', viewAltTarget?.alternateNumber)"
              @keyup.enter="openLightbox(src, viewAltTarget?.alternateName ?? 'alternate', viewAltTarget?.alternateNumber)"
            />
          </div>
          <div v-else class="alternate-photo-empty">
            <v-icon icon="mdi-account-child-outline" size="28" />
            <span>No photo captured for this alternate.</span>
          </div>
          <v-row dense>
            <v-col cols="6">
              <div class="text-caption text-medium-emphasis">Relationship</div>
              <div class="text-body-1">{{ viewAltTarget.relationship || '—' }}</div>
            </v-col>
            <v-col cols="6">
              <div class="text-caption text-medium-emphasis">Gender</div>
              <div class="text-body-1">{{ genderLabel(viewAltTarget.gender) }}</div>
            </v-col>
            <v-col cols="6">
              <div class="text-caption text-medium-emphasis">Age</div>
              <div class="text-body-1">{{ viewAltTarget.age ?? '—' }}</div>
            </v-col>
            <v-col cols="6">
              <div class="text-caption text-medium-emphasis">Phone number</div>
              <div class="text-body-1">{{ viewAltTarget.phoneNumber || '—' }}</div>
            </v-col>
            <v-col cols="12">
              <div class="text-caption text-medium-emphasis">Added</div>
              <div class="text-body-1">{{ formatTimestamp(viewAltTarget.createdAt) }}</div>
            </v-col>
            <v-col cols="12">
              <div class="text-caption text-medium-emphasis">Status</div>
              <v-chip size="small" :color="viewAltTarget.status === 1 ? 'success' : 'default'" variant="tonal">
                {{ viewAltTarget.status === 1 ? 'Active' : 'Inactive' }}
              </v-chip>
            </v-col>
          </v-row>
          <v-divider />
          <div class="text-subtitle-2 font-weight-bold">Biometrics</div>
          <div class="biometric-row">
            <div>
              <div>Fingerprints</div>
              <div class="text-caption text-medium-emphasis">
                {{ altCapturedFingerprintCount }} of 10 captured
              </div>
            </div>
            <div class="d-flex align-center ga-1">
              <v-chip size="small" :color="viewAltTarget.fingerprintStatus === 'ENROLLED' ? 'success' : 'warning'" variant="tonal">
                {{ viewAltTarget.fingerprintStatus ?? 'PENDING' }}
              </v-chip>
              <v-btn size="small" variant="text" prepend-icon="mdi-eye-outline" @click="altFingerprintDialog = true">
                View
              </v-btn>
            </div>
          </div>
          <div class="biometric-row">
            <div>
              <div>Face</div>
              <div class="text-caption text-medium-emphasis">Secure face template</div>
            </div>
            <v-chip size="small" :color="viewAltTarget.faceStatus === 'ENROLLED' ? 'success' : 'warning'" variant="tonal">
              {{ viewAltTarget.faceStatus ?? 'PENDING' }}
            </v-chip>
          </div>
        </v-card-text>
        <v-card-actions class="pa-4 pt-0">
          <v-spacer />
          <v-btn v-if="viewAltTarget.status === 1" variant="flat" color="secondary" @click="viewAltDialog = false; openEditAlternate(viewAltTarget!)">Edit</v-btn>
          <v-btn v-else variant="flat" color="secondary" prepend-icon="mdi-account-check-outline" @click="viewAltDialog = false; activateAlternate(viewAltTarget!)">Activate</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
/* Age and Gender share one row and must be exactly the same size -- give both
 * an equal flex basis instead of letting either grow from its own content/hint. */
.alt-form-row > * {
  flex: 1 1 0;
  min-width: 0;
}

.household-map {
  width: 100%;
  height: 260px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}

.household-detail-header,
.household-detail-heading,
.household-detail-actions {
  display: flex;
  align-items: center;
}

.household-detail-header {
  gap: 16px;
  flex-wrap: wrap;
}

.household-detail-heading {
  gap: 12px;
  flex: 1 1 260px;
  min-width: 0;
}

.household-detail-actions {
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
  margin-left: auto;
}

@media (max-width: 700px) {
  .household-detail-actions {
    width: 100%;
    justify-content: flex-start;
  }
}

.household-detail-avatar,
.clickable-photo {
  cursor: pointer;
}

.alt-table :deep(table) {
  min-width: 936px;
  table-layout: fixed;
}

.history-table :deep(table) {
  min-width: 760px;
  table-layout: fixed;
}

.alt-table :deep(th),
.alt-table :deep(td),
.history-table :deep(th),
.history-table :deep(td) {
  padding-inline: 20px;
}

.history-amount {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.redemption-state {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  font-variant-numeric: tabular-nums;
}

.alternates-card-title,
.alternates-toolbar {
  gap: 8px;
}

.alternates-card-title {
  flex-wrap: wrap;
}

.alternates-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
}

@media (max-width: 600px) {
  .alternates-card-title > .v-spacer {
    display: none;
  }

  .alternates-toolbar {
    width: 100%;
    justify-content: flex-start;
  }
}

.photo-lightbox-header {
  min-height: 58px;
}

.photo-lightbox-download {
  margin-right: 40px;
}

.photo-loading {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  color: #64748b;
  font-size: .875rem;
}

.location-coordinates,
.location-empty {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.location-coordinate-values {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 18px;
  color: #334155;
  font-size: .875rem;
  font-variant-numeric: tabular-nums;
}

.location-coordinate-values strong {
  margin-right: 4px;
  color: #64748b;
  font-size: .75rem;
  font-weight: 600;
}

.location-empty {
  align-items: center;
  min-height: 52px;
  color: #64748b;
}

.biometric-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.alternate-photo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(112px, 112px));
  gap: 10px;
}

.alternate-view-photo {
  flex: 0 0 112px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.alternate-photo-empty {
  display: flex;
  min-height: 96px;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 1px dashed #cbd5e1;
  border-radius: 10px;
  color: #64748b;
  background: #f8fafc;
}

.fingerprint-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.fingerprint-slot {
  display: flex;
  min-width: 0;
  min-height: 112px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 10px 6px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  color: #64748b;
  text-align: center;
}

.fingerprint-slot span {
  color: #334155;
  font-size: .8rem;
  font-weight: 600;
}

.fingerprint-slot small {
  font-size: .68rem;
}

.fingerprint-slot--captured {
  border-color: #99f6e4;
  background: #f0fdfa;
  color: #0f766e;
}

.fingerprint-slot--captured span,
.fingerprint-slot--captured small {
  color: #0f766e;
}

@media (max-width: 520px) {
  .fingerprint-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .biometric-row {
    align-items: flex-start;
  }
}
</style>
