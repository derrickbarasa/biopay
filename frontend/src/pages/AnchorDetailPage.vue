<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { ORG_MODULES, COUNTRIES } from '@/types/user'
import { capitalFor } from '@/utils/countries'

interface Anchor {
  id: number
  anchorCode: string
  name: string
  authorisedName?: string
  authorisedEmail?: string
  authorisedContact?: string
  address?: string
  country?: string
  city?: string
  status: number
}

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
}

interface UserRow {
  id: number
  email: string
  firstName?: string
  surname?: string
  anchorId?: number
  userScope: string
  roleId?: number
  roleName?: string
  status: number
}

interface Role {
  id: number
  name: string
  scope: string
  anchorId?: number | null
  builtIn?: boolean
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToast()
const { confirmAction } = useConfirm()
const loading = ref(true)
const anchor = ref<Anchor | null>(null)
const organizations = ref<Organization[]>([])
const users = ref<UserRow[]>([])
const roles = ref<Role[]>([])

const anchorId = computed(() => Number(route.params.anchorId))
const anchorUsers = computed(() => users.value.filter((user) => user.anchorId === anchorId.value && user.userScope === 'ANCHOR'))

const activeTab = ref('profile')

const organizationHeaders = [
  { title: 'Organization', key: 'name' },
  { title: 'Organization Code', key: 'organisationCode', minWidth: 172, nowrap: true },
  { title: 'Authorized contact', key: 'authorisedName' },
  { title: 'Country', key: 'country' },
  { title: 'Status', key: 'status' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'start' as const, width: 168, minWidth: 168, fixed: true, nowrap: true },
]

const userHeaders = [
  { title: 'User', key: 'email' },
  { title: 'Role', key: 'roleName' },
  { title: 'Status', key: 'status' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'start' as const, width: 148, minWidth: 148, fixed: true, nowrap: true },
]

function displayName(user: UserRow) {
  return [user.firstName, user.surname].filter(Boolean).join(' ') || 'Name not set'
}

async function load() {
  if (!Number.isInteger(anchorId.value) || anchorId.value <= 0) {
    loading.value = false
    return
  }

  loading.value = true
  try {
    const [anchorResponse, organizationResponse, userResponse] = await Promise.all([
      dispatch<{ results: Anchor[] }>('GET_ANCHORS'),
      dispatch<{ results: Organization[] }>('GET_ORGANIZATIONS', { targetAnchorId: anchorId.value }),
      dispatch<{ results: UserRow[] }>('GET_USERS'),
    ])
    anchor.value = (anchorResponse.results ?? []).find((item) => item.id === anchorId.value) ?? null
    organizations.value = organizationResponse.results ?? []
    users.value = userResponse.results ?? []
  } catch (error) {
    toast.error(error instanceof Error ? error.message : 'Unable to load anchor details')
  } finally {
    loading.value = false
  }
}

// ---- Organizations tab actions ----
const orgDialog = ref(false)
const orgSaving = ref(false)
const orgForm = ref({
  organisationCode: '', name: '', authorisedName: '', authorisedFirstName: '', authorisedSurname: '', authorisedEmail: '', authorisedContact: '', address: '',
  country: '', capitalCity: '', verificationMethod: 'BIOMETRIC', modules: [] as string[],
})

watch(() => orgForm.value.country, (country, previous) => {
  if (country && country !== previous) orgForm.value.capitalCity = capitalFor(country) || orgForm.value.capitalCity
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

function openCreateOrganization() {
  router.push({ name: 'organization-create', query: { anchorId: String(anchorId.value) } })
}

async function openEditOrganization(org: Organization) {
  orgForm.value = {
    organisationCode: org.organisationCode, name: org.name,
    authorisedName: org.authorisedName ?? '', authorisedFirstName: '', authorisedSurname: '',
    authorisedEmail: org.authorisedEmail ?? '',
    authorisedContact: org.authorisedContact ?? '', address: org.address ?? '',
    country: org.country ?? '', capitalCity: org.capitalCity ?? '', verificationMethod: org.verificationMethod ?? 'BIOMETRIC',
    modules: [],
  }
  orgDialog.value = true
  try {
    const res = await dispatch<{ results: string[] }>('GET_ORGANIZATION_MODULES', {
      organisationCode: org.organisationCode,
      targetAnchorId: anchorId.value,
    })
    orgForm.value.modules = res.results
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load modules')
  }
}

async function saveOrganization() {
  if (!orgForm.value.name.trim() || !orgForm.value.country || !orgForm.value.verificationMethod) {
    toast.error('Complete the organization name, country and verification method')
    return
  }
  if (!/.+@.+\..+/.test(orgForm.value.authorisedEmail)) {
    toast.error('Enter a valid authorized contact email')
    return
  }
  if (!orgForm.value.modules.length) {
    toast.error('Select at least one module')
    return
  }
  orgSaving.value = true
  try {
    await Promise.all([
      dispatch('UPDATE_ORGANIZATION', { ...orgForm.value, targetAnchorId: anchorId.value }),
      dispatch('UPDATE_ORGANIZATION_MODULES', { organisationCode: orgForm.value.organisationCode, modules: orgForm.value.modules, targetAnchorId: anchorId.value }),
    ])
    toast.success('Organization updated')
    orgDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Save failed')
  } finally {
    orgSaving.value = false
  }
}

async function toggleOrganizationStatus(org: Organization) {
  const deactivating = org.status === 1
  if (!await confirmAction({
    title: `${deactivating ? 'Deactivate' : 'Activate'} organization?`,
    message: deactivating
      ? `${org.name} will no longer be able to operate in BioPay until it is reactivated.`
      : `${org.name} will regain access to its enabled BioPay modules.`,
    confirmLabel: deactivating ? 'Deactivate' : 'Activate',
    color: deactivating ? 'warning' : 'secondary',
  })) return
  try {
    await dispatch('TOGGLE_ORGANIZATION_STATUS', { organisationCode: org.organisationCode, status: deactivating ? 0 : 1, targetAnchorId: anchorId.value })
    toast.success('Status updated')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Update failed')
  }
}

async function deleteOrganization(org: Organization) {
  if (!await confirmAction({
    title: 'Delete organization?',
    message: `${org.name} and its dashboard access will be removed. This action cannot be undone.`,
    confirmLabel: 'Delete organization',
    color: 'error',
  })) return
  try {
    await dispatch('DELETE_ORGANIZATION', { organisationCode: org.organisationCode, targetAnchorId: anchorId.value })
    toast.success('Organization deleted')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Delete failed')
  }
}

// ---- Anchor users tab actions ----
const userDialog = ref(false)
const userSaving = ref(false)
const userForm = ref({ email: '', username: '', firstName: '', surname: '', roleId: null as number | null })
const availableAnchorRoles = computed(() => roles.value.filter((r) => r.scope === 'ANCHOR' && (r.builtIn || r.anchorId === anchorId.value)))

async function loadRoles() {
  try {
    const res = await dispatch<{ results: Role[] }>('GET_ROLES', { targetAnchorId: anchorId.value })
    roles.value = res.results ?? []
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Unable to load roles')
  }
}

async function openCreateUser() {
  userForm.value = { email: '', username: '', firstName: '', surname: '', roleId: null }
  userDialog.value = true
  await loadRoles()
}

async function createUser() {
  if (!userForm.value.firstName.trim() || !/.+@.+\..+/.test(userForm.value.email) || !userForm.value.username.trim() || !userForm.value.roleId) {
    toast.error('Complete the first name, valid email, username and role')
    return
  }
  userSaving.value = true
  try {
    await dispatch('CREATE_USER', { ...userForm.value, userScope: 'ANCHOR', targetAnchorId: anchorId.value })
    toast.success('User created. A temporary password was emailed to them.')
    userDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Create failed')
  } finally {
    userSaving.value = false
  }
}

const userEditDialog = ref(false)
const userEditLoading = ref(false)
const userEditSaving = ref(false)
const userEditForm = ref({ id: 0, email: '', firstName: '', surname: '', roleId: null as number | null })

async function openEditUser(user: UserRow) {
  userEditDialog.value = true
  userEditLoading.value = true
  try {
    const [{ results: fresh }, roleResult] = await Promise.all([
      dispatch<{ results: UserRow }>('GET_USER', { userId: user.id }),
      dispatch<{ results: Role[] }>('GET_ROLES', { targetAnchorId: anchorId.value }),
    ])
    roles.value = roleResult.results ?? []
    userEditForm.value = { id: fresh.id, email: fresh.email, firstName: fresh.firstName ?? '', surname: fresh.surname ?? '', roleId: fresh.roleId ?? null }
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Unable to load user')
    userEditDialog.value = false
  } finally {
    userEditLoading.value = false
  }
}

async function saveUserEdit() {
  userEditSaving.value = true
  try {
    await dispatch('UPDATE_USER', { userId: userEditForm.value.id, firstName: userEditForm.value.firstName, surname: userEditForm.value.surname, roleId: userEditForm.value.roleId })
    toast.success('User updated')
    userEditDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Update failed')
  } finally {
    userEditSaving.value = false
  }
}

async function toggleUserStatus(user: UserRow) {
  const deactivating = user.status === 1
  if (!await confirmAction({
    title: `${deactivating ? 'Deactivate' : 'Activate'} user?`,
    message: deactivating ? `${user.email} will no longer be able to sign in.` : `${user.email} will be able to sign in again.`,
    confirmLabel: deactivating ? 'Deactivate' : 'Activate',
    color: deactivating ? 'warning' : 'secondary',
  })) return
  try {
    await dispatch('TOGGLE_USER_STATUS', { userId: user.id, status: deactivating ? 0 : 1 })
    toast.success(deactivating ? 'User deactivated' : 'User activated')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Status update failed')
  }
}

function openUserHistory(user: UserRow) {
  router.push({ name: 'activity-history', params: { actorKind: 'USER', actorId: user.id }, query: { name: displayName(user) || user.email || 'User' } })
}

onMounted(load)
</script>

<template>
  <div class="detail-page">
    <v-btn :to="{ name: 'anchors' }" variant="text" prepend-icon="mdi-arrow-left" class="back-link">Anchors</v-btn>

    <v-progress-linear v-if="loading" indeterminate color="primary" rounded aria-label="Loading anchor details" />

    <template v-else-if="anchor">
      <header class="detail-header">
        <div>
          <div class="title-line">
            <h1 class="page-title">{{ anchor.name }}</h1>
            <v-chip size="small" variant="tonal" :color="anchor.status === 1 ? 'success' : 'error'">
              {{ anchor.status === 1 ? 'Active' : 'Deleted' }}
            </v-chip>
          </div>
          <p>{{ anchor.anchorCode }} · Anchor account and the organizations it oversees.</p>
        </div>
      </header>

      <v-tabs v-model="activeTab" class="detail-tabs">
        <v-tab value="profile">Profile</v-tab>
        <v-tab value="organizations">Organizations</v-tab>
        <v-tab value="users">Anchor Users</v-tab>
      </v-tabs>

      <v-window v-model="activeTab">
        <v-window-item value="profile">
          <section class="profile-panel" aria-labelledby="anchor-profile-heading">
            <div class="section-heading">
              <div>
                <h2 id="anchor-profile-heading">Anchor profile</h2>
                <p>Primary contact and registered operating address.</p>
              </div>
              <v-icon icon="mdi-bank-outline" color="primary" />
            </div>
            <dl class="detail-grid">
              <div><dt>Administrator</dt><dd>{{ anchor.authorisedName || 'Not recorded' }}</dd></div>
              <div><dt>Sign-in email</dt><dd>{{ anchor.authorisedEmail || 'Not recorded' }}</dd></div>
              <div><dt>Phone</dt><dd>{{ anchor.authorisedContact || 'Not recorded' }}</dd></div>
              <div><dt>Location</dt><dd>{{ [anchor.city, anchor.country].filter(Boolean).join(', ') || 'Not recorded' }}</dd></div>
              <div class="wide"><dt>Address</dt><dd>{{ anchor.address || 'Not recorded' }}</dd></div>
            </dl>
          </section>
        </v-window-item>

        <v-window-item value="organizations">
          <section class="data-section" aria-labelledby="anchor-organizations-heading">
            <div class="section-heading">
              <div>
                <h2 id="anchor-organizations-heading">Organizations</h2>
                <p>{{ organizations.length }} {{ organizations.length === 1 ? 'organization' : 'organizations' }} under this anchor.</p>
              </div>
              <v-btn v-if="auth.can('ACCESS_ORGANISATIONS')" color="secondary" prepend-icon="mdi-domain-plus" @click="openCreateOrganization">Add Organization</v-btn>
            </div>
            <v-data-table :headers="organizationHeaders" :items="organizations" density="comfortable" class="detail-table">
              <template #item.name="{ item }">
                <div class="entity-cell"><strong>{{ item.name }}</strong><span>{{ item.authorisedEmail || 'No contact email' }}</span></div>
              </template>
              <template #item.country="{ item }">{{ item.country || '—' }}</template>
              <template #item.status="{ item }">
                <v-chip size="small" variant="tonal" :color="item.status === 1 ? 'success' : 'error'">{{ item.status === 1 ? 'Active' : 'Inactive' }}</v-chip>
              </template>
              <template #item.actions="{ item }">
                <v-btn :to="{ name: 'organization-detail', params: { organisationCode: item.organisationCode } }" icon="mdi-eye-outline" variant="text" size="small" :aria-label="`View ${item.name}`" />
                <v-btn v-if="auth.can('ACCESS_ORGANISATIONS')" icon="mdi-pencil" variant="text" size="small" :aria-label="`Edit ${item.name}`" @click="openEditOrganization(item)" />
                <v-btn v-if="auth.can('ACCESS_ORGANISATIONS')" :icon="item.status === 1 ? 'mdi-toggle-switch-off-outline' : 'mdi-toggle-switch'" variant="text" size="small" :aria-label="`${item.status === 1 ? 'Deactivate' : 'Activate'} ${item.name}`" @click="toggleOrganizationStatus(item)" />
                <v-btn v-if="auth.can('ACCESS_ORGANISATIONS')" icon="mdi-delete" variant="text" size="small" color="error" :aria-label="`Delete ${item.name}`" @click="deleteOrganization(item)" />
              </template>
              <template #no-data><div class="empty-state">No organizations are registered under this anchor yet.</div></template>
            </v-data-table>
          </section>
        </v-window-item>

        <v-window-item value="users">
          <section class="data-section" aria-labelledby="anchor-users-heading">
            <div class="section-heading">
              <div>
                <h2 id="anchor-users-heading">Anchor users</h2>
                <p>Dashboard accounts that can sign in at anchor scope.</p>
              </div>
              <div class="d-flex align-center ga-3">
                <v-chip size="small" variant="tonal" color="primary">{{ anchorUsers.length }} accounts</v-chip>
                <v-btn v-if="auth.can('ACCESS_USERS')" color="secondary" prepend-icon="mdi-account-plus-outline" @click="openCreateUser">Add User</v-btn>
              </div>
            </div>
            <v-data-table :headers="userHeaders" :items="anchorUsers" density="comfortable" class="detail-table">
              <template #item.email="{ item }">
                <div class="entity-cell"><strong>{{ displayName(item) }}</strong><span>{{ item.email }}</span></div>
              </template>
              <template #item.roleName="{ item }">{{ item.roleName || 'Anchor user' }}</template>
              <template #item.status="{ item }">
                <v-chip size="small" variant="tonal" :color="item.status === 1 ? 'success' : 'error'">{{ item.status === 1 ? 'Can sign in' : 'Inactive' }}</v-chip>
              </template>
              <template #item.actions="{ item }">
                <v-btn v-if="auth.can('ACCESS_USERS')" icon="mdi-history" variant="text" size="small" :aria-label="`View ${item.email} activity history`" @click="openUserHistory(item)" />
                <v-btn v-if="auth.can('ACCESS_USERS')" icon="mdi-pencil-outline" variant="text" size="small" :aria-label="`Edit ${item.email}`" @click="openEditUser(item)" />
                <v-btn v-if="auth.can('ACCESS_USERS')" :icon="item.status === 1 ? 'mdi-account-cancel-outline' : 'mdi-account-check-outline'" variant="text" size="small" :color="item.status === 1 ? 'error' : 'success'" :aria-label="`${item.status === 1 ? 'Deactivate' : 'Activate'} ${item.email}`" @click="toggleUserStatus(item)" />
              </template>
              <template #no-data><div class="empty-state">No anchor-level sign-in accounts were found.</div></template>
            </v-data-table>
          </section>
        </v-window-item>
      </v-window>
    </template>

    <v-alert v-else type="warning" variant="tonal" title="Anchor not found" text="This anchor does not exist or is no longer available." class="not-found" />

    <v-dialog v-model="orgDialog" max-width="880">
      <v-card class="org-editor" variant="flat" border>
        <div class="editor-heading">
          <div>
            <div class="editor-title">Edit Organization</div>
            <p>Update the organization profile and programme access.</p>
          </div>
          <dialog-close-button @close="orgDialog = false" />
        </div>

        <v-form @submit.prevent="saveOrganization">
          <div class="identity-grid">
            <section class="form-group" aria-labelledby="anchor-org-details-heading">
              <div id="anchor-org-details-heading" class="form-group-title"><v-icon icon="mdi-domain" size="19" /> Organization details</div>
              <v-text-field v-model="orgForm.name" label="Organization name" placeholder="e.g. Bright Future Trust" :rules="[required]" density="compact" hide-details="auto" />
              <v-autocomplete v-model="orgForm.country" :items="COUNTRIES" label="Country" :rules="[required]" density="compact" hide-details="auto" />
              <v-text-field v-model="orgForm.capitalCity" label="Capital city" placeholder="e.g. Nairobi" prepend-inner-icon="mdi-city-variant-outline" density="compact" hide-details="auto" />
              <v-text-field v-model="orgForm.address" label="Address" placeholder="e.g. Westlands Road" prepend-inner-icon="mdi-map-marker-outline" density="compact" hide-details="auto" />
            </section>

            <section class="form-group" aria-labelledby="anchor-org-contact-heading">
              <div id="anchor-org-contact-heading" class="form-group-title"><v-icon icon="mdi-account-outline" size="19" /> Authorized contact</div>
              <v-text-field v-model="orgForm.authorisedName" label="Contact name" placeholder="e.g. Amina Yusuf" density="compact" hide-details="auto" />
              <v-text-field
                v-model="orgForm.authorisedEmail" label="Email"
                placeholder="e.g. amina@brightfuture.org" type="email" :rules="[emailRule]" density="compact" hide-details="auto"
              />
              <v-text-field v-model="orgForm.authorisedContact" label="Phone" placeholder="e.g. +254 700 000000" density="compact" hide-details="auto" />
              <v-select
                v-model="orgForm.verificationMethod" :items="VERIFICATION_METHODS" label="Household verification method"
                :prepend-inner-icon="verificationMethodIcon(orgForm.verificationMethod)" :rules="[required]" density="compact"
                hint="How field officers verify a household member's identity during registration and payment." persistent-hint
              />
            </section>
          </div>

          <section class="module-section" aria-labelledby="anchor-org-module-heading">
            <div class="module-heading-row">
              <div>
                <div id="anchor-org-module-heading" class="form-group-title"><v-icon icon="mdi-view-dashboard-outline" size="19" /> Enabled modules</div>
                <p>Teams only see and use the capabilities selected here.</p>
              </div>
              <span>{{ orgForm.modules.length }} selected</span>
            </div>
            <div class="module-grid">
              <label v-for="m in ORG_MODULES" :key="m.code" class="module-option" :class="{ selected: orgForm.modules.includes(m.code) }">
                <v-checkbox v-model="orgForm.modules" :value="m.code" hide-details density="compact" />
                <span>{{ m.label }}</span>
              </label>
            </div>
          </section>

          <div class="editor-actions">
            <v-btn variant="flat" color="error" @click="orgDialog = false">Cancel</v-btn>
            <v-btn color="secondary" type="submit" :loading="orgSaving" prepend-icon="mdi-check">Save changes</v-btn>
          </div>
        </v-form>
      </v-card>
    </v-dialog>

    <v-dialog v-model="userDialog" max-width="660">
      <v-card class="pa-2">
        <dialog-close-button @close="userDialog = false" />
        <v-card-title>Create anchor user</v-card-title>
        <v-card-subtitle>A temporary password is generated automatically and emailed to the user.</v-card-subtitle>
        <v-card-text class="form-grid">
          <v-text-field v-model="userForm.firstName" label="First name" placeholder="e.g. Jane" variant="outlined" required />
          <v-text-field v-model="userForm.surname" label="Surname" placeholder="e.g. Mwangi" variant="outlined" />
          <v-text-field v-model="userForm.email" label="Email" type="email" placeholder="e.g. jane.mwangi@example.org" variant="outlined" required />
          <v-text-field v-model="userForm.username" label="Username" placeholder="e.g. jane.mwangi" variant="outlined" required />
          <v-select v-model="userForm.roleId" :items="availableAnchorRoles" item-title="name" item-value="id" label="Role" variant="outlined" required />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="flat" color="error" @click="userDialog = false">Cancel</v-btn>
          <v-btn variant="flat" color="secondary" :loading="userSaving" @click="createUser">Create user</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="userEditDialog" max-width="660">
      <v-card class="pa-2">
        <dialog-close-button @close="userEditDialog = false" />
        <v-card-title>View / edit user</v-card-title>
        <v-card-text class="form-grid">
          <v-text-field :model-value="userEditForm.email" label="Email" variant="outlined" readonly />
          <v-select v-model="userEditForm.roleId" :items="availableAnchorRoles" item-title="name" item-value="id" label="Role" variant="outlined" :loading="userEditLoading" />
          <v-text-field v-model="userEditForm.firstName" label="First name" variant="outlined" />
          <v-text-field v-model="userEditForm.surname" label="Surname" variant="outlined" />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="flat" color="error" @click="userEditDialog = false">Cancel</v-btn>
          <v-btn v-if="auth.can('ACCESS_USERS')" variant="flat" color="secondary" :loading="userEditSaving" @click="saveUserEdit">Save changes</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.detail-page { width: 100%; display: grid; gap: 18px; }
.back-link { width: fit-content; margin-left: -12px; }
.detail-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.title-line { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.detail-header p, .section-heading p { color: #64748b; font-size: .88rem; margin: 5px 0 0; }
.detail-tabs { background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; padding-inline: 4px; }
.profile-panel, .data-section { background: #fff; border: 1px solid #e2e8f0; border-radius: 14px; overflow: hidden; }
.profile-panel { padding: 20px; }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding: 18px 20px; }
.profile-panel > .section-heading { padding: 0 0 16px; border-bottom: 1px solid #e2e8f0; }
.section-heading h2 { color: #0f172a; font-size: 1rem; font-weight: 750; letter-spacing: -.015em; }
.detail-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 18px 24px; margin: 18px 0 0; }
.detail-grid div { min-width: 0; }
.detail-grid .wide { grid-column: span 2; }
.detail-grid dt { color: #64748b; font-size: .72rem; font-weight: 700; text-transform: uppercase; letter-spacing: .04em; }
.detail-grid dd { color: #0f172a; font-size: .9rem; margin: 5px 0 0; overflow-wrap: anywhere; }
.detail-table { border-top: 1px solid #e2e8f0; }
.entity-cell { display: grid; gap: 2px; padding-block: 8px; }
.entity-cell strong { color: #0f172a; font-size: .88rem; }
.entity-cell span { color: #64748b; font-size: .76rem; }
.empty-state { color: #64748b; padding: 28px 16px; text-align: center; }
.not-found { margin-top: 8px; }
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
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 2px 16px; }
@media (max-width: 900px) { .detail-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 700px) { .form-grid { grid-template-columns: 1fr; } }
@media (max-width: 680px) {
  .identity-grid { grid-template-columns: 1fr; gap: 0; }
  .module-grid { grid-template-columns: 1fr; }
  .editor-actions :deep(.v-btn) { flex: 1; }
}
@media (max-width: 560px) {
  .detail-grid { grid-template-columns: 1fr; }
  .detail-grid .wide { grid-column: auto; }
  .section-heading, .profile-panel { padding: 16px; }
}
</style>
