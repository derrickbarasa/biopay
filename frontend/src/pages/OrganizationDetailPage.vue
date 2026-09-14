<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

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
interface UserRow { id: number; email: string; firstName?: string; surname?: string; partnerCode?: string; userScope: string; roleName?: string; status: number }
interface Officer { id: number; email: string; firstName?: string; lastName?: string; organisationCode: string; active: string; createdAt?: string }

const route = useRoute()
const auth = useAuthStore()
const toast = useToast()
const loading = ref(true)
const organization = ref<Organization | null>(null)
const anchors = ref<Anchor[]>([])
const users = ref<UserRow[]>([])
const officers = ref<Officer[]>([])

const organisationCode = computed(() => String(route.params.organisationCode ?? ''))
const organizationUsers = computed(() => users.value.filter((user) => user.partnerCode === organisationCode.value && user.userScope === 'ORGANISATION'))
const anchor = computed(() => anchors.value.find((item) => item.id === organization.value?.anchorId) ?? null)

const userHeaders = [
  { title: 'User', key: 'email' },
  { title: 'Role', key: 'roleName' },
  { title: 'Status', key: 'status' },
]
const officerHeaders = [
  { title: 'Field officer', key: 'email' },
  { title: 'Status', key: 'active' },
  { title: 'Added', key: 'createdAt' },
]

function personName(firstName?: string, lastName?: string) {
  return [firstName, lastName].filter(Boolean).join(' ') || 'Name not set'
}

function isActive(value: string | number | undefined) {
  return value === 1 || value === '1' || value === 'true'
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

      <section class="data-section" aria-labelledby="organization-users-heading">
        <div class="section-heading">
          <div>
            <h2 id="organization-users-heading">Organization users</h2>
            <p>Dashboard accounts that can sign in for this organization.</p>
          </div>
          <v-chip v-if="auth.can('ACCESS_USERS')" size="small" variant="tonal" color="primary">{{ organizationUsers.length }} accounts</v-chip>
        </div>
        <v-data-table v-if="auth.can('ACCESS_USERS')" :headers="userHeaders" :items="organizationUsers" density="comfortable" class="detail-table">
          <template #item.email="{ item }">
            <div class="entity-cell"><strong>{{ personName(item.firstName, item.surname) }}</strong><span>{{ item.email }}</span></div>
          </template>
          <template #item.roleName="{ item }">{{ item.roleName || 'Organization user' }}</template>
          <template #item.status="{ item }">
            <v-chip size="small" variant="tonal" :color="item.status === 1 ? 'success' : 'error'">{{ item.status === 1 ? 'Can sign in' : 'Inactive' }}</v-chip>
          </template>
          <template #no-data><div class="empty-state">No organization sign-in accounts were found.</div></template>
        </v-data-table>
        <div v-else class="permission-state">You do not have permission to view organization users.</div>
      </section>

      <section class="data-section" aria-labelledby="field-officers-heading">
        <div class="section-heading">
          <div>
            <h2 id="field-officers-heading">Field officers</h2>
            <p>Officers assigned to this organization for field operations.</p>
          </div>
          <v-chip v-if="auth.can('ACCESS_SUPERVISORS')" size="small" variant="tonal" color="primary">{{ officers.length }} officers</v-chip>
        </div>
        <v-data-table v-if="auth.can('ACCESS_SUPERVISORS')" :headers="officerHeaders" :items="officers" density="comfortable" class="detail-table">
          <template #item.email="{ item }">
            <div class="entity-cell"><strong>{{ personName(item.firstName, item.lastName) }}</strong><span>{{ item.email }}</span></div>
          </template>
          <template #item.active="{ item }">
            <v-chip size="small" variant="tonal" :color="isActive(item.active) ? 'success' : 'error'">{{ isActive(item.active) ? 'Active' : 'Inactive' }}</v-chip>
          </template>
          <template #item.createdAt="{ item }">{{ formatDate(item.createdAt) }}</template>
          <template #no-data><div class="empty-state">No field officers are assigned to this organization yet.</div></template>
        </v-data-table>
        <div v-else class="permission-state">You do not have permission to view field officers.</div>
      </section>
    </template>

    <v-alert v-else type="warning" variant="tonal" title="Organization not found" text="This organization does not exist or is outside your permitted scope." class="not-found" />
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
.empty-state, .permission-state { color: #64748b; padding: 28px 16px; text-align: center; border-top: 1px solid #e2e8f0; }
.not-found { margin-top: 8px; }
@media (max-width: 900px) { .detail-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 560px) {
  .anchor-band { grid-template-columns: auto minmax(0, 1fr); }
  .anchor-band :deep(.v-btn) { grid-column: 1 / -1; width: 100%; }
  .detail-grid { grid-template-columns: 1fr; }
  .detail-grid .wide { grid-column: auto; }
  .section-heading, .profile-panel { padding: 16px; }
}
</style>
