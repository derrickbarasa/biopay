<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useAuthStore } from '@/stores/auth'
import { PERMISSION_GROUPS, isLegacyPermission, permissionActionLabel } from '@/constants/permissionCatalog'

interface Permission { id: number; name: string; displayName?: string; groupKey?: string; description: string; systemDefined?: boolean }
interface Role { id: number; name: string; description: string; scope: string; permissions: string[]; status: number; builtIn?: boolean; systemRole?: boolean; undeletable?: boolean; anchorId?: number }
interface Anchor { id: number; name: string }
interface PermissionItem { permission: Permission; actionLabel: string }
interface PermissionGroup { key: string; label: string; description: string; icon: string; items: PermissionItem[] }

const toast = useToast()
const auth = useAuthStore()
const { confirmAction } = useConfirm()

const SENSITIVE_CODES = new Set(['PAY_ONLINE', 'ACCESS_PAYMENTS', 'ACCESS_PAYMENT_CYCLES', 'ACCESS_VOUCHERS'])

// Starting points for common BioPay roles. "Use template" opens the create-role editor with
// these permissions pre-selected -- an anchor/org still reviews and adjusts before saving, it
// never creates the role silently. Codes reference the real catalogue in permissionCatalog.ts;
// any not present on this installation (e.g. a permission an anchor admin can't see) are dropped.
interface RoleTemplate { key: string; name: string; description: string; icon: string; permissionCodes: string[] }
const ROLE_TEMPLATES: RoleTemplate[] = [
  {
    key: 'FIELD_OFFICER', name: 'Field Officer', icon: 'mdi-account-hard-hat-outline',
    description: 'Front-line registration: households, alternates and attendance at assigned locations.',
    permissionCodes: ['ACCESS_HOUSEHOLDS', 'ACCESS_ALTERNATES', 'ACCESS_ATTENDANCE'],
  },
  {
    key: 'ANCHOR_COORDINATOR', name: 'Anchor Coordinator', icon: 'mdi-account-tie-outline',
    description: 'Runs an anchor day-to-day: everything a field officer does, plus payments, vouchers and reports.',
    permissionCodes: ['ACCESS_HOUSEHOLDS', 'ACCESS_ALTERNATES', 'ACCESS_ATTENDANCE', 'ACCESS_PAYMENTS', 'ACCESS_VOUCHERS', 'VIEW_REPORTS', 'DOWNLOAD_REPORTS', 'ACCESS_LOCATIONS'],
  },
  {
    key: 'PAYMENTS_OFFICER', name: 'Payments Officer', icon: 'mdi-cash-multiple',
    description: 'Processes disbursements: payments, payment cycles, vouchers and online pay-outs.',
    permissionCodes: ['ACCESS_PAYMENTS', 'PAY_ONLINE', 'ACCESS_PAYMENT_CYCLES', 'ACCESS_VOUCHERS', 'VIEW_REPORTS'],
  },
  {
    key: 'FINANCE_OFFICER', name: 'Finance Officer', icon: 'mdi-finance',
    description: 'Headquarters finance: payment cycles, subscription/billing and full reporting.',
    permissionCodes: ['ACCESS_PAYMENTS', 'ACCESS_PAYMENT_CYCLES', 'ACCESS_SUBSCRIPTION', 'VIEW_REPORTS', 'DOWNLOAD_REPORTS'],
  },
  {
    key: 'COMPLIANCE_AUDITOR', name: 'Compliance / Auditor', icon: 'mdi-shield-search-outline',
    description: 'Read-only oversight: views and downloads reports, no operational access.',
    permissionCodes: ['VIEW_REPORTS', 'DOWNLOAD_REPORTS'],
  },
  {
    key: 'USER_ADMINISTRATOR', name: 'User Administrator', icon: 'mdi-account-cog-outline',
    description: 'Manages who has access: users, roles, permissions and field officers.',
    permissionCodes: ['ACCESS_USERS', 'ACCESS_ROLES', 'ACCESS_PERMISSIONS', 'ACCESS_SUPERVISORS'],
  },
]

const loading = ref(false)
const saving = ref(false)
const permissionDialog = ref(false)
const creatingPermission = ref(false)
const roles = ref<Role[]>([])
const permissions = ref<Permission[]>([])
const anchors = ref<Anchor[]>([])
const tableSearch = ref('')
const permissionSearch = ref('')
const dialog = ref(false)
const openModules = ref<string[]>([])
const selectedTemplateKey = ref<string | null>(null)
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
// Organisation administrator: the same "Organisation Administrator" exception, one tier deeper
// -- they may fork it into an org-owned copy the same way an anchor forks the shared template
// (see Administration#saveRole's isOrgActor branch); they never see "Anchor Administrator" or
// "Platform Owner" in their role list at all, so no extra exclusion is needed here for those.
const isBuiltInRole = computed(() => {
  if (auth.isSystemAdmin) return false
  const role = selectedRole.value
  if (!role?.builtIn) return false
  return !((auth.isAnchorAdministrator || auth.isOrganisation) && role.scope === 'ORGANISATION')
})
const isUnlimitedRole = computed(() => !auth.isSystemAdmin && !!selectedRole.value?.systemRole)
// An organisation administrator can only ever grant organisation-wide access -- Anchor scope
// would reach beyond their own organisation, so their scope picker is always locked to it,
// not just once a role is selected (unlike the SYSTEM-scope lock below, which only matters when
// editing an existing System role since nobody but a Super Admin can create one in the first
// place, so there's no equivalent "locked while creating" case to cover).
const isFixedScopeRole = computed(() => selectedRole.value?.scope === 'SYSTEM' || auth.isOrganisation)
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
  ...(auth.isSystemAdmin ? [{ title: 'Anchor', key: 'anchorName' }] : []),
  { title: 'Actions', key: 'actions', sortable: false, align: 'start' as const, width: 104, minWidth: 104, fixed: true, nowrap: true },
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
      dispatch<{ results: Role[] }>('GET_ROLES', {}),
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
  selectedTemplateKey.value = null
  permissionSearch.value = ''
  openModules.value = permissionGroups.value.map((g) => g.key)
  dialog.value = true
}

function createRole() {
  Object.assign(form, { roleId: null, name: '', description: '', scope: 'ORGANISATION', permissionIds: [], anchorId: null })
  selectedTemplateKey.value = null
  permissionSearch.value = ''
  openModules.value = permissionGroups.value.map((g) => g.key)
  dialog.value = true
}

const displayedTemplates = computed(() => ROLE_TEMPLATES.map((template) => {
  const matched = permissions.value.filter((permission) => template.permissionCodes.includes(permission.name) && !isLegacyPermission(permission.name))
  const moneyMovingCount = matched.filter((permission) => isSensitive(permission.name)).length
  const exists = sortedRoles.value.some((role) => role.name.toLowerCase() === template.name.toLowerCase())
  return { ...template, matched, moneyMovingCount, exists }
}))

const roleTemplateItems = computed(() => displayedTemplates.value.map((template) => ({
  title: `${template.name} (${template.matched.length} permission${template.matched.length === 1 ? '' : 's'})`,
  value: template.key,
})))

function applyRoleTemplate(templateKey: string | null) {
  if (!templateKey) return
  const template = displayedTemplates.value.find((item) => item.key === templateKey)
  if (!template) return
  form.permissionIds = template.matched.map((permission) => permission.id)
  permissionSearch.value = ''
  openModules.value = permissionGroups.value.map((group) => group.key)
}

function useTemplate(template: (typeof displayedTemplates.value)[number]) {
  Object.assign(form, {
    roleId: null,
    name: template.name,
    description: template.description,
    scope: 'ORGANISATION',
    permissionIds: template.matched.map((permission) => permission.id),
    anchorId: null,
  })
  selectedTemplateKey.value = template.key
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
    await dispatch('DELETE_ROLE', { roleId: role.id })
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
</script>

<template>
  <div class="roles-page">
    <div class="page-heading d-flex align-center justify-space-between mb-5 ga-4">
      <div>
        <h1 class="page-title">Roles &amp; Permissions</h1>
        <p>{{ auth.isSystemAdmin ? "Every anchor's roles, in one place." : 'Create a role, then choose what it can access.' }}</p>
      </div>
      <div class="d-flex ga-2">
        <v-btn v-if="auth.isSystemAdmin" color="success" prepend-icon="mdi-plus" class="justify-center" @click="openCreatePermission">Create permission</v-btn>
        <v-btn v-if="auth.can('ACCESS_ROLES')" color="secondary" prepend-icon="mdi-plus" @click="createRole">New role</v-btn>
      </div>
    </div>

    <v-card variant="flat" border>
      <v-data-table class="roles-table" :headers="headers" :items="tableRows" :search="tableSearch" :loading="loading">
        <template #item.scope="{ item }"><v-chip size="small" variant="tonal">{{ scopeLabel(item.scope) }}</v-chip></template>
        <template #item.permissionCount="{ item }">{{ item.systemRole ? 'Unlimited' : item.permissionCount }}</template>
        <template #item.description="{ item }">{{ item.description || '—' }}</template>
        <template #item.actions="{ item }">
          <v-btn icon="mdi-pencil" variant="text" size="small" :aria-label="`Edit ${item.name}`" @click="openEdit(item)" />
          <v-btn
            v-if="auth.can('ACCESS_ROLES') && !item.undeletable" icon="mdi-delete-outline" variant="text" size="small" color="error"
            :aria-label="`Delete ${item.name}`" @click="removeRole(item)"
          />
        </template>
        <template #no-data>No roles yet. Create the first one.</template>
      </v-data-table>
    </v-card>

    <section v-if="auth.can('ACCESS_ROLES')" class="templates-section">
      <div class="templates-heading">
        <h2>Templates</h2>
        <p>Starting points for common roles. Using one opens a new role with its permissions already selected — adjust them before you create it.</p>
      </div>
      <div class="templates-grid">
        <v-card v-for="template in displayedTemplates" :key="template.key" variant="flat" border class="template-card">
          <div class="template-card-header">
            <v-icon :icon="template.icon" size="20" />
            <span class="template-name">{{ template.name }}</span>
            <v-chip v-if="template.exists" size="x-small" variant="tonal">Role exists</v-chip>
          </div>
          <p class="template-description">{{ template.description }}</p>
          <div class="template-meta">
            <v-chip size="small" variant="tonal">{{ template.matched.length }} permission{{ template.matched.length === 1 ? '' : 's' }}</v-chip>
            <v-chip v-if="template.moneyMovingCount" size="small" variant="tonal" color="error">{{ template.moneyMovingCount }} money-moving</v-chip>
          </div>
          <v-btn size="small" variant="outlined" prepend-icon="mdi-content-copy" class="mt-3" @click="useTemplate(template)">Use template</v-btn>
        </v-card>
      </div>
    </section>

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
                : auth.isOrganisation
                  ? [{ title: 'Organisation', value: 'ORGANISATION' }]
                  : [{ title: 'Organisation', value: 'ORGANISATION' }, { title: 'Anchor', value: 'ANCHOR' }]"
              label="Access scope" :disabled="isBuiltInRole || isFixedScopeRole" density="compact" hide-details="auto"
            />
            <v-select
              v-if="auth.isSystemAdmin && form.roleId === null && form.scope !== 'SYSTEM'" v-model="form.anchorId"
              :items="anchors" item-title="name" item-value="id" label="Anchor" density="compact" hide-details="auto"
            />
            <v-textarea
              v-model="form.description" label="Description" placeholder="What this role is for" rows="1" auto-grow density="compact" hide-details="auto" :disabled="isBuiltInRole"
              :class="{ 'span-2': !(auth.isSystemAdmin && form.roleId === null && form.scope !== 'SYSTEM') }"
            />
            <v-select
              v-if="form.roleId === null"
              v-model="selectedTemplateKey"
              :items="roleTemplateItems"
              label="Start from template (optional)"
              hint="Pre-selects permissions; you can still add or remove any of them."
              prepend-inner-icon="mdi-content-copy"
              class="span-2 role-template-select"
              density="compact"
              persistent-hint
              clearable
              @update:model-value="applyRoleTemplate"
            />
          </div>

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
          <v-btn v-if="form.roleId !== null && !selectedRole?.undeletable && auth.can('ACCESS_ROLES')" variant="outlined" color="error" @click="removeRole(selectedRole!)">Delete role</v-btn>
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
        <div class="identity-grid permission-form">
          <v-select v-model="newPermission.groupKey" :items="PERMISSION_GROUPS" item-title="label" item-value="key" label="Permission group" density="compact" hide-details="auto" />
          <v-text-field v-model="newPermission.displayName" label="Checkbox label" placeholder="Example: View audit log" density="compact" hide-details="auto" />
          <v-text-field v-model="newPermission.name" label="Permission code" placeholder="VIEW_AUDIT_LOG" density="compact" hide-details="auto" />
          <v-textarea v-model="newPermission.description" label="Description" rows="1" auto-grow density="compact" hide-details="auto" />
        </div>
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
.roles-table :deep(table) { min-width: 980px; }
.templates-section { margin-top: 28px; }
.templates-heading h2 { font-size: 1.1rem; font-weight: 700; color: #0f172a; letter-spacing: -.02em; }
.templates-heading p { color: #64748b; font-size: .85rem; margin: 4px 0 14px; }
.templates-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 14px; }
.template-card { padding: 16px; display: flex; flex-direction: column; }
.template-card-header { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.template-name { font-weight: 650; font-size: .92rem; color: #1e293b; }
.template-description { color: #64748b; font-size: .8rem; margin: 8px 0 0; flex: 1; }
.template-meta { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 12px; }
.page-heading h1 { color: #0f172a; letter-spacing: -.025em; }
.page-heading p { color: #64748b; font-size: .9rem; margin: 5px 0 0; }
.role-editor { padding: clamp(18px, 2.4vw, 26px); border-color: #cbd5e1 !important; background: #fff !important; }
.editor-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 16px; }
.editor-title { color: #0f172a; font-size: 1.15rem; font-weight: 750; letter-spacing: -.02em; }
.editor-heading p { color: #64748b; font-size: .82rem; margin: 3px 0 0; }
.identity-grid { display: grid; grid-template-columns: 1fr 1fr; column-gap: 16px; row-gap: 16px; margin-top: 18px; align-items: start; }
.identity-grid .span-2 { grid-column: 1 / -1; }
.role-template-select { margin-top: 2px; }
.permission-form { margin-top: 20px; }
@media (max-width: 600px) { .identity-grid { grid-template-columns: 1fr; } }
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
