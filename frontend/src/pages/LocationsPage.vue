<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'

interface GeoNode {
  code: string
  name: string
  stateCode?: string
  countyCode?: string
  locationCode?: string
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
const dialog = ref(false)
const dialogLevel = ref<'STATE' | 'COUNTY' | 'LOCATION' | 'VILLAGE'>('STATE')
const saving = ref(false)
const form = ref({ code: '', name: '', stateCode: '', countyCode: '', locationCode: '' })

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
    code: '', name: '',
    stateCode: level === 'COUNTY' ? (countyStateFilter.value ?? '') : (level === 'LOCATION' ? locationStateFilter.value ?? '' : villageStateFilter.value ?? ''),
    countyCode: level === 'LOCATION' ? locationCountyFilter.value ?? '' : villageCountyFilter.value ?? '',
    locationCode: villageLocationFilter.value ?? '',
  }
  dialog.value = true
}

const levelToCode: Record<typeof dialogLevel.value, 'CREATE_STATE' | 'CREATE_COUNTY' | 'CREATE_LOCATION' | 'CREATE_VILLAGE'> = {
  STATE: 'CREATE_STATE', COUNTY: 'CREATE_COUNTY', LOCATION: 'CREATE_LOCATION', VILLAGE: 'CREATE_VILLAGE',
}
const levelToField: Record<typeof dialogLevel.value, string> = {
  STATE: 'stateCode', COUNTY: 'countyCode', LOCATION: 'locationCode', VILLAGE: 'villageCode',
}

async function save() {
  if (!form.value.code.trim() || !form.value.name.trim()) {
    toast.error('Code and name are required')
    return
  }
  saving.value = true
  try {
    const payload: Record<string, unknown> = { name: form.value.name.trim() }
    payload[levelToField[dialogLevel.value]] = form.value.code.trim()
    if (dialogLevel.value !== 'STATE') payload.stateCode = form.value.stateCode
    if (dialogLevel.value === 'LOCATION' || dialogLevel.value === 'VILLAGE') payload.countyCode = form.value.countyCode
    if (dialogLevel.value === 'VILLAGE') payload.locationCode = form.value.locationCode

    await dispatch(levelToCode[dialogLevel.value], payload)
    toast.success('Created successfully')
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
            <div class="d-flex justify-end mb-2">
              <v-btn size="small" color="primary" prepend-icon="mdi-plus" @click="openCreate('STATE')">Add State</v-btn>
            </div>
            <v-table density="comfortable" :loading="loading">
              <thead><tr><th>Code</th><th>Name</th><th class="text-right">Actions</th></tr></thead>
              <tbody>
                <tr v-for="s in states" :key="s.code">
                  <td>{{ s.code }}</td><td>{{ s.name }}</td>
                  <td class="text-right"><v-btn icon="mdi-delete" size="small" variant="text" color="error" @click="remove('STATE', s.code)" /></td>
                </tr>
                <tr v-if="!states.length"><td colspan="3" class="text-center text-medium-emphasis">No states configured yet</td></tr>
              </tbody>
            </v-table>
          </v-card-text>
        </v-window-item>

        <v-window-item value="counties">
          <v-card-text>
            <div class="d-flex align-center ga-3 mb-2">
              <v-select v-model="countyStateFilter" :items="states" item-title="name" item-value="code" label="Filter by state" clearable hide-details density="compact" style="max-width: 280px" />
              <v-spacer />
              <v-btn size="small" color="primary" prepend-icon="mdi-plus" @click="openCreate('COUNTY')">Add County</v-btn>
            </div>
            <v-table density="comfortable" :loading="loading">
              <thead><tr><th>Code</th><th>Name</th><th>State</th><th class="text-right">Actions</th></tr></thead>
              <tbody>
                <tr v-for="c in counties" :key="c.code">
                  <td>{{ c.code }}</td><td>{{ c.name }}</td><td>{{ c.stateCode }}</td>
                  <td class="text-right"><v-btn icon="mdi-delete" size="small" variant="text" color="error" @click="remove('COUNTY', c.code)" /></td>
                </tr>
                <tr v-if="!counties.length"><td colspan="4" class="text-center text-medium-emphasis">No counties configured yet</td></tr>
              </tbody>
            </v-table>
          </v-card-text>
        </v-window-item>

        <v-window-item value="locations">
          <v-card-text>
            <div class="d-flex align-center ga-3 mb-2">
              <v-select v-model="locationStateFilter" :items="states" item-title="name" item-value="code" label="State" clearable hide-details density="compact" style="max-width: 220px" />
              <v-select v-model="locationCountyFilter" :items="countiesForState(locationStateFilter)" item-title="name" item-value="code" label="Filter by county" clearable hide-details density="compact" style="max-width: 220px" />
              <v-spacer />
              <v-btn size="small" color="primary" prepend-icon="mdi-plus" @click="openCreate('LOCATION')">Add Location</v-btn>
            </div>
            <v-table density="comfortable" :loading="loading">
              <thead><tr><th>Code</th><th>Name</th><th>County</th><th class="text-right">Actions</th></tr></thead>
              <tbody>
                <tr v-for="l in locations" :key="l.code">
                  <td>{{ l.code }}</td><td>{{ l.name }}</td><td>{{ l.countyCode }}</td>
                  <td class="text-right"><v-btn icon="mdi-delete" size="small" variant="text" color="error" @click="remove('LOCATION', l.code)" /></td>
                </tr>
                <tr v-if="!locations.length"><td colspan="4" class="text-center text-medium-emphasis">No locations configured yet</td></tr>
              </tbody>
            </v-table>
          </v-card-text>
        </v-window-item>

        <v-window-item value="villages">
          <v-card-text>
            <div class="d-flex align-center ga-3 mb-2 flex-wrap">
              <v-select v-model="villageStateFilter" :items="states" item-title="name" item-value="code" label="State" clearable hide-details density="compact" style="max-width: 200px" />
              <v-select v-model="villageCountyFilter" :items="countiesForState(villageStateFilter)" item-title="name" item-value="code" label="County" clearable hide-details density="compact" style="max-width: 200px" />
              <v-select v-model="villageLocationFilter" :items="locationsForCounty(villageCountyFilter)" item-title="name" item-value="code" label="Filter by location" clearable hide-details density="compact" style="max-width: 220px" />
              <v-spacer />
              <v-btn size="small" color="primary" prepend-icon="mdi-plus" @click="openCreate('VILLAGE')">Add Village</v-btn>
            </div>
            <v-table density="comfortable" :loading="loading">
              <thead><tr><th>Code</th><th>Name</th><th>Location</th><th class="text-right">Actions</th></tr></thead>
              <tbody>
                <tr v-for="v in villages" :key="v.code">
                  <td>{{ v.code }}</td><td>{{ v.name }}</td><td>{{ v.locationCode }}</td>
                  <td class="text-right"><v-btn icon="mdi-delete" size="small" variant="text" color="error" @click="remove('VILLAGE', v.code)" /></td>
                </tr>
                <tr v-if="!villages.length"><td colspan="4" class="text-center text-medium-emphasis">No villages configured yet</td></tr>
              </tbody>
            </v-table>
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
          <v-text-field v-model="form.code" label="Code" />
          <v-text-field v-model="form.name" label="Name" />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="dialog = false">Cancel</v-btn>
          <v-btn color="primary" :loading="saving" @click="save">Save</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
