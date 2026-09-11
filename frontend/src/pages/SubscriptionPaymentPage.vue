<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { formatCurrency } from '@/utils/currency'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToast()

function queryString(key: string): string {
  const raw = route.query[key]
  return typeof raw === 'string' ? raw : Array.isArray(raw) && typeof raw[0] === 'string' ? raw[0] : ''
}
const queryAnchorId = computed(() => {
  const raw = queryString('anchorId')
  const n = raw ? Number(raw) : NaN
  return Number.isFinite(n) ? n : null
})
const queryAnchorName = computed(() => queryString('anchorName'))

// The platform owner always pays FOR a specific anchor (picked from Subscription's Actions
// column, carried here via ?anchorId=); an anchor administrator always pays for themself.
const adminMode = computed(() => auth.isSystemAdmin)
const targetAnchorId = computed(() => (adminMode.value ? queryAnchorId.value : null))
const ready = computed(() => !adminMode.value || targetAnchorId.value != null)

const loadingPrice = ref(true)
const price = ref<{ amount: number; currency: string; isDefault: boolean } | null>(null)

async function loadPrice() {
  if (!ready.value) {
    loadingPrice.value = false
    return
  }
  loadingPrice.value = true
  try {
    const res = await dispatch<{ results: { amount: number; currency: string; isDefault: boolean } }>(
      'GET_SUBSCRIPTION_PRICE', adminMode.value ? { targetAnchorId: targetAnchorId.value } : {},
    )
    price.value = res.results
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load the subscription price')
  } finally {
    loadingPrice.value = false
  }
}
onMounted(loadPrice)

// ---- Anchor self-serve: Card / Mobile Money -----------------------------------------
type AnchorMethod = 'CARD' | 'MOBILE_MONEY' | null
const anchorMethod = ref<AnchorMethod>(null)
const MOBILE_PROVIDERS = [
  { value: 'MPESA', label: 'M-Pesa' },
  { value: 'AIRTEL', label: 'Airtel Money' },
  { value: 'MTN', label: 'MTN MoMo' },
]
const mobileProvider = ref<string | null>(null)
const phoneDigits = ref('')
// Card fields are display-only -- no gateway is wired up yet (see backend Subscription.java's
// class doc), so nothing here is ever transmitted; the request records method=CARD with no
// card data at all. Never wire these into the submit payload without a real PCI-compliant
// tokenization flow (e.g. Stripe Elements) in front of them.
const cardNumber = ref('')
const cardExpiry = ref('')
const cardCvv = ref('')
const cardName = ref('')
const submittingAnchor = ref(false)
const anchorResult = ref<{ reference: string; message: string } | null>(null)

async function submitAnchorPayment() {
  if (anchorMethod.value === 'MOBILE_MONEY') {
    if (!mobileProvider.value) { toast.error('Choose a mobile money provider'); return }
    if (!/^\d{7,12}$/.test(phoneDigits.value)) { toast.error('Enter a valid phone number'); return }
  } else if (anchorMethod.value === 'CARD') {
    if (!cardNumber.value.trim() || !cardExpiry.value.trim() || !cardCvv.value.trim() || !cardName.value.trim()) {
      toast.error('Complete the card details')
      return
    }
  } else {
    return
  }
  submittingAnchor.value = true
  try {
    const res = await dispatch<{ responseMessage: string; results: { reference: string } }>('CREATE_SUBSCRIPTION_PAYMENT_REQUEST', {
      method: anchorMethod.value,
      mobileProvider: anchorMethod.value === 'MOBILE_MONEY' ? mobileProvider.value : undefined,
      phoneNumber: anchorMethod.value === 'MOBILE_MONEY' ? `+254${phoneDigits.value}` : undefined,
      actorEmail: auth.user?.email,
    })
    anchorResult.value = { reference: res.results.reference, message: res.responseMessage }
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Payment failed')
  } finally {
    submittingAnchor.value = false
  }
}

// ---- Platform owner: Cash / Push Payment Link ----------------------------------------
type AdminMethod = 'CASH' | 'PUSH_LINK' | null
const adminMethod = ref<AdminMethod>(null)
const submittingCash = ref(false)

// Amount is never user-editable here -- it's always whatever the platform owner set for
// this anchor in Billing, regardless of which payment method is used.
const priceReady = computed(() => !!price.value && price.value.amount > 0)

const pushForm = ref({ comment: '', otpCode: '' })
const pushOtpSent = ref(false)
const sendingPushOtp = ref(false)
const submittingPush = ref(false)

async function submitCash() {
  if (!targetAnchorId.value || !price.value) return
  if (!priceReady.value) { toast.error('No subscription price has been set for this anchor yet'); return }
  submittingCash.value = true
  try {
    await dispatch('RENEW_SUBSCRIPTION', { targetAnchorId: targetAnchorId.value })
    toast.success('Subscription renewed')
    router.push('/app/subscription')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Renewal failed')
  } finally {
    submittingCash.value = false
  }
}

async function sendPushOtp() {
  sendingPushOtp.value = true
  try {
    await dispatch('REQUEST_SUBSCRIPTION_PUSH_OTP', { actorEmail: auth.user?.email })
    pushOtpSent.value = true
    toast.success('Verification code sent to your email')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to send verification code')
  } finally {
    sendingPushOtp.value = false
  }
}

async function submitPushLink() {
  if (!targetAnchorId.value || !price.value) return
  if (!priceReady.value) { toast.error('No subscription price has been set for this anchor yet'); return }
  if (!pushForm.value.comment.trim()) { toast.error('Add a comment describing this request'); return }
  if (!pushForm.value.otpCode.trim()) { toast.error('Enter the verification code sent to your email'); return }
  submittingPush.value = true
  try {
    const res = await dispatch<{ responseMessage: string }>('SEND_SUBSCRIPTION_PUSH_PAYMENT_LINK', {
      targetAnchorId: targetAnchorId.value,
      comment: pushForm.value.comment, otpCode: pushForm.value.otpCode, actorEmail: auth.user?.email,
    })
    toast.success(res.responseMessage || 'Payment link sent')
    router.push('/app/subscription')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to send payment link')
  } finally {
    submittingPush.value = false
  }
}

const anchorLabel = computed(() => adminMode.value ? queryAnchorName.value || `Anchor #${targetAnchorId.value}` : auth.fullName)
</script>

<template>
  <div>
    <div class="d-flex align-center ga-2 mb-4">
      <v-btn icon="mdi-arrow-left" variant="text" density="comfortable" aria-label="Back to Subscription" @click="router.push('/app/subscription')" />
      <div>
        <h1 class="page-title">Make Payment</h1>
        <p class="text-body-2 text-medium-emphasis mt-1">{{ adminMode ? `Renew the subscription for ${anchorLabel}` : 'Renew your anchor\'s subscription' }}</p>
      </div>
    </div>

    <v-alert v-if="adminMode && !targetAnchorId" type="warning" variant="tonal" class="mb-4">
      No anchor was selected. Go back to Subscription and choose Make Payment on the anchor you want to renew.
    </v-alert>

    <template v-else>
      <v-progress-linear v-if="loadingPrice" indeterminate color="primary" class="mb-4" />

      <div v-if="!loadingPrice" class="payment-layout">
        <!-- Left: method picker + amount summary -->
        <v-card variant="flat" border class="pa-5 payment-picker">
          <div class="amount-summary mb-4">
            <div class="amount-label">Amount Due</div>
            <div class="amount-value">{{ formatCurrency(price?.amount, price?.currency ?? 'USD') }}</div>
          </div>
          <h2 class="text-subtitle-1 font-weight-bold mb-3">Payment Method</h2>
          <div class="method-list">
            <template v-if="!adminMode">
              <button type="button" class="method-card" :class="{ active: anchorMethod === 'CARD' }" @click="anchorMethod = 'CARD'; anchorResult = null">
                <v-icon icon="mdi-credit-card-outline" /> Card
              </button>
              <button type="button" class="method-card" :class="{ active: anchorMethod === 'MOBILE_MONEY' }" @click="anchorMethod = 'MOBILE_MONEY'; anchorResult = null">
                <v-icon icon="mdi-cellphone-check" /> Mobile Money
              </button>
            </template>
            <template v-else>
              <button type="button" class="method-card" :class="{ active: adminMethod === 'CASH' }" @click="adminMethod = 'CASH'">
                <v-icon icon="mdi-cash" /> Cash
              </button>
              <button type="button" class="method-card" :class="{ active: adminMethod === 'PUSH_LINK' }" @click="adminMethod = 'PUSH_LINK'">
                <v-icon icon="mdi-link-variant" /> Push Payment Link
              </button>
            </template>
          </div>
        </v-card>

        <!-- Right: dynamic content for whichever method is selected -->
        <v-card variant="flat" border class="pa-6 payment-content">
          <template v-if="!adminMode">
            <div v-if="anchorResult" class="text-center py-8">
              <v-icon icon="mdi-clock-check-outline" size="48" color="primary" class="mb-3" />
              <h2 class="text-h6 font-weight-bold mb-2">Payment submitted</h2>
              <p class="text-body-2 text-medium-emphasis mb-1">{{ anchorResult.message }}</p>
              <p class="text-caption text-medium-emphasis">Reference: {{ anchorResult.reference }}</p>
              <v-btn class="mt-4" variant="text" prepend-icon="mdi-arrow-left" @click="router.push('/app/subscription')">Back to Subscription</v-btn>
            </div>
            <p v-else-if="!anchorMethod" class="make-payments-sentence">Make Payments</p>
            <div v-else-if="anchorMethod === 'CARD'">
              <h2 class="text-subtitle-1 font-weight-bold mb-3">Card Checkout</h2>
              <v-text-field v-model="cardName" label="Name on card" density="compact" class="mb-1" />
              <v-text-field v-model="cardNumber" label="Card number" density="compact" class="mb-1" inputmode="numeric" maxlength="19" />
              <v-row dense>
                <v-col cols="6"><v-text-field v-model="cardExpiry" label="MM/YY" density="compact" maxlength="5" /></v-col>
                <v-col cols="6"><v-text-field v-model="cardCvv" label="CVV" density="compact" type="password" maxlength="4" /></v-col>
              </v-row>
              <v-text-field :model-value="formatCurrency(price?.amount, price?.currency ?? 'USD')" label="Amount" density="compact" readonly class="mt-2" />
              <v-alert v-if="!priceReady" type="warning" variant="tonal" density="compact" class="mt-2">
                No subscription price has been set yet. Contact BioPay to set one up.
              </v-alert>
              <v-btn block color="secondary" class="mt-3" :disabled="!priceReady" :loading="submittingAnchor" @click="submitAnchorPayment">Proceed to Pay</v-btn>
            </div>
            <div v-else>
              <h2 class="text-subtitle-1 font-weight-bold mb-3">Mobile Money</h2>
              <div class="provider-row mb-4">
                <button
                  v-for="p in MOBILE_PROVIDERS" :key="p.value" type="button" class="provider-card"
                  :class="{ active: mobileProvider === p.value }" @click="mobileProvider = p.value"
                >{{ p.label }}</button>
              </div>
              <v-text-field v-model="phoneDigits" label="Phone number" prefix="+254" density="compact" inputmode="numeric" maxlength="10" placeholder="712345678" />
              <v-text-field :model-value="formatCurrency(price?.amount, price?.currency ?? 'USD')" label="Amount" density="compact" readonly class="mt-2" />
              <v-alert v-if="!priceReady" type="warning" variant="tonal" density="compact" class="mt-2">
                No subscription price has been set yet. Contact BioPay to set one up.
              </v-alert>
              <v-btn block color="secondary" class="mt-3" :disabled="!priceReady" :loading="submittingAnchor" @click="submitAnchorPayment">Proceed to Pay</v-btn>
            </div>
          </template>

          <template v-else>
            <p v-if="!adminMethod" class="make-payments-sentence">Make Payments</p>
            <div v-else-if="adminMethod === 'CASH'">
              <h2 class="text-subtitle-1 font-weight-bold mb-3">Cash Payment</h2>
              <v-alert v-if="!priceReady" type="warning" variant="tonal" density="compact" class="mb-3">
                No subscription price has been set for this anchor yet. Set one in Billing before renewing.
              </v-alert>
              <v-text-field :model-value="formatCurrency(price?.amount, price?.currency ?? 'USD')" label="Amount" density="compact" readonly />
              <v-btn block color="secondary" class="mt-3" :disabled="!priceReady" :loading="submittingCash" @click="submitCash">Renew Subscription</v-btn>
            </div>
            <div v-else>
              <h2 class="text-subtitle-1 font-weight-bold mb-3">Push Payment Link</h2>
              <v-alert v-if="!priceReady" type="warning" variant="tonal" density="compact" class="mb-3">
                No subscription price has been set for this anchor yet. Set one in Billing before sending a link.
              </v-alert>
              <v-text-field :model-value="formatCurrency(price?.amount, price?.currency ?? 'USD')" label="Amount" density="compact" readonly />
              <v-textarea v-model="pushForm.comment" label="Comment (sent to the anchor)" rows="2" density="compact" class="mt-2" />
              <div class="d-flex align-center ga-2 mt-2">
                <v-btn v-if="!pushOtpSent" variant="outlined" size="small" :loading="sendingPushOtp" @click="sendPushOtp">Authenticate</v-btn>
                <template v-else>
                  <v-text-field v-model="pushForm.otpCode" label="Verification code" density="compact" hide-details maxlength="8" />
                  <v-btn variant="text" size="small" :loading="sendingPushOtp" @click="sendPushOtp">Resend</v-btn>
                </template>
              </div>
              <v-btn block color="secondary" class="mt-3" :disabled="!pushOtpSent || !priceReady" :loading="submittingPush" @click="submitPushLink">Confirm and Submit</v-btn>
            </div>
          </template>
        </v-card>
      </div>
    </template>
  </div>
</template>

<style scoped>
.payment-layout { display: grid; grid-template-columns: 300px minmax(0, 1fr); gap: 16px; align-items: start; }
.payment-content { min-height: 320px; }
.make-payments-sentence { padding: 96px 0; text-align: center; color: #64748b; font-size: 1.1rem; font-weight: 600; }
.provider-row { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.provider-card {
  padding: 12px 8px; border: 1px solid #0f172a; border-radius: 10px; background: #fff;
  color: #0f172a; font-size: .82rem; font-weight: 700; cursor: pointer; transition: background 150ms ease, color 150ms ease;
}
.provider-card.active { background: #0d9488; border-color: #0d9488; color: #fff; }
.method-list { display: grid; gap: 10px; }
.method-card {
  display: flex; align-items: center; gap: 10px; padding: 14px; border: 1px solid #0f172a; border-radius: 10px;
  background: #fff; color: #0f172a; font-size: .88rem; font-weight: 700; cursor: pointer; transition: background 150ms ease, color 150ms ease;
}
.method-card.active { background: #f59e0b; border-color: #f59e0b; color: #0f172a; }
.amount-summary { padding: 14px; border: 1px solid #e2e8f0; border-radius: 10px; background: #f8fafc; }
.amount-label { color: #64748b; font-size: .68rem; font-weight: 700; letter-spacing: .04em; text-transform: uppercase; }
.amount-value { margin-top: 4px; color: #0f766e; font-size: 1.3rem; font-weight: 800; font-variant-numeric: tabular-nums; }
@media (max-width: 760px) {
  .payment-layout { grid-template-columns: 1fr; }
}
</style>
