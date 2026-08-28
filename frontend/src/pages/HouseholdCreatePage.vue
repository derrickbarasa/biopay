<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useAnchorScope } from '@/composables/useAnchorScope'
import { useOrgCascade } from '@/composables/useOrgCascade'

// Dedicated full page for "Add Household" (replaces the old in-dialog form on
// HouseholdsPage). Mirrors the HouseholdDetailPage/PayrollGeneratePage
// pattern: its own route so registration, the duplicate-candidate review and
// the location cascade all get real breathing room.

interface GeoNode {
  code: string
  name: string
  stateCode?: string
  countyCode?: string
  locationCode?: string
}

interface DuplicateCandidate {
  householdNumber: string
  householdName?: string
  phoneNumber?: string
  bomaCode?: string
  reasons: string[]
}

const auth = useAuthStore()
const toast = useToast()
const router = useRouter()
const { anchors } = useAnchorScope()
const { dialogAnchorId, dialogOrganizations, resetDialogScope } = useOrgCascade()

const saving = ref(false)
const checkingDup = ref(false)
const duplicateCandidates = ref<DuplicateCandidate[]>([])

const states = ref<GeoNode[]>([])
const counties = ref<GeoNode[]>([])
const locations = ref<GeoNode[]>([])
const villages = ref<GeoNode[]>([])

const countiesForState = (code: string) => (code ? counties.value.filter((c) => c.stateCode === code) : counties.value)
const locationsForCounty = (code: string) => (code ? locations.value.filter((l) => l.countyCode === code) : locations.value)
const villagesForLocation = (code: string) => (code ? villages.value.filter((v) => v.locationCode === code) : villages.value)

const form = ref({
  householdName: '', age: null as number | null, gender: '', phoneNumber: '',
  householdSize: null as number | null, stateCode: '', countyCode: '', locationCode: '', villageCode: '',
  organisationCode: null as string | null,
})

function onStateChange() { form.value.countyCode = ''; form.value.locationCode = ''; form.value.villageCode = '' }
function onCountyChange() { form.value.locationCode = ''; form.value.villageCode = '' }
function onLocationChange() { form.value.villageCode = '' }

async function loadGeo() {
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
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load locations')
  }
}

function goToList() {
  router.push({ name: 'households' })
}

// Screens the entry against existing households before creating. If any possible
// duplicates come back, they're shown and creation waits for an explicit "Register
// anyway"; a clean check registers immediately.
async function attemptSave() {
  if (!form.value.householdName.trim()) {
    toast.error('Head of household name is required')
    return
  }
  if (auth.isSystemAdmin && !dialogAnchorId.value) {
    toast.error('Select the anchor this household\'s organisation belongs to')
    return
  }
  if (auth.isAnchor && !form.value.organisationCode) {
    toast.error('Select the organisation this household belongs to')
    return
  }
  checkingDup.value = true
  try {
    const res = await dispatch<{ results: DuplicateCandidate[] }>('CHECK_HOUSEHOLD_DUPLICATE', {
      householdName: form.value.householdName,
      phoneNumber: form.value.phoneNumber || undefined,
      bomaCode: form.value.villageCode || undefined,
    })
    if (res.results.length) {
      duplicateCandidates.value = res.results
      return
    }
  } catch {
    // If the check itself fails, don't block registration -- fall through to save.
  } finally {
    checkingDup.value = false
  }
  await save()
}

async function save() {
  saving.value = true
  try {
    await dispatch('CREATE_HOUSEHOLD', {
      householdName: form.value.householdName, age: form.value.age, gender: form.value.gender,
      phoneNumber: form.value.phoneNumber, householdSize: form.value.householdSize,
      stateCode: form.value.stateCode, countyCode: form.value.countyCode,
      payamCode: form.value.locationCode, bomaCode: form.value.villageCode,
      organisationCode: form.value.organisationCode || undefined,
    })
    toast.success('Household registered')
    goToList()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Save failed')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  resetDialogScope(null)
  loadGeo()
})
</script>

<template>
  <div>
    <div class="d-flex align-center mb-4 ga-3">
      <v-btn icon="mdi-arrow-left" variant="text" aria-label="Back to households" @click="goToList" />
      <h1 class="text-h5 font-weight-bold mb-0">Add Household</h1>
    </div>

    <v-card variant="flat" border>
      <v-card-text>
        <v-select
          v-if="auth.isSystemAdmin"
          v-model="dialogAnchorId" :items="anchors" item-title="name" item-value="id"
          label="Anchor" class="mb-2" placeholder="Choose an anchor" required
        />
        <v-select
          v-if="auth.isAnchor"
          v-model="form.organisationCode" :items="dialogOrganizations" item-title="name" item-value="organisationCode"
          label="Organisation" class="mb-2" placeholder="Choose an organisation"
          :disabled="auth.isSystemAdmin && !dialogAnchorId" required
        />
        <v-text-field v-model="form.householdName" label="Head of household name" placeholder="e.g. Jane Doe" />
        <v-row>
          <v-col cols="6" sm="4"><v-text-field v-model.number="form.age" label="Age" type="number" placeholder="e.g. 34" /></v-col>
          <v-col cols="6" sm="4">
            <v-select v-model="form.gender" label="Gender" :items="['M', 'F']" />
          </v-col>
          <v-col cols="12" sm="4"><v-text-field v-model.number="form.householdSize" label="Household size" type="number" /></v-col>
        </v-row>
        <v-text-field v-model="form.phoneNumber" label="Phone number" />
        <div class="text-caption text-medium-emphasis mt-2 mb-1">Location</div>
        <v-row dense>
          <v-col cols="6" sm="3"><v-select v-model="form.stateCode" :items="states" item-title="name" item-value="code" label="State" density="compact" @update:model-value="onStateChange" /></v-col>
          <v-col cols="6" sm="3"><v-select v-model="form.countyCode" :items="countiesForState(form.stateCode)" item-title="name" item-value="code" label="County" density="compact" @update:model-value="onCountyChange" /></v-col>
          <v-col cols="6" sm="3"><v-select v-model="form.locationCode" :items="locationsForCounty(form.countyCode)" item-title="name" item-value="code" label="Location" density="compact" @update:model-value="onLocationChange" /></v-col>
          <v-col cols="6" sm="3"><v-select v-model="form.villageCode" :items="villagesForLocation(form.locationCode)" item-title="name" item-value="code" label="Village" density="compact" /></v-col>
        </v-row>

        <v-alert
          v-if="duplicateCandidates.length"
          type="warning" variant="tonal" density="compact" class="mt-3"
          icon="mdi-account-alert-outline"
        >
          <div class="font-weight-medium mb-1">
            {{ duplicateCandidates.length }} possible duplicate{{ duplicateCandidates.length > 1 ? 's' : '' }} found
          </div>
          <div v-for="c in duplicateCandidates" :key="c.householdNumber" class="text-body-2 mb-1">
            <strong>{{ c.householdName }}</strong> ({{ c.householdNumber }}) — {{ c.reasons.join(', ') }}
          </div>
          <div class="text-caption mt-1">Review these before registering a new record.</div>
        </v-alert>

        <div class="d-flex justify-end ga-3 mt-4">
          <v-btn variant="text" @click="goToList">Cancel</v-btn>
          <v-btn
            v-if="duplicateCandidates.length"
            color="warning" variant="flat" :loading="saving" @click="save"
          >
            Register anyway
          </v-btn>
          <v-btn v-else color="secondary" :loading="checkingDup || saving" @click="attemptSave">Save</v-btn>
        </div>
      </v-card-text>
    </v-card>
  </div>
</template>
