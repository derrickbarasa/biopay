<script setup lang="ts">
import { computed,onMounted,reactive,ref } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
interface UserRow{id:number;email:string;username:string;firstName?:string;otherNames?:string;partnerCode?:string;userScope:string;roleId?:number;roleName?:string;status:number;createdAt:string}
interface Role{id:number;name:string;scope:string}
interface Org{organisationCode:string;name:string}
const auth=useAuthStore(),toast=useToast(),loading=ref(false),saving=ref(false),dialog=ref(false),editDialog=ref(false),editSaving=ref(false),editLoading=ref(false),search=ref(''),users=ref<UserRow[]>([]),roles=ref<Role[]>([]),orgs=ref<Org[]>([])
const form=reactive({email:'',username:'',firstName:'',otherNames:'',userScope:'ORGANISATION',organisationCode:'',roleId:null as number|null})
const editForm=reactive({id:0,email:'',firstName:'',otherNames:'',roleId:null as number|null,userScope:'ORGANISATION'})
const headers=[{title:'User',key:'email'},{title:'Scope',key:'userScope'},{title:'Role',key:'roleName'},{title:'Status',key:'status'},{title:'',key:'actions',sortable:false}]
const availableRoles=computed(()=>roles.value.filter(r=>r.scope===form.userScope));
const availableEditRoles=computed(()=>roles.value.filter(r=>r.scope===editForm.userScope));
// GET_ORGANIZATIONS is safe for org-scoped callers too -- it just returns their own org --
// so this always fetches, letting the Scope column show a name instead of a raw partner code.
const orgNameByCode=computed(()=>new Map(orgs.value.map(o=>[o.organisationCode,o.name])));
function orgName(code?:string){return (code&&orgNameByCode.value.get(code))||code||'—'}
async function load(){loading.value=true;try{const [u,r,o]=await Promise.all([dispatch<{results:UserRow[]}>('GET_USERS'),dispatch<{results:Role[]}>('GET_ROLES'),dispatch<{results:Org[]}>('GET_ORGANIZATIONS')]);users.value=u.results??[];roles.value=r.results??[];orgs.value=o.results??[]}catch(e){toast.error(e instanceof Error?e.message:'Unable to load users')}finally{loading.value=false}}
function openCreate(){Object.assign(form,{email:'',username:'',firstName:'',otherNames:'',userScope:'ORGANISATION',organisationCode:auth.user?.partnerCode??'',roleId:null});dialog.value=true}
async function create(){saving.value=true;try{await dispatch('CREATE_USER',{...form});toast.success('User created. A temporary password was emailed to them.');dialog.value=false;await load()}catch(e){toast.error(e instanceof Error?e.message:'Create failed')}finally{saving.value=false}}
async function toggle(u:UserRow){try{await dispatch('TOGGLE_USER_STATUS',{userId:u.id,status:u.status===1?0:1});toast.success(u.status===1?'User deactivated':'User activated');await load()}catch(e){toast.error(e instanceof Error?e.message:'Status update failed')}}
async function openEdit(u:UserRow){editDialog.value=true;editLoading.value=true;try{const {results:r}=await dispatch<{results:UserRow}>('GET_USER',{userId:u.id});Object.assign(editForm,{id:r.id,email:r.email,firstName:r.firstName??'',otherNames:r.otherNames??'',roleId:r.roleId??null,userScope:r.userScope})}catch(e){toast.error(e instanceof Error?e.message:'Unable to load user');editDialog.value=false}finally{editLoading.value=false}}
async function saveEdit(){editSaving.value=true;try{await dispatch('UPDATE_USER',{userId:editForm.id,firstName:editForm.firstName,otherNames:editForm.otherNames,roleId:editForm.roleId});toast.success('User updated');editDialog.value=false;await load()}catch(e){toast.error(e instanceof Error?e.message:'Update failed')}finally{editSaving.value=false}}
onMounted(load)
</script>
<template>
 <div class="admin-page">
  <header class="admin-head"><div><div class="title-row"><h1>Users</h1><v-chip size="small" variant="tonal" color="primary">{{ auth.isAnchor?'Anchor-wide access':'Organisation access' }}</v-chip></div><p>Create accounts and control who can sign in.</p></div><v-btn color="secondary" prepend-icon="mdi-account-plus-outline" @click="openCreate">Add user</v-btn></header>
  <v-card border flat class="admin-card"><div class="table-tools"><v-text-field v-model="search" prepend-inner-icon="mdi-magnify" label="Search users" hide-details density="compact" variant="outlined"/><span>{{ users.length }} accounts</span></div>
   <v-data-table :headers="headers" :items="users" :search="search" :loading="loading">
    <template #item.email="{item}"><div class="py-2"><strong>{{ item.firstName }} {{ item.otherNames }}</strong><div class="text-caption text-medium-emphasis">{{ item.email }}</div></div></template>
    <template #item.userScope="{item}"><v-chip size="small" variant="tonal" :color="item.userScope==='ANCHOR'?'primary':'secondary'">{{ item.userScope==='ANCHOR'?'Anchor-wide':orgName(item.partnerCode) }}</v-chip></template>
    <template #item.status="{item}"><v-chip size="small" :color="item.status===1?'success':'error'" variant="tonal">{{ item.status===1?'Active':'Inactive' }}</v-chip></template>
    <template #item.actions="{item}"><v-btn size="small" variant="text" icon="mdi-pencil-outline" aria-label="View / edit user" class="mr-1" @click="openEdit(item)"/><v-btn size="small" variant="text" :color="item.status===1?'error':'success'" @click="toggle(item)">{{ item.status===1?'Deactivate':'Activate' }}</v-btn></template>
   </v-data-table>
  </v-card>
  <v-dialog v-model="dialog" max-width="660"><v-card class="pa-2"><v-card-title>Create dashboard user</v-card-title><v-card-subtitle>A temporary password is generated automatically and emailed to the user.</v-card-subtitle><v-card-text class="form-grid">
   <v-text-field v-model="form.firstName" label="First name" variant="outlined"/><v-text-field v-model="form.otherNames" label="Other names" variant="outlined"/>
   <v-text-field v-model="form.email" label="Email" type="email" variant="outlined"/><v-text-field v-model="form.username" label="Username" variant="outlined"/>
   <v-select v-if="auth.isAnchor" v-model="form.userScope" :items="['ANCHOR','ORGANISATION']" label="Access scope" variant="outlined"/>
   <v-select v-if="form.userScope==='ORGANISATION'&&auth.isAnchor" v-model="form.organisationCode" :items="orgs" item-title="name" item-value="organisationCode" label="Organisation" variant="outlined"/>
   <v-select v-model="form.roleId" :items="availableRoles" item-title="name" item-value="id" label="Role" variant="outlined"/>
  </v-card-text><v-card-actions><v-spacer/><v-btn variant="text" @click="dialog=false">Cancel</v-btn><v-btn color="secondary" :loading="saving" @click="create">Create user</v-btn></v-card-actions></v-card></v-dialog>
  <v-dialog v-model="editDialog" max-width="660"><v-card class="pa-2"><v-card-title>View / edit user</v-card-title><v-card-text class="form-grid">
   <v-text-field :model-value="editForm.email" label="Email" variant="outlined" readonly/><v-select v-model="editForm.roleId" :items="availableEditRoles" item-title="name" item-value="id" label="Role" variant="outlined" :loading="editLoading"/>
   <v-text-field v-model="editForm.firstName" label="First name" variant="outlined"/><v-text-field v-model="editForm.otherNames" label="Other names" variant="outlined"/>
  </v-card-text><v-card-actions><v-spacer/><v-btn variant="text" @click="editDialog=false">Cancel</v-btn><v-btn color="secondary" :loading="editSaving" @click="saveEdit">Save changes</v-btn></v-card-actions></v-card></v-dialog>
 </div>
</template>
<style scoped>
.admin-page{width:100%}.admin-head{display:flex;justify-content:space-between;gap:20px;align-items:center;margin-bottom:24px}.admin-head h1{font-size:2rem;letter-spacing:-.04em}.admin-head p{color:#64748b}.title-row{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.admin-card{border-radius:18px!important;overflow:hidden}.table-tools{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:18px 20px;border-bottom:1px solid #e2e8f0}.table-tools .v-input{max-width:380px}.table-tools span{font-size:.8rem;color:#64748b}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:2px 16px}@media(max-width:700px){.form-grid{grid-template-columns:1fr}.admin-head{align-items:flex-start;flex-direction:column}.table-tools{align-items:stretch;flex-direction:column}}
</style>
