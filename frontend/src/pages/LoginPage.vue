<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const email = ref('')
const password = ref('')
const showPassword = ref(false)
const loading = ref(false)
const errorMessage = ref('')

const rules = {
  required: (v: string) => !!v || 'Required',
  email: (v: string) => /.+@.+\..+/.test(v) || 'Enter a valid email',
}

async function handleSubmit() {
  errorMessage.value = ''
  if (!email.value || !password.value) return
  loading.value = true
  try {
    const requiresOtp = await auth.login(email.value, password.value)
    const requestedRedirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const redirect = requestedRedirect.startsWith('/app') ? requestedRedirect : undefined
    if (requiresOtp) {
      await router.push({ name: 'verify-otp', query: redirect ? { redirect } : {} })
    } else {
      const destination = redirect || '/app/dashboard'
      try {
        await router.replace(destination)
      } catch {
        // Recover from a stale lazy-loaded route chunk after a deployment/dev-server restart.
        window.location.assign(destination)
      }
    }
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Login failed'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <v-app>
    <v-main>
      <div class="split">
        <!-- Left: brand / system description (theme colour) -->
        <aside class="brand-side">
          <router-link to="/" class="brand-back">
            <v-icon icon="mdi-arrow-left" size="18" class="mr-1" /> Back to website
          </router-link>
          <img src="/biopay-features.webp" alt="BioPay — biometric payment solutions" class="brand-graphic" />
        </aside>

        <!-- Right: login form (white) -->
        <section class="form-side">
          <div class="form-wrap">
            <router-link to="/" class="auth-brand" aria-label="BioPay home">
              <img src="/biopay_logo_horizontal.svg" alt="BioPay" />
            </router-link>
            <div class="form-head">
              <h2 class="form-title">Welcome back</h2>
              <p class="form-subtitle">Sign in to your BioPay dashboard.</p>
            </div>

            <v-form @submit.prevent="handleSubmit">
              <v-alert v-if="errorMessage" type="error" variant="tonal" class="mb-4" density="compact">
                {{ errorMessage }}
              </v-alert>
              <v-text-field
                v-model="email"
                label="Email"
                :rules="[rules.required, rules.email]"
                autocomplete="username"
              />
              <v-text-field
                v-model="password"
                label="Password"
                :type="showPassword ? 'text' : 'password'"
                :rules="[rules.required]"
                autocomplete="current-password"
              >
                <template #append-inner>
                  <v-btn
                    :icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'" variant="text" density="compact"
                    :aria-label="showPassword ? 'Hide password' : 'Show password'"
                    @click="showPassword = !showPassword"
                  />
                </template>
              </v-text-field>
              <div class="d-flex justify-end mb-2">
                <router-link to="/forgot-password" class="text-caption text-decoration-none link">Forgot password?</router-link>
              </div>
              <v-btn type="submit" block size="large" :loading="loading" class="mt-2 btn-accent">
                Sign in
              </v-btn>
            </v-form>

            <div class="text-center text-body-2 mt-6">
              Don't have an account?
              <router-link to="/signup" class="text-decoration-none link font-weight-medium">Sign up</router-link>
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
.form-wrap { width: 100%; min-width: 0; max-width: 400px; }
.auth-brand { display: inline-flex; margin-bottom: 2.5rem; border-radius: 6px; }
.auth-brand img { display: block; width: 158px; height: auto; }
.auth-brand:focus-visible { outline: 3px solid #0d9488; outline-offset: 4px; }
.form-wrap :deep(.v-input) { max-width: 100%; }
.form-head { margin-bottom: 1.75rem; }
.form-title { font-size: 1.6rem; font-weight: 700; color: #0f172a; margin: 0 0 .3rem; letter-spacing: -.01em; }
.form-subtitle { color: #64748b; font-size: .95rem; margin: 0; }
.link { color: #0d9488; }

/* Sign-in button adopts the website footer's accent (amber) background. */
.btn-accent :deep(.v-btn__content),
.btn-accent { color: #1a1200 !important; }
.btn-accent { background-color: #f59e0b !important; }
.btn-accent:hover { background-color: #ea580c !important; }

@media (max-width: 900px) {
  .brand-side { display: flex; align-items: center; padding: .85rem 1.25rem; }
  .brand-back { position: static; background: none; backdrop-filter: none; padding: 0; }
}

@media (max-width: 900px) {
  .brand-side { flex-direction: row; align-items: center; justify-content: flex-start; padding: .85rem 1.25rem; }
  .brand-back { font-size: .78rem; }
  .form-side { padding: 1rem 1.25rem; }
  .form-head { margin-bottom: 1.1rem; }
  .auth-brand { margin-bottom: 1.6rem; }
  .form-title { font-size: 1.45rem; }
}

@media (max-width: 430px) {
  .form-wrap { max-width: 360px; }
}
</style>
