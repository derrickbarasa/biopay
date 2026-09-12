<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useAnchorScope } from '@/composables/useAnchorScope'
import { useOrgCascade } from '@/composables/useOrgCascade'
import { downloadCsv, parseCsv, toCsv } from '@/utils/csv'
import { householdGenderBreakdown } from '@/utils/gender'
import { HOUSEHOLD_REVIEW_STATUSES, householdReviewStatus } from '@/utils/householdReview'
import DistributionList from '@/components/DistributionList.vue'
import HouseholdReviewActions from '@/components/HouseholdReviewActions.vue'
import PieChart from '@/components/PieChart.vue'
import StackedDistribution from '@/components/StackedDistribution.vue'
import VerticalBarChart from '@/components/VerticalBarChart.vue'
import {
  LEGAL_STATUS_FILTER_OPTIONS,
  VULNERABILITY_FILTER_OPTIONS,
  legalStatusLabel,
  vulnerabilityLabel,
} from '@/constants/householdClassifications'

interface HouseholdRow {
  householdNumber: string
  householdName: string
  organisationCode: string
  age?: number
  gender?: string
  phoneNumber?: string
  householdSize?: number
  bomaCode?: string
  vulnerabilityStatus?: string
  vulnerabilityStatuses?: string[]
  legalStatus?: string
  reviewStatus?: string
  rejectionReason?: string
  voucherCount?: number
  paymentCycleCount?: number
  status: number
}

const REVIEW_STATUSES: string[] = [...HOUSEHOLD_REVIEW_STATUSES]
const reviewStatusColor: Record<string, string> = { PENDING: 'default', APPROVED: 'success', REJECTED: 'error' }

interface GeoNode {
  code: string
  name: string
  stateCode?: string
  countyCode?: string
  locationCode?: string
}

const auth = useAuthStore()
const toast = useToast()
const { confirmAction } = useConfirm()
const router = useRouter()
const { anchors, selectedAnchorId, anchorGateActive } = useAnchorScope()
const { dialogAnchorId: bulkDialogAnchorId, dialogOrganizations: bulkDialogOrganizations, resetDialogScope: resetBulkDialogScope } = useOrgCascade()
const loading = ref(true)
const households = ref<HouseholdRow[]>([])

// The backend treats an unset anchor/organisation filter as "show all", so the
// picker below narrows the view without ever blocking it.
const scopeReady = computed(() => true)
const saving = ref(false)

const states = ref<GeoNode[]>([])
const counties = ref<GeoNode[]>([])
const locations = ref<GeoNode[]>([])
const villages = ref<GeoNode[]>([])
const organizations = ref<{ organisationCode: string; name: string }[]>([])

// Each filter is its own backend query parameter; no client-side search.
const filters = ref({
  organisationCode: null as string | null,
  stateCode: null as string | null,
  countyCode: null as string | null,
  locationCode: null as string | null,
  villageCode: null as string | null,
  gender: null as string | null,
  status: null as number | null,
  vulnerabilityStatus: null as string | null,
  legalStatus: null as string | null,
  reviewStatus: null as string | null,
  dateFrom: null as string | null,
  dateTo: null as string | null,
  search: '',
})
const headers = [
  { title: 'Household #', key: 'householdNumber' },
  { title: 'Head of Household', key: 'householdName' },
  { title: 'Organization', key: 'organisationCode' },
  { title: 'Village', key: 'bomaCode' },
  { title: 'Size', key: 'householdSize' },
  { title: 'Vouchers', key: 'voucherCount' },
  { title: 'Cycles', key: 'paymentCycleCount' },
  { title: 'Status', key: 'status' },
  { title: 'Review', key: 'reviewStatus' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'start' as const },
]

// The table shows organisation/village names, not their internal codes.
const orgNameByCode = computed(() => new Map(organizations.value.map((o) => [o.organisationCode, o.name])))
const villageNameByCode = computed(() => new Map(villages.value.map((v) => [v.code, v.name])))
function orgName(code?: string) { return (code && orgNameByCode.value.get(code)) || code || '—' }
function villageName(code?: string) { return (code && villageNameByCode.value.get(code)) || code || '—' }

// Breakdown graphs computed client-side over the currently loaded (filtered) rows.
const genderBreakdown = computed(() => householdGenderBreakdown(households.value))

const ageBreakdown = computed(() => {
  const buckets = [
    { label: '0-17', value: 0 }, { label: '18-34', value: 0 }, { label: '35-49', value: 0 },
    { label: '50-64', value: 0 }, { label: '65+', value: 0 }, { label: 'Not recorded', value: 0 },
  ]
  for (const h of households.value) {
    const a = h.age
    if (a == null) buckets[5].value++
    else if (a < 18) buckets[0].value++
    else if (a < 35) buckets[1].value++
    else if (a < 50) buckets[2].value++
    else if (a < 65) buckets[3].value++
    else buckets[4].value++
  }
  return buckets.filter((bucket) => bucket.label !== 'Not recorded' || bucket.value > 0)
})

// "By status" reflects the review workflow. Legacy CHECKED records stay in the
// pending count until a reviewer makes the final approve/reject decision.
const statusBreakdown = computed(() => REVIEW_STATUSES.map((s) => ({
  label: s.charAt(0) + s.slice(1).toLowerCase(),
  value: households.value.filter((h) => householdReviewStatus(h.reviewStatus) === s).length,
  color: s === 'APPROVED' ? '#16A34A' : s === 'REJECTED' ? '#DC2626' : '#F59E0B',
})))

// Groups rows by a free-text attribute, counting blanks as "Unspecified".
function groupByAttribute(pick: (h: HouseholdRow) => string | undefined, label: (value?: string) => string = (value) => value || 'Not recorded') {
  const counts = new Map<string, number>()
  for (const h of households.value) {
    const key = (pick(h) || 'Unspecified').trim() || 'Unspecified'
    const display = label(key === 'Unspecified' ? undefined : key)
    counts.set(display, (counts.get(display) ?? 0) + 1)
  }
  return Array.from(counts, ([label, value]) => ({ label, value }))
}

const vulnerabilityBreakdown = computed(() => {
  const counts = new Map<string, number>()
  for (const household of households.value) {
    const categories = household.vulnerabilityStatuses?.length
      ? household.vulnerabilityStatuses
      : household.vulnerabilityStatus ? [household.vulnerabilityStatus] : []
    if (!categories.length) {
      counts.set('Not recorded', (counts.get('Not recorded') ?? 0) + 1)
      continue
    }
    for (const category of categories) {
      const label = vulnerabilityLabel(category)
      counts.set(label, (counts.get(label) ?? 0) + 1)
    }
  }
  return Array.from(counts, ([label, value]) => ({ label, value }))
})
const legalBreakdown = computed(() => groupByAttribute((h) => h.legalStatus, legalStatusLabel))

const countiesForState = (stateCode: string) => stateCode ? counties.value.filter((c) => c.stateCode === stateCode) : counties.value
const locationsForCounty = (countyCode: string) => countyCode ? locations.value.filter((l) => l.countyCode === countyCode) : locations.value
const villagesForLocation = (locationCode: string) => locationCode ? villages.value.filter((v) => v.locationCode === locationCode) : villages.value

// Sourced from GET_HOUSEHOLD_LOCATIONS, not GET_STATES/COUNTIES/LOCATIONS/VILLAGES: a household's
// state/county/location/village columns aren't tied to the anchor's geo catalogue (mobile's
// manual-entry path stores free-typed place names with no catalogue node), so filtering and the
// "Village" name lookup against the catalogue would silently miss those households.
async function loadGeo() {
  const targetAnchorId = auth.isSystemAdmin ? selectedAnchorId.value : undefined
  try {
    const requests: Promise<any>[] = [
      dispatch<{ results: GeoNode[] }>('GET_HOUSEHOLD_LOCATIONS', { level: 'STATE', targetAnchorId }),
      dispatch<{ results: GeoNode[] }>('GET_HOUSEHOLD_LOCATIONS', { level: 'COUNTY', targetAnchorId }),
      dispatch<{ results: GeoNode[] }>('GET_HOUSEHOLD_LOCATIONS', { level: 'LOCATION', targetAnchorId }),
      dispatch<{ results: GeoNode[] }>('GET_HOUSEHOLD_LOCATIONS', { level: 'VILLAGE', targetAnchorId }),
    ]
    requests.push(dispatch<{ results: typeof organizations.value }>('GET_ORGANIZATIONS', { targetAnchorId }))
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
      targetAnchorId: auth.isSystemAdmin ? selectedAnchorId.value : undefined,
      organisationCode: filters.value.organisationCode ?? undefined,
      stateCode: filters.value.stateCode ?? undefined,
      countyCode: filters.value.countyCode ?? undefined,
      locationCode: filters.value.locationCode ?? undefined,
      villageCode: filters.value.villageCode ?? undefined,
      gender: filters.value.gender ?? undefined,
      status: filters.value.status ?? undefined,
      vulnerabilityStatus: filters.value.vulnerabilityStatus || undefined,
      legalStatus: filters.value.legalStatus || undefined,
      reviewStatus: filters.value.reviewStatus ?? undefined,
      dateFrom: filters.value.dateFrom || undefined,
      dateTo: filters.value.dateTo || undefined,
      search: filters.value.search || undefined,
    })
    households.value = res.results
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load households')
  } finally {
    loading.value = false
  }
}

// Filters only take effect when the user presses Submit (see applyFilters/load in the
// template) -- picking a value no longer re-queries immediately. Cascading geo filters
// still clear their narrower selections right away so the dependent dropdowns' option
// lists stay correct, but that alone never triggers a reload.
watch(() => filters.value.stateCode, () => { filters.value.countyCode = null; filters.value.locationCode = null; filters.value.villageCode = null })
watch(() => filters.value.countyCode, () => { filters.value.locationCode = null; filters.value.villageCode = null })
watch(() => filters.value.locationCode, () => { filters.value.villageCode = null })

// A Super Admin switching anchors resets the organisation filter and refreshes the geo/org
// option lists for the newly selected anchor; the household table itself still waits for Submit.
watch(selectedAnchorId, () => { filters.value.organisationCode = null; loadGeo() })

function clearFilters() {
  filters.value = { organisationCode: null, stateCode: null, countyCode: null, locationCode: null, villageCode: null, gender: null, status: null, vulnerabilityStatus: null, legalStatus: null, reviewStatus: null, dateFrom: null, dateTo: null, search: '' }
  load()
}

function exportCsv() {
  if (!households.value.length) {
    toast.error('No households to export')
    return
  }
  const csv = toCsv(
    ['Household #', 'Head of Household', 'Organization', 'Age', 'Gender', 'Phone', 'Size', 'Village', 'Vulnerability categories', 'Legal status', 'Vouchers', 'Payment Cycles', 'Status', 'Review Status'],
    households.value.map((h) => [
      h.householdNumber, h.householdName, orgName(h.organisationCode), h.age ?? '', h.gender ?? '',
      h.phoneNumber ?? '', h.householdSize ?? '', villageName(h.bomaCode),
      (h.vulnerabilityStatuses ?? []).map(vulnerabilityLabel).join(' | '), legalStatusLabel(h.legalStatus),
      h.voucherCount ?? 0, h.paymentCycleCount ?? 0,
      h.status === 1 ? 'Active' : 'Inactive', h.reviewStatus ?? 'PENDING',
    ]),
  )
  downloadCsv(`households-${new Date().toISOString().slice(0, 10)}.csv`, csv)
}

onMounted(() => { load(); loadGeo() })

function viewDetail(row: HouseholdRow) {
  router.push({ name: 'household-detail', params: { householdNumber: row.householdNumber } })
}

async function remove(row: HouseholdRow) {
  if (!await confirmAction({
    title: 'Delete household?',
    message: `${row.householdName} (${row.householdNumber}) will be removed from programme records. This action cannot be undone.`,
    confirmLabel: 'Delete household',
    color: 'error',
  })) return
  try {
    await dispatch('DELETE_HOUSEHOLD', { householdNumber: row.householdNumber })
    toast.success('Household deleted')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Delete failed')
  }
}

// Import CSV: one template upload is scoped to one village's batch.
const bulkDialog = ref(false)
const bulkOrganisationCode = ref<string | null>(null)
const bulkStateCode = ref('')
const bulkCountyCode = ref('')
const bulkLocationCode = ref('')
const bulkVillageCode = ref('')
const bulkFileName = ref('')
const bulkRows = ref<Record<string, string>[]>([])
const bulkResult = ref<{ successCount: number; failureCount: number; errors: { row: number; message: string }[] } | null>(null)

const OPTIONAL_TEMPLATE_FIELDS: { key: string; label: string; sample: string }[] = [
  { key: 'age', label: 'Age', sample: '34' },
  { key: 'gender', label: 'Gender', sample: 'F' },
  { key: 'maritalStatus', label: 'Marital status', sample: 'Married' },
  { key: 'spouseName', label: 'Spouse name', sample: 'John Doe' },
  { key: 'idNumber', label: 'ID number', sample: 'ID123456' },
  { key: 'phoneNumber', label: 'Phone number', sample: '+211900000000' },
  { key: 'householdSize', label: 'Household size', sample: '5' },
  { key: 'femaleDependants', label: 'Female dependants', sample: '2' },
  { key: 'maleDependants', label: 'Male dependants', sample: '1' },
  { key: 'vulnerabilityStatuses', label: 'Vulnerability categories', sample: 'DISABILITY|ELDERLY_HEADED' },
  { key: 'legalStatus', label: 'Legal status', sample: 'CITIZEN' },
]
const templateFields = ref<string[]>(OPTIONAL_TEMPLATE_FIELDS.map((f) => f.key))
const templateDownloaded = ref(false)

function openBulk() {
  bulkOrganisationCode.value = null
  bulkStateCode.value = ''; bulkCountyCode.value = ''; bulkLocationCode.value = ''; bulkVillageCode.value = ''
  bulkFileName.value = ''; bulkRows.value = []; bulkResult.value = null
  templateFields.value = OPTIONAL_TEMPLATE_FIELDS.map((f) => f.key)
  templateDownloaded.value = false
  resetBulkDialogScope(auth.isSystemAdmin ? selectedAnchorId.value : null)
  bulkDialog.value = true
}
watch(bulkDialogAnchorId, () => { bulkOrganisationCode.value = null })

function downloadTemplate() {
  const headers = ['householdName', ...OPTIONAL_TEMPLATE_FIELDS.filter((f) => templateFields.value.includes(f.key)).map((f) => f.key)]
  const sampleRow = ['Jane Doe', ...OPTIONAL_TEMPLATE_FIELDS.filter((f) => templateFields.value.includes(f.key)).map((f) => f.sample)]
  downloadCsv('household-upload-template.csv', toCsv(headers, [sampleRow]))
  templateDownloaded.value = true
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

const bulkReady = computed(() =>
  !!bulkVillageCode.value && bulkRows.value.length > 0 && (!auth.isAnchor || !!bulkOrganisationCode.value)
  && (!auth.isSystemAdmin || !!bulkDialogAnchorId.value))

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
      vulnerabilityStatuses: r.vulnerabilityStatuses || undefined,
      legalStatus: r.legalStatus || undefined,
    }))
    const res = await dispatch<{ successCount: number; failureCount: number; errors: { row: number; message: string }[] }>(
      'BULK_UPLOAD_HOUSEHOLDS',
      { organisationCode: bulkOrganisationCode.value || undefined, villageCode: bulkVillageCode.value, fileName: bulkFileName.value, rows },
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
    <div class="households-page-header mb-4">
      <h1 class="page-title">Households</h1>
      <div v-if="scopeReady" class="households-page-actions">
        <v-btn v-if="auth.can('DOWNLOAD_REPORTS')" variant="outlined" prepend-icon="mdi-download" @click="exportCsv">Export CSV</v-btn>
        <v-btn v-if="auth.can('ACCESS_HOUSEHOLDS')" variant="outlined" prepend-icon="mdi-file-upload" @click="openBulk">Import CSV</v-btn>
        <v-btn v-if="auth.can('ACCESS_HOUSEHOLDS')" color="secondary" prepend-icon="mdi-home-plus" @click="router.push({ name: 'household-create' })">Add Household</v-btn>
      </div>
    </div>

    <template v-if="scopeReady">
    <h2 class="section-heading mb-2">Household breakdown</h2>
    <div class="breakdown-grid mb-4">
      <div class="breakdown-stack">
      <v-card class="breakdown-card breakdown-card--gender" variant="flat" border>
        <v-card-title class="breakdown-title">
          <span class="breakdown-icon breakdown-icon--teal"><v-icon icon="mdi-account-group-outline" size="18" /></span>
          <span><strong>By gender</strong><small>Household heads</small></span>
        </v-card-title>
        <v-card-text class="breakdown-body">
          <PieChart :data="genderBreakdown" variant="pie" :size="104" show-labels :show-legend-percent="false" />
        </v-card-text>
      </v-card>

      <v-card class="breakdown-card breakdown-card--status" variant="flat" border>
        <v-card-title class="breakdown-title">
          <span class="breakdown-icon breakdown-icon--teal"><v-icon icon="mdi-check-circle-outline" size="18" /></span>
          <span><strong>By status</strong><small>Household approval status</small></span>
        </v-card-title>
        <v-card-text class="breakdown-body breakdown-body--compact-pie">
          <PieChart :data="statusBreakdown" :colors="['#F59E0B', '#16A34A', '#DC2626']" :size="108" />
        </v-card-text>
      </v-card>
      </div>

      <div class="breakdown-stack">
      <v-card class="breakdown-card breakdown-card--vulnerability" variant="flat" border>
        <v-card-title class="breakdown-title">
          <span class="breakdown-icon breakdown-icon--green"><v-icon icon="mdi-shield-account-outline" size="18" /></span>
          <span><strong>By vulnerability status</strong><small>Households may appear in more than one category</small></span>
          <v-icon class="breakdown-info" icon="mdi-information-outline" size="16" title="Percentages use the filtered household total." />
        </v-card-title>
        <v-card-text class="breakdown-body">
          <DistributionList :data="vulnerabilityBreakdown" :total-value="households.length" :show-percent="false" color="#0D9F78" aria-label="Households by vulnerability status" />
        </v-card-text>
      </v-card>

      </div>

      <div class="breakdown-stack breakdown-stack--wide">
      <v-card class="breakdown-card breakdown-card--age" variant="flat" border>
        <v-card-title class="breakdown-title">
          <span class="breakdown-icon breakdown-icon--blue"><v-icon icon="mdi-chart-bar" size="18" /></span>
          <span><strong>By age group</strong><small>Household heads</small></span>
        </v-card-title>
        <v-card-text class="breakdown-body age-breakdown-body">
          <VerticalBarChart
            :data="ageBreakdown"
            aria-label="Household heads by age group, showing household counts"
          />
        </v-card-text>
      </v-card>

      <v-card class="breakdown-card breakdown-card--legal" variant="flat" border>
        <v-card-title class="breakdown-title">
          <span class="breakdown-icon breakdown-icon--teal"><v-icon icon="mdi-file-document-outline" size="18" /></span>
          <span><strong>By legal status</strong><small>Legal classification</small></span>
        </v-card-title>
        <v-card-text class="breakdown-body">
          <StackedDistribution :data="legalBreakdown" aria-label="Households by legal status" />
        </v-card-text>
      </v-card>
      </div>
    </div>

    <v-card variant="flat" border>
      <v-card-text>
        <v-row dense align="center">
          <v-col v-if="anchorGateActive" cols="12" sm="6" md="3">
            <v-select v-model="selectedAnchorId" :items="anchors" item-title="name" item-value="id" label="Anchor" clearable hide-details density="compact" prepend-inner-icon="mdi-bank-outline" />
          </v-col>
          <v-col v-if="auth.isSystemAdmin || auth.isAnchorAdministrator" cols="12" sm="6" md="3">
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
          <v-col cols="6" sm="3" md="2">
            <v-select v-model="filters.reviewStatus" :items="REVIEW_STATUSES" label="Review status" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="6" sm="3" md="2">
            <v-select
              v-model="filters.vulnerabilityStatus" :items="VULNERABILITY_FILTER_OPTIONS"
              item-title="title" item-value="value" label="Vulnerability category"
              clearable hide-details density="compact"
            />
          </v-col>
          <v-col cols="6" sm="3" md="2">
            <v-select
              v-model="filters.legalStatus" :items="LEGAL_STATUS_FILTER_OPTIONS"
              item-title="title" item-value="value" label="Legal status"
              clearable hide-details density="compact"
            />
          </v-col>
          <v-col cols="6" sm="3" md="2">
            <v-text-field v-model="filters.dateFrom" label="Registered from" type="date" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="6" sm="3" md="2">
            <v-text-field v-model="filters.dateTo" label="Registered to" type="date" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="12" sm="6" md="3">
            <v-text-field
              v-model="filters.search" prepend-inner-icon="mdi-magnify" label="Household name, number or ID"
              clearable hide-details density="compact" @keyup.enter="load"
            />
          </v-col>
          <v-col cols="auto" class="filter-actions">
            <v-btn class="filter-submit" color="secondary" @click="load">Submit</v-btn>
            <v-btn variant="text" size="small" @click="clearFilters">Clear filters</v-btn>
          </v-col>
        </v-row>
      </v-card-text>
      <v-data-table :headers="headers" :items="households" :loading="loading">
        <template #item.voucherCount="{ item }">
          <v-chip size="small" :color="item.voucherCount ? 'primary' : undefined" variant="tonal">
            {{ item.voucherCount ?? 0 }}
          </v-chip>
        </template>
        <template #item.paymentCycleCount="{ item }">
          <v-chip size="small" :color="item.paymentCycleCount ? 'secondary' : undefined" variant="tonal">
            {{ item.paymentCycleCount ?? 0 }}
          </v-chip>
        </template>
        <template #item.status="{ item }">
          <v-chip size="small" :color="item.status === 1 ? 'success' : 'error'" variant="tonal">
            {{ item.status === 1 ? 'Active' : 'Inactive' }}
          </v-chip>
        </template>
        <template #item.organisationCode="{ item }">{{ orgName(item.organisationCode) }}</template>
        <template #item.bomaCode="{ item }">{{ villageName(item.bomaCode) }}</template>
        <template #item.reviewStatus="{ item }">
          <v-tooltip v-if="householdReviewStatus(item.reviewStatus) === 'REJECTED' && item.rejectionReason" :text="item.rejectionReason" location="top">
            <template #activator="{ props: tip }">
              <v-chip v-bind="tip" size="small" :color="reviewStatusColor[householdReviewStatus(item.reviewStatus)]" variant="tonal">
                {{ householdReviewStatus(item.reviewStatus) }}
              </v-chip>
            </template>
          </v-tooltip>
          <v-chip v-else size="small" :color="reviewStatusColor[householdReviewStatus(item.reviewStatus)]" variant="tonal">
            {{ householdReviewStatus(item.reviewStatus) }}
          </v-chip>
        </template>
        <template #item.actions="{ item }">
          <div class="d-flex align-center ga-1">
            <v-btn icon="mdi-eye" variant="text" size="small" :aria-label="`View household ${item.householdName}`" @click="viewDetail(item)" />
            <HouseholdReviewActions
              v-if="auth.can('ACCESS_HOUSEHOLDS')"
              :household-number="item.householdNumber"
              :household-name="item.householdName"
              :review-status="item.reviewStatus"
              compact
              @updated="load"
            />
            <v-btn v-if="auth.can('ACCESS_HOUSEHOLDS')" icon="mdi-delete" variant="text" size="small" color="error" :aria-label="`Delete household ${item.householdName}`" @click="remove(item)" />
          </div>
        </template>
      </v-data-table>
    </v-card>
    </template>

    <v-dialog v-model="bulkDialog" max-width="640">
      <v-card>
        <dialog-close-button @close="bulkDialog = false" />
        <v-card-title>Import Households from CSV</v-card-title>
        <v-card-text>
          <v-alert type="info" variant="tonal" density="compact" class="mb-3">
            Pick the village this batch belongs to, choose which fields to include, download the template, fill in one row per household, then upload it here.
          </v-alert>
          <v-select
            v-if="auth.isSystemAdmin"
            v-model="bulkDialogAnchorId" :items="anchors" item-title="name" item-value="id"
            label="Anchor" density="compact" class="mb-2" placeholder="Choose an anchor" required
          />
          <v-select
            v-if="auth.isAnchor"
            v-model="bulkOrganisationCode" :items="bulkDialogOrganizations" item-title="name" item-value="organisationCode"
            label="Organisation" density="compact" class="mb-2" placeholder="Choose an organisation"
            :disabled="auth.isSystemAdmin && !bulkDialogAnchorId" required
          />
          <v-row dense>
            <v-col cols="4"><v-select v-model="bulkStateCode" :items="states" item-title="name" item-value="code" label="State" density="compact" /></v-col>
            <v-col cols="4"><v-select v-model="bulkCountyCode" :items="countiesForState(bulkStateCode)" item-title="name" item-value="code" label="County" density="compact" /></v-col>
            <v-col cols="4"><v-select v-model="bulkLocationCode" :items="locationsForCounty(bulkCountyCode)" item-title="name" item-value="code" label="Location" density="compact" /></v-col>
          </v-row>
          <v-select v-model="bulkVillageCode" :items="villagesForLocation(bulkLocationCode)" item-title="name" item-value="code" label="Village" density="compact" />

          <div class="text-caption text-medium-emphasis mt-3 mb-1">Fields to include (household name is always included)</div>
          <v-row dense>
            <v-col v-for="f in OPTIONAL_TEMPLATE_FIELDS" :key="f.key" cols="6" sm="4">
              <v-checkbox v-model="templateFields" :value="f.key" :label="f.label" density="compact" hide-details />
            </v-col>
          </v-row>

          <v-btn variant="outlined" size="small" prepend-icon="mdi-download" class="my-3" @click="downloadTemplate">
            Download Template
          </v-btn>
          <v-file-input
            label="Upload filled CSV" accept=".csv" prepend-icon="mdi-file-upload"
            :disabled="!bulkVillageCode || !templateDownloaded || (auth.isAnchor && !bulkOrganisationCode)" @change="onBulkFile"
          />
          <div v-if="!templateDownloaded" class="text-caption text-medium-emphasis mb-2">Download the template above before uploading a filled CSV.</div>
          <div v-if="bulkRows.length" class="text-caption mb-2">{{ bulkRows.length }} row(s) ready to upload from {{ bulkFileName }}</div>

          <v-alert v-if="bulkResult" :type="bulkResult.failureCount ? 'warning' : 'success'" variant="tonal" density="compact" class="mt-2">
            {{ bulkResult.successCount }} registered, {{ bulkResult.failureCount }} failed
            <div v-for="e in bulkResult.errors.slice(0, 5)" :key="e.row">Row {{ e.row }}: {{ e.message }}</div>
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="bulkDialog = false">Close</v-btn>
          <v-btn variant="flat" color="secondary" :loading="saving" :disabled="!bulkReady" @click="submitBulk">Upload</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

  </div>
</template>

<style scoped>
.section-heading { font-size: .95rem; font-weight: 700; color: #0f172a; }
.filter-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.households-page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.households-page-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
.breakdown-grid {
  display: grid;
  grid-template-columns: minmax(250px, .85fr) minmax(300px, 1fr) minmax(360px, 1.25fr);
  gap: 12px;
  align-items: start;
}
.breakdown-stack {
  display: grid;
  gap: 12px;
  min-width: 0;
  align-content: start;
}
.breakdown-card {
  overflow: hidden;
  border-color: #dbe5ec !important;
  background: #fff;
  min-width: 0;
  box-shadow: none !important;
}
.breakdown-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 11px 5px !important;
  min-height: auto !important;
  white-space: normal;
}
.breakdown-title > span:nth-child(2) { display: grid; gap: 1px; min-width: 0; }
.breakdown-title strong { color: #0f172a; font-size: .86rem; font-weight: 750; line-height: 1.2; }
.breakdown-title small { color: #64748b; font-size: .7rem; font-weight: 500; line-height: 1.3; overflow-wrap: anywhere; }
.breakdown-icon {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  border-radius: 8px;
}
.breakdown-icon--teal { color: #0f766e; background: #e8f6f4; }
.breakdown-icon--amber { color: #b45309; background: #fff4dc; }
.breakdown-icon--blue { color: #0369a1; background: #eaf5fb; }
.breakdown-icon--green { color: #15803d; background: #eaf8ef; }
.breakdown-info { margin-left: auto; color: #78909c; flex: 0 0 auto; }
.breakdown-body {
  display: flex;
  align-items: center;
  padding: 4px 11px 7px !important;
  min-height: 0;
}
.breakdown-body > :deep(.pie-wrap) {
  width: 100%;
  grid-template-columns: 104px minmax(0, 1fr);
  gap: 12px;
}
.breakdown-body > :deep(.pie-wrap .pie-svg-wrap) {
  width: 104px;
  height: 104px;
}
.age-breakdown-body { justify-content: center; }
.age-breakdown-body > :deep(.vertical-chart__column) { grid-template-rows: 116px auto; }
.breakdown-body--compact-pie > :deep(.pie-wrap) {
  grid-template-columns: 108px minmax(0, 1fr);
  max-width: 460px;
  margin-inline: auto;
}
.breakdown-body--compact-pie > :deep(.pie-wrap .pie-svg-wrap) {
  width: 108px;
  height: 108px;
}
.breakdown-stack--wide { grid-column: auto; }
@media (max-width: 1180px) {
  .breakdown-grid {
    grid-template-columns: minmax(260px, .9fr) minmax(360px, 1.25fr);
  }
  .breakdown-stack--wide { grid-column: 1 / -1; grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 900px) {
  .breakdown-grid {
    grid-template-columns: 1fr;
  }
  .breakdown-stack--wide { grid-column: auto; grid-template-columns: 1fr; }
}
@media (max-width: 600px) {
  .households-page-header { align-items: flex-start; }
  .households-page-header .page-title { width: 100%; }
  .households-page-actions { width: 100%; }
  .households-page-actions > :deep(.v-btn) { flex: 1 1 160px; }
  .breakdown-grid { grid-template-columns: 1fr; }
  .breakdown-card--vulnerability .breakdown-body > :deep(.distribution) { grid-template-columns: 1fr; }
  .breakdown-body { padding-inline: 11px !important; }
  .breakdown-body--compact-pie > :deep(.pie-wrap) { grid-template-columns: 108px minmax(0, 1fr); }
  .breakdown-body--compact-pie > :deep(.pie-wrap .pie-svg-wrap) { width: 108px; height: 108px; }
}
</style>
