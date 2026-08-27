<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { COUNTRIES } from '@/types/user'
import { capitalFor } from '@/utils/countries'

interface Anchor { id:number; anchorCode:string; name:string; authorisedName?:string; authorisedEmail?:string; authorisedContact?:string; address?:string; country?:string; city?:string; status:number }

const toast = useToast()
const { confirmAction } = useConfirm()
const loading = ref(false)
const saving = ref(false)
const creating = ref(false)
const createDialog = ref(false)
const editDialog = ref(false)
const anchors = ref<Anchor[]>([])
const tableSearch = ref('')
const anchor = reactive<Anchor>({ id: 0, anchorCode: '', name: '', status: 1 })
const newAnchor = reactive({ name: '', authorisedName: '', authorisedEmail: '', authorisedContact: '', country: '', city: '', address: '' })

const headers = [
  { title: 'Code', key: 'anchorCode' },
  { title: 'Name', key: 'name' },
  { title: 'Administrator', key: 'authorisedName' },
  { title: 'Email', key: 'authorisedEmail' },
  { title: 'Country', key: 'country' },
  { title: 'Status', key: 'status' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'start' as const },
]

async function load() {
  loading.value = true
  try {
    const r = await dispatch<{ results: Anchor[] }>('GET_ANCHORS')
    anchors.value = r.results ?? []
  } catch (e) {
    toast.error(e instanceof Error ? e.message : 'Unable to load anchors')
  } finally {
    loading.value = false
  }
}

function openEdit(item: Anchor) {
  Object.assign(anchor, item)
  editDialog.value = true
}

async function save() {
  if (!anchor.name.trim()) { toast.error('Anchor name is required'); return }
  saving.value = true
  try {
    await dispatch('UPDATE_ANCHOR', { ...anchor, targetAnchorId: anchor.id })
    toast.success('Anchor details updated')
    editDialog.value = false
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : 'Update failed')
  } finally {
    saving.value = false
  }
}

function openCreate() {
  Object.assign(newAnchor, { name: '', authorisedName: '', authorisedEmail: '', authorisedContact: '', country: '', city: '', address: '' })
  createDialog.value = true
}

async function createAnchor() {
  if (!newAnchor.name.trim() || !newAnchor.authorisedName.trim() || !/.+@.+\..+/.test(newAnchor.authorisedEmail)) {
    toast.error('Complete the anchor name, administrator name and a valid email')
    return
  }
  creating.value = true
  try {
    await dispatch('CREATE_ANCHOR', newAnchor)
    toast.success('Anchor and administrator created')
    createDialog.value = false
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : 'Unable to create anchor')
  } finally {
    creating.value = false
  }
}

async function toggleStatus(item: Anchor) {
  const deleting = item.status === 1
  if (!await confirmAction({
    title: deleting ? 'Delete anchor?' : 'Restore anchor?',
    message: deleting
      ? `${item.name} and its administrator will no longer be able to sign in. Every organization beneath it is unaffected but will also lose access until restored. This can be undone.`
      : `${item.name} will be restored and its administrator can sign in again.`,
    confirmLabel: deleting ? 'Delete anchor' : 'Restore anchor',
    color: deleting ? 'error' : 'secondary',
  })) return
  try {
    await dispatch('TOGGLE_ANCHOR_STATUS', { targetAnchorId: item.id, status: deleting ? 0 : 1 })
    toast.success(deleting ? 'Anchor deleted' : 'Anchor restored')
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : 'Update failed')
  }
}

// Auto-fills the capital when a country is picked; still a plain editable field afterwards.
watch(() => anchor.country, (country, previous) => {
  if (country && country !== previous) anchor.city = capitalFor(country) || anchor.city
})
watch(() => newAnchor.country, (country, previous) => {
  if (country && country !== previous) newAnchor.city = capitalFor(country) || newAnchor.city
})
onMounted(load)
</script>

<template>
  <div class="admin-page">
    <header class="admin-head">
      <div><h1>Anchors</h1><p>Every anchor operating programmes in BioPay.</p></div>
      <div class="head-chips"><v-chip size="small" variant="tonal" color="primary">{{ anchors.length }} anchor{{ anchors.length === 1 ? '' : 's' }}</v-chip><v-btn color="secondary" prepend-icon="mdi-bank-plus" @click="openCreate">New anchor</v-btn></div>
    </header>

    <v-dialog v-model="createDialog" max-width="760">
      <v-card title="Create anchor" subtitle="This also creates the anchor administrator, assigns the next anchor code (ANC001, ANC002, ...) automatically, and emails a temporary password.">
        <dialog-close-button @close="createDialog = false" />
        <v-card-text class="form-grid">
          <v-text-field v-model="newAnchor.name" label="Anchor name" placeholder="e.g. Frontier Trust Bank" variant="outlined" required />
          <v-text-field v-model="newAnchor.authorisedName" label="Administrator name" placeholder="e.g. Jane Mwangi" variant="outlined" required />
          <v-text-field v-model="newAnchor.authorisedEmail" label="Administrator email" placeholder="e.g. jane@frontiertrust.bank" type="email" variant="outlined" required />
          <v-text-field v-model="newAnchor.authorisedContact" label="Phone" placeholder="e.g. +254 700 000000" variant="outlined" />
          <v-autocomplete v-model="newAnchor.country" :items="COUNTRIES" label="Country" variant="outlined" />
          <v-text-field v-model="newAnchor.city" label="City" placeholder="e.g. Nairobi" variant="outlined" />
          <v-text-field v-model="newAnchor.address" label="Address" placeholder="e.g. Karen Road" variant="outlined" class="wide" />
        </v-card-text>
        <v-card-actions><v-spacer/><v-btn variant="text" @click="createDialog=false">Cancel</v-btn><v-btn color="secondary" :loading="creating" @click="createAnchor">Create anchor</v-btn></v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="editDialog" max-width="760">
      <v-card title="Edit anchor">
        <dialog-close-button @close="editDialog = false" />
        <v-card-text class="form-grid">
          <v-text-field :model-value="anchor.anchorCode" label="Anchor code" variant="outlined" readonly hint="Assigned automatically when the anchor was created" persistent-hint />
          <v-text-field v-model="anchor.name" label="Anchor name" placeholder="e.g. Frontier Trust Bank" variant="outlined" required />
          <v-text-field v-model="anchor.authorisedName" label="Authorised contact" placeholder="e.g. Jane Mwangi" variant="outlined" />
          <v-text-field :model-value="anchor.authorisedEmail" label="Sign-in email" type="email" variant="outlined" readonly hint="Change from Settings while signed in as this anchor" persistent-hint />
          <v-text-field v-model="anchor.authorisedContact" label="Phone" placeholder="e.g. +254 700 000000" variant="outlined" />
          <v-autocomplete v-model="anchor.country" :items="COUNTRIES" label="Country" variant="outlined" />
          <v-text-field v-model="anchor.city" label="City" placeholder="e.g. Nairobi" variant="outlined" />
          <v-text-field v-model="anchor.address" label="Address" placeholder="e.g. Karen Road" variant="outlined" />
        </v-card-text>
        <v-card-actions><v-spacer/><v-btn variant="text" @click="editDialog=false">Cancel</v-btn><v-btn color="secondary" :loading="saving" @click="save">Save changes</v-btn></v-card-actions>
      </v-card>
    </v-dialog>

    <v-card border flat class="admin-card">
      <v-card-text>
        <v-text-field v-model="tableSearch" prepend-inner-icon="mdi-magnify" label="Search" clearable hide-details density="compact" style="max-width: 260px" />
      </v-card-text>
      <v-data-table :headers="headers" :items="anchors" :search="tableSearch" :loading="loading">
        <template #item.status="{ item }">
          <v-chip size="small" :color="item.status === 1 ? 'success' : 'error'" variant="tonal">{{ item.status === 1 ? 'Active' : 'Deleted' }}</v-chip>
        </template>
        <template #item.actions="{ item }">
          <v-btn icon="mdi-pencil" variant="text" size="small" :aria-label="`Edit ${item.name}`" @click="openEdit(item)" />
          <v-btn :icon="item.status === 1 ? 'mdi-delete' : 'mdi-restore'" variant="text" size="small" :color="item.status === 1 ? 'error' : 'secondary'" :aria-label="`${item.status === 1 ? 'Delete' : 'Restore'} ${item.name}`" @click="toggleStatus(item)" />
        </template>
      </v-data-table>
    </v-card>
  </div>
</template>

<style scoped>
.admin-page{width:100%}.admin-head{display:flex;justify-content:space-between;gap:24px;align-items:flex-start;margin-bottom:24px}.admin-head h1{font-size:2rem;letter-spacing:-.04em}.admin-head p{color:#64748b}.head-chips{display:flex;gap:8px;align-items:center;flex-wrap:wrap}.admin-card{border-radius:18px!important}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:4px 18px;padding:26px}.form-grid .wide{grid-column:1/-1}@media(max-width:700px){.form-grid{grid-template-columns:1fr}.form-grid .wide{grid-column:auto}.admin-head{display:block}.head-chips{margin-top:14px}}
</style>
