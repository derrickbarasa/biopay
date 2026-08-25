<script setup lang="ts">
import { useConfirm } from '@/composables/useConfirm'

const { state, confirm, cancel } = useConfirm()
</script>

<template>
  <v-dialog :model-value="state.open" max-width="440" persistent>
    <v-card>
      <dialog-close-button @close="cancel" />
      <v-card-title>{{ state.title }}</v-card-title>
      <v-card-text class="text-body-2">{{ state.message }}</v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" @click="cancel">Cancel</v-btn>
        <v-btn :color="state.color" variant="flat" @click="confirm">{{ state.confirmLabel }}</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
