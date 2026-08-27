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
          <router-link to="/" class="brand-back">
            <v-icon icon="mdi-arrow-left" size="18" class="mr-1" /> Back to website
          </router-link>
          <img src="/biopay-features.webp" alt="BioPay — biometric payment solutions" class="brand-graphic" />
        </aside>

        <!-- Right: signup form (white) -->
        <section class="form-side">
          <div class="form-wrap">
            <router-link to="/" class="auth-brand" aria-label="BioPay home">
              <img src="/biopay_logo_horizontal.svg" alt="BioPay" />
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
  .brand-side { display: flex; align-items: center; padding: .85rem 1.25rem; }
  .brand-back { position: static; background: none; backdrop-filter: none; padding: 0; font-size: .78rem; }
  .form-side { padding: 1rem 1.25rem; }
  .form-head { margin-bottom: 1.1rem; }
  .auth-brand { margin-bottom: 1.25rem; }
  .form-title { font-size: 1.45rem; }
}

@media (max-width: 430px) {
  .form-wrap { max-width: 360px; }
}
</style>
