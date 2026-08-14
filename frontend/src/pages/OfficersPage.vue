<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

interface Officer {
  id: number
  email: string
  firstName: string
  lastName: string
  organisationCode: string
  active: string
  createdAt?: string
}

const auth = useAuthStore()
const toast = useToast()
const loading = ref(true)
const officers = ref<Officer[]>([])
const organizations = ref<{ organisationCode: string; name: string }[]>([])
const dialog = ref(false)
const saving = ref(false)
const form = ref({ firstName: '', lastName: '', email: '' })

const filters = ref({ organisationCode: null as string | null, active: null as string | null })

const headers = [
  { title: 'Name', key: 'name' },
  { title: 'Email', key: 'email' },
  { title: 'Organization', key: 'organisationCode' },
  { title: 'Status', key: 'active' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'end' as const },
]

async function load() {
  loading.value = true
  try {
    const res = await dispatch<{ results: Officer[] }>('GET_OFFICERS', {
      organisationCode: filters.value.organisationCode ?? undefined,
      active: filters.value.active ?? undefined,
    })
    officers.value = res.results
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load officers')
  } finally {
    loading.value = false
  }
}

watch(() => filters.value.organisationCode, load)
watch(() => filters.value.active, load)

function clearFilters() {
  filters.value = { organisationCode: null, active: null }
  load()
}

onMounted(async () => {
  load()
  if (auth.isAnchor) {
    try {
      const res = await dispatch<{ results: typeof organizations.value }>('GET_ORGANIZATIONS')
      organizations.value = res.results
    } catch {
      // Filter dropdown just stays empty; the list itself still loaded above.
    }
  }
})

function openCreate() {
  form.value = { firstName: '', lastName: '', email: '' }
  dialog.value = true
}

async function save() {
  saving.value = true
  try {
    await dispatch('CREATE_OFFICER', form.value)
    toast.success('Officer registered. Temporary password sent by email')
    dialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Save failed')
  } finally {
    saving.value = false
  }
}

async function remove(officer: Officer) {
  try {
    await dispatch('DELETE_OFFICER', { email: officer.email })
    toast.success('Officer deactivated')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Delete failed')
  }
}
</script>

<template>
  <div>
    <div class="d-flex align-center justify-space-between mb-4">
      <h1 class="text-h5 font-weight-bold">Field Officers</h1>
      <v-btn color="primary" prepend-icon="mdi-account-plus" @click="openCreate">Register Officer</v-btn>
    </div>

    <v-card variant="flat" border>
      <v-card-text>
        <v-row dense align="center">
          <v-col v-if="auth.isAnchor" cols="12" sm="4" md="3">
            <v-select v-model="filters.organisationCode" :items="organizations" item-title="name" item-value="organisationCode" label="Organisation" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="12" sm="4" md="3">
            <v-select v-model="filters.active" :items="[{ title: 'Active', value: '1' }, { title: 'Inactive', value: '0' }]" label="Status" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="auto">
            <v-btn variant="text" size="small" @click="clearFilters">Clear filters</v-btn>
          </v-col>
        </v-row>
      </v-card-text>
      <v-data-table :headers="headers" :items="officers" :loading="loading">
        <template #item.name="{ item }">{{ item.firstName }} {{ item.lastName }}</template>
        <template #item.active="{ item }">
          <v-chip size="small" :color="item.active === '1' ? 'success' : 'error'" variant="tonal">
            {{ item.active === '1' ? 'Active' : 'Inactive' }}
          </v-chip>
        </template>
        <template #item.actions="{ item }">
          <v-btn icon="mdi-delete" variant="text" size="small" color="error" @click="remove(item)" />
        </template>
      </v-data-table>
    </v-card>

    <v-dialog v-model="dialog" max-width="480">
      <v-card>
        <v-card-title>Register Field Officer</v-card-title>
        <v-card-text>
          <v-text-field v-model="form.firstName" label="First name" />
          <v-text-field v-model="form.lastName" label="Last name" />
          <v-text-field v-model="form.email" label="Email" type="email" />
          <v-alert type="info" variant="tonal" density="compact">
            A temporary password will be generated and emailed to this officer.
          </v-alert>
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
