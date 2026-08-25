<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'
import { COUNTRIES } from '@/types/user'
import { capitalFor } from '@/utils/countries'

interface Anchor { id:number; anchorCode:string; name:string; authorisedName?:string; authorisedEmail?:string; authorisedContact?:string; address?:string; country?:string; city?:string; website?:string; status:number }
const toast=useToast(); const loading=ref(false); const saving=ref(false); const creating=ref(false); const createDialog=ref(false); const anchors=ref<Anchor[]>([]); const selectedId=ref<number|null>(null); const anchor=reactive<Anchor>({id:0,anchorCode:'',name:'',status:1})
const newAnchor=reactive({name:'',authorisedName:'',authorisedEmail:'',authorisedContact:'',country:'',city:'',address:'',website:''})
function selectAnchor(id:number|null){const selected=anchors.value.find(item=>item.id===id);if(selected)Object.assign(anchor,selected)}
async function load(preferredId?:number){loading.value=true;try{const r=await dispatch<{results:Anchor[]}>('GET_ANCHORS');anchors.value=r.results??[];selectedId.value=preferredId&&anchors.value.some(item=>item.id===preferredId)?preferredId:(anchors.value[0]?.id??null);selectAnchor(selectedId.value)}catch(e){toast.error(e instanceof Error?e.message:'Unable to load anchors')}finally{loading.value=false}}
async function save(){if(!anchor.name.trim()){toast.error('Anchor name is required');return}saving.value=true;try{await dispatch('UPDATE_ANCHOR',{...anchor,targetAnchorId:anchor.id});toast.success('Anchor details updated');await load(anchor.id)}catch(e){toast.error(e instanceof Error?e.message:'Update failed')}finally{saving.value=false}}
function openCreate(){Object.assign(newAnchor,{name:'',authorisedName:'',authorisedEmail:'',authorisedContact:'',country:'',city:'',address:'',website:''});createDialog.value=true}
async function createAnchor(){if(!newAnchor.name.trim()||!newAnchor.authorisedName.trim()||!/.+@.+\..+/.test(newAnchor.authorisedEmail)){toast.error('Complete the anchor name, administrator name and a valid email');return}creating.value=true;try{const result=await dispatch<{results:{anchorId:number}}>('CREATE_ANCHOR',newAnchor);toast.success('Anchor and administrator created');createDialog.value=false;await load(result.results?.anchorId)}catch(e){toast.error(e instanceof Error?e.message:'Unable to create anchor')}finally{creating.value=false}}
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
      <div><h1>Anchors</h1><p>Create and maintain the anchors that operate programmes in BioPay.</p></div>
      <div class="head-chips"><v-chip size="small" variant="tonal" color="primary">{{ anchors.length }} anchor{{ anchors.length === 1 ? '' : 's' }}</v-chip><v-btn color="secondary" prepend-icon="mdi-bank-plus" @click="openCreate">New anchor</v-btn></div>
    </header>
    <v-dialog v-model="createDialog" max-width="760">
      <v-card title="Create anchor" subtitle="This also creates the anchor administrator, assigns the next anchor code (ANC001, ANC002, ...) automatically, and emails a temporary password.">
        <dialog-close-button @close="createDialog = false" />
        <v-card-text class="form-grid">
          <v-text-field v-model="newAnchor.name" label="Anchor name" variant="outlined" required />
          <v-text-field v-model="newAnchor.authorisedName" label="Administrator name" variant="outlined" required />
          <v-text-field v-model="newAnchor.authorisedEmail" label="Administrator email" type="email" variant="outlined" required />
          <v-text-field v-model="newAnchor.authorisedContact" label="Phone" variant="outlined" />
          <v-text-field v-model="newAnchor.website" label="Website" variant="outlined" />
          <v-autocomplete v-model="newAnchor.country" :items="COUNTRIES" label="Country" variant="outlined" />
          <v-text-field v-model="newAnchor.city" label="City" variant="outlined" />
          <v-text-field v-model="newAnchor.address" label="Address" variant="outlined" class="wide" />
        </v-card-text>
        <v-card-actions><v-spacer/><v-btn variant="text" @click="createDialog=false">Cancel</v-btn><v-btn color="secondary" :loading="creating" @click="createAnchor">Create anchor</v-btn></v-card-actions>
      </v-card>
    </v-dialog>
    <v-skeleton-loader v-if="loading" type="article, actions" />
    <template v-else>
      <v-select
        v-model="selectedId" :items="anchors" item-title="name" item-value="id" label="Anchor"
        variant="outlined" class="anchor-picker" prepend-inner-icon="mdi-bank-outline" @update:model-value="selectAnchor"
      />
    <v-card border flat class="admin-card">
      <div class="scope-band"><v-icon icon="mdi-vector-link" />Changes here apply across the anchor network.</div>
      <v-card-text class="form-grid">
        <v-text-field :model-value="anchor.anchorCode" label="Anchor code" variant="outlined" readonly hint="Assigned automatically when the anchor was created" persistent-hint />
        <v-text-field v-model="anchor.name" label="Anchor name" variant="outlined" required />
        <v-text-field v-model="anchor.website" label="Website" variant="outlined" />
        <v-text-field v-model="anchor.authorisedName" label="Authorised contact" variant="outlined" />
        <v-text-field :model-value="anchor.authorisedEmail" label="Sign-in email" type="email" variant="outlined" readonly hint="Change from Settings while signed in as this anchor" persistent-hint />
        <v-text-field v-model="anchor.authorisedContact" label="Phone" variant="outlined" />
        <v-autocomplete v-model="anchor.country" :items="COUNTRIES" label="Country" variant="outlined" />
        <v-text-field v-model="anchor.city" label="City" variant="outlined" />
        <v-text-field v-model="anchor.address" label="Address" placeholder="e.g. Karen Road" variant="outlined" />
      </v-card-text>
      <v-card-actions class="px-6 pb-6"><v-spacer/><v-btn color="secondary" :loading="saving" @click="save">Save changes</v-btn></v-card-actions>
    </v-card>
    </template>
  </div>
</template>

<style scoped>
.admin-page{width:100%}.admin-head{display:flex;justify-content:space-between;gap:24px;align-items:flex-start;margin-bottom:24px}.admin-head h1{font-size:2rem;letter-spacing:-.04em}.admin-head p{color:#64748b}.head-chips{display:flex;gap:8px;align-items:center;flex-wrap:wrap}.anchor-picker{max-width:420px}.admin-card{border-radius:18px!important}.scope-band{display:flex;gap:10px;align-items:center;padding:13px 22px;background:#ecfdf5;color:#115e59;font-size:.85rem;font-weight:650;border-bottom:1px solid #ccfbf1}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:4px 18px;padding:26px}.form-grid .wide{grid-column:1/-1}@media(max-width:700px){.form-grid{grid-template-columns:1fr}.form-grid .wide{grid-column:auto}.admin-head{display:block}.head-chips{margin-top:14px}.anchor-picker{max-width:none}}
</style>
