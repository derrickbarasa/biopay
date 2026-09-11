<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'

interface Cycle {
  cycleCode: string
  organisationCode: string
  periodStart: string
  periodEnd: string
  amountPerHousehold: number
  householdCount: number
  totalAmount: number
  currency?: string
  exchangeRate?: number
  amountOut?: number
  amountIn?: number
  status: string
  rejectionReason?: string
  makerAt?: string
  checkerAt?: string
  disbursedAt?: string
  createdAt?: string
}

interface PaymentLine {
  id: number
  uuid: string
  householdNumber: string
  householdName: string
  gender?: string | null
  bomaCode?: string | null
  amount: number
  amountOut?: number
  amountIn?: number
  status: number
  approved: number
  rejected?: number | null
  rejectionReason?: string | null
}

interface Organization { organisationCode: string; name: string }

const route = useRoute()
const router = useRouter()
const toast = useToast()

const cycleCode = computed(() => String(route.params.cycleCode ?? ''))
const cycle = ref<Cycle | null>(null)
const items = ref<PaymentLine[]>([])
const organizations = ref<Organization[]>([])
const loading = ref(true)
// Kept separate from "loaded fine, zero rows" -- a fetch failure must not read as an empty cycle.
const loadError = ref(false)

const statusColor: Record<string, string> = {
  DRAFT: 'grey', PENDING_APPROVAL: 'warning', APPROVED: 'warning', DISBURSED: 'success', REJECTED: 'error',
}

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const res = await dispatch<{ results: Cycle[]; payments: PaymentLine[] }>('GET_PAYROLL', { cycleCode: cycleCode.value })
    cycle.value = res.results?.[0] ?? null
    items.value = res.payments ?? []
    if (!cycle.value) loadError.value = true
  } catch (err) {
    loadError.value = true
    toast.error(err instanceof Error ? err.message : 'Failed to load payment cycle')
  } finally {
    loading.value = false
  }
}

async function loadOrganizations() {
  try {
    const res = await dispatch<{ results: Organization[] }>('GET_ORGANIZATIONS')
    organizations.value = res.results
  } catch {
    // Organisation column just falls back to showing the raw code.
  }
}

onMounted(() => { load(); loadOrganizations() })

const orgNameByCode = computed(() => new Map(organizations.value.map((o) => [o.organisationCode, o.name])))
function orgName(code?: string) { return (code && orgNameByCode.value.get(code)) || code || '—' }
function fmtAmount(v?: number | null) { return (v ?? 0).toLocaleString() }
function fmtDate(v?: string | null) {
  if (!v) return '—'
  const date = new Date(v)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString()
}
function genderLabel(gender?: string | null) {
  if (gender === 'M') return 'Male'
  if (gender === 'F') return 'Female'
  return '—'
}
function itemStatusText(item: PaymentLine) {
  if (item.rejected) return 'Rejected'
  if (item.status === 1) return 'Disbursed'
  if (item.approved) return 'Approved'
  return 'Pending'
}
function itemStatusColor(item: PaymentLine) {
  if (item.rejected) return 'error'
  if (item.status === 1) return 'success'
  if (item.approved) return 'warning'
  return 'grey'
}
// A payment line isn't a full household record, so send to the household detail page instead.
function viewHousehold(line: PaymentLine) {
  router.push({ name: 'household-detail', params: { householdNumber: line.householdNumber } })
}
function goToList() {
  router.push({ name: 'payroll' })
}
</script>

<template>
  <div>
    <div class="d-flex align-center ga-2 mb-4">
      <v-btn icon="mdi-arrow-left" variant="text" density="comfortable" aria-label="Back to Payment Cycles" @click="goToList" />
      <h1 class="page-title">Payment Cycle</h1>
    </div>

    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <v-alert v-if="!loading && loadError" type="error" variant="tonal">
      Couldn't load this payment cycle.
      <template #append>
        <v-btn variant="text" size="small" @click="load">Retry</v-btn>
      </template>
    </v-alert>

    <v-card v-else-if="!loading && cycle" variant="flat" border class="view-card" :class="`view-card--${(cycle.status || '').toLowerCase()}`">
      <v-card-title class="d-flex align-center ga-2 flex-wrap">
        {{ cycle.cycleCode }} — Payments
        <v-chip size="small" :color="statusColor[cycle.status] ?? 'grey'" variant="flat">{{ cycle.status }}</v-chip>
      </v-card-title>
      <v-card-subtitle class="pb-0">{{ orgName(cycle.organisationCode) }} · {{ cycle.periodStart }} – {{ cycle.periodEnd }}</v-card-subtitle>
      <v-card-text>
        <v-alert v-if="cycle.status === 'REJECTED' && cycle.rejectionReason" type="error" variant="tonal" density="compact" class="mb-3">
          Rejected: {{ cycle.rejectionReason }}
        </v-alert>
        <dl class="view-summary">
          <div class="view-summary-item">
            <dt>Households</dt>
            <dd class="num-cell">{{ cycle.householdCount }}</dd>
          </div>
          <div class="view-summary-item">
            <dt>Currency</dt>
            <dd>{{ cycle.currency ?? 'USD' }} <span class="view-summary-muted">rate {{ cycle.exchangeRate ?? 1 }}</span></dd>
          </div>
          <div class="view-summary-item">
            <dt>Amount out</dt>
            <dd class="num-cell">{{ fmtAmount(cycle.amountOut ?? cycle.totalAmount) }}</dd>
          </div>
          <div class="view-summary-item">
            <dt>Amount in</dt>
            <dd class="num-cell">{{ fmtAmount(cycle.amountIn ?? cycle.totalAmount) }}</dd>
          </div>
          <div class="view-summary-item">
            <dt>Generated</dt>
            <dd>{{ fmtDate(cycle.createdAt ?? cycle.makerAt) }}</dd>
          </div>
          <div class="view-summary-item">
            <dt>Approved</dt>
            <dd>{{ cycle.checkerAt ? fmtDate(cycle.checkerAt) : '—' }}</dd>
          </div>
          <div class="view-summary-item">
            <dt>Disbursed</dt>
            <dd>{{ cycle.disbursedAt ? fmtDate(cycle.disbursedAt) : '—' }}</dd>
          </div>
        </dl>

        <v-alert v-if="!items.length" type="info" variant="tonal" density="compact">No households in this cycle.</v-alert>
        <div v-else class="view-table-scroll">
          <v-table density="compact">
            <thead>
              <tr>
                <th>Household</th>
                <th>Village</th>
                <th>Gender</th>
                <th class="text-right">Amount out</th>
                <th class="text-right">Amount in</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="line in items" :key="line.id">
                <td>
                  <button type="button" class="household-link" @click="viewHousehold(line)">{{ line.householdName }}</button>
                  <div class="view-summary-muted">{{ line.householdNumber }}</div>
                </td>
                <td>{{ line.bomaCode || '—' }}</td>
                <td>{{ genderLabel(line.gender) }}</td>
                <td class="text-right num-cell">{{ fmtAmount(line.amountOut ?? line.amount) }}</td>
                <td class="text-right num-cell">{{ fmtAmount(line.amountIn ?? line.amount) }}</td>
                <td>
                  <v-tooltip v-if="line.rejected && line.rejectionReason" :text="line.rejectionReason" location="top">
                    <template #activator="{ props: tip }">
                      <v-chip v-bind="tip" size="small" :color="itemStatusColor(line)" variant="tonal">{{ itemStatusText(line) }}</v-chip>
                    </template>
                  </v-tooltip>
                  <v-chip v-else size="small" :color="itemStatusColor(line)" variant="tonal">{{ itemStatusText(line) }}</v-chip>
                </td>
              </tr>
            </tbody>
          </v-table>
        </div>
      </v-card-text>
    </v-card>
  </div>
</template>

<style scoped>
.num-cell { font-variant-numeric: tabular-nums; }

/* Vuetify's own elevated-card rule sets border-color with a two-class selector
 * (higher specificity than a single status modifier class), so plain
 * border-top-color here loses the cascade and silently falls back to Vuetify's
 * neutral default -- !important is the same escape hatch ui-fixes.css already
 * uses throughout for the same reason (overriding a more-specific Vuetify rule). */
.view-card { border-top: 4px solid #94a3b8 !important; }
.view-card--pending_approval { border-top-color: #f59e0b !important; }
.view-card--approved { border-top-color: #f59e0b !important; background: linear-gradient(180deg, #fff7ed 0, #fff 90px); }
.view-card--disbursed { border-top-color: #16a34a !important; background: linear-gradient(180deg, #f0fdf4 0, #fff 90px); }
.view-card--rejected { border-top-color: #dc2626 !important; }

.view-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 16px;
  padding: 12px 14px;
  margin: 0 0 16px;
  background: #F8FAFC;
  border: 1px solid #E2E8F0;
  border-radius: 10px;
}
.view-summary-item { min-width: 0; }
.view-summary dt {
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  color: #64748B;
  margin-bottom: 2px;
}
.view-summary dd {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 700;
  color: #0F172A;
}
.view-summary-muted {
  font-size: 0.78rem;
  font-weight: 400;
  color: #64748B;
}

.view-table-scroll {
  max-width: 100%;
  overflow-x: auto;
}

.household-link {
  background: none;
  border: none;
  padding: 0;
  font: inherit;
  font-weight: 600;
  color: #0F766E;
  cursor: pointer;
  text-align: left;
}
.household-link:hover,
.household-link:focus-visible {
  text-decoration: underline;
}
</style>
