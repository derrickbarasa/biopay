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
  amount: number
  currency?: string
  exchangeRate?: number
  amountOut?: number
  amountIn?: number
  status: number
  approved: number
  rejected?: number | null
  rejectionReason?: string | null
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

const headers = [
  { title: 'Cycle', key: 'cycleCode' },
  { title: 'Organization', key: 'organisationCode' },
  { title: 'Period', key: 'period' },
  { title: 'Households', key: 'householdCount' },
  { title: 'Currency', key: 'currency' },
  { title: 'Rate', key: 'exchangeRate' },
  { title: 'Amount Out', key: 'amountOut' },
  { title: 'Amount In', key: 'amountIn' },
  { title: 'Status', key: 'status' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'start' as const },
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

async function openView(cycle: Cycle) {
  viewTarget.value = cycle
  viewItems.value = []
  viewDialog.value = true
  viewLoading.value = true
  try {
    const res = await dispatch<{ payments: PaymentLine[] }>('GET_PAYROLL', { cycleCode: cycle.cycleCode })
    viewItems.value = res.payments ?? []
  } catch (err) {
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
      <v-data-table :headers="headers" :items="cycles" :search="tableSearch" :loading="loading">
        <template #item.organisationCode="{ item }">{{ orgName(item.organisationCode) }}</template>
        <template #item.period="{ item }">{{ item.periodStart }} – {{ item.periodEnd }}</template>
        <template #item.currency="{ item }">{{ item.currency ?? 'USD' }}</template>
        <template #item.exchangeRate="{ item }">{{ item.exchangeRate ?? 1 }}</template>
        <template #item.amountOut="{ item }">{{ fmtAmount(item.amountOut ?? item.totalAmount) }}</template>
        <template #item.amountIn="{ item }">{{ fmtAmount(item.amountIn ?? item.totalAmount) }}</template>
        <template #item.status="{ item }">
          <v-tooltip v-if="item.status === 'REJECTED' && item.rejectionReason" :text="item.rejectionReason" location="top">
            <template #activator="{ props: tip }">
              <v-chip v-bind="tip" size="small" :color="statusColor[item.status] ?? 'grey'" variant="tonal">{{ item.status }}</v-chip>
            </template>
          </v-tooltip>
          <v-chip v-else size="small" :color="statusColor[item.status] ?? 'grey'" variant="tonal">{{ item.status }}</v-chip>
        </template>
        <template #item.actions="{ item }">
          <v-btn icon="mdi-eye-outline" variant="text" size="small" class="mr-1" :aria-label="`View ${item.cycleCode}`" @click="openView(item)" />
          <template v-if="auth.isAnchor && item.status === 'PENDING_APPROVAL'">
            <v-btn v-if="auth.can('ACCESS_PAYMENT_CYCLES')" size="small" color="success" variant="tonal" class="mr-1" @click="openApprove(item)">Approve</v-btn>
            <v-btn v-if="auth.can('ACCESS_PAYMENT_CYCLES')" size="small" color="error" variant="tonal" @click="openReject(item)">Reject</v-btn>
          </template>
          <v-btn
            v-if="!auth.isAnchor && item.status === 'PENDING_APPROVAL' && isMakerOf(item)"
            size="small" variant="tonal" @click="openApprove(item)"
          >
            Review
          </v-btn>
          <v-btn v-if="auth.isAnchor && auth.can('ACCESS_PAYMENT_CYCLES') && item.status === 'APPROVED'" size="small" color="secondary" variant="tonal" @click="disburse(item)">
            Disburse
          </v-btn>
          <v-btn
            v-if="auth.can('ACCESS_PAYMENT_CYCLES') && (item.status === 'DRAFT' || item.status === 'PENDING_APPROVAL')"
            icon="mdi-delete" variant="text" size="small" color="error"
            :aria-label="`Delete cycle ${item.cycleCode}`" @click="removeCycle(item)"
          />
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
            <v-btn color="secondary" block class="mb-3" :loading="sendingApproveOtp" @click="sendApproveOtp">
              Send Verification Code
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

    <!-- View more: read-only line items for any cycle status -->
    <v-dialog v-model="viewDialog" max-width="640">
      <v-card v-if="viewTarget">
        <dialog-close-button @close="viewDialog = false" />
        <v-card-title>{{ viewTarget.cycleCode }} — Payments</v-card-title>
        <v-card-text>
          <div class="mb-3">
            {{ viewTarget.householdCount }} households · {{ viewTarget.currency ?? 'USD' }} rate {{ viewTarget.exchangeRate ?? 1 }} ·
            Out {{ fmtAmount(viewTarget.amountOut ?? viewTarget.totalAmount) }} · In {{ fmtAmount(viewTarget.amountIn ?? viewTarget.totalAmount) }}
          </div>
          <div v-if="viewLoading" class="d-flex justify-center my-4"><v-progress-circular indeterminate color="secondary" /></div>
          <v-table v-else density="compact" style="max-height: 360px; overflow-y: auto">
            <thead>
              <tr>
                <th>Household</th>
                <th class="text-right">Amount out</th>
                <th class="text-right">Amount in</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="line in viewItems" :key="line.id">
                <td>{{ line.householdName }} ({{ line.householdNumber }})</td>
                <td class="text-right">{{ fmtAmount(line.amountOut ?? line.amount) }}</td>
                <td class="text-right">{{ fmtAmount(line.amountIn ?? line.amount) }}</td>
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
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="viewDialog = false">Close</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
