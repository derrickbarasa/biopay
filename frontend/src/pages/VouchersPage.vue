<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { downloadCsv, parseCsv, toCsv } from '@/utils/csv'

interface VoucherRow {
  voucherCode: string
  householdNumber: string
  organisationCode: string
  villageCode?: string
  amount: number
  purpose?: string
  status: 'ISSUED' | 'REDEEMED' | 'VOID'
  expiresAt?: string
  redeemedAt?: string
  createdAt: string
}

interface GeoNode {
  code: string
  name: string
  stateCode?: string
  countyCode?: string
  locationCode?: string
}

const auth = useAuthStore()
const toast = useToast()
const loading = ref(true)
const householdFilter = ref('')
const statusFilter = ref<string | null>(null)
const vouchers = ref<VoucherRow[]>([])
const summary = ref<Record<string, any>>({})
const tableSearch = ref('')
const organizations = ref<{ organisationCode: string; name: string }[]>([])
const orgNameByCode = computed(() => new Map(organizations.value.map((o) => [o.organisationCode, o.name])))
function orgName(code?: string) { return (code && orgNameByCode.value.get(code)) || code || '—' }

// ---- Geo hierarchy -- shared by the Village column's name lookup and the "Generate by
// Village" dialog's cascading selects, following the same client-side code->name lookup
// convention as HouseholdsPage/LocationsPage (villageName()/orgName()). ----
const states = ref<GeoNode[]>([])
const counties = ref<GeoNode[]>([])
const locations = ref<GeoNode[]>([])
const villages = ref<GeoNode[]>([])
const villageNameByCode = computed(() => new Map(villages.value.map((v) => [v.code, v.name])))
function villageName(code?: string) { return (code && villageNameByCode.value.get(code)) || code || '—' }
const countiesForState = (stateCode: string | null) => stateCode ? counties.value.filter((c) => c.stateCode === stateCode) : counties.value
const locationsForCounty = (countyCode: string | null) => countyCode ? locations.value.filter((l) => l.countyCode === countyCode) : locations.value
const villagesForLocation = (locationCode: string | null) => locationCode ? villages.value.filter((v) => v.locationCode === locationCode) : villages.value

let householdDebounce: ReturnType<typeof setTimeout> | null = null

function onHouseholdFilterInput() {
  if (householdDebounce) clearTimeout(householdDebounce)
  householdDebounce = setTimeout(load, 400)
}

const issueDialog = ref(false)
const bulkDialog = ref(false)
const villageDialog = ref(false)
const redeemDialog = ref(false)
const saving = ref(false)
const redeeming = ref<VoucherRow | null>(null)
const redeemNotes = ref('')

const form = ref({ householdNumber: '', amount: null as number | null, purpose: '', expiresAt: '' })

const bulkPurpose = ref('')
const bulkExpiresAt = ref('')
const bulkFileName = ref('')
const bulkRows = ref<{ householdNumber: string; amount: number }[]>([])
const bulkErrors = ref<{ row: number; message: string }[]>([])

// ---- Generate vouchers by village -- picks a village from the geo hierarchy, auto-fills
// every active household in it as bulk-issue rows, then submits through the same
// BULK_ISSUE_VOUCHERS endpoint the CSV bulk-issue flow already uses. ----
const villageStateCode = ref<string | null>(null)
const villageCountyCode = ref<string | null>(null)
const villageLocationCode = ref<string | null>(null)
const villageSelected = ref<string | null>(null)
const villageLoading = ref(false)
const villageFlatAmount = ref<number | null>(null)
const villagePurpose = ref('')
const villageExpiresAt = ref('')
const villageRows = ref<{ householdNumber: string; amount: number }[]>([])
const villageRowHeaders = [
  { title: 'Household', key: 'householdNumber' },
  { title: 'Amount', key: 'amount' },
]
let geoLoaded = false

const headers = [
  { title: 'Voucher Code', key: 'voucherCode' },
  { title: 'Household', key: 'householdNumber' },
  { title: 'Village', key: 'villageCode' },
  { title: 'Organization', key: 'organisationCode' },
  { title: 'Amount', key: 'amount' },
  { title: 'Status', key: 'status' },
  { title: 'Expires', key: 'expiresAt' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'end' as const },
]

function currency(v: number | undefined) {
  return (v ?? 0).toLocaleString(undefined, { style: 'currency', currency: 'USD', maximumFractionDigits: 0 })
}

async function load() {
  loading.value = true
  try {
    const [v, s] = await Promise.all([
      dispatch<{ results: VoucherRow[] }>('GET_VOUCHERS', {
        pageSize: 100, status: statusFilter.value, householdNumber: householdFilter.value || undefined,
      }),
      dispatch<{ results: Record<string, any> }>('VOUCHER_SUMMARY'),
    ])
    vouchers.value = v.results
    summary.value = s.results
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load vouchers')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  load()
  if (auth.isAnchor) {
    try {
      const res = await dispatch<{ results: typeof organizations.value }>('GET_ORGANIZATIONS')
      organizations.value = res.results
    } catch {
      // Table just falls back to showing the raw code; the list itself still loaded above.
    }
  }
  try {
    const res = await dispatch<{ results: GeoNode[] }>('GET_VILLAGES')
    villages.value = res.results
  } catch {
    // Village column just falls back to showing the raw code.
  }
})

function openIssue() {
  form.value = { householdNumber: '', amount: null, purpose: '', expiresAt: '' }
  issueDialog.value = true
}

async function issue() {
  if (!form.value.householdNumber.trim() || !form.value.amount) {
    toast.error('Household number and amount are required')
    return
  }
  saving.value = true
  try {
    await dispatch('CREATE_VOUCHER', form.value)
    toast.success('Voucher issued')
    issueDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to issue voucher')
  } finally {
    saving.value = false
  }
}

function openBulk() {
  bulkPurpose.value = ''
  bulkExpiresAt.value = ''
  bulkFileName.value = ''
  bulkRows.value = []
  bulkErrors.value = []
  bulkDialog.value = true
}

function downloadBulkTemplate() {
  downloadCsv('voucher-issue-template.csv', toCsv(['householdNumber', 'amount'], [['HH-EXAMPLE-0001', '5000']]))
}

function onBulkFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  bulkFileName.value = file.name
  const reader = new FileReader()
  reader.onload = () => {
    const parsed = parseCsv(String(reader.result ?? ''))
    bulkRows.value = parsed
      .map((r) => ({ householdNumber: r.householdNumber, amount: Number(r.amount) }))
      .filter((r) => r.householdNumber)
  }
  reader.readAsText(file)
}

async function submitBulk() {
  if (!bulkRows.value.length) {
    toast.error('Upload a CSV with at least one row')
    return
  }
  saving.value = true
  try {
    const res = await dispatch<{ successCount: number; failureCount: number; errors: { row: number; message: string }[] }>(
      'BULK_ISSUE_VOUCHERS',
      { rows: bulkRows.value, purpose: bulkPurpose.value || undefined, expiresAt: bulkExpiresAt.value || undefined },
    )
    bulkErrors.value = res.errors ?? []
    toast.success(`${res.successCount} voucher(s) issued, ${res.failureCount} failed`)
    if (!bulkErrors.value.length) bulkDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Bulk issue failed')
  } finally {
    saving.value = false
  }
}

async function openVillageGenerate() {
  villageStateCode.value = null
  villageCountyCode.value = null
  villageLocationCode.value = null
  villageSelected.value = null
  villageFlatAmount.value = null
  villagePurpose.value = ''
  villageExpiresAt.value = ''
  villageRows.value = []
  villageDialog.value = true
  if (!geoLoaded) {
    try {
      const [s, c, l, v] = await Promise.all([
        dispatch<{ results: GeoNode[] }>('GET_STATES'),
        dispatch<{ results: GeoNode[] }>('GET_COUNTIES'),
        dispatch<{ results: GeoNode[] }>('GET_LOCATIONS'),
        dispatch<{ results: GeoNode[] }>('GET_VILLAGES'),
      ])
      states.value = s.results
      counties.value = c.results
      locations.value = l.results
      villages.value = v.results
      geoLoaded = true
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to load locations')
    }
  }
}

// Every active household in the chosen village, one page at a time (GET_HOUSEHOLDS caps
// pageSize at 200 server-side), stopping at BULK_ISSUE_VOUCHERS' own 500-row limit.
async function onVillageSelected(villageCode: string | null) {
  villageRows.value = []
  if (!villageCode) return
  villageLoading.value = true
  try {
    const rows: { householdNumber: string; amount: number }[] = []
    const pageSize = 200
    let page = 1
    while (rows.length < 500) {
      const res = await dispatch<{ results: { householdNumber: string }[] }>('GET_HOUSEHOLDS', {
        villageCode, status: 1, page, pageSize,
      })
      for (const h of res.results) {
        if (rows.length >= 500) break
        rows.push({ householdNumber: h.householdNumber, amount: villageFlatAmount.value ?? 0 })
      }
      if (res.results.length < pageSize) break
      page++
    }
    villageRows.value = rows
    if (!rows.length) toast.error('No active households found in this village')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load households for this village')
  } finally {
    villageLoading.value = false
  }
}

function onVillageStateChange() {
  villageCountyCode.value = null
  villageLocationCode.value = null
  villageSelected.value = null
  onVillageSelected(null)
}
function onVillageCountyChange() {
  villageLocationCode.value = null
  villageSelected.value = null
  onVillageSelected(null)
}
function onVillageLocationChange() {
  villageSelected.value = null
  onVillageSelected(null)
}

function applyFlatAmountToAll() {
  const amount = villageFlatAmount.value ?? 0
  for (const row of villageRows.value) row.amount = amount
}

async function submitVillage() {
  if (!villageRows.value.length) return
  if (villageRows.value.some((r) => !r.amount || r.amount <= 0)) {
    toast.error('Every household needs a positive amount')
    return
  }
  saving.value = true
  try {
    const res = await dispatch<{ successCount: number; failureCount: number; errors: { row: number; message: string }[] }>(
      'BULK_ISSUE_VOUCHERS',
      { rows: villageRows.value, purpose: villagePurpose.value || undefined, expiresAt: villageExpiresAt.value || undefined },
    )
    toast.success(`${res.successCount} voucher(s) issued, ${res.failureCount} failed`)
    if (!(res.errors ?? []).length) villageDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Village voucher generation failed')
  } finally {
    saving.value = false
  }
}

function openRedeem(row: VoucherRow) {
  redeeming.value = row
  redeemNotes.value = ''
  redeemDialog.value = true
}

async function confirmRedeem() {
  if (!redeeming.value) return
  saving.value = true
  try {
    await dispatch('REDEEM_VOUCHER', { voucherCode: redeeming.value.voucherCode, notes: redeemNotes.value || undefined })
    toast.success('Voucher redeemed')
    redeemDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Redemption failed')
  } finally {
    saving.value = false
  }
}

async function voidVoucher(row: VoucherRow) {
  try {
    await dispatch('VOID_VOUCHER', { voucherCode: row.voucherCode })
    toast.success('Voucher voided')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to void voucher')
  }
}

function statusColor(status: string) {
  return status === 'REDEEMED' ? 'success' : status === 'VOID' ? 'error' : 'warning'
}
</script>

<template>
  <div>
    <div class="d-flex align-center justify-space-between mb-4">
      <h1 class="page-title">Vouchers</h1>
      <div class="d-flex ga-2">
        <v-btn variant="outlined" prepend-icon="mdi-file-upload" @click="openBulk">Bulk Issue</v-btn>
        <v-btn color="secondary" prepend-icon="mdi-map-marker-radius-outline" @click="openVillageGenerate">Generate by Village</v-btn>
        <v-btn color="secondary" prepend-icon="mdi-ticket-confirmation-outline" @click="openIssue">Issue Voucher</v-btn>
      </div>
    </div>

    <div class="voucher-summary-grid mb-4">
      <v-card class="summary-card" variant="flat" border>
        <div class="summary-label">Issued (awaiting redemption)</div>
        <div class="summary-value">{{ currency(summary.issuedAmount) }}</div>
        <div class="summary-detail">{{ summary.issuedCount ?? 0 }} vouchers</div>
      </v-card>
      <v-card class="summary-card" variant="flat" border>
        <div class="summary-label">Redeemed</div>
        <div class="summary-value">{{ currency(summary.redeemedAmount) }}</div>
        <div class="summary-detail">{{ summary.redeemedCount ?? 0 }} vouchers</div>
      </v-card>
      <v-card class="summary-card" variant="flat" border>
        <div class="summary-label">Voided</div>
        <div class="summary-value">{{ summary.voidCount ?? 0 }}</div>
        <div class="summary-detail">&nbsp;</div>
      </v-card>
    </div>

    <v-card variant="flat" border>
      <v-card-text class="d-flex ga-3">
        <v-text-field
          v-model="householdFilter" prepend-inner-icon="mdi-magnify" label="Household number" clearable
          hide-details density="compact" style="max-width: 240px" @update:model-value="onHouseholdFilterInput" @click:clear="load"
        />
        <v-select v-model="statusFilter" :items="['ISSUED', 'REDEEMED', 'VOID']" label="Status" clearable hide-details density="compact" style="max-width: 200px" @update:model-value="load" />
        <v-text-field v-model="tableSearch" prepend-inner-icon="mdi-magnify" label="Search" clearable hide-details density="compact" style="max-width: 220px" />
      </v-card-text>
      <v-data-table :headers="headers" :items="vouchers" :search="tableSearch" :loading="loading">
        <template #item.villageCode="{ item }">{{ villageName(item.villageCode) }}</template>
        <template #item.organisationCode="{ item }">{{ orgName(item.organisationCode) }}</template>
        <template #item.amount="{ item }">{{ currency(item.amount) }}</template>
        <template #item.status="{ item }">
          <v-chip size="small" :color="statusColor(item.status)" variant="tonal">{{ item.status }}</v-chip>
        </template>
        <template #item.expiresAt="{ item }">{{ item.expiresAt ? new Date(item.expiresAt).toLocaleDateString() : '—' }}</template>
        <template #item.actions="{ item }">
          <v-btn v-if="item.status === 'ISSUED'" size="small" variant="text" color="success" @click="openRedeem(item)">Redeem</v-btn>
          <v-btn v-if="item.status === 'ISSUED'" size="small" variant="text" color="error" @click="voidVoucher(item)">Void</v-btn>
        </template>
      </v-data-table>
    </v-card>

    <v-dialog v-model="issueDialog" max-width="560">
      <v-card class="voucher-editor">
        <div class="editor-heading">
          <div>
            <div class="editor-title"><v-icon icon="mdi-ticket-confirmation-outline" size="20" /> Issue Voucher</div>
            <p>Issue a single voucher to a household, redeemable on presentation.</p>
          </div>
          <v-btn icon="mdi-close" variant="text" size="small" aria-label="Close voucher form" @click="issueDialog = false" />
        </div>
        <v-form @submit.prevent="issue">
          <div class="field-grid">
            <v-text-field v-model="form.householdNumber" label="Household number" prepend-inner-icon="mdi-home-outline" density="compact" />
            <v-text-field v-model.number="form.amount" label="Amount" type="number" prepend-inner-icon="mdi-cash" density="compact" />
            <v-text-field v-model="form.purpose" label="Purpose (optional)" prepend-inner-icon="mdi-clipboard-check-outline" density="compact" />
            <v-text-field v-model="form.expiresAt" label="Expires on (optional)" type="date" density="compact" />
          </div>
          <div class="editor-actions">
            <v-btn variant="text" @click="issueDialog = false">Cancel</v-btn>
            <v-btn color="secondary" type="submit" :loading="saving" prepend-icon="mdi-ticket-confirmation-outline">Issue voucher</v-btn>
          </div>
        </v-form>
      </v-card>
    </v-dialog>

    <v-dialog v-model="bulkDialog" max-width="560">
      <v-card>
        <v-card-title>Bulk Issue Vouchers</v-card-title>
        <v-card-text>
          <v-alert type="info" variant="tonal" density="compact" class="mb-3">
            Download the template, fill in one row per household, then upload it here.
          </v-alert>
          <v-btn variant="outlined" size="small" prepend-icon="mdi-download" class="mb-4" @click="downloadBulkTemplate">
            Download Template
          </v-btn>
          <v-file-input label="Upload filled CSV" accept=".csv" prepend-icon="mdi-file-upload" @change="onBulkFile" />
          <div v-if="bulkRows.length" class="text-caption mb-2">{{ bulkRows.length }} row(s) ready to issue from {{ bulkFileName }}</div>
          <v-text-field v-model="bulkPurpose" label="Purpose (applies to all, optional)" />
          <v-text-field v-model="bulkExpiresAt" label="Expires on (applies to all, optional)" type="date" />
          <v-alert v-if="bulkErrors.length" type="warning" variant="tonal" density="compact" class="mt-2">
            {{ bulkErrors.length }} row(s) failed:
            <div v-for="e in bulkErrors.slice(0, 5)" :key="e.row">Row {{ e.row }}: {{ e.message }}</div>
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="bulkDialog = false">Close</v-btn>
          <v-btn color="secondary" :loading="saving" :disabled="!bulkRows.length" @click="submitBulk">Issue All</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="villageDialog" max-width="680">
      <v-card>
        <v-card-title>Generate Vouchers by Village</v-card-title>
        <v-card-text>
          <v-alert type="info" variant="tonal" density="compact" class="mb-3">
            Pick a village and every active household registered in it is issued a voucher.
          </v-alert>
          <div class="d-flex ga-3 flex-wrap mb-3">
            <v-select
              v-model="villageStateCode" :items="states" item-title="name" item-value="code" label="State"
              clearable hide-details density="compact" style="max-width: 170px" @update:model-value="onVillageStateChange"
            />
            <v-select
              v-model="villageCountyCode" :items="countiesForState(villageStateCode)" item-title="name" item-value="code" label="County"
              clearable hide-details density="compact" style="max-width: 170px" @update:model-value="onVillageCountyChange"
            />
            <v-select
              v-model="villageLocationCode" :items="locationsForCounty(villageCountyCode)" item-title="name" item-value="code" label="Location"
              clearable hide-details density="compact" style="max-width: 170px" @update:model-value="onVillageLocationChange"
            />
            <v-select
              v-model="villageSelected" :items="villagesForLocation(villageLocationCode)" item-title="name" item-value="code" label="Village"
              clearable hide-details density="compact" style="max-width: 170px" @update:model-value="onVillageSelected"
            />
          </div>

          <v-alert v-if="villageLoading" type="info" variant="tonal" density="compact" class="mb-3">Loading households…</v-alert>
          <v-alert v-else-if="villageSelected && !villageRows.length" type="warning" variant="tonal" density="compact" class="mb-3">
            No active households found in this village.
          </v-alert>

          <template v-if="villageRows.length">
            <div class="d-flex align-center ga-3 flex-wrap mb-3">
              <v-text-field
                v-model.number="villageFlatAmount" label="Amount per voucher" type="number" density="compact"
                hide-details style="max-width: 200px"
              />
              <v-btn size="small" variant="outlined" @click="applyFlatAmountToAll">Apply to all rows</v-btn>
              <v-spacer />
              <span class="text-caption text-medium-emphasis">{{ villageRows.length }} household(s)</span>
            </div>
            <v-text-field v-model="villagePurpose" label="Purpose (applies to all, optional)" density="compact" class="mb-2" />
            <v-text-field v-model="villageExpiresAt" label="Expires on (applies to all, optional)" type="date" density="compact" class="mb-3" />
            <div class="village-rows-scroll">
              <v-data-table :headers="villageRowHeaders" :items="villageRows" density="compact" :items-per-page="10">
                <template #item.amount="{ item }">
                  <v-text-field v-model.number="item.amount" type="number" density="compact" hide-details style="max-width: 140px" />
                </template>
              </v-data-table>
            </div>
          </template>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="villageDialog = false">Close</v-btn>
          <v-btn color="secondary" :loading="saving" :disabled="!villageRows.length" @click="submitVillage">Generate Vouchers</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="redeemDialog" max-width="440">
      <v-card v-if="redeeming">
        <v-card-title>Redeem Voucher</v-card-title>
        <v-card-text>
          <p class="mb-3">
            Confirm redemption of <strong>{{ currency(redeeming.amount) }}</strong> for household
            <strong>{{ redeeming.householdNumber }}</strong> (voucher {{ redeeming.voucherCode }}).
          </p>
          <v-textarea v-model="redeemNotes" label="Notes (optional)" rows="2" />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="redeemDialog = false">Cancel</v-btn>
          <v-btn color="success" :loading="saving" @click="confirmRedeem">Confirm Redemption</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.voucher-summary-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.summary-card { padding: 16px 18px !important; }
.summary-label { color: #64748b; font-size: .72rem; font-weight: 700; letter-spacing: .03em; text-transform: uppercase; }
.summary-value { color: #0f172a; font-size: 1.25rem; font-weight: 750; letter-spacing: -.02em; margin-top: 6px; }
.summary-detail { color: #64748b; font-size: .74rem; margin-top: 4px; }
@media (max-width: 620px) { .voucher-summary-grid { grid-template-columns: 1fr; } }

.village-rows-scroll { max-height: 320px; overflow-y: auto; }

.voucher-editor { padding: 22px 24px; }
.editor-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.editor-title { display: flex; align-items: center; gap: 8px; color: #0f172a; font-size: 1.05rem; font-weight: 750; letter-spacing: -.02em; }
.editor-heading p { color: #64748b; font-size: .8rem; margin: 4px 0 0; }
.field-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 16px; }
.editor-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
@media (max-width: 520px) {
  .field-grid { grid-template-columns: 1fr; }
  .editor-actions :deep(.v-btn) { flex: 1; }
}
</style>
