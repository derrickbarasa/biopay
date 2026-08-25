<script setup lang="ts">
defineEmits<{ close: [] }>()
</script>

<template>
  <v-btn
    class="dialog-close-button"
    icon="mdi-close"
    variant="text"
    size="small"
    aria-label="Close dialog"
    @click="$emit('close')"
  />
</template>
