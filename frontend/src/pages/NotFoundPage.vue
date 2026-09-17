<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <main class="not-found-page">
    <section class="not-found-card" aria-labelledby="not-found-title">
      <img src="/biopay_logo_horizontal.svg" alt="BioPay" class="not-found-logo" />
      <p class="not-found-code">404</p>
      <h1 id="not-found-title">Page not found</h1>
      <p>The address may be incorrect, or the page may have moved.</p>
      <div class="not-found-actions">
        <v-btn color="primary" @click="router.push('/')">Go to homepage</v-btn>
        <v-btn variant="outlined" @click="router.back()">Go back</v-btn>
      </div>
    </section>
  </main>
</template>

<style scoped>
.not-found-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 2rem;
  background: #f4f7f6;
  color: #172320;
}

.not-found-card {
  width: min(100%, 34rem);
  padding: clamp(2rem, 6vw, 4rem);
  border: 1px solid #dce5e2;
  border-radius: 1rem;
  background: #fff;
  text-align: center;
  box-shadow: 0 1rem 3rem rgb(23 35 32 / 8%);
}

.not-found-logo {
  width: min(11rem, 55vw);
  height: auto;
  margin-bottom: 2rem;
}

.not-found-code {
  margin: 0;
  color: #0f766e;
  font-size: .82rem;
  font-weight: 800;
  letter-spacing: .18em;
}

h1 {
  margin: .35rem 0 .75rem;
  font-size: clamp(2rem, 7vw, 3rem);
}

.not-found-card > p:last-of-type {
  margin: 0 auto;
  color: #596863;
  line-height: 1.65;
}

.not-found-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: .75rem;
  margin-top: 2rem;
}
</style>
