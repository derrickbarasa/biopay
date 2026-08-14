<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { downloadCsv, parseCsv, toCsv } from '@/utils/csv'
import BarChart from '@/components/BarChart.vue'

interface HouseholdRow {
  householdNumber: string
  householdName: string
  organisationCode: string
  age?: number
  gender?: string
  phoneNumber?: string
  householdSize?: number
  bomaCode?: string
  vulnerabilityStatus?: string
  legalStatus?: string
  voucherCount?: number
  paymentCycleCount?: number
  status: number
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
const loading = ref(true)
const households = ref<HouseholdRow[]>([])
const dialog = ref(false)
const saving = ref(false)
const showBreakdown = ref(false)

const states = ref<GeoNode[]>([])
const counties = ref<GeoNode[]>([])
const locations = ref<GeoNode[]>([])
const villages = ref<GeoNode[]>([])
const organizations = ref<{ organisationCode: string; name: string }[]>([])

// ---- Filters -- each one its own backend query parameter, no client-side search ----
const filters = ref({
  organisationCode: null as string | null,
  stateCode: null as string | null,
  countyCode: null as string | null,
  locationCode: null as string | null,
  villageCode: null as string | null,
  gender: null as string | null,
  status: null as number | null,
  vulnerabilityStatus: '',
  legalStatus: '',
  dateFrom: null as string | null,
  dateTo: null as string | null,
  search: '',
})
let searchDebounce: ReturnType<typeof setTimeout> | null = null

function onSearchInput() {
  if (searchDebounce) clearTimeout(searchDebounce)
  searchDebounce = setTimeout(load, 400)
}

const form = ref({
  householdName: '', age: null as number | null, gender: '', phoneNumber: '',
  householdSize: null as number | null, stateCode: '', countyCode: '', locationCode: '', villageCode: '',
})

const headers = [
  { title: 'Household #', key: 'householdNumber' },
  { title: 'Head of Household', key: 'householdName' },
  { title: 'Organization', key: 'organisationCode' },
  { title: 'Village', key: 'bomaCode' },
  { title: 'Size', key: 'householdSize' },
  { title: 'Vouchers', key: 'voucherCount' },
  { title: 'Cycles', key: 'paymentCycleCount' },
  { title: 'Status', key: 'status' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'end' as const },
]

// ---- Client-side breakdown graphs over the currently loaded (filtered) rows ----
const genderBreakdown = computed(() => {
  const counts = { Male: 0, Female: 0, Other: 0 }
  for (const h of households.value) {
    if (h.gender === 'M') counts.Male++
    else if (h.gender === 'F') counts.Female++
    else counts.Other++
  }
  return [
    { label: 'Male', value: counts.Male },
    { label: 'Female', value: counts.Female },
    { label: 'Other', value: counts.Other },
  ]
})

const ageBreakdown = computed(() => {
  const buckets = [
    { label: '0-17', value: 0 }, { label: '18-34', value: 0 }, { label: '35-49', value: 0 },
    { label: '50-64', value: 0 }, { label: '65+', value: 0 }, { label: 'Unknown', value: 0 },
  ]
  for (const h of households.value) {
    const a = h.age
    if (a == null) buckets[5].value++
    else if (a < 18) buckets[0].value++
    else if (a < 35) buckets[1].value++
    else if (a < 50) buckets[2].value++
    else if (a < 65) buckets[3].value++
    else buckets[4].value++
  }
  return buckets
})

const statusBreakdown = computed(() => [
  { label: 'Active', value: households.value.filter((h) => h.status === 1).length },
  { label: 'Inactive', value: households.value.filter((h) => h.status !== 1).length },
])

// Groups the loaded rows by a free-text attribute (vulnerability / legal status),
// counting blanks as "Unspecified". Used for the two attribute breakdown charts.
function groupByAttribute(pick: (h: HouseholdRow) => string | undefined) {
  const counts = new Map<string, number>()
  for (const h of households.value) {
    const key = (pick(h) || 'Unspecified').trim() || 'Unspecified'
    counts.set(key, (counts.get(key) ?? 0) + 1)
  }
  return Array.from(counts, ([label, value]) => ({ label, value }))
}

const vulnerabilityBreakdown = computed(() => groupByAttribute((h) => h.vulnerabilityStatus))
const legalBreakdown = computed(() => groupByAttribute((h) => h.legalStatus))

const countiesForState = (stateCode: string) => stateCode ? counties.value.filter((c) => c.stateCode === stateCode) : counties.value
const locationsForCounty = (countyCode: string) => countyCode ? locations.value.filter((l) => l.countyCode === countyCode) : locations.value
const villagesForLocation = (locationCode: string) => locationCode ? villages.value.filter((v) => v.locationCode === locationCode) : villages.value

async function loadGeo() {
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
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load locations')
  }
}

async function load() {
  loading.value = true
  try {
    const res = await dispatch<{ results: HouseholdRow[] }>('GET_HOUSEHOLDS', {
      pageSize: 100,
      organisationCode: filters.value.organisationCode ?? undefined,
      stateCode: filters.value.stateCode ?? undefined,
      countyCode: filters.value.countyCode ?? undefined,
      locationCode: filters.value.locationCode ?? undefined,
      villageCode: filters.value.villageCode ?? undefined,
      gender: filters.value.gender ?? undefined,
      status: filters.value.status ?? undefined,
      vulnerabilityStatus: filters.value.vulnerabilityStatus || undefined,
      legalStatus: filters.value.legalStatus || undefined,
      dateFrom: filters.value.dateFrom || undefined,
      dateTo: filters.value.dateTo || undefined,
      search: filters.value.search || undefined,
    })
    households.value = res.results
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load households')
  } finally {
    loading.value = false
  }
}

// Every select filter re-queries immediately; the text filter debounces via onSearchInput
// instead (bound directly on the field), so typing doesn't fire a request per keystroke.
// Cascading geo filters clear their narrower selections when a broader one changes.
watch(() => filters.value.stateCode, () => { filters.value.countyCode = null; filters.value.locationCode = null; filters.value.villageCode = null; load() })
watch(() => filters.value.countyCode, () => { filters.value.locationCode = null; filters.value.villageCode = null; load() })
watch(() => filters.value.locationCode, () => { filters.value.villageCode = null; load() })
watch(() => filters.value.villageCode, load)
watch(() => filters.value.organisationCode, load)
watch(() => filters.value.gender, load)
watch(() => filters.value.status, load)
watch(() => filters.value.dateFrom, load)
watch(() => filters.value.dateTo, load)

function clearFilters() {
  filters.value = { organisationCode: null, stateCode: null, countyCode: null, locationCode: null, villageCode: null, gender: null, status: null, vulnerabilityStatus: '', legalStatus: '', dateFrom: null, dateTo: null, search: '' }
  load()
}

// Exports the currently loaded (i.e. filtered) household rows to CSV.
function exportCsv() {
  if (!households.value.length) {
    toast.error('No households to export')
    return
  }
  const csv = toCsv(
    ['Household #', 'Head of Household', 'Organization', 'Age', 'Gender', 'Phone', 'Size', 'Village', 'Vouchers', 'Payment Cycles', 'Status'],
    households.value.map((h) => [
      h.householdNumber, h.householdName, h.organisationCode, h.age ?? '', h.gender ?? '',
      h.phoneNumber ?? '', h.householdSize ?? '', h.bomaCode ?? '', h.voucherCount ?? 0, h.paymentCycleCount ?? 0,
      h.status === 1 ? 'Active' : 'Inactive',
    ]),
  )
  downloadCsv(`households-${new Date().toISOString().slice(0, 10)}.csv`, csv)
}

onMounted(() => { load(); loadGeo() })

function openCreate() {
  form.value = { householdName: '', age: null, gender: '', phoneNumber: '', householdSize: null, stateCode: '', countyCode: '', locationCode: '', villageCode: '' }
  dialog.value = true
}

async function save() {
  saving.value = true
  try {
    await dispatch('CREATE_HOUSEHOLD', {
      householdName: form.value.householdName, age: form.value.age, gender: form.value.gender,
      phoneNumber: form.value.phoneNumber, householdSize: form.value.householdSize,
      stateCode: form.value.stateCode, countyCode: form.value.countyCode,
      payamCode: form.value.locationCode, bomaCode: form.value.villageCode,
    })
    toast.success('Household registered')
    dialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Save failed')
  } finally {
    saving.value = false
  }
}

function viewDetail(row: HouseholdRow) {
  router.push({ name: 'household-detail', params: { householdNumber: row.householdNumber } })
}

async function remove(row: HouseholdRow) {
  try {
    await dispatch('DELETE_HOUSEHOLD', { householdNumber: row.householdNumber })
    toast.success('Household deleted')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Delete failed')
  }
}

// ---- Bulk upload (one CSV template upload = one village's batch) ---------------

const bulkDialog = ref(false)
const bulkStateCode = ref('')
const bulkCountyCode = ref('')
const bulkLocationCode = ref('')
const bulkVillageCode = ref('')
const bulkFileName = ref('')
const bulkRows = ref<Record<string, string>[]>([])
const bulkResult = ref<{ successCount: number; failureCount: number; errors: { row: number; message: string }[] } | null>(null)

const TEMPLATE_HEADERS = ['householdName', 'age', 'gender', 'maritalStatus', 'spouseName', 'idNumber', 'phoneNumber', 'householdSize', 'femaleDependants', 'maleDependants']

function openBulk() {
  bulkStateCode.value = ''; bulkCountyCode.value = ''; bulkLocationCode.value = ''; bulkVillageCode.value = ''
  bulkFileName.value = ''; bulkRows.value = []; bulkResult.value = null
  bulkDialog.value = true
}

function downloadTemplate() {
  downloadCsv('household-upload-template.csv', toCsv(TEMPLATE_HEADERS, [
    ['Jane Doe', '34', 'F', 'Married', 'John Doe', 'ID123456', '+211900000000', '5', '2', '1'],
  ]))
}

function onBulkFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  bulkFileName.value = file.name
  const reader = new FileReader()
  reader.onload = () => {
    bulkRows.value = parseCsv(String(reader.result ?? '')).filter((r) => r.householdName)
  }
  reader.readAsText(file)
}

const bulkReady = computed(() => !!bulkVillageCode.value && bulkRows.value.length > 0)

async function submitBulk() {
  if (!bulkReady.value) return
  saving.value = true
  try {
    const rows = bulkRows.value.map((r) => ({
      householdName: r.householdName,
      age: r.age ? Number(r.age) : undefined,
      gender: r.gender || undefined,
      maritalStatus: r.maritalStatus || undefined,
      spouseName: r.spouseName || undefined,
      idNumber: r.idNumber || undefined,
      phoneNumber: r.phoneNumber || undefined,
      householdSize: r.householdSize ? Number(r.householdSize) : undefined,
      femaleDependants: r.femaleDependants ? Number(r.femaleDependants) : undefined,
      maleDependants: r.maleDependants ? Number(r.maleDependants) : undefined,
    }))
    const res = await dispatch<{ successCount: number; failureCount: number; errors: { row: number; message: string }[] }>(
      'BULK_UPLOAD_HOUSEHOLDS',
      { villageCode: bulkVillageCode.value, fileName: bulkFileName.value, rows },
    )
    bulkResult.value = res
    toast.success(`${res.successCount} household(s) registered, ${res.failureCount} failed`)
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Bulk upload failed')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <div class="d-flex align-center justify-space-between mb-4">
      <h1 class="text-h5 font-weight-bold">Households</h1>
      <div class="d-flex ga-2">
        <v-btn
          variant="outlined"
          :prepend-icon="showBreakdown ? 'mdi-chart-box-outline' : 'mdi-chart-bar'"
          @click="showBreakdown = !showBreakdown"
        >
          {{ showBreakdown ? 'Hide' : 'Show' }} Breakdown
        </v-btn>
        <v-btn variant="outlined" prepend-icon="mdi-download" @click="exportCsv">Export CSV</v-btn>
        <v-btn variant="outlined" prepend-icon="mdi-file-upload" @click="openBulk">Bulk Upload</v-btn>
        <v-btn color="primary" prepend-icon="mdi-home-plus" @click="openCreate">Add Household</v-btn>
      </div>
    </div>

    <v-expand-transition>
      <v-row v-if="showBreakdown" dense class="mb-2">
        <v-col cols="12" md="4">
          <v-card variant="flat" border>
            <v-card-title class="text-subtitle-2">By gender</v-card-title>
            <v-card-text><BarChart :data="genderBreakdown" color="#0D9488" /></v-card-text>
          </v-card>
        </v-col>
        <v-col cols="12" md="4">
          <v-card variant="flat" border>
            <v-card-title class="text-subtitle-2">By age group</v-card-title>
            <v-card-text><BarChart :data="ageBreakdown" color="#0F766E" /></v-card-text>
          </v-card>
        </v-col>
        <v-col cols="12" md="4">
          <v-card variant="flat" border>
            <v-card-title class="text-subtitle-2">By status</v-card-title>
            <v-card-text><BarChart :data="statusBreakdown" color="#F59E0B" /></v-card-text>
          </v-card>
        </v-col>
        <v-col cols="12" md="6">
          <v-card variant="flat" border>
            <v-card-title class="text-subtitle-2">By vulnerability status</v-card-title>
            <v-card-text><BarChart :data="vulnerabilityBreakdown" color="#2196F3" /></v-card-text>
          </v-card>
        </v-col>
        <v-col cols="12" md="6">
          <v-card variant="flat" border>
            <v-card-title class="text-subtitle-2">By legal status</v-card-title>
            <v-card-text><BarChart :data="legalBreakdown" color="#0F766E" /></v-card-text>
          </v-card>
        </v-col>
      </v-row>
    </v-expand-transition>

    <v-card variant="flat" border>
      <v-card-text>
        <v-row dense align="center">
          <v-col v-if="auth.isAnchor" cols="12" sm="6" md="3">
            <v-select v-model="filters.organisationCode" :items="organizations" item-title="name" item-value="organisationCode" label="Organisation" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="6" sm="3" md="2">
            <v-select v-model="filters.stateCode" :items="states" item-title="name" item-value="code" label="State" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="6" sm="3" md="2">
            <v-select v-model="filters.countyCode" :items="countiesForState(filters.stateCode ?? '')" item-title="name" item-value="code" label="County" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="6" sm="3" md="2">
            <v-select v-model="filters.locationCode" :items="locationsForCounty(filters.countyCode ?? '')" item-title="name" item-value="code" label="Location" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="6" sm="3" md="2">
            <v-select v-model="filters.villageCode" :items="villagesForLocation(filters.locationCode ?? '')" item-title="name" item-value="code" label="Village" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="6" sm="3" md="2">
            <v-select v-model="filters.gender" :items="[{ title: 'Male', value: 'M' }, { title: 'Female', value: 'F' }]" label="Gender" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="6" sm="3" md="2">
            <v-select v-model="filters.status" :items="[{ title: 'Active', value: 1 }, { title: 'Inactive', value: 0 }]" label="Status" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="6" sm="3" md="2">
            <v-text-field
              v-model="filters.vulnerabilityStatus" label="Vulnerability status"
              clearable hide-details density="compact" @update:model-value="onSearchInput" @click:clear="load"
            />
          </v-col>
          <v-col cols="6" sm="3" md="2">
            <v-text-field
              v-model="filters.legalStatus" label="Legal status"
              clearable hide-details density="compact" @update:model-value="onSearchInput" @click:clear="load"
            />
          </v-col>
          <v-col cols="6" sm="3" md="2">
            <v-text-field v-model="filters.dateFrom" label="Registered from" type="date" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="6" sm="3" md="2">
            <v-text-field v-model="filters.dateTo" label="Registered to" type="date" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="12" sm="6" md="3">
            <v-text-field
              v-model="filters.search" prepend-inner-icon="mdi-magnify" label="Household name, number or ID"
              clearable hide-details density="compact" @update:model-value="onSearchInput" @click:clear="load"
            />
          </v-col>
          <v-col cols="auto">
            <v-btn variant="text" size="small" @click="clearFilters">Clear filters</v-btn>
          </v-col>
        </v-row>
      </v-card-text>
      <v-data-table :headers="headers" :items="households" :loading="loading">
        <template #item.voucherCount="{ item }">
          <v-chip size="small" :color="item.voucherCount ? 'primary' : undefined" variant="tonal">
            {{ item.voucherCount ?? 0 }}
          </v-chip>
        </template>
        <template #item.paymentCycleCount="{ item }">
          <v-chip size="small" :color="item.paymentCycleCount ? 'secondary' : undefined" variant="tonal">
            {{ item.paymentCycleCount ?? 0 }}
          </v-chip>
        </template>
        <template #item.status="{ item }">
          <v-chip size="small" :color="item.status === 1 ? 'success' : 'error'" variant="tonal">
            {{ item.status === 1 ? 'Active' : 'Inactive' }}
          </v-chip>
        </template>
        <template #item.actions="{ item }">
          <v-btn icon="mdi-eye" variant="text" size="small" @click="viewDetail(item)" />
          <v-btn icon="mdi-delete" variant="text" size="small" color="error" @click="remove(item)" />
        </template>
      </v-data-table>
    </v-card>

    <v-dialog v-model="dialog" max-width="560">
      <v-card>
        <v-card-title>Add Household</v-card-title>
        <v-card-text>
          <v-text-field v-model="form.householdName" label="Head of household name" />
          <v-row>
            <v-col cols="6"><v-text-field v-model.number="form.age" label="Age" type="number" /></v-col>
            <v-col cols="6">
              <v-select v-model="form.gender" label="Gender" :items="['M', 'F']" />
            </v-col>
          </v-row>
          <v-text-field v-model="form.phoneNumber" label="Phone number" />
          <v-text-field v-model.number="form.householdSize" label="Household size" type="number" />
          <div class="text-caption text-medium-emphasis mt-2 mb-1">Location</div>
          <v-row dense>
            <v-col cols="6"><v-select v-model="form.stateCode" :items="states" item-title="name" item-value="code" label="State" density="compact" /></v-col>
            <v-col cols="6"><v-select v-model="form.countyCode" :items="countiesForState(form.stateCode)" item-title="name" item-value="code" label="County" density="compact" /></v-col>
            <v-col cols="6"><v-select v-model="form.locationCode" :items="locationsForCounty(form.countyCode)" item-title="name" item-value="code" label="Location" density="compact" /></v-col>
            <v-col cols="6"><v-select v-model="form.villageCode" :items="villagesForLocation(form.locationCode)" item-title="name" item-value="code" label="Village" density="compact" /></v-col>
          </v-row>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="dialog = false">Cancel</v-btn>
          <v-btn color="primary" :loading="saving" @click="save">Save</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="bulkDialog" max-width="620">
      <v-card>
        <v-card-title>Bulk Upload Households</v-card-title>
        <v-card-text>
          <v-alert type="info" variant="tonal" density="compact" class="mb-3">
            Pick the village this batch belongs to, download the template, fill in one row per household, then upload it here.
          </v-alert>
          <v-row dense>
            <v-col cols="4"><v-select v-model="bulkStateCode" :items="states" item-title="name" item-value="code" label="State" density="compact" /></v-col>
            <v-col cols="4"><v-select v-model="bulkCountyCode" :items="countiesForState(bulkStateCode)" item-title="name" item-value="code" label="County" density="compact" /></v-col>
            <v-col cols="4"><v-select v-model="bulkLocationCode" :items="locationsForCounty(bulkCountyCode)" item-title="name" item-value="code" label="Location" density="compact" /></v-col>
          </v-row>
          <v-select v-model="bulkVillageCode" :items="villagesForLocation(bulkLocationCode)" item-title="name" item-value="code" label="Village" density="compact" />

          <v-btn variant="outlined" size="small" prepend-icon="mdi-download" class="my-3" @click="downloadTemplate">
            Download Template
          </v-btn>
          <v-file-input label="Upload filled CSV" accept=".csv" prepend-icon="mdi-file-upload" :disabled="!bulkVillageCode" @change="onBulkFile" />
          <div v-if="bulkRows.length" class="text-caption mb-2">{{ bulkRows.length }} row(s) ready to upload from {{ bulkFileName }}</div>

          <v-alert v-if="bulkResult" :type="bulkResult.failureCount ? 'warning' : 'success'" variant="tonal" density="compact" class="mt-2">
            {{ bulkResult.successCount }} registered, {{ bulkResult.failureCount }} failed
            <div v-for="e in bulkResult.errors.slice(0, 5)" :key="e.row">Row {{ e.row }}: {{ e.message }}</div>
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="bulkDialog = false">Close</v-btn>
          <v-btn color="primary" :loading="saving" :disabled="!bulkReady" @click="submitBulk">Upload</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

  </div>
</template>
