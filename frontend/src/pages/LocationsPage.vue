<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
// xlsx is dynamically imported (see downloadTemplate/onBulkFile) rather than a static
// top-level import -- it's a large parser only ever needed inside the bulk-upload dialog,
// and statically importing it pushed this page's chunk past Vite's 500kB warning threshold.
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'
import { COUNTRIES } from '@/utils/countries'

interface GeoNode {
  code: string
  name: string
  stateCode?: string
  countyCode?: string
  locationCode?: string
  country?: string
}

const toast = useToast()
const tab = ref('states')

const states = ref<GeoNode[]>([])
const counties = ref<GeoNode[]>([])
const locations = ref<GeoNode[]>([])
const villages = ref<GeoNode[]>([])

const countyStateFilter = ref<string | null>(null)
const locationStateFilter = ref<string | null>(null)
const locationCountyFilter = ref<string | null>(null)
const villageStateFilter = ref<string | null>(null)
const villageCountyFilter = ref<string | null>(null)
const villageLocationFilter = ref<string | null>(null)

const loading = ref(false)

// Shared free-text search for the active tab's data table; cleared on tab switch.
const tableSearch = ref('')
watch(tab, () => { tableSearch.value = '' })

const actionsHeader = { title: 'Actions', key: 'actions', sortable: false, align: 'end' as const }
const stateHeaders = [{ title: 'Code', key: 'code' }, { title: 'Name', key: 'name' }, { title: 'Country', key: 'country' }, actionsHeader]
const countyHeaders = [{ title: 'Code', key: 'code' }, { title: 'Name', key: 'name' }, { title: 'State', key: 'stateCode' }, actionsHeader]
const locationHeaders = [{ title: 'Code', key: 'code' }, { title: 'Name', key: 'name' }, { title: 'County', key: 'countyCode' }, actionsHeader]
const villageHeaders = [{ title: 'Code', key: 'code' }, { title: 'Name', key: 'name' }, { title: 'Location', key: 'locationCode' }, actionsHeader]

const dialog = ref(false)
const dialogLevel = ref<'STATE' | 'COUNTY' | 'LOCATION' | 'VILLAGE'>('STATE')
const saving = ref(false)
// Code is no longer typed in -- the backend generates <countryPrefix><sequence>
// (e.g. KE2000) from the chosen country (states) or the ancestor state's country.
const form = ref({ name: '', stateCode: '', countyCode: '', locationCode: '', country: '' })

async function loadStates() {
  const res = await dispatch<{ results: GeoNode[] }>('GET_STATES')
  states.value = res.results
}
async function loadCounties() {
  const res = await dispatch<{ results: GeoNode[] }>('GET_COUNTIES', { stateCode: countyStateFilter.value })
  counties.value = res.results
}
async function loadLocations() {
  const res = await dispatch<{ results: GeoNode[] }>('GET_LOCATIONS', { countyCode: locationCountyFilter.value })
  locations.value = res.results
}
async function loadVillages() {
  const res = await dispatch<{ results: GeoNode[] }>('GET_VILLAGES', { locationCode: villageLocationFilter.value })
  villages.value = res.results
}

// Parent-level columns (a county's state, a location's county, a village's location)
// show the parent's name, not its raw code.
const stateNameByCode = computed(() => new Map(states.value.map((s) => [s.code, s.name])))
const countyNameByCode = computed(() => new Map(counties.value.map((c) => [c.code, c.name])))
const locationNameByCode = computed(() => new Map(locations.value.map((l) => [l.code, l.name])))
function stateName(code?: string) { return (code && stateNameByCode.value.get(code)) || code || '—' }
function countyName(code?: string) { return (code && countyNameByCode.value.get(code)) || code || '—' }
function locationName(code?: string) { return (code && locationNameByCode.value.get(code)) || code || '—' }

async function loadAll() {
  loading.value = true
  try {
    await loadStates()
    await Promise.all([loadCounties(), loadLocations(), loadVillages()])
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load locations')
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
watch(countyStateFilter, loadCounties)
watch(locationCountyFilter, loadLocations)
watch(villageLocationFilter, loadVillages)

const countiesForState = (stateCode: string | null) => stateCode ? counties.value.filter((c) => c.stateCode === stateCode) : counties.value
const locationsForCounty = (countyCode: string | null) => countyCode ? locations.value.filter((l) => l.countyCode === countyCode) : locations.value

function openCreate(level: typeof dialogLevel.value) {
  dialogLevel.value = level
  form.value = {
    name: '',
    stateCode: level === 'COUNTY' ? (countyStateFilter.value ?? '') : (level === 'LOCATION' ? locationStateFilter.value ?? '' : villageStateFilter.value ?? ''),
    countyCode: level === 'LOCATION' ? locationCountyFilter.value ?? '' : villageCountyFilter.value ?? '',
    locationCode: villageLocationFilter.value ?? '',
    country: '',
  }
  dialog.value = true
}

const levelToCode: Record<typeof dialogLevel.value, 'CREATE_STATE' | 'CREATE_COUNTY' | 'CREATE_LOCATION' | 'CREATE_VILLAGE'> = {
  STATE: 'CREATE_STATE', COUNTY: 'CREATE_COUNTY', LOCATION: 'CREATE_LOCATION', VILLAGE: 'CREATE_VILLAGE',
}

async function save() {
  if (!form.value.name.trim()) {
    toast.error('Name is required')
    return
  }
  saving.value = true
  try {
    const payload: Record<string, unknown> = { name: form.value.name.trim() }
    if (dialogLevel.value !== 'STATE') payload.stateCode = form.value.stateCode
    if (dialogLevel.value === 'LOCATION' || dialogLevel.value === 'VILLAGE') payload.countyCode = form.value.countyCode
    if (dialogLevel.value === 'VILLAGE') payload.locationCode = form.value.locationCode
    if (dialogLevel.value === 'STATE' && form.value.country) payload.country = form.value.country

    const res = await dispatch<{ code: string }>(levelToCode[dialogLevel.value], payload)
    toast.success(`Created successfully (${res.code})`)
    dialog.value = false
    await loadAll()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Save failed')
  } finally {
    saving.value = false
  }
}

async function remove(level: typeof dialogLevel.value, code: string) {
  try {
    await dispatch('DELETE_GEO_NODE', { level, code })
    toast.success('Removed')
    await loadAll()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Delete failed')
  }
}

// ---- Import Excel (bulk upload, one level/parent-scope per file) --------------
// Mirrors the Households page's "Import CSV" flow (pick file -> parse client-side
// -> preview rows -> confirm -> submit), but reads .xlsx via SheetJS (`xlsx`)
// instead of CSV, and the code column is dropped from the template entirely --
// the backend now generates every code, so a row only ever needs a name (plus an
// optional country for states).

interface BulkRow { name: string; country?: string }

const bulkDialog = ref(false)
const bulkLevel = ref<typeof dialogLevel.value>('STATE')
const bulkStateCode = ref('')
const bulkCountyCode = ref('')
const bulkLocationCode = ref('')
const bulkFileName = ref('')
const bulkRows = ref<BulkRow[]>([])
const bulkUploading = ref(false)
const bulkResult = ref<{ successCount: number; failureCount: number; errors: { row: number; message: string }[] } | null>(null)

function openBulk(level: typeof dialogLevel.value) {
  bulkLevel.value = level
  bulkStateCode.value = level === 'COUNTY' ? (countyStateFilter.value ?? '') : (level === 'LOCATION' ? locationStateFilter.value ?? '' : villageStateFilter.value ?? '')
  bulkCountyCode.value = level === 'LOCATION' ? locationCountyFilter.value ?? '' : villageCountyFilter.value ?? ''
  bulkLocationCode.value = villageLocationFilter.value ?? ''
  bulkFileName.value = ''
  bulkRows.value = []
  bulkResult.value = null
  bulkDialog.value = true
}

async function downloadTemplate() {
  const XLSX = await import('xlsx')
  const headers = bulkLevel.value === 'STATE' ? ['name', 'country'] : ['name']
  const sample = bulkLevel.value === 'STATE' ? ['Central Equatoria', 'SS'] : ['Sample Name']
  const sheet = XLSX.utils.aoa_to_sheet([headers, sample])
  const workbook = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(workbook, sheet, 'Template')
  XLSX.writeFile(workbook, `${bulkLevel.value.toLowerCase()}-upload-template.xlsx`)
}

function onBulkFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  bulkFileName.value = file.name
  const reader = new FileReader()
  reader.onload = async () => {
    const XLSX = await import('xlsx')
    const workbook = XLSX.read(new Uint8Array(reader.result as ArrayBuffer), { type: 'array' })
    const sheet = workbook.Sheets[workbook.SheetNames[0]]
    const parsed = XLSX.utils.sheet_to_json<Record<string, unknown>>(sheet, { defval: '' })
    bulkRows.value = parsed
      .map((r) => ({
        name: String(r.name ?? r.Name ?? '').trim(),
        country: String(r.country ?? r.Country ?? '').trim().toUpperCase() || undefined,
      }))
      .filter((r) => r.name)
  }
  reader.readAsArrayBuffer(file)
}

const bulkParentReady = computed(() => {
  if (bulkLevel.value === 'STATE') return true
  if (!bulkStateCode.value) return false
  if ((bulkLevel.value === 'LOCATION' || bulkLevel.value === 'VILLAGE') && !bulkCountyCode.value) return false
  if (bulkLevel.value === 'VILLAGE' && !bulkLocationCode.value) return false
  return true
})
const bulkReady = computed(() => bulkParentReady.value && bulkRows.value.length > 0)

async function submitBulk() {
  if (!bulkReady.value) return
  bulkUploading.value = true
  try {
    const payload: Record<string, unknown> = {
      level: bulkLevel.value,
      fileName: bulkFileName.value,
      rows: bulkRows.value.map((r) => ({ name: r.name, country: r.country })),
    }
    if (bulkLevel.value !== 'STATE') payload.stateCode = bulkStateCode.value
    if (bulkLevel.value === 'LOCATION' || bulkLevel.value === 'VILLAGE') payload.countyCode = bulkCountyCode.value
    if (bulkLevel.value === 'VILLAGE') payload.locationCode = bulkLocationCode.value

    const res = await dispatch<{ successCount: number; failureCount: number; errors: { row: number; message: string }[] }>(
      'BULK_UPLOAD_GEO_NODES',
      payload,
    )
    bulkResult.value = res
    toast.success(`${res.successCount} created, ${res.failureCount} failed`)
    await loadAll()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Bulk upload failed')
  } finally {
    bulkUploading.value = false
  }
}
</script>

<template>
  <div>
    <div class="d-flex align-center justify-space-between mb-4">
      <div>
        <h1 class="text-h5 font-weight-bold">Locations</h1>
        <p class="text-caption text-medium-emphasis mb-0">
          Configure the state / county / location / village hierarchy your organisations register households against.
        </p>
      </div>
    </div>

    <v-card variant="flat" border>
      <v-tabs v-model="tab" color="primary">
        <v-tab value="states">States</v-tab>
        <v-tab value="counties">Counties</v-tab>
        <v-tab value="locations">Locations</v-tab>
        <v-tab value="villages">Villages</v-tab>
      </v-tabs>
      <v-divider />

      <v-window v-model="tab">
        <v-window-item value="states">
          <v-card-text>
            <div class="d-flex align-center ga-3 mb-2">
              <v-text-field v-model="tableSearch" prepend-inner-icon="mdi-magnify" label="Search" clearable hide-details density="compact" style="max-width: 280px" />
              <v-spacer />
              <v-btn size="small" variant="outlined" color="secondary" prepend-icon="mdi-file-upload" @click="openBulk('STATE')">Import Excel</v-btn>
              <v-btn size="small" color="secondary" prepend-icon="mdi-plus" @click="openCreate('STATE')">Add State</v-btn>
            </div>
            <v-data-table :headers="stateHeaders" :items="states" :search="tableSearch" :loading="loading" density="comfortable">
              <template #item.country="{ item }">{{ item.country || '—' }}</template>
              <template #item.actions="{ item }">
                <v-btn icon="mdi-delete" size="small" variant="text" color="error" :aria-label="`Delete state ${item.name}`" @click="remove('STATE', item.code)" />
              </template>
              <template #no-data><div class="text-center text-medium-emphasis py-4">No states configured yet</div></template>
            </v-data-table>
          </v-card-text>
        </v-window-item>

        <v-window-item value="counties">
          <v-card-text>
            <div class="d-flex align-center ga-3 mb-2">
              <v-select v-model="countyStateFilter" :items="states" item-title="name" item-value="code" label="Filter by state" clearable hide-details density="compact" style="max-width: 240px" />
              <v-text-field v-model="tableSearch" prepend-inner-icon="mdi-magnify" label="Search" clearable hide-details density="compact" style="max-width: 220px" />
              <v-spacer />
              <v-btn size="small" variant="outlined" color="secondary" prepend-icon="mdi-file-upload" @click="openBulk('COUNTY')">Import Excel</v-btn>
              <v-btn size="small" color="secondary" prepend-icon="mdi-plus" @click="openCreate('COUNTY')">Add County</v-btn>
            </div>
            <v-data-table :headers="countyHeaders" :items="counties" :search="tableSearch" :loading="loading" density="comfortable">
              <template #item.stateCode="{ item }">{{ stateName(item.stateCode) }}</template>
              <template #item.actions="{ item }">
                <v-btn icon="mdi-delete" size="small" variant="text" color="error" :aria-label="`Delete county ${item.name}`" @click="remove('COUNTY', item.code)" />
              </template>
              <template #no-data><div class="text-center text-medium-emphasis py-4">No counties configured yet</div></template>
            </v-data-table>
          </v-card-text>
        </v-window-item>

        <v-window-item value="locations">
          <v-card-text>
            <div class="d-flex align-center ga-3 mb-2">
              <v-select v-model="locationStateFilter" :items="states" item-title="name" item-value="code" label="State" clearable hide-details density="compact" style="max-width: 220px" />
              <v-select v-model="locationCountyFilter" :items="countiesForState(locationStateFilter)" item-title="name" item-value="code" label="Filter by county" clearable hide-details density="compact" style="max-width: 200px" />
              <v-text-field v-model="tableSearch" prepend-inner-icon="mdi-magnify" label="Search" clearable hide-details density="compact" style="max-width: 200px" />
              <v-spacer />
              <v-btn size="small" variant="outlined" color="secondary" prepend-icon="mdi-file-upload" @click="openBulk('LOCATION')">Import Excel</v-btn>
              <v-btn size="small" color="secondary" prepend-icon="mdi-plus" @click="openCreate('LOCATION')">Add Location</v-btn>
            </div>
            <v-data-table :headers="locationHeaders" :items="locations" :search="tableSearch" :loading="loading" density="comfortable">
              <template #item.countyCode="{ item }">{{ countyName(item.countyCode) }}</template>
              <template #item.actions="{ item }">
                <v-btn icon="mdi-delete" size="small" variant="text" color="error" :aria-label="`Delete location ${item.name}`" @click="remove('LOCATION', item.code)" />
              </template>
              <template #no-data><div class="text-center text-medium-emphasis py-4">No locations configured yet</div></template>
            </v-data-table>
          </v-card-text>
        </v-window-item>

        <v-window-item value="villages">
          <v-card-text>
            <div class="d-flex align-center ga-3 mb-2 flex-wrap">
              <v-select v-model="villageStateFilter" :items="states" item-title="name" item-value="code" label="State" clearable hide-details density="compact" style="max-width: 200px" />
              <v-select v-model="villageCountyFilter" :items="countiesForState(villageStateFilter)" item-title="name" item-value="code" label="County" clearable hide-details density="compact" style="max-width: 200px" />
              <v-select v-model="villageLocationFilter" :items="locationsForCounty(villageCountyFilter)" item-title="name" item-value="code" label="Filter by location" clearable hide-details density="compact" style="max-width: 200px" />
              <v-text-field v-model="tableSearch" prepend-inner-icon="mdi-magnify" label="Search" clearable hide-details density="compact" style="max-width: 180px" />
              <v-spacer />
              <v-btn size="small" variant="outlined" color="secondary" prepend-icon="mdi-file-upload" @click="openBulk('VILLAGE')">Import Excel</v-btn>
              <v-btn size="small" color="secondary" prepend-icon="mdi-plus" @click="openCreate('VILLAGE')">Add Village</v-btn>
            </div>
            <v-data-table :headers="villageHeaders" :items="villages" :search="tableSearch" :loading="loading" density="comfortable">
              <template #item.locationCode="{ item }">{{ locationName(item.locationCode) }}</template>
              <template #item.actions="{ item }">
                <v-btn icon="mdi-delete" size="small" variant="text" color="error" :aria-label="`Delete village ${item.name}`" @click="remove('VILLAGE', item.code)" />
              </template>
              <template #no-data><div class="text-center text-medium-emphasis py-4">No villages configured yet</div></template>
            </v-data-table>
          </v-card-text>
        </v-window-item>
      </v-window>
    </v-card>

    <v-dialog v-model="dialog" max-width="480">
      <v-card>
        <v-card-title>Add {{ dialogLevel.charAt(0) + dialogLevel.slice(1).toLowerCase() }}</v-card-title>
        <v-card-text>
          <v-select v-if="dialogLevel !== 'STATE'" v-model="form.stateCode" :items="states" item-title="name" item-value="code" label="State" />
          <v-select v-if="dialogLevel === 'LOCATION' || dialogLevel === 'VILLAGE'" v-model="form.countyCode" :items="countiesForState(form.stateCode)" item-title="name" item-value="code" label="County" />
          <v-select v-if="dialogLevel === 'VILLAGE'" v-model="form.locationCode" :items="locationsForCounty(form.countyCode)" item-title="name" item-value="code" label="Location" />
          <v-autocomplete
            v-if="dialogLevel === 'STATE'" v-model="form.country" :items="COUNTRIES" item-title="name" item-value="code"
            label="Country (optional)" clearable hint="Prefixes the generated code, e.g. Kenya -> KE2000" persistent-hint
          />
          <v-text-field v-model="form.name" label="Name" class="mt-2" />
          <p class="text-caption text-medium-emphasis mt-2 mb-0">The code is generated automatically once saved.</p>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="dialog = false">Cancel</v-btn>
          <v-btn color="secondary" :loading="saving" @click="save">Save</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="bulkDialog" max-width="640">
      <v-card>
        <v-card-title>Import {{ bulkLevel.charAt(0) + bulkLevel.slice(1).toLowerCase() }}s from Excel</v-card-title>
        <v-card-text>
          <v-alert type="info" variant="tonal" density="compact" class="mb-3">
            Download the template, fill in one row per {{ bulkLevel.toLowerCase() }} (name{{ bulkLevel === 'STATE' ? ', and optionally a country code' : '' }}), then upload it here. Codes are generated automatically.
          </v-alert>

          <v-row v-if="bulkLevel !== 'STATE'" dense>
            <v-col :cols="bulkLevel === 'COUNTY' ? 12 : 4">
              <v-select v-model="bulkStateCode" :items="states" item-title="name" item-value="code" label="State" density="compact" />
            </v-col>
            <v-col v-if="bulkLevel === 'LOCATION' || bulkLevel === 'VILLAGE'" cols="4">
              <v-select v-model="bulkCountyCode" :items="countiesForState(bulkStateCode)" item-title="name" item-value="code" label="County" density="compact" />
            </v-col>
            <v-col v-if="bulkLevel === 'VILLAGE'" cols="4">
              <v-select v-model="bulkLocationCode" :items="locationsForCounty(bulkCountyCode)" item-title="name" item-value="code" label="Location" density="compact" />
            </v-col>
          </v-row>

          <v-btn variant="outlined" size="small" prepend-icon="mdi-download" class="my-3" @click="downloadTemplate">
            Download Template
          </v-btn>
          <v-file-input
            label="Upload filled .xlsx" accept=".xlsx,.xls" prepend-icon="mdi-file-upload"
            :disabled="!bulkParentReady" @change="onBulkFile"
          />
          <div v-if="bulkRows.length" class="text-caption mb-2">{{ bulkRows.length }} row(s) ready from {{ bulkFileName }}</div>

          <v-table v-if="bulkRows.length" density="compact" class="mb-2" style="max-height: 240px; overflow-y: auto">
            <thead>
              <tr>
                <th>Name</th>
                <th v-if="bulkLevel === 'STATE'">Country</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in bulkRows" :key="i">
                <td>{{ r.name }}</td>
                <td v-if="bulkLevel === 'STATE'">{{ r.country || '—' }}</td>
              </tr>
            </tbody>
          </v-table>

          <v-alert v-if="bulkResult" :type="bulkResult.failureCount ? 'warning' : 'success'" variant="tonal" density="compact" class="mt-2">
            {{ bulkResult.successCount }} created, {{ bulkResult.failureCount }} failed
            <div v-for="e in bulkResult.errors.slice(0, 5)" :key="e.row">Row {{ e.row }}: {{ e.message }}</div>
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="bulkDialog = false">Close</v-btn>
          <v-btn color="secondary" :loading="bulkUploading" :disabled="!bulkReady" @click="submitBulk">Upload</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
