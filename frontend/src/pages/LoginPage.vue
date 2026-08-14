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
    <v-main class="login-bg">
      <router-link to="/" class="back-link">
        <v-icon icon="mdi-arrow-left" size="18" class="mr-1" />
        Back to website
      </router-link>

      <v-container class="fill-height" fluid>
        <v-row justify="center" align="center" class="fill-height">
          <v-col cols="12" sm="8" md="5" lg="4">
            <v-card elevation="0" class="pa-2 login-card">
              <v-card-text class="text-center pt-6">
                <router-link to="/" class="text-decoration-none">
                  <img src="/biopay_logo_horizontal.svg" alt="BioPay" class="login-logo" />
                </router-link>
                <div class="text-body-2 text-medium-emphasis">Biometric payments for NGOs & Anchors</div>
              </v-card-text>

              <v-card-text>
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

                <div class="text-center text-body-2 mt-5">
                  Don't have an account?
                  <router-link to="/signup" class="text-decoration-none link font-weight-medium">Sign up</router-link>
                </div>
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
  background: radial-gradient(circle at 15% 15%, rgba(20, 184, 166, .38), transparent 28rem), linear-gradient(135deg, #062f2d 0%, #047857 100%);
  position: relative;
}
.login-card { border: 1px solid rgba(255,255,255,.35); box-shadow: 0 30px 70px -26px rgba(0,0,0,.45) !important; }
.login-logo { width: 224px; height: auto; }
.link { color: #0d9488; }
.back-link {
  position: absolute;
  top: 20px;
  left: 24px;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  color: rgba(255, 255, 255, .88);
  text-decoration: none;
  font-size: .875rem;
  font-weight: 500;
}
.back-link:hover { color: #fff; }
</style>
