<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { ORG_MODULES, COUNTRIES } from '@/types/user'
import { capitalFor } from '@/utils/countries'
import { isValidPhone, phoneRule } from '@/utils/phone'

interface Anchor { id: number; name: string; anchorCode: string }

const auth = useAuthStore()
const toast = useToast()
const router = useRouter()
const route = useRoute()

// Arriving from an anchor's own detail page (its "Add Organization" button) fixes and
// locks the anchor, and sends Cancel/Save back to that anchor rather than the generic list.
const fromAnchorId = ref<number | null>(Number(route.query.anchorId) || null)

const saving = ref(false)
const anchors = ref<Anchor[]>([])
const form = ref({
  organisationCode: '', name: '', authorisedName: '', authorisedFirstName: '', authorisedSurname: '', authorisedEmail: '', authorisedContact: '', address: '',
  country: '', capitalCity: '', verificationMethod: 'BIOMETRIC', anchorId: fromAnchorId.value,
  modules: [] as string[],
})

watch(() => form.value.country, (country, previous) => {
  if (country && country !== previous) form.value.capitalCity = capitalFor(country) || form.value.capitalCity
})

const VERIFICATION_METHODS = [
  { title: 'Biometric (fingerprint)', value: 'BIOMETRIC' },
  { title: 'Facial recognition', value: 'FACIAL' },
  { title: 'Both', value: 'BOTH' },
]

function verificationMethodIcon(method?: string) {
  if (method === 'FACIAL') return 'mdi-account-outline'
  if (method === 'BOTH') return 'mdi-account-multiple-check-outline'
  return 'mdi-fingerprint'
}

const required = (value: string) => !!value?.trim() || 'Required'
const emailRule = (value: string) => /.+@.+\..+/.test(value ?? '') || 'A valid email is required to sign in'

async function loadAnchors() {
  if (!auth.isSystemAdmin) return
  // A deactivated anchor can't be picked for a brand-new organisation.
  const res = await dispatch<{ results: Anchor[] }>('GET_ANCHORS', { status: 1 })
  anchors.value = res.results ?? []
}
onMounted(loadAnchors)

function goToList() {
  if (fromAnchorId.value) {
    router.push({ name: 'anchor-detail', params: { anchorId: fromAnchorId.value } })
    return
  }
  router.push({ name: 'organizations' })
}

async function save() {
  if (!form.value.name.trim() || !form.value.country || !form.value.verificationMethod) {
    toast.error('Complete the organization name, country and verification method')
    return
  }
  if (auth.isSystemAdmin && !form.value.anchorId) {
    toast.error('Choose an anchor for this organization')
    return
  }
  if (!/.+@.+\..+/.test(form.value.authorisedEmail)) {
    toast.error('A valid email is required to create the organization\'s sign-in account')
    return
  }
  if (form.value.authorisedContact && !isValidPhone(form.value.authorisedContact)) {
    toast.error('Include the country code in the phone number, e.g. +254712345678')
    return
  }
  if (!form.value.modules.length) {
    toast.error('Select at least one module')
    return
  }
  saving.value = true
  try {
    await dispatch('CREATE_ORGANIZATION', {
      ...form.value,
      targetAnchorId: auth.isSystemAdmin ? form.value.anchorId : undefined,
    })
    toast.success('Organization created. A temporary password was emailed to sign in.')
    goToList()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Save failed')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="organizations-page">
    <div class="d-flex align-center mb-4 ga-3">
      <v-btn icon="mdi-arrow-left" variant="text" aria-label="Back to organizations" @click="goToList" />
      <h1 class="page-title">New Organization</h1>
    </div>

    <v-card class="org-editor" variant="flat" border>
      <div class="editor-heading">
        <div>
          <div class="editor-title">New Organization</div>
          <p>A sign-in account is created automatically for the email below, with a temporary password emailed to it.</p>
        </div>
      </div>

      <v-form @submit.prevent="save">
        <div class="identity-grid">
          <section class="form-group" aria-labelledby="org-details-heading">
            <div id="org-details-heading" class="form-group-title"><v-icon icon="mdi-domain" size="19" /> Organization details</div>
            <v-select
              v-if="auth.isSystemAdmin" v-model="form.anchorId" :items="anchors" item-title="name" item-value="id"
              label="Anchor" :rules="[v => !!v || 'Required']" density="compact" hide-details="auto" prepend-inner-icon="mdi-bank-outline"
              :disabled="!!fromAnchorId"
            />
            <v-text-field v-model="form.name" label="Organization name" placeholder="e.g. Bright Future Trust" :rules="[required]" density="compact" hide-details="auto" />
            <v-autocomplete v-model="form.country" :items="COUNTRIES" label="Country" :rules="[required]" density="compact" hide-details="auto" />
            <v-text-field v-model="form.capitalCity" label="Capital city" placeholder="e.g. Nairobi" prepend-inner-icon="mdi-city-variant-outline" density="compact" hide-details="auto" />
            <v-text-field v-model="form.address" label="Address" placeholder="e.g. Westlands Road" prepend-inner-icon="mdi-map-marker-outline" density="compact" hide-details="auto" />
          </section>

          <section class="form-group" aria-labelledby="contact-details-heading">
            <div id="contact-details-heading" class="form-group-title"><v-icon icon="mdi-account-outline" size="19" /> Authorized contact</div>
            <v-text-field v-model="form.authorisedFirstName" label="Contact first name" placeholder="e.g. Amina" density="compact" hide-details="auto" />
            <v-text-field v-model="form.authorisedSurname" label="Contact surname" placeholder="e.g. Yusuf" density="compact" hide-details="auto" />
            <v-text-field
              v-model="form.authorisedEmail" label="Email (used to sign in)"
              placeholder="e.g. amina@brightfuture.org" type="email" :rules="[emailRule]" density="compact" hide-details="auto"
            />
            <v-text-field v-model="form.authorisedContact" label="Phone" placeholder="e.g. +254712345678" density="compact" hide-details="auto" :rules="[phoneRule]" />
            <v-select
              v-model="form.verificationMethod" :items="VERIFICATION_METHODS" label="Household verification method"
              :prepend-inner-icon="verificationMethodIcon(form.verificationMethod)" :rules="[required]" density="compact"
              hint="How field officers verify a household member's identity during registration and payment." persistent-hint
            />
          </section>
        </div>

        <section class="module-section" aria-labelledby="module-heading">
          <div class="module-heading-row">
            <div>
              <div id="module-heading" class="form-group-title"><v-icon icon="mdi-view-dashboard-outline" size="19" /> Enabled modules</div>
              <p>Teams only see and use the capabilities selected here.</p>
            </div>
            <span>{{ form.modules.length }} selected</span>
          </div>
          <div class="module-grid">
            <label v-for="m in ORG_MODULES" :key="m.code" class="module-option" :class="{ selected: form.modules.includes(m.code) }">
              <v-checkbox v-model="form.modules" :value="m.code" hide-details density="compact" />
              <span>{{ m.label }}</span>
            </label>
          </div>
        </section>

        <div class="editor-actions">
          <v-btn variant="flat" color="error" @click="goToList">Cancel</v-btn>
          <v-btn color="secondary" type="submit" :loading="saving" prepend-icon="mdi-check">Create organization</v-btn>
        </div>
      </v-form>
    </v-card>
  </div>
</template>

<style scoped>
.org-editor { padding: clamp(18px, 2.4vw, 26px); border-color: #cbd5e1 !important; background: #fff !important; }
.editor-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 16px; }
.editor-title { color: #0f172a; font-size: 1.15rem; font-weight: 750; letter-spacing: -.02em; }
.editor-heading p, .module-heading-row p { color: #64748b; font-size: .82rem; margin: 3px 0 0; }
.identity-grid { display: grid; grid-template-columns: 1fr 1fr; column-gap: clamp(20px, 4vw, 44px); row-gap: 10px; }
.form-group { min-width: 0; display: grid; gap: 10px; align-content: start; }
.form-group-title { display: flex; align-items: center; gap: 8px; color: #0f766e; font-size: .8rem; font-weight: 750; margin-bottom: 2px; }
.module-section { border-top: 1px solid #e2e8f0; margin-top: 14px; padding-top: 14px; }
.module-heading-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 10px; }
.module-heading-row > span { flex-shrink: 0; color: #0f766e; background: #ccfbf1; border-radius: 999px; padding: 5px 10px; font-size: .72rem; font-weight: 750; }
.module-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 10px; }
.module-option { min-width: 0; min-height: 50px; display: flex; align-items: center; gap: 2px; padding: 5px 8px; border: 1px solid #e2e8f0; border-radius: 12px; color: #475569; cursor: pointer; transition: border-color 180ms ease, background 180ms ease, color 180ms ease; }
.module-option:hover { border-color: #94a3b8; }
.module-option.selected { border-color: #0d9488; background: #f0fdfa; color: #0f766e; }
.module-option span { min-width: 0; font-size: .78rem; font-weight: 650; line-height: 1.2; }
.module-option :deep(.v-selection-control) { min-height: 36px; }
.editor-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 16px; }
@media (max-width: 900px) { .module-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 680px) {
  .identity-grid { grid-template-columns: 1fr; gap: 0; }
  .module-grid { grid-template-columns: 1fr; }
  .editor-actions :deep(.v-btn) { flex: 1; }
}
</style>
