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

    <!-- Anchor KPIs -->
    <v-row v-if="!loadError && auth.isAnchor" class="metric-grid">
      <v-col cols="12" sm="6" md="3">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">Total Organizations</div>
          <div class="text-h4 font-weight-bold">{{ metrics.totalOrganizations ?? '-' }}</div>
        </v-card>
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">Total Households</div>
          <div class="text-h4 font-weight-bold">{{ metrics.totalHouseholds ?? '-' }}</div>
        </v-card>
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">Total Value Disbursed</div>
          <div class="text-h4 font-weight-bold">{{ currency(metrics.combinedAmount) }}</div>
          <div class="text-caption">{{ currency(metrics.totalPaymentsAmount) }} cash + {{ currency(metrics.voucherRedeemedAmount) }} vouchers</div>
        </v-card>
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">Alternates Registered</div>
          <div class="text-h4 font-weight-bold">{{ metrics.totalAlternates ?? '-' }}</div>
        </v-card>
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">Active Officers</div>
          <div class="text-h4 font-weight-bold">{{ metrics.activeOfficers ?? '-' }}</div>
        </v-card>
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">Pending Approvals</div>
          <div class="text-h4 font-weight-bold">{{ metrics.pendingPayrolls ?? '-' }}</div>
        </v-card>
      </v-col>
    </v-row>

    <!-- Organisation KPIs -->
    <v-row v-else-if="!loadError && auth.isOrganisation" class="metric-grid">
      <v-col cols="12" sm="6" md="3">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">My Households</div>
          <div class="text-h4 font-weight-bold">{{ metrics.totalHouseholds ?? '-' }}</div>
        </v-card>
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">Alternates Registered</div>
          <div class="text-h4 font-weight-bold">{{ metrics.totalAlternates ?? '-' }}</div>
        </v-card>
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">Registered Fingerprints</div>
          <div class="text-h4 font-weight-bold">{{ metrics.registeredFingerprints ?? '-' }}</div>
        </v-card>
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">Total Received</div>
          <div class="text-h4 font-weight-bold">{{ currency(metrics.combinedAmount) }}</div>
          <div class="text-caption">{{ currency(metrics.totalPaymentsReceivedAmount) }} cash + {{ currency(metrics.voucherRedeemedAmount) }} vouchers</div>
        </v-card>
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">Latest Payroll</div>
          <div class="text-h5 font-weight-bold">
            {{ metrics.latestPayroll ? metrics.latestPayroll.status : 'No cycles yet' }}
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
.metric-grid :deep(.v-card) { position: relative; overflow: hidden; min-height: 135px; padding: 22px !important; background: #fff !important; border-top: 3px solid #0d9488; }
.metric-grid :deep(.text-h4), .metric-grid :deep(.text-h5) { color: #0f172a; margin-top: 9px; letter-spacing: -.035em; }
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
}
</style>
