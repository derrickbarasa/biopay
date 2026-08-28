<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

const auth = useAuthStore()
const router = useRouter()
const toast = useToast()

const form = ref({
  name: '', authorisedName: '', email: '', phone: '', address: '',
  password: '', confirmPassword: '',
})
const showPassword = ref(false)
const showConfirmPassword = ref(false)
const formRef = ref()
const loading = ref(false)
const errorMessage = ref('')

const rules = {
  required: (v: string) => !!v || 'Required',
  email: (v: string) => /.+@.+\..+/.test(v) || 'Enter a valid email',
  minLen: (v: string) => v.length >= 8 || 'At least 8 characters',
}

async function handleSubmit() {
  errorMessage.value = ''
  const validation = await formRef.value?.validate()
  if (!validation?.valid) {
    errorMessage.value = 'Check the highlighted fields and try again.'
    return
  }
  if (form.value.password !== form.value.confirmPassword) {
    errorMessage.value = 'Passwords do not match'
    return
  }
  loading.value = true
  try {
    const requiresOtp = await auth.signup(form.value)
    if (requiresOtp) {
      toast.success('Account created. Verify to continue')
      await router.push({ name: 'verify-otp' })
    } else {
      toast.success('Account created. You are signed in')
      try {
        await router.replace('/app/dashboard')
      } catch {
        window.location.assign('/app/dashboard')
      }
    }
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Signup failed'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <v-app>
    <v-main>
      <div class="split">
        <!-- Left: brand / system description (theme colour), matches LoginPage -->
        <aside class="brand-side">
          <img src="/biopay-mark-watermark.webp" alt="" class="brand-watermark" aria-hidden="true" />
          <div class="brand-copy">
            <h1 class="brand-title">Biometric proof for every payment.</h1>
            <p class="brand-text">BioPay ties every disbursement to the beneficiary's own body — a fingerprint or face match, captured once and verified again the moment they're paid. From anchor to field officer, every batch moves through maker-checker approval, offline-ready capture, and continuous AI review for the patterns a manual check would miss.</p>
            <ul class="brand-points">
              <li><v-icon icon="mdi-fingerprint" size="20" /> Fingerprint &amp; face verification</li>
              <li><v-icon icon="mdi-wifi-off" size="20" /> Works fully offline, syncs when back online</li>
              <li><v-icon icon="mdi-account-multiple-check-outline" size="20" /> Maker-checker approval on every batch</li>
              <li><v-icon icon="mdi-shield-check-outline" size="20" /> Time-stamped, GPS-tagged and fully auditable</li>
            </ul>
          </div>
        </aside>

        <!-- Right: signup form (white) -->
        <section class="form-side">
          <div class="form-wrap">
            <router-link to="/" class="back-link">
              <v-icon icon="mdi-arrow-left" size="18" class="mr-1" /> Back to website
            </router-link>
            <div class="form-head">
              <h2 class="form-title">Create your anchor account</h2>
              <p class="form-subtitle">Takes about a minute to get started.</p>
            </div>

            <v-form ref="formRef" validate-on="submit" @submit.prevent="handleSubmit">
              <v-alert v-if="errorMessage" type="error" variant="tonal" class="mb-4" density="compact">
                {{ errorMessage }}
              </v-alert>

              <v-text-field v-model="form.name" label="Anchor name" :rules="[rules.required]" />
              <v-text-field v-model="form.authorisedName" label="Your name (authorised contact)" />
              <v-text-field v-model="form.email" label="Email" :rules="[rules.required, rules.email]" autocomplete="username" />
              <v-text-field v-model="form.phone" label="Phone (optional)" />
              <v-text-field v-model="form.address" label="Address (optional)" />
              <v-text-field
                v-model="form.password" label="Password"
                :type="showPassword ? 'text' : 'password'"
                :rules="[rules.required, rules.minLen]" hint="At least 8 characters" autocomplete="new-password"
              >
                <template #append-inner>
                  <v-btn :icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'" variant="text" density="compact" :aria-label="showPassword ? 'Hide password' : 'Show password'" @click="showPassword = !showPassword" />
                </template>
              </v-text-field>
              <v-text-field
                v-model="form.confirmPassword" label="Confirm password"
                :type="showConfirmPassword ? 'text' : 'password'"
                :rules="[rules.required, (v: string) => v === form.password || 'Passwords do not match']"
                class="mt-2" autocomplete="new-password"
              >
                <template #append-inner>
                  <v-btn :icon="showConfirmPassword ? 'mdi-eye-off' : 'mdi-eye'" variant="text" density="compact" :aria-label="showConfirmPassword ? 'Hide confirmation password' : 'Show confirmation password'" @click="showConfirmPassword = !showConfirmPassword" />
                </template>
              </v-text-field>

              <v-btn type="submit" block size="large" :loading="loading" :disabled="loading" class="mt-4 btn-accent">
                Create account
              </v-btn>
            </v-form>

            <div class="text-center text-body-2 mt-5">
              Already have an account?
              <router-link to="/login" class="text-decoration-none link font-weight-medium">Sign in</router-link>
            </div>
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

/* ---- Brand side -- the supplied BioPay feature graphic, full-bleed ---- */
.brand-side {
  position: relative;
  background: #0F766E;
  color: #fff;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  align-items: safe center;
  padding: clamp(2rem, 5vw, 4rem);
}
.brand-watermark {
  position: absolute;
  top: 50%;
  left: 50%;
  width: min(720px, 82%);
  height: auto;
  transform: translate(-50%, -50%);
  opacity: 0.55;
  pointer-events: none;
  user-select: none;
}
.brand-copy { position: relative; z-index: 1; max-width: 420px; }
.brand-title,
.brand-text,
.brand-points li { text-shadow: 0 1px 10px rgba(2, 10, 8, .6); }
.brand-title { font-size: clamp(1.7rem, 2.6vw, 2.3rem); font-weight: 700; line-height: 1.15; letter-spacing: -.01em; margin: 0 0 1rem; }
.brand-text { color: rgba(255,255,255,.78); font-size: 1rem; line-height: 1.6; margin: 0 0 2rem; }
.brand-points { list-style: none; margin: 0; padding: 0; display: grid; gap: 1rem; }
.brand-points li { display: flex; align-items: center; gap: .75rem; font-size: .92rem; color: rgba(255,255,255,.9); }
.brand-points .v-icon { color: #6ee7c5; flex: 0 0 auto; }

/* ---- Form side (white) ---- */
.form-side { width: 100%; min-height: 0; overflow-y: auto; overflow-x: hidden; background: #fff; display: flex; align-items: safe center; justify-content: center; padding: clamp(1.25rem, 4vh, 2.5rem) 1.5rem; }
.form-wrap { width: 100%; min-width: 0; max-width: 420px; }
.back-link {
  display: inline-flex; align-items: center;
  margin-bottom: 1.75rem;
  color: #64748b; text-decoration: none;
  font-size: .875rem; font-weight: 500;
  border-radius: 6px;
}
.back-link:hover { color: #0f172a; }
.back-link:focus-visible { outline: 3px solid #0d9488; outline-offset: 4px; }
.form-wrap :deep(.v-input) { max-width: 100%; }
.form-head { margin-bottom: 1.75rem; }
.form-title { font-size: 1.6rem; font-weight: 700; color: #0f172a; margin: 0 0 .3rem; letter-spacing: -.01em; }
.form-subtitle { color: #64748b; font-size: .95rem; margin: 0; }
.link { color: #0d9488; }

/* Create-account button adopts the website footer's accent (amber) background. */
.btn-accent :deep(.v-btn__content),
.btn-accent { color: #1a1200 !important; }
.btn-accent { background-color: #f59e0b !important; }
.btn-accent:hover { background-color: #ea580c !important; }

@media (max-width: 900px) {
  .brand-side { display: none; }
  .form-side { padding: 1rem 1.25rem; }
  .form-head { margin-bottom: 1.1rem; }
  .back-link { margin-bottom: 1.25rem; }
  .form-title { font-size: 1.45rem; }
}

@media (max-width: 430px) {
  .form-wrap { max-width: 360px; }
}
</style>
