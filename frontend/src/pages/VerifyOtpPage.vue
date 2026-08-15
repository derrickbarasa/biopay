<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import type { OtpMethod } from '@/types/user'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const toast = useToast()

const step = ref<'choose' | 'code'>('choose')
const selectedMethod = ref<OtpMethod | null>(null)
const code = ref('')
const sending = ref(false)
const loading = ref(false)
const errorMessage = ref('')

const methodInfo: Record<OtpMethod, { title: string; subtitle: string; icon: string }> = {
  EMAIL: { title: 'Email', subtitle: 'We\'ll send a 6-digit code to your inbox', icon: 'mdi-email-outline' },
  TOTP: { title: 'Authenticator app', subtitle: 'Enter the code from your authenticator app', icon: 'mdi-cellphone-key' },
}

onMounted(() => {
  if (!auth.pendingLogin) {
    router.replace({ name: 'login' })
    return
  }
  if (auth.pendingLogin.methods.length === 1) {
    selectMethod(auth.pendingLogin.methods[0])
  }
})

async function selectMethod(method: OtpMethod) {
  selectedMethod.value = method
  errorMessage.value = ''
  if (method === 'EMAIL') {
    sending.value = true
    try {
      await auth.requestLoginOtp('EMAIL')
      step.value = 'code'
    } catch (err) {
      errorMessage.value = err instanceof Error ? err.message : 'Failed to send code'
    } finally {
      sending.value = false
    }
  } else {
    step.value = 'code'
  }
}

async function resendCode() {
  try {
    await auth.requestLoginOtp('EMAIL')
    toast.success('Code resent')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to resend code')
  }
}

function changeMethod() {
  step.value = 'choose'
  code.value = ''
  errorMessage.value = ''
}

function useDifferentAccount() {
  auth.cancelPendingLogin()
  router.replace({ name: 'login' })
}

async function submitCode() {
  if (!selectedMethod.value || code.value.trim().length === 0) return
  errorMessage.value = ''
  loading.value = true
  try {
    await auth.verifyLoginOtp(selectedMethod.value, code.value.trim())
    const redirect = (route.query.redirect as string) || '/app/dashboard'
    router.push(redirect)
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Incorrect or expired code'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <v-app>
    <v-main class="login-bg">
      <a href="#" class="back-link" @click.prevent="useDifferentAccount">
        <v-icon icon="mdi-arrow-left" size="18" class="mr-1" />
        Use a different account
      </a>

      <v-container class="fill-height" fluid>
        <v-row justify="center" align="center" class="fill-height">
          <v-col cols="12" sm="8" md="5" lg="4">
            <v-card v-if="auth.pendingLogin" elevation="0" class="pa-2 login-card">
              <v-card-text class="text-center pt-6">
                <img src="/biopay_logo_horizontal.svg" alt="BioPay" class="login-logo" />
                <div class="text-body-2 text-medium-emphasis">Verify it's you</div>
              </v-card-text>

              <v-card-text>
                <template v-if="step === 'choose'">
                  <p class="text-body-2 text-medium-emphasis mb-4">Choose how you'd like to verify.</p>
                  <v-list class="method-list">
                    <v-list-item
                      v-for="m in auth.pendingLogin.methods" :key="m"
                      :prepend-icon="methodInfo[m].icon" :title="methodInfo[m].title" :subtitle="methodInfo[m].subtitle"
                      class="method-item" rounded="lg" :disabled="sending"
                      @click="selectMethod(m)"
                    />
                  </v-list>
                  <v-progress-linear v-if="sending" indeterminate color="primary" class="mt-3" />
                </template>

                <template v-else>
                  <v-alert v-if="errorMessage" type="error" variant="tonal" class="mb-4" density="compact">
                    {{ errorMessage }}
                  </v-alert>
                  <p class="text-body-2 text-medium-emphasis mb-4">
                    <template v-if="selectedMethod === 'EMAIL'">
                      Enter the 6-digit code sent to <strong>{{ auth.pendingLogin.maskedEmail }}</strong>.
                    </template>
                    <template v-else>
                      Enter the 6-digit code from your authenticator app.
                    </template>
                  </p>
                  <v-form @submit.prevent="submitCode">
                    <v-text-field
                      v-model="code" label="Verification code" prepend-inner-icon="mdi-shield-key-outline"
                      inputmode="numeric" maxlength="6" autofocus autocomplete="one-time-code"
                    />
                    <v-btn type="submit" block color="primary" size="large" :loading="loading" class="mt-2">
                      Verify and sign in
                    </v-btn>
                  </v-form>

                  <div class="d-flex justify-space-between mt-5 text-caption">
                    <a v-if="selectedMethod === 'EMAIL'" href="#" class="link text-decoration-none" @click.prevent="resendCode">Resend code</a>
                    <span v-else />
                    <a v-if="auth.pendingLogin.methods.length > 1" href="#" class="link text-decoration-none" @click.prevent="changeMethod">Choose another method</a>
                  </div>
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
.login-bg {
  background: radial-gradient(circle at 15% 15%, rgba(20, 184, 166, .38), transparent 28rem), linear-gradient(135deg, #134e4a 0%, #0f766e 100%);
  position: relative;
}
.login-card { border: 1px solid rgba(255,255,255,.35); box-shadow: 0 30px 70px -26px rgba(0,0,0,.45) !important; }
.login-logo { width: 224px; height: auto; }
.link { color: #0d9488; }
.method-list { background: transparent; }
.method-item { border: 1px solid rgba(0,0,0,.1); margin-bottom: 8px; cursor: pointer; }
.back-link {
  position: absolute;
  top: 20px;
  left: 24px;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  color: rgba(255, 255, 255, .88);
  text-decoration: none;
  font-size: .875rem;
  font-weight: 500;
}
.back-link:hover { color: #fff; }
</style>
