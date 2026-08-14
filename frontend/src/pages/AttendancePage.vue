<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

interface AttendanceRow {
  householdNumber: string
  beneficiaryId: string
  beneficiaryType: number
  clock: string
  time: string
  attendanceDate: string
  workCode?: string
}

const auth = useAuthStore()
const toast = useToast()
const loading = ref(true)
const records = ref<AttendanceRow[]>([])
const tableSearch = ref('')
const organizations = ref<{ organisationCode: string; name: string }[]>([])
const dateFilter = ref<string | null>(null)
const clockFilter = ref<string | null>(null)
const organisationFilter = ref<string | null>(null)

const headers = [
  { title: 'Household', key: 'householdNumber' },
  { title: 'Beneficiary', key: 'beneficiaryId' },
  { title: 'Type', key: 'beneficiaryType' },
  { title: 'Clock', key: 'clock' },
  { title: 'Time', key: 'time' },
  { title: 'Work Code', key: 'workCode' },
]

const checkInCount = computed(() => records.value.filter((r) => r.clock === 'I').length)
const checkOutCount = computed(() => records.value.filter((r) => r.clock === 'O').length)

async function load() {
  loading.value = true
  try {
    const r = await dispatch<{ results: AttendanceRow[] }>('GET_ATTENDANCE', {
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

function clearFilters() {
  dateFilter.value = null
  clockFilter.value = null
  organisationFilter.value = null
}

onMounted(async () => {
  load()
  if (auth.isAnchor) {
    try {
      const res = await dispatch<{ results: typeof organizations.value }>('GET_ORGANIZATIONS')
      organizations.value = res.results
    } catch {
      // Filter dropdown just stays empty; the list itself still loaded above.
    }
  }
})

function exportCsv() {
  const rows = [
    ['Household', 'Beneficiary', 'Type', 'Clock', 'Time', 'Date', 'Work Code'],
    ...records.value.map((r) => [
      r.householdNumber, r.beneficiaryId, r.beneficiaryType === 1 ? 'Head' : 'Alternate',
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
      <v-btn color="secondary" prepend-icon="mdi-download" @click="exportCsv">Export CSV</v-btn>
    </div>

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
          <v-col v-if="auth.isAnchor" cols="12" sm="4" md="3">
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
        <template #item.beneficiaryType="{ item }">{{ item.beneficiaryType === 1 ? 'Head' : 'Alternate' }}</template>
        <template #item.clock="{ item }">
          <v-chip size="small" :color="item.clock === 'I' ? 'success' : 'warning'" variant="tonal">
            {{ item.clock === 'I' ? 'In' : 'Out' }}
          </v-chip>
        </template>
        <template #item.time="{ item }">{{ item.time ? new Date(item.time).toLocaleString() : '-' }}</template>
      </v-data-table>
    </v-card>
  </div>
</template>
