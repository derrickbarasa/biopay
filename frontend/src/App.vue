<script setup lang="ts">
import { useToast } from '@/composables/useToast'

const { state } = useToast()
</script>

<template>
  <v-app>
    <router-view />
    <v-snackbar v-model="state.show" :color="state.color" location="top right" timeout="4000">
      {{ state.message }}
    </v-snackbar>
  </v-app>
</template>
