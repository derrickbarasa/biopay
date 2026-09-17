<script setup lang="ts">
import { computed } from 'vue'

interface OfficerRow {
  id: number
  email: string
  firstName: string
  lastName: string
  organisationCode: string
  active: string
}

interface OrganizationOption {
  organisationCode: string
  name: string
}

const props = withDefaults(defineProps<{
  items: OfficerRow[]
  organizations?: OrganizationOption[]
  search?: string
  loading?: boolean
  canManage?: boolean
  canViewHistory?: boolean
  canAssignLocations?: boolean
  noDataText?: string
}>(), {
  organizations: () => [],
  search: '',
  loading: false,
  canManage: false,
  canViewHistory: false,
  canAssignLocations: false,
  noDataText: 'No field officers found.',
})

const emit = defineEmits<{
  history: [officer: OfficerRow]
  edit: [officer: OfficerRow]
  'toggle-status': [officer: OfficerRow, active: boolean]
  'assign-location': [officer: OfficerRow]
}>()

const headers = [
  { title: 'Name', key: 'name' },
  { title: 'Email', key: 'email' },
  { title: 'Organization', key: 'organisationName' },
  { title: 'Status', key: 'active' },
  { title: 'Location', key: 'location', sortable: false, align: 'start' as const },
  { title: 'Actions', key: 'actions', sortable: false, align: 'start' as const },
]

function isActive(value: string | undefined) {
  return value === '1' || value === 'true'
}

function organizationName(code?: string) {
  return props.organizations.find((organization) => organization.organisationCode === code)?.name || code || '—'
}

// v-data-table's built-in :search only matches against each header's own key on the
// item -- "Name" and "Organization" are rendered from a slot that combines/looks up other
// fields, so without a real name/organisationName property on the item, typing an officer's
// actual name or organisation into Search silently matched nothing (email/status still did).
const displayItems = computed(() => props.items.map((item) => ({
  ...item,
  name: [item.firstName, item.lastName].filter(Boolean).join(' ') || 'Name not set',
  organisationName: organizationName(item.organisationCode),
})))
</script>

<template>
  <v-data-table
    :headers="headers"
    :items="displayItems"
    :search="search"
    :loading="loading"
    density="comfortable"
    class="detail-table"
  >
    <template #item.active="{ item }">
      <v-chip size="small" :color="isActive(item.active) ? 'success' : 'error'" variant="tonal">
        {{ isActive(item.active) ? 'Active' : 'Inactive' }}
      </v-chip>
    </template>
    <template #item.location="{ item }">
      <v-btn
        v-if="canAssignLocations"
        variant="tonal"
        size="small"
        color="primary"
        prepend-icon="mdi-map-marker-outline"
        @click="emit('assign-location', item)"
      >
        Assign Location
      </v-btn>
      <span v-else>—</span>
    </template>
    <template #item.actions="{ item }">
      <v-btn
        v-if="canViewHistory"
        icon="mdi-history"
        variant="text"
        size="small"
        :aria-label="`View ${item.firstName || ''} ${item.lastName || ''} activity history`"
        @click="emit('history', item)"
      />
      <v-btn
        v-if="canManage"
        icon="mdi-pencil"
        variant="text"
        size="small"
        :aria-label="`Edit ${item.firstName || ''} ${item.lastName || ''}`"
        @click="emit('edit', item)"
      />
      <v-btn
        v-if="canManage"
        :icon="isActive(item.active) ? 'mdi-account-cancel-outline' : 'mdi-account-check-outline'"
        variant="text"
        size="small"
        :color="isActive(item.active) ? 'error' : 'success'"
        :aria-label="`${isActive(item.active) ? 'Deactivate' : 'Activate'} ${item.firstName || ''} ${item.lastName || ''}`"
        @click="emit('toggle-status', item, !isActive(item.active))"
      />
    </template>
    <template #no-data>
      <div class="empty-state">{{ noDataText }}</div>
    </template>
  </v-data-table>
</template>
