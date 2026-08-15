<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

interface PaymentRow {
  id: number
  householdNumber: string
  householdName: string
  organisationCode: string
  amount: number
  status: number
  cycle?: string
  dateFrom?: string
  createdAt?: string
}

const auth = useAuthStore()
const toast = useToast()
const loading = ref(true)
const payments = ref<PaymentRow[]>([])
const tableSearch = ref('')
const organizations = ref<{ organisationCode: string; name: string }[]>([])
const summary = ref<Record<string, number>>({})
const statusFilter = ref<number | null>(null)
const organisationFilter = ref<string | null>(null)
const dateFromFilter = ref<string | null>(null)
const dateToFilter = ref<string | null>(null)

const headers = [
  { title: 'Household', key: 'householdName' },
  { title: 'Organization', key: 'organisationCode' },
  { title: 'Cycle', key: 'cycle' },
  { title: 'Amount', key: 'amount' },
  { title: 'Status', key: 'status' },
  { title: 'Date', key: 'createdAt' },
]

async function load() {
  loading.value = true
  try {
    const [p, s] = await Promise.all([
      dispatch<{ results: PaymentRow[] }>('GET_PAYMENTS', {
        pageSize: 100,
        status: statusFilter.value ?? undefined,
        organisationCode: organisationFilter.value ?? undefined,
        dateFrom: dateFromFilter.value ?? undefined,
        dateTo: dateToFilter.value ?? undefined,
      }),
      dispatch<{ results: Record<string, number> }>('PAYMENT_SUMMARY'),
    ])
    payments.value = p.results
    summary.value = s.results
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load payments')
  } finally {
    loading.value = false
  }
}

watch([statusFilter, organisationFilter, dateFromFilter, dateToFilter], load)

function clearFilters() {
  statusFilter.value = null
  organisationFilter.value = null
  dateFromFilter.value = null
  dateToFilter.value = null
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
    ['Household', 'Organization', 'Cycle', 'Amount', 'Status', 'Date'],
    ...payments.value.map((p) => [
      p.householdName, p.organisationCode, p.cycle ?? '', String(p.amount),
      p.status === 1 ? 'Paid' : 'Pending', p.createdAt ?? '',
    ]),
  ]
  const csv = rows.map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob([csv], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `payments-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div>
    <div class="d-flex align-center justify-space-between mb-4">
      <h1 class="text-h5 font-weight-bold">Payments</h1>
      <v-btn color="secondary" prepend-icon="mdi-download" @click="exportCsv">Export CSV</v-btn>
    </div>

    <v-row class="mb-2">
      <v-col cols="12" sm="4">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">Total Amount</div>
          <div class="text-h5 font-weight-bold">{{ (summary.totalAmount ?? 0).toLocaleString() }}</div>
        </v-card>
      </v-col>
      <v-col cols="12" sm="4">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">Paid</div>
          <div class="text-h5 font-weight-bold">{{ summary.paidCount ?? 0 }}</div>
        </v-card>
      </v-col>
      <v-col cols="12" sm="4">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">Pending</div>
          <div class="text-h5 font-weight-bold">{{ summary.pendingCount ?? 0 }}</div>
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
            <v-select
              v-model="statusFilter" :items="[{ title: 'Pending', value: 0 }, { title: 'Paid', value: 1 }]"
              label="Status" clearable hide-details density="compact"
            />
          </v-col>
          <v-col cols="6" sm="4" md="2">
            <v-text-field v-model="dateFromFilter" label="From" type="date" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="6" sm="4" md="2">
            <v-text-field v-model="dateToFilter" label="To" type="date" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="6" sm="4" md="3">
            <v-text-field v-model="tableSearch" prepend-inner-icon="mdi-magnify" label="Search" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="auto">
            <v-btn variant="text" size="small" @click="clearFilters">Clear filters</v-btn>
          </v-col>
        </v-row>
      </v-card-text>
      <v-data-table :headers="headers" :items="payments" :search="tableSearch" :loading="loading">
        <template #item.amount="{ item }">{{ (item.amount ?? 0).toLocaleString() }}</template>
        <template #item.status="{ item }">
          <v-chip size="small" :color="item.status === 1 ? 'success' : 'warning'" variant="tonal">
            {{ item.status === 1 ? 'Paid' : 'Pending' }}
          </v-chip>
        </template>
        <template #item.createdAt="{ item }">{{ item.createdAt ? new Date(item.createdAt).toLocaleDateString() : '-' }}</template>
      </v-data-table>
    </v-card>
  </div>
</template>
