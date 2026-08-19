<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
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
        <p>Define what a role can do, then bundle those capabilities into the roles your users are assigned.</p>
      </div>
    </header>

    <!-- Permissions first: what a role can be built from -->
    <v-card variant="flat" border class="section-card mb-4">
      <v-card-title class="section-title">
        <v-icon icon="mdi-shield-key-outline" size="20" class="mr-2" />
        Permissions
      </v-card-title>
      <v-card-text>
        <p class="section-hint">The individual capabilities available to bundle into roles below.</p>
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
              <h2>Capabilities</h2>
              <div class="permissions">
                <label v-for="p in permissions" :key="p.id">
                  <v-checkbox-btn v-model="form.permissionIds" :value="p.id" color="primary" />
                  <span>
                    <strong>{{ p.name.replaceAll('_', ' ') }}</strong>
                    <small>{{ p.description }}</small>
                  </span>
                </label>
                <p v-if="!permissions.length" class="text-caption text-medium-emphasis">Add a permission above first.</p>
              </div>
            </v-card-text>
            <v-card-actions class="px-6 pb-6">
              <v-spacer />
              <v-btn color="primary" :loading="saving" @click="save">Save role</v-btn>
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
