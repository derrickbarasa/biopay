<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'

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
  country?: string
  status: number
}

interface UserRow {
  id: number
  email: string
  firstName?: string
  surname?: string
  anchorId?: number
  userScope: string
  roleName?: string
  status: number
}

const route = useRoute()
const toast = useToast()
const loading = ref(true)
const anchor = ref<Anchor | null>(null)
const organizations = ref<Organization[]>([])
const users = ref<UserRow[]>([])

const anchorId = computed(() => Number(route.params.anchorId))
const anchorUsers = computed(() => users.value.filter((user) => user.anchorId === anchorId.value && user.userScope === 'ANCHOR'))

const organizationHeaders = [
  { title: 'Organization', key: 'name' },
  { title: 'Organization Code', key: 'organisationCode', minWidth: 172, nowrap: true },
  { title: 'Authorized contact', key: 'authorisedName' },
  { title: 'Country', key: 'country' },
  { title: 'Status', key: 'status' },
  { title: 'Action', key: 'actions', sortable: false, align: 'start' as const, width: 80, minWidth: 80, fixed: true, nowrap: true },
]

const userHeaders = [
  { title: 'User', key: 'email' },
  { title: 'Role', key: 'roleName' },
  { title: 'Status', key: 'status' },
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

      <section class="data-section" aria-labelledby="anchor-organizations-heading">
        <div class="section-heading">
          <div>
            <h2 id="anchor-organizations-heading">Organizations</h2>
            <p>{{ organizations.length }} {{ organizations.length === 1 ? 'organization' : 'organizations' }} under this anchor.</p>
          </div>
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
          </template>
          <template #no-data><div class="empty-state">No organizations are registered under this anchor yet.</div></template>
        </v-data-table>
      </section>

      <section class="data-section" aria-labelledby="anchor-users-heading">
        <div class="section-heading">
          <div>
            <h2 id="anchor-users-heading">Anchor users</h2>
            <p>Dashboard accounts that can sign in at anchor scope.</p>
          </div>
          <v-chip size="small" variant="tonal" color="primary">{{ anchorUsers.length }} accounts</v-chip>
        </div>
        <v-data-table :headers="userHeaders" :items="anchorUsers" density="comfortable" class="detail-table">
          <template #item.email="{ item }">
            <div class="entity-cell"><strong>{{ displayName(item) }}</strong><span>{{ item.email }}</span></div>
          </template>
          <template #item.roleName="{ item }">{{ item.roleName || 'Anchor user' }}</template>
          <template #item.status="{ item }">
            <v-chip size="small" variant="tonal" :color="item.status === 1 ? 'success' : 'error'">{{ item.status === 1 ? 'Can sign in' : 'Inactive' }}</v-chip>
          </template>
          <template #no-data><div class="empty-state">No anchor-level sign-in accounts were found.</div></template>
        </v-data-table>
      </section>
    </template>

    <v-alert v-else type="warning" variant="tonal" title="Anchor not found" text="This anchor does not exist or is no longer available." class="not-found" />
  </div>
</template>

<style scoped>
.detail-page { width: 100%; display: grid; gap: 18px; }
.back-link { width: fit-content; margin-left: -12px; }
.detail-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.title-line { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.detail-header p, .section-heading p { color: #64748b; font-size: .88rem; margin: 5px 0 0; }
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
@media (max-width: 900px) { .detail-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 560px) {
  .detail-grid { grid-template-columns: 1fr; }
  .detail-grid .wide { grid-column: auto; }
  .section-heading, .profile-panel { padding: 16px; }
}
</style>
