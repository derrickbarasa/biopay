<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useAnchorScope } from '@/composables/useAnchorScope'

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
const { anchors, selectedAnchorId, anchorGateActive, anchorChosen } = useAnchorScope()
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
  { title: 'Actions', key: 'actions', sortable: false, align: 'end' as const },
]

// Name-not-code lookups, matching the pattern used on Households.
const orgNameByCode = computed(() => new Map(organizations.value.map((o) => [o.organisationCode, o.name])))
const villageNameByCode = computed(() => new Map(villages.value.map((v) => [v.code, v.name])))
function orgName(code?: string) { return (code && orgNameByCode.value.get(code)) || code || '—' }
function villageName(code?: string) { return (code && villageNameByCode.value.get(code)) || code || '—' }

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
  if (!(auth.isAnchorAdministrator || (auth.isSystemAdmin && selectedAnchorId.value))) return
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

    <v-select
      v-if="anchorGateActive" v-model="selectedAnchorId" :items="anchors" item-title="name" item-value="id"
      label="Choose anchor" variant="outlined" class="mb-4" style="max-width: 420px"
      prepend-inner-icon="mdi-bank-outline"
    />
    <v-select
      v-else-if="auth.isAnchorAdministrator" v-model="filters.organisationCode" :items="organizations" item-title="name" item-value="organisationCode"
      label="Choose organisation" variant="outlined" class="mb-4" style="max-width: 420px"
      prepend-inner-icon="mdi-domain"
    />
    <v-alert v-if="auth.isSystemAdmin ? !anchorChosen : (auth.isAnchorAdministrator && !filters.organisationCode)" type="info" variant="tonal" class="mb-4">
      {{ auth.isSystemAdmin ? 'Showing field officers across every anchor. Choose one above to narrow the list.' : 'Showing field officers across every organisation. Choose one above to narrow the list.' }}
    </v-alert>

    <template v-if="scopeReady">
    <v-card variant="flat" border>
      <v-card-text>
        <v-row dense align="center">
          <v-col v-if="auth.isSystemAdmin" cols="12" sm="4" md="3">
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
        <template #item.actions="{ item }">
          <v-btn v-if="auth.can('ACCESS_SUPERVISORS')" icon="mdi-pencil" variant="text" size="small" :aria-label="`Edit ${item.firstName} ${item.lastName}`" @click="openEdit(item)" />
          <v-btn v-if="auth.can('ACCESS_SUPERVISORS')" icon="mdi-map-marker-outline" variant="text" size="small" :aria-label="`Assign locations to ${item.firstName} ${item.lastName}`" @click="openAssignLocations(item)" />
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
            <v-text-field v-model="form.firstName" label="First name" density="compact" required />
            <v-text-field v-model="form.lastName" label="Last name" density="compact" required />
            <v-text-field v-model="form.email" label="Email" type="email" :disabled="editing" density="compact" required />
            <v-select
              v-if="auth.isAnchor && !editing"
              v-model="form.organisationCode" :items="organizations" item-title="name" item-value="organisationCode"
              label="Organisation" density="compact"
            />
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
            {{ villageName(loc.bomaCode) }}
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
