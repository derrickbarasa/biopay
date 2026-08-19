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

const highlights = [
  { icon: 'mdi-domain', title: 'Anchor → organisation → officer', text: 'Oversight that mirrors how your programme is actually structured.' },
  { icon: 'mdi-fingerprint', title: 'Biometric verification', text: 'Fingerprint or face match at the moment of payment.' },
  { icon: 'mdi-ticket-confirmation-outline', title: 'Vouchers and interventions', text: 'Cash, food distribution, voucher redemption and in-kind goods, all on one ledger.' },
]

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
          <router-link to="/" class="brand-back">
            <v-icon icon="mdi-arrow-left" size="18" class="mr-1" /> Back to website
          </router-link>
          <div class="brand-inner">
            <img src="/biopay_logo_main.svg" alt="BioPay" class="brand-logo" />
            <h1 class="brand-headline">Set up your anchor.</h1>
            <p class="brand-sub">
              Create the account an anchor uses to bring organisations, field officers and
              households onto BioPay for cash, food distribution, vouchers and other
              interventions.
            </p>
            <ul class="brand-list">
              <li v-for="h in highlights" :key="h.title">
                <v-icon :icon="h.icon" size="22" class="brand-list-icon" />
                <div>
                  <div class="brand-list-title">{{ h.title }}</div>
                  <div class="brand-list-text">{{ h.text }}</div>
                </div>
              </li>
            </ul>
          </div>
          <div class="brand-foot">Anchor · Organisation · Field officer</div>
        </aside>

        <!-- Right: signup form (white) -->
        <section class="form-side">
          <div class="form-wrap">
            <div class="form-head">
              <h2 class="form-title">Create your anchor account</h2>
              <p class="form-subtitle">Takes about a minute to get started.</p>
            </div>

            <v-form ref="formRef" validate-on="submit" @submit.prevent="handleSubmit">
              <v-alert v-if="errorMessage" type="error" variant="tonal" class="mb-4" density="compact">
                {{ errorMessage }}
              </v-alert>

              <v-text-field v-model="form.name" label="Anchor / organisation name" prepend-inner-icon="mdi-bank" :rules="[rules.required]" />
              <v-text-field v-model="form.authorisedName" label="Your name (authorised contact)" prepend-inner-icon="mdi-account-outline" />
              <v-text-field v-model="form.email" label="Email" prepend-inner-icon="mdi-email-outline" :rules="[rules.required, rules.email]" autocomplete="username" />
              <v-text-field v-model="form.phone" label="Phone (optional)" prepend-inner-icon="mdi-phone-outline" />
              <v-text-field v-model="form.address" label="Address (optional)" prepend-inner-icon="mdi-map-marker-outline" />
              <v-text-field
                v-model="form.password" label="Password" prepend-inner-icon="mdi-lock-outline"
                :type="showPassword ? 'text' : 'password'" :append-inner-icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'"
                :rules="[rules.required, rules.minLen]" hint="At least 8 characters" autocomplete="new-password"
                @click:append-inner="showPassword = !showPassword"
              />
              <v-text-field
                v-model="form.confirmPassword" label="Confirm password" prepend-inner-icon="mdi-lock-check-outline"
                :type="showConfirmPassword ? 'text' : 'password'"
                :append-inner-icon="showConfirmPassword ? 'mdi-eye-off' : 'mdi-eye'"
                :rules="[rules.required, (v: string) => v === form.password || 'Passwords do not match']"
                class="mt-2" autocomplete="new-password"
                @click:append-inner="showConfirmPassword = !showConfirmPassword"
              />

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
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1fr 1fr;
}
@media (max-width: 900px) { .split { grid-template-columns: 1fr; } }

/* ---- Brand side (teal) ---- */
.brand-side {
  position: relative;
  background: radial-gradient(circle at 100% 0, rgba(45, 212, 191, .35), transparent 30rem), linear-gradient(150deg, #0f766e 0%, #134e4a 100%);
  color: #fff;
  padding: 2.5rem 3rem;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
@media (max-width: 900px) { .brand-side { padding: 1.75rem; min-height: 42vh; } }
.brand-back {
  display: inline-flex; align-items: center; color: rgba(255,255,255,.85);
  text-decoration: none; font-size: .875rem; font-weight: 500;
}
.brand-back:hover { color: #fff; }
.brand-inner { max-width: 30rem; }
.brand-logo { width: 190px; height: auto; margin-bottom: 2rem; }
.brand-headline { font-size: clamp(1.6rem, 1.2rem + 1.5vw, 2.4rem); font-weight: 700; line-height: 1.15; letter-spacing: -.02em; margin: 0 0 1rem; }
.brand-sub { color: rgba(255,255,255,.82); font-size: 1.02rem; line-height: 1.55; margin: 0 0 2rem; }
.brand-list { list-style: none; margin: 0; padding: 0; display: grid; gap: 1.1rem; }
.brand-list li { display: flex; gap: .85rem; align-items: flex-start; }
.brand-list-icon { color: #99f6e4; margin-top: 2px; flex-shrink: 0; }
.brand-list-title { font-weight: 600; font-size: .98rem; }
.brand-list-text { color: rgba(255,255,255,.72); font-size: .88rem; line-height: 1.45; }
.brand-foot { color: rgba(255,255,255,.6); font-size: .78rem; letter-spacing: .04em; }
@media (max-width: 900px) { .brand-foot { display: none; } }

/* ---- Form side (white) ---- */
.form-side { background: #fff; display: flex; align-items: center; justify-content: center; padding: 2.5rem 1.5rem; overflow-y: auto; }
.form-wrap { width: 100%; max-width: 420px; }
.form-head { margin-bottom: 1.75rem; }
.form-title { font-size: 1.6rem; font-weight: 700; color: #0f172a; margin: 0 0 .3rem; letter-spacing: -.01em; }
.form-subtitle { color: #64748b; font-size: .95rem; margin: 0; }
.link { color: #0d9488; }

/* Create-account button adopts the website footer's accent (amber) background. */
.btn-accent :deep(.v-btn__content),
.btn-accent { color: #1a1200 !important; }
.btn-accent { background-color: #f59e0b !important; }
.btn-accent:hover { background-color: #ea580c !important; }
</style>
