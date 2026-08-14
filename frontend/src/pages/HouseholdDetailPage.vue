<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'

// Dedicated, full-page household view (replaces the old popup dialog). Renders
// every field the GET_HOUSEHOLD / GET_ALTERNATES endpoints currently return.
// Fields not yet exposed by the backend (marital status, ID number, dependants,
// captured images, payment/audit history) are tracked in progress.md as backend
// follow-ups and intentionally not faked here.

interface Alternate {
  alternateNumber?: string
  alternateName?: string
  relationship?: string
  phoneNumber?: string
}

const route = useRoute()
const router = useRouter()
const toast = useToast()

const loading = ref(true)
const detail = ref<Record<string, any> | null>(null)
const alternates = ref<Alternate[]>([])

const householdNumber = computed(() => String(route.params.householdNumber ?? ''))

const genderLabel = (g?: string) => (g === 'M' ? 'Male' : g === 'F' ? 'Female' : (g || '—'))

// Only the fields the backend actually returns today (see summary() in Household.java).
const infoFields = computed(() => {
  const d = detail.value
  if (!d) return []
  return [
    { label: 'Household number', value: d.householdNumber },
    { label: 'Head of household', value: d.householdName },
    { label: 'Organization', value: d.organisationCode },
    { label: 'Age', value: d.age ?? '—' },
    { label: 'Gender', value: genderLabel(d.gender) },
    { label: 'Phone number', value: d.phoneNumber || '—' },
    { label: 'Household size', value: d.householdSize ?? '—' },
    { label: 'State', value: d.stateCode || '—' },
    { label: 'County', value: d.countyCode || '—' },
    { label: 'Location', value: d.payamCode || '—' },
    { label: 'Village', value: d.bomaCode || '—' },
    { label: 'Coordinates', value: d.latitude && d.longitude ? `${d.latitude}, ${d.longitude}` : '—' },
    { label: 'Registered', value: d.createdAt || '—' },
    { label: 'Last updated', value: d.updatedAt || '—' },
  ]
})

async function load() {
  loading.value = true
  try {
    const [h, alts] = await Promise.all([
      dispatch<{ results: any[] }>('GET_HOUSEHOLD', { householdNumber: householdNumber.value }),
      dispatch<{ results: Alternate[] }>('GET_ALTERNATES', { householdNumber: householdNumber.value }),
    ])
    detail.value = h.results?.[0] ?? null
    alternates.value = alts.results ?? []
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to load household')
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push({ name: 'households' })
}

onMounted(load)
</script>

<template>
  <div>
    <div class="d-flex align-center mb-4 ga-3">
      <v-btn icon="mdi-arrow-left" variant="text" @click="goBack" />
      <div>
        <h1 class="text-h5 font-weight-bold mb-0">
          {{ detail?.householdName ?? 'Household' }}
        </h1>
        <div class="text-body-2 text-medium-emphasis">{{ householdNumber }}</div>
      </div>
      <v-spacer />
      <v-chip
        v-if="detail"
        :color="detail.status === 1 ? 'success' : 'error'"
        variant="tonal"
      >
        {{ detail.status === 1 ? 'Active' : 'Inactive' }}
      </v-chip>
    </div>

    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <v-alert
      v-else-if="!detail"
      type="warning"
      variant="tonal"
      text="Household not found or not in your organisation."
    />

    <v-row v-else>
      <v-col cols="12" md="8">
        <v-card variant="flat" border class="mb-4">
          <v-card-title class="text-subtitle-1 font-weight-bold">Household details</v-card-title>
          <v-divider />
          <v-card-text>
            <v-row dense>
              <v-col v-for="f in infoFields" :key="f.label" cols="12" sm="6">
                <div class="text-caption text-medium-emphasis">{{ f.label }}</div>
                <div class="text-body-1">{{ f.value }}</div>
              </v-col>
            </v-row>
          </v-card-text>
        </v-card>

        <v-card variant="flat" border>
          <v-card-title class="text-subtitle-1 font-weight-bold">
            Alternates ({{ alternates.length }})
          </v-card-title>
          <v-divider />
          <v-list v-if="alternates.length">
            <v-list-item
              v-for="a in alternates"
              :key="a.alternateNumber ?? a.alternateName"
              :title="a.alternateName"
              :subtitle="[a.relationship, a.phoneNumber].filter(Boolean).join(' · ') || undefined"
              prepend-icon="mdi-account-child-outline"
            />
          </v-list>
          <v-card-text v-else class="text-medium-emphasis">
            No alternates registered for this household.
          </v-card-text>
        </v-card>
      </v-col>

      <v-col cols="12" md="4">
        <v-card variant="flat" border class="mb-4">
          <v-card-title class="text-subtitle-1 font-weight-bold">Biometrics</v-card-title>
          <v-divider />
          <v-card-text class="d-flex flex-column ga-3">
            <div class="d-flex align-center justify-space-between">
              <span>Fingerprints</span>
              <v-chip size="small" :color="detail.fingerprintStatus === 'ENROLLED' ? 'success' : 'warning'" variant="tonal">
                {{ detail.fingerprintStatus ?? 'PENDING' }}
              </v-chip>
            </div>
            <div class="d-flex align-center justify-space-between">
              <span>Photo</span>
              <v-chip size="small" :color="detail.imageStatus === 'UPLOADED' ? 'success' : 'warning'" variant="tonal">
                {{ detail.imageStatus ?? 'PENDING' }}
              </v-chip>
            </div>
            <v-alert type="info" variant="tonal" density="compact" class="mt-1">
              Fingerprints and photos are captured through the BioPay Android field app.
            </v-alert>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </div>
</template>
