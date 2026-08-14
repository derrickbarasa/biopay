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
const loading = ref(false)
const errorMessage = ref('')

const rules = {
  required: (v: string) => !!v || 'Required',
  email: (v: string) => /.+@.+\..+/.test(v) || 'Enter a valid email',
  minLen: (v: string) => v.length >= 8 || 'At least 8 characters',
}

async function handleSubmit() {
  errorMessage.value = ''
  if (!form.value.name || !form.value.email || !form.value.password) return
  if (form.value.password.length < 8) {
    errorMessage.value = 'Password must be at least 8 characters'
    return
  }
  if (form.value.password !== form.value.confirmPassword) {
    errorMessage.value = 'Passwords do not match'
    return
  }
  loading.value = true
  try {
    await auth.signup(form.value)
    toast.success('Account created. Verify to continue')
    router.push({ name: 'verify-otp' })
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : 'Signup failed'
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
          <v-col cols="12" sm="9" md="6" lg="5">
            <v-card elevation="0" class="pa-2 login-card">
              <v-card-text class="text-center pt-6">
                <router-link to="/" class="text-decoration-none">
                  <img src="/biopay_logo_horizontal.svg" alt="BioPay" class="login-logo" />
                </router-link>
                <div class="text-body-2 text-medium-emphasis">Create your anchor account</div>
              </v-card-text>

              <v-card-text>
                <v-form @submit.prevent="handleSubmit">
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
                    :type="showPassword ? 'text' : 'password'" :rules="[rules.required]" class="mt-2" autocomplete="new-password"
                  />

                  <v-btn type="submit" block color="primary" size="large" :loading="loading" class="mt-4">
                    Create account
                  </v-btn>
                </v-form>

                <div class="text-center text-body-2 mt-5">
                  Already have an account?
                  <router-link to="/login" class="text-decoration-none link font-weight-medium">Sign in</router-link>
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
