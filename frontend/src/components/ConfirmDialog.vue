<script setup lang="ts">
import { computed } from 'vue'
import { useConfirm } from '@/composables/useConfirm'

const { state, confirm, cancel } = useConfirm()

const typedConfirmOk = computed(() => !state.requireTypedText || state.typedInput === state.requireTypedText)
</script>

<template>
  <v-dialog :model-value="state.open" max-width="440" persistent>
    <v-card>
      <dialog-close-button @close="cancel" />
      <v-card-title>{{ state.title }}</v-card-title>
      <v-card-text class="text-body-2">
        {{ state.message }}
        <template v-if="state.requireTypedText">
          <p class="mt-3 mb-1">Type <strong>{{ state.requireTypedText }}</strong> to confirm.</p>
          <v-text-field
            v-model="state.typedInput"
            :placeholder="state.requireTypedText"
            variant="outlined"
            density="compact"
            hide-details
            autofocus
            @keyup.enter="typedConfirmOk && confirm()"
          />
        </template>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="flat" color="error" @click="cancel">Cancel</v-btn>
        <v-btn :color="state.color" variant="flat" :disabled="!typedConfirmOk" @click="confirm">{{ state.confirmLabel }}</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
