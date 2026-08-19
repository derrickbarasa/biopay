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

function displayDate(value: unknown) {
  if (!value) return '—'
  const date = new Date(String(value))
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString()
}

const anchorMetricCards = computed<MetricCard[]>(() => [
  { label: 'Organizations', value: metrics.value.totalOrganizations ?? '—', detail: 'Active programmes under this anchor', art: 'organizations' },
  { label: 'Households', value: metrics.value.totalHouseholds ?? '—', detail: 'Approved beneficiary records', art: 'households', tone: 'blue' },
  {
    label: 'Total Value Disbursed', value: currency(metrics.value.combinedAmount),
    detail: `${currency(metrics.value.totalPaymentsAmount)} cash + ${currency(metrics.value.voucherRedeemedAmount)} vouchers`,
    art: 'disbursed', tone: 'amber',
  },
  { label: 'Alternates Registered', value: metrics.value.totalAlternates ?? '—', detail: 'Approved alternate recipients', art: 'alternates' },
  { label: 'Active Officers', value: metrics.value.activeOfficers ?? '—', detail: 'Field officers currently enabled', art: 'officers', tone: 'blue' },
  { label: 'Pending Approvals', value: metrics.value.pendingPayrolls ?? '—', detail: 'Payment cycles awaiting a checker', art: 'pending', tone: 'amber' },
  {
    label: 'Total Generated', value: currency(metrics.value.totalGeneratedAmount),
    detail: `${metrics.value.generatedCycles ?? 0} non-rejected payment cycle${metrics.value.generatedCycles === 1 ? '' : 's'}`,
    art: 'generated', wide: true,
  },
])

const organisationMetricCards = computed<MetricCard[]>(() => [
  { label: 'My Households', value: metrics.value.totalHouseholds ?? '—', detail: 'Approved beneficiary records', art: 'households', tone: 'blue' },
  { label: 'Alternates Registered', value: metrics.value.totalAlternates ?? '—', detail: 'Approved alternate recipients', art: 'alternates' },
  { label: 'Registered Fingerprints', value: metrics.value.registeredFingerprints ?? '—', detail: 'Biometric templates ready to verify', art: 'fingerprints' },
  {
    label: 'Total Received', value: currency(metrics.value.combinedAmount),
    detail: `${currency(metrics.value.totalPaymentsReceivedAmount)} cash + ${currency(metrics.value.voucherRedeemedAmount)} vouchers`,
    art: 'disbursed', tone: 'amber',
  },
  {
    label: 'Latest Payroll', value: metrics.value.latestPayroll?.status ?? 'No cycles yet',
    detail: metrics.value.latestPayroll?.cycleCode ?? 'Generate a cycle to begin', art: 'payroll', tone: 'blue',
  },
  {
    label: 'Total Generated', value: currency(metrics.value.totalGeneratedAmount),
    detail: `${metrics.value.generatedCycles ?? 0} non-rejected payment cycle${metrics.value.generatedCycles === 1 ? '' : 's'}`,
    art: 'generated', wide: true,
  },
])
</script>

<template>
  <div class="dashboard-page">
    <div class="dashboard-heading d-flex align-start justify-space-between mb-6 ga-4">
      <div>
        <img
          src="/biopay_logo_horizontal.svg"
          alt="BioPay — Biometric Payment Solutions"
          class="dashboard-logo mb-4"
        />
        <h1 class="text-h4 font-weight-bold">Welcome back, {{ auth.fullName }}</h1>
        <p>Track programme activity and keep every disbursement accountable.</p>
        <p v-if="lastUpdated" class="updated-at">Updated {{ lastUpdated.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}</p>
      </div>
      <div class="dashboard-actions d-flex ga-2 flex-wrap justify-end">
        <v-btn variant="text" prepend-icon="mdi-refresh" :loading="loading" @click="load">
          Refresh
        </v-btn>
        <v-btn v-if="auth.isAnchor" color="primary" prepend-icon="mdi-domain-plus" @click="router.push('/app/organizations')">
          New Organization
        </v-btn>
        <v-btn v-if="auth.hasModule('CASH_TRANSFERS')" color="secondary" prepend-icon="mdi-calendar-month-outline" @click="router.push('/app/payroll')">
          Generate Payment Cycle
        </v-btn>
        <v-btn v-if="auth.hasModule('VOUCHERS')" color="secondary" variant="tonal" prepend-icon="mdi-ticket-confirmation-outline" @click="router.push('/app/vouchers')">
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

    <v-row v-if="!loadError && (auth.isAnchor || auth.isOrganisation)" class="metric-grid">
      <v-col
        v-for="card in (auth.isAnchor ? anchorMetricCards : organisationMetricCards)"
        :key="card.label"
        cols="12"
        sm="6"
        :md="card.wide ? 6 : 3"
      >
        <v-card class="metric-card" :class="[`tone-${card.tone ?? 'teal'}`, `art-${card.art}`, { 'metric-card-wide': card.wide }]" variant="flat" border>
          <div class="metric-copy">
            <div class="metric-label">{{ card.label }}</div>
            <div class="metric-value">{{ card.value }}</div>
            <div class="metric-detail">{{ card.detail }}</div>
          </div>

          <div class="metric-art" :class="`art-${card.art}`" aria-hidden="true">
            <svg v-if="card.art === 'organizations'" viewBox="0 0 112 78">
              <path d="M8 68h96M17 68V34h22v34M43 68V17h30v51M78 68V28h19v40" />
              <path d="M24 42h8M24 51h8M51 28h7M63 28h3M51 39h7M63 39h3M51 50h7M63 50h3M85 39h6M85 49h6" />
            </svg>
            <svg v-else-if="card.art === 'households'" viewBox="0 0 112 78">
              <path d="M9 45l21-18 21 18v23H15V45M61 42l17-14 18 14v26H61V42" />
              <path d="M25 68V52h11v16M70 49h16M78 42v15" />
            </svg>
            <svg v-else-if="card.art === 'disbursed'" viewBox="0 0 112 78">
              <rect x="8" y="16" width="96" height="51" rx="9" />
              <circle cx="56" cy="42" r="14" />
              <path d="M56 34v16M51 37h8.5a4 4 0 010 8H52M17 26h8M87 57h8" />
            </svg>
            <svg v-else-if="card.art === 'alternates'" viewBox="0 0 112 78">
              <path d="M31 39l23-18 26 20M31 39l17 24M80 41L64 63M48 63h16" />
              <circle cx="31" cy="39" r="10" /><circle cx="54" cy="21" r="10" /><circle cx="80" cy="41" r="10" /><circle cx="56" cy="63" r="10" />
            </svg>
            <svg v-else-if="card.art === 'officers'" viewBox="0 0 112 78">
              <circle cx="39" cy="27" r="11" /><circle cx="74" cy="31" r="9" />
              <path d="M16 68c1-18 10-28 23-28s22 10 23 28M58 68c1-15 7-24 17-24 11 0 18 9 20 24" />
              <path d="M34 48l5 6 5-6M71 51l4 5 4-5" />
            </svg>
            <svg v-else-if="card.art === 'pending'" viewBox="0 0 112 78">
              <circle cx="54" cy="40" r="27" /><path d="M54 24v18l13 8M44 8h20M54 8v5" />
              <circle cx="88" cy="19" r="10" /><path d="M88 14v6M88 24v.5" />
            </svg>
            <svg v-else-if="card.art === 'fingerprints'" viewBox="0 0 112 78">
              <path d="M56 10c-21 0-36 15-36 34 0 10-2 17-6 24M56 20c-15 0-26 10-26 25 0 11-2 19-6 25M56 30c-9 0-16 6-16 16 0 12-2 20-6 27M56 10c21 0 36 15 36 34 0 10 2 17 6 24M56 20c15 0 26 10 26 25 0 11 2 19 6 25M56 30c9 0 16 6 16 16 0 12 2 20 6 27M56 39v32" />
            </svg>
            <svg v-else-if="card.art === 'payroll'" viewBox="0 0 112 78">
              <rect x="22" y="11" width="68" height="57" rx="7" /><path d="M37 28h38M37 39h25M37 50h19" />
              <circle cx="80" cy="55" r="15" /><path d="M73 55l5 5 9-11" />
            </svg>
            <svg v-else viewBox="0 0 140 78">
              <path d="M20 19h75v48H20zM28 11h75v48M36 3h75v48" />
              <path d="M33 35h34M33 45h24M78 29l7 7 13-15" />
            </svg>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <v-row v-if="!loadError" class="mt-3 analytics-grid">
      <v-col cols="12" md="7">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-subtitle-1 font-weight-medium mb-2">Payment volume over time</div>
          <LineChart v-if="paymentsSeries.length" :data="paymentsSeries" value-prefix="SSP " />
          <div v-else class="chart-empty">
            <v-icon icon="mdi-chart-line" size="28" />
            <span>No payment activity to chart yet.</span>
          </div>
        </v-card>
      </v-col>
      <v-col cols="12" md="5">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-subtitle-1 font-weight-medium mb-2">Household registration trend</div>
          <BarChart v-if="householdsSeries.length" :data="householdsSeries" />
          <div v-else class="chart-empty">
            <v-icon icon="mdi-chart-bar" size="28" />
            <span>No household registrations to chart yet.</span>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <v-row v-if="auth.isAnchor && orgAmountSeries.length" class="mt-3">
      <v-col cols="12">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-subtitle-1 font-weight-medium mb-2">Amount generated by organisation</div>
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
            <v-table density="comfortable">
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
            <v-table density="comfortable">
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
.dashboard-logo {
  display: block;
  width: clamp(180px, 17vw, 238px);
  height: auto;
}

@media (max-width: 600px) {
  .dashboard-logo { width: 176px; }
}
</style>

<style scoped>
.dashboard-heading h1 { color: #0f172a; letter-spacing: -.03em; }
.dashboard-heading p { margin: 5px 0 0; color: #64748b; }
.dashboard-heading .updated-at { color: #0f766e; font-size: .78rem; font-weight: 600; margin-top: 10px; }
.dashboard-actions { max-width: 38rem; }
.metric-card {
  --metric-color: #0d9488;
  --metric-soft: #ccfbf1;
  position: relative;
  min-height: 164px;
  overflow: hidden;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 88px;
  align-items: center;
  gap: 10px;
  padding: 22px !important;
  background: #fff !important;
  border-color: #e2e8f0 !important;
}
.metric-card.tone-amber { --metric-color: #d97706; --metric-soft: #fef3c7; }
.metric-card.tone-blue { --metric-color: #2563eb; --metric-soft: #dbeafe; }
.metric-card-wide { grid-template-columns: minmax(0, 1fr) minmax(150px, 220px); }
.metric-card.art-disbursed:not(.metric-card-wide) {
  grid-template-columns: minmax(0, 1fr);
}
.metric-card.art-disbursed:not(.metric-card-wide) .metric-value {
  font-size: 1.35rem;
  overflow-wrap: normal;
}
.metric-card.art-disbursed:not(.metric-card-wide) .metric-art {
  position: absolute;
  right: 12px;
  bottom: 16px;
  width: 82px;
  opacity: .34;
}
.metric-copy { position: relative; z-index: 1; min-width: 0; }
.metric-label { color: #64748b; font-size: .76rem; font-weight: 700; letter-spacing: .045em; text-transform: uppercase; }
.metric-value { color: #0f172a; font-size: clamp(1.55rem, 1.2rem + .8vw, 2.1rem); font-weight: 750; letter-spacing: -.035em; line-height: 1.12; margin-top: 9px; overflow-wrap: anywhere; }
.metric-detail { color: #64748b; font-size: .76rem; line-height: 1.35; margin-top: 8px; max-width: 28ch; }
.metric-art {
  position: relative;
  width: 100%;
  aspect-ratio: 1.35;
  display: grid;
  place-items: center;
  color: var(--metric-color);
}
.metric-art::before {
  content: "";
  position: absolute;
  width: 92%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: var(--metric-soft);
  opacity: .72;
}
.metric-art svg { position: relative; width: 100%; max-height: 82px; fill: none; stroke: currentColor; stroke-width: 2.2; stroke-linecap: round; stroke-linejoin: round; }
.metric-card-wide .metric-art { max-width: 210px; justify-self: end; }
.metric-card-wide .metric-art::before { width: 72%; }
.metric-card-wide .metric-art svg { max-height: 92px; }
@media (hover: hover) and (pointer: fine) {
  .metric-card:hover .metric-art svg { transform: translateY(-2px); }
}
.metric-art svg { transition: transform 220ms cubic-bezier(.16, 1, .3, 1); }
.analytics-grid :deep(.v-card) { padding: 22px !important; }
.chart-empty { min-height: 220px; display: grid; place-content: center; justify-items: center; gap: 10px; color: #64748b; text-align: center; }
.dashboard-empty { min-height: 112px; display: flex; align-items: center; justify-content: center; gap: 16px; color: #475569; border: 1px dashed #cbd5e1; border-radius: 14px; background: #fff; padding: 24px; }
.dashboard-empty strong { color: #0f172a; }
.dashboard-empty p { margin: 3px 0 0; font-size: .875rem; }
.section-title { white-space: normal; overflow-wrap: anywhere; }
.table-scroll { max-width: 100%; overflow-x: auto; outline-offset: -2px; }
.table-scroll :deep(table) { min-width: 680px; }
.table-scroll:focus-visible { outline: 2px solid #0d9488; }
@media (max-width: 720px) {
  .dashboard-heading { flex-direction: column; }
  .dashboard-actions { width: 100%; justify-content: flex-start !important; }
  .dashboard-actions :deep(.v-btn) { flex: 1 1 auto; }
  .metric-card, .metric-card-wide { grid-template-columns: minmax(0, 1fr) 92px; }
  .metric-card-wide .metric-art { max-width: 110px; }
}
</style>
