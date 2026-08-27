<script setup lang="ts">
import { computed,onMounted,reactive,ref } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
interface UserRow{id:number;email:string;username:string;firstName?:string;otherNames?:string;partnerCode?:string;anchorId?:number;userScope:string;roleId?:number;roleName?:string;status:number;createdAt:string;systemAdmin?:boolean}
interface Role{id:number;name:string;scope:string;anchorId?:number|null;builtIn?:boolean;systemRole?:boolean}
interface Org{organisationCode:string;name:string;anchorId?:number}
interface Anchor{id:number;name:string}
const auth=useAuthStore(),toast=useToast(),loading=ref(false),saving=ref(false),dialog=ref(false),editDialog=ref(false),editSaving=ref(false),editLoading=ref(false),search=ref(''),users=ref<UserRow[]>([]),roles=ref<Role[]>([]),orgs=ref<Org[]>([]),anchors=ref<Anchor[]>([])
const { confirmAction } = useConfirm()
const form=reactive({email:'',username:'',firstName:'',otherNames:'',userScope:'ORGANISATION',organisationCode:'',roleId:null as number|null,targetAnchorId:null as number|null})
const editForm=reactive({id:0,email:'',firstName:'',otherNames:'',roleId:null as number|null,userScope:'ORGANISATION'})
const headers=[{title:'User',key:'email'},{title:'Scope',key:'userScope'},{title:'Role',key:'roleName'},{title:'Status',key:'status'},{title:'',key:'actions',sortable:false}]
const availableRoles=computed(()=>roles.value.filter(r=>r.scope===form.userScope&&(r.builtIn||!auth.isSystemAdmin||r.anchorId===form.targetAnchorId)));
// Only an existing Super Admin can mint another one -- a tenantless, permission-bypass identity
// with no anchor/organisation, so it's offered here only for auth.isSystemAdmin, not auth.isAnchor.
const scopeOptions=computed(()=>auth.isSystemAdmin?['SYSTEM','ANCHOR','ORGANISATION']:['ANCHOR','ORGANISATION']);
const availableEditRoles=computed(()=>roles.value.filter(r=>r.scope===editForm.userScope));
const availableOrganisations=computed(()=>auth.isSystemAdmin?orgs.value.filter(o=>o.anchorId===form.targetAnchorId):orgs.value)
// GET_ORGANIZATIONS is safe for org-scoped callers too -- it just returns their own org --
// so this always fetches, letting the Scope column show a name instead of a raw partner code.
const orgNameByCode=computed(()=>new Map(orgs.value.map(o=>[o.organisationCode,o.name])));
function orgName(code?:string){return (code&&orgNameByCode.value.get(code))||code||'—'}
async function load(){loading.value=true;try{const [u,r,o]=await Promise.all([dispatch<{results:UserRow[]}>('GET_USERS'),dispatch<{results:Role[]}>('GET_ROLES'),dispatch<{results:Org[]}>('GET_ORGANIZATIONS')]);users.value=u.results??[];roles.value=r.results??[];orgs.value=o.results??[];if(auth.isSystemAdmin){const a=await dispatch<{results:Anchor[]}>('GET_ANCHORS');anchors.value=a.results??[]}}catch(e){toast.error(e instanceof Error?e.message:'Unable to load users')}finally{loading.value=false}}
async function selectTargetAnchor(){form.organisationCode='';form.roleId=null;if(!form.targetAnchorId){roles.value=[];return}try{const r=await dispatch<{results:Role[]}>('GET_ROLES',{targetAnchorId:form.targetAnchorId});roles.value=r.results??[]}catch(e){toast.error(e instanceof Error?e.message:'Unable to load roles for this anchor')}}
function openCreate(){Object.assign(form,{email:'',username:'',firstName:'',otherNames:'',userScope:'ORGANISATION',organisationCode:auth.user?.partnerCode??'',roleId:null,targetAnchorId:auth.isSystemAdmin?null:auth.user?.anchorId??null});dialog.value=true}
async function create(){
 if(!form.firstName.trim()||!/.+@.+\..+/.test(form.email)||!form.username.trim()||!form.roleId||(form.userScope==='ORGANISATION'&&!form.organisationCode)||(form.userScope!=='SYSTEM'&&auth.isSystemAdmin&&!form.targetAnchorId)){toast.error('Complete the anchor, first name, valid email, username, access scope, organisation and role');return}
 saving.value=true;try{await dispatch('CREATE_USER',{...form});toast.success('User created. A temporary password was emailed to them.');dialog.value=false;await load()}catch(e){toast.error(e instanceof Error?e.message:'Create failed')}finally{saving.value=false}
}
async function toggle(u:UserRow){const deactivating=u.status===1;if(!await confirmAction({title:`${deactivating?'Deactivate':'Activate'} user?`,message:deactivating?`${u.email} will no longer be able to sign in.`:`${u.email} will be able to sign in again.`,confirmLabel:deactivating?'Deactivate':'Activate',color:deactivating?'warning':'secondary'}))return;try{await dispatch('TOGGLE_USER_STATUS',{userId:u.id,status:deactivating?0:1});toast.success(deactivating?'User deactivated':'User activated');await load()}catch(e){toast.error(e instanceof Error?e.message:'Status update failed')}}
async function openEdit(u:UserRow){editDialog.value=true;editLoading.value=true;try{const roleRequest=auth.isSystemAdmin&&u.anchorId?dispatch<{results:Role[]}>('GET_ROLES',{targetAnchorId:u.anchorId}):Promise.resolve({results:roles.value});const [{results:r},roleResult]=await Promise.all([dispatch<{results:UserRow}>('GET_USER',{userId:u.id}),roleRequest]);roles.value=roleResult.results??[];Object.assign(editForm,{id:r.id,email:r.email,firstName:r.firstName??'',otherNames:r.otherNames??'',roleId:r.roleId??null,userScope:r.userScope})}catch(e){toast.error(e instanceof Error?e.message:'Unable to load user');editDialog.value=false}finally{editLoading.value=false}}
async function saveEdit(){editSaving.value=true;try{await dispatch('UPDATE_USER',{userId:editForm.id,firstName:editForm.firstName,otherNames:editForm.otherNames,roleId:editForm.roleId});toast.success('User updated');editDialog.value=false;await load()}catch(e){toast.error(e instanceof Error?e.message:'Update failed')}finally{editSaving.value=false}}
onMounted(load)
</script>
<template>
 <div class="admin-page">
  <header class="admin-head"><div><div class="title-row"><h1>Users</h1><v-chip size="small" variant="tonal" color="primary">{{ auth.isSystemAdmin?'System oversight':auth.isAnchor?'Anchor-wide access':'Organisation access' }}</v-chip></div><p>Create accounts inside the correct anchor and organization.</p></div><v-btn v-if="auth.can('ACCESS_USERS')" color="secondary" prepend-icon="mdi-account-plus-outline" @click="openCreate">Add user</v-btn></header>
  <v-card border flat class="admin-card"><div class="table-tools"><v-text-field v-model="search" prepend-inner-icon="mdi-magnify" label="Search users" hide-details density="compact" variant="outlined"/><span>{{ users.length }} accounts</span></div>
   <v-data-table :headers="headers" :items="users" :search="search" :loading="loading">
    <template #item.email="{item}"><div class="py-2"><strong>{{ item.firstName }} {{ item.otherNames }}</strong><div class="text-caption text-medium-emphasis">{{ item.email }}</div></div></template>
    <template #item.userScope="{item}"><v-chip size="small" variant="tonal" :color="item.systemAdmin?'warning':item.userScope==='ANCHOR'?'primary':'secondary'">{{ item.systemAdmin?'System-wide':item.userScope==='ANCHOR'?'Anchor-wide':orgName(item.partnerCode) }}</v-chip></template>
    <template #item.status="{item}"><v-chip size="small" :color="item.status===1?'success':'error'" variant="tonal">{{ item.status===1?'Active':'Inactive' }}</v-chip></template>
    <template #item.actions="{item}"><v-chip v-if="item.systemAdmin" size="small" color="warning" variant="tonal" class="mr-1">Super Admin</v-chip><v-btn v-if="auth.can('ACCESS_USERS')&&!item.systemAdmin" size="small" variant="text" icon="mdi-pencil-outline" aria-label="Edit user" class="mr-1" @click="openEdit(item)"/><v-btn v-if="auth.can('ACCESS_USERS')" size="small" variant="text" :color="item.status===1?'error':'success'" @click="toggle(item)">{{ item.status===1?'Deactivate':'Activate' }}</v-btn></template>
   </v-data-table>
  </v-card>
  <v-dialog v-model="dialog" max-width="660"><v-card class="pa-2"><dialog-close-button @close="dialog=false"/><v-card-title>Create dashboard user</v-card-title><v-card-subtitle>A temporary password is generated automatically and emailed to the user.</v-card-subtitle><v-card-text class="form-grid">
   <v-text-field v-model="form.firstName" label="First name" variant="outlined" required/><v-text-field v-model="form.otherNames" label="Other names" variant="outlined"/>
   <v-text-field v-model="form.email" label="Email" type="email" variant="outlined" required/><v-text-field v-model="form.username" label="Username" variant="outlined" required/>
   <v-select v-if="auth.isSystemAdmin" v-model="form.userScope" :items="scopeOptions" label="Access scope" variant="outlined"/>
   <v-select v-else-if="auth.isAnchor" v-model="form.userScope" :items="['ANCHOR','ORGANISATION']" label="Access scope" variant="outlined"/>
   <v-select v-if="auth.isSystemAdmin&&form.userScope!=='SYSTEM'" v-model="form.targetAnchorId" :items="anchors" item-title="name" item-value="id" label="Anchor" variant="outlined" @update:model-value="selectTargetAnchor"/>
   <v-select v-if="form.userScope==='ORGANISATION'&&auth.isAnchor" v-model="form.organisationCode" :items="availableOrganisations" item-title="name" item-value="organisationCode" label="Organisation" variant="outlined" :disabled="auth.isSystemAdmin&&!form.targetAnchorId"/>
   <p v-if="form.userScope==='SYSTEM'" class="text-caption text-medium-emphasis" style="grid-column:1/-1">A Super Admin has permanent, tenantless access to every anchor and organisation.</p>
   <v-select v-model="form.roleId" :items="availableRoles" item-title="name" item-value="id" label="Role" variant="outlined" required/>
  </v-card-text><v-card-actions><v-spacer/><v-btn variant="text" @click="dialog=false">Cancel</v-btn><v-btn color="secondary" :loading="saving" @click="create">Create user</v-btn></v-card-actions></v-card></v-dialog>
  <v-dialog v-model="editDialog" max-width="660"><v-card class="pa-2"><dialog-close-button @close="editDialog=false"/><v-card-title>View / edit user</v-card-title><v-card-text class="form-grid">
   <v-text-field :model-value="editForm.email" label="Email" variant="outlined" readonly/><v-select v-model="editForm.roleId" :items="availableEditRoles" item-title="name" item-value="id" label="Role" variant="outlined" :loading="editLoading"/>
   <v-text-field v-model="editForm.firstName" label="First name" variant="outlined"/><v-text-field v-model="editForm.otherNames" label="Other names" variant="outlined"/>
  </v-card-text><v-card-actions><v-spacer/><v-btn variant="text" @click="editDialog=false">Cancel</v-btn><v-btn v-if="auth.can('ACCESS_USERS')" color="secondary" :loading="editSaving" @click="saveEdit">Save changes</v-btn></v-card-actions></v-card></v-dialog>
 </div>
</template>
<style scoped>
.admin-page{width:100%}.admin-head{display:flex;justify-content:space-between;gap:20px;align-items:center;margin-bottom:24px}.admin-head h1{font-size:2rem;letter-spacing:-.04em}.admin-head p{color:#64748b}.title-row{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.admin-card{border-radius:18px!important;overflow:hidden}.table-tools{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:18px 20px;border-bottom:1px solid #e2e8f0}.table-tools .v-input{max-width:380px}.table-tools span{font-size:.8rem;color:#64748b}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:2px 16px}@media(max-width:700px){.form-grid{grid-template-columns:1fr}.admin-head{align-items:flex-start;flex-direction:column}.table-tools{align-items:stretch;flex-direction:column}}
</style>
