<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useAnchorScope } from '@/composables/useAnchorScope'
import { useOrgCascade } from '@/composables/useOrgCascade'
import { LEGAL_STATUS_OPTIONS, MARITAL_STATUS_OPTIONS, VULNERABILITY_OPTIONS } from '@/constants/householdClassifications'
import { dateOfBirthToAge } from '@/utils/dateOfBirth'

const maritalStatusItems: readonly string[] = MARITAL_STATUS_OPTIONS

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
const { anchors } = useAnchorScope({ activeOnly: true })
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
  householdName: '', dateOfBirth: null as Date | null, gender: '', maritalStatus: null as string | null, spouseName: '', phoneNumber: '',
  householdSize: null as number | null, stateCode: '', countyCode: '', locationCode: '', villageCode: '',
  vulnerabilityStatuses: [] as string[], legalStatus: null as string | null,
  organisationCode: null as string | null,
  photo: null as File | null,
})

// A Super Admin's org list is re-fetched for whichever anchor they pick (see useOrgCascade) --
// a previously chosen organisation almost never belongs to the new anchor, so it's cleared
// rather than left stale/invalid in the field.
watch(dialogAnchorId, () => {
  form.value.organisationCode = null
  onStateChange()
  void loadGeo()
})

function onStateChange() { form.value.countyCode = ''; form.value.locationCode = ''; form.value.villageCode = '' }
function onCountyChange() { form.value.locationCode = ''; form.value.villageCode = '' }
function onLocationChange() { form.value.villageCode = '' }

async function loadGeo() {
  try {
    // A Platform Owner is registering under the anchor selected above. Loading the
    // catalogue without this scope mixed matching place names from every anchor into
    // one picker (for example, two separate "Ruiru" rows).
    const scope = auth.isSystemAdmin && dialogAnchorId.value != null
      ? { targetAnchorId: dialogAnchorId.value }
      : {}
    const [s, c, l, v] = await Promise.all([
      dispatch<{ results: GeoNode[] }>('GET_STATES', scope),
      dispatch<{ results: GeoNode[] }>('GET_COUNTIES', scope),
      dispatch<{ results: GeoNode[] }>('GET_LOCATIONS', scope),
      dispatch<{ results: GeoNode[] }>('GET_VILLAGES', scope),
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

// Screens for duplicates first; a clean check saves immediately, otherwise waits for "Register anyway".
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

function onPhotoFile(file: File | File[] | null) {
  form.value.photo = Array.isArray(file) ? (file[0] ?? null) : file
}

function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(reader.error ?? new Error('Failed to read the selected file'))
    reader.readAsDataURL(file)
  })
}

// The photo upload is a separate request from CREATE_HOUSEHOLD. Keep the Save action active
// until that upload completes, so the detail page reached after Save can reliably show the
// selected photo rather than racing a detached background request.
async function save() {
  saving.value = true
  try {
    const photo = form.value.photo
    if (photo && !['image/jpeg', 'image/png'].includes(photo.type)) {
      toast.error('Choose a JPG or PNG image for the household head photo')
      return
    }
    if (photo && photo.size > 5 * 1024 * 1024) {
      toast.error('The household head photo must be 5MB or smaller')
      return
    }
    const organisationCode = form.value.organisationCode || undefined
    const created = await dispatch<{ householdNumber: string }>('CREATE_HOUSEHOLD', {
      householdName: form.value.householdName, age: dateOfBirthToAge(form.value.dateOfBirth), gender: form.value.gender,
      maritalStatus: form.value.maritalStatus || undefined, spouseName: form.value.spouseName || undefined,
      phoneNumber: form.value.phoneNumber, householdSize: form.value.householdSize,
      vulnerabilityStatuses: form.value.vulnerabilityStatuses,
      legalStatus: form.value.legalStatus || undefined,
      stateCode: form.value.stateCode, countyCode: form.value.countyCode,
      payamCode: form.value.locationCode, bomaCode: form.value.villageCode,
      organisationCode,
    })
    if (photo) {
      try {
        const dataUrl = await fileToDataUrl(photo)
        await dispatch('UPLOAD_IMAGE', {
          beneficiaryId: created.householdNumber,
          beneficiaryType: 1,
          imageBase64: dataUrl,
          extension: (photo.name.split('.').pop() || 'jpg').toLowerCase(),
          organisationCode,
        })
      } catch (err) {
        toast.error(err instanceof Error
          ? `Household registered, but the photo failed to upload: ${err.message}`
          : 'Household registered, but the photo failed to upload')
      }
    }
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
      <h1 class="page-title">Add Household</h1>
    </div>

    <v-card variant="flat" border>
      <v-card-text>
        <v-row v-if="auth.isAnchor">
          <v-col v-if="auth.isSystemAdmin" cols="12" sm="4">
            <v-select
              v-model="dialogAnchorId" :items="anchors" item-title="name" item-value="id"
              label="Anchor" placeholder="Choose an anchor" required
            />
          </v-col>
          <v-col cols="12" :sm="auth.isSystemAdmin ? 4 : 6">
            <v-select
              v-model="form.organisationCode" :items="dialogOrganizations" item-title="name" item-value="organisationCode"
              label="Organisation" placeholder="Choose an organisation"
              :disabled="auth.isSystemAdmin && !dialogAnchorId" required
            />
          </v-col>
          <v-col cols="12" :sm="auth.isSystemAdmin ? 4 : 6">
            <v-text-field v-model="form.householdName" label="Head of household name" placeholder="e.g. Jane Doe" />
          </v-col>
        </v-row>
        <v-text-field v-else v-model="form.householdName" label="Head of household name" placeholder="e.g. Jane Doe" />
        <v-row>
          <v-col cols="6" sm="4"><v-date-input v-model="form.dateOfBirth" label="Date of birth" placeholder="e.g. 12/03/1990" :max="new Date()" clearable /></v-col>
          <v-col cols="6" sm="4">
            <v-select v-model="form.gender" label="Gender" :items="['M', 'F']" />
          </v-col>
          <v-col cols="12" sm="4"><v-text-field v-model.number="form.householdSize" label="Household size" type="number" /></v-col>
        </v-row>
        <v-row>
          <v-col cols="6" sm="4">
            <v-select v-model="form.maritalStatus" label="Marital status" :items="maritalStatusItems" clearable />
          </v-col>
          <v-col cols="6" sm="4"><v-text-field v-model="form.spouseName" label="Spouse name" /></v-col>
          <v-col cols="12" sm="4"><v-text-field v-model="form.phoneNumber" label="Phone number" /></v-col>
        </v-row>
        <v-row>
          <v-col cols="12" sm="4">
            <v-select
              v-model="form.legalStatus"
              :items="LEGAL_STATUS_OPTIONS"
              item-title="title"
              item-value="value"
              label="Legal status"
              clearable
            />
          </v-col>
          <v-col cols="12" sm="4">
            <v-select
              v-model="form.vulnerabilityStatuses"
              :items="VULNERABILITY_OPTIONS"
              item-title="title"
              item-value="value"
              label="Vulnerability categories"
              hint="Select every support need that applies"
              persistent-hint
              multiple
              chips
              closable-chips
            />
          </v-col>
          <v-col cols="12" sm="4">
            <v-file-input
              label="Household head photo (optional)"
              hint="You can also capture this photo later from the mobile field app"
              persistent-hint
              accept="image/jpeg,image/png"
              prepend-icon="mdi-camera-outline"
              show-size
              density="compact"
              clearable
              @update:model-value="onPhotoFile"
            />
          </v-col>
        </v-row>
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
          <v-btn variant="flat" color="error" @click="goToList">Cancel</v-btn>
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
