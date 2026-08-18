<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

interface Cycle {
  cycleCode: string
  organisationCode: string
  periodStart: string
  periodEnd: string
  amountPerHousehold: number
  householdCount: number
  totalAmount: number
  status: string
  rejectionReason?: string
}

const auth = useAuthStore()
const toast = useToast()

const loading = ref(true)
const cycles = ref<Cycle[]>([])
const tableSearch = ref('')
const organizations = ref<{ organisationCode: string; name: string }[]>([])
const statusFilter = ref<string | null>(null)
const organisationFilter = ref<string | null>(null)

const headers = [
  { title: 'Cycle', key: 'cycleCode' },
  { title: 'Organization', key: 'organisationCode' },
  { title: 'Period', key: 'period' },
  { title: 'Households', key: 'householdCount' },
  { title: 'Total', key: 'totalAmount' },
  { title: 'Status', key: 'status' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'end' as const },
]

async function load() {
  loading.value = true
  try {
    const res = await dispatch<{ results: Cycle[] }>('GET_PAYROLLS', {
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

onMounted(async () => {
  load()
  if (auth.isAnchor) {
    try {
      const res = await dispatch<{ results: typeof organizations.value }>('GET_ORGANIZATIONS')
      organizations.value = res.results
    } catch {
      // Filter dropdown just stays empty; the list itself still loaded above.
    }
  }
})

const statusColor: Record<string, string> = {
  DRAFT: 'grey', PENDING_APPROVAL: 'warning', APPROVED: 'info', DISBURSED: 'success', REJECTED: 'error',
}

// ---- Generate wizard ----
const wizard = ref(false)
const step = ref(1)
const genForm = ref({ periodStart: '', periodEnd: '', amountPerHousehold: null as number | null, otpCode: '' })
const otpSent = ref(false)
const sendingOtp = ref(false)
const generating = ref(false)

function openWizard() {
  step.value = 1
  genForm.value = { periodStart: '', periodEnd: '', amountPerHousehold: null, otpCode: '' }
  otpSent.value = false
  wizard.value = true
}

async function sendGenerateOtp() {
  sendingOtp.value = true
  try {
    await dispatch('REQUEST_PAYROLL_OTP', { action: 'GENERATE', actorEmail: auth.user?.email })
    otpSent.value = true
    toast.success('Verification code sent to ' + auth.user?.email)
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to send code')
  } finally {
    sendingOtp.value = false
  }
}

async function confirmGenerate() {
  generating.value = true
  try {
    await dispatch('GENERATE_PAYROLL', genForm.value)
    toast.success('Payroll cycle generated and pending approval')
    wizard.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to generate payroll')
  } finally {
    generating.value = false
  }
}

// ---- Approve flow (anchor only) ----
const approveDialog = ref(false)
const approveTarget = ref<Cycle | null>(null)
const approveOtp = ref('')
const approving = ref(false)
const sendingApproveOtp = ref(false)

function openApprove(cycle: Cycle) {
  approveTarget.value = cycle
  approveOtp.value = ''
  approveDialog.value = true
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

async function disburse(cycle: Cycle) {
  try {
    await dispatch('DISBURSE_PAYROLL', { cycleCode: cycle.cycleCode })
    toast.success('Payroll cycle disbursed')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Disbursement failed')
  }
}

async function reject(cycle: Cycle) {
  const reason = window.prompt('Reason for rejecting this payroll cycle?') ?? ''
  try {
    await dispatch('REJECT_PAYROLL', { cycleCode: cycle.cycleCode, reason })
    toast.success('Payroll cycle rejected')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Rejection failed')
  }
}
</script>

<template>
  <div>
    <div class="d-flex align-center justify-space-between mb-4">
      <h1 class="text-h5 font-weight-bold">Payment Cycles</h1>
      <v-btn color="primary" prepend-icon="mdi-calendar-month-outline" @click="openWizard">Generate Payment Cycle</v-btn>
    </div>

    <v-card variant="flat" border>
      <v-card-text>
        <v-row dense align="center">
          <v-col v-if="auth.isAnchor" cols="12" sm="4" md="3">
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
        <template #item.period="{ item }">{{ item.periodStart }} – {{ item.periodEnd }}</template>
        <template #item.totalAmount="{ item }">{{ (item.totalAmount ?? 0).toLocaleString() }}</template>
        <template #item.status="{ item }">
          <v-chip size="small" :color="statusColor[item.status] ?? 'grey'" variant="tonal">{{ item.status }}</v-chip>
        </template>
        <template #item.actions="{ item }">
          <template v-if="auth.isAnchor && item.status === 'PENDING_APPROVAL'">
            <v-btn size="small" color="success" variant="tonal" class="mr-1" @click="openApprove(item)">Approve</v-btn>
            <v-btn size="small" color="error" variant="tonal" @click="reject(item)">Reject</v-btn>
          </template>
          <v-btn v-if="auth.isAnchor && item.status === 'APPROVED'" size="small" color="primary" variant="tonal" @click="disburse(item)">
            Disburse
          </v-btn>
        </template>
      </v-data-table>
    </v-card>

    <!-- Generate wizard -->
    <v-dialog v-model="wizard" max-width="520" persistent>
      <v-card>
        <v-card-title>Generate Payment Cycle</v-card-title>
        <v-card-text>
          <v-stepper v-model="step" flat :items="['Period', 'Amount', 'Verify & Confirm']">
            <template #item.1>
              <v-text-field v-model="genForm.periodStart" label="Period start" type="date" />
              <v-text-field v-model="genForm.periodEnd" label="Period end" type="date" />
              <v-btn color="primary" block :disabled="!genForm.periodStart || !genForm.periodEnd" @click="step = 2">Next</v-btn>
            </template>
            <template #item.2>
              <v-text-field v-model.number="genForm.amountPerHousehold" label="Amount per household" type="number" />
              <v-btn color="primary" block :disabled="!genForm.amountPerHousehold" @click="step = 3">Next</v-btn>
            </template>
            <template #item.3>
              <v-alert type="info" variant="tonal" density="compact" class="mb-3">
                A verification code will be emailed to {{ auth.user?.email }} before this cycle is generated.
              </v-alert>
              <v-btn v-if="!otpSent" color="secondary" block :loading="sendingOtp" @click="sendGenerateOtp">Send Verification Code</v-btn>
              <template v-else>
                <v-text-field v-model="genForm.otpCode" label="Verification code" maxlength="6" />
                <v-btn color="primary" block :loading="generating" :disabled="!genForm.otpCode" @click="confirmGenerate">
                  Confirm & Generate
                </v-btn>
              </template>
            </template>
          </v-stepper>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="wizard = false">Cancel</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Approve dialog -->
    <v-dialog v-model="approveDialog" max-width="440">
      <v-card v-if="approveTarget">
        <v-card-title>Approve {{ approveTarget.cycleCode }}</v-card-title>
        <v-card-text>
          <div class="mb-3">
            {{ approveTarget.householdCount }} households · Total {{ (approveTarget.totalAmount ?? 0).toLocaleString() }}
          </div>
          <v-btn color="secondary" block class="mb-3" :loading="sendingApproveOtp" @click="sendApproveOtp">
            Send Verification Code
          </v-btn>
          <v-text-field v-model="approveOtp" label="Verification code" maxlength="6" />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="approveDialog = false">Cancel</v-btn>
          <v-btn color="primary" :loading="approving" :disabled="!approveOtp" @click="confirmApprove">Approve</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
