<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

interface SubscriptionStatus {
  status: 'NONE' | 'ACTIVE' | 'GRACE' | 'ARCHIVED'
  planCode?: string
  expiresAt?: string
  graceDays?: number
  daysToExpiry?: number
  daysToArchive?: number
}

interface Invoice {
  invoiceNumber: string
  planCode?: string
  amount?: number
  currency?: string
  periodStart?: string
  periodEnd?: string
  status: string
  createdAt: string
}

interface AnchorSubscription {
  anchorId: number
  anchorCode?: string
  anchorName?: string
  status: 'NONE' | 'ACTIVE' | 'GRACE' | 'ARCHIVED'
  planCode?: string
  expiresAt?: string
  graceDays?: number
  daysToExpiry?: number
}

const auth = useAuthStore()
const toast = useToast()

const loading = ref(true)
const subscription = ref<SubscriptionStatus>({ status: 'NONE' })
const invoices = ref<Invoice[]>([])
const renewing = ref(false)
const downloadingReceipt = ref<string | null>(null)

// The system owner can see every anchor's subscription, not just their own.
const allSubscriptions = ref<AnchorSubscription[]>([])
const loadingAll = ref(false)

/** Currencies available for renewal records -- USD is the platform default, the rest cover
 *  the anchors this system already operates across (see the org/anchor Country picker). */
const CURRENCIES = ['USD', 'KES', 'UGX', 'SSP', 'ETB', 'TZS', 'RWF', 'NGN', 'XAF', 'GBP', 'EUR']

const renewForm = ref({ amount: null as number | null, currency: 'USD' })
const renewDialog = ref(false)

const statusColor: Record<string, string> = { ACTIVE: 'success', GRACE: 'warning', ARCHIVED: 'error', NONE: 'grey' }

const allSubsHeaders = [
  { title: 'Anchor', key: 'anchorName' },
  { title: 'Status', key: 'status' },
  { title: 'Plan', key: 'planCode' },
  { title: 'Expires', key: 'expiresAt' },
  { title: 'Days to expiry', key: 'daysToExpiry' },
]

const headers = [
  { title: 'Invoice #', key: 'invoiceNumber' },
  { title: 'Period', key: 'period' },
  { title: 'Amount', key: 'amount' },
  { title: 'Status', key: 'status' },
  { title: 'Issued', key: 'createdAt' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'end' as const },
]

function currency(amount: number | undefined, code: string | undefined) {
  if (amount == null) return '—'
  return amount.toLocaleString(undefined, { style: 'currency', currency: code || 'USD', maximumFractionDigits: 0 })
}

function displayDate(value: unknown) {
  if (!value) return '—'
  const date = new Date(String(value))
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString()
}

async function load() {
  loading.value = true
  try {
    const [s, i] = await Promise.all([
      dispatch<{ results: SubscriptionStatus }>('GET_SUBSCRIPTION'),
      dispatch<{ results: Invoice[] }>('GET_SUBSCRIPTION_INVOICES'),
    ])
    subscription.value = s.results ?? { status: 'NONE' }
    invoices.value = i.results ?? []
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load subscription')
  } finally {
    loading.value = false
  }
}

async function loadAllSubscriptions() {
  if (!auth.isSystemAdmin) return
  loadingAll.value = true
  try {
    const r = await dispatch<{ results: AnchorSubscription[] }>('GET_ALL_SUBSCRIPTIONS')
    allSubscriptions.value = r.results ?? []
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load anchor subscriptions')
  } finally {
    loadingAll.value = false
  }
}

onMounted(() => { load(); loadAllSubscriptions() })

function openRenew() {
  renewForm.value = { amount: null, currency: 'USD' }
  renewDialog.value = true
}

async function confirmRenew() {
  renewing.value = true
  try {
    await dispatch('RENEW_SUBSCRIPTION', {
      amount: renewForm.value.amount ?? undefined,
      currency: renewForm.value.amount != null ? renewForm.value.currency : undefined,
    })
    toast.success('Subscription renewed')
    renewDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Renewal failed')
  } finally {
    renewing.value = false
  }
}

function escapeHtml(s: string): string {
  return String(s ?? '').replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c] as string
  ))
}

// Opens a print-ready receipt in a new window -- the same pattern as the household
// voucher print, so "download" here means "print / save as PDF" via the browser,
// consistent with how the rest of the product handles printable documents.
async function downloadReceipt(invoice: Invoice) {
  downloadingReceipt.value = invoice.invoiceNumber
  try {
    const res = await dispatch<{ results: Invoice & { anchorName?: string } }>(
      'GET_SUBSCRIPTION_INVOICE_RECEIPT', { invoiceNumber: invoice.invoiceNumber },
    )
    const r = res.results
    const w = window.open('', '_blank', 'width=640,height=800')
    if (!w) {
      toast.error('Allow pop-ups to view the receipt')
      return
    }
    w.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>Receipt ${escapeHtml(r.invoiceNumber)}</title>
      <style>
        * { box-sizing: border-box; font-family: Arial, Helvetica, sans-serif; }
        body { margin: 0; padding: 32px; color: #0f172a; }
        .receipt { max-width: 480px; margin: 0 auto; border: 2px solid #0d9488; border-radius: 14px; padding: 28px; }
        .title { font-size: 20px; font-weight: 800; color: #0f766e; letter-spacing: -.01em; }
        .sub { color: #64748b; font-size: 13px; margin-top: 4px; }
        .row { display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #e2e8f0; font-size: 14px; }
        .row span:first-child { color: #64748b; }
        .row span:last-child { font-weight: 600; }
        .total { font-size: 18px; font-weight: 800; color: #0f766e; }
        @media print { body { padding: 0; } }
      </style></head>
      <body onload="window.print()">
        <div class="receipt">
          <div class="title">BioPay Subscription Receipt</div>
          <div class="sub">${escapeHtml(r.anchorName ?? '')}</div>
          <div class="row"><span>Invoice number</span><span>${escapeHtml(r.invoiceNumber)}</span></div>
          <div class="row"><span>Plan</span><span>${escapeHtml(r.planCode ?? '—')}</span></div>
          <div class="row"><span>Period</span><span>${escapeHtml(r.periodStart ?? '—')} to ${escapeHtml(r.periodEnd ?? '—')}</span></div>
          <div class="row"><span>Status</span><span>${escapeHtml(r.status)}</span></div>
          <div class="row"><span>Issued</span><span>${escapeHtml(r.createdAt)}</span></div>
          <div class="row total"><span>Amount</span><span>${escapeHtml(currency(r.amount, r.currency))}</span></div>
        </div>
      </body></html>`)
    w.document.close()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load receipt')
  } finally {
    downloadingReceipt.value = null
  }
}

const statusHeadline = computed(() => {
  switch (subscription.value.status) {
    case 'ACTIVE': return `Active — renews in ${subscription.value.daysToExpiry ?? 0} day(s)`
    case 'GRACE': return `In grace period — ${subscription.value.daysToArchive ?? 0} day(s) left before archive`
    case 'ARCHIVED': return 'Archived — renew to restore access'
    default: return 'No subscription on record'
  }
})
</script>

<template>
  <div>
    <div class="d-flex align-center justify-space-between mb-4">
      <div>
        <h1 class="text-h5 font-weight-bold">Subscription</h1>
        <p class="text-body-2 text-medium-emphasis mt-1">Manage your anchor's subscription, and view payment history and receipts.</p>
      </div>
      <v-btn v-if="auth.isAnchor" color="secondary" prepend-icon="mdi-autorenew" :loading="renewing" @click="openRenew">
        Renew subscription
      </v-btn>
    </div>

    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <v-card variant="flat" border class="pa-5 mb-5 status-card">
      <div class="d-flex align-center flex-wrap ga-4">
        <v-chip :color="statusColor[subscription.status] ?? 'grey'" variant="tonal" size="large" class="font-weight-bold">
          {{ subscription.status }}
        </v-chip>
        <div>
          <div class="text-subtitle-1 font-weight-bold">{{ statusHeadline }}</div>
          <div class="text-caption text-medium-emphasis">
            <span v-if="subscription.expiresAt">Current period ends {{ displayDate(subscription.expiresAt) }}. </span>
            <span v-if="subscription.graceDays">{{ subscription.graceDays }}-day grace period after expiry.</span>
          </div>
        </div>
      </div>
      <p class="text-caption text-medium-emphasis mt-3 mb-0">
        Pricing is per your agreement with BioPay — renewal here records the payment and extends access by one month,
        it does not process a card or bank charge itself.
      </p>
    </v-card>

    <v-card v-if="auth.isSystemAdmin" variant="flat" border class="mb-5">
      <v-card-title class="text-subtitle-1 font-weight-bold">All anchors (system owner view)</v-card-title>
      <v-data-table :headers="allSubsHeaders" :items="allSubscriptions" :loading="loadingAll">
        <template #item.status="{ item }">
          <v-chip size="small" :color="statusColor[item.status] ?? 'grey'" variant="tonal">{{ item.status }}</v-chip>
        </template>
        <template #item.expiresAt="{ item }">{{ displayDate(item.expiresAt) }}</template>
        <template #no-data>
          <div class="text-center text-medium-emphasis py-6">No anchors found.</div>
        </template>
      </v-data-table>
    </v-card>

    <v-card variant="flat" border>
      <v-card-title class="text-subtitle-1 font-weight-bold">Payment history</v-card-title>
      <v-data-table :headers="headers" :items="invoices" :loading="loading">
        <template #item.period="{ item }">{{ displayDate(item.periodStart) }} – {{ displayDate(item.periodEnd) }}</template>
        <template #item.amount="{ item }">{{ currency(item.amount, item.currency) }}</template>
        <template #item.status="{ item }">
          <v-chip size="small" color="success" variant="tonal">{{ item.status }}</v-chip>
        </template>
        <template #item.createdAt="{ item }">{{ displayDate(item.createdAt) }}</template>
        <template #item.actions="{ item }">
          <v-btn
            variant="text" size="small" prepend-icon="mdi-receipt-text-outline"
            :loading="downloadingReceipt === item.invoiceNumber" @click="downloadReceipt(item)"
          >
            Receipt
          </v-btn>
        </template>
        <template #no-data>
          <div class="text-center text-medium-emphasis py-6">No invoices yet — they appear here after each renewal.</div>
        </template>
      </v-data-table>
    </v-card>

    <v-dialog v-model="renewDialog" max-width="440">
      <v-card>
        <v-card-title>Renew subscription</v-card-title>
        <v-card-text>
          <p class="text-body-2 text-medium-emphasis mb-3">
            Extends access by one month from the later of today or the current expiry. Recording an amount here is
            optional and only for your own payment records — it does not charge anything.
          </p>
          <v-row dense>
            <v-col cols="7"><v-text-field v-model.number="renewForm.amount" label="Amount paid (optional)" type="number" density="compact" /></v-col>
            <v-col cols="5"><v-select v-model="renewForm.currency" :items="CURRENCIES" label="Currency" density="compact" /></v-col>
          </v-row>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="renewDialog = false">Cancel</v-btn>
          <v-btn color="secondary" :loading="renewing" @click="confirmRenew">Confirm renewal</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.status-card { border-radius: 16px !important; }
</style>
