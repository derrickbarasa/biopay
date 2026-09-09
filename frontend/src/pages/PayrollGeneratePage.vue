<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useAnchorScope } from '@/composables/useAnchorScope'
import { useOrgCascade } from '@/composables/useOrgCascade'

interface HouseholdOption {
  householdNumber: string
  householdName: string
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
const router = useRouter()
const { anchors } = useAnchorScope()
const { dialogAnchorId, dialogOrganizations, resetDialogScope } = useOrgCascade()
const CURRENCIES = ['USD', 'SSP', 'KES', 'UGX', 'ETB', 'EUR', 'GBP']

const STEPS = ['Period', 'Households', 'Amount & Currency', 'Verify & Confirm']
const step = ref(1)

const genForm = ref({
  organisationCode: null as string | null,
  periodStart: '', periodEnd: '',
  amountPerHousehold: null as number | null,
  currency: 'USD', exchangeRate: 1,
  householdNumbers: [] as string[],
  otpCode: '',
})

function goToList() {
  router.push({ name: 'payroll' })
}

function prevStep() {
  if (step.value > 1) step.value -= 1
}

// Geography cascade: state -> county -> location -> village, same hierarchy as the Households page.
const states = ref<GeoNode[]>([])
const counties = ref<GeoNode[]>([])
const locations = ref<GeoNode[]>([])
const villages = ref<GeoNode[]>([])
const geo = ref({
  stateCode: null as string | null,
  countyCode: null as string | null,
  locationCode: null as string | null,
  villageCode: null as string | null,
})

const countiesForState = computed(() => (geo.value.stateCode ? counties.value.filter((c) => c.stateCode === geo.value.stateCode) : counties.value))
const locationsForCounty = computed(() => (geo.value.countyCode ? locations.value.filter((l) => l.countyCode === geo.value.countyCode) : locations.value))
const villagesForLocation = computed(() => (geo.value.locationCode ? villages.value.filter((v) => v.locationCode === geo.value.locationCode) : villages.value))

watch(() => geo.value.stateCode, () => { geo.value.countyCode = null; geo.value.locationCode = null; geo.value.villageCode = null })
watch(() => geo.value.countyCode, () => { geo.value.locationCode = null; geo.value.villageCode = null })
watch(() => geo.value.locationCode, () => { geo.value.villageCode = null })

function clearGeoFilters() {
  geo.value = { stateCode: null, countyCode: null, locationCode: null, villageCode: null }
}

// Sourced from GET_HOUSEHOLD_LOCATIONS, not GET_STATES/COUNTIES/LOCATIONS/VILLAGES: a household's
// state/county/location/village columns aren't tied to the anchor's geo catalogue (mobile's
// manual-entry path stores free-typed place names with no catalogue node), so filtering against
// the catalogue silently matched nothing. Sourcing dropdowns from households on file keeps both in sync.
async function loadGeoLookups() {
  const scope = { organisationCode: genForm.value.organisationCode || undefined }
  try {
    const [s, c, l, v] = await Promise.all([
      dispatch<{ results: GeoNode[] }>('GET_HOUSEHOLD_LOCATIONS', { level: 'STATE', ...scope }),
      dispatch<{ results: GeoNode[] }>('GET_HOUSEHOLD_LOCATIONS', { level: 'COUNTY', ...scope }),
      dispatch<{ results: GeoNode[] }>('GET_HOUSEHOLD_LOCATIONS', { level: 'LOCATION', ...scope }),
      dispatch<{ results: GeoNode[] }>('GET_HOUSEHOLD_LOCATIONS', { level: 'VILLAGE', ...scope }),
    ])
    states.value = s.results
    counties.value = c.results
    locations.value = l.results
    villages.value = v.results
  } catch {
    // Geography filters just stay empty; households can still be picked by organisation alone.
  }
}

// GET_HOUSEHOLDS caps pageSize at 200 server-side, so page through it; capped at 2000 households.
async function fetchActiveHouseholds(): Promise<HouseholdOption[]> {
  const rows: HouseholdOption[] = []
  const pageSize = 200
  let page = 1
  while (rows.length < 2000) {
    const res = await dispatch<{ results: HouseholdOption[] }>('GET_HOUSEHOLDS', {
      organisationCode: genForm.value.organisationCode || undefined,
      stateCode: geo.value.stateCode || undefined,
      countyCode: geo.value.countyCode || undefined,
      locationCode: geo.value.locationCode || undefined,
      villageCode: geo.value.villageCode || undefined,
      status: 1, page, pageSize,
    })
    rows.push(...res.results.map((h) => ({ householdNumber: h.householdNumber, householdName: h.householdName })))
    if (res.results.length < pageSize) break
    page += 1
  }
  return rows
}

const householdOptions = ref<HouseholdOption[]>([])
const householdsLoading = ref(false)
// Names survive filter changes, so a picked household keeps its name in the table below.
const knownHouseholds = ref<Map<string, string>>(new Map())

const householdItems = computed(() =>
  householdOptions.value.map((h) => ({ ...h, title: `${h.householdName} (${h.householdNumber})` })),
)

const selectedHouseholds = computed(() =>
  genForm.value.householdNumbers.map((n) => ({ householdNumber: n, householdName: knownHouseholds.value.get(n) ?? n })),
)

async function loadHouseholdOptions() {
  householdsLoading.value = true
  try {
    householdOptions.value = await fetchActiveHouseholds()
    for (const h of householdOptions.value) knownHouseholds.value.set(h.householdNumber, h.householdName)
    if (!householdOptions.value.length) toast.error('No active households found for this filter')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load households')
  } finally {
    householdsLoading.value = false
  }
}

watch(
  [() => genForm.value.organisationCode, () => geo.value.stateCode, () => geo.value.countyCode, () => geo.value.locationCode, () => geo.value.villageCode],
  () => { if (!auth.isAnchor || genForm.value.organisationCode) loadHouseholdOptions() },
)

// Location dropdowns are scoped to the chosen organisation, so switching it needs a fresh fetch.
watch(() => genForm.value.organisationCode, () => {
  clearGeoFilters()
  loadGeoLookups()
})

// Unions with the existing selection (doesn't overwrite), so filtering to a new
// village and selecting again builds up the cycle instead of replacing it.
function selectAllMatchingHouseholds() {
  const existing = new Set(genForm.value.householdNumbers)
  for (const h of householdOptions.value) existing.add(h.householdNumber)
  genForm.value.householdNumbers = [...existing]
}

function removeHousehold(householdNumber: string) {
  genForm.value.householdNumbers = genForm.value.householdNumbers.filter((n) => n !== householdNumber)
}

function clearSelection() {
  genForm.value.householdNumbers = []
}

watch(dialogAnchorId, () => { genForm.value.organisationCode = null })

// ---- Export / import households via Excel ----
// Export ignores the location filters (unlike selectAllMatchingHouseholds) so the
// spreadsheet is the full candidate list for the organisation; households are then
// pruned inside Excel and the trimmed file re-imported to drive the actual selection.
const exportingHouseholds = ref(false)
const importDialog = ref(false)
const importFileName = ref('')
const importRows = ref<HouseholdOption[]>([])

async function exportHouseholdsToExcel() {
  exportingHouseholds.value = true
  try {
    const XLSX = await import('xlsx')
    const rows: HouseholdOption[] = []
    const pageSize = 200
    let page = 1
    while (rows.length < 2000) {
      const res = await dispatch<{ results: HouseholdOption[] }>('GET_HOUSEHOLDS', {
        organisationCode: genForm.value.organisationCode || undefined,
        status: 1, page, pageSize,
      })
      rows.push(...res.results.map((h) => ({ householdNumber: h.householdNumber, householdName: h.householdName })))
      if (res.results.length < pageSize) break
      page += 1
    }
    if (!rows.length) {
      toast.error('No active households found to export')
      return
    }
    for (const h of rows) knownHouseholds.value.set(h.householdNumber, h.householdName)
    const sheet = XLSX.utils.json_to_sheet(
      rows.map((h) => ({ 'Household Number': h.householdNumber, 'Household Name': h.householdName })),
    )
    const workbook = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(workbook, sheet, 'Households')
    XLSX.writeFile(workbook, `payment-cycle-households-${genForm.value.organisationCode || 'all'}.xlsx`)
    toast.success(`Exported ${rows.length} households — remove the ones you don't want, then import the file back`)
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to export households')
  } finally {
    exportingHouseholds.value = false
  }
}

function openImportDialog() {
  importFileName.value = ''
  importRows.value = []
  importDialog.value = true
}

function onImportFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  importFileName.value = file.name
  const reader = new FileReader()
  reader.onload = async () => {
    const XLSX = await import('xlsx')
    const workbook = XLSX.read(new Uint8Array(reader.result as ArrayBuffer), { type: 'array' })
    const sheet = workbook.Sheets[workbook.SheetNames[0]]
    const parsed = XLSX.utils.sheet_to_json<Record<string, unknown>>(sheet, { defval: '' })
    importRows.value = parsed
      .map((r) => ({
        householdNumber: String(r['Household Number'] ?? r.householdNumber ?? r['household number'] ?? '').trim(),
        householdName: String(r['Household Name'] ?? r.householdName ?? r['household name'] ?? '').trim(),
      }))
      .filter((h) => h.householdNumber)
  }
  reader.readAsArrayBuffer(file)
}

function confirmImport() {
  if (!importRows.value.length) return
  for (const h of importRows.value) if (h.householdName) knownHouseholds.value.set(h.householdNumber, h.householdName)
  genForm.value.householdNumbers = [...new Set(importRows.value.map((h) => h.householdNumber))]
  toast.success(`Imported ${genForm.value.householdNumbers.length} household(s) from ${importFileName.value}`)
  importDialog.value = false
}

// ---- Amount & currency ----
const rateLoading = ref(false)
const rateAsOf = ref('')
const rateError = ref('')

async function fetchExchangeRate(currency: string) {
  const quote = currency.trim().toUpperCase()
  genForm.value.currency = quote
  rateError.value = ''
  rateAsOf.value = ''
  if (!quote || quote === 'USD') {
    genForm.value.exchangeRate = 1
    rateAsOf.value = new Date().toISOString().slice(0, 10)
    return
  }
  rateLoading.value = true
  try {
    const response = await fetch(`https://api.frankfurter.dev/v2/rate/USD/${encodeURIComponent(quote)}`)
    if (!response.ok) throw new Error('Rate is unavailable for this currency')
    const result = await response.json() as { rate?: number; date?: string }
    if (!result.rate || result.rate <= 0) throw new Error('The exchange-rate service returned an invalid rate')
    genForm.value.exchangeRate = result.rate
    rateAsOf.value = result.date ?? ''
  } catch (err) {
    genForm.value.exchangeRate = 0
    rateError.value = err instanceof Error ? err.message : 'Unable to retrieve the exchange rate'
  } finally {
    rateLoading.value = false
  }
}

watch(() => genForm.value.currency, (currency) => fetchExchangeRate(currency))

// ---- Verify & confirm ----
const otpSent = ref(false)
const sendingOtp = ref(false)
const generating = ref(false)

// Doubles as resend: a new request supersedes the previous code (backend verifies the
// most recently issued one), so the stale entry is cleared here.
async function sendGenerateOtp() {
  sendingOtp.value = true
  try {
    await dispatch('REQUEST_PAYROLL_OTP', { action: 'GENERATE', actorEmail: auth.user?.email })
    genForm.value.otpCode = ''
    const wasResend = otpSent.value
    otpSent.value = true
    toast.success((wasResend ? 'New verification code sent to ' : 'Verification code sent to ') + auth.user?.email)
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to send code')
  } finally {
    sendingOtp.value = false
  }
}

async function confirmGenerate() {
  if (auth.isSystemAdmin && !dialogAnchorId.value) {
    toast.error('Select the anchor this payment cycle\'s organisation belongs to')
    return
  }
  generating.value = true
  try {
    // An Anchor Administrator generating for their own organisation is already the
    // approving authority, so the backend auto-approves (see Payroll.java's `generate()`).
    const result = await dispatch<{ autoApproved?: boolean }>('GENERATE_PAYROLL', {
      ...genForm.value,
      otpCode: genForm.value.otpCode.trim(),
      organisationCode: genForm.value.organisationCode || undefined,
      targetAnchorId: auth.isSystemAdmin ? dialogAnchorId.value ?? undefined : undefined,
    })
    toast.success(result.autoApproved ? 'Payroll cycle generated and approved' : 'Payroll cycle generated and pending approval')
    goToList()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to generate payroll')
  } finally {
    generating.value = false
  }
}

const canNextStep1 = computed(() =>
  !!genForm.value.periodStart && !!genForm.value.periodEnd
  && (!auth.isAnchor || !!genForm.value.organisationCode)
  && (!auth.isSystemAdmin || !!dialogAnchorId.value),
)
const canNextStep2 = computed(() => genForm.value.householdNumbers.length > 0)
const canNextStep3 = computed(() => !rateLoading.value && !rateError.value && !!genForm.value.amountPerHousehold && !!genForm.value.currency && !!genForm.value.exchangeRate)

onMounted(() => {
  resetDialogScope(null)
  loadGeoLookups()
  if (!auth.isAnchor) loadHouseholdOptions()
})
</script>

<template>
  <div>
    <div class="d-flex align-center mb-4 ga-3">
      <v-btn icon="mdi-arrow-left" variant="text" aria-label="Back to payment cycles" @click="goToList" />
      <h1 class="text-h5 font-weight-bold mb-0">Generate Payment Cycle</h1>
    </div>

    <v-card variant="flat" border>
      <v-card-text>
        <div class="d-flex align-center mb-6 step-header">
          <template v-for="(label, idx) in STEPS" :key="label">
            <div class="d-flex flex-column align-center step-node">
              <v-avatar
                size="32"
                :color="idx + 1 < step ? 'secondary' : idx + 1 === step ? 'primary' : undefined"
                :variant="idx + 1 <= step ? 'flat' : 'outlined'"
              >
                <v-icon v-if="idx + 1 < step" icon="mdi-check" size="18" color="white" />
                <span v-else :class="idx + 1 === step ? 'text-white' : 'text-medium-emphasis'">{{ idx + 1 }}</span>
              </v-avatar>
              <div class="text-caption mt-1 text-center" :class="idx + 1 < step ? 'text-secondary font-weight-medium' : idx + 1 === step ? 'font-weight-medium' : 'text-medium-emphasis'">
                {{ label }}
              </div>
            </div>
            <v-divider v-if="idx < STEPS.length - 1" class="flex-grow-1 mx-2 step-divider" :class="idx + 1 < step ? 'border-secondary' : ''" />
          </template>
        </div>

        <!-- Step 1: Period -->
        <div v-if="step === 1">
          <v-select
            v-if="auth.isSystemAdmin"
            v-model="dialogAnchorId"
            :items="anchors" item-title="name" item-value="id"
            label="Anchor" class="mb-2" placeholder="Choose an anchor" required
          />
          <v-select
            v-if="auth.isAnchor"
            v-model="genForm.organisationCode"
            :items="dialogOrganizations" item-title="name" item-value="organisationCode"
            label="Organisation" class="mb-2" placeholder="Choose an organisation"
            :disabled="auth.isSystemAdmin && !dialogAnchorId" required
          />
          <v-text-field v-model="genForm.periodStart" label="Period start" type="date" />
          <v-text-field v-model="genForm.periodEnd" label="Period end" type="date" />
          <div class="d-flex justify-end">
            <v-btn color="secondary" :disabled="!canNextStep1" @click="step = 2">Next</v-btn>
          </div>
        </div>

        <!-- Step 2: Households, filterable by state/county/location/village -->
        <div v-else-if="step === 2">
          <div class="text-body-2 text-medium-emphasis mb-3">Narrow by location, then select the households this cycle is for</div>
          <v-row dense>
            <v-col cols="6" sm="3">
              <v-select v-model="geo.stateCode" :items="states" item-title="name" item-value="code" label="State" clearable hide-details density="compact" />
            </v-col>
            <v-col cols="6" sm="3">
              <v-select v-model="geo.countyCode" :items="countiesForState" item-title="name" item-value="code" label="County" clearable hide-details density="compact" />
            </v-col>
            <v-col cols="6" sm="3">
              <v-select v-model="geo.locationCode" :items="locationsForCounty" item-title="name" item-value="code" label="Location" clearable hide-details density="compact" />
            </v-col>
            <v-col cols="6" sm="3">
              <v-select v-model="geo.villageCode" :items="villagesForLocation" item-title="name" item-value="code" label="Village" clearable hide-details density="compact" />
            </v-col>
          </v-row>

          <div class="d-flex align-center justify-space-between mt-3 mb-2 flex-wrap ga-2">
            <v-btn variant="text" size="small" :disabled="!geo.stateCode && !geo.countyCode && !geo.locationCode && !geo.villageCode" @click="clearGeoFilters">
              Clear location filter
            </v-btn>
            <div class="d-flex ga-2 flex-wrap">
              <v-btn
                variant="outlined" size="small" color="secondary" prepend-icon="mdi-file-excel"
                :loading="exportingHouseholds" :disabled="!genForm.organisationCode && auth.isAnchor" @click="exportHouseholdsToExcel"
              >
                Export to Excel
              </v-btn>
              <v-btn variant="outlined" size="small" color="secondary" prepend-icon="mdi-file-upload-outline" @click="openImportDialog">
                Import from Excel
              </v-btn>
              <v-btn variant="tonal" size="small" color="secondary" :loading="householdsLoading" :disabled="!householdOptions.length" @click="selectAllMatchingHouseholds">
                Select all matching ({{ householdOptions.length }})
              </v-btn>
            </div>
          </div>

          <v-autocomplete
            v-model="genForm.householdNumbers"
            :items="householdItems"
            item-title="title"
            item-value="householdNumber"
            :loading="householdsLoading"
            multiple chips closable-chips small-chips
            label="Add households individually"
            :no-data-text="householdsLoading ? 'Loading…' : 'No active households found for this filter'"
          />

          <div class="d-flex align-center justify-space-between mt-4 mb-2">
            <span class="text-subtitle-2">Selected households ({{ selectedHouseholds.length }})</span>
            <v-btn v-if="selectedHouseholds.length" variant="text" size="small" color="error" @click="clearSelection">Remove all</v-btn>
          </div>
          <v-table v-if="selectedHouseholds.length" density="compact" style="max-height: 260px; overflow-y: auto">
            <thead>
              <tr>
                <th>Household</th>
                <th>Number</th>
                <th class="text-right">Remove</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="h in selectedHouseholds" :key="h.householdNumber">
                <td>{{ h.householdName }}</td>
                <td>{{ h.householdNumber }}</td>
                <td class="text-right">
                  <v-btn icon="mdi-close" variant="text" size="small" color="error" :aria-label="`Remove ${h.householdName}`" @click="removeHousehold(h.householdNumber)" />
                </td>
              </tr>
            </tbody>
          </v-table>
          <v-alert v-else type="info" variant="tonal" density="compact">No households selected yet.</v-alert>

          <div class="d-flex justify-space-between mt-4">
            <v-btn variant="outlined" prepend-icon="mdi-arrow-left" @click="prevStep">Back</v-btn>
            <v-btn color="secondary" :disabled="!canNextStep2" @click="step = 3">Next</v-btn>
          </div>
        </div>

        <!-- Step 3: Amount & currency -->
        <div v-else-if="step === 3">
          <v-text-field v-model.number="genForm.amountPerHousehold" label="Amount per household (amount out)" type="number" placeholder="e.g. 5000" />
          <v-row dense>
            <v-col cols="6">
              <v-autocomplete v-model="genForm.currency" :items="CURRENCIES" label="Payout currency" />
            </v-col>
            <v-col cols="6">
              <v-text-field
                v-model.number="genForm.exchangeRate" label="USD exchange rate" type="number"
                readonly :loading="rateLoading" hint="Picked automatically from the latest reference rate" persistent-hint
              />
            </v-col>
          </v-row>
          <v-alert v-if="rateError" type="error" variant="tonal" density="compact" class="mb-3">
            {{ rateError }}. Choose another currency or try again.
          </v-alert>
          <v-alert v-else type="info" variant="tonal" density="compact" class="mb-3">
            USD 1 = {{ genForm.currency }} {{ genForm.exchangeRate }}{{ rateAsOf ? `, reference date ${rateAsOf}` : '' }}. The rate is locked into this cycle when generated.
          </v-alert>
          <div class="d-flex justify-space-between mt-4">
            <v-btn variant="outlined" prepend-icon="mdi-arrow-left" @click="prevStep">Back</v-btn>
            <v-btn color="secondary" :disabled="!canNextStep3" @click="step = 4">Next</v-btn>
          </div>
        </div>

        <!-- Step 4: Verify & confirm -->
        <div v-else>
          <v-alert type="info" variant="tonal" density="compact" class="mb-3">
            {{ genForm.householdNumbers.length }} households · {{ genForm.currency }} {{ genForm.amountPerHousehold }} out per household
            (rate {{ genForm.exchangeRate }}). A verification code will be emailed to {{ auth.user?.email }} before this cycle is generated.
          </v-alert>
          <v-btn v-if="!otpSent" color="secondary" block :loading="sendingOtp" @click="sendGenerateOtp">Send Verification Code</v-btn>
          <template v-else>
            <v-text-field v-model="genForm.otpCode" label="Verification code" placeholder="6-digit code" maxlength="6" />
            <div class="d-flex justify-end mb-3">
              <v-btn variant="text" size="small" color="secondary" :loading="sendingOtp" @click="sendGenerateOtp">
                Resend code
              </v-btn>
            </div>
            <v-btn color="secondary" block :loading="generating" :disabled="!genForm.otpCode" @click="confirmGenerate">
              Confirm & Generate
            </v-btn>
          </template>
          <div class="d-flex justify-start mt-4">
            <v-btn variant="outlined" prepend-icon="mdi-arrow-left" :disabled="generating" @click="prevStep">Back</v-btn>
          </div>
        </div>
      </v-card-text>
    </v-card>

    <v-dialog v-model="importDialog" max-width="560">
      <v-card>
        <dialog-close-button @close="importDialog = false" />
        <v-card-title>Import households from Excel</v-card-title>
        <v-card-text>
          <v-alert type="info" variant="tonal" density="compact" class="mb-3">
            Upload the file you exported (or any .xlsx with "Household Number" / "Household Name" columns).
            The households in this file replace your current selection.
          </v-alert>
          <v-file-input label="Upload .xlsx" accept=".xlsx,.xls" prepend-icon="mdi-file-upload" @change="onImportFile" />
          <div v-if="importRows.length" class="text-caption mb-2">{{ importRows.length }} household(s) ready from {{ importFileName }}</div>
          <v-table v-if="importRows.length" density="compact" style="max-height: 260px; overflow-y: auto">
            <thead>
              <tr>
                <th>Household</th>
                <th>Number</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="h in importRows" :key="h.householdNumber">
                <td>{{ h.householdName || '—' }}</td>
                <td>{{ h.householdNumber }}</td>
              </tr>
            </tbody>
          </v-table>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="importDialog = false">Cancel</v-btn>
          <v-btn color="secondary" :disabled="!importRows.length" @click="confirmImport">
            Use these households
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.step-node { min-width: 88px; }
.step-divider { margin-bottom: 22px; }
</style>
