<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { formatCurrency } from '@/utils/currency'

interface AnchorPrice {
  anchorId: number
  anchorName?: string
  amount: number | null
  currency: string | null
  hasOverride: boolean
}

interface PaymentRequest {
  id: number
  anchorId: number
  anchorName?: string
  reference: string
  method: 'CARD' | 'MOBILE_MONEY' | 'CASH' | 'PUSH_LINK'
  mobileProvider?: string
  phoneNumber?: string
  amount: number
  currency: string
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED'
  comment?: string
  initiatedBy?: string
  createdAt: string
}

const auth = useAuthStore()
const toast = useToast()
const { confirmAction } = useConfirm()
const loading = ref(true)
const saving = ref(false)
const anchors = ref<AnchorPrice[]>([])
const requests = ref<PaymentRequest[]>([])
const loadingRequests = ref(true)
const actingOnRequest = ref<number | null>(null)

const CURRENCIES = ['USD', 'KES', 'UGX', 'SSP', 'ETB', 'TZS', 'RWF', 'NGN', 'XAF', 'GBP', 'EUR']

const headers = [
  { title: 'Anchor Name', key: 'anchorName' },
  { title: 'Price', key: 'price' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'start' as const },
]

const requestHeaders = [
  { title: 'Anchor Name', key: 'anchorName' },
  { title: 'Method', key: 'method' },
  { title: 'Amount', key: 'amount' },
  { title: 'Reference', key: 'reference' },
  { title: 'Requested', key: 'createdAt' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'start' as const },
]

const editDialog = ref(false)
const editForm = ref({ anchorId: null as number | null, anchorName: '', amount: null as number | null, currency: 'USD' })

async function load() {
  loading.value = true
  try {
    // Pricing is per-anchor only -- the platform-wide default this endpoint also returns
    // is deliberately unused here (see backend Subscription.java's currentPrice(), which
    // still falls back to it defensively if an anchor is ever paid against before its own
    // price is set), matching "price is per agreement, set per anchor."
    const res = await dispatch<{ results: { anchors: AnchorPrice[] } }>('GET_ALL_SUBSCRIPTION_PRICES')
    anchors.value = res.results.anchors ?? []
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load billing settings')
  } finally {
    loading.value = false
  }
}

async function loadRequests() {
  loadingRequests.value = true
  try {
    const res = await dispatch<{ results: PaymentRequest[] }>('GET_SUBSCRIPTION_PAYMENT_REQUESTS')
    requests.value = (res.results ?? []).filter((r) => r.status === 'PENDING')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load pending payments')
  } finally {
    loadingRequests.value = false
  }
}
onMounted(() => { load(); loadRequests() })

function openEdit(anchor: AnchorPrice) {
  editForm.value = {
    anchorId: anchor.anchorId, anchorName: anchor.anchorName ?? '',
    amount: anchor.amount, currency: anchor.currency ?? 'USD',
  }
  editDialog.value = true
}

async function saveAnchorPrice() {
  if (editForm.value.amount == null || editForm.value.amount < 0) {
    toast.error('Enter a non-negative amount')
    return
  }
  saving.value = true
  try {
    await dispatch('SET_SUBSCRIPTION_PRICE', {
      targetAnchorId: editForm.value.anchorId, amount: editForm.value.amount, currency: editForm.value.currency, actorEmail: auth.user?.email,
    })
    toast.success('Subscription price saved')
    editDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Save failed')
  } finally {
    saving.value = false
  }
}

function methodLabel(request: PaymentRequest) {
  if (request.method === 'MOBILE_MONEY') return `Mobile Money (${request.mobileProvider ?? '—'})`
  if (request.method === 'PUSH_LINK') return 'Push Payment Link'
  return request.method.charAt(0) + request.method.slice(1).toLowerCase()
}

async function confirmRequest(request: PaymentRequest) {
  if (!await confirmAction({
    title: 'Confirm this payment?',
    message: `Marks ${formatCurrency(request.amount, request.currency)} from ${request.anchorName} as received and renews their subscription by one month. Only confirm once the money has actually been seen (bank statement, mobile money till, etc.).`,
    confirmLabel: 'Confirm payment',
  })) return
  actingOnRequest.value = request.id
  try {
    await dispatch('CONFIRM_SUBSCRIPTION_PAYMENT', { requestId: request.id, actorEmail: auth.user?.email })
    toast.success('Payment confirmed and subscription renewed')
    await loadRequests()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Confirmation failed')
  } finally {
    actingOnRequest.value = null
  }
}

async function cancelRequest(request: PaymentRequest) {
  if (!await confirmAction({
    title: 'Cancel this payment request?',
    message: `Removes the pending ${formatCurrency(request.amount, request.currency)} request from ${request.anchorName}'s queue.`,
    confirmLabel: 'Cancel request',
    color: 'error',
  })) return
  actingOnRequest.value = request.id
  try {
    await dispatch('CANCEL_SUBSCRIPTION_PAYMENT', { requestId: request.id })
    toast.success('Payment request cancelled')
    await loadRequests()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Cancel failed')
  } finally {
    actingOnRequest.value = null
  }
}
</script>

<template>
  <div>
    <div class="mb-4">
      <h1 class="page-title">Billing</h1>
      <p class="text-body-2 text-medium-emphasis mt-1">
        Subscription pricing is per your agreement with each anchor. Set the price an anchor pays to renew below.
      </p>
    </div>

    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <v-card variant="flat" border class="mb-5">
      <v-card-title class="text-subtitle-1 font-weight-bold">Pending Payments</v-card-title>
      <p class="text-caption text-medium-emphasis px-4 pb-2">
        No payment gateway is connected yet, so Card, Mobile Money and Push Payment Link requests land here as pending
        until you confirm the money has actually been received off-platform, then the anchor's subscription renews.
      </p>
      <v-data-table :headers="requestHeaders" :items="requests" :loading="loadingRequests" item-value="id">
        <template #item.method="{ item }">{{ methodLabel(item) }}</template>
        <template #item.amount="{ item }">{{ formatCurrency(item.amount, item.currency) }}</template>
        <template #item.createdAt="{ item }">{{ new Date(item.createdAt).toLocaleString() }}</template>
        <template #item.actions="{ item }">
          <v-btn variant="text" size="small" color="secondary" :loading="actingOnRequest === item.id" @click="confirmRequest(item)">Confirm</v-btn>
          <v-btn variant="text" size="small" color="error" :loading="actingOnRequest === item.id" @click="cancelRequest(item)">Cancel</v-btn>
        </template>
        <template #no-data>
          <div class="text-center text-medium-emphasis py-6">No pending payments.</div>
        </template>
      </v-data-table>
    </v-card>

    <v-card variant="flat" border>
      <v-card-title class="text-subtitle-1 font-weight-bold">Anchor Prices</v-card-title>
      <v-data-table :headers="headers" :items="anchors" :loading="loading" item-value="anchorId">
        <template #item.price="{ item }">
          <span v-if="item.hasOverride">{{ formatCurrency(item.amount, item.currency ?? 'USD') }}</span>
          <span v-else class="text-medium-emphasis">Not set</span>
        </template>
        <template #item.actions="{ item }">
          <v-btn variant="text" size="small" :prepend-icon="item.hasOverride ? 'mdi-pencil-outline' : 'mdi-plus'" @click="openEdit(item)">
            {{ item.hasOverride ? 'Edit Price' : 'Add Price' }}
          </v-btn>
        </template>
        <template #no-data>
          <div class="text-center text-medium-emphasis py-6">No anchors found.</div>
        </template>
      </v-data-table>
    </v-card>

    <v-dialog v-model="editDialog" max-width="420">
      <v-card>
        <dialog-close-button @close="editDialog = false" />
        <v-card-title>Set Subscription Price</v-card-title>
        <v-card-text>
          <p class="text-body-2 text-medium-emphasis mb-3">{{ editForm.anchorName }}</p>
          <v-row dense>
            <v-col cols="7"><v-text-field v-model.number="editForm.amount" label="Amount" type="number" density="compact" /></v-col>
            <v-col cols="5"><v-select v-model="editForm.currency" :items="CURRENCIES" label="Currency" density="compact" /></v-col>
          </v-row>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="flat" color="error" @click="editDialog = false">Cancel</v-btn>
          <v-btn variant="flat" color="secondary" :loading="saving" @click="saveAnchorPrice">Save</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
