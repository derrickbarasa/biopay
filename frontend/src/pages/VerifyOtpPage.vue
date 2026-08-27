<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import type { OtpMethod } from '@/types/user'

const CODE_LENGTH = 6

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const toast = useToast()

const step = ref<'choose' | 'code'>('choose')
const selectedMethod = ref<OtpMethod | null>(null)
const digits = ref<string[]>(Array(CODE_LENGTH).fill(''))
const digitInputs = ref<(HTMLInputElement | null)[]>([])
const sending = ref(false)
const loading = ref(false)
const errorMessage = ref('')

const code = () => digits.value.join('')

function focusDigit(index: number) {
  digitInputs.value[index]?.focus()
  digitInputs.value[index]?.select()
}

function onDigitInput(index: number, event: Event) {
  const input = event.target as HTMLInputElement
  const value = input.value.replace(/\D/g, '')

  if (value.length > 1) {
    // Handles a paste that landed in a single box: spread it across the remaining boxes.
    const chars = value.slice(0, CODE_LENGTH - index).split('')
    chars.forEach((ch, offset) => { digits.value[index + offset] = ch })
    input.value = digits.value[index]
    const next = Math.min(index + chars.length, CODE_LENGTH - 1)
    nextTick(() => focusDigit(next))
    return
  }

  digits.value[index] = value
  input.value = value
  if (value && index < CODE_LENGTH - 1) {
    nextTick(() => focusDigit(index + 1))
  }
}

function onDigitKeydown(index: number, event: KeyboardEvent) {
  if (event.key === 'Backspace' && !digits.value[index] && index > 0) {
    event.preventDefault()
    digits.value[index - 1] = ''
    focusDigit(index - 1)
  } else if (event.key === 'ArrowLeft' && index > 0) {
    event.preventDefault()
    focusDigit(index - 1)
  } else if (event.key === 'ArrowRight' && index < CODE_LENGTH - 1) {
    event.preventDefault()
    focusDigit(index + 1)
  }
}

function onDigitPaste(index: number, event: ClipboardEvent) {
  const pasted = event.clipboardData?.getData('text').replace(/\D/g, '') ?? ''
  if (!pasted) return
  event.preventDefault()
  const chars = pasted.slice(0, CODE_LENGTH - index).split('')
  chars.forEach((ch, offset) => { digits.value[index + offset] = ch })
  const next = Math.min(index + chars.length, CODE_LENGTH - 1)
  nextTick(() => focusDigit(next))
}

function resetDigits() {
  digits.value = Array(CODE_LENGTH).fill('')
}

const methodInfo: Record<OtpMethod, { title: string; subtitle: string; icon: string }> = {
  EMAIL: { title: 'Email', subtitle: 'We\'ll send a 6-digit code to your inbox', icon: 'mdi-email-outline' },
  TOTP: { title: 'Authenticator app', subtitle: 'Enter the code from your authenticator app', icon: 'mdi-cellphone-key' },
}

onMounted(() => {
  if (!auth.pendingLogin) {
    router.replace({ name: 'login' })
  }
})

onUnmounted(() => {
  if (resendTimer) clearInterval(resendTimer)
})

const RESEND_COOLDOWN_SECONDS = 30
const resendCooldown = ref(0)
let resendTimer: ReturnType<typeof setInterval> | undefined

function startResendCooldown() {
  resendCooldown.value = RESEND_COOLDOWN_SECONDS
  if (resendTimer) clearInterval(resendTimer)
  resendTimer = setInterval(() => {
    resendCooldown.value -= 1
    if (resendCooldown.value <= 0 && resendTimer) {
      clearInterval(resendTimer)
      resendTimer = undefined
    }
  }, 1000)
}

async function selectMethod(method: OtpMethod) {
  selectedMethod.value = method
  errorMessage.value = ''
  if (method === 'EMAIL') {
    sending.value = true
    try {
      await auth.requestLoginOtp('EMAIL')
      step.value = 'code'
      startResendCooldown()
      nextTick(() => focusDigit(0))
    } catch (err) {
      errorMessage.value = err instanceof Error ? err.message : 'Failed to send code'
    } finally {
      sending.value = false
    }
  } else {
    step.value = 'code'
    nextTick(() => focusDigit(0))
  }
}

async function resendCode() {
  if (resendCooldown.value > 0) return
  try {
    await auth.requestLoginOtp('EMAIL')
    resetDigits()
    startResendCooldown()
    nextTick(() => focusDigit(0))
    toast.success('Code resent')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to resend code')
  }
}

function changeMethod() {
  step.value = 'choose'
  resetDigits()
  errorMessage.value = ''
}

function useDifferentAccount() {
  auth.cancelPendingLogin()
  router.replace({ name: 'login' })
}

async function submitCode() {
  const value = code()
  if (!selectedMethod.value || value.length !== CODE_LENGTH || loading.value) return
  errorMessage.value = ''
  loading.value = true
  try {
    await auth.verifyLoginOtp(selectedMethod.value, value)
    const redirect = (route.query.redirect as string) || '/app/dashboard'
    router.push(redirect)
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Incorrect or expired code'
    resetDigits()
    nextTick(() => focusDigit(0))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <v-app>
    <v-main>
      <div v-if="auth.pendingLogin" class="split">
        <!-- Left: brand / system description (theme colour), matches LoginPage/SignupPage -->
        <aside class="brand-side">
          <a href="#" class="brand-back" @click.prevent="useDifferentAccount">
            <v-icon icon="mdi-arrow-left" size="18" class="mr-1" /> Use a different account
          </a>
          <img src="/biopay-features.webp" alt="BioPay — biometric payment solutions" class="brand-graphic" />
        </aside>

        <!-- Right: verification step (white) -->
        <section class="form-side">
          <div class="form-wrap">
            <router-link to="/" class="auth-brand" aria-label="BioPay home">
              <img src="/biopay_logo_horizontal.svg" alt="BioPay" />
            </router-link>
            <div class="form-head">
              <h2 class="form-title">Verify it's you</h2>
              <p class="form-subtitle">One more step to keep your BioPay account secure.</p>
            </div>

            <template v-if="step === 'choose'">
              <p class="section-label">Choose how you'd like to verify</p>
              <div class="method-list">
                <button
                  v-for="m in auth.pendingLogin.methods" :key="m" type="button"
                  class="method-item" :disabled="sending"
                  @click="selectMethod(m)"
                >
                  <span class="method-icon"><v-icon :icon="methodInfo[m].icon" size="22" /></span>
                  <span class="method-copy">
                    <span class="method-title">{{ methodInfo[m].title }}</span>
                    <span class="method-subtitle">{{ methodInfo[m].subtitle }}</span>
                  </span>
                  <v-icon icon="mdi-chevron-right" size="20" class="method-chevron" />
                </button>
              </div>
              <v-progress-linear v-if="sending" indeterminate color="primary" rounded height="3" class="mt-4" />
            </template>

            <template v-else>
              <v-alert v-if="errorMessage" type="error" variant="tonal" class="mb-4" density="compact">
                {{ errorMessage }}
              </v-alert>
              <p class="section-label code-copy">
                <template v-if="selectedMethod === 'EMAIL'">
                  Enter the 6-digit code sent to <strong>{{ auth.pendingLogin.maskedEmail }}</strong>
                </template>
                <template v-else>
                  Enter the 6-digit code from your authenticator app
                </template>
              </p>
              <v-form @submit.prevent="submitCode">
                <div class="otp-boxes">
                  <input
                    v-for="(d, i) in digits" :key="i"
                    :ref="(el) => (digitInputs[i] = el as HTMLInputElement)"
                    :value="d"
                    class="otp-box"
                    type="text" inputmode="numeric" pattern="[0-9]*" maxlength="1"
                    :autocomplete="i === 0 ? 'one-time-code' : 'off'"
                    :disabled="loading"
                    @input="onDigitInput(i, $event)"
                    @keydown="onDigitKeydown(i, $event)"
                    @paste="onDigitPaste(i, $event)"
                  >
                </div>
                <v-btn type="submit" block size="large" :loading="loading" class="mt-6 btn-accent">
                  Verify and sign in
                </v-btn>
              </v-form>

              <div class="d-flex justify-space-between mt-5 text-caption">
                <template v-if="selectedMethod === 'EMAIL'">
                  <a v-if="resendCooldown <= 0" href="#" class="link text-decoration-none" @click.prevent="resendCode">Resend code</a>
                  <span v-else class="text-medium-emphasis">Resend code in 0:{{ String(resendCooldown).padStart(2, '0') }}</span>
                </template>
                <span v-else />
                <a v-if="auth.pendingLogin.methods.length > 1" href="#" class="link text-decoration-none" @click.prevent="changeMethod">Choose another method</a>
              </div>
            </template>
          </div>
        </section>
      </div>
    </v-main>
  </v-app>
</template>

<style scoped>
.split {
  height: 100vh;
  height: 100dvh;
  min-height: 0;
  overflow: hidden;
  display: grid;
  grid-template-columns: 1fr 1fr;
}
.split > * { min-width: 0; }
@media (max-width: 900px) { .split { grid-template-columns: 1fr; grid-template-rows: auto minmax(0, 1fr); } }

/* ---- Brand side -- identical to LoginPage/SignupPage's supplied feature graphic ---- */
.brand-side {
  position: relative;
  background: #050b1a;
  color: #fff;
  overflow: hidden;
}
.brand-back {
  position: absolute;
  top: clamp(1rem, 3vh, 1.75rem);
  left: clamp(1.25rem, 3vw, 2rem);
  z-index: 2;
  display: inline-flex; align-items: center; color: rgba(255,255,255,.92);
  text-decoration: none; font-size: .875rem; font-weight: 500;
  padding: .4rem .85rem .4rem .6rem;
  border-radius: 999px;
  background: rgba(5, 11, 26, .55);
  backdrop-filter: blur(6px);
}
.brand-back:hover { color: #fff; background: rgba(5, 11, 26, .74); }
.brand-graphic {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
}
@media (max-width: 900px) { .brand-graphic { display: none; } }

/* ---- Form side (white) ---- */
.form-side { width: 100%; min-height: 0; overflow-y: auto; overflow-x: hidden; background: #fff; display: flex; align-items: center; justify-content: center; padding: clamp(1.25rem, 4vh, 2.5rem) 1.5rem; }
.form-wrap { width: 100%; min-width: 0; max-width: 420px; }
.auth-brand { display: inline-flex; margin-bottom: 2rem; border-radius: 6px; }
.auth-brand img { display: block; width: 158px; height: auto; }
.auth-brand:focus-visible { outline: 3px solid #0d9488; outline-offset: 4px; }
.form-head { margin-bottom: 1.75rem; }
.form-title { font-size: 1.6rem; font-weight: 700; color: #0f172a; margin: 0 0 .3rem; letter-spacing: -.01em; }
.form-subtitle { color: #64748b; font-size: .95rem; margin: 0; }

.section-label { font-size: .8rem; font-weight: 600; color: #475569; text-transform: uppercase; letter-spacing: .04em; margin: 0 0 14px; }
.code-copy { font-size: .9rem; font-weight: 400; text-transform: none; letter-spacing: normal; color: #64748b; }

.link { color: #0d9488; font-weight: 600; }
.link:hover { color: #0f766e; }

/* Verify button adopts the same accent (amber) background as Login/Signup's primary CTA. */
.btn-accent :deep(.v-btn__content),
.btn-accent { color: #1a1200 !important; }
.btn-accent { background-color: #f59e0b !important; }
.btn-accent:hover { background-color: #ea580c !important; }

/* ---- Method chooser -- colour-chip icon rows instead of plain bordered list items ---- */
.method-list { display: flex; flex-direction: column; gap: 10px; }
.method-item {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 14px 16px;
  background: #fff;
  border: 1.5px solid #e6ebf0;
  border-radius: 16px;
  text-align: left;
  cursor: pointer;
  transition: border-color .15s ease, box-shadow .15s ease, transform .15s ease;
}
.method-item:hover:not(:disabled) {
  border-color: #0d9488;
  box-shadow: 0 10px 24px -16px rgba(13, 148, 136, .45);
  transform: translateY(-1px);
}
.method-item:disabled { opacity: .55; cursor: default; }
.method-icon {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: #f0fdfa;
  color: #0d9488;
}
.method-copy { display: flex; flex-direction: column; min-width: 0; }
.method-title { font-size: .95rem; font-weight: 600; color: #0f172a; }
.method-subtitle { font-size: .8rem; color: #64748b; }
.method-chevron { flex-shrink: 0; color: #cbd5e1; }
.method-item:hover:not(:disabled) .method-chevron { color: #0d9488; }

/* ---- OTP boxes ---- */
.otp-boxes { display: flex; justify-content: center; gap: .65rem; }
.otp-box {
  width: 3.1rem;
  height: 3.5rem;
  border: 1.5px solid rgba(15, 23, 42, .16);
  border-radius: 12px;
  text-align: center;
  font-size: 1.5rem;
  font-weight: 700;
  color: #0f172a;
  background: #f8fafc;
  box-shadow: inset 0 1px 2px rgba(15, 23, 42, .04);
  transition: border-color .15s, box-shadow .15s, background .15s;
}
.otp-box:focus {
  outline: none;
  border-color: #0d9488;
  background: #fff;
  box-shadow: 0 0 0 4px rgba(13, 148, 136, .14);
}
.otp-box:disabled { opacity: .6; }

@media (max-width: 900px) {
  .brand-side { display: flex; align-items: center; padding: .85rem 1.25rem; }
  .brand-back { position: static; background: none; backdrop-filter: none; padding: 0; font-size: .78rem; }
  .form-side { padding: 1rem 1.25rem; }
  .form-head { margin-bottom: 1.1rem; }
  .auth-brand { margin-bottom: 1.25rem; }
  .form-title { font-size: 1.45rem; }
  .otp-boxes { gap: .4rem; }
  .otp-box { width: 2.6rem; height: 3.1rem; font-size: 1.3rem; }
}

@media (max-width: 430px) {
  .form-wrap { max-width: 360px; }
}
</style>
