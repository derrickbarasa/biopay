<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const toast = useToast()

const token = (route.query.token as string) || ''
const password = ref('')
const confirmPassword = ref('')
const showPassword = ref(false)
const loading = ref(false)
const errorMessage = ref('')

const rules = {
  required: (v: string) => !!v || 'Required',
  minLen: (v: string) => v.length >= 8 || 'At least 8 characters',
}

async function handleSubmit() {
  errorMessage.value = ''
  if (!token) {
    errorMessage.value = 'This reset link is invalid. Request a new one'
    return
  }
  if (password.value.length < 8) {
    errorMessage.value = 'Password must be at least 8 characters'
    return
  }
  if (password.value !== confirmPassword.value) {
    errorMessage.value = 'Passwords do not match'
    return
  }
  loading.value = true
  try {
    await auth.resetPassword(token, password.value)
    toast.success('Password reset. Please sign in')
    router.push({ name: 'login' })
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Failed to reset password'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <v-app>
    <v-main class="login-bg">
      <router-link to="/login" class="back-link">
        <v-icon icon="mdi-arrow-left" size="18" class="mr-1" />
        Back to sign in
      </router-link>

      <v-container class="fill-height" fluid>
        <v-row justify="center" align="center" class="fill-height">
          <v-col cols="12" sm="8" md="5" lg="4">
            <v-card elevation="0" class="pa-2 login-card">
              <v-card-text class="text-center pt-6">
                <router-link to="/" class="text-decoration-none">
                  <img src="/biopay_logo_horizontal.svg" alt="BioPay" class="login-logo" />
                </router-link>
                <div class="text-body-2 text-medium-emphasis">Set a new password</div>
              </v-card-text>

              <v-card-text>
                <v-alert v-if="!token" type="warning" variant="tonal" density="compact" class="mb-4">
                  This link is missing its reset token. Request a new one from the
                  <router-link to="/forgot-password" class="link">forgot password</router-link> page.
                </v-alert>
                <v-form @submit.prevent="handleSubmit">
                  <v-alert v-if="errorMessage" type="error" variant="tonal" class="mb-4" density="compact">
                    {{ errorMessage }}
                  </v-alert>
                  <v-text-field
                    v-model="password" label="New password" prepend-inner-icon="mdi-lock-outline"
                    :type="showPassword ? 'text' : 'password'" :append-inner-icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'"
                    :rules="[rules.required, rules.minLen]" hint="At least 8 characters" autocomplete="new-password"
                    @click:append-inner="showPassword = !showPassword"
                  />
                  <v-text-field
                    v-model="confirmPassword" label="Confirm new password" prepend-inner-icon="mdi-lock-check-outline"
                    :type="showPassword ? 'text' : 'password'" :rules="[rules.required]" class="mt-2" autocomplete="new-password"
                  />
                  <v-btn type="submit" block color="primary" size="large" :loading="loading" :disabled="!token" class="mt-4">
                    Reset password
                  </v-btn>
                </v-form>
              </v-card-text>
            </v-card>
          </v-col>
        </v-row>
      </v-container>
    </v-main>
  </v-app>
</template>

<style scoped>
.login-bg {
  background: radial-gradient(circle at 15% 15%, rgba(204, 251, 241, .55), transparent 30rem), #f8fafc;
  position: relative;
}
.login-card { border: 1px solid rgba(15,23,42,.08); box-shadow: 0 24px 60px -28px rgba(15,23,42,.26) !important; }
.login-logo { width: 224px; height: auto; }
.link { color: #0d9488; }
.back-link {
  position: absolute;
  top: 20px;
  left: 24px;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  color: #475569;
  text-decoration: none;
  font-size: .875rem;
  font-weight: 500;
}
.back-link:hover { color: #0f172a; }
</style>
