<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'

interface Permission { id: number; name: string; description: string }
interface Role { id: number; name: string; description: string; scope: string; permissions: string[]; status: number }

const toast = useToast()
const loading = ref(false)
const saving = ref(false)
const creatingPermission = ref(false)
const roles = ref<Role[]>([])
const permissions = ref<Permission[]>([])
const selected = ref<number | null>(null)

const form = reactive({ roleId: null as number | null, name: '', description: '', scope: 'ORGANISATION', permissionIds: [] as number[] })
const newPermission = reactive({ name: '', description: '' })

const GROUP_LABELS: Record<string, string> = {
  ORGANISATIONS: 'Organisations',
  USERS: 'User Management',
  ROLES: 'Roles & Permissions',
  OFFICERS: 'Officers',
  HOUSEHOLDS: 'Households',
  PAYMENTS: 'Payments',
  VOUCHERS: 'Vouchers',
  REPORTS: 'Reports',
  LOCATIONS: 'Locations',
  ATTENDANCE: 'Attendance',
  ANCHORS: 'Anchors',
}

function toTitle(s: string) {
  return s.toLowerCase().split('_').map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')
}

interface PermissionGroup { key: string; label: string; items: { permission: Permission; actionLabel: string }[] }

const permissionGroups = computed<PermissionGroup[]>(() => {
  const map = new Map<string, PermissionGroup>()
  for (const p of permissions.value) {
    const parts = p.name.split('_')
    const verb = parts[0]
    const rest = parts.slice(1).join('_') || verb
    if (!map.has(rest)) map.set(rest, { key: rest, label: GROUP_LABELS[rest] ?? toTitle(rest), items: [] })
    map.get(rest)!.items.push({ permission: p, actionLabel: toTitle(verb) })
  }
  return [...map.values()].sort((a, b) => a.label.localeCompare(b.label))
})

const allPermissionsSelected = computed(() => permissions.value.length > 0 && form.permissionIds.length === permissions.value.length)

function groupSelectedCount(group: PermissionGroup) {
  return group.items.filter((i) => form.permissionIds.includes(i.permission.id)).length
}
function isGroupFullySelected(group: PermissionGroup) {
  return group.items.length > 0 && groupSelectedCount(group) === group.items.length
}
function isGroupPartiallySelected(group: PermissionGroup) {
  const count = groupSelectedCount(group)
  return count > 0 && count < group.items.length
}
function toggleGroup(group: PermissionGroup) {
  const groupIds = new Set(group.items.map((i) => i.permission.id))
  if (isGroupFullySelected(group)) {
    form.permissionIds = form.permissionIds.filter((id) => !groupIds.has(id))
  } else {
    form.permissionIds = [...new Set([...form.permissionIds, ...groupIds])]
  }
}

async function load() {
  loading.value = true
  try {
    const [r, p] = await Promise.all([
      dispatch<{ results: Role[] }>('GET_ROLES'),
      dispatch<{ results: Permission[] }>('GET_PERMISSIONS'),
    ])
    roles.value = r.results ?? []
    permissions.value = p.results ?? []
    if (selected.value === null && roles.value.length) edit(roles.value[0])
  } catch (e) {
    toast.error(e instanceof Error ? e.message : 'Unable to load roles')
  } finally {
    loading.value = false
  }
}

function edit(role: Role) {
  selected.value = role.id
  Object.assign(form, {
    roleId: role.id, name: role.name, description: role.description ?? '', scope: role.scope,
    permissionIds: permissions.value.filter((p) => role.permissions.includes(p.name)).map((p) => p.id),
  })
}

function createRole() {
  selected.value = null
  Object.assign(form, { roleId: null, name: '', description: '', scope: 'ORGANISATION', permissionIds: [] })
}

async function save() {
  saving.value = true
  try {
    await dispatch('SAVE_ROLE', { ...form })
    toast.success('Role saved')
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : 'Save failed')
  } finally {
    saving.value = false
  }
}

async function createPermission() {
  if (!newPermission.name.trim()) {
    toast.error('Permission name is required')
    return
  }
  creatingPermission.value = true
  try {
    await dispatch('CREATE_PERMISSION', { ...newPermission })
    toast.success('Permission created -- assign it to a role below')
    newPermission.name = ''
    newPermission.description = ''
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : 'Failed to create permission')
  } finally {
    creatingPermission.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="roles-page">
    <header class="page-head">
      <div>
        <div class="title-row">
          <h1>Roles &amp; permissions</h1>
          <v-chip size="small" variant="tonal" color="primary">Anchor-wide policy</v-chip>
        </div>
        <p>Define what a role can do, then bundle those permissions into the roles your users are assigned.</p>
      </div>
    </header>

    <!-- Permissions first: what a role can be built from -->
    <v-card variant="flat" border class="section-card mb-4">
      <v-card-title class="section-title">
        <v-icon icon="mdi-shield-key-outline" size="20" class="mr-2" />
        Permissions
      </v-card-title>
      <v-card-text>
        <p class="section-hint">The individual permissions available to bundle into roles below.</p>
        <div class="permission-chips">
          <v-chip v-for="p in permissions" :key="p.id" variant="tonal" color="primary" size="small" class="mb-1">
            <strong class="mr-1">{{ p.name.replaceAll('_', ' ') }}</strong>
            <span v-if="p.description" class="text-medium-emphasis">— {{ p.description }}</span>
          </v-chip>
          <span v-if="!loading && !permissions.length" class="text-caption text-medium-emphasis">No permissions yet.</span>
        </div>

        <v-divider class="my-4" />

        <div class="text-caption text-medium-emphasis mb-2">New permission</div>
        <v-row dense align="center">
          <v-col cols="12" sm="4">
            <v-text-field v-model="newPermission.name" label="Permission name" density="compact" hide-details placeholder="e.g. MANAGE_ATTENDANCE" />
          </v-col>
          <v-col cols="12" sm="5">
            <v-text-field v-model="newPermission.description" label="Description" density="compact" hide-details />
          </v-col>
          <v-col cols="12" sm="3">
            <v-btn block variant="outlined" prepend-icon="mdi-plus" :loading="creatingPermission" @click="createPermission">Add permission</v-btn>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <!-- Roles: bundles of the permissions above -->
    <v-card variant="flat" border class="section-card">
      <v-card-title class="section-title d-flex align-center justify-space-between">
        <span><v-icon icon="mdi-account-group-outline" size="20" class="mr-2" />Roles</span>
        <v-btn variant="outlined" size="small" prepend-icon="mdi-plus" @click="createRole">New role</v-btn>
      </v-card-title>
      <v-card-text>
        <div class="role-shell">
          <aside>
            <button v-for="r in roles" :key="r.id" :class="{ active: selected === r.id }" @click="edit(r)">
              <span>{{ r.name }}</span>
              <small>{{ r.permissions.length }} permission{{ r.permissions.length === 1 ? '' : 's' }} · {{ r.scope }}</small>
            </button>
            <p v-if="!loading && !roles.length" class="text-caption text-medium-emphasis">No roles yet -- create one.</p>
          </aside>

          <v-card border flat class="editor">
            <v-card-text>
              <div class="scope-band">
                <v-icon icon="mdi-shield-key-outline" />
                This role controls access across your anchor.
              </div>
              <div class="fields">
                <v-text-field v-model="form.name" label="Role name" variant="outlined" density="compact" />
                <v-select v-model="form.scope" :items="['ANCHOR', 'ORGANISATION']" label="Scope" variant="outlined" density="compact" />
                <v-textarea v-model="form.description" label="Description" variant="outlined" rows="2" density="compact" class="wide" />
              </div>
              <div class="permissions-head d-flex align-center justify-space-between flex-wrap">
                <h2 class="mb-0">Permissions</h2>
                <v-chip v-if="allPermissionsSelected" size="small" color="primary" variant="tonal">Full access -- every permission selected</v-chip>
              </div>
              <div class="permission-groups">
                <div v-for="group in permissionGroups" :key="group.key" class="permission-group">
                  <div class="group-head">
                    <span class="group-title">{{ group.label }}</span>
                    <label class="select-all">
                      <v-checkbox-btn
                        :model-value="isGroupFullySelected(group)"
                        :indeterminate="isGroupPartiallySelected(group)"
                        color="primary"
                        density="compact"
                        @update:model-value="toggleGroup(group)"
                      />
                      <span>Select all</span>
                    </label>
                  </div>
                  <div class="permissions">
                    <label v-for="item in group.items" :key="item.permission.id">
                      <v-checkbox-btn v-model="form.permissionIds" :value="item.permission.id" color="primary" />
                      <span>
                        <strong>{{ item.actionLabel }}</strong>
                        <small>{{ item.permission.description }}</small>
                      </span>
                    </label>
                  </div>
                </div>
                <p v-if="!permissions.length" class="text-caption text-medium-emphasis">Add a permission above first.</p>
              </div>
            </v-card-text>
            <v-card-actions class="px-6 pb-6">
              <v-spacer />
              <v-btn color="secondary" :loading="saving" @click="save">Save role</v-btn>
            </v-card-actions>
          </v-card>
        </div>
      </v-card-text>
    </v-card>
  </div>
</template>

<style scoped>
.roles-page { width: 100%; }
.page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-head h1 { font-size: 2rem; letter-spacing: -.04em; }
.page-head p { color: #64748b; margin-top: 4px; max-width: 60ch; }
.title-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }

.section-card { border-radius: 18px !important; }
.section-title { font-size: 1.05rem; font-weight: 700; }
.section-hint { color: #64748b; font-size: .85rem; margin-bottom: 12px; }
.permission-chips { display: flex; flex-wrap: wrap; gap: 8px; }

.role-shell { display: grid; grid-template-columns: 280px 1fr; gap: 18px; align-items: start; }
aside { display: flex; flex-direction: column; gap: 8px; }
aside button { text-align: left; background: #fff; border: 1px solid #e2e8f0; border-radius: 14px; padding: 15px 16px; color: #334155; }
aside button.active { border-color: #14b8a6; background: #f0fdfa; box-shadow: inset 3px 0 #0d9488; }
aside span, aside small { display: block; }
aside span { font-weight: 750; }
aside small { color: #64748b; margin-top: 3px; }
.editor { border-radius: 18px !important; }
.scope-band { display: flex; align-items: center; gap: 9px; background: #ecfdf5; color: #115e59; margin: -16px -16px 24px; padding: 12px 18px; }
.fields { display: grid; grid-template-columns: 1fr 220px; gap: 0 16px; }
.wide { grid-column: 1 / -1; }
h2 { font-size: .82rem; text-transform: uppercase; letter-spacing: .1em; color: #475569; margin: 8px 0 12px; }
.permissions-head { gap: 8px; margin: 8px 0 12px; }
.permissions-head h2 { margin: 0; }

.permission-groups { display: flex; flex-direction: column; gap: 14px; }
.permission-group { border: 1px solid #e2e8f0; border-radius: 14px; padding: 14px; }
.group-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 10px; }
.group-title { font-size: .78rem; font-weight: 750; text-transform: uppercase; letter-spacing: .07em; color: #334155; }
.select-all { display: flex; align-items: center; gap: 2px; cursor: pointer; font-size: .78rem; color: #475569; user-select: none; }

.permissions { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.permissions label { display: flex; gap: 8px; border: 1px solid #e2e8f0; border-radius: 12px; padding: 10px; }
.permissions strong, .permissions small { display: block; }
.permissions strong { font-size: .78rem; }
.permissions small { font-size: .75rem; color: #64748b; margin-top: 3px; }

@media (max-width: 800px) {
  .role-shell { grid-template-columns: 1fr; }
  .permissions, .fields { grid-template-columns: 1fr; }
  .wide { grid-column: auto; }
  .page-head { align-items: flex-start; gap: 14px; flex-direction: column; }
}
</style>
