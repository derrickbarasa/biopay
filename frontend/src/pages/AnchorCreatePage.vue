<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'
import { COUNTRIES } from '@/types/user'
import { capitalFor } from '@/utils/countries'

const router = useRouter()
const toast = useToast()
const creating = ref(false)

const newAnchor = reactive({
  name: '', authorisedFirstName: '', authorisedSurname: '', authorisedEmail: '',
  authorisedContact: '', country: '', city: '', address: '',
})

watch(() => newAnchor.country, (country, previous) => {
  if (country && country !== previous) newAnchor.city = capitalFor(country) || newAnchor.city
})

function goToList() {
  router.push({ name: 'anchors' })
}

async function createAnchor() {
  if (!newAnchor.name.trim() || !newAnchor.authorisedFirstName.trim() || !/.+@.+\..+/.test(newAnchor.authorisedEmail)) {
    toast.error('Complete the anchor name, administrator name and a valid email')
    return
  }
  creating.value = true
  try {
    await dispatch('CREATE_ANCHOR', newAnchor)
    toast.success('Anchor and administrator created')
    goToList()
  } catch (e) {
    toast.error(e instanceof Error ? e.message : 'Unable to create anchor')
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <div>
    <div class="d-flex align-center mb-4 ga-3">
      <v-btn icon="mdi-arrow-left" variant="text" aria-label="Back to anchors" @click="goToList" />
      <h1 class="page-title">New Anchor</h1>
    </div>

    <v-card variant="flat" border>
      <v-card-title>Create Anchor</v-card-title>
      <v-card-text class="form-grid">
        <v-text-field v-model="newAnchor.name" label="Name" placeholder="e.g. Frontier Trust Bank" variant="outlined" required />
        <v-text-field v-model="newAnchor.authorisedFirstName" label="Administrator first name" placeholder="e.g. Jane" variant="outlined" required />
        <v-text-field v-model="newAnchor.authorisedSurname" label="Administrator surname" placeholder="e.g. Mwangi" variant="outlined" />
        <v-text-field v-model="newAnchor.authorisedEmail" label="Administrator email" placeholder="e.g. jane@frontiertrust.bank" type="email" variant="outlined" required />
        <v-text-field v-model="newAnchor.authorisedContact" label="Phone" placeholder="e.g. +254 700 000000" variant="outlined" />
        <v-autocomplete v-model="newAnchor.country" :items="COUNTRIES" label="Country" variant="outlined" />
        <v-text-field v-model="newAnchor.city" label="City" placeholder="e.g. Nairobi" variant="outlined" />
        <v-text-field v-model="newAnchor.address" label="Address" placeholder="e.g. Karen Road" variant="outlined" />
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="flat" color="error" @click="goToList">Cancel</v-btn>
        <v-btn variant="flat" color="secondary" :loading="creating" @click="createAnchor">Create anchor</v-btn>
      </v-card-actions>
    </v-card>
  </div>
</template>

<style scoped>
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 18px; padding: 26px; }
@media (max-width: 700px) { .form-grid { grid-template-columns: 1fr; } }
</style>
