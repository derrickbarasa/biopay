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
  height: 100vh;
  height: 100dvh;
  min-height: 0;
  overflow: hidden;
  display: grid;
  grid-template-columns: 1fr 1fr;
}
.split > * { min-width: 0; }
@media (max-width: 900px) { .split { grid-template-columns: 1fr; grid-template-rows: auto minmax(0, 1fr); } }

/* ---- Brand side (teal) ---- */
.brand-side {
  position: relative;
  background-image: linear-gradient(100deg, rgba(8, 51, 47, .93) 0%, rgba(12, 74, 69, .82) 34%, rgba(15, 118, 110, .55) 62%, rgba(15, 118, 110, .38) 100%), url('/login-bg.png');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  color: #fff;
  padding: clamp(1.35rem, 3.2vh, 2.25rem) clamp(1.75rem, 3.2vw, 3rem);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
@media (max-width: 900px) { .brand-side { padding: 1rem 1.25rem 1.15rem; min-height: 0; } }
.brand-back {
  display: inline-flex; align-items: center; color: rgba(255,255,255,.85);
  text-decoration: none; font-size: .875rem; font-weight: 500;
}
.brand-back:hover { color: #fff; }
.brand-inner { max-width: 30rem; }
.brand-headline { font-size: clamp(1.6rem, 1.2rem + 1.5vw, 2.4rem); font-weight: 700; line-height: 1.15; letter-spacing: -.02em; margin: 0 0 1rem; }
.brand-sub { color: rgba(255,255,255,.82); font-size: .96rem; line-height: 1.48; margin: 0 0 clamp(1rem, 2.5vh, 1.7rem); }
.brand-list { list-style: none; margin: 0; padding: 0; display: grid; gap: clamp(.65rem, 1.6vh, 1rem); }
.brand-list li { display: flex; gap: .85rem; align-items: flex-start; }
.brand-list-icon { color: #99f6e4; margin-top: 2px; flex-shrink: 0; }
.brand-list-title { font-weight: 600; font-size: .98rem; }
.brand-list-text { color: rgba(255,255,255,.72); font-size: .84rem; line-height: 1.38; }
.brand-foot { color: rgba(255,255,255,.6); font-size: .78rem; letter-spacing: .04em; }
@media (max-width: 900px) { .brand-foot { display: none; } }

/* ---- Form side (white) ---- */
.form-side { width: 100%; min-height: 0; overflow-y: auto; overflow-x: hidden; background: #fff; display: flex; align-items: center; justify-content: center; padding: clamp(1.25rem, 4vh, 2.5rem) 1.5rem; }
.form-wrap { width: 100%; min-width: 0; max-width: 420px; }
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

@media (min-width: 901px) and (max-height: 760px) {
  .brand-side { padding-top: 1.1rem; padding-bottom: 1.1rem; }
  .brand-headline { font-size: 1.55rem; margin-bottom: .65rem; }
  .brand-sub { font-size: .88rem; margin-bottom: .75rem; }
  .brand-list { gap: .45rem; }
  .brand-list-text { font-size: .78rem; line-height: 1.3; }
  .brand-list-icon { font-size: 19px !important; }
}

@media (max-width: 900px) {
  .brand-side { flex-direction: row; align-items: center; justify-content: flex-start; gap: .65rem; }
  .brand-back { font-size: .78rem; }
  .brand-inner { max-width: none; }
  .brand-headline { max-width: 18ch; font-size: clamp(1.05rem, 4vw, 1.35rem); margin: 0; }
  .brand-sub, .brand-list, .brand-foot { display: none; }
  .form-side { padding: 1rem 1.25rem; }
  .form-head { margin-bottom: 1.1rem; }
  .form-title { font-size: 1.45rem; }
}

@media (max-width: 430px) {
  .brand-headline { font-size: 1rem; }
  .form-wrap { max-width: 360px; }
}
</style>
