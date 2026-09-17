<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { COUNTRIES } from '@/types/user'
import { capitalFor } from '@/utils/countries'

interface Anchor { id:number; anchorCode:string; name:string; authorisedName?:string; authorisedFirstName?:string; authorisedSurname?:string; authorisedEmail?:string; authorisedContact?:string; address?:string; country?:string; city?:string; status:number }

const toast = useToast()
const router = useRouter()
const { confirmAction } = useConfirm()
const loading = ref(false)
const saving = ref(false)
const editDialog = ref(false)
const anchors = ref<Anchor[]>([])
const tableSearch = ref('')
const anchor = reactive<Anchor>({ id: 0, anchorCode: '', name: '', status: 1 })

const headers = [
  { title: 'Anchor Code', key: 'anchorCode', minWidth: 136, nowrap: true },
  { title: 'Name', key: 'name' },
  { title: 'Administrator', key: 'authorisedName' },
  { title: 'Email', key: 'authorisedEmail' },
  { title: 'Country', key: 'country' },
  { title: 'Status', key: 'status' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'start' as const, width: 96, minWidth: 96, fixed: true, nowrap: true },
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
  router.push({ name: 'anchor-create' })
}

async function toggleStatus(item: Anchor) {
  const deactivating = item.status === 1
  if (!await confirmAction({
    title: deactivating ? 'Deactivate anchor?' : 'Activate anchor?',
    message: deactivating
      ? `${item.name} and its administrator will no longer be able to sign in. Every organization, field officer and dashboard user under it will also be deactivated -- the anchor holds the subscription, so nothing under it can keep operating. This can be undone, but reactivating only restores the anchor itself; anything deactivated by this action must be reactivated individually.`
      : `${item.name} will be reactivated and its administrator can sign in again. Organizations, field officers and users under it stay deactivated until reactivated individually.`,
    confirmLabel: deactivating ? 'Deactivate anchor' : 'Activate anchor',
    color: deactivating ? 'error' : 'success',
  })) return
  try {
    await dispatch('TOGGLE_ANCHOR_STATUS', { targetAnchorId: item.id, status: deactivating ? 0 : 1 })
    toast.success(deactivating ? 'Anchor deactivated' : 'Anchor activated')
    await load()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : 'Update failed')
  }
}

// Auto-fills the capital when a country is picked; still editable afterwards.
watch(() => anchor.country, (country, previous) => {
  if (country && country !== previous) anchor.city = capitalFor(country) || anchor.city
})
onMounted(load)
</script>

<template>
  <div class="admin-page">
    <header class="admin-head">
      <div><h1 class="page-title">Anchors</h1><p>Every anchor operating programmes in BioPay.</p></div>
      <div class="head-chips"><v-btn color="secondary" prepend-icon="mdi-bank-plus" @click="openCreate">New anchor</v-btn></div>
    </header>

    <v-dialog v-model="editDialog" max-width="760">
      <v-card title="Edit anchor">
        <dialog-close-button @close="editDialog = false" />
        <v-card-text class="form-grid">
          <v-text-field :model-value="anchor.anchorCode" label="Anchor code" variant="outlined" readonly hint="Assigned automatically when the anchor was created" persistent-hint />
          <v-text-field v-model="anchor.name" label="Name" placeholder="e.g. Frontier Trust Bank" variant="outlined" required />
          <v-text-field v-model="anchor.authorisedFirstName" label="Authorised contact first name" placeholder="e.g. Jane" variant="outlined" />
          <v-text-field v-model="anchor.authorisedSurname" label="Authorised contact surname" placeholder="e.g. Mwangi" variant="outlined" />
          <v-text-field :model-value="anchor.authorisedEmail" label="Sign-in email" type="email" variant="outlined" readonly hint="Change from Settings while signed in as this anchor" persistent-hint />
          <v-text-field v-model="anchor.authorisedContact" label="Phone" placeholder="e.g. +254 700 000000" variant="outlined" />
          <v-autocomplete v-model="anchor.country" :items="COUNTRIES" label="Country" variant="outlined" />
          <v-text-field v-model="anchor.city" label="City" placeholder="e.g. Nairobi" variant="outlined" />
          <v-text-field v-model="anchor.address" label="Address" placeholder="e.g. Karen Road" variant="outlined" />
        </v-card-text>
        <v-card-actions><v-spacer/><v-btn variant="flat" color="error" @click="editDialog=false">Cancel</v-btn><v-btn variant="flat" color="secondary" :loading="saving" @click="save">Save changes</v-btn></v-card-actions>
      </v-card>
    </v-dialog>

    <v-card border flat class="admin-card">
      <v-card-text>
        <v-text-field v-model="tableSearch" prepend-inner-icon="mdi-magnify" label="Search" clearable hide-details density="compact" style="max-width: 260px" />
      </v-card-text>
      <v-data-table :headers="headers" :items="anchors" :search="tableSearch" :loading="loading">
        <template #item.status="{ item }">
          <v-chip size="small" :color="item.status === 1 ? 'success' : 'error'" variant="tonal">{{ item.status === 1 ? 'Active' : 'Inactive' }}</v-chip>
        </template>
        <template #item.actions="{ item }">
          <v-btn :to="{ name: 'anchor-detail', params: { anchorId: item.id } }" icon="mdi-eye-outline" variant="text" size="small" :aria-label="`View ${item.name}`" />
          <v-btn icon="mdi-pencil" variant="text" size="small" :aria-label="`Edit ${item.name}`" @click="openEdit(item)" />
          <v-btn :icon="item.status === 1 ? 'mdi-account-cancel-outline' : 'mdi-account-check-outline'" variant="text" size="small" :color="item.status === 1 ? 'error' : 'success'" :aria-label="`${item.status === 1 ? 'Deactivate' : 'Activate'} ${item.name}`" @click="toggleStatus(item)" />
        </template>
      </v-data-table>
    </v-card>
  </div>
</template>

<style scoped>
.admin-page{width:100%}.admin-head{display:flex;justify-content:space-between;gap:24px;align-items:flex-start;margin-bottom:24px}.admin-head h1{font-size:2rem;letter-spacing:-.04em}.admin-head p{color:#64748b}.head-chips{display:flex;gap:8px;align-items:center;flex-wrap:wrap}.admin-card{border-radius:18px!important}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:4px 18px;padding:26px}.form-grid .wide{grid-column:1/-1}@media(max-width:700px){.form-grid{grid-template-columns:1fr}.form-grid .wide{grid-column:auto}.admin-head{display:block}.head-chips{margin-top:14px}}
</style>
