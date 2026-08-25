<script setup lang="ts">
import { useToast } from '@/composables/useToast'
import { routeNavigating } from '@/composables/useRouteProgress'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const { state } = useToast()
</script>

<template>
  <v-app>
    <v-progress-linear
      v-if="routeNavigating"
      indeterminate
      color="primary"
      height="3"
      class="route-progress"
    />
    <router-view />
    <ConfirmDialog />
    <v-snackbar v-model="state.show" :color="state.color" location="top right" timeout="4000">
      {{ state.message }}
    </v-snackbar>
  </v-app>
</template>

<style scoped>
.route-progress {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 3000;
}
</style>
