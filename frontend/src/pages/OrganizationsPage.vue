<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { ORG_MODULES } from '@/types/user'

interface Organization {
  organisationCode: string
  name: string
  authorisedName?: string
  authorisedEmail?: string
  authorisedContact?: string
  address?: string
  status: number
  createdAt?: string
}

const auth = useAuthStore()
const toast = useToast()

const loading = ref(true)
const statusFilter = ref<number | null>(null)
const organizations = ref<Organization[]>([])
const tableSearch = ref('')
const dialog = ref(false)
const editing = ref(false)
const saving = ref(false)
const form = ref({
  organisationCode: '', name: '', authorisedName: '', authorisedEmail: '', authorisedContact: '', address: '',
  modules: [] as string[],
})

const headers = [
  { title: 'Code', key: 'organisationCode' },
  { title: 'Name', key: 'name' },
  { title: 'Contact', key: 'authorisedName' },
  { title: 'Email', key: 'authorisedEmail' },
  { title: 'Status', key: 'status' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'end' as const },
]

async function load() {
  loading.value = true
  try {
    const res = await dispatch<{ results: Organization[] }>('GET_ORGANIZATIONS', { status: statusFilter.value ?? undefined })
    organizations.value = res.results
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load organizations')
  } finally {
    loading.value = false
  }
}

watch(statusFilter, load)

onMounted(load)

function openCreate() {
  editing.value = false
  form.value = { organisationCode: '', name: '', authorisedName: '', authorisedEmail: '', authorisedContact: '', address: '', modules: [] }
  dialog.value = true
}

async function openEdit(org: Organization) {
  editing.value = true
  form.value = {
    organisationCode: org.organisationCode, name: org.name,
    authorisedName: org.authorisedName ?? '', authorisedEmail: org.authorisedEmail ?? '',
    authorisedContact: org.authorisedContact ?? '', address: org.address ?? '',
    modules: [],
  }
  dialog.value = true
  try {
    const res = await dispatch<{ results: string[] }>('GET_ORGANIZATION_MODULES', { organisationCode: org.organisationCode })
    form.value.modules = res.results
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load modules')
  }
}

async function save() {
  if (!form.value.modules.length) {
    toast.error('Select at least one module')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await Promise.all([
        dispatch('UPDATE_ORGANIZATION', form.value),
        dispatch('UPDATE_ORGANIZATION_MODULES', { organisationCode: form.value.organisationCode, modules: form.value.modules }),
      ])
      toast.success('Organization updated')
    } else {
      await dispatch('CREATE_ORGANIZATION', { ...form.value, anchorId: auth.user?.anchorId })
      toast.success('Organization created')
    }
    dialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Save failed')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(org: Organization) {
  try {
    await dispatch('TOGGLE_ORGANIZATION_STATUS', { organisationCode: org.organisationCode, status: org.status === 1 ? 0 : 1 })
    toast.success('Status updated')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Update failed')
  }
}
</script>

<template>
  <div>
    <div class="d-flex align-center justify-space-between mb-4">
      <h1 class="text-h5 font-weight-bold">Organizations</h1>
      <v-btn color="primary" prepend-icon="mdi-domain-plus" @click="openCreate">New Organization</v-btn>
    </div>

    <v-card variant="flat" border>
      <v-card-text>
        <div class="d-flex ga-3 flex-wrap">
          <v-select
            v-model="statusFilter" :items="[{ title: 'Active', value: 1 }, { title: 'Inactive', value: 0 }]"
            label="Status" clearable hide-details density="compact" style="max-width: 220px"
          />
          <v-text-field v-model="tableSearch" prepend-inner-icon="mdi-magnify" label="Search" clearable hide-details density="compact" style="max-width: 260px" />
        </div>
      </v-card-text>
      <v-data-table :headers="headers" :items="organizations" :search="tableSearch" :loading="loading">
        <template #item.status="{ item }">
          <v-chip size="small" :color="item.status === 1 ? 'success' : 'error'" variant="tonal">
            {{ item.status === 1 ? 'Active' : 'Inactive' }}
          </v-chip>
        </template>
        <template #item.actions="{ item }">
          <v-btn icon="mdi-pencil" variant="text" size="small" @click="openEdit(item)" />
          <v-btn
            :icon="item.status === 1 ? 'mdi-toggle-switch-off-outline' : 'mdi-toggle-switch'"
            variant="text" size="small" @click="toggleStatus(item)"
          />
        </template>
      </v-data-table>
    </v-card>

    <v-dialog v-model="dialog" max-width="520">
      <v-card>
        <v-card-title>{{ editing ? 'Edit Organization' : 'New Organization' }}</v-card-title>
        <v-card-text>
          <v-text-field v-model="form.organisationCode" label="Organization code" :disabled="editing" />
          <v-text-field v-model="form.name" label="Organization name" />
          <v-text-field v-model="form.authorisedName" label="Authorised contact name" />
          <v-text-field v-model="form.authorisedEmail" label="Authorised email" type="email" />
          <v-text-field v-model="form.authorisedContact" label="Authorised phone" />
          <v-text-field v-model="form.address" label="Address" />
          <div class="text-caption text-medium-emphasis mt-2 mb-1">Modules enabled for this organisation</div>
          <v-checkbox
            v-for="m in ORG_MODULES" :key="m.code"
            v-model="form.modules" :value="m.code" :label="m.label"
            density="compact" hide-details class="mb-1"
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="dialog = false">Cancel</v-btn>
          <v-btn color="primary" :loading="saving" @click="save">Save</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
