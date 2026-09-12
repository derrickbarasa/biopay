<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'

interface ApiClientRow { id: number; name: string; keyId: string; partnerCode?: string; anchorId?: number; userScope: string; roleId?: number; roleName?: string; status: number; lastLoginAt?: string; createdAt: string }
interface Role { id: number; name: string; scope: string; anchorId?: number | null; builtIn?: boolean; systemRole?: boolean }
interface Org { organisationCode: string; name: string; anchorId?: number }
interface Anchor { id: number; name: string }

const auth = useAuthStore(), toast = useToast()
const { confirmAction } = useConfirm()
const loading = ref(false), saving = ref(false), dialog = ref(false)
const clients = ref<ApiClientRow[]>([]), roles = ref<Role[]>([]), orgs = ref<Org[]>([]), anchors = ref<Anchor[]>([])
const createdCredential = ref<{ keyId: string; secret: string } | null>(null)

const form = reactive({ name: '', userScope: 'ORGANISATION', organisationCode: '', roleId: null as number | null, targetAnchorId: null as number | null })

const headers = [
  { title: 'Name', key: 'name' },
  { title: 'Key ID', key: 'keyId' },
  { title: 'Scope', key: 'userScope' },
  { title: 'Role', key: 'roleName' },
  { title: 'Status', key: 'status' },
  { title: 'Last used', key: 'lastLoginAt' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'start' as const },
]

const availableRoles = computed(() => roles.value.filter(r => r.scope === form.userScope && (r.builtIn || !auth.isSystemAdmin || r.anchorId === form.targetAnchorId)))
const scopeOptions = computed(() => auth.isSystemAdmin || auth.isAnchor ? ['ANCHOR', 'ORGANISATION'] : ['ORGANISATION'])
const availableOrganisations = computed(() => auth.isSystemAdmin ? orgs.value.filter(o => o.anchorId === form.targetAnchorId) : orgs.value)
const orgNameByCode = computed(() => new Map(orgs.value.map(o => [o.organisationCode, o.name])))
function orgName(code?: string) { return (code && orgNameByCode.value.get(code)) || code || '—' }

async function load() {
  loading.value = true
  try {
    const [c, r, o] = await Promise.all([
      dispatch<{ results: ApiClientRow[] }>('GET_API_CLIENTS'),
      dispatch<{ results: Role[] }>('GET_ROLES'),
      dispatch<{ results: Org[] }>('GET_ORGANIZATIONS'),
    ])
    clients.value = c.results ?? []
    roles.value = r.results ?? []
    orgs.value = o.results ?? []
    if (auth.isSystemAdmin) {
      const a = await dispatch<{ results: Anchor[] }>('GET_ANCHORS')
      anchors.value = a.results ?? []
    }
  } catch (e) {
    toast.error(e instanceof Error ? e.message : 'Unable to load API clients')
  } finally {
    loading.value = false
  }
}

async function selectTargetAnchor() {
  form.organisationCode = ''
  form.roleId = null
  if (!form.targetAnchorId) { roles.value = []; return }
  try {
    const r = await dispatch<{ results: Role[] }>('GET_ROLES', { targetAnchorId: form.targetAnchorId })
    roles.value = r.results ?? []
  } catch (e) {
    toast.error(e instanceof Error ? e.message : 'Unable to load roles for this anchor')
  }
}

function openCreate() {
  createdCredential.value = null
  Object.assign(form, { name: '', userScope: 'ORGANISATION', organisationCode: auth.user?.partnerCode ?? '', roleId: null, targetAnchorId: auth.isSystemAdmin ? null : auth.user?.anchorId ?? null })
  dialog.value = true
}

async function create() {
  if (!form.name.trim() || !form.roleId || (form.userScope === 'ORGANISATION' && !form.organisationCode) || (auth.isSystemAdmin && !form.targetAnchorId)) {
    toast.error('Complete the anchor, name, access scope, organisation and role')
    return
  }
  saving.value = true
  try {
    const res = await dispatch<{ keyId: string; secret: string }>('CREATE_API_CLIENT', { ...form })
    createdCredential.value = { keyId: res.keyId, secret: res.secret }
    toast.success('API client created')
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : 'Create failed')
  } finally {
    saving.value = false
  }
}

async function toggle(client: ApiClientRow) {
  const revoking = client.status === 1
  if (!await confirmAction({
    title: `${revoking ? 'Revoke' : 'Restore'} API client?`,
    message: revoking
      ? `${client.name} will no longer be able to obtain new access tokens. Any tokens it already holds keep working until they expire.`
      : `${client.name} will be able to obtain access tokens again.`,
    confirmLabel: revoking ? 'Revoke' : 'Restore',
    color: revoking ? 'warning' : 'secondary',
  })) return
  try {
    await dispatch('TOGGLE_API_CLIENT_STATUS', { userId: client.id, status: revoking ? 0 : 1 })
    toast.success(revoking ? 'API client revoked' : 'API client restored')
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : 'Status update failed')
  }
}

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    toast.success('Copied')
  } catch {
    toast.error('Unable to copy — select and copy manually')
  }
}

onMounted(load)
</script>
<template>
  <div class="admin-page">
    <header class="admin-head">
      <div>
        <h1 class="page-title">API access</h1>
        <p>Machine credentials for programmatic access — separate from any person's dashboard login, and revocable on their own.</p>
      </div>
      <v-btn v-if="auth.can('ACCESS_USERS')" color="secondary" prepend-icon="mdi-key-plus" @click="openCreate">New API client</v-btn>
    </header>

    <v-card border flat class="admin-card">
      <v-data-table :headers="headers" :items="clients" :loading="loading">
        <template #item.name="{ item }"><strong>{{ item.name }}</strong></template>
        <template #item.keyId="{ item }"><code class="key-id">{{ item.keyId }}</code></template>
        <template #item.userScope="{ item }"><v-chip size="small" variant="tonal">{{ item.userScope === 'ANCHOR' ? 'Anchor-wide' : orgName(item.partnerCode) }}</v-chip></template>
        <template #item.status="{ item }"><v-chip size="small" :color="item.status === 1 ? 'success' : 'error'" variant="tonal">{{ item.status === 1 ? 'Active' : 'Revoked' }}</v-chip></template>
        <template #item.lastLoginAt="{ item }">{{ item.lastLoginAt ?? 'Never used' }}</template>
        <template #item.actions="{ item }">
          <v-btn size="small" variant="text" :color="item.status === 1 ? 'error' : 'success'" @click="toggle(item)">{{ item.status === 1 ? 'Revoke' : 'Restore' }}</v-btn>
        </template>
        <template #no-data>
          <div class="empty-state">
            <v-icon icon="mdi-key-outline" size="28" />
            <p>No API clients yet. Create one to let an integration call the BioPay API without using a person's login.</p>
          </div>
        </template>
      </v-data-table>
    </v-card>

    <v-dialog v-model="dialog" max-width="560" persistent>
      <v-card class="pa-2">
        <dialog-close-button @close="dialog = false" />
        <template v-if="createdCredential">
          <v-card-title>API client created</v-card-title>
          <v-card-text>
            <v-alert type="warning" variant="tonal" density="compact" class="mb-4">
              This secret is shown once and cannot be retrieved again. Store it now — if it's lost, revoke this client and create a new one.
            </v-alert>
            <p class="credential-label">Key ID</p>
            <div class="credential-row"><code>{{ createdCredential.keyId }}</code><v-btn icon="mdi-content-copy" size="small" variant="text" aria-label="Copy key ID" @click="copy(createdCredential.keyId)" /></div>
            <p class="credential-label mt-3">Secret</p>
            <div class="credential-row"><code>{{ createdCredential.secret }}</code><v-btn icon="mdi-content-copy" size="small" variant="text" aria-label="Copy secret" @click="copy(createdCredential.secret)" /></div>
          </v-card-text>
          <v-card-actions><v-spacer /><v-btn variant="flat" color="secondary" @click="dialog = false">Done</v-btn></v-card-actions>
        </template>
        <template v-else>
          <v-card-title>New API client</v-card-title>
          <v-card-subtitle>Generates a key ID and secret; no password or OTP is required to use it.</v-card-subtitle>
          <v-card-text class="form-grid">
            <v-select v-if="auth.isSystemAdmin" v-model="form.userScope" :items="scopeOptions" label="Access scope" variant="outlined" />
            <v-select v-else-if="auth.isAnchor" v-model="form.userScope" :items="['ANCHOR', 'ORGANISATION']" label="Access scope" variant="outlined" />
            <v-select v-if="auth.isSystemAdmin" v-model="form.targetAnchorId" :items="anchors" item-title="name" item-value="id" label="Anchor" variant="outlined" placeholder="Choose an anchor" @update:model-value="selectTargetAnchor" />
            <v-select v-if="form.userScope === 'ORGANISATION' && auth.isAnchor" v-model="form.organisationCode" :items="availableOrganisations" item-title="name" item-value="organisationCode" label="Organisation" variant="outlined" placeholder="Choose an organisation" :disabled="auth.isSystemAdmin && !form.targetAnchorId" />
            <v-text-field v-model="form.name" label="Name" placeholder="e.g. Reporting integration" variant="outlined" required class="wide" />
            <v-select v-model="form.roleId" :items="availableRoles" item-title="name" item-value="id" label="Role" variant="outlined" required class="wide" />
          </v-card-text>
          <v-card-actions><v-spacer /><v-btn variant="flat" color="error" @click="dialog = false">Cancel</v-btn><v-btn variant="flat" color="secondary" :loading="saving" @click="create">Create</v-btn></v-card-actions>
        </template>
      </v-card>
    </v-dialog>
  </div>
</template>
<style scoped>
.admin-page{width:100%}.admin-head{display:flex;justify-content:space-between;gap:20px;align-items:flex-start;margin-bottom:24px}.admin-head h1{font-size:2rem;letter-spacing:-.04em}.admin-head p{color:#64748b;max-width:52ch}.admin-card{border-radius:18px!important;overflow:hidden}
.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:4px 16px;padding:8px 4px}.form-grid .wide{grid-column:1/-1}
.key-id{font-size:.8rem}
.credential-label{margin:0 0 4px;font-size:.72rem;font-weight:700;text-transform:uppercase;letter-spacing:.04em;color:#64748b}
.credential-row{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:8px 12px;border:1px solid #e2e8f0;border-radius:8px;background:#f8fafc}
.credential-row code{overflow-wrap:anywhere;font-size:.82rem}
.empty-state{display:flex;flex-direction:column;align-items:center;gap:8px;padding:40px 20px;color:#64748b;text-align:center}
@media(max-width:700px){.form-grid{grid-template-columns:1fr}.form-grid .wide{grid-column:auto}}
</style>
