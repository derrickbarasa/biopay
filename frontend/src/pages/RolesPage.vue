<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/auth'
import { PERMISSION_GROUPS, isLegacyPermission, permissionActionLabel } from '@/constants/permissionCatalog'

interface Permission { id: number; name: string; displayName?: string; groupKey?: string; description: string; systemDefined?: boolean }
interface Role { id: number; name: string; description: string; scope: string; permissions: string[]; status: number; builtIn?: boolean; systemRole?: boolean; anchorId?: number }
interface Anchor { id: number; name: string }
interface PermissionItem { permission: Permission; actionLabel: string }
interface PermissionGroup { key: string; label: string; description: string; icon: string; items: PermissionItem[] }

const toast = useToast()
const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const permissionDialog = ref(false)
const creatingPermission = ref(false)
const roles = ref<Role[]>([])
const permissions = ref<Permission[]>([])
const anchors = ref<Anchor[]>([])
const selectedAnchorId = ref<number | null>(null)
const selected = ref<number | null>(null)
const permissionSearch = ref('')
const form = reactive({ roleId: null as number | null, name: '', description: '', scope: 'ORGANISATION', permissionIds: [] as number[], anchorId: null as number | null })
const newPermission = reactive({ name: '', displayName: '', groupKey: 'REPORTS', description: '' })

const assignablePermissions = computed(() => permissions.value.filter((permission) => !isLegacyPermission(permission.name)))
const selectedRole = computed(() => roles.value.find((role) => role.id === selected.value))
const anchorNameById = computed(() => new Map(anchors.value.map((a) => [a.id, a.name])))
function roleAnchorName(role: Role) { return role.anchorId != null ? anchorNameById.value.get(role.anchorId) ?? null : null }
// The Super Admin has full system access, so no role or permission is locked to them -- these
// built-in-role locks only ever apply to an Anchor Administrator managing their own tenant's roles.
const isBuiltInRole = computed(() => !auth.isSystemAdmin && !!selectedRole.value?.builtIn)
const isUnlimitedRole = computed(() => !auth.isSystemAdmin && !!selectedRole.value?.systemRole)
// The Super Admin role's scope has nowhere sensible to move to (there is no picker option for
// "System"), so its scope field alone stays fixed even though everything else about the role
// -- name, description, permissions -- opens up for the Super Admin like any other role.
const isFixedScopeRole = computed(() => selectedRole.value?.scope === 'SYSTEM')
// Creating a brand-new tenant role needs a target anchor -- chosen right here in the editor,
// not as a page-wide precondition -- since a role has to belong to some anchor. Editing an
// existing one never does: an Anchor Administrator's own anchor is always known from their
// session, and the Super Admin can save changes to any anchor's role without narrowing the
// page down to that one tenant first.
const canSave = computed(() => auth.can('ACCESS_ROLES') && (!auth.isSystemAdmin || form.roleId !== null || !!form.anchorId) && !isBuiltInRole.value)
const selectedPermissionCount = computed(() => form.permissionIds.filter((id) => assignablePermissions.value.some((permission) => permission.id === id)).length)
const allPermissionsSelected = computed(() => assignablePermissions.value.length > 0 && selectedPermissionCount.value === assignablePermissions.value.length)

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
function isGroupPartiallySelected(group: PermissionGroup) {
  const count = groupSelectedCount(group)
  return count > 0 && count < group.items.length
}
function toggleGroup(group: PermissionGroup) {
  const ids = new Set(group.items.map((item) => item.permission.id))
  form.permissionIds = isGroupFullySelected(group)
    ? form.permissionIds.filter((id) => !ids.has(id))
    : [...new Set([...form.permissionIds, ...ids])]
}

async function load(preferredRoleId?: number | null) {
  loading.value = true
  try {
    const [roleResponse, permissionResponse] = await Promise.all([
      dispatch<{ results: Role[] }>('GET_ROLES', auth.isSystemAdmin ? { targetAnchorId: selectedAnchorId.value } : {}),
      dispatch<{ results: Permission[] }>('GET_PERMISSIONS'),
    ])
    roles.value = roleResponse.results ?? []
    permissions.value = permissionResponse.results ?? []
    const target = roles.value.find((role) => role.id === preferredRoleId) ?? roles.value.find((role) => role.id === selected.value) ?? roles.value[0]
    if (target) edit(target)
  } catch (error) {
    toast.error(error instanceof Error ? error.message : 'Unable to load roles')
  } finally {
    loading.value = false
  }
}

function edit(role: Role) {
  selected.value = role.id
  Object.assign(form, {
    roleId: role.id,
    name: role.name,
    description: role.description ?? '',
    scope: role.scope,
    permissionIds: permissions.value.filter((permission) => role.permissions.includes(permission.name) && !isLegacyPermission(permission.name)).map((permission) => permission.id),
    anchorId: role.anchorId ?? null,
  })
  permissionSearch.value = ''
}

function createRole() {
  selected.value = null
  Object.assign(form, { roleId: null, name: '', description: '', scope: 'ORGANISATION', permissionIds: [], anchorId: selectedAnchorId.value })
  permissionSearch.value = ''
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
  if (auth.isSystemAdmin && form.roleId === null && !form.anchorId) {
    toast.error('Choose an anchor for this role')
    return
  }
  saving.value = true
  try {
    const wasNew = form.roleId === null
    await dispatch('SAVE_ROLE', { ...form, targetAnchorId: auth.isSystemAdmin && wasNew ? form.anchorId : undefined })
    toast.success(wasNew ? 'Role created' : 'Role permissions updated')
    await load()
  } catch (error) {
    toast.error(error instanceof Error ? error.message : 'Unable to save role')
  } finally {
    saving.value = false
  }
}

const deleting = ref(false)
const deleteRoleDialog = ref(false)

async function confirmDeleteRole() {
  if (form.roleId === null) return
  deleting.value = true
  try {
    await dispatch('DELETE_ROLE', { roleId: form.roleId, targetAnchorId: auth.isSystemAdmin ? selectedAnchorId.value : undefined })
    toast.success('Role deleted')
    deleteRoleDialog.value = false
    createRole()
    await load()
  } catch (error) {
    toast.error(error instanceof Error ? error.message : 'Unable to delete role')
  } finally {
    deleting.value = false
  }
}

async function deletePermission(permission: Permission) {
  if (!confirm(`Delete the "${permission.displayName || permission.name}" permission? Any role that has it will lose it.`)) return
  try {
    await dispatch('DELETE_PERMISSION', { permissionId: permission.id })
    toast.success('Permission deleted')
    await load(selected.value)
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
    await load(selected.value)
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
watch(selectedAnchorId, () => { selected.value = null; void load() })
</script>

<template>
  <div class="roles-page">
    <header class="page-head">
      <div>
        <div class="title-row">
          <h1>Roles &amp; permissions</h1>
          <v-chip size="small" variant="tonal" color="success">{{ auth.isSystemAdmin ? 'System policy control' : 'Anchor policy control' }}</v-chip>
        </div>
        <p>{{ auth.isSystemAdmin ? 'Your Super Admin access is permanent. Every anchor\'s roles are shown below -- choose one to focus on just that tenant, or create a new role for it.' : 'Create a role, then choose exactly what people with that role can view or change.' }}</p>
      </div>
      <div class="head-actions">
        <v-btn v-if="auth.isSystemAdmin" variant="outlined" prepend-icon="mdi-shield-plus-outline" @click="openCreatePermission">Create permission</v-btn>
        <v-btn v-if="auth.can('ACCESS_ROLES')" color="secondary" prepend-icon="mdi-plus" @click="createRole">Create role</v-btn>
      </div>
    </header>

    <v-select
      v-if="auth.isSystemAdmin" v-model="selectedAnchorId" :items="anchors" item-title="name" item-value="id" clearable
      label="Filter by anchor (optional)" variant="outlined" class="mb-5" style="max-width: 420px"
    />

    <div class="role-workspace">
      <aside class="role-list" aria-label="Available roles">
        <div class="role-list-head"><strong>Roles</strong><span>{{ roles.length }}</span></div>
        <v-skeleton-loader v-if="loading" type="list-item-two-line@3" />
        <template v-else>
          <button v-for="role in roles" :key="role.id" type="button" :class="{ active: selected === role.id }" @click="edit(role)">
            <span>{{ role.name }}</span>
            <small>{{ role.systemRole ? 'Unlimited platform access' : `${role.permissions.filter((permission) => !isLegacyPermission(permission)).length} permissions` }} · {{ role.scope === 'SYSTEM' ? 'System' : role.scope === 'ANCHOR' ? 'Anchor' : 'Organisation' }}{{ auth.isSystemAdmin && !selectedAnchorId && roleAnchorName(role) ? ` · ${roleAnchorName(role)}` : '' }}</small>
          </button>
        </template>
        <p v-if="!loading && !roles.length" class="empty-copy">No roles yet. Create the first role.</p>
      </aside>

      <main class="role-editor">
        <div class="editor-head">
          <div>
            <h2>{{ form.roleId === null ? 'Create a role' : `Edit ${selectedRole?.name ?? 'role'}` }}</h2>
            <p>{{ isUnlimitedRole ? 'Permanent platform access. This Super Admin role cannot be reduced, reassigned, or changed.' : form.roleId === null ? 'Name the role and assign only the access it needs.' : isBuiltInRole ? 'This built-in tenant administrator role is locked to its defined scope.' : 'Changes apply to every user assigned to this role.' }}</p>
          </div>
          <div class="selection-total" aria-live="polite"><strong>{{ selectedPermissionCount }}</strong><span>selected</span></div>
        </div>

        <div class="role-fields">
          <v-select v-if="auth.isSystemAdmin && form.roleId === null" v-model="form.anchorId" :items="anchors" item-title="name" item-value="id" label="Anchor" variant="outlined" density="compact" class="wide" />
          <v-text-field v-model="form.name" label="Role name" variant="outlined" density="compact" required :disabled="isBuiltInRole" />
          <v-select v-model="form.scope" :items="[{ title: 'Organisation', value: 'ORGANISATION' }, { title: 'Anchor', value: 'ANCHOR' }, { title: 'System', value: 'SYSTEM' }]" label="Access scope" variant="outlined" density="compact" :disabled="isBuiltInRole || isFixedScopeRole" />
          <v-textarea v-model="form.description" label="Description" variant="outlined" rows="2" density="compact" class="wide" :disabled="isBuiltInRole" />
        </div>

        <div class="permission-heading">
          <div><h3>Assign permissions</h3><p>Permissions are grouped by the part of BioPay they control.</p></div>
          <v-chip v-if="allPermissionsSelected" size="small" color="primary" variant="tonal">Full access</v-chip>
        </div>
        <v-text-field v-model="permissionSearch" label="Search permissions" prepend-inner-icon="mdi-magnify" clearable density="compact" hide-details class="permission-search" />

        <div class="permission-groups">
          <section v-for="group in permissionGroups" :key="group.key" class="permission-group">
            <div class="group-head">
              <div class="group-identity">
                <v-icon :icon="group.icon" size="21" />
                <div><h4>{{ group.label }}</h4><p>{{ group.description }}</p></div>
              </div>
              <label class="select-all">
                <v-checkbox-btn :model-value="isGroupFullySelected(group)" :indeterminate="isGroupPartiallySelected(group)" :disabled="isBuiltInRole" color="primary" density="compact" @update:model-value="toggleGroup(group)" />
                <span>{{ groupSelectedCount(group) }}/{{ group.items.length }}</span><strong>Select all</strong>
              </label>
            </div>
            <div class="permission-options">
              <label v-for="item in group.items" :key="item.permission.id" :class="{ selected: form.permissionIds.includes(item.permission.id) }">
                <v-checkbox-btn v-model="form.permissionIds" :value="item.permission.id" :disabled="isBuiltInRole" color="primary" />
                <span><strong>{{ item.actionLabel }}</strong><small>{{ item.permission.description }}</small></span>
                <v-btn
                  v-if="auth.isSystemAdmin && !item.permission.systemDefined"
                  icon="mdi-trash-can-outline" size="x-small" variant="text" color="error"
                  :title="`Delete ${item.actionLabel}`" @click.stop.prevent="deletePermission(item.permission)"
                />
              </label>
            </div>
          </section>
          <div v-if="!loading && !permissionGroups.length" class="empty-copy">No permissions match your search.</div>
        </div>

        <div class="editor-actions">
          <p v-if="!canSave">{{ isUnlimitedRole ? 'Super Admin access is enforced by the platform and cannot be changed.' : isBuiltInRole ? 'Built-in tenant administrator permissions are managed by BioPay policy.' : auth.isSystemAdmin && form.roleId === null && !form.anchorId ? 'Choose an anchor for this role.' : 'Your role can view roles but cannot change them.' }}</p>
          <v-btn v-if="canSave && form.roleId !== null" variant="outlined" color="error" prepend-icon="mdi-trash-can-outline" @click="deleteRoleDialog = true">Delete role</v-btn>
          <v-btn color="secondary" :loading="saving" :disabled="!canSave" prepend-icon="mdi-content-save-outline" @click="save">{{ form.roleId === null ? 'Create role' : 'Save permissions' }}</v-btn>
        </div>
      </main>
    </div>

    <v-dialog v-model="permissionDialog" max-width="560">
      <v-card class="permission-editor">
        <dialog-close-button @close="permissionDialog = false" />
        <v-card-title>Create permission</v-card-title>
        <v-card-subtitle>Add a permission to one of the dashboard groups.</v-card-subtitle>
        <v-card-text>
          <v-select v-model="newPermission.groupKey" :items="PERMISSION_GROUPS" item-title="label" item-value="key" label="Permission group" variant="outlined" />
          <v-text-field v-model="newPermission.displayName" label="Checkbox label" placeholder="Example: View audit log" variant="outlined" />
          <v-text-field v-model="newPermission.name" label="Permission code" placeholder="VIEW_AUDIT_LOG" variant="outlined" hint="Use a stable code that the related feature can check." persistent-hint />
          <v-textarea v-model="newPermission.description" label="Description" rows="2" variant="outlined" class="mt-3" />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="permissionDialog = false">Cancel</v-btn>
          <v-btn color="secondary" :loading="creatingPermission" @click="createPermission">Create permission</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="deleteRoleDialog" max-width="440">
      <v-card>
        <dialog-close-button @close="deleteRoleDialog = false" />
        <v-card-title>Delete "{{ selectedRole?.name }}"?</v-card-title>
        <v-card-text>This cannot be undone. Roles still assigned to a user can't be deleted -- reassign them first.</v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="deleteRoleDialog = false">Cancel</v-btn>
          <v-btn color="error" :loading="deleting" @click="confirmDeleteRole">Delete role</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.roles-page { width: 100%; }
.page-head { display: flex; justify-content: space-between; align-items: center; gap: 20px; margin-bottom: 24px; }
.page-head h1 { font-size: 2rem; letter-spacing: -.03em; }
.page-head p, .editor-head p, .permission-heading p, .group-identity p { color: #64748b; }
.page-head p { margin-top: 4px; max-width: 68ch; }
.title-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.head-actions { display: flex; align-items: center; gap: 10px; }
.role-workspace { display: grid; grid-template-columns: 270px minmax(0, 1fr); gap: 18px; align-items: start; }
.role-list, .role-editor { background: #fff; border: 1px solid #e2e8f0; border-radius: 16px; }
.role-list { padding: 10px; display: flex; flex-direction: column; gap: 7px; position: sticky; top: 18px; }
.role-list-head { display: flex; justify-content: space-between; align-items: center; padding: 8px 8px 10px; color: #334155; }
.role-list-head span { min-width: 24px; height: 24px; display: grid; place-items: center; border-radius: 999px; background: #e2e8f0; font-size: .75rem; font-weight: 700; }
.role-list button { width: 100%; text-align: left; background: transparent; border: 1px solid transparent; border-radius: 12px; padding: 13px 12px; color: #334155; cursor: pointer; }
.role-list button:hover { background: #f8fafc; }
.role-list button:focus-visible { outline: 3px solid rgba(13, 148, 136, .25); outline-offset: 1px; }
.role-list button.active { border-color: #99f6e4; background: #f0fdfa; }
.role-list button span, .role-list button small { display: block; }
.role-list button span { font-weight: 750; }
.role-list button small { color: #64748b; margin-top: 3px; }
.role-editor { padding: 24px; }
.editor-head, .permission-heading, .group-head, .editor-actions { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.editor-head { padding-bottom: 22px; border-bottom: 1px solid #e2e8f0; }
.editor-head h2 { font-size: 1.3rem; letter-spacing: -.02em; color: #0f172a; }
.editor-head p, .permission-heading p { margin-top: 3px; font-size: .86rem; }
.selection-total { display: flex; flex-direction: column; align-items: center; min-width: 74px; padding: 9px 12px; border-radius: 12px; background: #f0fdfa; color: #115e59; }
.selection-total strong { font-size: 1.15rem; line-height: 1; }
.selection-total span { font-size: .72rem; margin-top: 3px; }
.role-fields { display: grid; grid-template-columns: minmax(0, 1fr) 220px; gap: 0 16px; margin-top: 22px; }
.wide { grid-column: 1 / -1; }
.permission-heading { margin: 4px 0 14px; }
.permission-heading h3 { font-size: 1rem; color: #1e293b; }
.permission-search { max-width: 420px; margin-bottom: 16px; }
.permission-groups { display: flex; flex-direction: column; gap: 14px; }
.permission-group { border: 1px solid #e2e8f0; border-radius: 14px; overflow: hidden; }
.group-head { padding: 14px 16px; background: #f8fafc; }
.group-identity { display: flex; align-items: flex-start; gap: 11px; min-width: 0; }
.group-identity :deep(.v-icon) { color: #0f766e; margin-top: 1px; }
.group-identity h4 { color: #1e293b; font-size: .9rem; }
.group-identity p { margin-top: 2px; font-size: .76rem; }
.select-all { display: flex; align-items: center; white-space: nowrap; cursor: pointer; color: #475569; font-size: .75rem; }
.select-all > span { margin-right: 7px; color: #64748b; }
.permission-options { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1px; background: #e2e8f0; }
.permission-options label { display: flex; gap: 8px; min-height: 72px; padding: 11px 13px; background: #fff; cursor: pointer; transition: background-color .16s ease; }
.permission-options label:hover { background: #f8fafc; }
.permission-options label.selected { background: #f0fdfa; }
.permission-options label:last-child:nth-child(odd) { grid-column: 1 / -1; }
.permission-options label > span { flex: 1; min-width: 0; }
.permission-options strong, .permission-options small { display: block; }
.permission-options strong { color: #1e293b; font-size: .82rem; margin-top: 3px; }
.permission-options small { color: #64748b; font-size: .74rem; margin-top: 3px; line-height: 1.35; }
.editor-actions { margin-top: 22px; padding-top: 18px; border-top: 1px solid #e2e8f0; justify-content: flex-end; }
.editor-actions p { color: #92400e; font-size: .8rem; margin-right: auto; }
.empty-copy { color: #64748b; font-size: .82rem; padding: 16px 10px; }
.permission-editor { border-radius: 16px !important; }
@media (max-width: 900px) { .role-workspace { grid-template-columns: 1fr; } .role-list { position: static; } }
@media (max-width: 680px) { .page-head, .editor-head, .permission-heading, .group-head { align-items: flex-start; flex-direction: column; } .head-actions { width: 100%; flex-direction: column; } .page-head :deep(.v-btn) { width: 100%; } .role-editor { padding: 18px; } .role-fields, .permission-options { grid-template-columns: 1fr; } .wide { grid-column: auto; } .select-all { align-self: stretch; } .editor-actions { align-items: stretch; flex-direction: column; } }
</style>
