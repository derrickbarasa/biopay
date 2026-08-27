<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { downloadCsv, parseCsv, toCsv } from '@/utils/csv'
import { useAnchorScope } from '@/composables/useAnchorScope'

interface VoucherRow {
  voucherCode: string
  householdNumber: string
  householdName: string
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
interface HouseholdOption { householdNumber: string; householdName: string }
interface OrganizationOption { organisationCode: string; name: string; anchorId?: number }

const auth = useAuthStore()
const toast = useToast()
const { confirmAction } = useConfirm()
const { anchors, selectedAnchorId, anchorGateActive, anchorChosen } = useAnchorScope()
const loading = ref(true)
const householdFilter = ref('')
const statusFilter = ref<string | null>(null)
const organisationFilter = ref<string | null>(null)
const vouchers = ref<VoucherRow[]>([])
const summary = ref<Record<string, any>>({})
const tableSearch = ref('')
const organizations = ref<OrganizationOption[]>([])

// Both roles see everything in their scope immediately -- the backend already
// treats an unset anchor/organisation filter as "show all" (`IS NULL OR ...`),
// so the picker below narrows the view without ever blocking it.
const scopeReady = computed(() => true)
const householdOptions = ref<HouseholdOption[]>([])
const householdsLoading = ref(false)
const orgNameByCode = computed(() => new Map(organizations.value.map((o) => [o.organisationCode, o.name])))
function orgName(code?: string) { return (code && orgNameByCode.value.get(code)) || code || '—' }

function targetAnchorForOrg(code?: string | null) {
  return auth.isSystemAdmin ? organizations.value.find((organization) => organization.organisationCode === code)?.anchorId : undefined
}

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

const form = ref({ organisationCode: null as string | null, householdNumber: '', amount: null as number | null, purpose: '', expiresAt: '' })

const bulkPurpose = ref('')
const bulkOrganisationCode = ref<string | null>(null)
const bulkExpiresAt = ref('')
const bulkFileName = ref('')
const bulkRows = ref<{ householdNumber: string; householdName: string; amount: number }[]>([])
const bulkErrors = ref<{ row: number; message: string }[]>([])

// ---- Generate vouchers by geographic level -- picks a state, county, location or village,
// auto-fills every active household in it, then submits through the same
// BULK_ISSUE_VOUCHERS endpoint the CSV bulk-issue flow already uses. ----
type GenerationLevel = 'STATE' | 'COUNTY' | 'LOCATION' | 'VILLAGE'
const generationLevel = ref<GenerationLevel>('VILLAGE')
const villageStateCode = ref<string | null>(null)
const villageOrganisationCode = ref<string | null>(null)
const villageCountyCode = ref<string | null>(null)
const villageLocationCode = ref<string | null>(null)
const villageSelected = ref<string | null>(null)
const villageLoading = ref(false)
const villageFlatAmount = ref<number | null>(null)
const villagePurpose = ref('')
const villageExpiresAt = ref('')
const villageRows = ref<{ householdNumber: string; householdName: string; amount: number }[]>([])
const villageRowHeaders = [
  { title: 'Household', key: 'householdName' },
  { title: 'Amount', key: 'amount' },
]
let geoLoaded = false

const headers = [
  { title: 'Voucher Code', key: 'voucherCode' },
  { title: 'Household', key: 'householdName' },
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
        pageSize: 100, status: statusFilter.value, householdName: householdFilter.value || undefined,
        organisationCode: organisationFilter.value ?? undefined,
        targetAnchorId: auth.isSystemAdmin ? selectedAnchorId.value : undefined,
      }),
      dispatch<{ results: Record<string, any> }>('VOUCHER_SUMMARY', {
        organisationCode: organisationFilter.value ?? undefined,
        targetAnchorId: auth.isSystemAdmin ? selectedAnchorId.value : undefined,
      }),
    ])
    vouchers.value = v.results
    summary.value = s.results
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load vouchers')
  } finally {
    loading.value = false
  }
}

watch(organisationFilter, load)

async function loadOrganizations() {
  if (!auth.isAnchorAdministrator && !auth.isSystemAdmin) { organizations.value = []; return }
  try {
    const res = await dispatch<{ results: typeof organizations.value }>('GET_ORGANIZATIONS', {
      targetAnchorId: auth.isSystemAdmin ? selectedAnchorId.value : undefined,
    })
    organizations.value = res.results
  } catch {
    // Table just falls back to showing the raw code; the list itself still loaded above.
  }
}

// Super Admin picking a different anchor resets whatever organisation was
// selected under the previous one, then reloads both lists.
watch(selectedAnchorId, () => { organisationFilter.value = null; loadOrganizations(); load() })

onMounted(async () => {
  load()
  loadOrganizations()
  try {
    const res = await dispatch<{ results: GeoNode[] }>('GET_VILLAGES')
    villages.value = res.results
  } catch {
    // Village column just falls back to showing the raw code.
  }
})

async function loadHouseholdOptions(organisationCode?: string | null) {
  if (auth.isAnchor && !organisationCode) {
    householdOptions.value = []
    return
  }
  householdsLoading.value = true
  try {
    const res = await dispatch<{ results: HouseholdOption[] }>('GET_HOUSEHOLDS', {
      organisationCode: organisationCode || undefined, targetAnchorId: targetAnchorForOrg(organisationCode), status: 1, pageSize: 200,
    })
    householdOptions.value = res.results ?? []
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load available households')
  } finally {
    householdsLoading.value = false
  }
}

function openIssue() {
  form.value = { organisationCode: null, householdNumber: '', amount: null, purpose: '', expiresAt: '' }
  issueDialog.value = true
  if (!auth.isAnchor) loadHouseholdOptions()
}

watch(() => form.value.organisationCode, (code) => {
  form.value.householdNumber = ''
  if (issueDialog.value) loadHouseholdOptions(code)
})

async function issue() {
  if ((auth.isAnchor && !form.value.organisationCode) || !form.value.householdNumber || !form.value.amount) {
    toast.error('Choose an organisation, household and positive amount')
    return
  }
  saving.value = true
  try {
    await dispatch('CREATE_VOUCHER', { ...form.value, organisationCode: form.value.organisationCode || undefined, targetAnchorId: targetAnchorForOrg(form.value.organisationCode) })
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
  bulkOrganisationCode.value = null
  bulkPurpose.value = ''
  bulkExpiresAt.value = ''
  bulkFileName.value = ''
  bulkRows.value = []
  bulkErrors.value = []
  bulkDialog.value = true
  if (!auth.isAnchor) void loadHouseholdOptions()
}

function downloadBulkTemplate() {
  downloadCsv('voucher-issue-template.csv', toCsv(['householdName', 'amount'], [['Example Household', '5000']]))
}

function onBulkFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  bulkFileName.value = file.name
  const reader = new FileReader()
  reader.onload = () => {
    const parsed = parseCsv(String(reader.result ?? ''))
    const mapped: typeof bulkRows.value = []
    const errors: typeof bulkErrors.value = []
    parsed.forEach((row, index) => {
      const householdName = String(row.householdName ?? '').trim()
      const matches = householdOptions.value.filter((option) => option.householdName.trim().toLowerCase() === householdName.toLowerCase())
      if (!householdName || matches.length !== 1) {
        errors.push({ row: index + 1, message: matches.length > 1 ? `More than one household is named ${householdName}` : `Household ${householdName || '(blank)'} was not found` })
        return
      }
      mapped.push({ householdNumber: matches[0].householdNumber, householdName: matches[0].householdName, amount: Number(row.amount) })
    })
    bulkRows.value = mapped
    bulkErrors.value = errors
  }
  reader.readAsText(file)
}

watch(bulkOrganisationCode, (code) => {
  bulkRows.value = []
  bulkErrors.value = []
  if (bulkDialog.value) void loadHouseholdOptions(code)
})

async function submitBulk() {
  if (auth.isAnchor && !bulkOrganisationCode.value) {
    toast.error('Select the organisation receiving these vouchers')
    return
  }
  if (!bulkRows.value.length) {
    toast.error('Upload a CSV with at least one row')
    return
  }
  saving.value = true
  try {
    const res = await dispatch<{ successCount: number; failureCount: number; errors: { row: number; message: string }[] }>(
      'BULK_ISSUE_VOUCHERS',
      { organisationCode: bulkOrganisationCode.value || undefined, targetAnchorId: targetAnchorForOrg(bulkOrganisationCode.value), rows: bulkRows.value, purpose: bulkPurpose.value || undefined, expiresAt: bulkExpiresAt.value || undefined },
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
  generationLevel.value = 'VILLAGE'
  villageOrganisationCode.value = null
  villageStateCode.value = null
  villageCountyCode.value = null
  villageLocationCode.value = null
  villageSelected.value = null
  villageFlatAmount.value = null
  villagePurpose.value = ''
  villageExpiresAt.value = ''
  villageRows.value = []
  villageDialog.value = true
  if (!auth.isSystemAdmin) await loadGenerationGeo()
}

async function loadGenerationGeo() {
  if (geoLoaded && !auth.isSystemAdmin) return
  const selectedOrganization = organizations.value.find((organization) => organization.organisationCode === villageOrganisationCode.value)
  if (auth.isSystemAdmin && !selectedOrganization?.anchorId) return
  try {
    const scope = auth.isSystemAdmin ? { targetAnchorId: selectedOrganization?.anchorId } : {}
    const [s, c, l, v] = await Promise.all([
      dispatch<{ results: GeoNode[] }>('GET_STATES', scope),
      dispatch<{ results: GeoNode[] }>('GET_COUNTIES', scope),
      dispatch<{ results: GeoNode[] }>('GET_LOCATIONS', scope),
      dispatch<{ results: GeoNode[] }>('GET_VILLAGES', scope),
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

const selectedScopeCode = computed(() => {
  if (generationLevel.value === 'STATE') return villageStateCode.value
  if (generationLevel.value === 'COUNTY') return villageCountyCode.value
  if (generationLevel.value === 'LOCATION') return villageLocationCode.value
  return villageSelected.value
})

const selectedScopeName = computed(() => {
  const code = selectedScopeCode.value
  const source = generationLevel.value === 'STATE' ? states.value
    : generationLevel.value === 'COUNTY' ? counties.value
      : generationLevel.value === 'LOCATION' ? locations.value : villages.value
  return source.find((item) => item.code === code)?.name ?? ''
})

function resetGenerationScope() {
  villageStateCode.value = null
  villageCountyCode.value = null
  villageLocationCode.value = null
  villageSelected.value = null
  villageRows.value = []
}

// Every active household in the chosen geographic scope, one page at a time (GET_HOUSEHOLDS caps
// pageSize at 200 server-side), stopping at BULK_ISSUE_VOUCHERS' own 500-row limit.
async function loadScopeHouseholds() {
  villageRows.value = []
  if (!selectedScopeCode.value) return
  villageLoading.value = true
  try {
    const rows: { householdNumber: string; householdName: string; amount: number }[] = []
    const pageSize = 200
    let page = 1
    while (rows.length < 500) {
      const geographicFilter = generationLevel.value === 'STATE' ? { stateCode: villageStateCode.value }
        : generationLevel.value === 'COUNTY' ? { countyCode: villageCountyCode.value }
          : generationLevel.value === 'LOCATION' ? { locationCode: villageLocationCode.value }
            : { villageCode: villageSelected.value }
      const res = await dispatch<{ results: { householdNumber: string; householdName: string }[] }>('GET_HOUSEHOLDS', {
        organisationCode: villageOrganisationCode.value || undefined, targetAnchorId: targetAnchorForOrg(villageOrganisationCode.value), ...geographicFilter, status: 1, page, pageSize,
      })
      for (const h of res.results) {
        if (rows.length >= 500) break
        rows.push({ householdNumber: h.householdNumber, householdName: h.householdName, amount: villageFlatAmount.value ?? 0 })
      }
      if (res.results.length < pageSize) break
      page++
    }
    villageRows.value = rows
    if (!rows.length) toast.error(`No active households found in this ${generationLevel.value.toLowerCase()}`)
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load households for this area')
  } finally {
    villageLoading.value = false
  }
}

watch(villageOrganisationCode, () => {
  resetGenerationScope()
  if (auth.isSystemAdmin) {
    geoLoaded = false
    void loadGenerationGeo()
  }
})

function onVillageStateChange() {
  villageCountyCode.value = null
  villageLocationCode.value = null
  villageSelected.value = null
  villageRows.value = []
  if (generationLevel.value === 'STATE') void loadScopeHouseholds()
}
function onVillageCountyChange() {
  villageLocationCode.value = null
  villageSelected.value = null
  villageRows.value = []
  if (generationLevel.value === 'COUNTY') void loadScopeHouseholds()
}
function onVillageLocationChange() {
  villageSelected.value = null
  villageRows.value = []
  if (generationLevel.value === 'LOCATION') void loadScopeHouseholds()
}

watch(generationLevel, resetGenerationScope)

function applyFlatAmountToAll() {
  const amount = villageFlatAmount.value ?? 0
  for (const row of villageRows.value) row.amount = amount
}

async function submitVillage() {
  if (auth.isAnchor && !villageOrganisationCode.value) {
    toast.error('Select the organisation receiving these vouchers')
    return
  }
  if (!villageRows.value.length) return
  if (villageRows.value.some((r) => !r.amount || r.amount <= 0)) {
    toast.error('Every household needs a positive amount')
    return
  }
  saving.value = true
  try {
    const res = await dispatch<{ successCount: number; failureCount: number; errors: { row: number; message: string }[] }>(
      'BULK_ISSUE_VOUCHERS',
      { organisationCode: villageOrganisationCode.value || undefined, targetAnchorId: targetAnchorForOrg(villageOrganisationCode.value), rows: villageRows.value, purpose: villagePurpose.value || undefined, expiresAt: villageExpiresAt.value || undefined },
    )
    toast.success(`${res.successCount} voucher(s) issued, ${res.failureCount} failed`)
    if (!(res.errors ?? []).length) villageDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Geographic voucher generation failed')
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
  if (!await confirmAction({
    title: 'Void voucher?',
    message: `${row.voucherCode} will no longer be redeemable. This action cannot be undone.`,
    confirmLabel: 'Void voucher',
    color: 'error',
  })) return
  try {
    await dispatch('VOID_VOUCHER', { voucherCode: row.voucherCode })
    toast.success('Voucher voided')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to void voucher')
  }
}

function escapePrintValue(value: unknown) {
  return String(value ?? '').replace(/[&<>'"]/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;',
  })[character] ?? character)
}

async function printVoucher(row: VoucherRow) {
  const printWindow = window.open('', '_blank', 'width=760,height=760')
  if (!printWindow) {
    toast.error('Allow pop-ups to print this voucher')
    return
  }
  try {
    const QRCode = await import('qrcode')
    const qrImage = await QRCode.toDataURL(`BIOPAY|VOUCHER|${row.voucherCode}`, {
      width: 240, margin: 1, errorCorrectionLevel: 'H', color: { dark: '#0f172a', light: '#ffffff' },
    })
    printWindow.document.write(`<!doctype html><html><head><title>Voucher ${escapePrintValue(row.voucherCode)}</title><style>
      body{font-family:Ubuntu,'Trebuchet MS',sans-serif;color:#0f172a;margin:0;padding:32px;background:#f8fafc}.voucher{max-width:620px;margin:auto;background:#fff;border:1px solid #cbd5e1;border-radius:10px;padding:28px}.brand{color:#0f766e;font-size:22px;font-weight:800}.code{font-size:13px;color:#64748b;margin-top:3px}.grid{display:grid;grid-template-columns:1fr auto;gap:24px;align-items:center;margin-top:24px}.name{font-size:24px;font-weight:800}.amount{font-size:30px;font-weight:800;color:#0f766e;margin:14px 0}.meta{font-size:14px;line-height:1.7;color:#475569}.qr{width:210px;height:210px}.scan{text-align:center;font-size:12px;color:#64748b;margin-top:6px}@media print{body{padding:0;background:#fff}.voucher{border:1px solid #94a3b8}}
    </style></head><body><section class="voucher"><div class="brand">BioPay Voucher</div><div class="code">${escapePrintValue(row.voucherCode)}</div><div class="grid"><div><div class="name">${escapePrintValue(row.householdName)}</div><div class="amount">${escapePrintValue(currency(row.amount))}</div><div class="meta">Organisation: ${escapePrintValue(orgName(row.organisationCode))}<br>Purpose: ${escapePrintValue(row.purpose || 'General assistance')}<br>Expires: ${escapePrintValue(row.expiresAt ? new Date(row.expiresAt).toLocaleDateString() : 'No expiry')}<br>Status: ${escapePrintValue(row.status)}</div></div><div><img class="qr" src="${qrImage}" alt="Voucher QR code"><div class="scan">Scan voucher code</div></div></div></section><script>window.onload=()=>{window.print()}<\/script></body></html>`)
    printWindow.document.close()
  } catch {
    printWindow.close()
    toast.error('Unable to prepare this voucher for printing')
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
      <div v-if="scopeReady" class="d-flex ga-2">
        <v-btn v-if="auth.can('ACCESS_VOUCHERS')" variant="outlined" prepend-icon="mdi-file-upload" @click="openBulk">Bulk Issue</v-btn>
        <v-btn v-if="auth.can('ACCESS_VOUCHERS')" color="secondary" prepend-icon="mdi-map-marker-radius-outline" @click="openVillageGenerate">Generate by Area</v-btn>
        <v-btn v-if="auth.can('ACCESS_VOUCHERS')" color="secondary" prepend-icon="mdi-ticket-confirmation-outline" @click="openIssue">Issue Voucher</v-btn>
      </div>
    </div>

    <v-alert v-if="auth.isSystemAdmin ? !anchorChosen : (auth.isAnchorAdministrator && !organisationFilter)" type="info" variant="tonal" class="mb-4">
      {{ auth.isSystemAdmin ? 'Showing vouchers across every anchor. Choose one in the filters below to narrow the list.' : 'Showing vouchers across every organisation. Choose one in the filters below to narrow the list.' }}
    </v-alert>

    <template v-if="scopeReady">
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
      <v-card-text class="d-flex ga-3 flex-wrap">
        <v-select
          v-if="anchorGateActive" v-model="selectedAnchorId" :items="anchors" item-title="name" item-value="id"
          label="Anchor" clearable hide-details density="compact" style="max-width: 220px" prepend-inner-icon="mdi-bank-outline"
        />
        <v-select
          v-if="auth.isSystemAdmin || auth.isAnchorAdministrator" v-model="organisationFilter" :items="organizations" item-title="name" item-value="organisationCode"
          label="Organisation" clearable hide-details density="compact" style="max-width: 220px"
        />
        <v-text-field
          v-model="householdFilter" prepend-inner-icon="mdi-magnify" label="Household name" clearable
          hide-details density="compact" style="max-width: 240px" @update:model-value="onHouseholdFilterInput" @click:clear="load"
        />
        <v-select v-model="statusFilter" :items="['ISSUED', 'REDEEMED', 'VOID']" label="Status" clearable hide-details density="compact" style="max-width: 200px" @update:model-value="load" />
        <v-text-field v-model="tableSearch" prepend-inner-icon="mdi-magnify" label="Search" clearable hide-details density="compact" style="max-width: 220px" />
      </v-card-text>
      <v-data-table :headers="headers" :items="vouchers" :search="tableSearch" :loading="loading">
        <template #item.householdName="{ item }">{{ item.householdName || '—' }}</template>
        <template #item.villageCode="{ item }">{{ villageName(item.villageCode) }}</template>
        <template #item.organisationCode="{ item }">{{ orgName(item.organisationCode) }}</template>
        <template #item.amount="{ item }">{{ currency(item.amount) }}</template>
        <template #item.status="{ item }">
          <v-chip size="small" :color="statusColor(item.status)" variant="tonal">{{ item.status }}</v-chip>
        </template>
        <template #item.expiresAt="{ item }">{{ item.expiresAt ? new Date(item.expiresAt).toLocaleDateString() : '—' }}</template>
        <template #item.actions="{ item }">
          <v-btn size="small" variant="tonal" color="primary" prepend-icon="mdi-printer" @click="printVoucher(item)">Print</v-btn>
          <v-btn v-if="auth.can('ACCESS_VOUCHERS') && item.status === 'ISSUED'" size="small" variant="text" color="success" @click="openRedeem(item)">Redeem</v-btn>
          <v-btn v-if="auth.can('ACCESS_VOUCHERS') && item.status === 'ISSUED'" size="small" variant="text" color="error" @click="voidVoucher(item)">Void</v-btn>
        </template>
      </v-data-table>
    </v-card>
    </template>

    <v-dialog v-model="issueDialog" max-width="560">
      <v-card class="voucher-editor">
        <dialog-close-button @close="issueDialog = false" />
        <div class="editor-heading">
          <div>
            <div class="editor-title"><v-icon icon="mdi-ticket-confirmation-outline" size="20" /> Issue Voucher</div>
            <p>Issue a single voucher to a household, redeemable on presentation.</p>
          </div>
          <v-btn icon="mdi-close" variant="text" size="small" aria-label="Close voucher form" @click="issueDialog = false" />
        </div>
        <v-form @submit.prevent="issue">
          <div class="field-grid">
            <v-select
              v-if="auth.isAnchor" v-model="form.organisationCode" :items="organizations"
              item-title="name" item-value="organisationCode" label="Organisation" density="compact"
            />
            <v-autocomplete
              v-model="form.householdNumber" :items="householdOptions" item-title="householdName" item-value="householdNumber"
              label="Household name" density="compact" :loading="householdsLoading"
              :disabled="auth.isAnchor && !form.organisationCode" no-data-text="No active households available"
            />
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
        <dialog-close-button @close="bulkDialog = false" />
        <v-card-title>Bulk Issue Vouchers</v-card-title>
        <v-card-text>
          <v-alert type="info" variant="tonal" density="compact" class="mb-3">
            Download the template, enter each household name and amount, then upload it here.
          </v-alert>
          <v-select
            v-if="auth.isAnchor" v-model="bulkOrganisationCode" :items="organizations"
            item-title="name" item-value="organisationCode" label="Organisation" class="mb-2"
          />
          <v-btn variant="outlined" size="small" prepend-icon="mdi-download" class="mb-4" @click="downloadBulkTemplate">
            Download Template
          </v-btn>
          <v-file-input label="Upload filled CSV" accept=".csv" prepend-icon="mdi-file-upload" :disabled="auth.isAnchor && !bulkOrganisationCode" @change="onBulkFile" />
          <div v-if="bulkRows.length" class="text-caption mb-2">{{ bulkRows.length }} row(s) ready to issue from {{ bulkFileName }}</div>
          <v-table v-if="bulkRows.length" density="compact" class="mb-3">
            <thead><tr><th>Household</th><th class="text-right">Amount</th></tr></thead>
            <tbody><tr v-for="row in bulkRows.slice(0, 8)" :key="row.householdNumber"><td>{{ row.householdName }}</td><td class="text-right">{{ currency(row.amount) }}</td></tr></tbody>
          </v-table>
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
          <v-btn color="secondary" :loading="saving" :disabled="!bulkRows.length || (auth.isAnchor && !bulkOrganisationCode)" @click="submitBulk">Issue All</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="villageDialog" max-width="680">
      <v-card>
        <dialog-close-button @close="villageDialog = false" />
        <v-card-title>Generate Vouchers by Area</v-card-title>
        <v-card-text>
          <v-alert type="info" variant="tonal" density="compact" class="mb-3">
            Choose a state, county, location or village. Every active household in that area will be prepared for voucher generation.
          </v-alert>
          <v-select
            v-if="auth.isAnchor" v-model="villageOrganisationCode" :items="organizations"
            item-title="name" item-value="organisationCode" label="Organisation" class="mb-3"
          />
          <v-select
            v-model="generationLevel"
            :items="[{ title: 'State', value: 'STATE' }, { title: 'County', value: 'COUNTY' }, { title: 'Location', value: 'LOCATION' }, { title: 'Village', value: 'VILLAGE' }]"
            label="Generate for" density="compact" class="mb-3" style="max-width: 240px"
          />
          <div class="d-flex ga-3 flex-wrap mb-3">
            <v-select
              v-model="villageStateCode" :items="states" item-title="name" item-value="code" label="State"
              clearable hide-details density="compact" style="max-width: 170px" :disabled="auth.isAnchor && !villageOrganisationCode" @update:model-value="onVillageStateChange"
            />
            <v-select
              v-if="generationLevel !== 'STATE'"
              v-model="villageCountyCode" :items="countiesForState(villageStateCode)" item-title="name" item-value="code" label="County"
              clearable hide-details density="compact" style="max-width: 170px" @update:model-value="onVillageCountyChange"
            />
            <v-select
              v-if="generationLevel === 'LOCATION' || generationLevel === 'VILLAGE'"
              v-model="villageLocationCode" :items="locationsForCounty(villageCountyCode)" item-title="name" item-value="code" label="Location"
              clearable hide-details density="compact" style="max-width: 170px" @update:model-value="onVillageLocationChange"
            />
            <v-select
              v-if="generationLevel === 'VILLAGE'"
              v-model="villageSelected" :items="villagesForLocation(villageLocationCode)" item-title="name" item-value="code" label="Village"
              clearable hide-details density="compact" style="max-width: 170px" @update:model-value="loadScopeHouseholds"
            />
          </div>

          <v-alert v-if="villageLoading" type="info" variant="tonal" density="compact" class="mb-3">Loading households…</v-alert>
          <v-alert v-else-if="selectedScopeCode && !villageRows.length" type="warning" variant="tonal" density="compact" class="mb-3">
            No active households found in this {{ generationLevel.toLowerCase() }}.
          </v-alert>

          <template v-if="villageRows.length">
            <div class="d-flex align-center ga-3 flex-wrap mb-3">
              <v-text-field
                v-model.number="villageFlatAmount" label="Amount per voucher" type="number" density="compact"
                hide-details style="max-width: 200px"
              />
              <v-btn size="small" color="primary" variant="flat" @click="applyFlatAmountToAll">Apply to all rows</v-btn>
              <v-spacer />
              <span class="text-caption text-medium-emphasis">{{ villageRows.length }} household(s) in {{ selectedScopeName }}</span>
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
        <dialog-close-button @close="redeemDialog = false" />
        <v-card-title>Redeem Voucher</v-card-title>
        <v-card-text>
          <p class="mb-3">
            Confirm redemption of <strong>{{ currency(redeeming.amount) }}</strong> for household
            <strong>{{ redeeming.householdName }}</strong> (voucher {{ redeeming.voucherCode }}).
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
