<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AuditLogPanel from '@/components/AuditLogPanel.vue'

const route = useRoute()
const router = useRouter()

const actorId = computed(() => {
  const raw = Number(route.params.actorId)
  return Number.isFinite(raw) ? raw : undefined
})
const actorKind = computed<'USER' | 'OFFICER'>(() => (route.params.actorKind === 'OFFICER' ? 'OFFICER' : 'USER'))
const subjectName = computed(() => String(route.query.name ?? '') || (actorKind.value === 'OFFICER' ? 'Field officer' : 'User'))

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push({ name: actorKind.value === 'OFFICER' ? 'officers' : 'users' })
}
</script>

<template>
  <div>
    <div class="d-flex align-center mb-4 ga-3">
      <v-btn icon="mdi-arrow-left" variant="text" aria-label="Back" @click="goBack" />
      <div>
        <h1 class="page-title">{{ subjectName }} activity</h1>
        <p class="text-medium-emphasis mb-0">Successful and failed sign-ins and recorded system activity.</p>
      </div>
    </div>

    <v-card variant="flat" border>
      <AuditLogPanel :actor-id="actorId" :actor-kind="actorKind" />
    </v-card>
  </div>
</template>
