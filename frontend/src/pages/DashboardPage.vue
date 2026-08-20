<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { dispatch } from '@/api/client'
import LineChart from '@/components/LineChart.vue'
import BarChart from '@/components/BarChart.vue'

const auth = useAuthStore()
const router = useRouter()

const loading = ref(true)
const loadError = ref('')
const lastUpdated = ref<Date | null>(null)
const metrics = ref<Record<string, any>>({})
const paymentsSeries = ref<{ label: string; value: number }[]>([])
const householdsSeries = ref<{ label: string; value: number }[]>([])

type MetricArt = 'organizations' | 'households' | 'disbursed' | 'alternates' | 'officers' | 'pending' | 'generated' | 'fingerprints' | 'payroll'

interface MetricCard {
  label: string
  value: string | number
  detail: string
  art: MetricArt
  tone?: 'teal' | 'amber' | 'blue'
  wide?: boolean
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [m, p, h] = await Promise.all([
      dispatch<{ results: Record<string, any> }>('DASHBOARD_METRICS'),
      dispatch<{ results: { period: string; amount: number; count: number }[] }>('DASHBOARD_PAYMENTS_CHART'),
      dispatch<{ results: { period: string; count: number }[] }>('DASHBOARD_HOUSEHOLDS_CHART'),
    ])
    metrics.value = m.results
    paymentsSeries.value = p.results.map((r) => ({ label: r.period, value: r.amount }))
    householdsSeries.value = h.results.map((r) => ({ label: r.period, value: r.count }))
    lastUpdated.value = new Date()
  } catch (err) {
    loadError.value = err instanceof Error ? err.message : 'The dashboard could not be loaded.'
  } finally {
    loading.value = false
  }
}

onMounted(load)

// Anchor-only: total generated per organisation, as a chart alongside the existing table.
const orgAmountSeries = computed(() =>
  (metrics.value.amountsByOrganisation ?? []).map((o: any) => ({
    label: o.organisationName ?? o.organisationCode,
    value: o.totalAmount ?? 0,
  })),
)

function currency(v: number | undefined) {
  return (v ?? 0).toLocaleString(undefined, { style: 'currency', currency: 'SSP', maximumFractionDigits: 0 })
}

// Card values use a compact/abbreviated form (SSP 2.4M) so long amounts never
// force one KPI card to grow taller or wider than the rest of the row; full
// precision remains in the detail line and every table below.
function compactCurrency(v: number | undefined) {
  return (v ?? 0).toLocaleString(undefined, { style: 'currency', currency: 'SSP', notation: 'compact', maximumFractionDigits: 1 })
}

function displayDate(value: unknown) {
  if (!value) return '—'
  const date = new Date(String(value))
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString()
}

const anchorMetricCards = computed<MetricCard[]>(() => [
  { label: 'Organizations', value: metrics.value.totalOrganizations ?? '—', detail: 'Active programmes under this anchor', art: 'organizations' },
  { label: 'Households', value: metrics.value.totalHouseholds ?? '—', detail: 'Approved beneficiary records', art: 'households', tone: 'blue' },
  {
    label: 'Total Value Disbursed', value: compactCurrency(metrics.value.combinedAmount),
    detail: `${currency(metrics.value.totalPaymentsAmount)} cash + ${currency(metrics.value.voucherRedeemedAmount)} vouchers`,
    art: 'disbursed', tone: 'amber',
  },
  { label: 'Alternates Registered', value: metrics.value.totalAlternates ?? '—', detail: 'Approved alternate recipients', art: 'alternates' },
  { label: 'Active Officers', value: metrics.value.activeOfficers ?? '—', detail: 'Field officers currently enabled', art: 'officers', tone: 'blue' },
  { label: 'Pending Approvals', value: metrics.value.pendingPayrolls ?? '—', detail: 'Payment cycles awaiting a checker', art: 'pending', tone: 'amber' },
  {
    label: 'Total Generated', value: compactCurrency(metrics.value.totalGeneratedAmount),
    detail: `${metrics.value.generatedCycles ?? 0} non-rejected payment cycle${metrics.value.generatedCycles === 1 ? '' : 's'}`,
    art: 'generated', wide: true,
  },
])

// Fingerprint icon: concentric ridge arcs computed from polar coordinates
// rather than hand-drawn path data, so the whorl stays geometrically even.
function polarPoint(cx: number, cy: number, r: number, deg: number) {
  const a = ((deg - 90) * Math.PI) / 180
  return { x: cx + r * Math.cos(a), y: cy + r * Math.sin(a) }
}
function arcPath(cx: number, cy: number, r: number, startDeg: number, endDeg: number) {
  const s = polarPoint(cx, cy, r, startDeg)
  const e = polarPoint(cx, cy, r, endDeg)
  const large = endDeg - startDeg <= 180 ? 0 : 1
  return `M ${s.x.toFixed(2)} ${s.y.toFixed(2)} A ${r} ${r} 0 ${large} 1 ${e.x.toFixed(2)} ${e.y.toFixed(2)}`
}
const fingerprintRidges = [8, 14, 20, 26, 30].map((r) => arcPath(48, 32, r, -120, 120))

const organisationMetricCards = computed<MetricCard[]>(() => [
  { label: 'My Households', value: metrics.value.totalHouseholds ?? '—', detail: 'Approved beneficiary records', art: 'households', tone: 'blue' },
  { label: 'Alternates Registered', value: metrics.value.totalAlternates ?? '—', detail: 'Approved alternate recipients', art: 'alternates' },
  { label: 'Registered Fingerprints', value: metrics.value.registeredFingerprints ?? '—', detail: 'Biometric templates ready to verify', art: 'fingerprints' },
  {
    label: 'Total Received', value: compactCurrency(metrics.value.combinedAmount),
    detail: `${currency(metrics.value.totalPaymentsReceivedAmount)} cash + ${currency(metrics.value.voucherRedeemedAmount)} vouchers`,
    art: 'disbursed', tone: 'amber',
  },
  {
    label: 'Latest Payroll', value: metrics.value.latestPayroll?.status ?? 'No cycles yet',
    detail: metrics.value.latestPayroll?.cycleCode ?? 'Generate a cycle to begin', art: 'payroll', tone: 'blue',
  },
  {
    label: 'Total Generated', value: compactCurrency(metrics.value.totalGeneratedAmount),
    detail: `${metrics.value.generatedCycles ?? 0} non-rejected payment cycle${metrics.value.generatedCycles === 1 ? '' : 's'}`,
    art: 'generated', wide: true,
  },
])
</script>

<template>
  <div class="dashboard-page">
    <div class="dashboard-heading d-flex align-start justify-space-between mb-5 ga-4">
      <div>
        <img
          src="/biopay_logo_horizontal.svg"
          alt="BioPay — Biometric Payment Solutions"
          class="dashboard-logo mb-3"
        />
        <h1 class="dashboard-title">Welcome back, {{ auth.fullName }}</h1>
        <p>Track programme activity and keep every disbursement accountable.</p>
        <p v-if="lastUpdated" class="updated-at">Updated {{ lastUpdated.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}</p>
      </div>
      <div class="dashboard-actions d-flex ga-2 flex-wrap justify-end">
        <v-btn size="small" variant="text" prepend-icon="mdi-refresh" :loading="loading" @click="load">
          Refresh
        </v-btn>
        <v-btn v-if="auth.isAnchor" size="small" color="primary" prepend-icon="mdi-domain-plus" @click="router.push('/app/organizations')">
          New Organization
        </v-btn>
        <v-btn v-if="auth.hasModule('CASH_TRANSFERS')" size="small" color="secondary" prepend-icon="mdi-calendar-month-outline" @click="router.push('/app/payroll')">
          Generate Payment Cycle
        </v-btn>
        <v-btn v-if="auth.hasModule('VOUCHERS')" size="small" color="secondary" variant="tonal" prepend-icon="mdi-ticket-confirmation-outline" @click="router.push('/app/vouchers')">
          Issue Voucher
        </v-btn>
      </div>
    </div>

    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />
    <v-alert
      v-if="loadError"
      type="error"
      variant="tonal"
      class="mb-5"
      title="Dashboard unavailable"
    >
      <div class="d-flex align-center flex-wrap ga-3">
        <span>{{ loadError }}</span>
        <v-spacer />
        <v-btn variant="outlined" color="error" size="small" prepend-icon="mdi-refresh" @click="load">
          Try again
        </v-btn>
      </div>
    </v-alert>

    <div v-if="!loadError && (auth.isAnchor || auth.isOrganisation)" class="metric-grid">
      <v-card
        v-for="card in (auth.isAnchor ? anchorMetricCards : organisationMetricCards)"
        :key="card.label"
        class="metric-card" :class="[`tone-${card.tone ?? 'teal'}`, { 'metric-card-wide': card.wide }]" variant="flat" border
      >
        <div class="metric-copy">
          <div class="metric-label">{{ card.label }}</div>
          <div class="metric-value">{{ card.value }}</div>
          <div class="metric-detail">{{ card.detail }}</div>
        </div>

        <div class="metric-art" aria-hidden="true">
            <svg v-if="card.art === 'organizations'" viewBox="0 0 96 72">
              <path d="M28 10 48 2 68 10" />
              <rect x="30" y="10" width="36" height="54" rx="2" />
              <path d="M38 16v42M48 16v42M58 16v42" />
              <path d="M24 64h48M26 60h44" />
            </svg>
            <svg v-else-if="card.art === 'households'" viewBox="0 0 96 72">
              <path d="M16 38 48 14 80 38" />
              <path d="M24 38v28h48V38" />
              <rect x="42" y="48" width="12" height="18" rx="1" />
              <rect x="30" y="44" width="9" height="9" rx="1" />
              <rect x="57" y="44" width="9" height="9" rx="1" />
              <path d="M60 18v12" />
              <path d="M63 15c3-3-3-6 0-9" />
            </svg>
            <svg v-else-if="card.art === 'disbursed'" viewBox="0 0 96 72">
              <rect x="10" y="22" width="54" height="32" rx="4" />
              <circle cx="37" cy="38" r="9" />
              <circle cx="72" cy="50" r="15" />
              <path d="M72 43v14M65 50h14" />
            </svg>
            <svg v-else-if="card.art === 'alternates'" viewBox="0 0 96 72">
              <circle cx="48" cy="38" r="9" />
              <circle cx="48" cy="12" r="7" />
              <circle cx="22" cy="56" r="7" />
              <circle cx="74" cy="56" r="7" />
              <path d="M48 19v10M28 51l14-7M68 51l-14-7" />
            </svg>
            <svg v-else-if="card.art === 'officers'" viewBox="0 0 96 72">
              <path d="M48 8 72 18v20c0 18-12 26-24 30-12-4-24-12-24-30V18Z" />
              <path d="M36 38l9 9 16-18" />
            </svg>
            <svg v-else-if="card.art === 'pending'" viewBox="0 0 96 72">
              <rect x="26" y="10" width="44" height="5" rx="2" />
              <rect x="26" y="57" width="44" height="5" rx="2" />
              <path d="M31 15h34l-15 21 15 21H31l15-21Z" />
              <circle class="art-dot" cx="48" cy="33" r="1.6" />
              <circle class="art-dot" cx="48" cy="39" r="1.6" />
            </svg>
            <svg v-else-if="card.art === 'fingerprints'" viewBox="0 0 96 72">
              <path v-for="(d, i) in fingerprintRidges" :key="i" :d="d" />
              <path d="M48 52v14" />
            </svg>
            <svg v-else-if="card.art === 'payroll'" viewBox="0 0 96 72">
              <rect x="18" y="16" width="54" height="46" rx="6" />
              <path d="M18 28h54" />
              <rect x="30" y="8" width="5" height="14" rx="2" />
              <rect x="57" y="8" width="5" height="14" rx="2" />
              <path d="M26 36h24M26 44h24M26 52h20" />
              <circle cx="66" cy="52" r="13" />
              <path d="M60 52l6 6 9-11" />
            </svg>
            <svg v-else viewBox="0 0 96 72">
              <rect x="14" y="46" width="12" height="18" rx="2" />
              <rect x="34" y="36" width="12" height="28" rx="2" />
              <rect x="54" y="24" width="12" height="40" rx="2" />
              <rect x="74" y="10" width="12" height="54" rx="2" />
              <path d="M18 42 40 32 60 20 80 8" />
              <path d="M72 14 80 8 86 15" />
            </svg>
          </div>
      </v-card>
    </div>

    <v-row v-if="!loadError" class="mt-2 analytics-grid">
      <v-col cols="12" md="7">
        <v-card class="pa-3" variant="flat" border>
          <div class="chart-heading">Payment volume over time</div>
          <LineChart v-if="paymentsSeries.length" :data="paymentsSeries" value-prefix="SSP " />
          <div v-else class="chart-empty">
            <v-icon icon="mdi-chart-line" size="24" />
            <span>No payment activity to chart yet.</span>
          </div>
        </v-card>
      </v-col>
      <v-col cols="12" md="5">
        <v-card class="pa-3" variant="flat" border>
          <div class="chart-heading">Household registration trend</div>
          <BarChart v-if="householdsSeries.length" :data="householdsSeries" />
          <div v-else class="chart-empty">
            <v-icon icon="mdi-chart-bar" size="24" />
            <span>No household registrations to chart yet.</span>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <v-row v-if="auth.isAnchor && orgAmountSeries.length" class="mt-2">
      <v-col cols="12">
        <v-card class="pa-3" variant="flat" border>
          <div class="chart-heading">Amount generated by organisation</div>
          <BarChart :data="orgAmountSeries" color="#0D9488" />
        </v-card>
      </v-col>
    </v-row>

    <v-row v-else-if="!loadError && auth.isAnchor && !loading" class="mt-3">
      <v-col cols="12">
        <div class="dashboard-empty">
          <v-icon icon="mdi-domain-off" size="30" />
          <div>
            <strong>No organisation disbursements yet</strong>
            <p>Amounts will appear here after cash transfers or vouchers are processed.</p>
          </div>
        </div>
      </v-col>
    </v-row>

    <v-row v-if="auth.isAnchor && metrics.amountsByOrganisation?.length" class="mt-3">
      <v-col cols="12">
        <v-card variant="flat" border>
          <v-card-title class="section-title text-subtitle-1">Amount generated by organisation</v-card-title>
          <div class="table-scroll" tabindex="0" aria-label="Organisation disbursement totals">
            <v-table density="compact">
            <thead>
              <tr>
                <th>Organization</th>
                <th class="text-right">Cash Transfers</th>
                <th class="text-right">Vouchers</th>
                <th class="text-right">Total</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="org in metrics.amountsByOrganisation" :key="org.organisationCode">
                <td>{{ org.organisationName || org.organisationCode }}</td>
                <td class="text-right">{{ currency(org.paymentsAmount) }}</td>
                <td class="text-right">{{ currency(org.voucherAmount) }}</td>
                <td class="text-right font-weight-bold">{{ currency(org.totalAmount) }}</td>
              </tr>
            </tbody>
            </v-table>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <v-row v-if="auth.isAnchor && metrics.recentTransactions?.length" class="mt-3">
      <v-col cols="12">
        <v-card variant="flat" border>
          <v-card-title class="section-title text-subtitle-1">Recent activity</v-card-title>
          <div class="table-scroll" tabindex="0" aria-label="Recent payment activity">
            <v-table density="compact">
            <thead>
              <tr>
                <th>Household</th>
                <th>Organization</th>
                <th class="text-right">Amount</th>
                <th>Status</th>
                <th>Date</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="tx in metrics.recentTransactions" :key="tx.id">
                <td>{{ tx.householdName }}</td>
                <td>{{ tx.organisationName || tx.organisationCode }}</td>
                <td class="text-right">{{ currency(tx.amount) }}</td>
                <td>
                  <v-chip size="small" :color="tx.status === 1 ? 'success' : 'warning'" variant="tonal">
                    {{ tx.status === 1 ? 'Paid' : 'Pending' }}
                  </v-chip>
                </td>
                <td>{{ displayDate(tx.createdAt) }}</td>
              </tr>
            </tbody>
            </v-table>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <v-row v-else-if="!loadError && auth.isAnchor && !loading" class="mt-3">
      <v-col cols="12">
        <div class="dashboard-empty">
          <v-icon icon="mdi-history" size="30" />
          <div>
            <strong>No recent payment activity</strong>
            <p>Completed and pending transfers will appear here.</p>
          </div>
        </div>
      </v-col>
    </v-row>
  </div>
</template>

<style scoped>
.dashboard-page { font-size: .9375rem; }

.dashboard-logo {
  display: block;
  width: clamp(138px, 11vw, 172px);
  height: auto;
}

@media (max-width: 600px) {
  .dashboard-logo { width: 140px; }
}
</style>

<style scoped>
.dashboard-title { font-size: clamp(1.3rem, 1.05rem + .7vw, 1.625rem); font-weight: 700; color: #0f172a; letter-spacing: -.03em; line-height: 1.25; margin: 0; }
.dashboard-heading p { margin: 4px 0 0; color: #64748b; font-size: .85rem; }
.dashboard-heading .updated-at { color: #0f766e; font-size: .72rem; font-weight: 600; margin-top: 8px; }
.dashboard-actions { max-width: 34rem; }

/* Real CSS grid, not v-row/v-col: every cell in a row shares the same
   explicit row height, so all seven KPI cards line up pixel-for-pixel no
   matter how short or long their value/detail text is. The wide "Total
   Generated" card spans two columns but never grows taller than its peers. */
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.metric-card {
  --metric-color: #0d9488;
  --metric-soft: #ccfbf1;
  position: relative;
  height: 116px;
  overflow: hidden;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 60px;
  align-items: center;
  gap: 8px;
  padding: 14px 16px !important;
  background: #fff !important;
  border-color: #e2e8f0 !important;
}
.metric-card.tone-amber { --metric-color: #d97706; --metric-soft: #fef3c7; }
.metric-card.tone-blue { --metric-color: #2563eb; --metric-soft: #dbeafe; }
.metric-card-wide { grid-column: span 2; grid-template-columns: minmax(0, 1fr) 96px; }
.metric-copy { position: relative; z-index: 1; min-width: 0; }
.metric-label { color: #64748b; font-size: .66rem; font-weight: 700; letter-spacing: .045em; text-transform: uppercase; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.metric-value { color: #0f172a; font-size: clamp(1.1rem, .96rem + .45vw, 1.375rem); font-weight: 750; letter-spacing: -.03em; line-height: 1.15; margin-top: 5px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.metric-detail { color: #64748b; font-size: .68rem; line-height: 1.3; margin-top: 5px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.metric-art {
  position: relative;
  width: 100%;
  aspect-ratio: 1.15;
  display: grid;
  place-items: center;
  color: var(--metric-color);
}
.metric-art::before {
  content: "";
  position: absolute;
  width: 88%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: var(--metric-soft);
  opacity: .72;
}
.metric-art svg { position: relative; width: 100%; max-height: 46px; fill: none; stroke: currentColor; stroke-width: 2.4; stroke-linecap: round; stroke-linejoin: round; }
.metric-art svg .art-dot { fill: currentColor; stroke: none; }
.metric-card-wide .metric-art svg { max-height: 52px; }
@media (hover: hover) and (pointer: fine) {
  .metric-card:hover .metric-art svg { transform: translateY(-2px); }
}
.metric-art svg { transition: transform 220ms cubic-bezier(.16, 1, .3, 1); }
.analytics-grid :deep(.v-card) { padding: 16px !important; }
.chart-heading { font-size: .85rem; font-weight: 600; color: #0f172a; margin-bottom: 8px; }
.chart-empty { min-height: 180px; display: grid; place-content: center; justify-items: center; gap: 8px; color: #64748b; text-align: center; font-size: .85rem; }
.dashboard-empty { min-height: 92px; display: flex; align-items: center; justify-content: center; gap: 14px; color: #475569; border: 1px dashed #cbd5e1; border-radius: 14px; background: #fff; padding: 18px; font-size: .85rem; }
.dashboard-empty strong { color: #0f172a; font-size: .9rem; }
.dashboard-empty p { margin: 3px 0 0; font-size: .8rem; }
.section-title { white-space: normal; overflow-wrap: anywhere; font-size: .85rem !important; padding: 14px 18px !important; min-height: auto !important; }
.table-scroll { max-width: 100%; overflow-x: auto; outline-offset: -2px; }
.table-scroll :deep(table) { font-size: .8rem; }
.table-scroll:focus-visible { outline: 2px solid #0d9488; }
@media (max-width: 1100px) {
  .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .metric-card-wide { grid-column: span 2; }
}
@media (max-width: 720px) {
  .dashboard-heading { flex-direction: column; }
  .dashboard-actions { width: 100%; justify-content: flex-start !important; }
  .dashboard-actions :deep(.v-btn) { flex: 1 1 auto; }
  .metric-grid { grid-template-columns: 1fr; }
  .metric-card, .metric-card-wide { grid-column: span 1; height: auto; min-height: 108px; }
}
</style>
