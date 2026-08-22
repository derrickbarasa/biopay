<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'
import { COUNTRIES } from '@/types/user'
import { capitalFor } from '@/utils/countries'

interface Anchor { id:number; anchorCode:string; name:string; authorisedName?:string; authorisedEmail?:string; authorisedContact?:string; address?:string; country?:string; city?:string; website?:string; status:number }
const toast=useToast(); const loading=ref(false); const saving=ref(false); const anchor=reactive<Anchor>({id:0,anchorCode:'',name:'',status:1})
async function load(){loading.value=true;try{const r=await dispatch<{results:Anchor[]}>('GET_ANCHORS');Object.assign(anchor,r.results?.[0]??{})}catch(e){toast.error(e instanceof Error?e.message:'Unable to load anchor')}finally{loading.value=false}}
async function save(){saving.value=true;try{await dispatch('UPDATE_ANCHOR',{...anchor});toast.success('Anchor details updated')}catch(e){toast.error(e instanceof Error?e.message:'Update failed')}finally{saving.value=false}}
// Auto-fills the capital when a country is picked; still a plain editable field afterwards.
watch(() => anchor.country, (country, previous) => {
  if (country && country !== previous) anchor.city = capitalFor(country) || anchor.city
})
onMounted(load)
</script>

<template>
  <div class="admin-page">
    <header class="admin-head">
      <div><h1>Anchor profile</h1><p>Identity and contact details shared by every organisation in this programme.</p></div>
      <div class="head-chips"><v-chip size="small" variant="tonal" color="primary">Anchor-wide settings</v-chip><v-chip color="primary" variant="tonal" prepend-icon="mdi-bank-outline">{{ anchor.anchorCode || 'Loading' }}</v-chip></div>
    </header>
    <v-skeleton-loader v-if="loading" type="article, actions" />
    <v-card v-else border flat class="admin-card">
      <div class="scope-band"><v-icon icon="mdi-vector-link" />Changes here apply across the anchor network.</div>
      <v-card-text class="form-grid">
        <v-text-field v-model="anchor.name" label="Anchor name" variant="outlined" />
        <v-text-field v-model="anchor.website" label="Website" variant="outlined" />
        <v-text-field v-model="anchor.authorisedName" label="Authorised contact" variant="outlined" />
        <v-text-field v-model="anchor.authorisedEmail" label="Contact email" type="email" variant="outlined" />
        <v-text-field v-model="anchor.authorisedContact" label="Phone" variant="outlined" />
        <v-autocomplete v-model="anchor.country" :items="COUNTRIES" label="Country" variant="outlined" />
        <v-text-field v-model="anchor.city" label="City" variant="outlined" />
        <v-text-field v-model="anchor.address" label="Address" placeholder="e.g. Karen Road" variant="outlined" />
      </v-card-text>
      <v-card-actions class="px-6 pb-6"><v-spacer/><v-btn color="secondary" :loading="saving" @click="save">Save changes</v-btn></v-card-actions>
    </v-card>
  </div>
</template>

<style scoped>
.admin-page{width:100%}.admin-head{display:flex;justify-content:space-between;gap:24px;align-items:flex-start;margin-bottom:24px}.admin-head h1{font-size:2rem;letter-spacing:-.04em}.admin-head p{color:#64748b}.head-chips{display:flex;gap:8px;align-items:center;flex-wrap:wrap}.admin-card{border-radius:18px!important}.scope-band{display:flex;gap:10px;align-items:center;padding:13px 22px;background:#ecfdf5;color:#115e59;font-size:.85rem;font-weight:650;border-bottom:1px solid #ccfbf1}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:4px 18px;padding:26px}@media(max-width:700px){.form-grid{grid-template-columns:1fr}.admin-head{display:block}.admin-head .v-chip{margin-top:14px}}
</style>
