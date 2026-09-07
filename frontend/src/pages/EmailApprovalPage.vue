<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { dispatch } from '@/api/client'

interface ApprovalDetails {
  requestType: 'PAYROLL' | 'HOUSEHOLD'
  referenceCode: string
  title: string
  summary: string
  expiresAt?: string
  state: 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED' | 'SUPERSEDED' | 'UNAVAILABLE'
  canApprove: boolean
}

interface ApprovalResponse {
  responseCode: string
  responseMessage?: string
  results: ApprovalDetails
}

const route = useRoute()
const token = computed(() => typeof route.query.token === 'string' ? route.query.token.trim() : '')
const details = ref<ApprovalDetails | null>(null)
const loading = ref(true)
const confirming = ref(false)
const errorMessage = ref('')

const actionLabel = computed(() => details.value?.requestType === 'HOUSEHOLD'
  ? 'Approve household'
  : 'Approve payment cycle')

const stateMessage = computed(() => {
  if (!details.value) return ''
  switch (details.value.state) {
    case 'APPROVED': return 'This request has been approved.'
    case 'REJECTED': return 'This request was rejected in BioPay.'
    case 'SUPERSEDED': return 'A newer approval link was issued for this request.'
    case 'EXPIRED': return 'This approval link has expired.'
    default: return 'This approval request is no longer available.'
  }
})

async function loadApproval() {
  errorMessage.value = ''
  details.value = null
  if (!token.value) {
    errorMessage.value = 'This approval link is missing its secure token.'
    loading.value = false
    return
  }
  loading.value = true
  try {
    const response = await dispatch<ApprovalResponse>('GET_EMAIL_APPROVAL', { token: token.value })
    details.value = response.results
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'This approval link could not be opened.'
  } finally {
    loading.value = false
  }
}

async function confirmApproval() {
  if (!token.value || !details.value?.canApprove) return
  errorMessage.value = ''
  confirming.value = true
  try {
    const response = await dispatch<ApprovalResponse>('CONFIRM_EMAIL_APPROVAL', { token: token.value })
    details.value = response.results
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'The request could not be approved.'
    await loadApproval()
  } finally {
    confirming.value = false
  }
}

onMounted(loadApproval)
</script>

<template>
  <v-app>
    <v-main class="approval-page">
      <v-container class="fill-height py-8" fluid>
        <v-row justify="center" align="center">
          <v-col cols="12" sm="9" md="6" lg="5" xl="4">
            <v-card elevation="0" class="approval-card">
              <div class="approval-banner">
                <router-link to="/" aria-label="BioPay home">
                  <img src="/biopay_logo_horizontal_light.svg" alt="BioPay" class="approval-logo" />
                </router-link>
              </div>

              <v-card-text class="pa-6 pa-sm-8">
                <div v-if="loading" class="text-center py-10" aria-live="polite">
                  <v-progress-circular indeterminate color="primary" size="42" />
                  <p class="text-body-2 text-medium-emphasis mt-4 mb-0">Checking this approval request…</p>
                </div>

                <template v-else-if="details">
                  <div class="status-icon" :class="details.canApprove ? 'status-icon--pending' : 'status-icon--complete'">
                    <v-icon :icon="details.canApprove ? 'mdi-shield-check-outline' : 'mdi-check-circle-outline'" size="32" />
                  </div>
                  <p class="eyebrow mb-2">Secure approval</p>
                  <h1 class="approval-title">{{ details.title }}</h1>
                  <p v-if="details.canApprove" class="text-body-2 text-medium-emphasis mt-2 mb-5">
                    Review the details below, then confirm your decision. Opening this page has not approved anything.
                  </p>
                  <v-alert v-else type="success" variant="tonal" density="comfortable" class="mt-4 mb-5">
                    {{ stateMessage }}
                  </v-alert>

                  <dl class="approval-details">
                    <div>
                      <dt>Reference</dt>
                      <dd>{{ details.referenceCode }}</dd>
                    </div>
                    <div v-if="details.summary">
                      <dt>Request</dt>
                      <dd>{{ details.summary }}</dd>
                    </div>
                  </dl>

                  <v-alert v-if="errorMessage" type="error" variant="tonal" density="compact" class="mt-5">
                    {{ errorMessage }}
                  </v-alert>

                  <v-btn
                    v-if="details.canApprove"
                    block color="secondary" size="large" class="mt-6"
                    prepend-icon="mdi-check-circle-outline"
                    :loading="confirming" @click="confirmApproval"
                  >
                    {{ actionLabel }}
                  </v-btn>
                  <v-btn v-else block color="primary" variant="tonal" size="large" class="mt-6" to="/login">
                    Open BioPay
                  </v-btn>
                  <p v-if="details.canApprove" class="approval-help">
                    Prefer the dashboard? Sign in and use the OTP approval option there.
                  </p>
                </template>

                <template v-else>
                  <div class="status-icon status-icon--error"><v-icon icon="mdi-alert-circle" size="32" /></div>
                  <p class="eyebrow mb-2">Approval unavailable</p>
                  <h1 class="approval-title">This link cannot be used</h1>
                  <v-alert type="error" variant="tonal" density="comfortable" class="mt-4 mb-5">
                    {{ errorMessage }}
                  </v-alert>
                  <p class="text-body-2 text-medium-emphasis mb-5">
                    Sign in to BioPay to review the current request and approve with an OTP.
                  </p>
                  <v-btn block color="primary" size="large" to="/login">Open BioPay</v-btn>
                </template>
              </v-card-text>
            </v-card>
          </v-col>
        </v-row>
      </v-container>
    </v-main>
  </v-app>
</template>

<style scoped>
.approval-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at 10% 12%, rgba(20, 184, 166, .14), transparent 30rem),
    radial-gradient(circle at 88% 82%, rgba(245, 158, 11, .12), transparent 26rem),
    #f8fafc;
}
.approval-card {
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, .09);
  border-radius: 20px;
  box-shadow: 0 28px 70px -34px rgba(15, 23, 42, .32) !important;
}
.approval-banner {
  display: flex;
  justify-content: center;
  padding: 25px 24px;
  background: #0c9488;
}
.approval-logo { display: block; width: min(240px, 68vw); height: auto; }
.status-icon {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  margin-bottom: 20px;
  border-radius: 18px;
}
.status-icon--pending { color: #0f766e; background: #ccfbf1; }
.status-icon--complete { color: #15803d; background: #dcfce7; }
.status-icon--error { color: #b91c1c; background: #fee2e2; }
.eyebrow {
  color: #0f766e;
  font-size: .72rem;
  font-weight: 800;
  letter-spacing: .09em;
  text-transform: uppercase;
}
.approval-title { color: #0f172a; font-size: clamp(1.55rem, 4vw, 2rem); line-height: 1.18; }
.approval-details {
  margin: 0;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #f8fafc;
}
.approval-details div { padding: 14px 16px; }
.approval-details div + div { border-top: 1px solid #e2e8f0; }
.approval-details dt {
  margin-bottom: 4px;
  color: #64748b;
  font-size: .7rem;
  font-weight: 800;
  letter-spacing: .06em;
  text-transform: uppercase;
}
.approval-details dd { margin: 0; color: #0f172a; font-weight: 650; overflow-wrap: anywhere; }
.approval-help { margin: 14px 0 0; color: #64748b; font-size: .78rem; line-height: 1.5; text-align: center; }
</style>
