<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'

interface Officer {
  id: number
  email: string
  firstName: string
  lastName: string
}

interface GeoNode {
  code: string
  name: string
  stateCode?: string
  countyCode?: string
  locationCode?: string
}

interface OfficerLocation {
  stateCode?: string
  countyCode?: string
  payamCode?: string
  bomaCode?: string
}

const props = defineProps<{
  modelValue: boolean
  officer: Officer | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const toast = useToast()
const states = ref<GeoNode[]>([])
const counties = ref<GeoNode[]>([])
const locations = ref<GeoNode[]>([])
const villages = ref<GeoNode[]>([])
const geoLoaded = ref(false)
const geoLoading = ref(false)
const loadingLocations = ref(false)
const assigningLocation = ref(false)
const assignedLocations = ref<OfficerLocation[]>([])
const locationForm = ref({ stateCode: '', countyCode: '', locationCode: '', villageCode: '' })

const stateNameByCode = computed(() => new Map(states.value.map((item) => [item.code, item.name])))
const countyNameByCode = computed(() => new Map(counties.value.map((item) => [item.code, item.name])))
const locationNameByCode = computed(() => new Map(locations.value.map((item) => [item.code, item.name])))
const villageNameByCode = computed(() => new Map(villages.value.map((item) => [item.code, item.name])))

const countiesForState = computed(() => locationForm.value.stateCode
  ? counties.value.filter((item) => item.stateCode === locationForm.value.stateCode)
  : counties.value)
const locationsForCounty = computed(() => locationForm.value.countyCode
  ? locations.value.filter((item) => item.countyCode === locationForm.value.countyCode)
  : locations.value)
const villagesForLocation = computed(() => locationForm.value.locationCode
  ? villages.value.filter((item) => item.locationCode === locationForm.value.locationCode)
  : villages.value)

watch(() => locationForm.value.stateCode, () => {
  locationForm.value.countyCode = ''
  locationForm.value.locationCode = ''
  locationForm.value.villageCode = ''
})

watch(() => locationForm.value.countyCode, () => {
  locationForm.value.locationCode = ''
  locationForm.value.villageCode = ''
})

watch(() => locationForm.value.locationCode, () => {
  locationForm.value.villageCode = ''
})

watch(() => [props.modelValue, props.officer?.email] as const, async ([open]) => {
  if (!open || !props.officer) return
  locationForm.value = { stateCode: '', countyCode: '', locationCode: '', villageCode: '' }
  assignedLocations.value = []
  await Promise.all([loadGeo(), loadAssignedLocations()])
}, { immediate: true })

function close() {
  emit('update:modelValue', false)
}

async function loadGeo() {
  if (geoLoaded.value || geoLoading.value) return
  geoLoading.value = true
  try {
    const [stateResponse, countyResponse, locationResponse, villageResponse] = await Promise.all([
      dispatch<{ results: GeoNode[] }>('GET_STATES'),
      dispatch<{ results: GeoNode[] }>('GET_COUNTIES'),
      dispatch<{ results: GeoNode[] }>('GET_LOCATIONS'),
      dispatch<{ results: GeoNode[] }>('GET_VILLAGES'),
    ])
    states.value = stateResponse.results ?? []
    counties.value = countyResponse.results ?? []
    locations.value = locationResponse.results ?? []
    villages.value = villageResponse.results ?? []
    geoLoaded.value = true
  } catch (error) {
    toast.error(error instanceof Error ? error.message : 'Failed to load the location hierarchy')
  } finally {
    geoLoading.value = false
  }
}

async function loadAssignedLocations() {
  if (!props.officer) return
  loadingLocations.value = true
  try {
    const response = await dispatch<{ results: { locations?: OfficerLocation[] }[] }>('GET_OFFICER', { email: props.officer.email })
    assignedLocations.value = response.results?.[0]?.locations ?? []
  } catch (error) {
    toast.error(error instanceof Error ? error.message : 'Failed to load assigned locations')
  } finally {
    loadingLocations.value = false
  }
}

function locationPath(location: OfficerLocation) {
  const names = [
    location.stateCode && (stateNameByCode.value.get(location.stateCode) || location.stateCode),
    location.countyCode && (countyNameByCode.value.get(location.countyCode) || location.countyCode),
    location.payamCode && (locationNameByCode.value.get(location.payamCode) || location.payamCode),
    location.bomaCode && (villageNameByCode.value.get(location.bomaCode) || location.bomaCode),
  ].filter(Boolean)
  return names.join(' › ') || 'Location unavailable'
}

async function assignLocation() {
  if (!props.officer || !locationForm.value.villageCode) {
    toast.error('Pick a village to assign')
    return
  }
  assigningLocation.value = true
  try {
    await dispatch('ASSIGN_OFFICER_LOCATION', {
      supervisorId: props.officer.id,
      stateCode: locationForm.value.stateCode || undefined,
      countyCode: locationForm.value.countyCode || undefined,
      payamCode: locationForm.value.locationCode || undefined,
      bomaCode: locationForm.value.villageCode,
    })
    toast.success('Location assigned')
    await loadAssignedLocations()
    locationForm.value = { stateCode: '', countyCode: '', locationCode: '', villageCode: '' }
  } catch (error) {
    toast.error(error instanceof Error ? error.message : 'Failed to assign location')
  } finally {
    assigningLocation.value = false
  }
}
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="560" @update:model-value="emit('update:modelValue', $event)">
    <v-card v-if="officer">
      <dialog-close-button @close="close" />
      <v-card-title>Assign locations — {{ officer.firstName }} {{ officer.lastName }}</v-card-title>
      <v-card-text>
        <p class="text-caption text-medium-emphasis mb-3">
          Map this officer to the villages they cover, so attendance and household activity can be geotagged to a real coverage area.
        </p>
        <v-row dense>
          <v-col cols="12" sm="6">
            <v-select v-model="locationForm.stateCode" :items="states" item-title="name" item-value="code" label="State" density="compact" :loading="geoLoading" :disabled="geoLoading" />
          </v-col>
          <v-col cols="12" sm="6">
            <v-select v-model="locationForm.countyCode" :items="countiesForState" item-title="name" item-value="code" label="County" density="compact" :loading="geoLoading" :disabled="geoLoading || !locationForm.stateCode" />
          </v-col>
          <v-col cols="12" sm="6">
            <v-select v-model="locationForm.locationCode" :items="locationsForCounty" item-title="name" item-value="code" label="Location" density="compact" :loading="geoLoading" :disabled="geoLoading || !locationForm.countyCode" />
          </v-col>
          <v-col cols="12" sm="6">
            <v-select v-model="locationForm.villageCode" :items="villagesForLocation" item-title="name" item-value="code" label="Village" density="compact" :loading="geoLoading" :disabled="geoLoading || !locationForm.locationCode" />
          </v-col>
        </v-row>
        <p v-if="!geoLoading && !states.length" class="text-caption text-medium-emphasis mb-2">
          No location hierarchy configured yet — add states, counties, locations and villages on the Locations page first.
        </p>
        <v-btn variant="outlined" size="small" prepend-icon="mdi-plus" :loading="assigningLocation" :disabled="!locationForm.villageCode" @click="assignLocation">
          Add coverage area
        </v-btn>

        <v-divider class="my-4" />
        <div class="text-caption text-medium-emphasis mb-2">Currently assigned</div>
        <v-progress-linear v-if="loadingLocations" indeterminate color="primary" class="mb-2" />
        <v-chip v-for="(location, index) in assignedLocations" :key="`${location.bomaCode}-${index}`" size="small" variant="tonal" color="primary" class="mr-1 mb-1">
          <v-icon icon="mdi-map-marker-outline" size="14" start />
          {{ locationPath(location) }}
        </v-chip>
        <p v-if="!loadingLocations && !assignedLocations.length" class="text-caption text-medium-emphasis">No coverage areas assigned yet.</p>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" @click="close">Close</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
