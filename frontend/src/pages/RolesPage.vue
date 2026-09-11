<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useAuthStore } from '@/stores/auth'
import { PERMISSION_GROUPS, isLegacyPermission, permissionActionLabel } from '@/constants/permissionCatalog'

interface Permission { id: number; name: string; displayName?: string; groupKey?: string; description: string; systemDefined?: boolean }
interface Role { id: number; name: string; description: string; scope: string; permissions: string[]; status: number; builtIn?: boolean; systemRole?: boolean; anchorId?: number }
interface Anchor { id: number; name: string }
interface PermissionItem { permission: Permission; actionLabel: string }
interface PermissionGroup { key: string; label: string; description: string; icon: string; items: PermissionItem[] }

const toast = useToast()
const auth = useAuthStore()
const { confirmAction } = useConfirm()

const SENSITIVE_CODES = new Set(['PAY_ONLINE', 'ACCESS_PAYMENTS', 'ACCESS_PAYMENT_CYCLES', 'ACCESS_VOUCHERS'])

const loading = ref(false)
const saving = ref(false)
const permissionDialog = ref(false)
const creatingPermission = ref(false)
const roles = ref<Role[]>([])
const permissions = ref<Permission[]>([])
const anchors = ref<Anchor[]>([])
const selectedAnchorId = ref<number | null>(null)
const tableSearch = ref('')
const permissionSearch = ref('')
const dialog = ref(false)
const openModules = ref<string[]>([])
const form = reactive({ roleId: null as number | null, name: '', description: '', scope: 'ORGANISATION', permissionIds: [] as number[], anchorId: null as number | null })
const newPermission = reactive({ name: '', displayName: '', groupKey: 'REPORTS', description: '' })

const assignablePermissions = computed(() => permissions.value.filter((permission) => !isLegacyPermission(permission.name)))
const selectedRole = computed(() => roles.value.find((role) => role.id === form.roleId))
const ownScopeRank = (role: Role) => (auth.isSystemAdmin ? (role.scope === 'SYSTEM' ? 0 : 1) : (role.scope === auth.role ? 0 : 1))
const sortedRoles = computed(() => [...roles.value].sort((a, b) => ownScopeRank(a) - ownScopeRank(b)))
const anchorNameById = computed(() => new Map(anchors.value.map((a) => [a.id, a.name])))
function roleAnchorName(role: Role) { return role.anchorId != null ? anchorNameById.value.get(role.anchorId) ?? null : null }
// System admin: no role is locked, built-in or not. Anchor administrator: every built-in role
// is locked except "Organisation Administrator" -- they may customize their own anchor's copy
// of it (forked into an anchor-owned row server-side on first save, see Administration#saveRole),
// but not "Anchor Administrator" (their own role -- editable-by-self risks a self-lockout) or
// the system-only "Super Admin" role, which anchor admins never see in their role list anyway.
const isBuiltInRole = computed(() => {
  if (auth.isSystemAdmin) return false
  const role = selectedRole.value
  if (!role?.builtIn) return false
  return !(auth.isAnchorAdministrator && role.scope === 'ORGANISATION')
})
const isUnlimitedRole = computed(() => !auth.isSystemAdmin && !!selectedRole.value?.systemRole)
const isFixedScopeRole = computed(() => selectedRole.value?.scope === 'SYSTEM')
const canSave = computed(() => auth.can('ACCESS_ROLES') && (!auth.isSystemAdmin || form.roleId !== null || form.scope === 'SYSTEM' || !!form.anchorId) && !isBuiltInRole.value)
const selectedPermissionCount = computed(() => form.permissionIds.filter((id) => assignablePermissions.value.some((permission) => permission.id === id)).length)

function isSensitive(code: string) { return SENSITIVE_CODES.has(code) }

const permissionGroups = computed<PermissionGroup[]>(() => {
  const query = permissionSearch.value.trim().toLowerCase()
  const known = new Set(PERMISSION_GROUPS.flatMap((group) => group.permissions))
  const groups: PermissionGroup[] = PERMISSION_GROUPS.map((group) => ({
    key: group.key, label: group.label, description: group.description, icon: group.icon,
    items: assignablePermissions.value
      .filter((permission) => group.permissions.includes(permission.name) || permission.groupKey === group.key)
      .map((permission) => ({ permission, actionLabel: permission.displayName || permissionActionLabel(permission.name) })),
  }))
  const uncatalogued = assignablePermissions.value
    .filter((permission) => !known.has(permission.name) && !PERMISSION_GROUPS.some((group) => group.key === permission.groupKey))
    .map((permission) => ({ permission, actionLabel: permission.displayName || permissionActionLabel(permission.name) }))
  if (uncatalogued.length) groups.push({ key: 'CUSTOM', label: 'Custom permissions', description: 'Additional permissions created for this BioPay installation.', icon: 'mdi-shield-plus-outline', items: uncatalogued })
  return groups
    .map((group) => ({ ...group, items: group.items.filter((item) => !query || `${group.label} ${item.actionLabel} ${item.permission.description ?? ''}`.toLowerCase().includes(query)) }))
    .filter((group) => group.items.length > 0)
})

function groupSelectedCount(group: PermissionGroup) {
  return group.items.filter((item) => form.permissionIds.includes(item.permission.id)).length
}
function isGroupFullySelected(group: PermissionGroup) {
  return group.items.length > 0 && groupSelectedCount(group) === group.items.length
}
function toggleGroup(group: PermissionGroup) {
  const ids = new Set(group.items.map((item) => item.permission.id))
  form.permissionIds = isGroupFullySelected(group)
    ? form.permissionIds.filter((id) => !ids.has(id))
    : [...new Set([...form.permissionIds, ...ids])]
}
function togglePermission(id: number) {
  if (isBuiltInRole.value) return
  const index = form.permissionIds.indexOf(id)
  if (index >= 0) form.permissionIds.splice(index, 1)
  else form.permissionIds.push(id)
}

const headers = computed(() => [
  { title: 'Name', key: 'name' },
  { title: 'Description', key: 'description' },
  { title: 'Scope', key: 'scope' },
  { title: 'Permissions', key: 'permissionCount' },
  ...(auth.isSystemAdmin && !selectedAnchorId.value ? [{ title: 'Anchor', key: 'anchorName' }] : []),
  { title: 'Actions', key: 'actions', sortable: false, align: 'end' as const },
])
const tableRows = computed(() => sortedRoles.value.map((role) => ({
  ...role,
  permissionCount: role.permissions.filter((code) => !isLegacyPermission(code)).length,
  anchorName: roleAnchorName(role) ?? '—',
})))
function scopeLabel(scope: string) { return scope === 'SYSTEM' ? 'System' : scope === 'ANCHOR' ? 'Anchor' : 'Organisation' }

async function load(preferredRoleId?: number | null) {
  loading.value = true
  try {
    const [roleResponse, permissionResponse] = await Promise.all([
      dispatch<{ results: Role[] }>('GET_ROLES', auth.isSystemAdmin ? { targetAnchorId: selectedAnchorId.value } : {}),
      dispatch<{ results: Permission[] }>('GET_PERMISSIONS'),
    ])
    roles.value = roleResponse.results ?? []
    permissions.value = permissionResponse.results ?? []
    if (preferredRoleId != null) {
      const target = roles.value.find((role) => role.id === preferredRoleId)
      if (target) fillForm(target)
    }
  } catch (error) {
    toast.error(error instanceof Error ? error.message : 'Unable to load roles')
  } finally {
    loading.value = false
  }
}

function fillForm(role: Role) {
  Object.assign(form, {
    roleId: role.id,
    name: role.name,
    description: role.description ?? '',
    scope: role.scope,
    permissionIds: permissions.value.filter((permission) => role.permissions.includes(permission.name) && !isLegacyPermission(permission.name)).map((permission) => permission.id),
    anchorId: role.anchorId ?? null,
  })
}

function openEdit(role: Role) {
  fillForm(role)
  permissionSearch.value = ''
  openModules.value = permissionGroups.value.map((g) => g.key)
  dialog.value = true
}

function createRole() {
  Object.assign(form, { roleId: null, name: '', description: '', scope: 'ORGANISATION', permissionIds: [], anchorId: selectedAnchorId.value })
  permissionSearch.value = ''
  openModules.value = permissionGroups.value.map((g) => g.key)
  dialog.value = true
}

async function save() {
  if (!form.name.trim()) {
    toast.error('Enter a role name before saving')
    return
  }
  if (!form.permissionIds.length) {
    toast.error('Select at least one permission for this role')
    return
  }
  if (auth.isSystemAdmin && form.roleId === null && form.scope !== 'SYSTEM' && !form.anchorId) {
    toast.error('Choose an anchor for this role')
    return
  }
  saving.value = true
  try {
    const wasNew = form.roleId === null
    await dispatch('SAVE_ROLE', { ...form, targetAnchorId: auth.isSystemAdmin && wasNew ? form.anchorId : undefined })
    toast.success(wasNew ? 'Role created' : 'Role permissions updated')
    dialog.value = false
    await load()
  } catch (error) {
    toast.error(error instanceof Error ? error.message : 'Unable to save role')
  } finally {
    saving.value = false
  }
}

async function removeRole(role: Role) {
  if (!await confirmAction({
    title: 'Delete role?',
    message: `Delete "${role.name}"? Roles still assigned to a user can't be deleted -- reassign them first.`,
    confirmLabel: 'Delete role',
    color: 'error',
  })) return
  try {
    await dispatch('DELETE_ROLE', { roleId: role.id, targetAnchorId: auth.isSystemAdmin ? selectedAnchorId.value : undefined })
    toast.success('Role deleted')
    dialog.value = false
    await load()
  } catch (error) {
    toast.error(error instanceof Error ? error.message : 'Unable to delete role')
  }
}

async function deletePermission(permission: Permission) {
  if (!await confirmAction({
    title: 'Delete permission?',
    message: `Delete the "${permission.displayName || permission.name}" permission? Any role that has it will lose it.`,
    confirmLabel: 'Delete permission',
    color: 'error',
  })) return
  try {
    await dispatch('DELETE_PERMISSION', { permissionId: permission.id })
    toast.success('Permission deleted')
    await load(form.roleId)
  } catch (error) {
    toast.error(error instanceof Error ? error.message : 'Unable to delete permission')
  }
}

function openCreatePermission() {
  Object.assign(newPermission, { name: '', displayName: '', groupKey: 'REPORTS', description: '' })
  permissionDialog.value = true
}

async function createPermission() {
  if (!newPermission.name.trim() || !newPermission.displayName.trim() || !newPermission.groupKey) {
    toast.error('Enter a permission code, label and group')
    return
  }
  creatingPermission.value = true
  try {
    await dispatch('CREATE_PERMISSION', { ...newPermission })
    toast.success('Permission created')
    permissionDialog.value = false
    await load(form.roleId)
  } catch (error) {
    toast.error(error instanceof Error ? error.message : 'Unable to create permission')
  } finally {
    creatingPermission.value = false
  }
}

onMounted(async () => {
  if (auth.isSystemAdmin) {
    const response = await dispatch<{ results: Anchor[] }>('GET_ANCHORS')
    anchors.value = response.results ?? []
  }
  await load()
})
watch(selectedAnchorId, () => void load())
</script>

<template>
  <div class="roles-page">
    <div class="page-heading d-flex align-center justify-space-between mb-5 ga-4">
      <div>
        <h1 class="page-title">Roles &amp; permissions</h1>
        <p>{{ auth.isSystemAdmin ? "Every anchor's roles, in one place." : 'Create a role, then choose what it can access.' }}</p>
      </div>
      <div class="d-flex ga-2">
        <v-btn v-if="auth.isSystemAdmin" variant="outlined" prepend-icon="mdi-shield-plus-outline" @click="openCreatePermission">Create permission</v-btn>
        <v-btn v-if="auth.can('ACCESS_ROLES')" color="secondary" prepend-icon="mdi-plus" @click="createRole">New role</v-btn>
      </div>
    </div>

    <v-card variant="flat" border>
      <v-card-text v-if="auth.isSystemAdmin">
        <v-select
          v-model="selectedAnchorId" :items="anchors" item-title="name" item-value="id" clearable
          label="Anchor" density="compact" hide-details style="max-width: 260px" prepend-inner-icon="mdi-bank-outline"
        />
      </v-card-text>
      <v-data-table :headers="headers" :items="tableRows" :search="tableSearch" :loading="loading">
        <template #item.scope="{ item }"><v-chip size="small" variant="tonal">{{ scopeLabel(item.scope) }}</v-chip></template>
        <template #item.permissionCount="{ item }">{{ item.systemRole ? 'Unlimited' : item.permissionCount }}</template>
        <template #item.description="{ item }">{{ item.description || '—' }}</template>
        <template #item.actions="{ item }">
          <v-btn icon="mdi-pencil" variant="text" size="small" :aria-label="`Edit ${item.name}`" @click="openEdit(item)" />
          <v-btn
            v-if="canSave && !item.builtIn && !item.systemRole" icon="mdi-delete-outline" variant="text" size="small" color="error"
            :aria-label="`Delete ${item.name}`" @click="removeRole(item)"
          />
        </template>
        <template #no-data>No roles yet. Create the first one.</template>
      </v-data-table>
    </v-card>

    <v-dialog v-model="dialog" max-width="760" scrollable>
      <v-card class="role-editor" variant="flat" border>
        <div class="editor-heading">
          <div>
            <div class="editor-title">{{ form.roleId === null ? 'Create role' : `Edit ${selectedRole?.name ?? 'role'}` }}</div>
            <p>{{ isUnlimitedRole ? 'Permanent platform access -- this role cannot be changed.' : isBuiltInRole ? 'This built-in role is locked to its defined scope.' : 'Changes apply to every user assigned to this role.' }}</p>
          </div>
          <dialog-close-button @close="dialog = false" />
        </div>

        <v-form>
          <div class="identity-grid">
            <v-text-field v-model="form.name" label="Role name" placeholder="e.g. Regional Coordinator" :disabled="isBuiltInRole" density="compact" hide-details="auto" />
            <v-select
              v-model="form.scope"
              :items="auth.isSystemAdmin
                ? [{ title: 'Organisation', value: 'ORGANISATION' }, { title: 'Anchor', value: 'ANCHOR' }, { title: 'System', value: 'SYSTEM' }]
                : [{ title: 'Organisation', value: 'ORGANISATION' }, { title: 'Anchor', value: 'ANCHOR' }]"
              label="Access scope" :disabled="isBuiltInRole || isFixedScopeRole" density="compact" hide-details="auto"
            />
          </div>
          <v-select
            v-if="auth.isSystemAdmin && form.roleId === null && form.scope !== 'SYSTEM'" v-model="form.anchorId"
            :items="anchors" item-title="name" item-value="id" label="Anchor" density="compact" hide-details="auto" class="mt-3"
          />
          <v-textarea v-model="form.description" label="Description" placeholder="What this role is for" rows="2" density="compact" hide-details="auto" class="mt-3" :disabled="isBuiltInRole" />

          <section class="permissions-section">
            <div class="permissions-heading-row">
              <div><h3>Permissions</h3><p>Grouped by the part of BioPay they control.</p></div>
              <div class="permissions-meta">
                <span>{{ selectedPermissionCount }} of {{ assignablePermissions.length }} selected</span>
                <v-btn v-if="selectedPermissionCount" size="small" variant="text" :disabled="isBuiltInRole" @click="form.permissionIds = []">Clear all</v-btn>
              </div>
            </div>
            <v-text-field v-model="permissionSearch" placeholder="Filter permissions…" prepend-inner-icon="mdi-magnify" density="compact" hide-details clearable class="mb-3" />

            <v-expansion-panels v-model="openModules" multiple variant="accordion" class="permission-modules">
              <v-expansion-panel v-for="group in permissionGroups" :key="group.key" :value="group.key">
                <v-expansion-panel-title>
                  <div class="module-title-row">
                    <v-icon :icon="group.icon" size="19" />
                    <span>{{ group.label }}</span>
                    <v-chip size="x-small" variant="tonal">{{ groupSelectedCount(group) }}/{{ group.items.length }}</v-chip>
                    <v-btn size="x-small" variant="text" :disabled="isBuiltInRole" @click.stop="toggleGroup(group)">
                      {{ isGroupFullySelected(group) ? 'Clear' : 'Select all' }}
                    </v-btn>
                  </div>
                </v-expansion-panel-title>
                <v-expansion-panel-text>
                  <p class="module-description">{{ group.description }}</p>
                  <div class="permission-chip-row">
                    <v-chip
                      v-for="item in group.items" :key="item.permission.id"
                      :color="form.permissionIds.includes(item.permission.id) ? (isSensitive(item.permission.name) ? 'error' : 'primary') : undefined"
                      :variant="form.permissionIds.includes(item.permission.id) ? 'flat' : 'outlined'"
                      :disabled="isBuiltInRole"
                      :closable="auth.isSystemAdmin && !item.permission.systemDefined"
                      :title="item.permission.description || item.actionLabel"
                      @click="togglePermission(item.permission.id)"
                      @click:close="deletePermission(item.permission)"
                    >
                      <v-icon v-if="form.permissionIds.includes(item.permission.id)" start size="14">mdi-check</v-icon>
                      {{ item.actionLabel }}
                    </v-chip>
                  </div>
                </v-expansion-panel-text>
              </v-expansion-panel>
            </v-expansion-panels>
            <p v-if="!permissionGroups.length" class="empty-copy">No permissions match "{{ permissionSearch }}".</p>
          </section>
        </v-form>

        <v-card-actions class="editor-actions">
          <p v-if="!canSave" class="lock-copy">{{ isUnlimitedRole ? 'Platform Owner access is fixed by the platform.' : isBuiltInRole ? 'This role is managed by BioPay policy.' : auth.isSystemAdmin && form.roleId === null && form.scope !== 'SYSTEM' && !form.anchorId ? 'Choose an anchor for this role.' : 'You can view roles but not change them.' }}</p>
          <v-btn v-if="canSave && form.roleId !== null" variant="outlined" color="error" @click="removeRole(selectedRole!)">Delete role</v-btn>
          <v-spacer />
          <v-btn variant="flat" color="error" @click="dialog = false">Cancel</v-btn>
          <v-btn variant="flat" color="secondary" :loading="saving" :disabled="!canSave" @click="save">{{ form.roleId === null ? 'Create role' : 'Save changes' }}</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="permissionDialog" max-width="640">
      <v-card class="role-editor" variant="flat" border>
        <div class="editor-heading">
          <div>
            <div class="editor-title">Create permission</div>
            <p>Add a permission to one of the dashboard groups.</p>
          </div>
          <dialog-close-button @close="permissionDialog = false" />
        </div>
        <v-row dense>
          <v-col cols="12" sm="6"><v-select v-model="newPermission.groupKey" :items="PERMISSION_GROUPS" item-title="label" item-value="key" label="Permission group" density="compact" hide-details="auto" /></v-col>
          <v-col cols="12" sm="6"><v-text-field v-model="newPermission.displayName" label="Checkbox label" placeholder="Example: View audit log" density="compact" hide-details="auto" /></v-col>
          <v-col cols="12" sm="6"><v-text-field v-model="newPermission.name" label="Permission code" placeholder="VIEW_AUDIT_LOG" hint="Use a stable code the related feature can check." persistent-hint density="compact" /></v-col>
          <v-col cols="12" sm="6"><v-textarea v-model="newPermission.description" label="Description" rows="2" density="compact" hide-details="auto" /></v-col>
        </v-row>
        <v-card-actions class="editor-actions">
          <v-spacer />
          <v-btn variant="flat" color="error" @click="permissionDialog = false">Cancel</v-btn>
          <v-btn variant="flat" color="secondary" :loading="creatingPermission" @click="createPermission">Create permission</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.roles-page { width: 100%; }
.page-heading h1 { color: #0f172a; letter-spacing: -.025em; }
.page-heading p { color: #64748b; font-size: .9rem; margin: 5px 0 0; }
.role-editor { padding: clamp(18px, 2.4vw, 26px); border-color: #cbd5e1 !important; background: #fff !important; }
.editor-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 16px; }
.editor-title { color: #0f172a; font-size: 1.15rem; font-weight: 750; letter-spacing: -.02em; }
.editor-heading p { color: #64748b; font-size: .82rem; margin: 3px 0 0; }
.identity-grid { display: grid; grid-template-columns: 1fr 220px; column-gap: 16px; }
.permissions-section { border-top: 1px solid #e2e8f0; margin-top: 18px; padding-top: 16px; }
.permissions-heading-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 12px; }
.permissions-heading-row h3 { font-size: 1rem; color: #1e293b; }
.permissions-heading-row p { color: #64748b; font-size: .82rem; margin: 3px 0 0; }
.permissions-meta { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.permissions-meta > span { color: #64748b; font-size: .78rem; white-space: nowrap; }
.permission-modules { border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden; }
.permission-modules :deep(.v-expansion-panel-title) { min-height: 46px; }
.module-title-row { display: flex; align-items: center; gap: 8px; width: 100%; }
.module-title-row span { font-size: .88rem; font-weight: 650; color: #1e293b; }
.module-title-row .v-btn { margin-left: auto; margin-right: 8px; }
.module-description { color: #64748b; font-size: .78rem; margin: 0 0 10px; }
.permission-chip-row { display: flex; flex-wrap: wrap; gap: 8px; }
.editor-actions { padding: 0; margin-top: 18px; }
.lock-copy { color: #92400e; font-size: .8rem; margin-right: auto; }
.empty-copy { color: #64748b; font-size: .82rem; padding: 16px 4px; }
@media (max-width: 680px) {
  .page-heading { align-items: flex-start !important; flex-direction: column; }
  .identity-grid { grid-template-columns: 1fr; }
  .editor-actions { flex-wrap: wrap; }
  .editor-actions :deep(.v-btn) { flex: 1; }
}
</style>
