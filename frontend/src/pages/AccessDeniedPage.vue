<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="access-denied">
    <v-icon icon="mdi-shield-lock-outline" size="48" color="primary" />
    <h1>Access not assigned</h1>
    <p>Your role does not include permission for that area. Ask an anchor administrator to update your role if you need access.</p>
    <v-btn variant="outlined" prepend-icon="mdi-arrow-left" @click="router.back()">Go back</v-btn>
  </div>
</template>

<style scoped>
.access-denied { min-height: 55vh; display: flex; flex-direction: column; justify-content: center; align-items: center; text-align: center; gap: 14px; }
.access-denied h1 { color: #0f172a; font-size: 1.8rem; letter-spacing: -.03em; }
.access-denied p { color: #475569; max-width: 58ch; line-height: 1.6; }
</style>
