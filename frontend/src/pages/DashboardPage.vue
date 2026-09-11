<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { dispatch } from '@/api/client'
import ChartPeriodPicker from '@/components/ChartPeriodPicker.vue'
import DashboardChart from '@/components/DashboardChart.vue'
import PieChart from '@/components/PieChart.vue'
import { dateKey, filledSeries, dashboardCurrency, type ChartPeriod, type ChartPoint } from '@/utils/dashboard'

const auth = useAuthStore()
const router = useRouter()

const loading = ref(true)
const paymentChartLoading = ref(false)
const registrationChartLoading = ref(false)
const loadError = ref('')
const paymentChartError = ref('')
const registrationChartError = ref('')
const metrics = ref<Record<string, any>>({})
const cashSeries = ref<ChartPoint[]>([])
const voucherSeries = ref<ChartPoint[]>([])
const householdsSeries = ref<ChartPoint[]>([])
const alternatesSeries = ref<ChartPoint[]>([])


const paymentPeriod = ref<ChartPeriod>('month')
const registrationPeriod = ref<ChartPeriod>('month')
const paymentDate = ref(dateKey(new Date()))
const registrationDate = ref(dateKey(new Date()))

interface MetricCard {
  label: string
  value: string | number
  detail: string
  icon: string
  tone?: 'teal' | 'amber' | 'green' | 'slate'
}

const paymentCounts = ref({ cash: 0, vouchers: 0 })
const periodCaption = (period: ChartPeriod, date: string) => {
  const selected = new Date(date + 'T00:00:00')
  const text = period === 'year' ? selected.getFullYear().toString() : period === 'month'
    ? selected.toLocaleDateString(undefined, { month: 'long', year: 'numeric' }) : date
  return period === 'week' ? 'Week containing ' + text : text
}

let paymentChartRequest = 0
async function loadPaymentChart() {
  const request = ++paymentChartRequest
  const period = paymentPeriod.value
  const referenceDate = paymentDate.value
  paymentChartLoading.value = true
  paymentChartError.value = ''
  try {
    const response = await dispatch<{ results: { period: string; cashAmount: number; voucherAmount: number; cashCount: number; voucherCount: number }[] }>(
      'DASHBOARD_PAYMENTS_CHART', { period, referenceDate },
    )
    if (request !== paymentChartRequest) return
    cashSeries.value = filledSeries(response.results, (row) => Number(row.cashAmount ?? 0), period, referenceDate)
    voucherSeries.value = filledSeries(response.results, (row) => Number(row.voucherAmount ?? 0), period, referenceDate)
    paymentCounts.value = {
      cash: filledSeries(response.results, row => Number(row.cashCount ?? 0), period, referenceDate).reduce((sum, point) => sum + point.value, 0),
      vouchers: filledSeries(response.results, row => Number(row.voucherCount ?? 0), period, referenceDate).reduce((sum, point) => sum + point.value, 0),
    }
  } catch (err) {
    if (request !== paymentChartRequest) return
    cashSeries.value = []; voucherSeries.value = []
    paymentCounts.value = { cash: 0, vouchers: 0 }
    paymentChartError.value = err instanceof Error ? err.message : 'The payment chart could not be loaded.'
  } finally {
    if (request === paymentChartRequest) paymentChartLoading.value = false
  }
}

let registrationChartRequest = 0
async function loadRegistrationChart() {
  const request = ++registrationChartRequest
  const period = registrationPeriod.value
  const referenceDate = registrationDate.value
  registrationChartLoading.value = true
  registrationChartError.value = ''
  try {
    const response = await dispatch<{ results: { period: string; householdCount: number; alternateCount: number }[] }>(
      'DASHBOARD_HOUSEHOLDS_CHART', { period, referenceDate },
    )
    if (request !== registrationChartRequest) return
    householdsSeries.value = filledSeries(response.results, (row) => Number(row.householdCount ?? 0), period, referenceDate)
    alternatesSeries.value = filledSeries(response.results, (row) => Number(row.alternateCount ?? 0), period, referenceDate)
  } catch (err) {
    if (request !== registrationChartRequest) return
    householdsSeries.value = []; alternatesSeries.value = []
    registrationChartError.value = err instanceof Error ? err.message : 'The registration chart could not be loaded.'
  } finally {
    if (request === registrationChartRequest) registrationChartLoading.value = false
  }
}

let metricsRequest = 0
async function loadMetrics() {
  const request = ++metricsRequest
  loading.value = true
  loadError.value = ''
  try {
    const response = await dispatch<{ results: Record<string, any> }>('DASHBOARD_METRICS')
    if (request === metricsRequest) metrics.value = response.results
  } catch (err) {
    if (request === metricsRequest) loadError.value = err instanceof Error ? err.message : 'The dashboard could not be loaded.'
  } finally {
    if (request === metricsRequest) loading.value = false
  }
}
async function load() { await Promise.all([loadMetrics(), loadPaymentChart(), loadRegistrationChart()]) }
onMounted(load)
watch([paymentPeriod, paymentDate], loadPaymentChart)
watch([registrationPeriod, registrationDate], loadRegistrationChart)
const currency = dashboardCurrency

function displayDate(value: unknown) {
  if (!value) return '—'
  const date = new Date(String(value))
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString()
}

const dashboardCards = computed<MetricCard[]>(() => {
  const cards: MetricCard[] = []
  const cashAmount = auth.isAnchor ? metrics.value.totalPaymentsAmount : metrics.value.totalPaymentsReceivedAmount
  const cashCount = auth.isAnchor ? metrics.value.totalPaymentsCount : metrics.value.totalPaymentsReceivedCount

  if (auth.isSystemAdmin) {
    cards.push({
      label: 'Anchors', value: metrics.value.totalAnchors ?? 0,
      detail: 'Active anchor institutions on the platform', icon: 'mdi-bank', tone: 'slate',
    })
  }
  if (auth.isAnchor && auth.can('ACCESS_ORGANISATIONS')) {
    cards.push({
      label: 'Organizations', value: metrics.value.totalOrganizations ?? 0,
      detail: auth.isSystemAdmin ? 'Active programmes across BioPay' : 'Active programmes under this anchor',
      icon: 'mdi-domain', tone: 'teal',
    })
  }
  if (auth.can('ACCESS_HOUSEHOLDS')) {
    cards.push({
      label: auth.isAnchor ? 'Households' : 'My households', value: metrics.value.totalHouseholds ?? 0,
      detail: 'Active beneficiary records; all review statuses', icon: 'mdi-home-group', tone: 'green',
    })
  }
  if ((auth.hasModule('CASH_TRANSFERS') || auth.hasModule('VOUCHERS')) && (auth.can('ACCESS_PAYMENTS') || auth.can('ACCESS_VOUCHERS'))) {
    cards.push({
      label: auth.isAnchor ? 'Value Disbursed' : 'Value Received',
      value: currency(metrics.value.combinedAmount),
      detail: `${cashCount ?? 0} cash payment${cashCount === 1 ? '' : 's'} (${currency(cashAmount)}) · ${metrics.value.voucherRedeemedCount ?? 0} voucher${metrics.value.voucherRedeemedCount === 1 ? '' : 's'} (${currency(metrics.value.voucherRedeemedAmount)})`,
      icon: 'mdi-cash-multiple', tone: 'amber',
    })
  }
  if (auth.hasModule('VOUCHERS') && auth.can('ACCESS_VOUCHERS')) {
    cards.push({ label: 'Vouchers redeemed', value: metrics.value.voucherRedeemedCount ?? 0,
      detail: currency(metrics.value.voucherRedeemedAmount) + ' redeemed', icon: 'mdi-ticket-confirmation-outline', tone: 'teal' })
  }
  if (auth.isAnchor && auth.hasModule('CASH_TRANSFERS') && auth.can('ACCESS_PAYMENT_CYCLES')) {
    cards.push({
      label: 'Pending approvals', value: metrics.value.pendingPayrolls ?? 0,
      detail: 'Payment cycles awaiting a checker', icon: 'mdi-clock-alert-outline', tone: 'amber',
    })
  }
  if (auth.isAnchor && auth.can('ACCESS_SUPERVISORS')) {
    cards.push({
      label: 'Active officers', value: metrics.value.activeOfficers ?? 0,
      detail: 'Field officers currently enabled', icon: 'mdi-account-check-outline', tone: 'slate',
    })
  }
  if (auth.can('ACCESS_HOUSEHOLDS')) {
    const scopeDetail = auth.isAnchor ? 'Across accessible organizations' : 'Ready for verification'
    cards.push({
      label: 'Registered Fingerprints', value: metrics.value.registeredFingerprints ?? 0,
      detail: `${scopeDetail} · ${metrics.value.faceScanned ?? 0} face${metrics.value.faceScanned === 1 ? '' : 's'} scanned`,
      icon: 'mdi-fingerprint', tone: 'teal',
    })
  }
  if (auth.hasModule('CASH_TRANSFERS') && auth.can('ACCESS_PAYMENT_CYCLES')) {
    cards.push({
      label: 'Total generated', value: currency(metrics.value.totalGeneratedAmount),
      detail: `${metrics.value.generatedCycles ?? 0} non-rejected payment cycle${metrics.value.generatedCycles === 1 ? '' : 's'}`,
      icon: 'mdi-chart-line', tone: 'green',
    })
  }
  if (auth.can('ACCESS_ALTERNATES')) {
    cards.push({
      label: 'Alternates registered', value: metrics.value.totalAlternates ?? 0,
      detail: 'Active alternate recipients', icon: 'mdi-account-check-outline', tone: 'green',
    })
  }
  return cards
})

const orgRanking = computed(() => {
  const rows = [...(metrics.value.amountsByOrganisation ?? [])]
    .sort((a: any, b: any) => Number(b.totalAmount ?? 0) - Number(a.totalAmount ?? 0))
    .slice(0, 4)
  const max = Math.max(...rows.map((row: any) => Number(row.totalAmount ?? 0)), 1)
  return rows.map((row: any) => ({ ...row, share: (Number(row.totalAmount ?? 0) / max) * 100 }))
})

const cashPeriodTotal = computed(() => cashSeries.value.reduce((total, point) => total + point.value, 0))
const voucherPeriodTotal = computed(() => voucherSeries.value.reduce((total, point) => total + point.value, 0))
const householdPeriodTotal = computed(() => householdsSeries.value.reduce((total, point) => total + point.value, 0))
const alternatePeriodTotal = computed(() => alternatesSeries.value.reduce((total, point) => total + point.value, 0))
</script>

<template>
  <div class="dashboard-page">
    <header class="dashboard-heading">
      <div class="heading-copy">
        <img src="/biopay_logo_horizontal.svg" alt="BioPay" class="dashboard-logo" />
        <h1 class="page-title">Welcome back, {{ auth.fullName }}</h1>
      </div>
      <div class="dashboard-actions">
        <v-btn size="small" variant="text" prepend-icon="mdi-refresh" :loading="loading" @click="load">Refresh</v-btn>
        <v-btn v-if="auth.isAnchor && auth.can('ACCESS_ORGANISATIONS')" size="small" color="secondary" prepend-icon="mdi-domain-plus" @click="router.push('/app/organizations')">New Organization</v-btn>
        <v-btn v-if="auth.hasModule('CASH_TRANSFERS') && auth.can('ACCESS_PAYMENT_CYCLES')" size="small" color="secondary" prepend-icon="mdi-calendar-month-outline" @click="router.push('/app/payroll/generate')">Generate Payment Cycle</v-btn>
        <v-btn v-if="auth.hasModule('VOUCHERS') && auth.can('ACCESS_VOUCHERS')" size="small" color="secondary" variant="tonal" prepend-icon="mdi-ticket-confirmation-outline" @click="router.push('/app/vouchers')">Issue Voucher</v-btn>
      </div>
    </header>

    <v-progress-linear v-if="loading" indeterminate color="primary" class="dashboard-progress" />
    <v-alert v-if="loadError" type="error" variant="tonal" class="mb-5" title="Dashboard unavailable">
      <div class="d-flex align-center flex-wrap ga-3">
        <span>{{ loadError }}</span><v-spacer />
        <v-btn variant="outlined" color="error" size="small" prepend-icon="mdi-refresh" @click="load">Try again</v-btn>
      </div>
    </v-alert>

      <p class="scope-note">Cards show all-time totals in your access scope. Amounts are source USD values before payout conversion.</p>
      <p v-if="loading" role="status">Loading programme summary...</p>
      <section v-if="!loading && !loadError" aria-label="Programme summary">
        <div class="metric-grid">
          <article v-for="card in dashboardCards" :key="card.label" class="metric-card" :class="`tone-${card.tone ?? 'teal'}`">
            <div class="metric-icon" aria-hidden="true"><v-icon :icon="card.icon" size="20" /></div>
            <div class="metric-copy">
              <div class="metric-label">{{ card.label }}</div>
              <div class="metric-value" :title="String(card.value)">{{ card.value }}</div>
              <div class="metric-detail">{{ card.detail }}</div>
            </div>
          </article>
        </div>
      </section>

      <section class="analytics-grid" aria-label="Programme trends">
        <v-card variant="flat" border class="chart-panel">
          <v-progress-linear v-if="paymentChartLoading" indeterminate color="primary" class="chart-loading" />
          <div class="panel-heading">
            <div>
              <h3>Payment volume</h3><p class="chart-context">{{ periodCaption(paymentPeriod, paymentDate) }} &middot; completed payments and redeemed vouchers</p>
              <div class="chart-legend"><span class="legend-cash">Cash</span><span class="legend-voucher">Vouchers</span></div>
            </div>
            <div class="chart-card-actions">
              <ChartPeriodPicker v-model:period="paymentPeriod" v-model:date="paymentDate" control-label="Choose payment chart date" />
              <div v-if="!paymentChartLoading && !paymentChartError" class="panel-totals"><span>{{ currency(cashPeriodTotal) }} &middot; {{ paymentCounts.cash }} cash payment{{ paymentCounts.cash === 1 ? '' : 's' }}</span><span>{{ currency(voucherPeriodTotal) }} &middot; {{ paymentCounts.vouchers }} voucher{{ paymentCounts.vouchers === 1 ? '' : 's' }}</span></div>
            </div>
          </div>
          <v-alert v-if="paymentChartError" type="error" variant="tonal" density="compact" class="chart-error">{{ paymentChartError }}<v-btn size="small" variant="text" @click="loadPaymentChart">Retry</v-btn></v-alert>
          <p v-if="paymentChartLoading" role="status" class="chart-loading-message">Loading payment activity...</p>
          <DashboardChart
            v-if="!paymentChartLoading && !paymentChartError" :data="cashSeries" :secondary-data="voucherSeries" money series-label="Cash" secondary-label="Vouchers"
            color="#0D9488" secondary-color="#F59E0B" secondary-as-line :ariaLabel="`Cash and voucher payment volume for ${periodCaption(paymentPeriod, paymentDate)}`"
          />
        </v-card>
        <v-card variant="flat" border class="chart-panel pie-panel">
          <v-progress-linear v-if="registrationChartLoading" indeterminate color="primary" class="chart-loading" />
          <div class="panel-heading">
            <div>
              <h3>Registration Trend</h3><p class="chart-context">{{ periodCaption(registrationPeriod, registrationDate) }} &middot; all registrations, including inactive records</p>
            </div>
            <div class="chart-card-actions">
              <ChartPeriodPicker v-model:period="registrationPeriod" v-model:date="registrationDate" control-label="Choose registration chart date" />
            </div>
          </div>
          <v-alert v-if="registrationChartError" type="error" variant="tonal" density="compact" class="chart-error">{{ registrationChartError }}<v-btn size="small" variant="text" @click="loadRegistrationChart">Retry</v-btn></v-alert>
          <p v-if="registrationChartLoading" role="status" class="chart-loading-message">Loading registration activity...</p>
          <PieChart
            v-if="!registrationChartLoading && !registrationChartError"
            :data="[{ label: 'Households', value: householdPeriodTotal }, { label: 'Alternates', value: alternatePeriodTotal }]"
            :colors="['#15803D', '#0EA5E9']" variant="pie" :size="200" centered class="pie-panel-chart"
          />
        </v-card>
      </section>

      <section v-if="auth.isAnchor && !loading && !loadError" class="operations-grid" aria-label="Organisation performance and recent activity">
        <v-card variant="flat" border class="ranking-panel">
          <div class="panel-heading">
            <div><h2>Organisation performance</h2><span>Top 4 organisations by all-time disbursements</span></div>
            <v-btn v-if="auth.can('ACCESS_ORGANISATIONS')" size="small" variant="text" append-icon="mdi-arrow-right" @click="router.push('/app/organizations')">View all</v-btn>
          </div>
          <div v-if="orgRanking.length" class="ranking-list">
            <div v-for="(org, index) in orgRanking" :key="org.organisationCode" class="ranking-row">
              <span class="ranking-index">{{ String(index + 1).padStart(2, '0') }}</span>
              <div class="ranking-data">
                <div class="ranking-copy"><strong :title="org.organisationName || org.organisationCode">{{ org.organisationName || org.organisationCode }}</strong><span>{{ currency(org.totalAmount) }}</span></div>
                <div class="ranking-track" aria-hidden="true"><span :style="{ width: `${org.share}%` }" /></div>
                <div class="ranking-breakdown"><span>Cash {{ currency(org.paymentsAmount) }}</span><span>Vouchers {{ currency(org.voucherAmount) }}</span></div>
              </div>
            </div>
          </div>
          <div v-else-if="!loading" class="compact-empty"><v-icon icon="mdi-domain-off" size="24" /><div><strong>No organisation disbursements yet</strong><span>Processed cash transfers and vouchers will appear here.</span></div></div>
        </v-card>

        <v-card variant="flat" border class="activity-panel">
          <div class="panel-heading"><div><h2>Recent activity</h2><span>Latest 4 cash-transfer records</span></div><v-btn v-if="auth.can('ACCESS_PAYMENTS')" size="small" variant="text" append-icon="mdi-arrow-right" @click="router.push('/app/payments')">View payments</v-btn></div>
          <div v-if="metrics.recentTransactions?.length" class="activity-list">
            <div v-for="transaction in metrics.recentTransactions.slice(0, 4)" :key="transaction.id" class="activity-row">
              <div class="activity-mark" :class="{ pending: transaction.status !== 1 || transaction.rejected }" aria-hidden="true" />
              <div class="activity-copy"><strong>{{ transaction.householdName || 'Household' }}</strong><span>{{ transaction.organisationName || transaction.organisationCode }} &middot; {{ transaction.rejected ? 'Rejected' : transaction.status === 1 ? 'Paid' : transaction.status === 2 ? 'Failed' : 'Pending' }}</span></div>
              <div class="activity-value"><strong>{{ currency(transaction.amount) }}</strong><span>{{ displayDate(transaction.activityAt ?? transaction.createdAt) }}</span></div>
            </div>
          </div>
          <div v-else-if="!loading" class="compact-empty"><v-icon icon="mdi-history" size="24" /><div><strong>No recent payment activity</strong><span>Completed and pending transfers will appear here.</span></div></div>
        </v-card>
      </section>

      <v-card v-if="auth.isAnchor && !loading && !loadError && metrics.amountsByOrganisation?.length" variant="flat" border class="totals-panel">
        <div class="panel-heading totals-heading">
          <div><h2>Amount disbursed by organisation</h2><span>All organisations, including inactive ones &middot; completed cash transfers and redeemed vouchers</span></div>
        </div>
        <div class="table-scroll" tabindex="0" aria-label="Organization disbursement totals">
          <v-table density="compact">
            <thead>
              <tr>
                <th>Organization</th>
                <th class="text-right">Cash transfers</th>
                <th class="text-right">Vouchers</th>
                <th class="text-right">Total</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="org in metrics.amountsByOrganisation" :key="org.organisationCode">
                <td>{{ org.organisationName || org.organisationCode }}<span v-if="org.active === false"> (Inactive)</span></td>
                <td class="text-right">{{ currency(org.paymentsAmount) }}</td>
                <td class="text-right">{{ currency(org.voucherAmount) }}</td>
                <td class="text-right font-weight-bold">{{ currency(org.totalAmount) }}</td>
              </tr>
            </tbody>
            <tfoot><tr><th>Total</th><td class="text-right">{{ currency(metrics.totalPaymentsAmount) }}</td><td class="text-right">{{ currency(metrics.voucherRedeemedAmount) }}</td><td class="text-right font-weight-bold">{{ currency(metrics.combinedAmount) }}</td></tr></tfoot>
          </v-table>
        </div>
      </v-card>
  </div>
</template>

<style scoped>
/* Compact operations-ledger layout: scan density is intentional. */
.scope-note { margin: 0 0 12px; color: #64748b; font-size: .75rem; }
.chart-context { margin-top: 5px; color: #64748b; font-size: .7rem; line-height: 1.4; }
.chart-loading-message { min-height: 230px; display: grid; place-items: center; color: #64748b; }
.dashboard-page { color: #0f172a; font-size: .9375rem; }
.dashboard-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 14px; }
.heading-copy { min-width: 0; }
.dashboard-logo { display: block; width: 92px; height: auto; margin-bottom: 5px; }
.dashboard-heading h1 { margin: 0; font-size: clamp(1.05rem, .98rem + .3vw, 1.25rem); font-weight: 750; letter-spacing: -.02em; line-height: 1.2; }
.dashboard-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; max-width: 38rem; }
.dashboard-progress { margin: -10px 0 18px; }
.panel-heading h2, .panel-heading h3 { margin: 0; color: #0f172a; font-size: .82rem; font-weight: 700; letter-spacing: -.01em; }
.panel-heading span { margin: 2px 0 0; color: #64748b; font-size: .67rem; }
.metric-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 10px; }
.metric-card { --metric-color: #0d9488; --metric-soft: #e6fffb; display: grid; grid-template-columns: minmax(0, 1fr) 34px; align-items: start; gap: 10px; min-height: 92px; padding: 12px; overflow: hidden; border: 1px solid #e2e8f0; border-radius: 14px; background: #fff; }
.metric-card.tone-amber { --metric-color: #b45309; --metric-soft: #fff7ed; }
.metric-card.tone-green { --metric-color: #15803d; --metric-soft: #f0fdf4; }
.metric-card.tone-slate { --metric-color: #475569; --metric-soft: #f1f5f9; }
.metric-icon { grid-column: 2; grid-row: 1; display: grid; place-items: center; width: 34px; height: 34px; border-radius: 50%; color: var(--metric-color); background: var(--metric-soft); }
.metric-copy { min-width: 0; }
.metric-label { color: #64748b; font-size: .61rem; font-weight: 700; letter-spacing: .025em; text-transform: uppercase; white-space: normal; overflow-wrap: anywhere; }
.metric-value { margin-top: 4px; color: #0f172a; font-size: clamp(1rem, .94rem + .2vw, 1.16rem); font-weight: 760; letter-spacing: -.025em; line-height: 1.15; font-variant-numeric: tabular-nums; white-space: normal; overflow-wrap: anywhere; }
.metric-detail { margin-top: 5px; color: #64748b; font-size: .61rem; line-height: 1.25; overflow-wrap: anywhere; }
.analytics-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: stretch; gap: 12px; margin-top: 12px; }
.chart-panel, .ranking-panel, .activity-panel, .totals-panel { border-color: #e2e8f0 !important; border-radius: 14px !important; }
.chart-panel { position: relative; min-width: 0; overflow: hidden; padding: 16px 16px 8px; }
.pie-panel { display: flex; flex-direction: column; }
.pie-panel-chart { flex: 1 1 auto; align-content: center; min-height: 0; }
.chart-loading { position: absolute; inset: 0 0 auto; }
.panel-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding-bottom: 10px; }
.panel-heading > div { min-width: 0; }
.panel-heading .panel-total { color: #0f172a; font-size: .82rem; font-weight: 750; font-variant-numeric: tabular-nums; }
.chart-panel > .panel-heading { flex-wrap: wrap; gap: 8px 12px; }
.chart-panel > .panel-heading > div:first-child { flex: 1 1 140px; }
.chart-card-actions { flex: 0 0 auto; margin-left: auto; min-width: 192px; display: grid; justify-items: end; gap: 5px; }
.panel-totals { display: grid; justify-items: end; gap: 2px; color: #475569; font-size: .67rem; font-variant-numeric: tabular-nums; white-space: nowrap; }
.chart-legend { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 5px; color: #64748b; font-size: .65rem; }
.chart-legend span { display: inline-flex; align-items: center; gap: 5px; }
.chart-legend span::before { content: ''; width: 7px; height: 7px; border-radius: 50%; background: var(--legend-color); }
.legend-cash { --legend-color: #0d9488; }
.legend-voucher { --legend-color: #f59e0b; }
.legend-household { --legend-color: #15803d; }
.legend-alternate { --legend-color: #0ea5e9; }
.operations-grid { display: grid; align-items: stretch; grid-template-columns: minmax(0, 1.2fr) minmax(320px, .8fr); gap: 12px; margin-top: 12px; }
.ranking-panel, .activity-panel { padding: 16px; }
.ranking-list, .activity-list { border-top: 1px solid #eef2f6; }
.ranking-row { display: grid; grid-template-columns: 24px minmax(0, 1fr); gap: 10px; padding: 10px 0; border-bottom: 1px solid #f1f5f9; }
.ranking-row:last-child, .activity-row:last-child { border-bottom: 0; }
.ranking-index { padding-top: 1px; color: #94a3b8; font-size: .65rem; font-weight: 700; font-variant-numeric: tabular-nums; }
.ranking-data { min-width: 0; }
.ranking-copy, .ranking-breakdown { display: flex; justify-content: space-between; gap: 12px; }
.ranking-copy strong { overflow: hidden; color: #334155; font-size: .76rem; font-weight: 650; white-space: nowrap; text-overflow: ellipsis; }
.ranking-copy span { color: #0f172a; font-size: .73rem; font-weight: 700; font-variant-numeric: tabular-nums; white-space: nowrap; }
.ranking-track { height: 6px; margin: 6px 0 5px; overflow: hidden; border-radius: 4px; background: #e8eef2; }
.ranking-track span { display: block; height: 100%; border-radius: inherit; background: #0d9488; }
.ranking-breakdown { justify-content: flex-start; color: #64748b; font-size: .62rem; }
.ranking-breakdown span + span::before { content: '·'; margin-right: 8px; color: #cbd5e1; }
.activity-row { display: grid; grid-template-columns: 8px minmax(0, 1fr) auto; align-items: center; gap: 10px; min-height: 54px; border-bottom: 1px solid #f1f5f9; }
.activity-mark { width: 7px; height: 7px; border-radius: 50%; background: #16a34a; }
.activity-mark.pending { background: #f59e0b; }
.activity-copy, .activity-value { min-width: 0; display: grid; gap: 2px; }
.activity-copy strong { overflow: hidden; color: #334155; font-size: .74rem; font-weight: 650; white-space: nowrap; text-overflow: ellipsis; }
.activity-copy span, .activity-value span { overflow: hidden; color: #64748b; font-size: .64rem; white-space: nowrap; text-overflow: ellipsis; }
.activity-value { justify-items: end; }
.activity-value strong { color: #0f172a; font-size: .72rem; font-weight: 700; font-variant-numeric: tabular-nums; }
.compact-empty { min-height: 120px; display: flex; align-items: center; justify-content: center; gap: 12px; color: #64748b; text-align: left; }
.compact-empty div { display: grid; gap: 2px; }
.compact-empty strong { color: #334155; font-size: .8rem; }
.compact-empty span { font-size: .72rem; }
.totals-panel { margin-top: 12px; overflow: hidden; }
.totals-heading { padding: 14px 16px 10px; }
.table-scroll { max-width: 100%; overflow-x: auto; scrollbar-width: thin; scrollbar-color: #94a3b8 transparent; outline-offset: -2px; }
.table-scroll:focus-visible { outline: 2px solid #0d9488; }
.table-scroll :deep(table) { font-size: .76rem; }
.table-scroll :deep(th) { color: #64748b; font-size: .65rem !important; }
@media (max-width: 1200px) { .metric-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); } }
@media (max-width: 900px) { .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .analytics-grid, .operations-grid { grid-template-columns: 1fr; } }
@media (max-width: 720px) { .dashboard-heading { flex-direction: column; } .dashboard-actions { width: 100%; justify-content: flex-start; } .dashboard-actions :deep(.v-btn) { flex: 1 1 auto; } }
@media (max-width: 520px) { .metric-grid { grid-template-columns: 1fr; } .metric-card { min-height: 86px; } .ranking-breakdown { flex-wrap: wrap; row-gap: 2px; } .panel-heading { gap: 10px; } .chart-card-actions { min-width: 132px; } }
</style>
