<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiClient, dispatch } from '@/api/client'
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
}

const route = useRoute()
const router = useRouter()
const toast = useToast()

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

const loading = ref(true)
const detail = ref<Record<string, any> | null>(null)
const alternates = ref<Alternate[]>([])
const payments = ref<PaymentEvent[]>([])
const events = ref<AuditEvent[]>([])
// Object URLs for photos fetched (with the auth header) through apiClient as blobs --
// a plain <img src> can't reach the JWT-protected /files route.
const photoUrls = ref<string[]>([])

const householdNumber = computed(() => String(route.params.householdNumber ?? ''))

const genderLabel = (g?: string) => (g === 'M' ? 'Male' : g === 'F' ? 'Female' : (g || '—'))

const infoFields = computed(() => {
  const d = detail.value
  if (!d) return []
  return [
    { label: 'Household number', value: d.householdNumber },
    { label: 'Head of household', value: d.householdName },
    { label: 'Organization', value: d.organisationCode },
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
    { label: 'State', value: d.stateCode || '—' },
    { label: 'County', value: d.countyCode || '—' },
    { label: 'Location', value: d.payamCode || '—' },
    { label: 'Village', value: d.bomaCode || '—' },
    { label: 'Coordinates', value: d.latitude && d.longitude ? `${d.latitude}, ${d.longitude}` : '—' },
    { label: 'Registered', value: d.createdAt || '—' },
    { label: 'Last updated', value: d.updatedAt || '—' },
  ]
})

function revokePhotos() {
  for (const u of photoUrls.value) URL.revokeObjectURL(u)
  photoUrls.value = []
}

// Fetches each JWT-protected photo through apiClient (which attaches the bearer
// token) and turns the blob into a displayable object URL.
async function loadPhotos(paths: string[]) {
  revokePhotos()
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
  photoUrls.value = urls
}

async function load() {
  loading.value = true
  try {
    const [h, alts, hist] = await Promise.all([
      dispatch<{ results: any[] }>('GET_HOUSEHOLD', { householdNumber: householdNumber.value }),
      dispatch<{ results: Alternate[] }>('GET_ALTERNATES', { householdNumber: householdNumber.value }),
      dispatch<{ results: { payments: PaymentEvent[]; events: AuditEvent[] } }>(
        'GET_HOUSEHOLD_HISTORY', { householdNumber: householdNumber.value },
      ),
    ])
    detail.value = h.results?.[0] ?? null
    alternates.value = alts.results ?? []
    payments.value = hist.results?.payments ?? []
    events.value = hist.results?.events ?? []
    const images: string[] = detail.value?.images ?? []
    if (images.length) loadPhotos(images)
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load household')
  } finally {
    loading.value = false
  }
}

onUnmounted(revokePhotos)

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
        * { box-sizing: border-box; font-family: Arial, Helvetica, sans-serif; }
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
            <div><div class="title">Payment Voucher</div><div class="sub">${escapeHtml(v.organisationCode ?? '')}</div></div>
            <div class="qrwrap">${qr}<div class="qrcap">${escapeHtml(v.householdNumber)}</div></div>
          </div>
          <div class="body">
            ${photo}
            <div class="info">
              <p class="name">${escapeHtml(v.householdName ?? '')}</p>
              <div class="row"><span class="label">Household #:</span> ${escapeHtml(v.householdNumber)}</div>
              <div class="row"><span class="label">Organization:</span> ${escapeHtml(v.organisationCode ?? '')}</div>
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

onMounted(load)
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
        v-if="detail"
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
      v-else-if="!detail"
      type="warning"
      variant="tonal"
      text="Household not found or not in your organisation."
    />

    <v-row v-else>
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

        <v-card variant="flat" border>
          <v-card-title class="text-subtitle-1 font-weight-bold d-flex align-center">
            Alternates ({{ alternates.length }})
            <v-spacer />
            <v-btn
              v-if="alternates.length"
              size="small"
              variant="text"
              prepend-icon="mdi-download"
              @click="exportAlternates"
            >
              Export
            </v-btn>
          </v-card-title>
          <v-divider />
          <v-list v-if="alternates.length">
            <v-list-item
              v-for="a in alternates"
              :key="a.alternateNumber ?? a.alternateName"
              :title="a.alternateName"
              :subtitle="[a.relationship, a.phoneNumber].filter(Boolean).join(' · ') || undefined"
              prepend-icon="mdi-account-child-outline"
            />
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
  </div>
</template>
