<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { downloadCsv, parseCsv, toCsv } from '@/utils/csv'

interface HouseholdRow {
  householdNumber: string
  householdName: string
  organisationCode: string
  age?: number
  gender?: string
  phoneNumber?: string
  householdSize?: number
  bomaCode?: string
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
const loading = ref(true)
const households = ref<HouseholdRow[]>([])
const dialog = ref(false)
const saving = ref(false)
const detailDialog = ref(false)
const detail = ref<Record<string, any> | null>(null)

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
  { title: 'Status', key: 'status' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'end' as const },
]

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

function clearFilters() {
  filters.value = { organisationCode: null, stateCode: null, countyCode: null, locationCode: null, villageCode: null, gender: null, status: null, search: '' }
  load()
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

async function viewDetail(row: HouseholdRow) {
  try {
    const [h, alts] = await Promise.all([
      dispatch<{ results: any[] }>('GET_HOUSEHOLD', { householdNumber: row.householdNumber }),
      dispatch<{ results: any[] }>('GET_ALTERNATES', { householdNumber: row.householdNumber }),
    ])
    detail.value = { ...h.results[0], alternates: alts.results }
    detailDialog.value = true
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load household')
  }
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
        <v-btn variant="outlined" prepend-icon="mdi-file-upload" @click="openBulk">Bulk Upload</v-btn>
        <v-btn color="primary" prepend-icon="mdi-home-plus" @click="openCreate">Add Household</v-btn>
      </div>
    </div>

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

    <v-dialog v-model="detailDialog" max-width="640">
      <v-card v-if="detail">
        <v-card-title>{{ detail.householdName }}</v-card-title>
        <v-card-text>
          <v-tabs v-model="detail._tab" color="primary">
            <v-tab value="info">Info</v-tab>
            <v-tab value="alternates">Alternates ({{ detail.alternates?.length ?? 0 }})</v-tab>
            <v-tab value="fingerprints">Fingerprints</v-tab>
          </v-tabs>
          <v-window v-model="detail._tab" class="pt-4">
            <v-window-item value="info">
              <div>Household #: {{ detail.householdNumber }}</div>
              <div>Organization: {{ detail.organisationCode }}</div>
              <div>Phone: {{ detail.phoneNumber }}</div>
              <div>Fingerprint status: <v-chip size="small">{{ detail.fingerprintStatus }}</v-chip></div>
              <div>Image status: <v-chip size="small">{{ detail.imageStatus }}</v-chip></div>
            </v-window-item>
            <v-window-item value="alternates">
              <v-list>
                <v-list-item v-for="a in detail.alternates" :key="a.alternateNumber" :title="a.alternateName" :subtitle="a.relationship" />
                <v-list-item v-if="!detail.alternates?.length" title="No alternates registered" />
              </v-list>
            </v-window-item>
            <v-window-item value="fingerprints">
              <v-alert type="info" variant="tonal" density="compact">
                Fingerprints are captured through the BioPay Android field app.
              </v-alert>
            </v-window-item>
          </v-window>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="detailDialog = false">Close</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
