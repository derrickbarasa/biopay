<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
// xlsx is dynamically imported below (see downloadTemplate/onBulkFile) -- it's only
// needed inside the bulk-upload dialog and is too large to import statically.
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { COUNTRIES } from '@/utils/countries'

interface GeoNode {
  code: string
  name: string
  stateCode?: string
  countyCode?: string
  locationCode?: string
  anchorId?: number
  country?: string
  displayCode?: string
  status?: number
}
interface Anchor { id: number; name: string }

const auth = useAuthStore()
const toast = useToast()
const { confirmAction } = useConfirm()
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
const anchors = ref<Anchor[]>([])
const selectedAnchorId = ref<number | null>(null)
// Sending the key at all (even null) opts this page into seeing every status -- GET_STATES/etc.
// default to active-only for every other caller (create-flow pickers) when the key is absent.
const statusFilter = ref<number | null>(null)
function scoped(payload: Record<string, unknown> = {}) {
  const withStatus = { ...payload, status: statusFilter.value }
  return auth.isSystemAdmin ? { ...withStatus, targetAnchorId: selectedAnchorId.value } : withStatus
}

// Shared free-text search for the active tab's data table; cleared on tab switch.
const tableSearch = ref('')
watch(tab, () => { tableSearch.value = '' })

const actionsHeader = { title: 'Actions', key: 'actions', sortable: false, align: 'start' as const, width: 132, minWidth: 132, fixed: true, nowrap: true }
const statusHeader = { title: 'Status', key: 'status', sortable: false, width: 100, minWidth: 100, nowrap: true }
const codeHeader = (title: string, key = 'code') => ({ title, key, minWidth: 132, nowrap: true })
const stateHeaders = [codeHeader('State Code', 'displayCode'), { title: 'State', key: 'name' }, { title: 'Country', key: 'country' }, statusHeader, actionsHeader]
const countyHeaders = [codeHeader('County Code'), { title: 'Name', key: 'name' }, { title: 'State', key: 'stateCode' }, statusHeader, actionsHeader]
const locationHeaders = [codeHeader('Location Code'), { title: 'Name', key: 'name' }, { title: 'County', key: 'countyCode' }, statusHeader, actionsHeader]
const villageHeaders = [codeHeader('Village Code'), { title: 'Name', key: 'name' }, { title: 'Location', key: 'locationCode' }, statusHeader, actionsHeader]

const dialog = ref(false)
const dialogLevel = ref<'STATE' | 'COUNTY' | 'LOCATION' | 'VILLAGE'>('STATE')
const editing = ref(false)
const editingCode = ref('')
const saving = ref(false)
const form = ref({ name: '', country: '', stateCode: '', countyCode: '', locationCode: '', anchorId: null as number | null })

async function loadStates() {
  const res = await dispatch<{ results: GeoNode[] }>('GET_STATES', scoped())
  states.value = res.results
}
async function loadCounties() {
  const res = await dispatch<{ results: GeoNode[] }>('GET_COUNTIES', scoped({ stateCode: countyStateFilter.value }))
  counties.value = res.results
}
async function loadLocations() {
  const res = await dispatch<{ results: GeoNode[] }>('GET_LOCATIONS', scoped({ countyCode: locationCountyFilter.value }))
  locations.value = res.results
}
async function loadVillages() {
  const res = await dispatch<{ results: GeoNode[] }>('GET_VILLAGES', scoped({ locationCode: villageLocationFilter.value }))
  villages.value = res.results
}

// Parent-level columns show the parent's name, not its raw code.
const stateNameByCode = computed(() => new Map(states.value.map((s) => [s.code, s.name])))
const countyNameByCode = computed(() => new Map(counties.value.map((c) => [c.code, c.name])))
const locationNameByCode = computed(() => new Map(locations.value.map((l) => [l.code, l.name])))
function stateName(code?: string) { return (code && stateNameByCode.value.get(code)) || code || '—' }
function countyName(code?: string) { return (code && countyNameByCode.value.get(code)) || code || '—' }
function locationName(code?: string) { return (code && locationNameByCode.value.get(code)) || code || '—' }
function countryName(code?: string) { return COUNTRIES.find((country) => country.code === code)?.name || code || '—' }
function normalizeCountryCode(value: unknown) {
  const candidate = String(value ?? '').trim()
  if (!candidate) return ''
  return COUNTRIES.find((country) => country.code.toLowerCase() === candidate.toLowerCase() || country.name.toLowerCase() === candidate.toLowerCase())?.code ?? candidate.toUpperCase()
}

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

onMounted(async () => {
  if (auth.isSystemAdmin) {
    const res = await dispatch<{ results: Anchor[] }>('GET_ANCHORS')
    anchors.value = res.results
  }
  await loadAll()
})
watch(selectedAnchorId, () => {
  countyStateFilter.value = null; locationStateFilter.value = null; locationCountyFilter.value = null
  villageStateFilter.value = null; villageCountyFilter.value = null; villageLocationFilter.value = null
  void loadAll()
})
watch(countyStateFilter, loadCounties)
watch(locationCountyFilter, loadLocations)
watch(villageLocationFilter, loadVillages)
watch(statusFilter, loadAll)

const countiesForState = (stateCode: string | null) => stateCode ? counties.value.filter((c) => c.stateCode === stateCode) : counties.value
const locationsForCounty = (countyCode: string | null) => countyCode ? locations.value.filter((l) => l.countyCode === countyCode) : locations.value

// Parent pickers in the dialogs stay scoped to the chosen anchor, so a State from
// anchor A can never be picked as the parent of a County created under anchor B. Also
// excludes inactive parents -- statusFilter may be showing Inactive/All in the table
// behind the dialog, but a deactivated state/county/location can't be picked for new work.
const dialogStates = computed(() => states.value.filter((s) => s.status !== 0 && (!auth.isSystemAdmin || !form.value.anchorId || s.anchorId === form.value.anchorId)))
const bulkStates = computed(() => states.value.filter((s) => s.status !== 0 && (!auth.isSystemAdmin || !bulkAnchorId.value || s.anchorId === bulkAnchorId.value)))
const activeCountiesForState = (stateCode: string | null) => countiesForState(stateCode).filter((c) => c.status !== 0)
const activeLocationsForCounty = (countyCode: string | null) => locationsForCounty(countyCode).filter((l) => l.status !== 0)

function openCreate(level: typeof dialogLevel.value) {
  dialogLevel.value = level
  editing.value = false
  editingCode.value = ''
  form.value = {
    name: '',
    country: '',
    stateCode: level === 'COUNTY' ? (countyStateFilter.value ?? '') : (level === 'LOCATION' ? locationStateFilter.value ?? '' : villageStateFilter.value ?? ''),
    countyCode: level === 'LOCATION' ? locationCountyFilter.value ?? '' : villageCountyFilter.value ?? '',
    locationCode: villageLocationFilter.value ?? '',
    anchorId: selectedAnchorId.value,
  }
  dialog.value = true
}

function openEdit(level: typeof dialogLevel.value, item: GeoNode) {
  dialogLevel.value = level
  editing.value = true
  editingCode.value = item.code
  form.value = {
    name: item.name,
    country: item.country ?? '',
    stateCode: item.stateCode ?? '',
    countyCode: item.countyCode ?? '',
    locationCode: item.locationCode ?? '',
    anchorId: item.anchorId ?? selectedAnchorId.value,
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
  if (auth.isSystemAdmin && !form.value.anchorId) {
    toast.error('Choose an anchor for this location')
    return
  }
  if (dialogLevel.value === 'STATE' && !form.value.country) {
    toast.error('Choose the country this state belongs to')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await dispatch('UPDATE_GEO_NODE', {
        level: dialogLevel.value,
        code: editingCode.value,
        name: form.value.name.trim(),
        country: dialogLevel.value === 'STATE' ? form.value.country : undefined,
        targetAnchorId: form.value.anchorId,
      })
      toast.success('Location name updated')
    } else {
      const payload: Record<string, unknown> = { name: form.value.name.trim(), targetAnchorId: form.value.anchorId }
      if (dialogLevel.value === 'STATE') payload.country = form.value.country
      if (dialogLevel.value !== 'STATE') payload.stateCode = form.value.stateCode
      if (dialogLevel.value === 'LOCATION' || dialogLevel.value === 'VILLAGE') payload.countyCode = form.value.countyCode
      if (dialogLevel.value === 'VILLAGE') payload.locationCode = form.value.locationCode

      const res = await dispatch<{ code: string }>(levelToCode[dialogLevel.value], payload)
      toast.success(`Created successfully (${res.code})`)
    }
    dialog.value = false
    await loadAll()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Save failed')
  } finally {
    saving.value = false
  }
}

async function remove(level: typeof dialogLevel.value, item: GeoNode) {
  const label = level.toLowerCase()
  if (!await confirmAction({
    title: `Permanently delete this ${label}?`,
    message: `${item.name} will be permanently deleted -- this cannot be undone. Only possible while nothing (counties, households, officer assignments) still depends on it; deactivate it instead to keep it but stop new work from using it.`,
    confirmLabel: `Delete ${label}`,
    color: 'error',
    requireTypedText: 'DELETE',
  })) return
  try {
    await dispatch('DELETE_GEO_NODE', { level, code: item.code, targetAnchorId: auth.isSystemAdmin ? item.anchorId : undefined })
    toast.success('Deleted permanently')
    await loadAll()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Delete failed')
  }
}

async function toggleStatus(level: typeof dialogLevel.value, item: GeoNode) {
  const label = level.toLowerCase()
  const deactivating = item.status !== 0
  if (!await confirmAction({
    title: `${deactivating ? 'Deactivate' : 'Activate'} this ${label}?`,
    message: deactivating
      ? `${item.name} will be hidden from pickers and can no longer be used for new work. Its existing records stay intact and it can be reactivated any time.`
      : `${item.name} will become available again for new work.`,
    confirmLabel: deactivating ? 'Deactivate' : 'Activate',
    color: deactivating ? 'error' : 'success',
  })) return
  try {
    await dispatch('TOGGLE_GEO_NODE_STATUS', {
      level, code: item.code, status: deactivating ? 0 : 1,
      targetAnchorId: auth.isSystemAdmin ? item.anchorId : undefined,
    })
    toast.success('Status updated')
    await loadAll()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Update failed')
  }
}

// ---- Import Excel (bulk upload, one level/parent-scope per file) ----
// The backend generates every code. State rows also carry the ISO country code
// that supplies the prefix; lower hierarchy levels inherit it from the state.

interface BulkRow { name: string; country?: string }

const bulkDialog = ref(false)
const bulkLevel = ref<typeof dialogLevel.value>('STATE')
const bulkStateCode = ref('')
const bulkCountyCode = ref('')
const bulkLocationCode = ref('')
const bulkAnchorId = ref<number | null>(null)
const bulkFileName = ref('')
const bulkRows = ref<BulkRow[]>([])
const bulkUploading = ref(false)
const bulkResult = ref<{ successCount: number; failureCount: number; errors: { row: number; message: string }[] } | null>(null)

function openBulk(level: typeof dialogLevel.value) {
  bulkLevel.value = level
  bulkStateCode.value = level === 'COUNTY' ? (countyStateFilter.value ?? '') : (level === 'LOCATION' ? locationStateFilter.value ?? '' : villageStateFilter.value ?? '')
  bulkCountyCode.value = level === 'LOCATION' ? locationCountyFilter.value ?? '' : villageCountyFilter.value ?? ''
  bulkLocationCode.value = villageLocationFilter.value ?? ''
  bulkAnchorId.value = selectedAnchorId.value
  bulkFileName.value = ''
  bulkRows.value = []
  bulkResult.value = null
  bulkDialog.value = true
}

async function downloadTemplate() {
  const XLSX = await import('xlsx')
  const headers = bulkLevel.value === 'STATE' ? ['name', 'countryCode'] : ['name']
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
        country: bulkLevel.value === 'STATE' ? normalizeCountryCode(r.countryCode ?? r.CountryCode ?? r.country ?? r.Country) : undefined,
      }))
      .filter((r) => r.name)
  }
  reader.readAsArrayBuffer(file)
}

const bulkParentReady = computed(() => {
  if (auth.isSystemAdmin && !bulkAnchorId.value) return false
  if (bulkLevel.value === 'STATE') return true
  if (!bulkStateCode.value) return false
  if ((bulkLevel.value === 'LOCATION' || bulkLevel.value === 'VILLAGE') && !bulkCountyCode.value) return false
  if (bulkLevel.value === 'VILLAGE' && !bulkLocationCode.value) return false
  return true
})
const invalidBulkCountryCount = computed(() => bulkLevel.value === 'STATE'
  ? bulkRows.value.filter((row) => !COUNTRIES.some((country) => country.code === row.country)).length
  : 0)
const bulkReady = computed(() => bulkParentReady.value
  && bulkRows.value.length > 0
  && (bulkLevel.value !== 'STATE' || bulkRows.value.every((row) => COUNTRIES.some((country) => country.code === row.country))))

async function submitBulk() {
  if (!bulkReady.value) return
  bulkUploading.value = true
  try {
    const payload: Record<string, unknown> = {
      level: bulkLevel.value,
      fileName: bulkFileName.value,
      rows: bulkRows.value.map((row) => ({ name: row.name, country: row.country })),
      targetAnchorId: bulkAnchorId.value,
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
        <h1 class="page-title">Locations</h1>
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
              <v-select
                v-if="auth.isSystemAdmin" v-model="selectedAnchorId" :items="anchors" item-title="name" item-value="id"
                label="Anchor" clearable hide-details density="compact" style="max-width: 220px" prepend-inner-icon="mdi-bank-outline"
              />
              <v-select
                v-model="statusFilter" :items="[{ title: 'Active', value: 1 }, { title: 'Inactive', value: 0 }]"
                label="Status" clearable hide-details density="compact" style="max-width: 160px"
              />
              <v-text-field v-model="tableSearch" prepend-inner-icon="mdi-magnify" label="Search" clearable hide-details density="compact" style="max-width: 280px" />
              <v-spacer />
              <v-btn v-if="auth.can('ACCESS_LOCATIONS')" size="small" variant="outlined" color="secondary" prepend-icon="mdi-file-upload" @click="openBulk('STATE')">Import Excel</v-btn>
              <v-btn v-if="auth.can('ACCESS_LOCATIONS')" size="small" color="secondary" prepend-icon="mdi-plus" @click="openCreate('STATE')">Add State</v-btn>
            </div>
            <v-data-table :headers="stateHeaders" :items="states" :search="tableSearch" :loading="loading" density="comfortable">
              <template #item.displayCode="{ item }">{{ item.displayCode || item.code }}</template>
              <template #item.country="{ item }">{{ countryName(item.country) }}</template>
              <template #item.status="{ item }">
                <v-chip :color="item.status === 0 ? 'default' : 'success'" size="small" variant="tonal">{{ item.status === 0 ? 'Inactive' : 'Active' }}</v-chip>
              </template>
              <template #item.actions="{ item }">
                <v-btn v-if="auth.can('ACCESS_LOCATIONS')" icon="mdi-pencil-outline" size="small" variant="text" :aria-label="`Edit state ${item.name}`" @click="openEdit('STATE', item)" />
                <v-btn v-if="auth.can('ACCESS_LOCATIONS')" :icon="item.status === 0 ? 'mdi-check-circle-outline' : 'mdi-cancel'" size="small" variant="text" :color="item.status === 0 ? 'success' : 'error'" :aria-label="`${item.status === 0 ? 'Activate' : 'Deactivate'} state ${item.name}`" @click="toggleStatus('STATE', item)" />
                <v-btn v-if="auth.can('ACCESS_LOCATIONS')" icon="mdi-delete" size="small" variant="text" color="error" :aria-label="`Delete state ${item.name}`" @click="remove('STATE', item)" />
              </template>
              <template #no-data><div class="text-center text-medium-emphasis py-4">No states configured yet</div></template>
            </v-data-table>
          </v-card-text>
        </v-window-item>

        <v-window-item value="counties">
          <v-card-text>
            <div class="d-flex align-center ga-3 mb-2">
              <v-select
                v-if="auth.isSystemAdmin" v-model="selectedAnchorId" :items="anchors" item-title="name" item-value="id"
                label="Anchor" clearable hide-details density="compact" style="max-width: 220px" prepend-inner-icon="mdi-bank-outline"
              />
              <v-select v-model="countyStateFilter" :items="states" item-title="name" item-value="code" label="Filter by state" clearable hide-details density="compact" style="max-width: 240px" />
              <v-select
                v-model="statusFilter" :items="[{ title: 'Active', value: 1 }, { title: 'Inactive', value: 0 }]"
                label="Status" clearable hide-details density="compact" style="max-width: 160px"
              />
              <v-text-field v-model="tableSearch" prepend-inner-icon="mdi-magnify" label="Search" clearable hide-details density="compact" style="max-width: 220px" />
              <v-spacer />
              <v-btn v-if="auth.can('ACCESS_LOCATIONS')" size="small" variant="outlined" color="secondary" prepend-icon="mdi-file-upload" @click="openBulk('COUNTY')">Import Excel</v-btn>
              <v-btn v-if="auth.can('ACCESS_LOCATIONS')" size="small" color="secondary" prepend-icon="mdi-plus" @click="openCreate('COUNTY')">Add County</v-btn>
            </div>
            <v-data-table :headers="countyHeaders" :items="counties" :search="tableSearch" :loading="loading" density="comfortable">
              <template #item.stateCode="{ item }">{{ stateName(item.stateCode) }}</template>
              <template #item.status="{ item }">
                <v-chip :color="item.status === 0 ? 'default' : 'success'" size="small" variant="tonal">{{ item.status === 0 ? 'Inactive' : 'Active' }}</v-chip>
              </template>
              <template #item.actions="{ item }">
                <v-btn v-if="auth.can('ACCESS_LOCATIONS')" icon="mdi-pencil-outline" size="small" variant="text" :aria-label="`Edit county ${item.name}`" @click="openEdit('COUNTY', item)" />
                <v-btn v-if="auth.can('ACCESS_LOCATIONS')" :icon="item.status === 0 ? 'mdi-check-circle-outline' : 'mdi-cancel'" size="small" variant="text" :color="item.status === 0 ? 'success' : 'error'" :aria-label="`${item.status === 0 ? 'Activate' : 'Deactivate'} county ${item.name}`" @click="toggleStatus('COUNTY', item)" />
                <v-btn v-if="auth.can('ACCESS_LOCATIONS')" icon="mdi-delete" size="small" variant="text" color="error" :aria-label="`Delete county ${item.name}`" @click="remove('COUNTY', item)" />
              </template>
              <template #no-data><div class="text-center text-medium-emphasis py-4">No counties configured yet</div></template>
            </v-data-table>
          </v-card-text>
        </v-window-item>

        <v-window-item value="locations">
          <v-card-text>
            <div class="d-flex align-center ga-3 mb-2">
              <v-select
                v-if="auth.isSystemAdmin" v-model="selectedAnchorId" :items="anchors" item-title="name" item-value="id"
                label="Anchor" clearable hide-details density="compact" style="max-width: 220px" prepend-inner-icon="mdi-bank-outline"
              />
              <v-select v-model="locationStateFilter" :items="states" item-title="name" item-value="code" label="State" clearable hide-details density="compact" style="max-width: 220px" />
              <v-select v-model="locationCountyFilter" :items="countiesForState(locationStateFilter)" item-title="name" item-value="code" label="Filter by county" clearable hide-details density="compact" style="max-width: 200px" />
              <v-select
                v-model="statusFilter" :items="[{ title: 'Active', value: 1 }, { title: 'Inactive', value: 0 }]"
                label="Status" clearable hide-details density="compact" style="max-width: 160px"
              />
              <v-text-field v-model="tableSearch" prepend-inner-icon="mdi-magnify" label="Search" clearable hide-details density="compact" style="max-width: 200px" />
              <v-spacer />
              <v-btn v-if="auth.can('ACCESS_LOCATIONS')" size="small" variant="outlined" color="secondary" prepend-icon="mdi-file-upload" @click="openBulk('LOCATION')">Import Excel</v-btn>
              <v-btn v-if="auth.can('ACCESS_LOCATIONS')" size="small" color="secondary" prepend-icon="mdi-plus" @click="openCreate('LOCATION')">Add Location</v-btn>
            </div>
            <v-data-table :headers="locationHeaders" :items="locations" :search="tableSearch" :loading="loading" density="comfortable">
              <template #item.countyCode="{ item }">{{ countyName(item.countyCode) }}</template>
              <template #item.status="{ item }">
                <v-chip :color="item.status === 0 ? 'default' : 'success'" size="small" variant="tonal">{{ item.status === 0 ? 'Inactive' : 'Active' }}</v-chip>
              </template>
              <template #item.actions="{ item }">
                <v-btn v-if="auth.can('ACCESS_LOCATIONS')" icon="mdi-pencil-outline" size="small" variant="text" :aria-label="`Edit location ${item.name}`" @click="openEdit('LOCATION', item)" />
                <v-btn v-if="auth.can('ACCESS_LOCATIONS')" :icon="item.status === 0 ? 'mdi-check-circle-outline' : 'mdi-cancel'" size="small" variant="text" :color="item.status === 0 ? 'success' : 'error'" :aria-label="`${item.status === 0 ? 'Activate' : 'Deactivate'} location ${item.name}`" @click="toggleStatus('LOCATION', item)" />
                <v-btn v-if="auth.can('ACCESS_LOCATIONS')" icon="mdi-delete" size="small" variant="text" color="error" :aria-label="`Delete location ${item.name}`" @click="remove('LOCATION', item)" />
              </template>
              <template #no-data><div class="text-center text-medium-emphasis py-4">No locations configured yet</div></template>
            </v-data-table>
          </v-card-text>
        </v-window-item>

        <v-window-item value="villages">
          <v-card-text>
            <div class="d-flex align-center ga-3 mb-2 flex-wrap">
              <v-select
                v-if="auth.isSystemAdmin" v-model="selectedAnchorId" :items="anchors" item-title="name" item-value="id"
                label="Anchor" clearable hide-details density="compact" style="max-width: 200px" prepend-inner-icon="mdi-bank-outline"
              />
              <v-select v-model="villageStateFilter" :items="states" item-title="name" item-value="code" label="State" clearable hide-details density="compact" style="max-width: 200px" />
              <v-select v-model="villageCountyFilter" :items="countiesForState(villageStateFilter)" item-title="name" item-value="code" label="County" clearable hide-details density="compact" style="max-width: 200px" />
              <v-select v-model="villageLocationFilter" :items="locationsForCounty(villageCountyFilter)" item-title="name" item-value="code" label="Filter by location" clearable hide-details density="compact" style="max-width: 200px" />
              <v-select
                v-model="statusFilter" :items="[{ title: 'Active', value: 1 }, { title: 'Inactive', value: 0 }]"
                label="Status" clearable hide-details density="compact" style="max-width: 160px"
              />
              <v-text-field v-model="tableSearch" prepend-inner-icon="mdi-magnify" label="Search" clearable hide-details density="compact" style="max-width: 180px" />
              <v-spacer />
              <v-btn v-if="auth.can('ACCESS_LOCATIONS')" size="small" variant="outlined" color="secondary" prepend-icon="mdi-file-upload" @click="openBulk('VILLAGE')">Import Excel</v-btn>
              <v-btn v-if="auth.can('ACCESS_LOCATIONS')" size="small" color="secondary" prepend-icon="mdi-plus" @click="openCreate('VILLAGE')">Add Village</v-btn>
            </div>
            <v-data-table :headers="villageHeaders" :items="villages" :search="tableSearch" :loading="loading" density="comfortable">
              <template #item.locationCode="{ item }">{{ locationName(item.locationCode) }}</template>
              <template #item.status="{ item }">
                <v-chip :color="item.status === 0 ? 'default' : 'success'" size="small" variant="tonal">{{ item.status === 0 ? 'Inactive' : 'Active' }}</v-chip>
              </template>
              <template #item.actions="{ item }">
                <v-btn v-if="auth.can('ACCESS_LOCATIONS')" icon="mdi-pencil-outline" size="small" variant="text" :aria-label="`Edit village ${item.name}`" @click="openEdit('VILLAGE', item)" />
                <v-btn v-if="auth.can('ACCESS_LOCATIONS')" :icon="item.status === 0 ? 'mdi-check-circle-outline' : 'mdi-cancel'" size="small" variant="text" :color="item.status === 0 ? 'success' : 'error'" :aria-label="`${item.status === 0 ? 'Activate' : 'Deactivate'} village ${item.name}`" @click="toggleStatus('VILLAGE', item)" />
                <v-btn v-if="auth.can('ACCESS_LOCATIONS')" icon="mdi-delete" size="small" variant="text" color="error" :aria-label="`Delete village ${item.name}`" @click="remove('VILLAGE', item)" />
              </template>
              <template #no-data><div class="text-center text-medium-emphasis py-4">No villages configured yet</div></template>
            </v-data-table>
          </v-card-text>
        </v-window-item>
      </v-window>
    </v-card>

    <v-dialog v-model="dialog" max-width="480">
      <v-card>
        <dialog-close-button @close="dialog = false" />
        <v-card-title>{{ editing ? 'Edit' : 'Add' }} {{ dialogLevel.charAt(0) + dialogLevel.slice(1).toLowerCase() }}</v-card-title>
        <v-card-text>
          <v-select v-if="auth.isSystemAdmin" v-model="form.anchorId" :items="anchors" item-title="name" item-value="id" label="Anchor" class="mb-2" :disabled="editing" />
          <v-select v-if="dialogLevel !== 'STATE'" v-model="form.stateCode" :items="dialogStates" item-title="name" item-value="code" label="State" :disabled="editing" />
          <v-select v-if="dialogLevel === 'LOCATION' || dialogLevel === 'VILLAGE'" v-model="form.countyCode" :items="activeCountiesForState(form.stateCode)" item-title="name" item-value="code" label="County" :disabled="editing" />
          <v-select v-if="dialogLevel === 'VILLAGE'" v-model="form.locationCode" :items="activeLocationsForCounty(form.countyCode)" item-title="name" item-value="code" label="Location" :disabled="editing" />
          <v-select
            v-if="dialogLevel === 'STATE'"
            v-model="form.country"
            :items="COUNTRIES"
            item-title="name"
            item-value="code"
            label="Country"
            placeholder="Choose the country"
            required
          />
          <v-text-field v-model="form.name" label="Name" placeholder="e.g. Central" class="mt-2" />
          <p class="text-caption text-medium-emphasis mt-2 mb-0">
            {{ editing ? `The internal key ${editingCode} remains unchanged; the displayed code uses the selected country's ISO prefix.` : 'The code is generated automatically from the selected country’s ISO two-letter prefix.' }}
          </p>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="flat" color="error" @click="dialog = false">Cancel</v-btn>
          <v-btn variant="flat" color="secondary" :loading="saving" @click="save">{{ editing ? 'Save changes' : 'Create' }}</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="bulkDialog" max-width="640">
      <v-card>
        <dialog-close-button @close="bulkDialog = false" />
        <v-card-title>Import {{ bulkLevel.charAt(0) + bulkLevel.slice(1).toLowerCase() }}s from Excel</v-card-title>
        <v-card-text>
          <v-alert type="info" variant="tonal" density="compact" class="mb-3">
            Download the template, fill in one name per {{ bulkLevel.toLowerCase() }}, then upload it here.
            {{ bulkLevel === 'STATE' ? 'Every state row also requires an ISO two-letter country code.' : 'Codes are generated automatically.' }}
          </v-alert>

          <v-select v-if="auth.isSystemAdmin" v-model="bulkAnchorId" :items="anchors" item-title="name" item-value="id" label="Anchor" density="compact" class="mb-2" />
          <v-row v-if="bulkLevel !== 'STATE'" dense>
            <v-col :cols="bulkLevel === 'COUNTY' ? 12 : 4">
              <v-select v-model="bulkStateCode" :items="bulkStates" item-title="name" item-value="code" label="State" density="compact" />
            </v-col>
            <v-col v-if="bulkLevel === 'LOCATION' || bulkLevel === 'VILLAGE'" cols="4">
              <v-select v-model="bulkCountyCode" :items="activeCountiesForState(bulkStateCode)" item-title="name" item-value="code" label="County" density="compact" />
            </v-col>
            <v-col v-if="bulkLevel === 'VILLAGE'" cols="4">
              <v-select v-model="bulkLocationCode" :items="activeLocationsForCounty(bulkCountyCode)" item-title="name" item-value="code" label="Location" density="compact" />
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
                <td v-if="bulkLevel === 'STATE'">{{ countryName(r.country) }}</td>
              </tr>
            </tbody>
          </v-table>

          <v-alert v-if="invalidBulkCountryCount" type="warning" variant="tonal" density="compact" class="mt-2">
            {{ invalidBulkCountryCount }} row(s) have a missing or invalid ISO country code. Use values such as GQ, KE, or SO.
          </v-alert>

          <v-alert v-if="bulkResult" :type="bulkResult.failureCount ? 'warning' : 'success'" variant="tonal" density="compact" class="mt-2">
            {{ bulkResult.successCount }} created, {{ bulkResult.failureCount }} failed
            <div v-for="e in bulkResult.errors.slice(0, 5)" :key="e.row">Row {{ e.row }}: {{ e.message }}</div>
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="bulkDialog = false">Close</v-btn>
          <v-btn variant="flat" color="secondary" :loading="bulkUploading" :disabled="!bulkReady" @click="submitBulk">Upload</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
