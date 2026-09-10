<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useAnchorScope } from '@/composables/useAnchorScope'
import SummaryMetricCard from '@/components/SummaryMetricCard.vue'
import { formatCurrency } from '@/utils/currency'

interface PaymentRow {
  id: number
  householdNumber: string
  householdName: string
  organisationCode: string
  amount: number
  currency?: string
  exchangeRate?: number
  status: number
  approved?: number
  cycle?: string
  dateFrom?: string
  dateTo?: string
  createdAt?: string
  verifiedAt?: string
  approvedAt?: string
  latitude?: string | null
  longitude?: string | null
  paymentChannel?: string | null
  onlineReference?: string | null
}

const auth = useAuthStore()
const toast = useToast()
const { confirmAction } = useConfirm()
const { anchors, selectedAnchorId, anchorGateActive } = useAnchorScope()
const loading = ref(true)
const payments = ref<PaymentRow[]>([])
const tableSearch = ref('')
const organizations = ref<{ organisationCode: string; name: string }[]>([])
const summary = ref<Record<string, number>>({})
const statusFilter = ref<number | null>(null)
const organisationFilter = ref<string | null>(null)
const dateFromFilter = ref<string | null>(null)
const dateToFilter = ref<string | null>(null)
const detailDialog = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailPayment = ref<PaymentRow | null>(null)

// An unset anchor/organisation filter already means "show all" on the backend,
// so the picker below narrows the view without ever blocking it.
const scopeReady = computed(() => true)

const headers = [
  { title: 'Household', key: 'householdName' },
  { title: 'Organization', key: 'organisationCode' },
  { title: 'Cycle', key: 'cycle' },
  { title: 'Amount', key: 'amount' },
  { title: 'Status', key: 'status' },
  { title: 'Date', key: 'createdAt' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'start' as const },
]

const orgNameByCode = computed(() => new Map(organizations.value.map((o) => [o.organisationCode, o.name])))
function orgName(code?: string) { return (code && orgNameByCode.value.get(code)) || code || '—' }

const totalCount = computed(() => (summary.value.paidCount ?? 0) + (summary.value.pendingCount ?? 0) + (summary.value.failedCount ?? 0))
const paidShare = computed(() => (totalCount.value ? (summary.value.paidCount ?? 0) / totalCount.value : 0))
const pendingShare = computed(() => (totalCount.value ? (summary.value.pendingCount ?? 0) / totalCount.value : 0))
const failedShare = computed(() => (totalCount.value ? (summary.value.failedCount ?? 0) / totalCount.value : 0))
const paidAmountShare = computed(() => {
  const paidAmount = summary.value.paidAmount
  const total = summary.value.totalAmount
  return total ? (paidAmount ?? 0) / total : paidShare.value
})
function paymentShareLabel(fraction: number) {
  return totalCount.value ? `${Math.round(fraction * 100)}% of payments` : 'No payments recorded'
}

function statusLabel(status: number) {
  return status === 1 ? 'Paid' : status === 2 ? 'Failed' : 'Pending'
}

function displayDate(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function amountLabel(row: PaymentRow) {
  return formatCurrency(row.amount, row.currency || 'USD')
}

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
  try {
    const res = await dispatch<{ results: typeof organizations.value }>('GET_ORGANIZATIONS', {
      targetAnchorId: auth.isSystemAdmin ? selectedAnchorId.value : undefined,
    })
    organizations.value = res.results
  } catch {
    // Filter dropdown just stays empty; the list itself still loaded above.
  }
}

// Switching anchor resets any organisation filter from the previous one, then reloads both lists.
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
      p.status === 1 ? 'Paid' : p.status === 2 ? 'Failed' : 'Pending', p.createdAt ?? '',
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

// Hidden for every role until explicitly granted PAY_ONLINE from the Roles page.
async function payOnline(row: PaymentRow) {
  if (!await confirmAction({
    title: 'Pay online?',
    message: `Recover ${row.householdName}'s failed payment of ${row.amount.toLocaleString()} by paying it online now.`,
    confirmLabel: 'Pay online',
    color: 'secondary',
  })) return
  try {
    await dispatch('PAY_PAYMENT_ONLINE', { id: row.id })
    toast.success('Payment paid online')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Online payment failed')
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

async function viewPayment(row: PaymentRow) {
  detailPayment.value = row
  detailError.value = ''
  detailDialog.value = true
  detailLoading.value = true
  try {
    const response = await dispatch<{ results: PaymentRow[] }>('GET_PAYMENT', { id: row.id })
    if (!response.results?.length) throw new Error('Payment not found')
    detailPayment.value = response.results[0]
  } catch (error) {
    detailError.value = error instanceof Error ? error.message : 'Failed to load payment details'
  } finally {
    detailLoading.value = false
  }
}

function escapeHtml(value: unknown) {
  return String(value ?? '').replace(/[&<>"']/g, (character) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[character] as string
  ))
}

function printReceipt(row: PaymentRow) {
  if (row.status !== 1) return
  const receipt = window.open('', '_blank', 'width=680,height=820')
  if (!receipt) {
    toast.error('Allow pop-ups to view the receipt')
    return
  }
  const channel = row.paymentChannel === 'ONLINE' ? 'Online payment' : 'Field payment'
  receipt.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>Payment receipt ${escapeHtml(row.id)}</title>
    <style>
      * { box-sizing: border-box; font-family: "Segoe UI", sans-serif; }
      body { margin: 0; padding: 32px; color: #0f172a; }
      .receipt { max-width: 520px; margin: 0 auto; border: 2px solid #0d9488; border-radius: 16px; padding: 30px; }
      h1 { margin: 0; color: #0f766e; font-size: 22px; }
      .sub { margin: 5px 0 24px; color: #64748b; font-size: 13px; }
      .row { display: flex; justify-content: space-between; gap: 24px; padding: 11px 0; border-bottom: 1px solid #e2e8f0; font-size: 13px; }
      .row span:first-child { color: #64748b; }
      .row span:last-child { max-width: 65%; font-weight: 650; text-align: right; overflow-wrap: anywhere; }
      .total { color: #0f766e; font-size: 20px; font-weight: 800; }
      @media print { body { padding: 0; } }
    </style></head><body onload="window.print()"><div class="receipt">
      <h1>BioPay Payment Receipt</h1><div class="sub">Confirmed payment record</div>
      <div class="row"><span>Household</span><span>${escapeHtml(row.householdName)}</span></div>
      <div class="row"><span>Household number</span><span>${escapeHtml(row.householdNumber)}</span></div>
      <div class="row"><span>Organization</span><span>${escapeHtml(orgName(row.organisationCode))}</span></div>
      <div class="row"><span>Payment cycle</span><span>${escapeHtml(row.cycle || '—')}</span></div>
      <div class="row"><span>Payment method</span><span>${escapeHtml(channel)}</span></div>
      ${row.onlineReference ? `<div class="row"><span>Transaction reference</span><span>${escapeHtml(row.onlineReference)}</span></div>` : ''}
      <div class="row"><span>Paid on</span><span>${escapeHtml(displayDate(row.verifiedAt || row.createdAt))}</span></div>
      <div class="row total"><span>Amount paid</span><span>${escapeHtml(amountLabel(row))}</span></div>
    </div></body></html>`)
  receipt.document.close()
}
</script>

<template>
  <div>
    <div class="d-flex align-center justify-space-between mb-4">
      <h1 class="text-h5 font-weight-bold">Payments</h1>
      <v-btn v-if="scopeReady && auth.can('DOWNLOAD_REPORTS')" color="secondary" prepend-icon="mdi-download" @click="exportCsv">Export CSV</v-btn>
    </div>

    <template v-if="scopeReady">
    <v-row class="mb-2">
      <v-col cols="12" sm="6" md="3">
        <SummaryMetricCard
          label="Total amount"
          :value="(summary.totalAmount ?? 0).toLocaleString()"
          :detail="totalCount ? `${Math.round(paidAmountShare * 100)}% paid` : 'No payments recorded'"
          icon="mdi-cash-multiple"
          tone="teal"
          :progress="paidAmountShare"
          progress-label="Paid share of total payment amount"
        />
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <SummaryMetricCard
          label="Paid"
          :value="summary.paidCount ?? 0"
          :detail="paymentShareLabel(paidShare)"
          icon="mdi-check-circle-outline"
          tone="green"
          :progress="paidShare"
          progress-label="Paid share of all payments"
        />
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <SummaryMetricCard
          label="Pending"
          :value="summary.pendingCount ?? 0"
          :detail="paymentShareLabel(pendingShare)"
          icon="mdi-clock-alert-outline"
          tone="amber"
          :progress="pendingShare"
          progress-label="Pending share of all payments"
        />
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <SummaryMetricCard
          label="Failed"
          :value="summary.failedCount ?? 0"
          :detail="paymentShareLabel(failedShare)"
          icon="mdi-alert-circle"
          tone="red"
          :progress="failedShare"
          progress-label="Failed share of all payments"
        />
      </v-col>
    </v-row>

    <v-card variant="flat" border>
      <v-card-text>
        <v-row dense align="center">
          <v-col v-if="anchorGateActive" cols="12" sm="4" md="3">
            <v-select v-model="selectedAnchorId" :items="anchors" item-title="name" item-value="id" label="Anchor" clearable hide-details density="compact" prepend-inner-icon="mdi-bank-outline" />
          </v-col>
          <v-col v-if="auth.isSystemAdmin || auth.isAnchorAdministrator" cols="12" sm="4" md="3">
            <v-select
              v-model="organisationFilter" :items="organizations" item-title="name" item-value="organisationCode"
              label="Organisation" clearable hide-details density="compact" :disabled="auth.isSystemAdmin && !selectedAnchorId"
            />
          </v-col>
          <v-col cols="6" sm="4" md="2">
            <v-select
              v-model="statusFilter" :items="[{ title: 'Pending', value: 0 }, { title: 'Paid', value: 1 }, { title: 'Failed', value: 2 }]"
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
        <template #item.amount="{ item }">{{ amountLabel(item) }}</template>
        <template #item.status="{ item }">
          <v-chip size="small" :color="item.status === 1 ? 'success' : item.status === 2 ? 'error' : 'warning'" variant="tonal">
            {{ item.status === 1 ? 'Paid' : item.status === 2 ? 'Failed' : 'Pending' }}
          </v-chip>
          <div v-if="item.status === 1 && item.paymentChannel === 'ONLINE'" class="text-caption text-medium-emphasis mt-1">
            Paid online{{ item.onlineReference ? ` · ${item.onlineReference}` : '' }}
          </div>
        </template>
        <template #item.createdAt="{ item }">{{ item.createdAt ? new Date(item.createdAt).toLocaleDateString() : '-' }}</template>
        <template #item.actions="{ item }">
          <div class="payment-actions">
            <v-tooltip text="View details" location="top"><template #activator="{ props: tip }">
              <v-btn v-bind="tip" icon="mdi-eye-outline" variant="text" size="small" :aria-label="`View payment to ${item.householdName}`" @click="viewPayment(item)" />
            </template></v-tooltip>
            <v-tooltip v-if="item.status === 1" text="Print receipt" location="top"><template #activator="{ props: tip }">
              <v-btn v-bind="tip" icon="mdi-receipt-text-outline" variant="text" size="small" color="primary" :aria-label="`Print receipt for ${item.householdName}`" @click="printReceipt(item)" />
            </template></v-tooltip>
            <v-tooltip v-if="auth.can('PAY_ONLINE') && item.status === 2" text="Pay online" location="top"><template #activator="{ props: tip }">
              <v-btn v-bind="tip" icon="mdi-credit-card-outline" variant="text" size="small" color="secondary" :aria-label="`Pay ${item.householdName}'s failed payment online`" @click="payOnline(item)" />
            </template></v-tooltip>
            <v-tooltip v-if="auth.can('ACCESS_PAYMENTS') && item.status !== 1" text="Delete" location="top"><template #activator="{ props: tip }">
              <v-btn v-bind="tip" icon="mdi-delete" variant="text" size="small" color="error" :aria-label="`Delete payment to ${item.householdName}`" @click="remove(item)" />
            </template></v-tooltip>
          </div>
        </template>
      </v-data-table>
    </v-card>
    </template>

    <v-dialog v-model="detailDialog" max-width="620">
      <v-card v-if="detailPayment">
        <dialog-close-button @close="detailDialog = false" />
        <v-card-title>Payment details</v-card-title>
        <v-card-subtitle>{{ detailPayment.householdName }} · {{ detailPayment.cycle || 'No cycle' }}</v-card-subtitle>
        <v-card-text>
          <div v-if="detailLoading" class="d-flex justify-center py-8"><v-progress-circular indeterminate color="secondary" /></div>
          <v-alert v-else-if="detailError" type="error" variant="tonal" density="compact" class="mb-3">
            {{ detailError }}
            <template #append><v-btn size="small" variant="text" @click="viewPayment(detailPayment)">Retry</v-btn></template>
          </v-alert>
          <dl v-else class="payment-detail-grid">
            <div><dt>Status</dt><dd><v-chip size="small" :color="detailPayment.status === 1 ? 'success' : detailPayment.status === 2 ? 'error' : 'warning'" variant="tonal">{{ statusLabel(detailPayment.status) }}</v-chip></dd></div>
            <div><dt>Amount</dt><dd class="detail-amount">{{ amountLabel(detailPayment) }}</dd></div>
            <div><dt>Household number</dt><dd>{{ detailPayment.householdNumber }}</dd></div>
            <div><dt>Organization</dt><dd>{{ orgName(detailPayment.organisationCode) }}</dd></div>
            <div><dt>Payment cycle</dt><dd>{{ detailPayment.cycle || '—' }}</dd></div>
            <div><dt>Period</dt><dd>{{ detailPayment.dateFrom || '—' }} to {{ detailPayment.dateTo || '—' }}</dd></div>
            <div><dt>Payment method</dt><dd>{{ detailPayment.paymentChannel === 'ONLINE' ? 'Online payment' : detailPayment.status === 1 ? 'Field payment' : '—' }}</dd></div>
            <div><dt>Transaction reference</dt><dd>{{ detailPayment.onlineReference || '—' }}</dd></div>
            <div><dt>Created</dt><dd>{{ displayDate(detailPayment.createdAt) }}</dd></div>
            <div><dt>Completed</dt><dd>{{ displayDate(detailPayment.verifiedAt) }}</dd></div>
          </dl>
        </v-card-text>
        <v-card-actions>
          <v-btn v-if="detailPayment.status === 1 && !detailLoading" variant="tonal" color="primary" prepend-icon="mdi-receipt-text-outline" @click="printReceipt(detailPayment)">Print receipt</v-btn>
          <v-spacer />
          <v-btn variant="text" @click="detailDialog = false">Close</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.payment-actions { display: flex; align-items: center; gap: 2px; min-width: max-content; }
.payment-detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin: 0; }
.payment-detail-grid > div { min-width: 0; padding: 14px; border: 1px solid #e2e8f0; border-radius: 12px; background: #f8fafc; }
.payment-detail-grid dt { margin-bottom: 5px; color: #64748b; font-size: .72rem; font-weight: 750; letter-spacing: .04em; text-transform: uppercase; }
.payment-detail-grid dd { margin: 0; color: #0f172a; font-weight: 600; overflow-wrap: anywhere; }
.payment-detail-grid .detail-amount { color: #0f766e; font-size: 1.15rem; font-weight: 800; }
@media (max-width: 600px) { .payment-detail-grid { grid-template-columns: 1fr; } }
</style>
