<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useAnchorScope } from '@/composables/useAnchorScope'
import { useOrgCascade } from '@/composables/useOrgCascade'

interface Officer {
  id: number
  email: string
  firstName: string
  lastName: string
  organisationCode: string
  active: string
  createdAt?: string
}

interface GeoNode {
  code: string
  name: string
  stateCode?: string
  countyCode?: string
  locationCode?: string
}

interface OfficerLocation { stateCode?: string; countyCode?: string; payamCode?: string; bomaCode?: string }

const auth = useAuthStore()
const toast = useToast()
const { confirmAction } = useConfirm()
const { anchors, selectedAnchorId, anchorGateActive } = useAnchorScope()
const { dialogAnchorId, dialogOrganizations, resetDialogScope } = useOrgCascade()
const loading = ref(true)
const officers = ref<Officer[]>([])
const tableSearch = ref('')
const organizations = ref<{ organisationCode: string; name: string }[]>([])
const dialog = ref(false)
const editing = ref(false)
const saving = ref(false)
const form = ref({ firstName: '', lastName: '', email: '', organisationCode: '' })

const states = ref<GeoNode[]>([])
const counties = ref<GeoNode[]>([])
const locations = ref<GeoNode[]>([])
const villages = ref<GeoNode[]>([])
const geoLoading = ref(true)

const filters = ref({ organisationCode: null as string | null, active: null as string | null })

// Both roles see everything in their scope immediately -- the backend already
// treats an unset anchor/organisation filter as "show all" (`IS NULL OR ...`),
// so the picker below narrows the view without ever blocking it.
const scopeReady = computed(() => true)

const headers = [
  { title: 'Name', key: 'name' },
  { title: 'Email', key: 'email' },
  { title: 'Organization', key: 'organisationCode' },
  { title: 'Status', key: 'active' },
  { title: 'Location', key: 'location', sortable: false, align: 'start' as const },
  { title: 'Actions', key: 'actions', sortable: false, align: 'start' as const },
]

// Name-not-code lookups, matching the pattern used on Households.
const orgNameByCode = computed(() => new Map(organizations.value.map((o) => [o.organisationCode, o.name])))
const stateNameByCode = computed(() => new Map(states.value.map((s) => [s.code, s.name])))
const countyNameByCode = computed(() => new Map(counties.value.map((c) => [c.code, c.name])))
const locationNameByCode = computed(() => new Map(locations.value.map((l) => [l.code, l.name])))
const villageNameByCode = computed(() => new Map(villages.value.map((v) => [v.code, v.name])))
function orgName(code?: string) { return (code && orgNameByCode.value.get(code)) || code || '—' }
function stateName(code?: string) { return (code && stateNameByCode.value.get(code)) || code || '—' }
function countyName(code?: string) { return (code && countyNameByCode.value.get(code)) || code || '—' }
function locationNodeName(code?: string) { return (code && locationNameByCode.value.get(code)) || code || '—' }
function villageName(code?: string) { return (code && villageNameByCode.value.get(code)) || code || '—' }
// Villages can share a name across different states/counties, so any display of an
// assigned location must show the full path, not just the village.
function locationPath(loc: OfficerLocation) {
  return [stateName(loc.stateCode), countyName(loc.countyCode), locationNodeName(loc.payamCode), villageName(loc.bomaCode)].join(' › ')
}

const countiesForState = (stateCode: string) => stateCode ? counties.value.filter((c) => c.stateCode === stateCode) : counties.value
const locationsForCounty = (countyCode: string) => countyCode ? locations.value.filter((l) => l.countyCode === countyCode) : locations.value
const villagesForLocation = (locationCode: string) => locationCode ? villages.value.filter((v) => v.locationCode === locationCode) : villages.value

async function load() {
  loading.value = true
  try {
    const res = await dispatch<{ results: Officer[] }>('GET_OFFICERS', {
      targetAnchorId: auth.isSystemAdmin ? selectedAnchorId.value : undefined,
      organisationCode: filters.value.organisationCode ?? undefined,
      active: filters.value.active ?? undefined,
    })
    officers.value = res.results
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load officers')
  } finally {
    loading.value = false
  }
}

watch(dialogAnchorId, () => { form.value.organisationCode = '' })
watch(() => filters.value.organisationCode, load)
watch(() => filters.value.active, load)
watch(selectedAnchorId, () => { filters.value.organisationCode = null; loadOrganizations(); load() })

function clearFilters() {
  filters.value = { organisationCode: null, active: null }
  load()
}

async function loadGeo() {
  geoLoading.value = true
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
  } catch {
    // Assign-location dialog just shows empty pickers; the rest of the page still works.
  } finally {
    geoLoading.value = false
  }
}

async function loadOrganizations() {
  try {
    const res = await dispatch<{ results: typeof organizations.value }>('GET_ORGANIZATIONS', {
      targetAnchorId: auth.isSystemAdmin ? selectedAnchorId.value : undefined,
    })
    organizations.value = res.results
  } catch {
    // Filter dropdown just stays empty; the list itself still loaded above.
  }
}

onMounted(() => {
  load()
  loadGeo()
  loadOrganizations()
})

function openCreate() {
  editing.value = false
  form.value = { firstName: '', lastName: '', email: '', organisationCode: '' }
  resetDialogScope(auth.isSystemAdmin ? selectedAnchorId.value : null)
  dialog.value = true
}

function openEdit(officer: Officer) {
  editing.value = true
  form.value = { firstName: officer.firstName, lastName: officer.lastName, email: officer.email, organisationCode: officer.organisationCode }
  dialog.value = true
}

async function save() {
  if (!form.value.firstName.trim() || !form.value.lastName.trim() || !/.+@.+\..+/.test(form.value.email)) {
    toast.error('Enter the officer\'s first name, last name and a valid email address')
    return
  }
  if (auth.isSystemAdmin && !editing.value && !dialogAnchorId.value) {
    toast.error('Select the anchor this officer\'s organisation belongs to')
    return
  }
  if (auth.isAnchor && !editing.value && !form.value.organisationCode) {
    toast.error('Select the organisation this officer belongs to')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await dispatch('UPDATE_OFFICER', form.value)
      toast.success('Officer updated')
    } else {
      await dispatch('CREATE_OFFICER', form.value)
      toast.success('Officer registered. Temporary password sent by email')
    }
    dialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Save failed')
  } finally {
    saving.value = false
  }
}

async function setOfficerActive(officer: Officer, active: boolean) {
  if (!await confirmAction({
    title: `${active ? 'Activate' : 'Deactivate'} officer?`,
    message: active
      ? `${officer.firstName} ${officer.lastName} will regain access to the field app.`
      : `${officer.firstName} ${officer.lastName} will no longer be able to sign in to the field app.`,
    confirmLabel: active ? 'Activate' : 'Deactivate',
    color: active ? 'secondary' : 'warning',
  })) return
  try {
    await dispatch('TOGGLE_OFFICER_STATUS', { email: officer.email, active: active ? 1 : 0 })
    toast.success(active ? 'Officer activated' : 'Officer deactivated')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : `Failed to ${active ? 'activate' : 'deactivate'} officer`)
  }
}

// ---- Assign locations (geotagging -- which villages/counties an officer covers) ----
const locationDialog = ref(false)
const locationTarget = ref<Officer | null>(null)
const locationForm = ref({ stateCode: '', countyCode: '', locationCode: '', villageCode: '' })
const assignedLocations = ref<OfficerLocation[]>([])
const loadingLocations = ref(false)
const assigningLocation = ref(false)

async function openAssignLocations(officer: Officer) {
  locationTarget.value = officer
  locationForm.value = { stateCode: '', countyCode: '', locationCode: '', villageCode: '' }
  assignedLocations.value = []
  locationDialog.value = true
  loadingLocations.value = true
  try {
    const res = await dispatch<{ results: { locations?: OfficerLocation[] }[] }>('GET_OFFICER', { email: officer.email })
    assignedLocations.value = res.results?.[0]?.locations ?? []
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load assigned locations')
  } finally {
    loadingLocations.value = false
  }
}

async function assignLocation() {
  if (!locationTarget.value || !locationForm.value.villageCode) {
    toast.error('Pick a village to assign')
    return
  }
  assigningLocation.value = true
  try {
    await dispatch('ASSIGN_OFFICER_LOCATION', {
      supervisorId: locationTarget.value.id,
      stateCode: locationForm.value.stateCode || undefined,
      countyCode: locationForm.value.countyCode || undefined,
      payamCode: locationForm.value.locationCode || undefined,
      bomaCode: locationForm.value.villageCode,
    })
    toast.success('Location assigned')
    assignedLocations.value = [{ ...locationForm.value, payamCode: locationForm.value.locationCode, bomaCode: locationForm.value.villageCode }, ...assignedLocations.value]
    locationForm.value = { stateCode: '', countyCode: '', locationCode: '', villageCode: '' }
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to assign location')
  } finally {
    assigningLocation.value = false
  }
}
</script>

<template>
  <div>
    <div class="d-flex align-center justify-space-between mb-4">
      <h1 class="page-title">Field Officers</h1>
      <v-btn v-if="scopeReady && auth.can('ACCESS_SUPERVISORS')" color="secondary" prepend-icon="mdi-account-plus" @click="openCreate">Register Field Officer</v-btn>
    </div>

    <template v-if="scopeReady">
    <v-card variant="flat" border>
      <v-card-text>
        <v-row dense align="center">
          <v-col v-if="anchorGateActive" cols="12" sm="4" md="3">
            <v-select v-model="selectedAnchorId" :items="anchors" item-title="name" item-value="id" label="Anchor" clearable hide-details density="compact" prepend-inner-icon="mdi-bank-outline" />
          </v-col>
          <v-col v-if="auth.isSystemAdmin || auth.isAnchorAdministrator" cols="12" sm="4" md="3">
            <v-select v-model="filters.organisationCode" :items="organizations" item-title="name" item-value="organisationCode" label="Organisation" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="12" sm="4" md="3">
            <v-select v-model="filters.active" :items="[{ title: 'Active', value: '1' }, { title: 'Inactive', value: '0' }]" label="Status" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="12" sm="4" md="3">
            <v-text-field v-model="tableSearch" prepend-inner-icon="mdi-magnify" label="Search" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="auto">
            <v-btn variant="text" size="small" @click="clearFilters">Clear filters</v-btn>
          </v-col>
        </v-row>
      </v-card-text>
      <v-data-table :headers="headers" :items="officers" :search="tableSearch" :loading="loading">
        <template #item.name="{ item }">{{ item.firstName }} {{ item.lastName }}</template>
        <template #item.organisationCode="{ item }">{{ orgName(item.organisationCode) }}</template>
        <template #item.active="{ item }">
          <v-chip size="small" :color="item.active === '1' ? 'success' : 'error'" variant="tonal">
            {{ item.active === '1' ? 'Active' : 'Inactive' }}
          </v-chip>
        </template>
        <template #item.location="{ item }">
          <v-btn v-if="auth.can('ACCESS_SUPERVISORS')" variant="tonal" size="small" color="primary" prepend-icon="mdi-map-marker-outline" @click="openAssignLocations(item)">
            Assign Location
          </v-btn>
        </template>
        <template #item.actions="{ item }">
          <v-btn v-if="auth.can('ACCESS_SUPERVISORS')" icon="mdi-pencil" variant="text" size="small" :aria-label="`Edit ${item.firstName} ${item.lastName}`" @click="openEdit(item)" />
          <v-btn
            v-if="auth.can('ACCESS_SUPERVISORS')"
            :icon="item.active === '1' ? 'mdi-account-cancel-outline' : 'mdi-account-check-outline'"
            variant="text" size="small" :color="item.active === '1' ? 'error' : 'success'"
            :aria-label="`${item.active === '1' ? 'Deactivate' : 'Activate'} ${item.firstName} ${item.lastName}`"
            @click="setOfficerActive(item, item.active !== '1')"
          />
        </template>
      </v-data-table>
    </v-card>
    </template>

    <v-dialog v-model="dialog" max-width="560">
      <v-card class="officer-editor">
        <div class="editor-heading">
          <div>
            <div class="editor-title"><v-icon icon="mdi-account-tie" size="20" /> {{ editing ? 'Edit Officer' : 'Register Field Officer' }}</div>
            <p>{{ editing ? 'Update this officer\'s profile.' : 'Create a field officer account and assign their organisation.' }}</p>
          </div>
          <dialog-close-button @close="dialog = false" />
        </div>
        <v-form @submit.prevent="save">
          <div class="field-grid">
            <v-select
              v-if="auth.isSystemAdmin && !editing"
              v-model="dialogAnchorId" :items="anchors" item-title="name" item-value="id"
              label="Anchor" density="compact" placeholder="Choose an anchor" required
            />
            <v-select
              v-if="auth.isAnchor && !editing"
              v-model="form.organisationCode" :items="dialogOrganizations" item-title="name" item-value="organisationCode"
              label="Organisation" density="compact" placeholder="Choose an organisation"
              :disabled="auth.isSystemAdmin && !dialogAnchorId" required
            />
            <v-text-field v-model="form.firstName" label="First name" placeholder="e.g. Jane" density="compact" required />
            <v-text-field v-model="form.lastName" label="Last name" placeholder="e.g. Mwangi" density="compact" required />
            <v-text-field v-model="form.email" label="Email" type="email" placeholder="e.g. jane.mwangi@example.org" :disabled="editing" density="compact" required />
          </div>
          <v-alert v-if="!editing" type="info" variant="tonal" density="compact" class="mt-1">
            A temporary password will be generated and emailed to this officer.
          </v-alert>
          <div class="editor-actions">
            <v-btn variant="text" @click="dialog = false">Cancel</v-btn>
            <v-btn color="secondary" type="submit" :loading="saving" prepend-icon="mdi-check">Save</v-btn>
          </div>
        </v-form>
      </v-card>
    </v-dialog>

    <v-dialog v-model="locationDialog" max-width="560">
      <v-card v-if="locationTarget">
        <dialog-close-button @close="locationDialog = false" />
        <v-card-title>Assign locations — {{ locationTarget.firstName }} {{ locationTarget.lastName }}</v-card-title>
        <v-card-text>
          <p class="text-caption text-medium-emphasis mb-3">
            Map this officer to the villages they cover, so attendance and household activity can be geotagged to a real coverage area.
          </p>
          <v-row dense>
            <v-col cols="6"><v-select v-model="locationForm.stateCode" :items="states" item-title="name" item-value="code" label="State" density="compact" :loading="geoLoading" :disabled="geoLoading" /></v-col>
            <v-col cols="6"><v-select v-model="locationForm.countyCode" :items="countiesForState(locationForm.stateCode)" item-title="name" item-value="code" label="County" density="compact" :loading="geoLoading" :disabled="geoLoading" /></v-col>
            <v-col cols="6"><v-select v-model="locationForm.locationCode" :items="locationsForCounty(locationForm.countyCode)" item-title="name" item-value="code" label="Location" density="compact" :loading="geoLoading" :disabled="geoLoading" /></v-col>
            <v-col cols="6"><v-select v-model="locationForm.villageCode" :items="villagesForLocation(locationForm.locationCode)" item-title="name" item-value="code" label="Village" density="compact" :loading="geoLoading" :disabled="geoLoading" /></v-col>
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
          <v-chip v-for="(loc, i) in assignedLocations" :key="i" size="small" variant="tonal" color="primary" class="mr-1 mb-1">
            <v-icon icon="mdi-map-marker-outline" size="14" start />
            {{ locationPath(loc) }}
          </v-chip>
          <p v-if="!loadingLocations && !assignedLocations.length" class="text-caption text-medium-emphasis">No coverage areas assigned yet.</p>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="locationDialog = false">Close</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.officer-editor { padding: 22px 24px; }
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
