<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'
import LineChart from '@/components/LineChart.vue'
import BarChart from '@/components/BarChart.vue'

const auth = useAuthStore()
const router = useRouter()
const toast = useToast()

const loading = ref(true)
const metrics = ref<Record<string, any>>({})
const paymentsSeries = ref<{ label: string; value: number }[]>([])
const householdsSeries = ref<{ label: string; value: number }[]>([])

async function load() {
  loading.value = true
  try {
    const [m, p, h] = await Promise.all([
      dispatch<{ results: Record<string, any> }>('DASHBOARD_METRICS'),
      dispatch<{ results: { period: string; amount: number; count: number }[] }>('DASHBOARD_PAYMENTS_CHART'),
      dispatch<{ results: { period: string; count: number }[] }>('DASHBOARD_HOUSEHOLDS_CHART'),
    ])
    metrics.value = m.results
    paymentsSeries.value = p.results.map((r) => ({ label: r.period, value: r.amount }))
    householdsSeries.value = h.results.map((r) => ({ label: r.period, value: r.count }))
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load dashboard')
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
</script>

<template>
  <div class="dashboard-page">
    <div class="dashboard-heading d-flex align-center justify-space-between mb-6">
      <div>
        <div class="overline">Operations overview</div>
        <h1 class="text-h4 font-weight-bold">Welcome back, {{ auth.fullName }}</h1>
        <p>Track programme activity and keep every disbursement accountable.</p>
      </div>
      <div class="d-flex ga-2">
        <v-btn v-if="auth.isAnchor" color="primary" prepend-icon="mdi-domain-plus" @click="router.push('/app/organizations')">
          New Organization
        </v-btn>
        <v-btn v-if="auth.hasModule('CASH_TRANSFERS')" color="secondary" prepend-icon="mdi-calendar-cash" @click="router.push('/app/payroll')">
          Generate Payment Cycle
        </v-btn>
        <v-btn v-if="auth.hasModule('VOUCHERS')" color="secondary" variant="tonal" prepend-icon="mdi-ticket-plus" @click="router.push('/app/vouchers')">
          Issue Voucher
        </v-btn>
      </div>
    </div>

    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <!-- Anchor KPIs -->
    <v-row v-if="auth.isAnchor" class="metric-grid">
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
    <v-row v-else-if="auth.isOrganisation" class="metric-grid">
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

    <v-row class="mt-3 analytics-grid">
      <v-col cols="12" md="7">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-subtitle-1 font-weight-medium mb-2">Payment volume over time</div>
          <LineChart :data="paymentsSeries" value-prefix="SSP " />
        </v-card>
      </v-col>
      <v-col cols="12" md="5">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-subtitle-1 font-weight-medium mb-2">Household registration trend</div>
          <BarChart :data="householdsSeries" />
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

    <v-row v-if="auth.isAnchor && metrics.amountsByOrganisation?.length" class="mt-3">
      <v-col cols="12">
        <v-card variant="flat" border>
          <v-card-title class="text-subtitle-1">Amount generated by organisation</v-card-title>
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
                <td>{{ org.organisationName }}</td>
                <td class="text-right">{{ currency(org.paymentsAmount) }}</td>
                <td class="text-right">{{ currency(org.voucherAmount) }}</td>
                <td class="text-right font-weight-bold">{{ currency(org.totalAmount) }}</td>
              </tr>
            </tbody>
          </v-table>
        </v-card>
      </v-col>
    </v-row>

    <v-row v-if="auth.isAnchor && metrics.recentTransactions?.length" class="mt-3">
      <v-col cols="12">
        <v-card variant="flat" border>
          <v-card-title class="text-subtitle-1">Recent activity</v-card-title>
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
                <td>{{ tx.organisationCode }}</td>
                <td class="text-right">{{ currency(tx.amount) }}</td>
                <td>
                  <v-chip size="small" :color="tx.status === 1 ? 'success' : 'warning'" variant="tonal">
                    {{ tx.status === 1 ? 'Paid' : 'Pending' }}
                  </v-chip>
                </td>
                <td>{{ new Date(tx.createdAt).toLocaleDateString() }}</td>
              </tr>
            </tbody>
          </v-table>
        </v-card>
      </v-col>
    </v-row>
  </div>
</template>

<style scoped>
.dashboard-heading h1 { color: #0f172a; letter-spacing: -.03em; }
.dashboard-heading p { margin: 5px 0 0; color: #64748b; }
.overline { color: #0d9488; font-size: .72rem; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; margin-bottom: 6px; }
.metric-grid :deep(.v-card) { position: relative; overflow: hidden; min-height: 135px; padding: 22px !important; background: linear-gradient(135deg, #fff, #f0fdfa) !important; }
.metric-grid :deep(.v-card)::after { content: ''; position: absolute; width: 92px; height: 92px; right: -38px; bottom: -42px; border-radius: 50%; background: rgba(13, 148, 136, .12); }
.metric-grid :deep(.text-h4), .metric-grid :deep(.text-h5) { color: #134e4a; margin-top: 9px; letter-spacing: -.035em; }
.analytics-grid :deep(.v-card) { padding: 22px !important; }
</style>
