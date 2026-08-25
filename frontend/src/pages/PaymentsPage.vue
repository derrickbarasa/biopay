<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useAnchorScope } from '@/composables/useAnchorScope'

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
const { confirmAction } = useConfirm()
const { anchors, selectedAnchorId, anchorGateActive, anchorChosen } = useAnchorScope()
const loading = ref(true)
const payments = ref<PaymentRow[]>([])
const tableSearch = ref('')
const organizations = ref<{ organisationCode: string; name: string }[]>([])
const summary = ref<Record<string, number>>({})
const statusFilter = ref<number | null>(null)
const organisationFilter = ref<string | null>(null)
const dateFromFilter = ref<string | null>(null)
const dateToFilter = ref<string | null>(null)

// Both roles see everything in their scope immediately -- the backend already
// treats an unset anchor/organisation filter as "show all" (`IS NULL OR ...`),
// so the picker below narrows the view without ever blocking it.
const scopeReady = computed(() => true)

const headers = [
  { title: 'Household', key: 'householdName' },
  { title: 'Organization', key: 'organisationCode' },
  { title: 'Cycle', key: 'cycle' },
  { title: 'Amount', key: 'amount' },
  { title: 'Status', key: 'status' },
  { title: 'Date', key: 'createdAt' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'end' as const },
]

// Name-not-code lookup, matching the pattern used elsewhere in the dashboard.
const orgNameByCode = computed(() => new Map(organizations.value.map((o) => [o.organisationCode, o.name])))
function orgName(code?: string) { return (code && orgNameByCode.value.get(code)) || code || '—' }

// Small decorative progress rings for the three summary cards -- paid/pending share of the
// total count, and (for the amount card) the paid share of the total amount. Plain stroke-
// dasharray circles rather than the full PieChart component, which carries a legend/hover
// state sized for a standalone chart, not a compact card accent.
const RING_R = 15.9155
const RING_CIRC = 2 * Math.PI * RING_R
function ringDash(fraction: number) {
  const clamped = Math.max(0, Math.min(1, fraction || 0))
  return `${(clamped * RING_CIRC).toFixed(2)} ${RING_CIRC.toFixed(2)}`
}
const totalCount = computed(() => (summary.value.paidCount ?? 0) + (summary.value.pendingCount ?? 0))
const paidShare = computed(() => (totalCount.value ? (summary.value.paidCount ?? 0) / totalCount.value : 0))
const pendingShare = computed(() => (totalCount.value ? (summary.value.pendingCount ?? 0) / totalCount.value : 0))
const paidAmountShare = computed(() => {
  const paidAmount = summary.value.paidAmount
  const total = summary.value.totalAmount
  return total ? (paidAmount ?? 0) / total : paidShare.value
})

async function load() {
  loading.value = true
  try {
    const [p, s] = await Promise.all([
      dispatch<{ results: PaymentRow[] }>('GET_PAYMENTS', {
        pageSize: 100,
        targetAnchorId: auth.isSystemAdmin ? selectedAnchorId.value : undefined,
        status: statusFilter.value ?? undefined,
        organisationCode: organisationFilter.value ?? undefined,
        dateFrom: dateFromFilter.value ?? undefined,
        dateTo: dateToFilter.value ?? undefined,
      }),
      dispatch<{ results: Record<string, number> }>('PAYMENT_SUMMARY', {
        targetAnchorId: auth.isSystemAdmin ? selectedAnchorId.value : undefined,
        organisationCode: organisationFilter.value ?? undefined,
      }),
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

async function loadOrganizations() {
  if (!auth.isAnchorAdministrator && !(auth.isSystemAdmin && selectedAnchorId.value)) { organizations.value = []; return }
  try {
    const res = await dispatch<{ results: typeof organizations.value }>('GET_ORGANIZATIONS', {
      targetAnchorId: auth.isSystemAdmin ? selectedAnchorId.value : undefined,
    })
    organizations.value = res.results
  } catch {
    // Filter dropdown just stays empty; the list itself still loaded above.
  }
}

// System Owner picking a different anchor resets whatever organisation was
// selected under the previous one, then reloads both lists.
watch(selectedAnchorId, () => { organisationFilter.value = null; loadOrganizations(); load() })

onMounted(() => {
  load()
  loadOrganizations()
})

function exportCsv() {
  const rows = [
    ['Household', 'Organization', 'Cycle', 'Amount', 'Status', 'Date'],
    ...payments.value.map((p) => [
      p.householdName, orgName(p.organisationCode), p.cycle ?? '', String(p.amount),
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

async function markPaid(row: PaymentRow) {
  if (!await confirmAction({
    title: 'Mark payment as paid?',
    message: `Confirm that ${row.householdName} received this payment. This changes the programme ledger.`,
    confirmLabel: 'Mark as paid',
    color: 'secondary',
  })) return
  try {
    await dispatch('UPDATE_PAYMENT_STATUS', { id: row.id, status: 1 })
    toast.success('Payment marked as paid')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Update failed')
  }
}

async function remove(row: PaymentRow) {
  if (!await confirmAction({
    title: 'Delete payment?',
    message: `The payment record for ${row.householdName} will be removed. This action cannot be undone.`,
    confirmLabel: 'Delete payment',
    color: 'error',
  })) return
  try {
    await dispatch('DELETE_PAYMENT', { id: row.id })
    toast.success('Payment deleted')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Delete failed')
  }
}
</script>

<template>
  <div>
    <div class="d-flex align-center justify-space-between mb-4">
      <h1 class="text-h5 font-weight-bold">Payments</h1>
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
    <v-alert v-if="auth.isSystemAdmin ? !anchorChosen : (auth.isAnchorAdministrator && !organisationFilter)" type="info" variant="tonal" class="mb-4">
      {{ auth.isSystemAdmin ? 'Showing payments across every anchor. Choose one above to narrow the list.' : 'Showing payments across every organisation. Choose one above to narrow the list.' }}
    </v-alert>

    <template v-if="scopeReady">
    <v-row class="mb-2">
      <v-col cols="12" sm="4">
        <v-card class="pa-4 summary-card" variant="flat" border>
          <div>
            <div class="text-caption text-medium-emphasis">Total Amount</div>
            <div class="text-h5 font-weight-bold">{{ (summary.totalAmount ?? 0).toLocaleString() }}</div>
          </div>
          <svg viewBox="0 0 36 36" class="summary-ring" role="img" aria-label="Paid share of total amount">
            <circle cx="18" cy="18" r="15.9155" fill="none" stroke="#EEF2F6" stroke-width="3.2" />
            <circle
              cx="18" cy="18" r="15.9155" fill="none" stroke="#0D9488" stroke-width="3.2"
              stroke-linecap="round" transform="rotate(-90 18 18)"
              :stroke-dasharray="ringDash(paidAmountShare)"
            />
          </svg>
        </v-card>
      </v-col>
      <v-col cols="12" sm="4">
        <v-card class="pa-4 summary-card" variant="flat" border>
          <div>
            <div class="text-caption text-medium-emphasis">Paid</div>
            <div class="text-h5 font-weight-bold">{{ summary.paidCount ?? 0 }}</div>
          </div>
          <svg viewBox="0 0 36 36" class="summary-ring" role="img" aria-label="Paid share of all payments">
            <circle cx="18" cy="18" r="15.9155" fill="none" stroke="#EEF2F6" stroke-width="3.2" />
            <circle
              cx="18" cy="18" r="15.9155" fill="none" stroke="#10B981" stroke-width="3.2"
              stroke-linecap="round" transform="rotate(-90 18 18)"
              :stroke-dasharray="ringDash(paidShare)"
            />
          </svg>
        </v-card>
      </v-col>
      <v-col cols="12" sm="4">
        <v-card class="pa-4 summary-card" variant="flat" border>
          <div>
            <div class="text-caption text-medium-emphasis">Pending</div>
            <div class="text-h5 font-weight-bold">{{ summary.pendingCount ?? 0 }}</div>
          </div>
          <svg viewBox="0 0 36 36" class="summary-ring" role="img" aria-label="Pending share of all payments">
            <circle cx="18" cy="18" r="15.9155" fill="none" stroke="#EEF2F6" stroke-width="3.2" />
            <circle
              cx="18" cy="18" r="15.9155" fill="none" stroke="#F59E0B" stroke-width="3.2"
              stroke-linecap="round" transform="rotate(-90 18 18)"
              :stroke-dasharray="ringDash(pendingShare)"
            />
          </svg>
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
        <template #item.organisationCode="{ item }">{{ orgName(item.organisationCode) }}</template>
        <template #item.amount="{ item }">{{ (item.amount ?? 0).toLocaleString() }}</template>
        <template #item.status="{ item }">
          <v-chip size="small" :color="item.status === 1 ? 'success' : 'warning'" variant="tonal">
            {{ item.status === 1 ? 'Paid' : 'Pending' }}
          </v-chip>
        </template>
        <template #item.createdAt="{ item }">{{ item.createdAt ? new Date(item.createdAt).toLocaleDateString() : '-' }}</template>
        <template #item.actions="{ item }">
          <v-btn v-if="auth.can('ACCESS_PAYMENTS') && item.status !== 1"
            icon="mdi-check-circle-outline" variant="text" size="small" color="success"
            :aria-label="`Mark payment to ${item.householdName} as paid`" @click="markPaid(item)"
          />
          <v-btn v-if="auth.can('ACCESS_PAYMENTS') && item.status !== 1"
            icon="mdi-delete" variant="text" size="small" color="error"
            :aria-label="`Delete payment to ${item.householdName}`" @click="remove(item)"
          />
        </template>
      </v-data-table>
    </v-card>
    </template>
  </div>
</template>

<style scoped>
.summary-card { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.summary-ring { width: 52px; height: 52px; flex-shrink: 0; }
</style>
