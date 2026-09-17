<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useAnchorScope } from '@/composables/useAnchorScope'
import { useOrgCascade } from '@/composables/useOrgCascade'
import OfficerDataTable from '@/components/OfficerDataTable.vue'
import OfficerLocationDialog from '@/components/OfficerLocationDialog.vue'

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
const router = useRouter()
const { confirmAction } = useConfirm()
const { anchors, selectedAnchorId, anchorGateActive } = useAnchorScope()
// Separate, activeOnly list for the "create officer" dialog's anchor picker -- a
// deactivated anchor must still show on the page-level filter above (so its
// existing officers stay reviewable) but can't be picked for new work.
const { anchors: dialogAnchors } = useAnchorScope({ activeOnly: true })
const { dialogAnchorId, dialogOrganizations, resetDialogScope } = useOrgCascade()
const loading = ref(true)
const officers = ref<Officer[]>([])
const tableSearch = ref('')
const organizations = ref<{ organisationCode: string; name: string }[]>([])
const dialog = ref(false)
const editing = ref(false)
const saving = ref(false)
const form = ref({ firstName: '', lastName: '', email: '', organisationCode: '' })

const filters = ref({ organisationCode: null as string | null, active: null as string | null })

// Always true: an unset anchor/organisation filter already means "show all" server-side.
const scopeReady = computed(() => true)

async function load() {
  loading.value = true
  try {
    const res = await dispatch<{ results: Officer[] }>('GET_OFFICERS', {
      targetAnchorId: auth.isSystemAdmin ? selectedAnchorId.value : undefined,
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

watch(dialogAnchorId, () => { form.value.organisationCode = '' })
watch(() => filters.value.organisationCode, load)
watch(() => filters.value.active, load)
watch(selectedAnchorId, () => { filters.value.organisationCode = null; loadOrganizations(); load() })

function clearFilters() {
  filters.value = { organisationCode: null, active: null }
  load()
}

async function loadOrganizations() {
  try {
    const res = await dispatch<{ results: typeof organizations.value }>('GET_ORGANIZATIONS', {
      targetAnchorId: auth.isSystemAdmin ? selectedAnchorId.value : undefined,
    })
    organizations.value = res.results
  } catch {
    // Filter dropdown just stays empty; the list itself still loaded above.
  }
}

onMounted(() => {
  load()
  loadOrganizations()
})

function openCreate() {
  editing.value = false
  form.value = { firstName: '', lastName: '', email: '', organisationCode: '' }
  resetDialogScope(auth.isSystemAdmin ? selectedAnchorId.value : null)
  dialog.value = true
}

function openEdit(officer: Officer) {
  editing.value = true
  form.value = { firstName: officer.firstName, lastName: officer.lastName, email: officer.email, organisationCode: officer.organisationCode }
  dialog.value = true
}

function openHistory(officer: Officer) {
  router.push({
    name: 'activity-history',
    params: { actorKind: 'OFFICER', actorId: officer.id },
    query: { name: `${officer.firstName} ${officer.lastName}`.trim() || 'Field officer' },
  })
}

async function save() {
  if (!form.value.firstName.trim() || !form.value.lastName.trim() || !/.+@.+\..+/.test(form.value.email)) {
    toast.error('Enter the officer\'s first name, last name and a valid email address')
    return
  }
  if (auth.isSystemAdmin && !editing.value && !dialogAnchorId.value) {
    toast.error('Select the anchor this officer\'s organisation belongs to')
    return
  }
  if (auth.isAnchor && !editing.value && !form.value.organisationCode) {
    toast.error('Select the organisation this officer belongs to')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await dispatch('UPDATE_OFFICER', form.value)
      toast.success('Officer updated')
    } else {
      await dispatch('CREATE_OFFICER', form.value)
      toast.success('Officer registered. Temporary password sent by email')
    }
    dialog.value = false
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Save failed')
  } finally {
    saving.value = false
  }
}

async function setOfficerActive(officer: Officer, active: boolean) {
  if (!await confirmAction({
    title: `${active ? 'Activate' : 'Deactivate'} officer?`,
    message: active
      ? `${officer.firstName} ${officer.lastName} will regain access to the field app.`
      : `${officer.firstName} ${officer.lastName} will no longer be able to sign in to the field app.`,
    confirmLabel: active ? 'Activate' : 'Deactivate',
    color: active ? 'secondary' : 'warning',
  })) return
  try {
    await dispatch('TOGGLE_OFFICER_STATUS', { email: officer.email, active: active ? 1 : 0 })
    toast.success(active ? 'Officer activated' : 'Officer deactivated')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : `Failed to ${active ? 'activate' : 'deactivate'} officer`)
  }
}

async function removeOfficer(officer: Officer) {
  if (!await confirmAction({
    title: 'Delete officer?',
    message: `${officer.firstName} ${officer.lastName} will be permanently removed. This cannot be undone. The officer must already be deactivated first.`,
    confirmLabel: 'Delete officer',
    color: 'error',
    requireTypedText: 'DELETE',
  })) return
  try {
    await dispatch('DELETE_OFFICER', { email: officer.email })
    toast.success('Officer deleted')
    await load()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Delete failed')
  }
}

// ---- Assign locations ----
const locationDialog = ref(false)
const locationTarget = ref<Officer | null>(null)
function openAssignLocations(officer: Officer) {
  locationTarget.value = officer
  locationDialog.value = true
}
</script>

<template>
  <div>
    <div class="d-flex align-center justify-space-between mb-4">
      <h1 class="page-title">Field Officers</h1>
      <v-btn v-if="scopeReady && auth.can('ACCESS_SUPERVISORS')" color="secondary" prepend-icon="mdi-account-plus" @click="openCreate">Register Field Officer</v-btn>
    </div>

    <template v-if="scopeReady">
    <v-card variant="flat" border>
      <v-card-text>
        <v-row dense align="center">
          <v-col v-if="anchorGateActive" cols="12" sm="4" md="3">
            <v-select v-model="selectedAnchorId" :items="anchors" item-title="name" item-value="id" label="Anchor" clearable hide-details density="compact" prepend-inner-icon="mdi-bank-outline" />
          </v-col>
          <v-col v-if="auth.isSystemAdmin || auth.isAnchorAdministrator" cols="12" sm="4" md="3">
            <v-select v-model="filters.organisationCode" :items="organizations" item-title="name" item-value="organisationCode" label="Organisation" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="12" sm="4" md="3">
            <v-select v-model="filters.active" :items="[{ title: 'Active', value: '1' }, { title: 'Inactive', value: '0' }]" label="Status" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="12" sm="4" md="3">
            <v-text-field v-model="tableSearch" prepend-inner-icon="mdi-magnify" label="Search" clearable hide-details density="compact" />
          </v-col>
          <v-col cols="auto">
            <v-btn variant="text" size="small" @click="clearFilters">Clear filters</v-btn>
          </v-col>
        </v-row>
      </v-card-text>
      <OfficerDataTable
        :items="officers"
        :organizations="organizations"
        :search="tableSearch"
        :loading="loading"
        :can-manage="auth.can('ACCESS_SUPERVISORS')"
        :can-view-history="auth.can('ACCESS_USERS') || auth.can('ACCESS_SUPERVISORS')"
        :can-assign-locations="auth.can('ACCESS_SUPERVISORS')"
        @history="openHistory"
        @edit="openEdit"
        @toggle-status="setOfficerActive"
        @assign-location="openAssignLocations"
        @delete="removeOfficer"
      />
    </v-card>
    </template>

    <v-dialog v-model="dialog" max-width="560">
      <v-card class="officer-editor">
        <div class="editor-heading">
          <div>
            <div class="editor-title"><v-icon icon="mdi-account-tie" size="20" /> {{ editing ? 'Edit Officer' : 'Register Field Officer' }}</div>
            <p>{{ editing ? 'Update this officer\'s profile.' : 'Create a field officer account and assign their organisation.' }}</p>
          </div>
          <dialog-close-button @close="dialog = false" />
        </div>
        <v-form @submit.prevent="save">
          <div class="field-grid">
            <v-select
              v-if="auth.isSystemAdmin && !editing"
              v-model="dialogAnchorId" :items="dialogAnchors" item-title="name" item-value="id"
              label="Anchor" density="compact" placeholder="Choose an anchor" required
            />
            <v-select
              v-if="auth.isAnchor && !editing"
              v-model="form.organisationCode" :items="dialogOrganizations" item-title="name" item-value="organisationCode"
              label="Organisation" density="compact" placeholder="Choose an organisation"
              :disabled="auth.isSystemAdmin && !dialogAnchorId" required
            />
            <v-text-field v-model="form.firstName" label="First name" placeholder="e.g. Jane" density="compact" required />
            <v-text-field v-model="form.lastName" label="Last name" placeholder="e.g. Mwangi" density="compact" required />
            <v-text-field v-model="form.email" label="Email" type="email" placeholder="e.g. jane.mwangi@example.org" :disabled="editing" density="compact" required />
          </div>
          <v-alert v-if="!editing" type="info" variant="tonal" density="compact" class="mt-1">
            A temporary password will be generated and emailed to this officer.
          </v-alert>
          <div class="editor-actions">
            <v-btn variant="flat" color="error" @click="dialog = false">Cancel</v-btn>
            <v-btn color="secondary" type="submit" :loading="saving" prepend-icon="mdi-check">Save</v-btn>
          </div>
        </v-form>
      </v-card>
    </v-dialog>

    <OfficerLocationDialog v-model="locationDialog" :officer="locationTarget" />

  </div>
</template>

<style scoped>
.officer-editor { padding: 22px 24px; }
.editor-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.editor-title { display: flex; align-items: center; gap: 8px; color: #0f172a; font-size: 1.05rem; font-weight: 750; letter-spacing: -.02em; }
.editor-heading p { color: #64748b; font-size: .8rem; margin: 4px 0 0; }
.field-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 16px; }
.editor-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
@media (max-width: 520px) {
  .field-grid { grid-template-columns: 1fr; }
  .editor-actions :deep(.v-btn) { flex: 1; }
}
</style>
