<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiClient, dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { downloadCsv, toCsv } from '@/utils/csv'

// Dedicated, full-page household view (replaces the old popup dialog). Renders
// every field the GET_HOUSEHOLD / GET_ALTERNATES endpoints currently return.
// Fields not yet exposed by the backend (marital status, ID number, dependants,
// captured images, payment/audit history) are tracked in progress.md as backend
// follow-ups and intentionally not faked here.

interface Alternate {
  alternateNumber?: string
  alternateName?: string
  relationship?: string
  phoneNumber?: string
  age?: number
  gender?: string
  images?: string[]
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToast()

interface GeoNode { code: string; name: string; stateCode?: string; countyCode?: string; locationCode?: string }

interface PaymentEvent {
  id?: number
  amount?: number
  status?: number
  cycle?: string
  createdAt?: string
}
interface AuditEvent {
  action?: string
  entityType?: string
  details?: string
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
const events = ref<AuditEvent[]>([])
const vouchers = ref<VoucherEvent[]>([])
// Object URLs for photos fetched (with the auth header) through apiClient as blobs --
// a plain image URL can't reach the JWT-protected /files route.
const photoUrls = ref<string[]>([])
// Same blob-fetch pattern, keyed by alternateNumber, for each alternate's own gallery.
const alternatePhotoUrls = ref<Record<string, string[]>>({})

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

const genderLabel = (g?: string) => (g === 'M' ? 'Male' : g === 'F' ? 'Female' : (g || '—'))

const infoFields = computed(() => {
  const d = detail.value
  if (!d) return []
  return [
    { label: 'Household number', value: d.householdNumber },
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
    { label: 'Vulnerability status', value: d.vulnerabilityStatus || '—' },
    { label: 'Legal status', value: d.legalStatus || '—' },
    { label: 'Review status', value: d.reviewStatus || 'PENDING' },
    { label: 'State', value: stateNameByCode.value.get(d.stateCode) || d.stateCode || '—' },
    { label: 'County', value: countyNameByCode.value.get(d.countyCode) || d.countyCode || '—' },
    { label: 'Location', value: locationNameByCode.value.get(d.payamCode) || d.payamCode || '—' },
    { label: 'Village', value: villageNameByCode.value.get(d.bomaCode) || d.bomaCode || '—' },
    { label: 'Coordinates', value: d.latitude && d.longitude ? `${d.latitude}, ${d.longitude}` : '—' },
    { label: 'Registered', value: d.createdAt || '—' },
    { label: 'Last updated', value: d.updatedAt || '—' },
  ]
})

function revokePhotos() {
  for (const u of photoUrls.value) URL.revokeObjectURL(u)
  photoUrls.value = []
}

function revokeAlternatePhotos() {
  for (const urls of Object.values(alternatePhotoUrls.value)) {
    for (const u of urls) URL.revokeObjectURL(u)
  }
  alternatePhotoUrls.value = {}
}

// Fetches each JWT-protected photo through apiClient (which attaches the bearer
// token) and turns the blob into a displayable object URL. Shared by the household
// head's own gallery and every alternate's gallery below.
async function fetchPhotoBlobs(paths: string[]): Promise<string[]> {
  const urls: string[] = []
  for (const p of paths) {
    try {
      const rel = String(p).replace(/^\/biopay/, '')
      const res = await apiClient.get(rel, { responseType: 'blob' })
      urls.push(URL.createObjectURL(res.data as Blob))
    } catch {
      // Skip an image that fails to load rather than failing the whole page.
    }
  }
  return urls
}

async function loadPhotos(paths: string[]) {
  revokePhotos()
  photoUrls.value = await fetchPhotoBlobs(paths)
}

async function loadAlternatePhotos() {
  revokeAlternatePhotos()
  const withPhotos = alternates.value.filter((a) => a.alternateNumber && a.images?.length)
  const entries = await Promise.all(
    withPhotos.map(async (a) => [a.alternateNumber as string, await fetchPhotoBlobs(a.images ?? [])] as const),
  )
  alternatePhotoUrls.value = Object.fromEntries(entries)
}

async function load() {
  loading.value = true
  try {
    const [h, alts, hist] = await Promise.all([
      dispatch<{ results: any[] }>('GET_HOUSEHOLD', { householdNumber: householdNumber.value }),
      auth.can('ACCESS_ALTERNATES')
        ? dispatch<{ results: Alternate[] }>('GET_ALTERNATES', { householdNumber: householdNumber.value })
        : Promise.resolve({ results: [] as Alternate[] }),
      dispatch<{ results: { payments: PaymentEvent[]; events: AuditEvent[]; vouchers: VoucherEvent[] } }>(
        'GET_HOUSEHOLD_HISTORY', { householdNumber: householdNumber.value },
      ),
    ])
    detail.value = h.results?.[0] ?? null
    alternates.value = alts.results ?? []
    payments.value = hist.results?.payments ?? []
    events.value = hist.results?.events ?? []
    vouchers.value = hist.results?.vouchers ?? []
    const images: string[] = detail.value?.images ?? []
    if (images.length) loadPhotos(images)
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
        * { box-sizing: border-box; font-family: "Segoe UI", sans-serif; }
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
              <div class="row"><span class="label">Household #:</span> ${escapeHtml(v.householdNumber)}</div>
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

// ---- Edit household (UPDATE_HOUSEHOLD already existed on the backend; this wires
// it into the UI, which previously offered no way to correct a household's own
// details after registration). ------------------------------------------------

const editDialog = ref(false)
const editing = ref(false)
const editForm = ref({
  householdName: '', age: null as number | null, gender: '', maritalStatus: '', phoneNumber: '',
  householdSize: null as number | null, stateCode: '', countyCode: '', locationCode: '', villageCode: '',
})

// Bound to each geo select's own change event (not a watcher on the whole form),
// so resetting dependent fields only happens when the user actively picks a new
// parent value -- never when openEdit() below populates the form in one go.
function onEditStateChange() { editForm.value.countyCode = ''; editForm.value.locationCode = ''; editForm.value.villageCode = '' }
function onEditCountyChange() { editForm.value.locationCode = ''; editForm.value.villageCode = '' }
function onEditLocationChange() { editForm.value.villageCode = '' }

function openEdit() {
  const d = detail.value
  if (!d) return
  editForm.value = {
    householdName: d.householdName ?? '',
    age: d.age ?? null,
    gender: d.gender ?? '',
    maritalStatus: d.maritalStatus ?? '',
    phoneNumber: d.phoneNumber ?? '',
    householdSize: d.householdSize ?? null,
    stateCode: d.stateCode ?? '',
    countyCode: d.countyCode ?? '',
    locationCode: d.payamCode ?? '',
    villageCode: d.bomaCode ?? '',
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
      age: editForm.value.age ?? undefined,
      gender: editForm.value.gender || undefined,
      maritalStatus: editForm.value.maritalStatus || undefined,
      phoneNumber: editForm.value.phoneNumber || undefined,
      householdSize: editForm.value.householdSize ?? undefined,
      bomaCode: editForm.value.villageCode || undefined,
    })
    toast.success('Household updated')
    editDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to update household')
  } finally {
    editing.value = false
  }
}

// Exports this household's alternates to CSV.
function exportAlternates() {
  if (!alternates.value.length) {
    toast.error('No alternates to export')
    return
  }
  const csv = toCsv(
    ['Alternate #', 'Name', 'Relationship', 'Phone'],
    alternates.value.map((a) => [
      a.alternateNumber ?? '', a.alternateName ?? '', a.relationship ?? '', a.phoneNumber ?? '',
    ]),
  )
  downloadCsv(`alternates-${householdNumber.value}.csv`, csv)
}

// ---- Add alternate (+ optional photo) -----------------------------------------
// Creates the alternate via CREATE_ALTERNATE (already anchor/org scoped, same as
// CREATE_HOUSEHOLD), then -- if a photo was picked -- uploads it via the existing
// mobile-facing UPLOAD_IMAGE code, keyed to the new alternateNumber with
// beneficiaryType 2 (alternate), matching the app-wide 1=head/2=alternate
// convention. Nothing new is invented on the backend for either step.

const addAltDialog = ref(false)
const addingAlt = ref(false)
const altForm = ref({ alternateName: '', relationship: '', phoneNumber: '', gender: '', age: null as number | null })
const altPhotoFile = ref<File | null>(null)

function openAddAlternate() {
  altForm.value = { alternateName: '', relationship: '', phoneNumber: '', gender: '', age: null }
  altPhotoFile.value = null
  addAltDialog.value = true
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
    const created = await dispatch<{ alternateNumber: string }>('CREATE_ALTERNATE', {
      householdNumber: householdNumber.value,
      organisationCode,
      alternateName: altForm.value.alternateName.trim(),
      relationship: altForm.value.relationship || undefined,
      phoneNumber: altForm.value.phoneNumber || undefined,
      gender: altForm.value.gender || undefined,
      age: altForm.value.age ?? undefined,
    })
    if (altPhotoFile.value) {
      try {
        const dataUrl = await fileToDataUrl(altPhotoFile.value)
        const extension = (altPhotoFile.value.name.split('.').pop() || 'jpg').toLowerCase()
        await dispatch('UPLOAD_IMAGE', {
          beneficiaryId: created.alternateNumber,
          beneficiaryType: 2,
          imageBase64: dataUrl,
          extension,
          organisationCode,
        })
      } catch (err) {
        toast.error(err instanceof Error
          ? `Alternate added, but the photo failed to upload: ${err.message}`
          : 'Alternate added, but the photo failed to upload')
        addAltDialog.value = false
        await load()
        return
      }
    }
    toast.success('Alternate added')
    addAltDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to add alternate')
  } finally {
    addingAlt.value = false
  }
}

onMounted(() => { load(); loadNameLookups() })
</script>

<template>
  <div>
    <div class="d-flex align-center mb-4 ga-3">
      <v-btn icon="mdi-arrow-left" variant="text" aria-label="Back to households" @click="goBack" />
      <div>
        <h1 class="text-h5 font-weight-bold mb-0">
          {{ detail?.householdName ?? 'Household' }}
        </h1>
        <div class="text-body-2 text-medium-emphasis">{{ householdNumber }}</div>
      </div>
      <v-spacer />
      <v-btn
        v-if="detail && auth.can('ACCESS_HOUSEHOLDS')"
        variant="tonal"
        prepend-icon="mdi-pencil-outline"
        class="mr-3"
        @click="openEdit"
      >
        Edit
      </v-btn>
      <v-btn
        v-if="detail && auth.can('ACCESS_VOUCHERS')"
        color="primary"
        variant="tonal"
        prepend-icon="mdi-printer"
        :loading="printing"
        class="mr-3"
        @click="printVoucher"
      >
        Print Voucher
      </v-btn>
      <v-chip
        v-if="detail"
        :color="detail.status === 1 ? 'success' : 'error'"
        variant="tonal"
      >
        {{ detail.status === 1 ? 'Active' : 'Inactive' }}
      </v-chip>
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

        <v-card v-if="auth.can('ACCESS_ALTERNATES')" variant="flat" border>
          <v-card-title class="text-subtitle-1 font-weight-bold d-flex align-center">
            Alternates ({{ alternates.length }})
            <v-spacer />
            <v-btn
              v-if="alternates.length && auth.can('DOWNLOAD_REPORTS')"
              size="small"
              variant="text"
              prepend-icon="mdi-download"
              class="mr-2"
              @click="exportAlternates"
            >
              Export
            </v-btn>
            <v-btn v-if="auth.can('ACCESS_ALTERNATES')"
              size="small"
              color="secondary"
              prepend-icon="mdi-account-plus-outline"
              @click="openAddAlternate"
            >
              Add alternate
            </v-btn>
          </v-card-title>
          <v-divider />
          <v-list v-if="alternates.length">
            <template v-for="a in alternates" :key="a.alternateNumber ?? a.alternateName">
              <v-list-item
                :title="a.alternateName"
                :subtitle="[a.relationship, a.phoneNumber].filter(Boolean).join(' · ') || undefined"
              >
                <template #prepend>
                  <v-avatar v-if="alternatePhotoUrls[a.alternateNumber ?? '']?.length" size="40">
                    <v-img :src="alternatePhotoUrls[a.alternateNumber ?? '']![0]" cover />
                  </v-avatar>
                  <v-icon v-else icon="mdi-account-child-outline" />
                </template>
              </v-list-item>
              <div
                v-if="(alternatePhotoUrls[a.alternateNumber ?? ''] ?? []).length > 1"
                class="px-4 pb-3 d-flex ga-2 flex-wrap"
              >
                <v-img
                  v-for="(src, i) in (alternatePhotoUrls[a.alternateNumber ?? ''] ?? []).slice(1)"
                  :key="i"
                  :src="src"
                  width="56"
                  height="56"
                  cover
                  class="rounded-lg"
                />
              </div>
            </template>
          </v-list>
          <v-card-text v-else class="text-medium-emphasis">
            No alternates registered for this household.
          </v-card-text>
        </v-card>

        <v-card variant="flat" border class="mt-4">
          <v-card-title class="text-subtitle-1 font-weight-bold">Payment history</v-card-title>
          <v-divider />
          <v-list v-if="payments.length">
            <v-list-item
              v-for="p in payments"
              :key="p.id"
              :title="`${(p.amount ?? 0).toLocaleString()}${p.cycle ? ' · ' + p.cycle : ''}`"
              :subtitle="p.createdAt || undefined"
              prepend-icon="mdi-cash"
            >
              <template #append>
                <v-chip size="small" :color="p.status === 1 ? 'success' : 'warning'" variant="tonal">
                  {{ p.status === 1 ? 'Paid' : 'Pending' }}
                </v-chip>
              </template>
            </v-list-item>
          </v-list>
          <v-card-text v-else class="text-medium-emphasis">No payments recorded for this household.</v-card-text>
        </v-card>

        <v-card variant="flat" border class="mt-4">
          <v-card-title class="text-subtitle-1 font-weight-bold">Voucher history</v-card-title>
          <v-divider />
          <v-list v-if="vouchers.length">
            <v-list-item
              v-for="v in vouchers"
              :key="v.voucherCode"
              :title="`${(v.amount ?? 0).toLocaleString()}${v.purpose ? ' · ' + v.purpose : ''}`"
              :subtitle="[v.voucherCode, v.status === 'REDEEMED' ? v.redeemedAt : v.createdAt].filter(Boolean).join(' · ') || undefined"
              prepend-icon="mdi-ticket-confirmation-outline"
            >
              <template #append>
                <v-chip
                  size="small"
                  variant="tonal"
                  :color="v.status === 'REDEEMED' ? 'success' : v.status === 'VOID' ? 'error' : 'warning'"
                >
                  {{ v.status ?? 'ISSUED' }}
                </v-chip>
              </template>
            </v-list-item>
          </v-list>
          <v-card-text v-else class="text-medium-emphasis">No vouchers issued to this household.</v-card-text>
        </v-card>

        <v-card variant="flat" border class="mt-4">
          <v-card-title class="text-subtitle-1 font-weight-bold">Audit history</v-card-title>
          <v-divider />
          <v-list v-if="events.length">
            <v-list-item
              v-for="(e, i) in events"
              :key="i"
              :title="e.action"
              :subtitle="[e.entityType, e.createdAt].filter(Boolean).join(' · ') || undefined"
              prepend-icon="mdi-history"
            />
          </v-list>
          <v-card-text v-else class="text-medium-emphasis">No audit events recorded for this household.</v-card-text>
        </v-card>
      </v-col>

      <v-col cols="12" md="4">
        <v-card variant="flat" border class="mb-4">
          <v-card-title class="text-subtitle-1 font-weight-bold">Photos</v-card-title>
          <v-divider />
          <v-card-text>
            <v-row v-if="photoUrls.length" dense>
              <v-col v-for="(src, i) in photoUrls" :key="i" cols="6">
                <v-img :src="src" aspect-ratio="1" cover class="rounded-lg" />
              </v-col>
            </v-row>
            <div v-else class="text-medium-emphasis">No photos uploaded for this household.</div>
          </v-card-text>
        </v-card>

        <v-card variant="flat" border class="mb-4">
          <v-card-title class="text-subtitle-1 font-weight-bold">Biometrics</v-card-title>
          <v-divider />
          <v-card-text class="d-flex flex-column ga-3">
            <div class="d-flex align-center justify-space-between">
              <span>Fingerprints</span>
              <v-chip size="small" :color="detail.fingerprintStatus === 'ENROLLED' ? 'success' : 'warning'" variant="tonal">
                {{ detail.fingerprintStatus ?? 'PENDING' }}
              </v-chip>
            </div>
            <div class="d-flex align-center justify-space-between">
              <span>Photo</span>
              <v-chip size="small" :color="detail.imageStatus === 'UPLOADED' ? 'success' : 'warning'" variant="tonal">
                {{ detail.imageStatus ?? 'PENDING' }}
              </v-chip>
            </div>
            <v-alert type="info" variant="tonal" density="compact" class="mt-1">
              Fingerprints and photos are captured through the BioPay Android field app.
            </v-alert>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-dialog v-model="editDialog" max-width="560">
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
            <v-col cols="6"><v-text-field v-model.number="editForm.age" label="Age" type="number" /></v-col>
            <v-col cols="6">
              <v-select v-model="editForm.gender" label="Gender" :items="['M', 'F']" />
            </v-col>
          </v-row>
          <v-text-field v-model="editForm.maritalStatus" label="Marital status" />
          <v-text-field v-model="editForm.phoneNumber" label="Phone number" />
          <v-text-field v-model.number="editForm.householdSize" label="Household size" type="number" />
          <div class="text-caption text-medium-emphasis mt-2 mb-1">Location</div>
          <v-row dense>
            <v-col cols="6"><v-select v-model="editForm.stateCode" :items="states" item-title="name" item-value="code" label="State" density="compact" @update:model-value="onEditStateChange" /></v-col>
            <v-col cols="6"><v-select v-model="editForm.countyCode" :items="countiesForState(editForm.stateCode)" item-title="name" item-value="code" label="County" density="compact" @update:model-value="onEditCountyChange" /></v-col>
            <v-col cols="6"><v-select v-model="editForm.locationCode" :items="locationsForCounty(editForm.countyCode)" item-title="name" item-value="code" label="Location" density="compact" @update:model-value="onEditLocationChange" /></v-col>
            <v-col cols="6"><v-select v-model="editForm.villageCode" :items="villagesForLocation(editForm.locationCode)" item-title="name" item-value="code" label="Village" density="compact" /></v-col>
          </v-row>
        </v-card-text>
        <v-card-actions class="pa-4 pt-0">
          <v-spacer />
          <v-btn variant="text" @click="editDialog = false">Cancel</v-btn>
          <v-btn color="secondary" :loading="editing" @click="saveEdit">Save</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="addAltDialog" max-width="520">
      <v-card>
        <v-card-title class="d-flex align-center">
          Add alternate
          <v-spacer />
        </v-card-title>
        <dialog-close-button @close="addAltDialog = false" />
        <v-divider />
        <v-card-text class="d-flex flex-column ga-3 pt-4">
          <v-text-field v-model="altForm.alternateName" label="Full name" hide-details density="compact" />
          <v-text-field v-model="altForm.relationship" label="Relationship to household head" hide-details density="compact" />
          <div class="d-flex ga-3">
            <v-text-field v-model.number="altForm.age" label="Age" type="number" hide-details density="compact" />
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
            label="Photo (optional)"
            accept="image/*"
            prepend-icon="mdi-camera-outline"
            hide-details
            density="compact"
            @change="onAltPhotoFile"
          />
        </v-card-text>
        <v-card-actions class="pa-4 pt-0">
          <v-spacer />
          <v-btn variant="text" @click="addAltDialog = false">Cancel</v-btn>
          <v-btn color="secondary" :loading="addingAlt" @click="saveAlternate">Save</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
