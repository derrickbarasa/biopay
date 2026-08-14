<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'
import { downloadCsv, parseCsv, toCsv } from '@/utils/csv'

interface VoucherRow {
  voucherCode: string
  householdNumber: string
  organisationCode: string
  amount: number
  purpose?: string
  status: 'ISSUED' | 'REDEEMED' | 'VOID'
  expiresAt?: string
  redeemedAt?: string
  createdAt: string
}

const toast = useToast()
const loading = ref(true)
const householdFilter = ref('')
const statusFilter = ref<string | null>(null)
const vouchers = ref<VoucherRow[]>([])
const summary = ref<Record<string, any>>({})
let householdDebounce: ReturnType<typeof setTimeout> | null = null

function onHouseholdFilterInput() {
  if (householdDebounce) clearTimeout(householdDebounce)
  householdDebounce = setTimeout(load, 400)
}

const issueDialog = ref(false)
const bulkDialog = ref(false)
const redeemDialog = ref(false)
const saving = ref(false)
const redeeming = ref<VoucherRow | null>(null)
const redeemNotes = ref('')

const form = ref({ householdNumber: '', amount: null as number | null, purpose: '', expiresAt: '' })

const bulkPurpose = ref('')
const bulkExpiresAt = ref('')
const bulkFileName = ref('')
const bulkRows = ref<{ householdNumber: string; amount: number }[]>([])
const bulkErrors = ref<{ row: number; message: string }[]>([])

const headers = [
  { title: 'Voucher Code', key: 'voucherCode' },
  { title: 'Household', key: 'householdNumber' },
  { title: 'Amount', key: 'amount' },
  { title: 'Status', key: 'status' },
  { title: 'Expires', key: 'expiresAt' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'end' as const },
]

function currency(v: number | undefined) {
  return (v ?? 0).toLocaleString(undefined, { style: 'currency', currency: 'SSP', maximumFractionDigits: 0 })
}

async function load() {
  loading.value = true
  try {
    const [v, s] = await Promise.all([
      dispatch<{ results: VoucherRow[] }>('GET_VOUCHERS', {
        pageSize: 100, status: statusFilter.value, householdNumber: householdFilter.value || undefined,
      }),
      dispatch<{ results: Record<string, any> }>('VOUCHER_SUMMARY'),
    ])
    vouchers.value = v.results
    summary.value = s.results
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load vouchers')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openIssue() {
  form.value = { householdNumber: '', amount: null, purpose: '', expiresAt: '' }
  issueDialog.value = true
}

async function issue() {
  if (!form.value.householdNumber.trim() || !form.value.amount) {
    toast.error('Household number and amount are required')
    return
  }
  saving.value = true
  try {
    await dispatch('CREATE_VOUCHER', form.value)
    toast.success('Voucher issued')
    issueDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to issue voucher')
  } finally {
    saving.value = false
  }
}

function openBulk() {
  bulkPurpose.value = ''
  bulkExpiresAt.value = ''
  bulkFileName.value = ''
  bulkRows.value = []
  bulkErrors.value = []
  bulkDialog.value = true
}

function downloadBulkTemplate() {
  downloadCsv('voucher-issue-template.csv', toCsv(['householdNumber', 'amount'], [['HH-EXAMPLE-0001', '5000']]))
}

function onBulkFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  bulkFileName.value = file.name
  const reader = new FileReader()
  reader.onload = () => {
    const parsed = parseCsv(String(reader.result ?? ''))
    bulkRows.value = parsed
      .map((r) => ({ householdNumber: r.householdNumber, amount: Number(r.amount) }))
      .filter((r) => r.householdNumber)
  }
  reader.readAsText(file)
}

async function submitBulk() {
  if (!bulkRows.value.length) {
    toast.error('Upload a CSV with at least one row')
    return
  }
  saving.value = true
  try {
    const res = await dispatch<{ successCount: number; failureCount: number; errors: { row: number; message: string }[] }>(
      'BULK_ISSUE_VOUCHERS',
      { rows: bulkRows.value, purpose: bulkPurpose.value || undefined, expiresAt: bulkExpiresAt.value || undefined },
    )
    bulkErrors.value = res.errors ?? []
    toast.success(`${res.successCount} voucher(s) issued, ${res.failureCount} failed`)
    if (!bulkErrors.value.length) bulkDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Bulk issue failed')
  } finally {
    saving.value = false
  }
}

function openRedeem(row: VoucherRow) {
  redeeming.value = row
  redeemNotes.value = ''
  redeemDialog.value = true
}

async function confirmRedeem() {
  if (!redeeming.value) return
  saving.value = true
  try {
    await dispatch('REDEEM_VOUCHER', { voucherCode: redeeming.value.voucherCode, notes: redeemNotes.value || undefined })
    toast.success('Voucher redeemed')
    redeemDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Redemption failed')
  } finally {
    saving.value = false
  }
}

async function voidVoucher(row: VoucherRow) {
  try {
    await dispatch('VOID_VOUCHER', { voucherCode: row.voucherCode })
    toast.success('Voucher voided')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to void voucher')
  }
}

function statusColor(status: string) {
  return status === 'REDEEMED' ? 'success' : status === 'VOID' ? 'error' : 'warning'
}
</script>

<template>
  <div>
    <div class="d-flex align-center justify-space-between mb-4">
      <h1 class="text-h5 font-weight-bold">Vouchers</h1>
      <div class="d-flex ga-2">
        <v-btn variant="outlined" prepend-icon="mdi-file-upload" @click="openBulk">Bulk Issue</v-btn>
        <v-btn color="primary" prepend-icon="mdi-ticket-plus" @click="openIssue">Issue Voucher</v-btn>
      </div>
    </div>

    <v-row class="mb-2">
      <v-col cols="12" sm="4">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">Issued (awaiting redemption)</div>
          <div class="text-h5 font-weight-bold">{{ currency(summary.issuedAmount) }}</div>
          <div class="text-caption">{{ summary.issuedCount ?? 0 }} vouchers</div>
        </v-card>
      </v-col>
      <v-col cols="12" sm="4">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">Redeemed</div>
          <div class="text-h5 font-weight-bold">{{ currency(summary.redeemedAmount) }}</div>
          <div class="text-caption">{{ summary.redeemedCount ?? 0 }} vouchers</div>
        </v-card>
      </v-col>
      <v-col cols="12" sm="4">
        <v-card class="pa-4" variant="flat" border>
          <div class="text-caption text-medium-emphasis">Voided</div>
          <div class="text-h5 font-weight-bold">{{ summary.voidCount ?? 0 }}</div>
        </v-card>
      </v-col>
    </v-row>

    <v-card variant="flat" border>
      <v-card-text class="d-flex ga-3">
        <v-text-field
          v-model="householdFilter" prepend-inner-icon="mdi-magnify" label="Household number" clearable
          hide-details density="compact" style="max-width: 240px" @update:model-value="onHouseholdFilterInput" @click:clear="load"
        />
        <v-select v-model="statusFilter" :items="['ISSUED', 'REDEEMED', 'VOID']" label="Status" clearable hide-details density="compact" style="max-width: 200px" @update:model-value="load" />
      </v-card-text>
      <v-data-table :headers="headers" :items="vouchers" :loading="loading">
        <template #item.amount="{ item }">{{ currency(item.amount) }}</template>
        <template #item.status="{ item }">
          <v-chip size="small" :color="statusColor(item.status)" variant="tonal">{{ item.status }}</v-chip>
        </template>
        <template #item.expiresAt="{ item }">{{ item.expiresAt ? new Date(item.expiresAt).toLocaleDateString() : '—' }}</template>
        <template #item.actions="{ item }">
          <v-btn v-if="item.status === 'ISSUED'" size="small" variant="text" color="success" @click="openRedeem(item)">Redeem</v-btn>
          <v-btn v-if="item.status === 'ISSUED'" size="small" variant="text" color="error" @click="voidVoucher(item)">Void</v-btn>
        </template>
      </v-data-table>
    </v-card>

    <v-dialog v-model="issueDialog" max-width="480">
      <v-card>
        <v-card-title>Issue Voucher</v-card-title>
        <v-card-text>
          <v-text-field v-model="form.householdNumber" label="Household number" />
          <v-text-field v-model.number="form.amount" label="Amount" type="number" />
          <v-text-field v-model="form.purpose" label="Purpose (optional)" />
          <v-text-field v-model="form.expiresAt" label="Expires on (optional)" type="date" />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="issueDialog = false">Cancel</v-btn>
          <v-btn color="primary" :loading="saving" @click="issue">Issue</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="bulkDialog" max-width="560">
      <v-card>
        <v-card-title>Bulk Issue Vouchers</v-card-title>
        <v-card-text>
          <v-alert type="info" variant="tonal" density="compact" class="mb-3">
            Download the template, fill in one row per household, then upload it here.
          </v-alert>
          <v-btn variant="outlined" size="small" prepend-icon="mdi-download" class="mb-4" @click="downloadBulkTemplate">
            Download Template
          </v-btn>
          <v-file-input label="Upload filled CSV" accept=".csv" prepend-icon="mdi-file-upload" @change="onBulkFile" />
          <div v-if="bulkRows.length" class="text-caption mb-2">{{ bulkRows.length }} row(s) ready to issue from {{ bulkFileName }}</div>
          <v-text-field v-model="bulkPurpose" label="Purpose (applies to all, optional)" />
          <v-text-field v-model="bulkExpiresAt" label="Expires on (applies to all, optional)" type="date" />
          <v-alert v-if="bulkErrors.length" type="warning" variant="tonal" density="compact" class="mt-2">
            {{ bulkErrors.length }} row(s) failed:
            <div v-for="e in bulkErrors.slice(0, 5)" :key="e.row">Row {{ e.row }}: {{ e.message }}</div>
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="bulkDialog = false">Close</v-btn>
          <v-btn color="primary" :loading="saving" :disabled="!bulkRows.length" @click="submitBulk">Issue All</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="redeemDialog" max-width="440">
      <v-card v-if="redeeming">
        <v-card-title>Redeem Voucher</v-card-title>
        <v-card-text>
          <p class="mb-3">
            Confirm redemption of <strong>{{ currency(redeeming.amount) }}</strong> for household
            <strong>{{ redeeming.householdNumber }}</strong> (voucher {{ redeeming.voucherCode }}).
          </p>
          <v-textarea v-model="redeemNotes" label="Notes (optional)" rows="2" />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="redeemDialog = false">Cancel</v-btn>
          <v-btn color="success" :loading="saving" @click="confirmRedeem">Confirm Redemption</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
