<script setup lang="ts">
import { computed,onMounted,reactive,ref } from 'vue'
import { useRouter } from 'vue-router'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
interface UserRow{id:number;email:string;username:string;firstName?:string;surname?:string;partnerCode?:string;anchorId?:number;anchorName?:string;userScope:string;roleId?:number;roleName?:string;status:number;createdAt:string;systemAdmin?:boolean;locked?:boolean}
interface Role{id:number;name:string;scope:string;anchorId?:number|null;builtIn?:boolean;systemRole?:boolean}
interface Org{organisationCode:string;name:string;anchorId?:number;status?:number}
interface Anchor{id:number;name:string}
const auth=useAuthStore(),toast=useToast(),router=useRouter(),loading=ref(false),saving=ref(false),dialog=ref(false),editDialog=ref(false),editSaving=ref(false),editLoading=ref(false),search=ref(''),roleFilter=ref<string|null>(null),users=ref<UserRow[]>([]),roles=ref<Role[]>([]),orgs=ref<Org[]>([]),anchors=ref<Anchor[]>([])
const roleFilterOptions=computed(()=>[...new Set(users.value.map(u=>u.roleName).filter((n):n is string=>!!n))].sort())
const filteredUsers=computed(()=>roleFilter.value?users.value.filter(u=>u.roleName===roleFilter.value):users.value)
const { confirmAction } = useConfirm()
const form=reactive({email:'',username:'',firstName:'',surname:'',userScope:null as string|null,organisationCode:'',roleId:null as number|null,targetAnchorId:null as number|null})
const editForm=reactive({id:0,email:'',firstName:'',surname:'',roleId:null as number|null,userScope:'ORGANISATION',locked:false})
const resettingPassword=ref(false),unblocking=ref(false)
const headers=[{title:'User',key:'email'},{title:'Scope',key:'userScope'},{title:'Role',key:'roleName'},{title:'Status',key:'status'},{title:'Actions',key:'actions',sortable:false,align:'start' as const}]
const availableRoles=computed(()=>roles.value.filter(r=>r.scope===form.userScope&&(r.builtIn||!auth.isSystemAdmin||r.anchorId===form.targetAnchorId)));
// Only an existing Super Admin can mint another SYSTEM-scope (tenantless) user.
const tenantScopeOptions=[{title:'Anchor-wide user',value:'ANCHOR'},{title:'Organisation user',value:'ORGANISATION'}]
const scopeOptions=computed(()=>auth.isSystemAdmin?[{title:'System-wide platform owner',value:'SYSTEM'},...tenantScopeOptions]:tenantScopeOptions);
const availableEditRoles=computed(()=>roles.value.filter(r=>r.scope===editForm.userScope));
// A deactivated organisation can't be picked for a new user -- same "no new work under
// something disabled" rule as the anchor/organisation pickers elsewhere (useAnchorScope,
// useOrgCascade). orgs.value itself stays unfiltered so orgName() can still resolve names
// for existing users who belong to an org that's since been deactivated.
const availableOrganisations=computed(()=>orgs.value.filter(o=>o.status!==0&&(!auth.isSystemAdmin||o.anchorId===form.targetAnchorId)))
const orgNameByCode=computed(()=>new Map(orgs.value.map(o=>[o.organisationCode,o.name])));
function orgName(code?:string){return (code&&orgNameByCode.value.get(code))||code||'—'}
// A NULL anchorName with a non-null anchorId means the join in Administration#getUsers found no
// matching row in `anchors` -- a dangling anchor_id (e.g. left over from an anchor that no longer
// exists at that id). Surfacing it as "Unknown anchor" instead of the generic "Anchor-wide" label
// makes that otherwise-invisible data problem visible right on this list.
function anchorLabel(item:UserRow){return item.anchorName ?? (item.anchorId!=null?`Unknown anchor (#${item.anchorId})`:'Anchor-wide')}
async function load(){loading.value=true;try{const [u,r,o]=await Promise.all([dispatch<{results:UserRow[]}>('GET_USERS'),dispatch<{results:Role[]}>('GET_ROLES'),dispatch<{results:Org[]}>('GET_ORGANIZATIONS')]);users.value=u.results??[];roles.value=r.results??[];orgs.value=o.results??[];if(auth.isSystemAdmin){const a=await dispatch<{results:Anchor[]}>('GET_ANCHORS',{status:1});anchors.value=a.results??[]}}catch(e){toast.error(e instanceof Error?e.message:'Unable to load users')}finally{loading.value=false}}
async function selectTargetAnchor(){form.organisationCode='';form.roleId=null;if(!form.targetAnchorId){roles.value=[];return}try{const r=await dispatch<{results:Role[]}>('GET_ROLES',{targetAnchorId:form.targetAnchorId});roles.value=r.results??[]}catch(e){toast.error(e instanceof Error?e.message:'Unable to load roles for this anchor')}}
function openCreate(){
 // Org admins have no scope picker at all (they can only ever create organisation users, see
 // the template below and Administration#createUser's own enforcement of that) so it's fixed
 // for them -- everyone who does get a picker (system/anchor admin) starts with it unset, so
 // they have to actively choose rather than accept a silent default.
 const fixedScope = auth.isSystemAdmin||auth.isAnchor ? null : 'ORGANISATION'
 Object.assign(form,{email:'',username:'',firstName:'',surname:'',userScope:fixedScope,organisationCode:auth.user?.partnerCode??'',roleId:null,targetAnchorId:auth.isSystemAdmin?null:auth.user?.anchorId??null});dialog.value=true
}
async function create(){
 if(!form.userScope||!form.firstName.trim()||!/.+@.+\..+/.test(form.email)||!form.username.trim()||!form.roleId||(form.userScope==='ORGANISATION'&&!form.organisationCode)||(form.userScope!=='SYSTEM'&&auth.isSystemAdmin&&!form.targetAnchorId)){toast.error('Complete the access scope, anchor, first name, valid email, username, organisation and role');return}
 saving.value=true;try{await dispatch('CREATE_USER',{...form});toast.success('User created. A temporary password was emailed to them.');dialog.value=false;await load()}catch(e){toast.error(e instanceof Error?e.message:'Create failed')}finally{saving.value=false}
}
async function toggle(u:UserRow){const deactivating=u.status===1;if(!await confirmAction({title:`${deactivating?'Deactivate':'Activate'} user?`,message:deactivating?`${u.email} will no longer be able to sign in.`:`${u.email} will be able to sign in again.`,confirmLabel:deactivating?'Deactivate':'Activate',color:deactivating?'error':'success'}))return;try{await dispatch('TOGGLE_USER_STATUS',{userId:u.id,status:deactivating?0:1});toast.success(deactivating?'User deactivated':'User activated');await load()}catch(e){toast.error(e instanceof Error?e.message:'Status update failed')}}
async function openEdit(u:UserRow){editDialog.value=true;editLoading.value=true;try{const roleRequest=auth.isSystemAdmin&&u.anchorId?dispatch<{results:Role[]}>('GET_ROLES',{targetAnchorId:u.anchorId}):Promise.resolve({results:roles.value});const [{results:r},roleResult]=await Promise.all([dispatch<{results:UserRow}>('GET_USER',{userId:u.id}),roleRequest]);roles.value=roleResult.results??[];Object.assign(editForm,{id:r.id,email:r.email,firstName:r.firstName??'',surname:r.surname??'',roleId:r.roleId??null,userScope:r.userScope,locked:!!r.locked})}catch(e){toast.error(e instanceof Error?e.message:'Unable to load user');editDialog.value=false}finally{editLoading.value=false}}
async function saveEdit(){editSaving.value=true;try{await dispatch('UPDATE_USER',{userId:editForm.id,firstName:editForm.firstName,surname:editForm.surname,roleId:editForm.roleId});toast.success('User updated');editDialog.value=false;await load()}catch(e){toast.error(e instanceof Error?e.message:'Update failed')}finally{editSaving.value=false}}
async function unblock(u:{id:number;email:string}){if(!await confirmAction({title:'Unblock user?',message:`${u.email} was locked out after too many failed sign-in attempts. They will be able to try signing in again.`,confirmLabel:'Unblock',color:'success'}))return;unblocking.value=true;try{await dispatch('UNBLOCK_USER',{userId:u.id});toast.success('User unblocked');editForm.locked=false;await load()}catch(e){toast.error(e instanceof Error?e.message:'Unblock failed')}finally{unblocking.value=false}}
async function resetPassword(u:{id:number;email:string}){if(!await confirmAction({title:'Reset password?',message:`${u.email} will receive a temporary password by email and must change it on their next sign-in.`,confirmLabel:'Send temporary password',color:'warning'}))return;resettingPassword.value=true;try{await dispatch('RESET_USER_PASSWORD',{userId:u.id});toast.success('A temporary password was emailed to the user')}catch(e){toast.error(e instanceof Error?e.message:'Password reset failed')}finally{resettingPassword.value=false}}
function openHistory(user:UserRow){router.push({name:'activity-history',params:{actorKind:'USER',actorId:user.id},query:{name:user.firstName||user.email||'User'}})}
onMounted(load)
</script>
<template>
 <div class="admin-page">
  <header class="admin-head"><div><div class="title-row"><h1 class="page-title">Users</h1><v-chip size="small" variant="tonal" color="primary">{{ auth.isSystemAdmin?'System oversight':auth.isAnchor?'Anchor-wide access':'Organisation access' }}</v-chip></div></div><v-btn v-if="auth.can('ACCESS_USERS')" color="secondary" prepend-icon="mdi-account-plus-outline" @click="openCreate">Add user</v-btn></header>
  <v-card border flat class="admin-card"><div class="table-tools"><v-text-field v-model="search" prepend-inner-icon="mdi-magnify" label="Search users" hide-details density="compact" variant="outlined"/><v-select v-model="roleFilter" :items="roleFilterOptions" label="Filter by role" clearable hide-details density="compact" variant="outlined" class="role-filter"/><span>{{ filteredUsers.length }} accounts</span></div>
   <v-data-table :headers="headers" :items="filteredUsers" :search="search" :loading="loading">
    <template #item.email="{item}"><div class="py-2"><strong>{{ item.firstName }} {{ item.surname }}</strong><div class="text-caption text-medium-emphasis">{{ item.email }}</div></div></template>
    <template #item.userScope="{item}"><v-chip size="small" variant="tonal" :color="item.systemAdmin?'warning':item.userScope==='ANCHOR'?(item.anchorId!=null&&!item.anchorName?'error':'primary'):'secondary'">{{ item.systemAdmin?'System-wide':item.userScope==='ANCHOR'?anchorLabel(item):orgName(item.partnerCode) }}</v-chip></template>
    <template #item.status="{item}"><v-chip v-if="item.status===1&&item.locked" size="small" color="warning" variant="tonal">Blocked</v-chip><v-chip v-else size="small" :color="item.status===1?'success':'error'" variant="tonal">{{ item.status===1?'Active':'Inactive' }}</v-chip></template>
    <template #item.actions="{item}"><v-btn v-if="auth.can('ACCESS_USERS')" size="small" variant="text" icon="mdi-history" :aria-label="`View ${item.email} activity history`" @click="openHistory(item)"/><v-btn v-if="auth.can('ACCESS_USERS')" size="small" variant="text" icon="mdi-pencil-outline" aria-label="Edit user" class="mr-1" @click="openEdit(item)"/><v-btn v-if="auth.isSystemAdmin&&item.locked" size="small" variant="text" icon="mdi-lock-open-variant-outline" color="warning" class="mr-1" :aria-label="`Unblock ${item.email}`" @click="unblock(item)"/><v-btn v-if="auth.can('ACCESS_USERS')" size="small" variant="text" :icon="item.status===1?'mdi-account-cancel-outline':'mdi-account-check-outline'" :color="item.status===1?'error':'success'" :aria-label="`${item.status===1?'Deactivate':'Activate'} ${item.email}`" @click="toggle(item)"/></template>
   </v-data-table>
  </v-card>
  <v-dialog v-model="dialog" max-width="660"><v-card class="pa-2"><dialog-close-button @close="dialog=false"/><v-card-title>Create dashboard user</v-card-title><v-card-subtitle>A temporary password is generated automatically and emailed to the user.</v-card-subtitle><v-card-text class="form-grid">
   <v-select v-if="auth.isSystemAdmin" v-model="form.userScope" :items="scopeOptions" label="Access scope" placeholder="Choose an access scope" variant="outlined"/>
   <v-select v-else-if="auth.isAnchor" v-model="form.userScope" :items="tenantScopeOptions" label="Access scope" placeholder="Choose an access scope" variant="outlined"/>
   <v-select v-if="auth.isSystemAdmin&&form.userScope!=='SYSTEM'" v-model="form.targetAnchorId" :items="anchors" item-title="name" item-value="id" label="Anchor" variant="outlined" placeholder="Choose an anchor" @update:model-value="selectTargetAnchor"/>
   <v-select v-if="form.userScope==='ORGANISATION'&&auth.isAnchor" v-model="form.organisationCode" :items="availableOrganisations" item-title="name" item-value="organisationCode" label="Organisation" variant="outlined" placeholder="Choose an organisation" :disabled="auth.isSystemAdmin&&!form.targetAnchorId"/>
   <p v-if="form.userScope==='SYSTEM'" class="text-caption text-medium-emphasis" style="grid-column:1/-1">A Platform Owner has permanent, tenantless access to every anchor and organisation.</p>
   <p v-else-if="form.userScope==='ANCHOR'" class="text-caption text-medium-emphasis" style="grid-column:1/-1">This creates a user with access across the selected anchor. To create a new anchor, use the Anchors page.</p>
   <v-text-field v-model="form.firstName" label="First name" placeholder="e.g. Jane" variant="outlined" required/><v-text-field v-model="form.surname" label="Surname" placeholder="e.g. Mwangi" variant="outlined"/>
   <v-text-field v-model="form.email" label="Email" type="email" placeholder="e.g. jane.mwangi@example.org" variant="outlined" required/><v-text-field v-model="form.username" label="Username" placeholder="e.g. jane.mwangi" variant="outlined" required/>
   <v-select v-model="form.roleId" :items="availableRoles" item-title="name" item-value="id" label="Role" variant="outlined" required/>
  </v-card-text><v-card-actions><v-spacer/><v-btn variant="flat" color="error" @click="dialog=false">Cancel</v-btn><v-btn variant="flat" color="secondary" :loading="saving" @click="create">Create user</v-btn></v-card-actions></v-card></v-dialog>
  <v-dialog v-model="editDialog" max-width="660"><v-card class="pa-2"><dialog-close-button @close="editDialog=false"/><v-card-title>View / edit user</v-card-title>
   <v-alert v-if="editForm.locked" type="warning" variant="tonal" density="compact" class="mx-4 mb-2">
    This account is blocked after too many failed sign-in attempts.
    <template v-if="auth.isSystemAdmin" #append><v-btn size="small" variant="flat" color="warning" :loading="unblocking" @click="unblock(editForm)">Unblock</v-btn></template>
   </v-alert>
   <v-card-text class="form-grid">
   <v-text-field :model-value="editForm.email" label="Email" variant="outlined" readonly/><v-select v-model="editForm.roleId" :items="availableEditRoles" item-title="name" item-value="id" label="Role" variant="outlined" :loading="editLoading"/>
   <v-text-field v-model="editForm.firstName" label="First name" variant="outlined"/><v-text-field v-model="editForm.surname" label="Surname" variant="outlined"/>
  </v-card-text><v-card-actions><v-btn v-if="auth.isSystemAdmin" variant="text" color="warning" prepend-icon="mdi-email-lock-outline" :loading="resettingPassword" @click="resetPassword(editForm)">Reset password</v-btn><v-spacer/><v-btn variant="flat" color="error" @click="editDialog=false">Cancel</v-btn><v-btn v-if="auth.can('ACCESS_USERS')" variant="flat" color="secondary" :loading="editSaving" @click="saveEdit">Save changes</v-btn></v-card-actions></v-card></v-dialog>
 </div>
</template>
<style scoped>
.admin-page{width:100%}.admin-head{display:flex;justify-content:space-between;gap:20px;align-items:center;margin-bottom:24px}.admin-head h1{font-size:2rem;letter-spacing:-.04em}.admin-head p{color:#64748b}.title-row{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.admin-card{border-radius:18px!important;overflow:hidden}.table-tools{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:18px 20px;border-bottom:1px solid #e2e8f0}.table-tools .v-input{max-width:380px}.role-filter{max-width:220px}.table-tools span{font-size:.8rem;color:#64748b}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:2px 16px}@media(max-width:700px){.form-grid{grid-template-columns:1fr}.admin-head{align-items:flex-start;flex-direction:column}.table-tools{align-items:stretch;flex-direction:column}}
</style>
