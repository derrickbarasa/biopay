<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const email = ref('')
const loading = ref(false)
const errorMessage = ref('')
const sent = ref(false)

const rules = {
  required: (v: string) => !!v || 'Required',
  email: (v: string) => /.+@.+\..+/.test(v) || 'Enter a valid email',
}

async function handleSubmit() {
  errorMessage.value = ''
  if (!email.value) return
  loading.value = true
  try {
    await auth.requestPasswordReset(email.value)
    sent.value = true
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Something went wrong'
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
                <div class="text-body-2 text-medium-emphasis">Reset your password</div>
              </v-card-text>

              <v-card-text>
                <template v-if="sent">
                  <v-alert type="success" variant="tonal" density="compact">
                    If <strong>{{ email }}</strong> is registered, we've sent a link to reset your password.
                    It expires in 60 minutes.
                  </v-alert>
                  <div class="text-center text-body-2 mt-5">
                    <router-link to="/login" class="text-decoration-none link font-weight-medium">Back to sign in</router-link>
                  </div>
                </template>
                <v-form v-else @submit.prevent="handleSubmit">
                  <v-alert v-if="errorMessage" type="error" variant="tonal" class="mb-4" density="compact">
                    {{ errorMessage }}
                  </v-alert>
                  <p class="text-body-2 text-medium-emphasis mb-4">
                    Enter the email on your account and we'll send you a link to reset your password.
                  </p>
                  <v-text-field
                    v-model="email" label="Email" prepend-inner-icon="mdi-email-outline"
                    :rules="[rules.required, rules.email]" autocomplete="username"
                  />
                  <v-btn type="submit" block color="primary" size="large" :loading="loading" class="mt-2">
                    Send reset link
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
