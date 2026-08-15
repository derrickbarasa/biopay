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

const highlights = [
  { icon: 'mdi-fingerprint', title: 'Biometric verification', text: 'Fingerprint or face match at the moment of payment.' },
  { icon: 'mdi-wifi-off', title: 'Works offline', text: 'Register and pay in the field with no signal, sync later.' },
  { icon: 'mdi-shield-check-outline', title: 'Accountable by design', text: 'Every disbursement time- and location-stamped.' },
]

async function handleSubmit() {
  errorMessage.value = ''
  if (!email.value || !password.value) return
  loading.value = true
  try {
    await auth.login(email.value, password.value)
    const redirect = (route.query.redirect as string) || undefined
    router.push({ name: 'verify-otp', query: redirect ? { redirect } : {} })
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
          <div class="brand-inner">
            <img src="/biopay_logo_main.svg" alt="BioPay" class="brand-logo" />
            <h1 class="brand-headline">Biometric payments you can account for.</h1>
            <p class="brand-sub">
              The registration and disbursement platform for anchors and organisations running
              cash-transfer and voucher programmes.
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
              <v-btn type="submit" block color="primary" size="large" :loading="loading" class="mt-2">
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
.form-side { background: #fff; display: flex; align-items: center; justify-content: center; padding: 2.5rem 1.5rem; }
.form-wrap { width: 100%; max-width: 400px; }
.form-head { margin-bottom: 1.75rem; }
.form-title { font-size: 1.6rem; font-weight: 700; color: #0f172a; margin: 0 0 .3rem; letter-spacing: -.01em; }
.form-subtitle { color: #64748b; font-size: .95rem; margin: 0; }
.link { color: #0d9488; }
</style>
