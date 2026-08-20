<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

const auth = useAuthStore()
const toast = useToast()

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const saving = ref(false)
const savingProfile = ref(false)
const profileFirstName = ref(auth.user?.firstName ?? '')
const profileLastName = ref(auth.user?.lastName ?? auth.user?.otherNames ?? '')
const organizationName = ref('')

onMounted(async () => {
  if (auth.user?.partnerCode) {
    try {
      const res = await dispatch<{ results: { name: string }[] }>('GET_ORGANIZATION', { organisationCode: auth.user.partnerCode })
      organizationName.value = res.results?.[0]?.name ?? auth.user.partnerCode
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
</script>

<template>
  <div>
    <h1 class="text-h5 font-weight-bold mb-4">Settings</h1>

    <v-row>
      <v-col cols="12" md="6">
        <v-card variant="flat" border class="pa-4">
          <v-card-title class="pl-0">Profile</v-card-title>
          <v-card-text class="pl-0">
            <div class="d-flex align-center mb-4">
              <v-avatar color="primary" size="48" class="mr-3">
                <span class="text-h6">{{ auth.initials }}</span>
              </v-avatar>
              <div>
                <div class="text-subtitle-1 font-weight-medium">{{ auth.fullName }}</div>
                <div class="text-caption text-medium-emphasis">{{ auth.user?.email }}</div>
              </div>
            </div>
            <v-chip color="secondary" variant="tonal">{{ auth.roleLabel }}</v-chip>
            <div v-if="auth.user?.partnerCode" class="mt-2 text-body-2">
              Organization: {{ organizationName || auth.user.partnerCode }}
            </div>
            <v-divider class="my-4" />
            <v-text-field v-model="profileFirstName" label="First name" autocomplete="given-name" />
            <v-text-field v-model="profileLastName" label="Last name" autocomplete="family-name" />
            <v-text-field :model-value="auth.user?.email" label="Email address" readonly hint="Contact an administrator to change the sign-in email." persistent-hint />
            <v-btn color="primary" class="mt-4" :loading="savingProfile" @click="saveProfile">Save profile</v-btn>
          </v-card-text>
        </v-card>

        <v-card variant="flat" border class="pa-4 mt-4">
          <v-card-title class="pl-0">Two-Factor Authentication</v-card-title>
          <v-card-text class="pl-0">
            <div class="d-flex align-center justify-space-between py-2">
              <div class="d-flex align-center">
                <v-icon icon="mdi-email-check-outline" class="mr-3" color="success" />
                <div>
                  <div class="text-body-2 font-weight-medium">Email</div>
                  <div class="text-caption text-medium-emphasis">Always enabled as your default verification method</div>
                </div>
              </div>
              <v-chip color="success" variant="tonal" size="small">Enabled</v-chip>
            </div>
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
              <v-btn v-else size="small" color="primary" :loading="enrolling" @click="startEnroll">Enable</v-btn>
            </div>
          </v-card-text>
        </v-card>
      </v-col>

      <v-col cols="12" md="6">
        <v-card variant="flat" border class="pa-4">
          <v-card-title class="pl-0">Change Password</v-card-title>
          <v-card-text class="pl-0">
            <v-text-field v-model="oldPassword" label="Current password" type="password" />
            <v-text-field v-model="newPassword" label="New password" type="password" hint="At least 8 characters" persistent-hint />
            <v-text-field v-model="confirmPassword" label="Confirm new password" type="password" class="mt-2" />
            <v-btn color="primary" class="mt-4" :loading="saving" @click="changePassword">Update Password</v-btn>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-dialog v-model="enrollDialog" max-width="420">
      <v-card>
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
          <v-btn variant="text" @click="enrollDialog = false">Cancel</v-btn>
          <v-btn color="primary" :loading="confirming" @click="confirmEnroll">Confirm</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="disableDialog" max-width="420">
      <v-card>
        <v-card-title>Disable Authenticator App</v-card-title>
        <v-card-text>
          <p class="text-body-2 mb-3">Enter your current password to confirm.</p>
          <v-text-field v-model="disablePassword" label="Current password" type="password" autofocus />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="disableDialog = false">Cancel</v-btn>
          <v-btn color="error" :loading="disabling" @click="confirmDisable">Disable</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
