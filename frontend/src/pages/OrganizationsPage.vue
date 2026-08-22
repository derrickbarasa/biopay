<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { ORG_MODULES, COUNTRIES } from '@/types/user'
import { capitalFor } from '@/utils/countries'

interface Organization {
  organisationCode: string
  name: string
  authorisedName?: string
  authorisedEmail?: string
  authorisedContact?: string
  address?: string
  country?: string
  capitalCity?: string
  verificationMethod?: string
  status: number
  createdAt?: string
}

const auth = useAuthStore()
const toast = useToast()

const loading = ref(true)
const statusFilter = ref<number | null>(null)
const organizations = ref<Organization[]>([])
const tableSearch = ref('')
const dialog = ref(false)
const editing = ref(false)
const saving = ref(false)
const form = ref({
  organisationCode: '', name: '', authorisedName: '', authorisedEmail: '', authorisedContact: '', address: '',
  country: '', capitalCity: '', verificationMethod: 'BIOMETRIC',
  modules: [] as string[],
})

// Auto-fills the capital when a country is picked; still a plain editable field afterwards
// in case the operator wants to record a different city.
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

function verificationMethodLabel(method?: string) {
  if (method === 'FACIAL') return 'Facial'
  if (method === 'BOTH') return 'Both'
  return 'Fingerprint'
}

const required = (value: string) => !!value?.trim() || 'Required'
const emailRule = (value: string) => !value || /.+@.+\..+/.test(value) || 'Enter a valid email'

const headers = [
  { title: 'Code', key: 'organisationCode' },
  { title: 'Name', key: 'name' },
  { title: 'Contact', key: 'authorisedName' },
  { title: 'Email', key: 'authorisedEmail' },
  { title: 'Country', key: 'country' },
  { title: 'Verification', key: 'verificationMethod' },
  { title: 'Status', key: 'status' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'end' as const },
]

async function load() {
  loading.value = true
  try {
    const res = await dispatch<{ results: Organization[] }>('GET_ORGANIZATIONS', { status: statusFilter.value ?? undefined })
    organizations.value = res.results
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load organizations')
  } finally {
    loading.value = false
  }
}

watch(statusFilter, load)

onMounted(load)

function openCreate() {
  editing.value = false
  form.value = {
    organisationCode: '', name: '', authorisedName: '', authorisedEmail: '', authorisedContact: '', address: '',
    country: '', capitalCity: '', verificationMethod: 'BIOMETRIC', modules: [],
  }
  dialog.value = true
}

async function openEdit(org: Organization) {
  editing.value = true
  form.value = {
    organisationCode: org.organisationCode, name: org.name,
    authorisedName: org.authorisedName ?? '', authorisedEmail: org.authorisedEmail ?? '',
    authorisedContact: org.authorisedContact ?? '', address: org.address ?? '',
    country: org.country ?? '', capitalCity: org.capitalCity ?? '', verificationMethod: org.verificationMethod ?? 'BIOMETRIC',
    modules: [],
  }
  dialog.value = true
  try {
    const res = await dispatch<{ results: string[] }>('GET_ORGANIZATION_MODULES', { organisationCode: org.organisationCode })
    form.value.modules = res.results
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load modules')
  }
}

async function remove(org: Organization) {
  try {
    await dispatch('DELETE_ORGANIZATION', { organisationCode: org.organisationCode })
    toast.success('Organization deleted')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Delete failed')
  }
}

async function save() {
  if (!form.value.organisationCode.trim() || !form.value.name.trim() || !form.value.country || !form.value.verificationMethod) {
    toast.error('Complete the organization code, name, country and verification method')
    return
  }
  if (form.value.authorisedEmail && !/.+@.+\..+/.test(form.value.authorisedEmail)) {
    toast.error('Enter a valid authorized contact email')
    return
  }
  if (!form.value.modules.length) {
    toast.error('Select at least one module')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await Promise.all([
        dispatch('UPDATE_ORGANIZATION', form.value),
        dispatch('UPDATE_ORGANIZATION_MODULES', { organisationCode: form.value.organisationCode, modules: form.value.modules }),
      ])
      toast.success('Organization updated')
    } else {
      await dispatch('CREATE_ORGANIZATION', { ...form.value, anchorId: auth.user?.anchorId })
      toast.success('Organization created')
    }
    dialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Save failed')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(org: Organization) {
  try {
    await dispatch('TOGGLE_ORGANIZATION_STATUS', { organisationCode: org.organisationCode, status: org.status === 1 ? 0 : 1 })
    toast.success('Status updated')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Update failed')
  }
}
</script>

<template>
  <div class="organizations-page">
    <div class="page-heading d-flex align-center justify-space-between mb-5 ga-4">
      <div>
        <h1 class="page-title">Organizations</h1>
        <p>Configure delivery partners, verification policy and programme access.</p>
      </div>
      <v-btn color="secondary" prepend-icon="mdi-domain-plus" @click="openCreate">New Organization</v-btn>
    </div>

    <v-dialog v-model="dialog" max-width="880">
      <v-card class="org-editor" variant="flat" border>
        <div class="editor-heading">
          <div>
            <div class="editor-title">{{ editing ? 'Edit Organization' : 'New Organization' }}</div>
            <p>{{ editing ? 'Update the organization profile and programme access.' : 'Create the delivery partner, then choose what its teams can operate.' }}</p>
          </div>
          <v-btn icon="mdi-close" variant="text" size="small" aria-label="Close organization form" @click="dialog = false" />
        </div>

        <v-form @submit.prevent="save">
          <div class="identity-grid">
            <section class="form-group" aria-labelledby="org-details-heading">
              <div id="org-details-heading" class="form-group-title"><v-icon icon="mdi-domain" size="19" /> Organization details</div>
              <v-text-field v-model="form.organisationCode" label="Organization code" :disabled="editing" :rules="[required]" density="compact" hide-details="auto" />
              <v-text-field v-model="form.name" label="Organization name" :rules="[required]" density="compact" hide-details="auto" />
              <v-autocomplete v-model="form.country" :items="COUNTRIES" label="Country" :rules="[required]" density="compact" hide-details="auto" />
              <v-text-field v-model="form.capitalCity" label="Capital city" prepend-inner-icon="mdi-city-variant-outline" density="compact" hide-details="auto" />
            </section>

            <section class="form-group" aria-labelledby="contact-details-heading">
              <div id="contact-details-heading" class="form-group-title"><v-icon icon="mdi-account-outline" size="19" /> Authorized contact</div>
              <v-text-field v-model="form.authorisedName" label="Contact name" density="compact" hide-details="auto" />
              <v-text-field v-model="form.authorisedEmail" label="Email" type="email" :rules="[emailRule]" density="compact" hide-details="auto" />
              <v-text-field v-model="form.authorisedContact" label="Phone" density="compact" hide-details="auto" />
            </section>
          </div>

          <div class="policy-row">
            <v-text-field v-model="form.address" label="Address" prepend-inner-icon="mdi-map-marker-outline" density="compact" hide-details="auto" />
            <v-select v-model="form.verificationMethod" :items="VERIFICATION_METHODS" label="Verification method" :prepend-inner-icon="verificationMethodIcon(form.verificationMethod)" :rules="[required]" density="compact" hide-details="auto" />
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
            <v-btn variant="text" @click="dialog = false">Cancel</v-btn>
            <v-btn color="secondary" type="submit" :loading="saving" prepend-icon="mdi-check">
              {{ editing ? 'Save changes' : 'Create organization' }}
            </v-btn>
          </div>
        </v-form>
      </v-card>
    </v-dialog>

    <v-card variant="flat" border>
      <v-card-text>
        <div class="d-flex ga-3 flex-wrap">
          <v-select
            v-model="statusFilter" :items="[{ title: 'Active', value: 1 }, { title: 'Inactive', value: 0 }]"
            label="Status" clearable hide-details density="compact" style="max-width: 220px"
          />
          <v-text-field v-model="tableSearch" prepend-inner-icon="mdi-magnify" label="Search" clearable hide-details density="compact" style="max-width: 260px" />
        </div>
      </v-card-text>
      <v-data-table :headers="headers" :items="organizations" :search="tableSearch" :loading="loading">
        <template #item.status="{ item }">
          <v-chip size="small" :color="item.status === 1 ? 'success' : 'error'" variant="tonal">{{ item.status === 1 ? 'Active' : 'Inactive' }}</v-chip>
        </template>
        <template #item.country="{ item }">{{ item.country || '—' }}</template>
        <template #item.verificationMethod="{ item }">
          <v-chip size="small" color="primary" variant="tonal">
            <v-icon :icon="verificationMethodIcon(item.verificationMethod)" start />
            {{ verificationMethodLabel(item.verificationMethod) }}
          </v-chip>
        </template>
        <template #item.actions="{ item }">
          <v-btn icon="mdi-pencil" variant="text" size="small" :aria-label="`Edit ${item.name}`" @click="openEdit(item)" />
          <v-btn :icon="item.status === 1 ? 'mdi-toggle-switch-off-outline' : 'mdi-toggle-switch'" variant="text" size="small" :aria-label="`${item.status === 1 ? 'Deactivate' : 'Activate'} ${item.name}`" @click="toggleStatus(item)" />
          <v-btn icon="mdi-delete" variant="text" size="small" color="error" :aria-label="`Delete ${item.name}`" @click="remove(item)" />
        </template>
      </v-data-table>
    </v-card>
  </div>
</template>

<style scoped>
.page-heading h1 { color: #0f172a; letter-spacing: -.025em; }
.page-heading p { color: #64748b; font-size: .9rem; margin: 5px 0 0; }
.org-editor { padding: clamp(18px, 2.4vw, 26px); border-color: #cbd5e1 !important; background: #fff !important; }
.editor-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 16px; }
.editor-title { color: #0f172a; font-size: 1.15rem; font-weight: 750; letter-spacing: -.02em; }
.editor-heading p, .module-heading-row p { color: #64748b; font-size: .82rem; margin: 3px 0 0; }
.identity-grid { display: grid; grid-template-columns: 1fr 1fr; column-gap: clamp(20px, 4vw, 44px); row-gap: 10px; }
.form-group { min-width: 0; display: grid; gap: 10px; align-content: start; }
.form-group-title { display: flex; align-items: center; gap: 8px; color: #0f766e; font-size: .8rem; font-weight: 750; margin-bottom: 2px; }
.policy-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 12px; }
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
  .page-heading { align-items: flex-start !important; flex-direction: column; }
  .page-heading :deep(.v-btn) { width: 100%; }
  .identity-grid, .policy-row { grid-template-columns: 1fr; gap: 0; }
  .module-grid { grid-template-columns: 1fr; }
  .editor-actions :deep(.v-btn) { flex: 1; }
}
</style>
