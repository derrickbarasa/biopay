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
const showConfirmPassword = ref(false)
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

        <section class="form-side">
          <div class="form-wrap">
            <a href="#" class="back-link" @click.prevent="logout">
              <v-icon icon="mdi-arrow-left" size="18" class="mr-1" /> Log out
            </a>
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
                v-model="confirmPassword" label="Confirm new password" :type="showConfirmPassword ? 'text' : 'password'"
                autocomplete="new-password" class="mt-2"
              >
                <template #append-inner>
                  <v-btn
                    :icon="showConfirmPassword ? 'mdi-eye-off' : 'mdi-eye'" variant="text" density="compact"
                    :aria-label="showConfirmPassword ? 'Hide password' : 'Show password'"
                    @click="showConfirmPassword = !showConfirmPassword"
                  />
                </template>
              </v-text-field>
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
.form-head { margin-bottom: 1.75rem; }
.form-title { font-size: 1.6rem; font-weight: 700; color: #0f172a; margin: 0 0 .3rem; letter-spacing: -.01em; }
.form-subtitle { color: #64748b; font-size: .95rem; margin: 0; }

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
