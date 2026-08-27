<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const showOldPassword = ref(false)
const showNewPassword = ref(false)
const loading = ref(false)
const errorMessage = ref('')

async function submit() {
  errorMessage.value = ''
  if (!oldPassword.value) {
    errorMessage.value = 'Enter the temporary password from your email'
    return
  }
  if (newPassword.value.length < 8) {
    errorMessage.value = 'New password must be at least 8 characters'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    errorMessage.value = 'Passwords do not match'
    return
  }
  loading.value = true
  try {
    await dispatch('CHANGE_PASSWORD', { oldPassword: oldPassword.value, newPassword: newPassword.value })
    await auth.refreshProfile()
    router.replace('/app/dashboard')
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Failed to change password'
  } finally {
    loading.value = false
  }
}

async function logout() {
  await auth.logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <v-app>
    <v-main>
      <div class="split">
        <aside class="brand-side">
          <a href="#" class="brand-back" @click.prevent="logout">
            <v-icon icon="mdi-arrow-left" size="18" class="mr-1" /> Log out
          </a>
          <img src="/biopay-features.webp" alt="BioPay — biometric payment solutions" class="brand-graphic" />
        </aside>

        <section class="form-side">
          <div class="form-wrap">
            <router-link to="/" class="auth-brand" aria-label="BioPay home">
              <img src="/biopay_logo_horizontal.svg" alt="BioPay" />
            </router-link>
            <div class="form-head">
              <h2 class="form-title">Set a new password</h2>
              <p class="form-subtitle">Your account was created with a temporary password. Choose your own before continuing.</p>
            </div>

            <v-form @submit.prevent="submit">
              <v-alert v-if="errorMessage" type="error" variant="tonal" class="mb-4" density="compact">
                {{ errorMessage }}
              </v-alert>
              <v-text-field
                v-model="oldPassword" label="Temporary password" :type="showOldPassword ? 'text' : 'password'"
                autocomplete="current-password" autofocus
              >
                <template #append-inner>
                  <v-btn
                    :icon="showOldPassword ? 'mdi-eye-off' : 'mdi-eye'" variant="text" density="compact"
                    :aria-label="showOldPassword ? 'Hide password' : 'Show password'"
                    @click="showOldPassword = !showOldPassword"
                  />
                </template>
              </v-text-field>
              <v-text-field
                v-model="newPassword" label="New password" :type="showNewPassword ? 'text' : 'password'"
                hint="At least 8 characters" persistent-hint autocomplete="new-password" class="mt-2"
              >
                <template #append-inner>
                  <v-btn
                    :icon="showNewPassword ? 'mdi-eye-off' : 'mdi-eye'" variant="text" density="compact"
                    :aria-label="showNewPassword ? 'Hide password' : 'Show password'"
                    @click="showNewPassword = !showNewPassword"
                  />
                </template>
              </v-text-field>
              <v-text-field
                v-model="confirmPassword" label="Confirm new password" type="password"
                autocomplete="new-password" class="mt-2"
              />
              <v-btn type="submit" block size="large" :loading="loading" class="mt-4 btn-accent">
                Set password and continue
              </v-btn>
            </v-form>
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

.form-side { width: 100%; min-height: 0; overflow-y: auto; overflow-x: hidden; background: #fff; display: flex; align-items: center; justify-content: center; padding: clamp(1.25rem, 4vh, 2.5rem) 1.5rem; }
.form-wrap { width: 100%; min-width: 0; max-width: 420px; }
.auth-brand { display: inline-flex; margin-bottom: 2rem; border-radius: 6px; }
.auth-brand img { display: block; width: 158px; height: auto; }
.auth-brand:focus-visible { outline: 3px solid #0d9488; outline-offset: 4px; }
.form-head { margin-bottom: 1.75rem; }
.form-title { font-size: 1.6rem; font-weight: 700; color: #0f172a; margin: 0 0 .3rem; letter-spacing: -.01em; }
.form-subtitle { color: #64748b; font-size: .95rem; margin: 0; }

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
