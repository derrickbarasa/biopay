<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useAnchorScope } from '@/composables/useAnchorScope'

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
  makerId?: number
}

interface PaymentLine {
  id: number
  uuid: string
  householdNumber: string
  householdName: string
  gender?: string | null
  bomaCode?: string | null
  amount: number
  currency?: string
  exchangeRate?: number
  amountOut?: number
  amountIn?: number
  status: number
  approved: number
  rejected?: number | null
  rejectionReason?: string | null
  createdAt?: string
}

const auth = useAuthStore()
const toast = useToast()
const router = useRouter()
const { confirmAction } = useConfirm()
const { anchors, selectedAnchorId, anchorGateActive } = useAnchorScope()

const loading = ref(true)
const cycles = ref<Cycle[]>([])
const tableSearch = ref('')
const organizations = ref<{ organisationCode: string; name: string; anchorId?: number }[]>([])
const statusFilter = ref<string | null>(null)
const organisationFilter = ref<string | null>(null)

// Both roles see everything in their scope immediately -- the backend already
// treats an unset anchor/organisation filter as "show all" (`IS NULL OR ...`),
// so the picker below narrows the view without ever blocking it.
const scopeReady = computed(() => true)

// Explicit widths (Vuetify applies each as the column's own inline style) instead of letting
// the table auto-shrink every column to fit the card: without them, a value as long as
// "PENDING_APPROVAL" or a two-date period got squeezed narrower than its own content and
// either clipped (the status chip) or wrapped across three lines (the period), and the whole
// row could end up wider than intended with the Actions column pushed past the visible edge.
// Fixed widths mean the table is exactly as wide as it needs to be and scrolls horizontally
// on a narrow viewport instead -- both v-table__wrapper (this table) and .view-table-scroll
// (the View dialog's table) already provide that scroll container.
const headers = [
  { title: 'Cycle', key: 'cycleCode', width: 190, nowrap: true },
  { title: 'Organization', key: 'organisationCode', width: 160, nowrap: true },
  { title: 'Period', key: 'period', width: 190, nowrap: true },
  { title: 'Households', key: 'householdCount', width: 90 },
  { title: 'Currency', key: 'currency', width: 90 },
  { title: 'Rate', key: 'exchangeRate', width: 90 },
  { title: 'Amount Out', key: 'amountOut', width: 110 },
  { title: 'Amount In', key: 'amountIn', width: 110 },
  { title: 'Status', key: 'status', width: 150, nowrap: true },
  { title: 'Actions', key: 'actions', width: 170, sortable: false, align: 'start' as const, nowrap: true },
]

async function load() {
  loading.value = true
  try {
    const res = await dispatch<{ results: Cycle[] }>('GET_PAYROLLS', {
      targetAnchorId: auth.isSystemAdmin ? selectedAnchorId.value : undefined,
      status: statusFilter.value ?? undefined,
      organisationCode: organisationFilter.value ?? undefined,
    })
    cycles.value = res.results
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load payroll cycles')
  } finally {
    loading.value = false
  }
}

watch([statusFilter, organisationFilter], load)

function clearFilters() {
  statusFilter.value = null
  organisationFilter.value = null
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

// Super Admin picking a different anchor resets whatever organisation was
// selected under the previous one, then reloads both lists.
watch(selectedAnchorId, () => { organisationFilter.value = null; loadOrganizations(); load() })

onMounted(() => {
  load()
  loadOrganizations()
})

const statusColor: Record<string, string> = {
  DRAFT: 'grey', PENDING_APPROVAL: 'warning', APPROVED: 'info', DISBURSED: 'success', REJECTED: 'error',
}

const orgNameByCode = computed(() => new Map(organizations.value.map((o) => [o.organisationCode, o.name])))
function orgName(code?: string) { return (code && orgNameByCode.value.get(code)) || code || '—' }
function fmtAmount(v?: number | null) { return (v ?? 0).toLocaleString() }

async function removeCycle(cycle: Cycle) {
  if (!await confirmAction({
    title: 'Delete payment cycle?',
    message: `${cycle.cycleCode} and its generated payment lines will be removed. This action cannot be undone.`,
    confirmLabel: 'Delete cycle',
    color: 'error',
  })) return
  try {
    await dispatch('DELETE_PAYROLL', { cycleCode: cycle.cycleCode })
    toast.success('Payroll cycle deleted')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Delete failed')
  }
}

function openWizard() {
  router.push({ name: 'payroll-generate' })
}

// ---- Approve flow (anchor only) -- line items load alongside so the maker's rejects
//      (per-row checkboxes below) can be sent before the rest of the cycle is approved ----
const approveDialog = ref(false)
const approveTarget = ref<Cycle | null>(null)
const approveOtp = ref('')
const approving = ref(false)
const sendingApproveOtp = ref(false)
const approveItems = ref<PaymentLine[]>([])
const approveItemsLoading = ref(false)
const approveRejectedIds = ref<Set<number>>(new Set())
const approveRejectReason = ref('')

async function openApprove(cycle: Cycle) {
  approveTarget.value = cycle
  approveOtp.value = ''
  approveRejectedIds.value = new Set()
  approveRejectReason.value = ''
  approveItems.value = []
  approveDialog.value = true
  approveItemsLoading.value = true
  try {
    const res = await dispatch<{ payments: PaymentLine[] }>('GET_PAYROLL', { cycleCode: cycle.cycleCode })
    approveItems.value = res.payments ?? []
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load line items')
  } finally {
    approveItemsLoading.value = false
  }
}

function toggleItemReject(id: number) {
  const next = new Set(approveRejectedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  approveRejectedIds.value = next
}

async function sendApproveOtp() {
  if (!approveTarget.value) return
  sendingApproveOtp.value = true
  try {
    await dispatch('REQUEST_PAYROLL_OTP', { action: 'APPROVE', cycleCode: approveTarget.value.cycleCode, actorEmail: auth.user?.email })
    toast.success('Verification code sent to ' + auth.user?.email)
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to send code')
  } finally {
    sendingApproveOtp.value = false
  }
}

async function confirmApprove() {
  if (!approveTarget.value) return
  approving.value = true
  try {
    if (approveRejectedIds.value.size) {
      await dispatch('REJECT_PAYROLL_ITEMS', {
        cycleCode: approveTarget.value.cycleCode,
        paymentIds: [...approveRejectedIds.value],
        reason: approveRejectReason.value,
      })
    }
    await dispatch('APPROVE_PAYROLL', { cycleCode: approveTarget.value.cycleCode, otpCode: approveOtp.value })
    toast.success('Payroll cycle approved')
    approveDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Approval failed')
  } finally {
    approving.value = false
  }
}

// The cycle's own generator (maker) can trim it down -- reject individual households they
// picked -- any time before a checker approves it, without needing anchor-admin approval rights.
function isMakerOf(cycle: Cycle) {
  return !!auth.user?.id && cycle.makerId === auth.user.id
}

async function saveRejections() {
  if (!approveTarget.value || !approveRejectedIds.value.size) return
  approving.value = true
  try {
    await dispatch('REJECT_PAYROLL_ITEMS', {
      cycleCode: approveTarget.value.cycleCode,
      paymentIds: [...approveRejectedIds.value],
      reason: approveRejectReason.value,
    })
    toast.success('Selected households removed from this cycle')
    approveDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to update cycle')
  } finally {
    approving.value = false
  }
}

async function disburse(cycle: Cycle) {
  if (!await confirmAction({
    title: 'Disburse payment cycle?',
    message: `${cycle.cycleCode} will be posted to the payment ledger for ${cycle.householdCount} household(s).`,
    confirmLabel: 'Disburse cycle',
    color: 'secondary',
  })) return
  try {
    await dispatch('DISBURSE_PAYROLL', { cycleCode: cycle.cycleCode })
    toast.success('Payroll cycle disbursed')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Disbursement failed')
  }
}

const rejectDialog = ref(false)
const rejectTarget = ref<Cycle | null>(null)
const rejectReason = ref('')
const rejecting = ref(false)

function openReject(cycle: Cycle) {
  rejectTarget.value = cycle
  rejectReason.value = ''
  rejectDialog.value = true
}

async function confirmReject() {
  if (!rejectTarget.value) return
  if (!rejectReason.value.trim()) {
    toast.error('Enter a reason for rejecting this payment cycle')
    return
  }
  rejecting.value = true
  try {
    await dispatch('REJECT_PAYROLL', { cycleCode: rejectTarget.value.cycleCode, reason: rejectReason.value.trim() })
    toast.success('Payroll cycle rejected')
    rejectDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Rejection failed')
  } finally {
    rejecting.value = false
  }
}

// ---- View more (read-only line-item panel, any cycle status) ----
const viewDialog = ref(false)
const viewTarget = ref<Cycle | null>(null)
const viewItems = ref<PaymentLine[]>([])
const viewLoading = ref(false)
// Distinct from "loaded fine, zero rows": GET_PAYROLL failing (e.g. the backend being
// unreachable) also leaves viewItems empty, and conflating the two showed a calm "No
// households in this cycle" for a cycle that actually does have households whose fetch just
// failed -- misleading enough to read as a real data problem instead of a request that needs retrying.
const viewError = ref(false)

async function openView(cycle: Cycle) {
  viewTarget.value = cycle
  viewItems.value = []
  viewError.value = false
  viewDialog.value = true
  viewLoading.value = true
  try {
    const res = await dispatch<{ payments: PaymentLine[] }>('GET_PAYROLL', { cycleCode: cycle.cycleCode })
    viewItems.value = res.payments ?? []
  } catch (err) {
    viewError.value = true
    toast.error(err instanceof Error ? err.message : 'Failed to load payments')
  } finally {
    viewLoading.value = false
  }
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
  if (item.approved) return 'info'
  return 'warning'
}
function genderLabel(gender?: string | null) {
  if (gender === 'M') return 'Male'
  if (gender === 'F') return 'Female'
  return '—'
}
// The line-item cycle_code column is anchor/organisation-scoped payment data, not a full
// household record -- send the maker/checker to the same household detail page HouseholdsPage
// itself links to, rather than duplicating that record's fields into this dialog.
function viewHousehold(line: PaymentLine) {
  viewDialog.value = false
  router.push({ name: 'household-detail', params: { householdNumber: line.householdNumber } })
}
</script>

<template>
  <div>
    <div class="d-flex align-center justify-space-between mb-4">
      <h1 class="text-h5 font-weight-bold">Payment Cycles</h1>
      <v-btn v-if="scopeReady && auth.can('ACCESS_PAYMENT_CYCLES')" color="secondary" prepend-icon="mdi-calendar-month-outline" @click="openWizard">Generate Payment Cycle</v-btn>
    </div>

    <template v-if="scopeReady">
    <v-card variant="flat" border>
      <v-card-text>
        <v-row dense align="center">
          <v-col v-if="anchorGateActive" cols="12" sm="4" md="3">
            <v-select v-model="selectedAnchorId" :items="anchors" item-title="name" item-value="id" label="Anchor" clearable hide-details density="compact" prepend-inner-icon="mdi-bank-outline" />
          </v-col>
          <v-col v-if="auth.isSystemAdmin || auth.isAnchorAdministrator" cols="12" sm="4" md="3">
            <v-select v-model="organisationFilter" :items="organizations" item-title="name" item-value="organisationCode" label="Organisation" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="6" sm="4" md="3">
            <v-select
              v-model="statusFilter"
              :items="['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'DISBURSED', 'REJECTED']"
              label="Status" clearable hide-details density="compact"
            />
          </v-col>
          <v-col cols="6" sm="4" md="3">
            <v-text-field v-model="tableSearch" prepend-inner-icon="mdi-magnify" label="Search" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="auto">
            <v-btn variant="text" size="small" @click="clearFilters">Clear filters</v-btn>
          </v-col>
        </v-row>
      </v-card-text>
      <v-data-table class="cycles-table" :headers="headers" :items="cycles" :search="tableSearch" :loading="loading">
        <template #item.organisationCode="{ item }">{{ orgName(item.organisationCode) }}</template>
        <template #item.period="{ item }">{{ item.periodStart }} – {{ item.periodEnd }}</template>
        <template #item.householdCount="{ item }"><span class="num-cell">{{ item.householdCount }}</span></template>
        <template #item.currency="{ item }">{{ item.currency ?? 'USD' }}</template>
        <template #item.exchangeRate="{ item }"><span class="num-cell">{{ item.exchangeRate ?? 1 }}</span></template>
        <template #item.amountOut="{ item }"><span class="num-cell">{{ fmtAmount(item.amountOut ?? item.totalAmount) }}</span></template>
        <template #item.amountIn="{ item }"><span class="num-cell">{{ fmtAmount(item.amountIn ?? item.totalAmount) }}</span></template>
        <template #item.status="{ item }">
          <v-tooltip v-if="item.status === 'REJECTED' && item.rejectionReason" :text="item.rejectionReason" location="top">
            <template #activator="{ props: tip }">
              <v-chip v-bind="tip" size="small" :color="statusColor[item.status] ?? 'grey'" variant="tonal">{{ item.status }}</v-chip>
            </template>
          </v-tooltip>
          <v-chip v-else size="small" :color="statusColor[item.status] ?? 'grey'" variant="tonal">{{ item.status }}</v-chip>
        </template>
        <!-- Icon-only actions, each named by a tooltip rather than an inline label: the label
             text ("Approve", "Disburse", ...) was what pushed this cell past the card's edge --
             up to four buttons wide before a row even reaches its status-dependent maximum. -->
        <template #item.actions="{ item }">
          <div class="actions-cell">
            <v-tooltip text="View details" location="top">
              <template #activator="{ props: tip }">
                <v-btn v-bind="tip" icon="mdi-eye-outline" variant="text" density="comfortable" size="small" :aria-label="`View ${item.cycleCode}`" @click="openView(item)" />
              </template>
            </v-tooltip>
            <template v-if="auth.isAnchor && item.status === 'PENDING_APPROVAL' && auth.can('ACCESS_PAYMENT_CYCLES')">
              <v-tooltip text="Approve" location="top">
                <template #activator="{ props: tip }">
                  <v-btn v-bind="tip" icon="mdi-check-circle-outline" variant="tonal" color="success" density="comfortable" size="small" :aria-label="`Approve ${item.cycleCode}`" @click="openApprove(item)" />
                </template>
              </v-tooltip>
              <v-tooltip text="Reject" location="top">
                <template #activator="{ props: tip }">
                  <v-btn v-bind="tip" icon="mdi-close-circle-outline" variant="tonal" color="error" density="comfortable" size="small" :aria-label="`Reject ${item.cycleCode}`" @click="openReject(item)" />
                </template>
              </v-tooltip>
            </template>
            <v-tooltip v-if="!auth.isAnchor && item.status === 'PENDING_APPROVAL' && isMakerOf(item)" text="Review" location="top">
              <template #activator="{ props: tip }">
                <v-btn v-bind="tip" icon="mdi-clipboard-text-search-outline" variant="tonal" density="comfortable" size="small" :aria-label="`Review ${item.cycleCode}`" @click="openApprove(item)" />
              </template>
            </v-tooltip>
            <v-tooltip v-if="auth.isAnchor && auth.can('ACCESS_PAYMENT_CYCLES') && item.status === 'APPROVED'" text="Disburse" location="top">
              <template #activator="{ props: tip }">
                <v-btn v-bind="tip" icon="mdi-cash-fast" variant="tonal" color="secondary" density="comfortable" size="small" :aria-label="`Disburse ${item.cycleCode}`" @click="disburse(item)" />
              </template>
            </v-tooltip>
            <v-tooltip v-if="auth.can('ACCESS_PAYMENT_CYCLES') && (item.status === 'DRAFT' || item.status === 'PENDING_APPROVAL')" text="Delete" location="top">
              <template #activator="{ props: tip }">
                <v-btn v-bind="tip" icon="mdi-delete-outline" variant="text" density="comfortable" size="small" color="error" :aria-label="`Delete cycle ${item.cycleCode}`" @click="removeCycle(item)" />
              </template>
            </v-tooltip>
          </div>
        </template>
      </v-data-table>
    </v-card>
    </template>

    <!-- Approve dialog: per-row reject checkboxes apply before the rest of the cycle is approved -->
    <v-dialog v-model="approveDialog" max-width="640">
      <v-card v-if="approveTarget">
        <dialog-close-button @close="approveDialog = false" />
        <v-card-title>{{ auth.isAnchor ? 'Approve' : 'Review' }} {{ approveTarget.cycleCode }}</v-card-title>
        <v-card-text>
          <div v-if="!auth.isAnchor" class="text-body-2 text-medium-emphasis mb-3">
            Uncheck any households you no longer want in this cycle before it goes to your anchor for approval.
          </div>
          <div class="mb-3">
            {{ approveTarget.householdCount }} households · Total {{ approveTarget.currency ?? 'USD' }} {{ fmtAmount(approveTarget.totalAmount) }} out
          </div>

          <div v-if="approveItemsLoading" class="d-flex justify-center my-4"><v-progress-circular indeterminate color="secondary" /></div>
          <v-table v-else density="compact" class="mb-3" style="max-height: 280px; overflow-y: auto">
            <thead>
              <tr>
                <th>Reject</th>
                <th>Household</th>
                <th class="text-right">Amount out</th>
                <th class="text-right">Amount in</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="line in approveItems" :key="line.id">
                <td><v-checkbox-btn :model-value="approveRejectedIds.has(line.id)" @update:model-value="toggleItemReject(line.id)" /></td>
                <td>{{ line.householdName }} ({{ line.householdNumber }})</td>
                <td class="text-right">{{ fmtAmount(line.amountOut ?? line.amount) }}</td>
                <td class="text-right">{{ fmtAmount(line.amountIn ?? line.amount) }}</td>
              </tr>
            </tbody>
          </v-table>
          <v-text-field
            v-if="approveRejectedIds.size"
            v-model="approveRejectReason" label="Reason for rejecting the checked households" class="mb-2"
          />

          <template v-if="auth.isAnchor">
            <v-alert type="info" variant="tonal" density="compact" class="mb-3">
              An approval link was emailed when this cycle was submitted. To approve here, request an OTP and enter it below.
            </v-alert>
            <v-btn color="secondary" block class="mb-3" :loading="sendingApproveOtp" @click="sendApproveOtp">
              Send OTP code
            </v-btn>
            <v-text-field v-model="approveOtp" label="Verification code" placeholder="6-digit code" maxlength="6" />
          </template>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="approveDialog = false">Cancel</v-btn>
          <v-btn v-if="auth.isAnchor" color="secondary" :loading="approving" :disabled="!approveOtp" @click="confirmApprove">Approve</v-btn>
          <v-btn v-else color="secondary" :loading="approving" :disabled="!approveRejectedIds.size" @click="saveRejections">Save changes</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="rejectDialog" max-width="460" persistent>
      <v-card v-if="rejectTarget">
        <dialog-close-button @close="rejectDialog = false" />
        <v-card-title>Reject payment cycle?</v-card-title>
        <v-card-text>
          <p class="mb-3">Explain why {{ rejectTarget.cycleCode }} cannot proceed. This reason is kept with the cycle.</p>
          <v-textarea v-model="rejectReason" label="Rejection reason" placeholder="Why this cycle cannot proceed" rows="3" autofocus required />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="rejectDialog = false">Cancel</v-btn>
          <v-btn color="error" :loading="rejecting" @click="confirmReject">Reject cycle</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- View more: full household-level breakdown for any cycle status -->
    <v-dialog v-model="viewDialog" max-width="820">
      <v-card v-if="viewTarget">
        <dialog-close-button @close="viewDialog = false" />
        <v-card-title>{{ viewTarget.cycleCode }} — Payments</v-card-title>
        <v-card-subtitle class="pb-0">{{ orgName(viewTarget.organisationCode) }} · {{ viewTarget.periodStart }} – {{ viewTarget.periodEnd }}</v-card-subtitle>
        <v-card-text>
          <dl class="view-summary">
            <div class="view-summary-item">
              <dt>Households</dt>
              <dd class="num-cell">{{ viewTarget.householdCount }}</dd>
            </div>
            <div class="view-summary-item">
              <dt>Currency</dt>
              <dd>{{ viewTarget.currency ?? 'USD' }} <span class="view-summary-muted">rate {{ viewTarget.exchangeRate ?? 1 }}</span></dd>
            </div>
            <div class="view-summary-item">
              <dt>Amount out</dt>
              <dd class="num-cell">{{ fmtAmount(viewTarget.amountOut ?? viewTarget.totalAmount) }}</dd>
            </div>
            <div class="view-summary-item">
              <dt>Amount in</dt>
              <dd class="num-cell">{{ fmtAmount(viewTarget.amountIn ?? viewTarget.totalAmount) }}</dd>
            </div>
          </dl>

          <div v-if="viewLoading" class="d-flex justify-center my-6"><v-progress-circular indeterminate color="secondary" /></div>
          <v-alert v-else-if="viewError" type="error" variant="tonal" density="compact">
            Couldn't load this cycle's households.
            <template #append>
              <v-btn variant="text" size="small" @click="openView(viewTarget)">Retry</v-btn>
            </template>
          </v-alert>
          <v-alert v-else-if="!viewItems.length" type="info" variant="tonal" density="compact">No households in this cycle.</v-alert>
          <div v-else class="view-table-scroll">
            <v-table density="compact" style="max-height: 420px; overflow-y: auto">
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
                <tr v-for="line in viewItems" :key="line.id">
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
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="viewDialog = false">Close</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
/* This table is wider than its card on most screens now that every column has a real,
   unsqueezed width (see the `headers` comment above), so it scrolls horizontally inside
   Vuetify's own `.v-table__wrapper` -- but style.css hides scrollbars everywhere by default
   (deliberately, app-wide) so nothing hints that the Actions column, off to the right, exists
   at all. DefaultLayout.vue's own `.dashboard-main` already carries this exact override for
   the app's main vertical scroll for the same reason ("a thin neutral scrollbar so long pages
   remain discoverable", per DESIGN.md) -- mirrored here for this table's horizontal one. */
.cycles-table :deep(.v-table__wrapper) {
  scrollbar-width: thin;
  scrollbar-color: #94a3b8 transparent;
}
.cycles-table :deep(.v-table__wrapper)::-webkit-scrollbar { display: block; height: 9px; }
.cycles-table :deep(.v-table__wrapper)::-webkit-scrollbar-track { background: transparent; }
.cycles-table :deep(.v-table__wrapper)::-webkit-scrollbar-thumb { border: 2px solid transparent; border-radius: 999px; background: #94a3b8; background-clip: padding-box; }
.cycles-table :deep(.v-table__wrapper)::-webkit-scrollbar-thumb:hover { background: #64748b; background-clip: padding-box; }

.actions-cell {
  display: flex;
  align-items: center;
  gap: 2px;
  white-space: nowrap;
}

/* Amounts, counts and rates line up under a stable width as their digits change --
   see DESIGN.md's "Data Stays Still" rule. */
.num-cell {
  font-variant-numeric: tabular-nums;
}

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
