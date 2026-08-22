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
          <img src="/biopay-features.png" alt="BioPay — biometric payment solutions" class="brand-graphic" />
        </aside>

        <!-- Right: login form (white) -->
        <section class="form-side">
          <div class="form-wrap">
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
                prepend-inner-icon="mdi-email-outline"
                :rules="[rules.required, rules.email]"
                autocomplete="username"
              />
              <v-text-field
                v-model="password"
                label="Password"
                prepend-inner-icon="mdi-lock-outline"
                :type="showPassword ? 'text' : 'password'"
                :append-inner-icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'"
                :rules="[rules.required]"
                autocomplete="current-password"
                @click:append-inner="showPassword = !showPassword"
              />
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

/* ---- Brand side -- the supplied BioPay feature graphic, shown as-is ---- */
.brand-side {
  position: relative;
  background: #050b1a;
  color: #fff;
  padding: clamp(1.35rem, 3.2vh, 2.25rem) clamp(1.75rem, 3.2vw, 3rem);
  display: flex;
  flex-direction: column;
}
@media (max-width: 900px) { .brand-side { padding: 1rem 1.25rem 1.15rem; min-height: 0; } }
.brand-back {
  display: inline-flex; align-items: center; color: rgba(255,255,255,.85);
  text-decoration: none; font-size: .875rem; font-weight: 500;
  flex-shrink: 0;
}
.brand-back:hover { color: #fff; }
.brand-graphic {
  flex: 1;
  width: 100%;
  min-height: 0;
  object-fit: contain;
  margin-top: clamp(.75rem, 2vh, 1.5rem);
}
@media (max-width: 900px) { .brand-graphic { display: none; } }

/* ---- Form side (white) ---- */
.form-side { width: 100%; min-height: 0; overflow-y: auto; overflow-x: hidden; background: #fff; display: flex; align-items: center; justify-content: center; padding: clamp(1.25rem, 4vh, 2.5rem) 1.5rem; }
.form-wrap { width: 100%; min-width: 0; max-width: 400px; }
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

@media (min-width: 901px) and (max-height: 720px) {
  .brand-side { padding-top: 1.1rem; padding-bottom: 1.1rem; }
}

@media (max-width: 900px) {
  .brand-side { flex-direction: row; align-items: center; justify-content: flex-start; padding: .85rem 1.25rem; }
  .brand-back { font-size: .78rem; }
  .form-side { padding: 1rem 1.25rem; }
  .form-head { margin-bottom: 1.1rem; }
  .form-title { font-size: 1.45rem; }
}

@media (max-width: 430px) {
  .form-wrap { max-width: 360px; }
}
</style>
