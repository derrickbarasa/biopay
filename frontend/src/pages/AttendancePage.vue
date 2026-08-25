<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useAnchorScope } from '@/composables/useAnchorScope'

interface AttendanceRow {
  householdNumber: string
  beneficiaryId: string
  beneficiaryType: number
  clock: string
  time: string
  attendanceDate: string
  workCode?: string
  organisationCode?: string
  latitude?: string
  longitude?: string
}

const auth = useAuthStore()
const toast = useToast()
const { anchors, selectedAnchorId, anchorGateActive, anchorChosen } = useAnchorScope()
const loading = ref(true)
const records = ref<AttendanceRow[]>([])
const tableSearch = ref('')
const organizations = ref<{ organisationCode: string; name: string }[]>([])
const dateFilter = ref<string | null>(null)
const clockFilter = ref<string | null>(null)
const organisationFilter = ref<string | null>(null)

const scopeReady = computed(() => {
  if (auth.isSystemAdmin) return anchorChosen.value
  if (auth.isAnchorAdministrator) return !!organisationFilter.value
  return true
})

const headers = [
  { title: 'Household', key: 'householdNumber' },
  { title: 'Beneficiary', key: 'beneficiaryId' },
  { title: 'Organization', key: 'organisationCode' },
  { title: 'Type', key: 'beneficiaryType' },
  { title: 'Clock', key: 'clock' },
  { title: 'Time', key: 'time' },
  { title: 'Location', key: 'location', sortable: false },
  { title: 'Work Code', key: 'workCode' },
]

const checkInCount = computed(() => records.value.filter((r) => r.clock === 'I').length)
const checkOutCount = computed(() => records.value.filter((r) => r.clock === 'O').length)

// Name-not-code lookup, matching the pattern used on Households/Officers.
const orgNameByCode = computed(() => new Map(organizations.value.map((o) => [o.organisationCode, o.name])))
function orgName(code?: string) { return (code && orgNameByCode.value.get(code)) || code || '—' }

async function load() {
  if (!scopeReady.value) { records.value = []; return }
  loading.value = true
  try {
    const r = await dispatch<{ results: AttendanceRow[] }>('GET_ATTENDANCE', {
      targetAnchorId: auth.isSystemAdmin ? selectedAnchorId.value : undefined,
      attendanceDate: dateFilter.value ?? undefined,
      clock: clockFilter.value ?? undefined,
      organisationCode: organisationFilter.value ?? undefined,
    })
    records.value = r.results
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load attendance')
  } finally {
    loading.value = false
  }
}

watch([dateFilter, clockFilter, organisationFilter], load)
watch(selectedAnchorId, () => { organisationFilter.value = null; loadOrganizations(); load() })

function clearFilters() {
  dateFilter.value = null
  clockFilter.value = null
  organisationFilter.value = null
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
  loadOrganizations()
})

function exportCsv() {
  const rows = [
    ['Household', 'Beneficiary', 'Organization', 'Type', 'Clock', 'Time', 'Date', 'Work Code'],
    ...records.value.map((r) => [
      r.householdNumber, r.beneficiaryId, orgName(r.organisationCode), r.beneficiaryType === 1 ? 'Head' : 'Alternate',
      r.clock === 'I' ? 'In' : 'Out', r.time, r.attendanceDate, r.workCode ?? '',
    ]),
  ]
  const csv = rows.map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob([csv], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `attendance-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div>
    <div class="d-flex align-center justify-space-between mb-4">
      <h1 class="text-h5 font-weight-bold">Attendance</h1>
      <v-btn v-if="scopeReady && auth.can('DOWNLOAD_REPORTS')" color="secondary" prepend-icon="mdi-download" @click="exportCsv">Export CSV</v-btn>
    </div>

    <v-select
      v-if="anchorGateActive" v-model="selectedAnchorId" :items="anchors" item-title="name" item-value="id"
      label="Choose anchor" variant="outlined" class="mb-4" style="max-width: 420px"
      prepend-inner-icon="mdi-bank-outline"
    />
    <v-select
      v-else-if="auth.isAnchorAdministrator" v-model="organisationFilter" :items="organizations" item-title="name" item-value="organisationCode"
      label="Choose organisation" variant="outlined" class="mb-4" style="max-width: 420px"
      prepend-inner-icon="mdi-domain"
    />
    <v-alert v-if="!scopeReady" type="info" variant="tonal" class="mb-4">
      {{ auth.isSystemAdmin && !anchorChosen ? 'Choose an anchor to see attendance records.' : 'Choose an organisation to see attendance records.' }}
    </v-alert>

    <template v-if="scopeReady">
    <v-row class="mb-2">
      <v-col cols="12" sm="4">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">Total Records</div>
          <div class="text-h5 font-weight-bold">{{ records.length }}</div>
        </v-card>
      </v-col>
      <v-col cols="12" sm="4">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">Clocked In</div>
          <div class="text-h5 font-weight-bold">{{ checkInCount }}</div>
        </v-card>
      </v-col>
      <v-col cols="12" sm="4">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">Clocked Out</div>
          <div class="text-h5 font-weight-bold">{{ checkOutCount }}</div>
        </v-card>
      </v-col>
    </v-row>

    <v-card variant="flat" border>
      <v-card-text>
        <v-row dense align="center">
          <v-col v-if="auth.isSystemAdmin" cols="12" sm="4" md="3">
            <v-select v-model="organisationFilter" :items="organizations" item-title="name" item-value="organisationCode" label="Organisation" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="6" sm="4" md="2">
            <v-text-field v-model="dateFilter" type="date" label="Date" hide-details density="compact" clearable />
          </v-col>
          <v-col cols="6" sm="4" md="2">
            <v-select
              v-model="clockFilter" :items="[{ title: 'Clocked In', value: 'I' }, { title: 'Clocked Out', value: 'O' }]"
              label="Clock" clearable hide-details density="compact"
            />
          </v-col>
          <v-col cols="6" sm="4" md="3">
            <v-text-field v-model="tableSearch" prepend-inner-icon="mdi-magnify" label="Search" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="auto">
            <v-btn variant="text" size="small" @click="clearFilters">Clear filters</v-btn>
          </v-col>
        </v-row>
      </v-card-text>
      <v-data-table :headers="headers" :items="records" :search="tableSearch" :loading="loading">
        <template #item.organisationCode="{ item }">{{ orgName(item.organisationCode) }}</template>
        <template #item.beneficiaryType="{ item }">{{ item.beneficiaryType === 1 ? 'Head' : 'Alternate' }}</template>
        <template #item.clock="{ item }">
          <v-chip size="small" :color="item.clock === 'I' ? 'success' : 'warning'" variant="tonal">
            {{ item.clock === 'I' ? 'In' : 'Out' }}
          </v-chip>
        </template>
        <template #item.time="{ item }">{{ item.time ? new Date(item.time).toLocaleString() : '-' }}</template>
        <template #item.location="{ item }">
          <a
            v-if="item.latitude && item.longitude"
            :href="`https://www.google.com/maps?q=${item.latitude},${item.longitude}`"
            target="_blank" rel="noopener" class="text-decoration-none d-inline-flex align-center ga-1"
          >
            <v-icon icon="mdi-map-marker-outline" size="16" />
            <span class="text-caption">Map</span>
          </a>
          <span v-else class="text-caption text-medium-emphasis">—</span>
        </template>
      </v-data-table>
    </v-card>
    </template>
  </div>
</template>
