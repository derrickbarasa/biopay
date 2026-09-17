<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import OfficerDataTable from '@/components/OfficerDataTable.vue'
import OfficerLocationDialog from '@/components/OfficerLocationDialog.vue'
import { scopedUserHeaders } from '@/constants/tableHeaders'

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
  anchorId?: number
  anchorName?: string
  status: number
  createdAt?: string
}

interface Anchor { id: number; name: string; anchorCode: string }
interface UserRow { id: number; email: string; firstName?: string; surname?: string; partnerCode?: string; userScope: string; roleId?: number; roleName?: string; status: number }
interface Officer { id: number; email: string; firstName: string; lastName: string; organisationCode: string; active: string; createdAt?: string }
interface Role { id: number; name: string; scope: string; anchorId?: number | null; builtIn?: boolean }

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToast()
const { confirmAction } = useConfirm()
const loading = ref(true)
const organization = ref<Organization | null>(null)
const anchors = ref<Anchor[]>([])
const users = ref<UserRow[]>([])
const officers = ref<Officer[]>([])
const roles = ref<Role[]>([])

const organisationCode = computed(() => String(route.params.organisationCode ?? ''))
const organizationUsers = computed(() => users.value.filter((user) => user.partnerCode === organisationCode.value && user.userScope === 'ORGANISATION'))
const organizationOptions = computed(() => organization.value ? [{ organisationCode: organization.value.organisationCode, name: organization.value.name }] : [])
const anchor = computed(() => anchors.value.find((item) => item.id === organization.value?.anchorId) ?? null)
// System admins scope roles/users/officers by the organization's parent anchor; anchor and
// organisation viewers already only ever see their own tenant, so no anchor id is needed.
const targetAnchorId = computed(() => (auth.isSystemAdmin ? organization.value?.anchorId : undefined))

const activeTab = ref('profile')

function personName(firstName?: string, lastName?: string) {
  return [firstName, lastName].filter(Boolean).join(' ') || 'Name not set'
}

function verificationLabel(method?: string) {
  if (method === 'FACIAL') return 'Facial recognition'
  if (method === 'BOTH') return 'Fingerprint and facial recognition'
  return method === 'BIOMETRIC' ? 'Fingerprint' : 'Not recorded'
}

function formatDate(value?: string) {
  if (!value) return '—'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(parsed)
}

async function load() {
  if (!organisationCode.value) {
    loading.value = false
    return
  }

  loading.value = true
  try {
    const organizationResponse = await dispatch<{ results: Organization[] }>('GET_ORGANIZATION', { organisationCode: organisationCode.value })
    organization.value = organizationResponse.results?.[0] ?? null

    if (!organization.value) return

    // Only roles above the organization in the hierarchy may resolve its parent anchor.
    if (auth.isAnchor) {
      const anchorResponse = await dispatch<{ results: Anchor[] }>('GET_ANCHORS')
      anchors.value = anchorResponse.results ?? []
    }

    const relatedRequests: Promise<void>[] = []
    if (auth.can('ACCESS_USERS')) {
      relatedRequests.push(dispatch<{ results: UserRow[] }>('GET_USERS').then((response) => { users.value = response.results ?? [] }))
    }
    if (auth.can('ACCESS_SUPERVISORS')) {
      relatedRequests.push(dispatch<{ results: Officer[] }>('GET_OFFICERS', {
        organisationCode: organisationCode.value,
        targetAnchorId: auth.isSystemAdmin ? organization.value.anchorId : undefined,
      }).then((response) => { officers.value = response.results ?? [] }))
    }
    await Promise.all(relatedRequests)
  } catch (error) {
    toast.error(error instanceof Error ? error.message : 'Unable to load organization details')
  } finally {
    loading.value = false
  }
}

// ---- Organization users tab actions ----
const userDialog = ref(false)
const userSaving = ref(false)
const userForm = ref({ email: '', username: '', firstName: '', surname: '', roleId: null as number | null })
const availableOrgRoles = computed(() => roles.value.filter((r) => r.scope === 'ORGANISATION'))

async function loadRoles() {
  try {
    const res = await dispatch<{ results: Role[] }>('GET_ROLES', { targetAnchorId: targetAnchorId.value })
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
    await dispatch('CREATE_USER', { ...userForm.value, userScope: 'ORGANISATION', organisationCode: organisationCode.value, targetAnchorId: targetAnchorId.value })
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
      dispatch<{ results: Role[] }>('GET_ROLES', { targetAnchorId: targetAnchorId.value }),
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
  router.push({ name: 'activity-history', params: { actorKind: 'USER', actorId: user.id }, query: { name: personName(user.firstName, user.surname) || user.email || 'User' } })
}

// ---- Field officers tab actions ----
const officerDialog = ref(false)
const officerEditing = ref(false)
const officerSaving = ref(false)
const officerForm = ref({ firstName: '', lastName: '', email: '' })

function openCreateOfficer() {
  officerEditing.value = false
  officerForm.value = { firstName: '', lastName: '', email: '' }
  officerDialog.value = true
}

function openEditOfficer(officer: Officer) {
  officerEditing.value = true
  officerForm.value = { firstName: officer.firstName ?? '', lastName: officer.lastName ?? '', email: officer.email }
  officerDialog.value = true
}

async function saveOfficer() {
  if (!officerForm.value.firstName.trim() || !officerForm.value.lastName.trim() || !/.+@.+\..+/.test(officerForm.value.email)) {
    toast.error('Enter the officer\'s first name, last name and a valid email address')
    return
  }
  officerSaving.value = true
  try {
    if (officerEditing.value) {
      await dispatch('UPDATE_OFFICER', { ...officerForm.value, organisationCode: organisationCode.value })
      toast.success('Officer updated')
    } else {
      await dispatch('CREATE_OFFICER', { ...officerForm.value, organisationCode: organisationCode.value })
      toast.success('Officer registered. Temporary password sent by email')
    }
    officerDialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Save failed')
  } finally {
    officerSaving.value = false
  }
}

async function toggleOfficerStatus(officer: Officer, active: boolean) {
  if (!await confirmAction({
    title: `${active ? 'Activate' : 'Deactivate'} officer?`,
    message: active
      ? `${officer.firstName} ${officer.lastName} will regain access to the field app.`
      : `${officer.firstName} ${officer.lastName} will no longer be able to sign in to the field app.`,
    confirmLabel: active ? 'Activate' : 'Deactivate',
    color: active ? 'secondary' : 'warning',
  })) return
  try {
    await dispatch('TOGGLE_OFFICER_STATUS', { email: officer.email, active: active ? 1 : 0 })
    toast.success(active ? 'Officer activated' : 'Officer deactivated')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : `Failed to ${active ? 'activate' : 'deactivate'} officer`)
  }
}

function openOfficerHistory(officer: Officer) {
  router.push({
    name: 'activity-history',
    params: { actorKind: 'OFFICER', actorId: officer.id },
    query: { name: personName(officer.firstName, officer.lastName) || 'Field officer' },
  })
}

const officerLocationDialog = ref(false)
const officerLocationTarget = ref<Officer | null>(null)

function openOfficerLocations(officer: Officer) {
  officerLocationTarget.value = officer
  officerLocationDialog.value = true
}

onMounted(load)
</script>

<template>
  <div class="detail-page">
    <v-btn :to="auth.isOrganisation ? { name: 'dashboard' } : { name: 'organizations' }" variant="text" prepend-icon="mdi-arrow-left" class="back-link">
      {{ auth.isOrganisation ? 'Dashboard' : 'Organizations' }}
    </v-btn>

    <v-progress-linear v-if="loading" indeterminate color="primary" rounded aria-label="Loading organization details" />

    <template v-else-if="organization">
      <header class="detail-header">
        <div>
          <div class="title-line">
            <h1 class="page-title">{{ organization.name }}</h1>
            <v-chip size="small" variant="tonal" :color="organization.status === 1 ? 'success' : 'error'">
              {{ organization.status === 1 ? 'Active' : 'Inactive' }}
            </v-chip>
          </div>
          <p>{{ organization.organisationCode }} · Organization access and field team.</p>
        </div>
      </header>

      <section v-if="auth.isAnchor" class="anchor-band" aria-labelledby="parent-anchor-heading">
        <div class="anchor-icon"><v-icon icon="mdi-bank-outline" size="22" /></div>
        <div>
          <span id="parent-anchor-heading">Belongs to anchor</span>
          <strong>{{ anchor?.name || organization.anchorName || 'Anchor not available' }}</strong>
          <small v-if="anchor?.anchorCode">{{ anchor.anchorCode }}</small>
        </div>
        <v-btn v-if="auth.isSystemAdmin && organization.anchorId" :to="{ name: 'anchor-detail', params: { anchorId: organization.anchorId } }" variant="text" append-icon="mdi-arrow-right">View anchor</v-btn>
      </section>

      <v-tabs v-model="activeTab" class="detail-tabs">
        <v-tab value="profile">Profile</v-tab>
        <v-tab value="users">Org Users</v-tab>
        <v-tab value="officers">Field Officers</v-tab>
      </v-tabs>

      <v-window v-model="activeTab">
        <v-window-item value="profile">
          <section class="profile-panel" aria-labelledby="organization-profile-heading">
            <div class="section-heading">
              <div>
                <h2 id="organization-profile-heading">Organization profile</h2>
                <p>Registered contact, location, and verification setup.</p>
              </div>
              <v-icon icon="mdi-domain" color="primary" />
            </div>
            <dl class="detail-grid">
              <div><dt>Authorized contact</dt><dd>{{ organization.authorisedName || 'Not recorded' }}</dd></div>
              <div><dt>Contact email</dt><dd>{{ organization.authorisedEmail || 'Not recorded' }}</dd></div>
              <div><dt>Phone</dt><dd>{{ organization.authorisedContact || 'Not recorded' }}</dd></div>
              <div><dt>Verification</dt><dd>{{ verificationLabel(organization.verificationMethod) }}</dd></div>
              <div><dt>Location</dt><dd>{{ [organization.capitalCity, organization.country].filter(Boolean).join(', ') || 'Not recorded' }}</dd></div>
              <div><dt>Created</dt><dd>{{ formatDate(organization.createdAt) }}</dd></div>
              <div class="wide"><dt>Address</dt><dd>{{ organization.address || 'Not recorded' }}</dd></div>
            </dl>
          </section>
        </v-window-item>

        <v-window-item value="users">
          <section class="data-section" aria-labelledby="organization-users-heading">
            <div class="section-heading">
              <div>
                <h2 id="organization-users-heading">Organization users</h2>
                <p>Dashboard accounts that can sign in for this organization.</p>
              </div>
              <div class="d-flex align-center ga-3">
                <v-chip v-if="auth.can('ACCESS_USERS')" size="small" variant="tonal" color="primary">{{ organizationUsers.length }} accounts</v-chip>
                <v-btn v-if="auth.can('ACCESS_USERS')" color="secondary" prepend-icon="mdi-account-plus-outline" @click="openCreateUser">Add User</v-btn>
              </div>
            </div>
            <v-data-table v-if="auth.can('ACCESS_USERS')" :headers="scopedUserHeaders" :items="organizationUsers" density="comfortable" class="detail-table">
              <template #item.name="{ item }">{{ personName(item.firstName, item.surname) }}</template>
              <template #item.roleName="{ item }">{{ item.roleName || 'Organization user' }}</template>
              <template #item.status="{ item }">
                <v-chip size="small" variant="tonal" :color="item.status === 1 ? 'success' : 'error'">{{ item.status === 1 ? 'Active' : 'Inactive' }}</v-chip>
              </template>
              <template #item.actions="{ item }">
                <v-btn icon="mdi-history" variant="text" size="small" :aria-label="`View ${item.email} activity history`" @click="openUserHistory(item)" />
                <v-btn icon="mdi-pencil-outline" variant="text" size="small" :aria-label="`Edit ${item.email}`" @click="openEditUser(item)" />
                <v-btn :icon="item.status === 1 ? 'mdi-account-cancel-outline' : 'mdi-account-check-outline'" variant="text" size="small" :color="item.status === 1 ? 'error' : 'success'" :aria-label="`${item.status === 1 ? 'Deactivate' : 'Activate'} ${item.email}`" @click="toggleUserStatus(item)" />
              </template>
              <template #no-data><div class="empty-state">No organization sign-in accounts were found.</div></template>
            </v-data-table>
            <div v-else class="permission-state">You do not have permission to view organization users.</div>
          </section>
        </v-window-item>

        <v-window-item value="officers">
          <section class="data-section" aria-labelledby="field-officers-heading">
            <div class="section-heading">
              <div>
                <h2 id="field-officers-heading">Field officers</h2>
                <p>Officers assigned to this organization for field operations.</p>
              </div>
              <div class="d-flex align-center ga-3">
                <v-chip v-if="auth.can('ACCESS_SUPERVISORS')" size="small" variant="tonal" color="primary">{{ officers.length }} officers</v-chip>
                <v-btn v-if="auth.can('ACCESS_SUPERVISORS')" color="secondary" prepend-icon="mdi-account-plus" @click="openCreateOfficer">Add Field Officer</v-btn>
              </div>
            </div>
            <OfficerDataTable
              v-if="auth.can('ACCESS_SUPERVISORS')"
              :items="officers"
              :organizations="organizationOptions"
              can-manage
              can-view-history
              can-assign-locations
              no-data-text="No field officers are assigned to this organization yet."
              @history="openOfficerHistory"
              @edit="openEditOfficer"
              @toggle-status="toggleOfficerStatus"
              @assign-location="openOfficerLocations"
            />
            <div v-else class="permission-state">You do not have permission to view field officers.</div>
          </section>
        </v-window-item>
      </v-window>
    </template>

    <v-alert v-else type="warning" variant="tonal" title="Organization not found" text="This organization does not exist or is outside your permitted scope." class="not-found" />

    <OfficerLocationDialog v-model="officerLocationDialog" :officer="officerLocationTarget" />

    <v-dialog v-model="userDialog" max-width="660">
      <v-card class="pa-2">
        <dialog-close-button @close="userDialog = false" />
        <v-card-title>Create organization user</v-card-title>
        <v-card-subtitle>A temporary password is generated automatically and emailed to the user.</v-card-subtitle>
        <v-card-text class="form-grid">
          <v-text-field v-model="userForm.firstName" label="First name" placeholder="e.g. Jane" variant="outlined" required />
          <v-text-field v-model="userForm.surname" label="Surname" placeholder="e.g. Mwangi" variant="outlined" />
          <v-text-field v-model="userForm.email" label="Email" type="email" placeholder="e.g. jane.mwangi@example.org" variant="outlined" required />
          <v-text-field v-model="userForm.username" label="Username" placeholder="e.g. jane.mwangi" variant="outlined" required />
          <v-select v-model="userForm.roleId" :items="availableOrgRoles" item-title="name" item-value="id" label="Role" variant="outlined" required />
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
          <v-select v-model="userEditForm.roleId" :items="availableOrgRoles" item-title="name" item-value="id" label="Role" variant="outlined" :loading="userEditLoading" />
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

    <v-dialog v-model="officerDialog" max-width="560">
      <v-card class="officer-editor">
        <div class="editor-heading">
          <div>
            <div class="editor-title"><v-icon icon="mdi-account-tie" size="20" /> {{ officerEditing ? 'Edit Officer' : 'Register Field Officer' }}</div>
            <p>{{ officerEditing ? 'Update this officer\'s profile.' : 'Create a field officer account for this organization.' }}</p>
          </div>
          <dialog-close-button @close="officerDialog = false" />
        </div>
        <v-form @submit.prevent="saveOfficer">
          <div class="field-grid">
            <v-text-field v-model="officerForm.firstName" label="First name" placeholder="e.g. Jane" density="compact" required />
            <v-text-field v-model="officerForm.lastName" label="Last name" placeholder="e.g. Mwangi" density="compact" required />
            <v-text-field v-model="officerForm.email" label="Email" type="email" placeholder="e.g. jane.mwangi@example.org" :disabled="officerEditing" density="compact" required />
          </div>
          <v-alert v-if="!officerEditing" type="info" variant="tonal" density="compact" class="mt-1">
            A temporary password will be generated and emailed to this officer.
          </v-alert>
          <div class="editor-actions">
            <v-btn variant="flat" color="error" @click="officerDialog = false">Cancel</v-btn>
            <v-btn color="secondary" type="submit" :loading="officerSaving" prepend-icon="mdi-check">Save</v-btn>
          </div>
        </v-form>
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
.anchor-band { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 14px; padding: 14px 16px; color: #0f172a; background: #f0fdfa; border: 1px solid #99f6e4; border-radius: 12px; }
.anchor-icon { display: grid; place-items: center; width: 38px; height: 38px; color: #0f766e; background: #ccfbf1; border-radius: 50%; }
.anchor-band div:nth-child(2) { display: grid; gap: 1px; }
.anchor-band span, .anchor-band small { color: #64748b; font-size: .72rem; }
.anchor-band strong { font-size: .92rem; }
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
.empty-state, .permission-state { color: #64748b; padding: 28px 16px; text-align: center; border-top: 1px solid #e2e8f0; }
.not-found { margin-top: 8px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 2px 16px; }
.officer-editor { padding: 22px 24px; }
.editor-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.editor-title { display: flex; align-items: center; gap: 8px; color: #0f172a; font-size: 1.05rem; font-weight: 750; letter-spacing: -.02em; }
.editor-heading p { color: #64748b; font-size: .8rem; margin: 4px 0 0; }
.field-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 16px; }
.editor-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
@media (max-width: 900px) { .detail-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 700px) { .form-grid, .field-grid { grid-template-columns: 1fr; } }
@media (max-width: 560px) {
  .anchor-band { grid-template-columns: auto minmax(0, 1fr); }
  .anchor-band :deep(.v-btn) { grid-column: 1 / -1; width: 100%; }
  .detail-grid { grid-template-columns: 1fr; }
  .detail-grid .wide { grid-column: auto; }
  .section-heading, .profile-panel { padding: 16px; }
  .editor-actions :deep(.v-btn) { flex: 1; }
}
</style>
