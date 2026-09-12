<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

const auth = useAuthStore()
const toast = useToast()

type SettingsSection = 'profile' | 'authentication' | 'password'
const activeSection = ref<SettingsSection | null>(null)
const sections: { key: SettingsSection; icon: string; label: string; description: string }[] = [
  { key: 'profile', icon: 'mdi-account-outline', label: 'Profile', description: 'Name and account details' },
  { key: 'authentication', icon: 'mdi-shield-key-outline', label: 'Authentication', description: 'Email and authenticator app sign-in' },
  { key: 'password', icon: 'mdi-lock-outline', label: 'Password', description: 'Change your sign-in password' },
]

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const showOldPassword = ref(false)
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)
const saving = ref(false)
const savingProfile = ref(false)
const profileFirstName = ref(auth.user?.firstName ?? '')
const profileLastName = ref(auth.user?.lastName ?? auth.user?.surname ?? '')
const organizationName = ref('')

onMounted(async () => {
  if (auth.user?.partnerCode) {
    try {
      const res = await dispatch<{ results: { organisationCode: string; name: string }[] }>('GET_ORGANIZATIONS')
      organizationName.value = res.results?.find((o) => o.organisationCode === auth.user?.partnerCode)?.name ?? auth.user.partnerCode
    } catch {
      organizationName.value = auth.user.partnerCode
    }
  }
})

async function saveProfile() {
  if (!profileFirstName.value.trim()) {
    toast.error('First name is required')
    return
  }
  savingProfile.value = true
  try {
    await auth.updateProfile(profileFirstName.value.trim(), profileLastName.value.trim())
    toast.success('Profile updated')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to update profile')
  } finally {
    savingProfile.value = false
  }
}

async function changePassword() {
  if (newPassword.value.length < 8) {
    toast.error('New password must be at least 8 characters')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    toast.error('Passwords do not match')
    return
  }
  saving.value = true
  try {
    await dispatch('CHANGE_PASSWORD', { oldPassword: oldPassword.value, newPassword: newPassword.value })
    toast.success('Password changed successfully')
    oldPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to change password')
  } finally {
    saving.value = false
  }
}

// ---- Authenticator app (TOTP) enrollment -----------------------------------

const enrollDialog = ref(false)
const disableDialog = ref(false)
const enrolling = ref(false)
const confirming = ref(false)
const disabling = ref(false)
const qrCode = ref('')
const secret = ref('')
const confirmCode = ref('')
const disablePassword = ref('')
const showDisablePassword = ref(false)

async function startEnroll() {
  enrolling.value = true
  try {
    const res = await dispatch<{ qrCode: string; secret: string }>('TOTP_SETUP_INIT')
    qrCode.value = res.qrCode
    secret.value = res.secret
    confirmCode.value = ''
    enrollDialog.value = true
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to start enrollment')
  } finally {
    enrolling.value = false
  }
}

async function confirmEnroll() {
  confirming.value = true
  try {
    await dispatch('TOTP_SETUP_CONFIRM', { code: confirmCode.value })
    toast.success('Authenticator app enabled')
    enrollDialog.value = false
    await auth.refreshProfile()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Incorrect code')
  } finally {
    confirming.value = false
  }
}

function openDisable() {
  disablePassword.value = ''
  disableDialog.value = true
}

async function confirmDisable() {
  disabling.value = true
  try {
    await dispatch('TOTP_DISABLE', { password: disablePassword.value })
    toast.success('Authenticator app disabled')
    disableDialog.value = false
    await auth.refreshProfile()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to disable')
  } finally {
    disabling.value = false
  }
}

// ---- Email OTP toggle -- defaults on; disabling needs TOTP already enabled -----

const emailEnabling = ref(false)
const emailDisableDialog = ref(false)
const emailDisabling = ref(false)
const emailDisablePassword = ref('')
const showEmailDisablePassword = ref(false)

async function enableEmailOtp() {
  emailEnabling.value = true
  try {
    await dispatch('EMAIL_OTP_ENABLE')
    toast.success('Email verification enabled')
    await auth.refreshProfile()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to enable')
  } finally {
    emailEnabling.value = false
  }
}

function openEmailDisable() {
  emailDisablePassword.value = ''
  emailDisableDialog.value = true
}

async function confirmEmailDisable() {
  emailDisabling.value = true
  try {
    await dispatch('EMAIL_OTP_DISABLE', { password: emailDisablePassword.value })
    toast.success('Email verification disabled')
    emailDisableDialog.value = false
    await auth.refreshProfile()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to disable')
  } finally {
    emailDisabling.value = false
  }
}
</script>

<template>
  <div>
    <h1 class="page-title mb-4">Settings</h1>

    <v-card variant="flat" border class="pa-4 mb-4 identity-card">
      <div class="d-flex align-center">
        <v-avatar color="primary" size="48" class="mr-3">
          <span class="text-h6">{{ auth.initials }}</span>
        </v-avatar>
        <div class="min-width-0">
          <div class="text-subtitle-1 font-weight-medium">{{ auth.fullName }}</div>
          <div class="text-caption text-medium-emphasis">{{ auth.user?.email }}</div>
        </div>
        <v-spacer />
        <v-chip color="secondary" variant="tonal" class="ml-2">{{ auth.roleLabel }}</v-chip>
      </div>
      <div v-if="auth.user?.partnerCode" class="mt-2 text-body-2">
        Organization: {{ organizationName || auth.user.partnerCode }}
      </div>
    </v-card>

    <div class="section-grid mb-4">
      <button
        v-for="s in sections" :key="s.key" type="button" class="section-tile"
        :class="{ active: activeSection === s.key }" @click="activeSection = activeSection === s.key ? null : s.key"
      >
        <v-icon :icon="s.icon" size="22" />
        <div class="section-tile-copy">
          <div class="section-tile-label">{{ s.label }}</div>
          <div class="section-tile-description">{{ s.description }}</div>
        </div>
        <v-icon icon="mdi-chevron-right" size="18" class="section-tile-chevron" />
      </button>
    </div>

    <v-card v-if="activeSection === 'profile'" variant="flat" border class="pa-4 mb-4">
      <v-card-title class="pl-0 d-flex align-center">
        Profile
        <v-spacer />
        <v-btn icon="mdi-close" variant="text" size="small" aria-label="Close" @click="activeSection = null" />
      </v-card-title>
      <v-card-text class="pl-0">
        <div class="profile-grid">
          <v-text-field v-model="profileFirstName" label="First name" autocomplete="given-name" />
          <v-text-field v-model="profileLastName" label="Last name" autocomplete="family-name" />
          <v-text-field
            :model-value="auth.user?.email" label="Email address" readonly
            :hint="auth.isSystemAdmin ? 'Sign-in email cannot be changed here.' : 'Contact your anchor or organisation administrator to change the sign-in email.'"
            persistent-hint
          />
          <v-text-field :model-value="auth.roleLabel" label="Role" readonly hint="Contact your administrator to change your role." persistent-hint />
        </div>
        <v-btn color="secondary" class="mt-4" :loading="savingProfile" @click="saveProfile">Save profile</v-btn>
      </v-card-text>
    </v-card>

    <v-card v-if="activeSection === 'authentication'" variant="flat" border class="pa-4 mb-4">
      <v-card-title class="pl-0 d-flex align-center">
        Authentication
        <v-spacer />
        <v-btn icon="mdi-close" variant="text" size="small" aria-label="Close" @click="activeSection = null" />
      </v-card-title>
      <v-card-text class="pl-0">
        <div class="d-flex align-center justify-space-between py-2">
          <div class="d-flex align-center">
            <v-icon icon="mdi-email-check-outline" class="mr-3" :color="auth.user?.emailOtpEnabled ? 'success' : 'medium-emphasis'" />
            <div>
              <div class="text-body-2 font-weight-medium">Email</div>
              <div class="text-caption text-medium-emphasis">
                <template v-if="auth.user?.emailOtpEnabled">Send a 6-digit code to your inbox at sign-in</template>
                <template v-else>Turned off &mdash; you sign in with the authenticator app only</template>
              </div>
            </div>
          </div>
          <v-btn
            v-if="auth.user?.emailOtpEnabled" size="small" variant="outlined" color="error"
            :disabled="!auth.user?.totpEnabled"
            @click="openEmailDisable"
          >
            Disable
          </v-btn>
          <v-btn v-else size="small" color="secondary" :loading="emailEnabling" @click="enableEmailOtp">Enable</v-btn>
        </div>
        <p v-if="auth.user?.emailOtpEnabled && !auth.user?.totpEnabled" class="text-caption text-medium-emphasis mb-0">
          Enable the authenticator app before you can turn this off.
        </p>
        <v-divider class="my-2" />
        <div class="d-flex align-center justify-space-between py-2">
          <div class="d-flex align-center">
            <v-icon icon="mdi-cellphone-key" class="mr-3" :color="auth.user?.totpEnabled ? 'success' : 'medium-emphasis'" />
            <div>
              <div class="text-body-2 font-weight-medium">Authenticator app</div>
              <div class="text-caption text-medium-emphasis">Use Google Authenticator, Authy or similar for faster sign-in</div>
            </div>
          </div>
          <v-btn v-if="auth.user?.totpEnabled" size="small" variant="outlined" color="error" @click="openDisable">Disable</v-btn>
          <v-btn v-else size="small" color="secondary" :loading="enrolling" @click="startEnroll">Enable</v-btn>
        </div>
      </v-card-text>
    </v-card>

    <v-card v-if="activeSection === 'password'" variant="flat" border class="pa-4 mb-4">
      <v-card-title class="pl-0 d-flex align-center">
        Password
        <v-spacer />
        <v-btn icon="mdi-close" variant="text" size="small" aria-label="Close" @click="activeSection = null" />
      </v-card-title>
      <v-card-text class="pl-0">
        <v-text-field v-model="oldPassword" label="Current password" :type="showOldPassword ? 'text' : 'password'" autocomplete="current-password">
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
          hint="At least 8 characters" persistent-hint autocomplete="new-password"
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
          class="mt-2" autocomplete="new-password"
        >
          <template #append-inner>
            <v-btn
              :icon="showConfirmPassword ? 'mdi-eye-off' : 'mdi-eye'" variant="text" density="compact"
              :aria-label="showConfirmPassword ? 'Hide password' : 'Show password'"
              @click="showConfirmPassword = !showConfirmPassword"
            />
          </template>
        </v-text-field>
        <v-btn color="secondary" class="mt-4" :loading="saving" @click="changePassword">Update Password</v-btn>
      </v-card-text>
    </v-card>

    <v-dialog v-model="enrollDialog" max-width="420">
      <v-card>
        <dialog-close-button @close="enrollDialog = false" />
        <v-card-title>Enable Authenticator App</v-card-title>
        <v-card-text>
          <p class="text-body-2 mb-3">Scan this QR code with Google Authenticator, Authy or a similar app.</p>
          <div class="text-center mb-3">
            <img v-if="qrCode" :src="qrCode" alt="TOTP QR code" width="200" height="200" />
          </div>
          <p class="text-caption text-medium-emphasis mb-1">Can't scan it? Enter this key manually:</p>
          <code class="d-block mb-4 text-body-2">{{ secret }}</code>
          <v-text-field
            v-model="confirmCode" label="Enter the 6-digit code from your app"
            inputmode="numeric" maxlength="6" autofocus
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="flat" color="error" @click="enrollDialog = false">Cancel</v-btn>
          <v-btn variant="flat" color="secondary" :loading="confirming" @click="confirmEnroll">Confirm</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="disableDialog" max-width="420">
      <v-card>
        <dialog-close-button @close="disableDialog = false" />
        <v-card-title>Disable Authenticator App</v-card-title>
        <v-card-text>
          <p class="text-body-2 mb-3">Enter your current password to confirm.</p>
          <v-text-field v-model="disablePassword" label="Current password" :type="showDisablePassword ? 'text' : 'password'" autofocus autocomplete="current-password">
            <template #append-inner>
              <v-btn
                :icon="showDisablePassword ? 'mdi-eye-off' : 'mdi-eye'" variant="text" density="compact"
                :aria-label="showDisablePassword ? 'Hide password' : 'Show password'"
                @click="showDisablePassword = !showDisablePassword"
              />
            </template>
          </v-text-field>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="flat" color="error" @click="disableDialog = false">Cancel</v-btn>
          <v-btn variant="flat" color="error" :loading="disabling" @click="confirmDisable">Disable</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="emailDisableDialog" max-width="420">
      <v-card>
        <dialog-close-button @close="emailDisableDialog = false" />
        <v-card-title>Disable Email Verification</v-card-title>
        <v-card-text>
          <p class="text-body-2 mb-3">Enter your current password to confirm. You'll sign in with the authenticator app only from now on.</p>
          <v-text-field v-model="emailDisablePassword" label="Current password" :type="showEmailDisablePassword ? 'text' : 'password'" autofocus autocomplete="current-password">
            <template #append-inner>
              <v-btn
                :icon="showEmailDisablePassword ? 'mdi-eye-off' : 'mdi-eye'" variant="text" density="compact"
                :aria-label="showEmailDisablePassword ? 'Hide password' : 'Show password'"
                @click="showEmailDisablePassword = !showEmailDisablePassword"
              />
            </template>
          </v-text-field>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="flat" color="error" @click="emailDisableDialog = false">Cancel</v-btn>
          <v-btn variant="flat" color="error" :loading="emailDisabling" @click="confirmEmailDisable">Disable</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.identity-card { border-radius: 14px !important; }
.min-width-0 { min-width: 0; }
.section-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 10px; }
.profile-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 16px; }
@media (max-width: 560px) { .profile-grid { grid-template-columns: 1fr; } }
.section-tile {
  display: flex; align-items: center; gap: 12px; padding: 14px; border: 1px solid #0f172a; border-radius: 10px;
  background: #fff; color: #0f172a; text-align: left; cursor: pointer; transition: background 150ms ease, color 150ms ease;
}
.section-tile.active { background: #0d9488; border-color: #0d9488; color: #fff; }
.section-tile.active .section-tile-description { color: rgba(255, 255, 255, .82); }
.section-tile-copy { min-width: 0; flex: 1; }
.section-tile-label { font-size: .88rem; font-weight: 700; }
.section-tile-description { margin-top: 2px; font-size: .72rem; color: #64748b; overflow-wrap: anywhere; }
.section-tile-chevron { flex: 0 0 auto; opacity: .6; }
</style>
