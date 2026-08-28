<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useAnchorScope } from '@/composables/useAnchorScope'
import { useOrgCascade } from '@/composables/useOrgCascade'

// Dedicated full page for "Generate Vouchers by Area" (replaces the old
// in-dialog wizard on VouchersPage). Mirrors the PayrollGeneratePage pattern:
// its own route so the picker gets real breathing room and the maker can
// step back to the vouchers list without losing the list's own filters.

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
const { anchors } = useAnchorScope()
const { dialogAnchorId, dialogOrganizations, resetDialogScope } = useOrgCascade()

function goToList() {
  router.push({ name: 'vouchers' })
}

type GenerationLevel = 'STATE' | 'COUNTY' | 'LOCATION' | 'VILLAGE'
const generationLevel = ref<GenerationLevel>('VILLAGE')
const organisationCode = ref<string | null>(null)
const stateCode = ref<string | null>(null)
const countyCode = ref<string | null>(null)
const locationCode = ref<string | null>(null)
const villageSelected = ref<string | null>(null)
const householdsLoading = ref(false)
const flatAmount = ref<number | null>(null)
const purpose = ref('')
const expiresAt = ref('')
const rows = ref<{ householdNumber: string; householdName: string; amount: number }[]>([])
const rowHeaders = [
  { title: 'Household', key: 'householdName' },
  { title: 'Amount', key: 'amount' },
]
const saving = ref(false)

const states = ref<GeoNode[]>([])
const counties = ref<GeoNode[]>([])
const locations = ref<GeoNode[]>([])
const villages = ref<GeoNode[]>([])
let geoLoaded = false

const countiesForState = (code: string | null) => (code ? counties.value.filter((c) => c.stateCode === code) : counties.value)
const locationsForCounty = (code: string | null) => (code ? locations.value.filter((l) => l.countyCode === code) : locations.value)
const villagesForLocation = (code: string | null) => (code ? villages.value.filter((v) => v.locationCode === code) : villages.value)

async function loadGeo() {
  if (geoLoaded && !auth.isSystemAdmin) return
  if (auth.isSystemAdmin && !dialogAnchorId.value) return
  try {
    const scope = auth.isSystemAdmin ? { targetAnchorId: dialogAnchorId.value } : {}
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
    geoLoaded = true
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load locations')
  }
}

const selectedScopeCode = computed(() => {
  if (generationLevel.value === 'STATE') return stateCode.value
  if (generationLevel.value === 'COUNTY') return countyCode.value
  if (generationLevel.value === 'LOCATION') return locationCode.value
  return villageSelected.value
})

const selectedScopeName = computed(() => {
  const code = selectedScopeCode.value
  const source = generationLevel.value === 'STATE' ? states.value
    : generationLevel.value === 'COUNTY' ? counties.value
      : generationLevel.value === 'LOCATION' ? locations.value : villages.value
  return source.find((item) => item.code === code)?.name ?? ''
})

function resetScope() {
  stateCode.value = null
  countyCode.value = null
  locationCode.value = null
  villageSelected.value = null
  rows.value = []
}

// Every active household in the chosen geographic scope, one page at a time (GET_HOUSEHOLDS caps
// pageSize at 200 server-side), stopping at BULK_ISSUE_VOUCHERS' own 500-row limit.
async function loadScopeHouseholds() {
  rows.value = []
  if (!selectedScopeCode.value) return
  householdsLoading.value = true
  try {
    const collected: { householdNumber: string; householdName: string; amount: number }[] = []
    const pageSize = 200
    let page = 1
    while (collected.length < 500) {
      const geographicFilter = generationLevel.value === 'STATE' ? { stateCode: stateCode.value }
        : generationLevel.value === 'COUNTY' ? { countyCode: countyCode.value }
          : generationLevel.value === 'LOCATION' ? { locationCode: locationCode.value }
            : { villageCode: villageSelected.value }
      const res = await dispatch<{ results: { householdNumber: string; householdName: string }[] }>('GET_HOUSEHOLDS', {
        organisationCode: organisationCode.value || undefined, targetAnchorId: auth.isSystemAdmin ? dialogAnchorId.value ?? undefined : undefined, ...geographicFilter, status: 1, page, pageSize,
      })
      for (const h of res.results) {
        if (collected.length >= 500) break
        collected.push({ householdNumber: h.householdNumber, householdName: h.householdName, amount: flatAmount.value ?? 0 })
      }
      if (res.results.length < pageSize) break
      page++
    }
    rows.value = collected
    if (!collected.length) toast.error(`No active households found in this ${generationLevel.value.toLowerCase()}`)
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load households for this area')
  } finally {
    householdsLoading.value = false
  }
}

watch(dialogAnchorId, () => {
  organisationCode.value = null
  resetScope()
  if (auth.isSystemAdmin) {
    geoLoaded = false
    void loadGeo()
  }
})

watch(organisationCode, resetScope)

function onStateChange() {
  countyCode.value = null
  locationCode.value = null
  villageSelected.value = null
  rows.value = []
  if (generationLevel.value === 'STATE') void loadScopeHouseholds()
}
function onCountyChange() {
  locationCode.value = null
  villageSelected.value = null
  rows.value = []
  if (generationLevel.value === 'COUNTY') void loadScopeHouseholds()
}
function onLocationChange() {
  villageSelected.value = null
  rows.value = []
  if (generationLevel.value === 'LOCATION') void loadScopeHouseholds()
}

watch(generationLevel, resetScope)

function applyFlatAmountToAll() {
  const amount = flatAmount.value ?? 0
  for (const row of rows.value) row.amount = amount
}

async function submit() {
  if (auth.isSystemAdmin && !dialogAnchorId.value) {
    toast.error('Select the anchor these vouchers belong to')
    return
  }
  if (auth.isAnchor && !organisationCode.value) {
    toast.error('Select the organisation receiving these vouchers')
    return
  }
  if (!rows.value.length) return
  if (rows.value.some((r) => !r.amount || r.amount <= 0)) {
    toast.error('Every household needs a positive amount')
    return
  }
  saving.value = true
  try {
    const res = await dispatch<{ successCount: number; failureCount: number; errors: { row: number; message: string }[] }>(
      'BULK_ISSUE_VOUCHERS',
      { organisationCode: organisationCode.value || undefined, targetAnchorId: auth.isSystemAdmin ? dialogAnchorId.value ?? undefined : undefined, rows: rows.value, purpose: purpose.value || undefined, expiresAt: expiresAt.value || undefined },
    )
    toast.success(`${res.successCount} voucher(s) issued, ${res.failureCount} failed`)
    if (!(res.errors ?? []).length) goToList()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Geographic voucher generation failed')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  resetDialogScope(null)
  if (!auth.isSystemAdmin) await loadGeo()
})
</script>

<template>
  <div>
    <div class="d-flex align-center mb-4 ga-3">
      <v-btn icon="mdi-arrow-left" variant="text" aria-label="Back to vouchers" @click="goToList" />
      <h1 class="text-h5 font-weight-bold mb-0">Generate Vouchers by Area</h1>
    </div>

    <v-card variant="flat" border>
      <v-card-text>
        <v-alert type="info" variant="tonal" density="compact" class="mb-4">
          Choose a state, county, location or village. Every active household in that area will be prepared for voucher generation.
        </v-alert>

        <v-select
          v-if="auth.isSystemAdmin" v-model="dialogAnchorId" :items="anchors" item-title="name" item-value="id"
          label="Anchor" class="mb-3" placeholder="Choose an anchor" required
        />
        <v-select
          v-if="auth.isAnchor" v-model="organisationCode" :items="dialogOrganizations"
          item-title="name" item-value="organisationCode" label="Organisation" class="mb-3"
          placeholder="Choose an organisation" :disabled="auth.isSystemAdmin && !dialogAnchorId" required
        />
        <v-select
          v-model="generationLevel"
          :items="[{ title: 'State', value: 'STATE' }, { title: 'County', value: 'COUNTY' }, { title: 'Location', value: 'LOCATION' }, { title: 'Village', value: 'VILLAGE' }]"
          label="Generate for" density="compact" class="mb-3" style="max-width: 260px"
        />
        <div class="d-flex ga-3 flex-wrap mb-3">
          <v-select
            v-model="stateCode" :items="states" item-title="name" item-value="code" label="State"
            clearable hide-details density="compact" style="max-width: 200px" :disabled="auth.isAnchor && !organisationCode" @update:model-value="onStateChange"
          />
          <v-select
            v-if="generationLevel !== 'STATE'"
            v-model="countyCode" :items="countiesForState(stateCode)" item-title="name" item-value="code" label="County"
            clearable hide-details density="compact" style="max-width: 200px" @update:model-value="onCountyChange"
          />
          <v-select
            v-if="generationLevel === 'LOCATION' || generationLevel === 'VILLAGE'"
            v-model="locationCode" :items="locationsForCounty(countyCode)" item-title="name" item-value="code" label="Location"
            clearable hide-details density="compact" style="max-width: 200px" @update:model-value="onLocationChange"
          />
          <v-select
            v-if="generationLevel === 'VILLAGE'"
            v-model="villageSelected" :items="villagesForLocation(locationCode)" item-title="name" item-value="code" label="Village"
            clearable hide-details density="compact" style="max-width: 200px" @update:model-value="loadScopeHouseholds"
          />
        </div>

        <v-alert v-if="householdsLoading" type="info" variant="tonal" density="compact" class="mb-3">Loading households…</v-alert>
        <v-alert v-else-if="selectedScopeCode && !rows.length" type="warning" variant="tonal" density="compact" class="mb-3">
          No active households found in this {{ generationLevel.toLowerCase() }}.
        </v-alert>

        <template v-if="rows.length">
          <div class="d-flex align-center ga-3 flex-wrap mb-3">
            <v-text-field
              v-model.number="flatAmount" label="Amount per voucher" type="number" density="compact"
              hide-details style="max-width: 200px"
            />
            <v-btn size="small" color="primary" variant="flat" @click="applyFlatAmountToAll">Apply to all rows</v-btn>
            <v-spacer />
            <span class="text-caption text-medium-emphasis">{{ rows.length }} household(s) in {{ selectedScopeName }}</span>
          </div>
          <v-text-field v-model="purpose" label="Purpose (applies to all, optional)" placeholder="e.g. School fees" density="compact" class="mb-2" />
          <v-text-field v-model="expiresAt" label="Expires on (applies to all, optional)" type="date" density="compact" class="mb-3" />
          <div class="rows-scroll">
            <v-data-table :headers="rowHeaders" :items="rows" density="compact" :items-per-page="10">
              <template #item.amount="{ item }">
                <v-text-field v-model.number="item.amount" type="number" density="compact" hide-details style="max-width: 140px" />
              </template>
            </v-data-table>
          </div>
        </template>

        <div class="d-flex justify-end mt-4">
          <v-btn color="secondary" :loading="saving" :disabled="!rows.length" prepend-icon="mdi-map-marker-radius-outline" @click="submit">
            Generate Vouchers
          </v-btn>
        </div>
      </v-card-text>
    </v-card>
  </div>
</template>

<style scoped>
.rows-scroll { max-height: 360px; overflow-y: auto; }
</style>
