<script setup lang="ts">
import AuditLogPanel from './AuditLogPanel.vue'

defineProps<{ actorId?: number; actorKind?: 'USER' | 'OFFICER'; title?: string }>()
const open = defineModel<boolean>({ required: true })
</script>

<template>
  <v-dialog v-model="open" max-width="1180">
    <v-card>
      <dialog-close-button @close="open = false" />
      <v-card-title>{{ title || 'Activity history' }}</v-card-title>
      <v-card-subtitle>Successful and failed sign-ins and recorded system activity.</v-card-subtitle>
      <v-card-text class="pa-0 mt-3"><AuditLogPanel v-if="open" :actor-id="actorId" :actor-kind="actorKind" compact /></v-card-text>
      <v-card-actions><v-spacer /><v-btn variant="text" @click="open = false">Close</v-btn></v-card-actions>
    </v-card>
  </v-dialog>
</template>
