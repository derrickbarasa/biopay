<script setup lang="ts">
import { computed } from 'vue'

interface OrganizationRow {
  organisationCode: string
  name: string
  authorisedName?: string
  authorisedEmail?: string
  country?: string
  verificationMethod?: string
  anchorName?: string
  anchorStatus?: number
  status: number
}

const props = withDefaults(defineProps<{
  items: OrganizationRow[]
  search?: string
  loading?: boolean
  showAnchor?: boolean
  canManage?: boolean
  noDataText?: string
}>(), {
  search: '',
  loading: false,
  showAnchor: false,
  canManage: false,
  noDataText: 'No organizations found.',
})

const emit = defineEmits<{
  edit: [organization: OrganizationRow]
  'toggle-status': [organization: OrganizationRow]
}>()

const headers = computed(() => [
  ...(props.showAnchor ? [{ title: 'Anchor', key: 'anchorName' }] : []),
  { title: 'Org Name', key: 'name' },
  { title: 'Contact', key: 'authorisedName' },
  { title: 'Email', key: 'authorisedEmail' },
  { title: 'Country', key: 'country' },
  { title: 'Verification', key: 'verificationMethod' },
  { title: 'Status', key: 'status' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'start' as const },
])

function verificationMethodIcon(method?: string) {
  if (method === 'FACIAL') return 'mdi-account-outline'
  if (method === 'BOTH') return 'mdi-account-multiple-check-outline'
  return 'mdi-fingerprint'
}

function verificationMethodLabel(method?: string) {
  if (method === 'FACIAL') return 'Facial'
  if (method === 'BOTH') return 'Both'
  return 'Fingerprint'
}

// An organisation is dependent on its anchor -- it can't be (re)activated while the anchor
// itself is deactivated (enforced server-side too, see Organization#toggleStatus). Deactivating
// an organisation is never blocked by this.
function cannotActivate(item: OrganizationRow) {
  return item.status !== 1 && item.anchorStatus === 0
}
</script>

<template>
  <v-data-table
    :headers="headers"
    :items="items"
    :search="search"
    :loading="loading"
    density="comfortable"
    class="detail-table"
  >
    <template #item.authorisedName="{ item }">{{ item.authorisedName || '—' }}</template>
    <template #item.authorisedEmail="{ item }">{{ item.authorisedEmail || '—' }}</template>
    <template #item.country="{ item }">{{ item.country || '—' }}</template>
    <template #item.verificationMethod="{ item }">
      <v-chip size="small" variant="tonal" color="primary" :prepend-icon="verificationMethodIcon(item.verificationMethod)">
        {{ verificationMethodLabel(item.verificationMethod) }}
      </v-chip>
    </template>
    <template #item.status="{ item }">
      <v-chip size="small" :color="item.status === 1 ? 'success' : 'error'" variant="tonal">
        {{ item.status === 1 ? 'Active' : 'Inactive' }}
      </v-chip>
    </template>
    <template #item.actions="{ item }">
      <v-btn
        :to="{ name: 'organization-detail', params: { organisationCode: item.organisationCode } }"
        icon="mdi-eye-outline"
        variant="text"
        size="small"
        :aria-label="`View ${item.name}`"
      />
      <v-btn v-if="canManage" icon="mdi-pencil" variant="text" size="small" :aria-label="`Edit ${item.name}`" @click="emit('edit', item)" />
      <v-tooltip v-if="canManage && cannotActivate(item)" text="This organisation's anchor is deactivated -- reactivate the anchor first" location="top">
        <template #activator="{ props: tip }">
          <span v-bind="tip">
            <v-btn icon="mdi-account-check-outline" variant="text" size="small" disabled :aria-label="`Activate ${item.name}`" />
          </span>
        </template>
      </v-tooltip>
      <v-btn
        v-else-if="canManage"
        :icon="item.status === 1 ? 'mdi-account-cancel-outline' : 'mdi-account-check-outline'"
        variant="text"
        size="small"
        :color="item.status === 1 ? 'error' : 'success'"
        :aria-label="`${item.status === 1 ? 'Deactivate' : 'Activate'} ${item.name}`"
        @click="emit('toggle-status', item)"
      />
    </template>
    <template #no-data>
      <div class="empty-state">{{ noDataText }}</div>
    </template>
  </v-data-table>
</template>
