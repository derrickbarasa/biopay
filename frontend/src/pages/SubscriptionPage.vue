<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { formatCurrency } from '@/utils/currency'
import { daysUntilExpiry } from '@/utils/subscriptionDates'

interface SubscriptionStatus {
  status: 'NONE' | 'ACTIVE' | 'GRACE' | 'ARCHIVED' | 'SUSPENDED' | 'CANCELLED'
  planCode?: string
  periodDays?: number | null
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
  periodDays?: number
}

interface AnchorSubscription {
  anchorId: number
  anchorCode?: string
  anchorName?: string
  status: 'NONE' | 'ACTIVE' | 'GRACE' | 'ARCHIVED' | 'SUSPENDED' | 'CANCELLED'
  planCode?: string
  periodDays?: number
  expiresAt?: string
  graceDays?: number
  daysToExpiry?: number
}

const auth = useAuthStore()
const toast = useToast()
const { confirmAction } = useConfirm()
const route = useRoute()
const router = useRouter()

const loading = ref(true)
const subscription = ref<SubscriptionStatus>({ status: 'NONE' })
const invoices = ref<Invoice[]>([])
const downloadingReceipt = ref<string | null>(null)
const managingAnchorId = ref<number | null>(null)

// The super admin can see every anchor's subscription, not just their own.
const allSubscriptions = ref<AnchorSubscription[]>([])
const loadingAll = ref(false)
const routeAnchorId = computed(() => {
  const value = Number(route.params.anchorId)
  return Number.isInteger(value) && value > 0 ? value : null
})
const isDetailRoute = computed(() => route.name === 'subscription-detail')
const selectedAnchorId = ref<number | null>(routeAnchorId.value)
const subscriptionScope = computed(() => auth.isSystemAdmin ? { targetAnchorId: selectedAnchorId.value } : {})
const statusFilter = ref<string | null>(null)
const anchorSearch = ref('')
const filteredSubscriptions = computed(() => allSubscriptions.value.filter((a) => {
  if (statusFilter.value && a.status !== statusFilter.value) return false
  if (anchorSearch.value && !(a.anchorName ?? '').toLowerCase().includes(anchorSearch.value.toLowerCase())) return false
  return true
}))
const selectedAnchor = computed(() => allSubscriptions.value.find((a) => a.anchorId === selectedAnchorId.value) ?? null)
const displayedDaysToExpiry = computed(() => daysUntilExpiry(subscription.value.expiresAt))

const statusColor: Record<string, string> = { ACTIVE: 'success', GRACE: 'warning', ARCHIVED: 'error', SUSPENDED: 'warning', CANCELLED: 'error', NONE: 'grey' }

const allSubsHeaders = [
  { title: 'Anchor Name', key: 'anchorName' },
  { title: 'Status', key: 'status' },
  { title: 'Plan', key: 'planCode' },
  { title: 'Expires', key: 'expiresAt' },
  { title: 'Days to expiry', key: 'daysToExpiry' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'start' as const },
]

const headers = [
  { title: 'Invoice #', key: 'invoiceNumber' },
  { title: 'Period', key: 'period' },
  { title: 'Amount', key: 'amount' },
  { title: 'Status', key: 'status' },
  { title: 'Issued', key: 'createdAt' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'start' as const },
]

function currency(amount: number | undefined, code: string | undefined) {
  if (amount == null) return '—'
  return formatCurrency(amount, code || 'USD')
}

function displayDate(value: unknown) {
  if (!value) return '—'
  const date = new Date(String(value))
  // Spelled-out month avoids the DD/MM vs MM/DD ambiguity of a numeric locale date.
  return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(date)
}

// periodDays is the actual day count applied when expiresAt was computed
// (Subscription.java always advances by the single configured period, never
// by whatever planCode happens to say) -- it's the source of truth, so it
// wins over a stale/mismatched planCode label rather than being ignored.
function displayPlan(value?: string, periodDays?: number | null) {
  if (periodDays) return `${periodDays}-day`
  if (!value) return '—'
  return value
    .toLowerCase()
    .replace(/[_-]+/g, ' ')
    .replace(/\b\w/g, (character) => character.toUpperCase())
}

async function load() {
  if (auth.isSystemAdmin && !selectedAnchorId.value) {
    subscription.value = { status: 'NONE' }
    invoices.value = []
    loading.value = false
    return
  }
  loading.value = true
  try {
    const [s, i] = await Promise.all([
      dispatch<{ results: SubscriptionStatus }>('GET_SUBSCRIPTION', subscriptionScope.value),
      dispatch<{ results: Invoice[] }>('GET_SUBSCRIPTION_INVOICES', subscriptionScope.value),
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

onMounted(async () => {
  await loadAllSubscriptions()
  await load()
})
watch(selectedAnchorId, () => { void load() })
watch(routeAnchorId, (anchorId) => {
  selectedAnchorId.value = anchorId
})

function viewAnchorDetails(item: AnchorSubscription) {
  router.push({ name: 'subscription-detail', params: { anchorId: item.anchorId } })
}

// Actions column / own-status "Make Payment" both land on the same page; for the super
// admin acting on behalf of an anchor, pass which one along so it doesn't have to be
// re-selected there.
function goToMakePayment(anchorId?: number, anchorName?: string) {
  router.push({ path: '/app/subscription/pay', query: anchorId ? { anchorId: String(anchorId), anchorName: anchorName ?? '' } : {} })
}

function canRenew(status: AnchorSubscription['status'] | SubscriptionStatus['status']) {
  return ['NONE', 'GRACE', 'ARCHIVED', 'CANCELLED'].includes(status)
}

async function manageSubscription(item: AnchorSubscription, action: 'SUSPEND' | 'RESUME' | 'CANCEL') {
  const label = action.toLowerCase()
  const anchorName = item.anchorName || 'This anchor'
  const confirmed = await confirmAction({
    title: `${action === 'RESUME' ? 'Resume' : action === 'SUSPEND' ? 'Suspend' : 'Cancel'} subscription?`,
    message: action === 'SUSPEND'
      ? `${anchorName}'s access will be paused until the subscription is resumed.`
      : action === 'CANCEL'
        ? `${anchorName}'s subscription will end and access will require renewal.`
        : `${anchorName}'s subscription access will be restored through its current expiry date.`,
    confirmLabel: action === 'RESUME' ? 'Resume subscription' : `${action === 'SUSPEND' ? 'Suspend' : 'Cancel'} subscription`,
    color: action === 'RESUME' ? 'secondary' : 'error',
  })
  if (!confirmed) return
  managingAnchorId.value = item.anchorId
  try {
    await dispatch('SET_SUBSCRIPTION_STATE', { targetAnchorId: item.anchorId, action })
    toast.success(`Subscription ${label === 'suspend' ? 'suspended' : label === 'cancel' ? 'cancelled' : 'resumed'}`)
    await loadAllSubscriptions()
    if (selectedAnchorId.value === item.anchorId) await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : `Failed to ${label} subscription`)
  } finally {
    managingAnchorId.value = null
  }
}

function escapeHtml(s: string): string {
  return String(s ?? '').replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c] as string
  ))
}

// "Download" means print/save-as-PDF via a print-ready window, not a real file download.
async function downloadReceipt(invoice: Invoice) {
  downloadingReceipt.value = invoice.invoiceNumber
  try {
    const res = await dispatch<{ results: Invoice & { anchorName?: string } }>(
      'GET_SUBSCRIPTION_INVOICE_RECEIPT', { invoiceNumber: invoice.invoiceNumber, ...subscriptionScope.value },
    )
    const r = res.results
    const logoUrl = new URL('/biopay_logo_horizontal.svg', window.location.origin).href
    const w = window.open('', '_blank', 'width=860,height=900')
    if (!w) {
      toast.error('Allow pop-ups to view the receipt')
      return
    }
    w.document.write(`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Receipt ${escapeHtml(r.invoiceNumber)}</title>
      <style>
        * { box-sizing: border-box; }
        body { margin: 0; padding: 32px; background: #f8fafc; color: #0f172a; font-family: Ubuntu, "Segoe UI", sans-serif; font-variant-numeric: tabular-nums; }
        .receipt { width: 100%; max-width: 780px; margin: 0 auto; overflow: hidden; background: #fff; border: 1px solid #cbd5e1; border-radius: 16px; }
        .header { display: flex; align-items: center; justify-content: space-between; gap: 32px; padding: 28px 34px; border-bottom: 1px solid #e2e8f0; }
        .logo { display: block; width: 200px; height: auto; }
        .receipt-heading { min-width: 0; text-align: right; }
        .receipt-type { color: #0f766e; font-size: 17px; font-weight: 800; letter-spacing: .035em; text-transform: uppercase; }
        .receipt-number { max-width: 310px; margin-top: 7px; color: #475569; font-size: 14px; overflow-wrap: anywhere; }
        .content { padding: 28px 34px 0; }
        .intro { display: flex; align-items: center; justify-content: space-between; gap: 28px; margin-bottom: 24px; }
        .title { margin: 0; color: #0f172a; font-size: 30px; font-weight: 800; letter-spacing: -.025em; line-height: 1.15; }
        .sub { margin-top: 7px; color: #64748b; font-size: 15px; }
        .paid { display: inline-flex; align-items: center; gap: 8px; flex: 0 0 auto; padding: 10px 15px; border-radius: 999px; background: #dcfce7; color: #166534; font-size: 13px; font-weight: 800; letter-spacing: .05em; text-transform: uppercase; }
        .paid-mark { display: grid; place-items: center; width: 20px; height: 20px; border-radius: 50%; background: #10b981; color: #fff; }
        .paid-mark svg { display: block; width: 12px; height: 12px; }
        .details { margin: 0; }
        .row { display: grid; grid-template-columns: minmax(130px, .65fr) minmax(0, 1.35fr); align-items: baseline; gap: 28px; padding: 13px 0; border-bottom: 1px solid #e2e8f0; font-size: 14px; }
        .row dt { color: #64748b; }
        .row dd { margin: 0; color: #0f172a; font-weight: 600; text-align: right; overflow-wrap: anywhere; }
        .total { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-top: 24px; padding: 20px 24px; border-radius: 10px; background: #0f766e; color: #fff; }
        .total-label { display: flex; align-items: center; gap: 13px; font-size: 15px; font-weight: 700; }
        .card-icon { display: grid; place-items: center; flex: 0 0 40px; width: 40px; height: 40px; border-radius: 50%; background: rgba(255,255,255,.13); }
        .card-icon svg { display: block; width: 22px; height: 22px; }
        .total-amount { font-size: 30px; font-weight: 800; letter-spacing: -.02em; }
        .footer { padding: 22px 34px 26px; color: #64748b; font-size: 12px; line-height: 1.5; text-align: center; }
        @media (max-width: 620px) { body { padding: 12px; } .header { align-items: flex-start; gap: 18px; padding: 22px; } .logo { width: 145px; } .receipt-type { font-size: 13px; } .receipt-number { font-size: 11px; } .content { padding: 23px 22px 0; } .intro { align-items: flex-start; } .title { font-size: 24px; } .sub { font-size: 13px; } .paid { padding: 8px 11px; font-size: 11px; } .row { grid-template-columns: 105px minmax(0,1fr); gap: 16px; font-size: 12px; } .total { padding: 17px; } .total-amount { font-size: 22px; } .footer { padding: 19px 22px 22px; } }
        @media (max-width: 420px) { .header { display: block; } .receipt-heading { margin-top: 20px; text-align: left; } .receipt-number { max-width: none; } .intro { display: block; } .paid { margin-top: 16px; } .total { align-items: flex-start; flex-direction: column; gap: 13px; } .total-amount { align-self: flex-end; } }
        @media print { @page { size: A4 portrait; margin: 14mm; } body { padding: 0; background: #fff; -webkit-print-color-adjust: exact; print-color-adjust: exact; } .receipt { max-width: none; border-color: #94a3b8; break-inside: avoid; } }
      </style></head>
      <body onload="window.focus();window.print()">
        <article class="receipt" aria-label="Subscription payment receipt">
          <header class="header">
            <img class="logo" src="${escapeHtml(logoUrl)}" alt="BioPay">
            <div class="receipt-heading"><div class="receipt-type">Subscription receipt</div><div class="receipt-number">${escapeHtml(r.invoiceNumber)}</div></div>
          </header>
          <div class="content">
            <div class="intro">
              <div><h1 class="title">Payment received</h1><div class="sub">Thank you for your subscription payment.</div></div>
              <span class="paid"><span class="paid-mark" aria-hidden="true"><svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m3.5 8.2 2.8 2.8 6.2-6.2"/></svg></span>${escapeHtml(r.status)}</span>
            </div>
            <dl class="details">
              <div class="row"><dt>Customer</dt><dd>${escapeHtml(r.anchorName || 'Anchor subscription')}</dd></div>
              <div class="row"><dt>Invoice number</dt><dd>${escapeHtml(r.invoiceNumber)}</dd></div>
              <div class="row"><dt>Plan</dt><dd>${escapeHtml(displayPlan(r.planCode, r.periodDays))}</dd></div>
              <div class="row"><dt>Billing period</dt><dd>${escapeHtml(displayDate(r.periodStart))} &ndash; ${escapeHtml(displayDate(r.periodEnd))}</dd></div>
              <div class="row"><dt>Status</dt><dd>${escapeHtml(r.status)}</dd></div>
              <div class="row"><dt>Issued</dt><dd>${escapeHtml(displayDate(r.createdAt))}</dd></div>
            </dl>
            <div class="total">
              <div class="total-label"><span class="card-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="2.5" y="5" width="19" height="14" rx="2.5"/><path d="M2.5 9.5h19M6 15h4"/></svg></span><span>Amount paid</span></div>
              <span class="total-amount">${escapeHtml(currency(r.amount, r.currency))}</span>
            </div>
          </div>
          <div class="footer">Thank you. Keep this receipt for your subscription records.</div>
        </article>
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
    case 'ACTIVE': return `Active — renews in ${displayedDaysToExpiry.value ?? subscription.value.daysToExpiry ?? 0} day(s)`
    case 'GRACE': return `In grace period — ${subscription.value.daysToArchive ?? 0} day(s) left before archive`
    case 'ARCHIVED': return 'Archived — renew to restore access'
    case 'SUSPENDED': return 'Suspended — access is paused by the platform owner'
    case 'CANCELLED': return 'Cancelled — renew to restore access'
    default: return 'No subscription on record'
  }
})
</script>

<template>
  <div>
    <v-btn v-if="isDetailRoute" :to="{ name: 'subscription' }" variant="text" prepend-icon="mdi-arrow-left" class="mb-3 ml-n3">
      Subscriptions
    </v-btn>

    <div class="mb-4">
      <h1 class="page-title">{{ isDetailRoute ? 'Subscription Details' : 'Subscription' }}</h1>
      <p class="text-body-2 text-medium-emphasis mt-1">
        {{ isDetailRoute ? `View ${selectedAnchor?.anchorName || 'this anchor'}'s subscription and payment history.` : "Manage your anchor's subscription, and view payment history and receipts." }}
      </p>
    </div>

    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <v-card v-if="!auth.isSystemAdmin" variant="flat" border class="pa-5 mb-5 status-card">
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
        <v-spacer />
        <v-btn
          v-if="auth.can('ACCESS_SUBSCRIPTION') && canRenew(subscription.status)"
          color="secondary" prepend-icon="mdi-credit-card-outline"
          @click="goToMakePayment(auth.isSystemAdmin ? selectedAnchorId ?? undefined : undefined, auth.isSystemAdmin ? allSubscriptions.find((a) => a.anchorId === selectedAnchorId)?.anchorName : undefined)"
        >
          Make Payment
        </v-btn>
      </div>
      <p class="text-caption text-medium-emphasis mt-3 mb-0">
        Pricing is set on the Billing page. A confirmed payment extends access by {{ subscription.periodDays ?? 60 }} days.
      </p>
    </v-card>

    <v-card v-if="auth.isSystemAdmin && !isDetailRoute" variant="flat" border class="mb-5">
      <div class="d-flex align-center flex-wrap ga-3 pa-4">
        <h2 class="text-subtitle-1 font-weight-bold mr-auto">All anchors</h2>
        <v-text-field v-model="anchorSearch" prepend-inner-icon="mdi-magnify" label="Search anchors" hide-details density="compact" variant="outlined" style="max-width: 240px" />
        <v-select v-model="statusFilter" :items="['ACTIVE', 'GRACE', 'ARCHIVED', 'SUSPENDED', 'CANCELLED', 'NONE']" label="Status" clearable hide-details density="compact" variant="outlined" style="max-width: 160px" />
      </div>
      <v-data-table
        class="all-anchors-table"
        :headers="allSubsHeaders" :items="filteredSubscriptions" :loading="loadingAll"
      >
        <template #item.status="{ item }">
          <v-chip size="small" :color="statusColor[item.status] ?? 'grey'" variant="tonal">{{ item.status }}</v-chip>
        </template>
        <template #item.planCode="{ item }">{{ displayPlan(item.planCode, item.periodDays) }}</template>
        <template #item.expiresAt="{ item }">{{ displayDate(item.expiresAt) }}</template>
        <template #item.daysToExpiry="{ item }">{{ daysUntilExpiry(item.expiresAt) ?? '—' }}</template>
        <template #item.actions="{ item }">
          <div class="subscription-actions">
          <v-btn
            variant="text" size="small" color="primary"
            prepend-icon="mdi-eye-outline"
            @click.stop="viewAnchorDetails(item)"
          >View</v-btn>
          <v-btn
            v-if="canRenew(item.status)" variant="text" size="small" color="secondary" prepend-icon="mdi-credit-card-outline"
            @click.stop="goToMakePayment(item.anchorId, item.anchorName)"
          >Make Payment</v-btn>
          <v-btn
            v-if="item.status === 'ACTIVE' || item.status === 'GRACE'" variant="text" size="small" color="warning"
            :loading="managingAnchorId === item.anchorId" @click.stop="manageSubscription(item, 'SUSPEND')"
          >Suspend</v-btn>
          <v-btn
            v-if="item.status === 'SUSPENDED'" variant="text" size="small" color="secondary"
            :loading="managingAnchorId === item.anchorId" @click.stop="manageSubscription(item, 'RESUME')"
          >Resume</v-btn>
          <v-btn
            v-if="item.status === 'ACTIVE' || item.status === 'GRACE' || item.status === 'SUSPENDED'" variant="text" size="small" color="error"
            :loading="managingAnchorId === item.anchorId" @click.stop="manageSubscription(item, 'CANCEL')"
          >Cancel</v-btn>
          </div>
        </template>
        <template #no-data>
          <div class="text-center text-medium-emphasis py-6">No anchors found.</div>
        </template>
      </v-data-table>

    </v-card>

    <template v-if="auth.isSystemAdmin && isDetailRoute">
      <section v-if="selectedAnchor" class="anchor-subscription-detail" aria-live="polite">
          <div class="anchor-detail-heading">
            <h2 class="text-h6 font-weight-bold">{{ selectedAnchor.anchorName || 'Anchor' }}</h2>
            <p v-if="selectedAnchor.anchorCode" class="text-body-2 text-medium-emphasis mb-0">{{ selectedAnchor.anchorCode }}</p>
          </div>

          <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />
          <div class="anchor-detail-summary">
            <div>
              <span class="detail-label">Status</span>
              <v-chip size="small" :color="statusColor[subscription.status] ?? 'grey'" variant="tonal" class="font-weight-bold">{{ subscription.status }}</v-chip>
            </div>
            <div><span class="detail-label">Plan</span><strong>{{ displayPlan(subscription.planCode, subscription.periodDays) }}</strong></div>
            <div><span class="detail-label">Current period ends</span><strong>{{ displayDate(subscription.expiresAt) }}</strong></div>
            <div><span class="detail-label">Days to expiry</span><strong>{{ displayedDaysToExpiry ?? '—' }}</strong></div>
          </div>

          <div class="d-flex align-center flex-wrap ga-3 mt-5 mb-2">
            <div>
              <h4 class="text-subtitle-1 font-weight-bold">Payment history</h4>
              <p class="text-caption text-medium-emphasis mb-0">Invoices and downloadable receipts for this anchor.</p>
            </div>
            <v-spacer />
            <v-btn
              v-if="canRenew(subscription.status)" color="secondary" variant="tonal" size="small"
              prepend-icon="mdi-credit-card-outline" @click="goToMakePayment(selectedAnchor.anchorId, selectedAnchor.anchorName)"
            >Make Payment</v-btn>
          </div>
          <v-data-table :headers="headers" :items="invoices" :loading="loading" density="comfortable" class="history-table">
            <template #item.period="{ item }">{{ displayDate(item.periodStart) }} – {{ displayDate(item.periodEnd) }}</template>
            <template #item.amount="{ item }">{{ currency(item.amount, item.currency) }}</template>
            <template #item.status="{ item }"><v-chip size="small" color="success" variant="tonal">{{ item.status }}</v-chip></template>
            <template #item.createdAt="{ item }">{{ displayDate(item.createdAt) }}</template>
            <template #item.actions="{ item }">
              <v-btn variant="text" size="small" prepend-icon="mdi-receipt-text-outline" :loading="downloadingReceipt === item.invoiceNumber" @click="downloadReceipt(item)">Download receipt</v-btn>
            </template>
            <template #no-data><div class="text-center text-medium-emphasis py-6">No invoices yet — they appear here after each renewal.</div></template>
          </v-data-table>
      </section>
      <div v-else-if="loadingAll || loading" class="pa-5">
        <v-progress-linear indeterminate color="primary" aria-label="Loading subscription details" />
      </div>
      <v-alert v-else type="warning" variant="tonal" title="Subscription not found" text="This anchor does not exist or is outside your permitted scope." />
    </template>

    <v-card v-if="!auth.isSystemAdmin" variant="flat" border>
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
  </div>
</template>

<style scoped>
.status-card { border-radius: 16px !important; }
.subscription-actions { display: flex; align-items: center; gap: 2px; white-space: nowrap; }
.anchor-subscription-detail { padding-bottom: 20px; }
.anchor-detail-heading { margin-bottom: 18px; }
.anchor-detail-summary { display: grid; grid-template-columns: repeat(4, minmax(140px, 1fr)); gap: 12px; }
.anchor-detail-summary > div { display: flex; min-height: 78px; flex-direction: column; justify-content: center; gap: 7px; padding: 13px 15px; border-radius: 10px; background: rgba(var(--v-theme-primary), .055); }
.detail-label { color: rgba(var(--v-theme-on-surface), .65); font-size: .75rem; }
.history-table { margin-top: 10px; border: 1px solid rgba(var(--v-border-color), var(--v-border-opacity)); border-radius: 10px; overflow: hidden; }

@media (max-width: 900px) {
  .anchor-detail-summary { grid-template-columns: repeat(2, minmax(140px, 1fr)); }
}

@media (max-width: 600px) {
  .anchor-detail-summary { grid-template-columns: 1fr; }
  .subscription-actions { flex-wrap: wrap; white-space: normal; }
}
</style>
