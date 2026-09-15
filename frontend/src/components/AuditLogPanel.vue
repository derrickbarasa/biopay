<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'

interface AuditRow {
  id: number
  actorType: string
  actorId?: number
  actorName?: string
  actorEmail?: string
  actorRole?: string
  anchorName?: string
  organisationCode?: string
  organisationName?: string
  action: string
  entityType?: string
  entityId?: string
  details?: string
  channel?: string
  createdAt: string
}

const props = defineProps<{ actorId?: number; actorKind?: 'USER' | 'OFFICER'; compact?: boolean }>()
const toast = useToast()
const loading = ref(false)
const rows = ref<AuditRow[]>([])
const search = ref('')
const actionFilter = ref<string | null>(null)
const channelFilter = ref<string | null>(null)

const headers = computed(() => [
  ...(!props.actorId ? [{ title: 'Person', key: 'actorName' }] : []),
  { title: 'Activity', key: 'action' },
  { title: 'Role', key: 'actorRole' },
  { title: 'Scope', key: 'scope' },
  { title: 'Channel', key: 'channel' },
  { title: 'When', key: 'createdAt' },
])
const actions = computed(() => [...new Set(rows.value.map((row) => row.action))].sort())

function actionLabel(action: string) {
  return action.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function scopeLabel(row: AuditRow) {
  if (row.organisationName) return row.organisationName
  if (row.anchorName) return row.anchorName
  if (row.actorRole === 'Platform Owner' || row.actorRole === 'Admin User') return 'Platform-wide'
  return 'Unassigned'
}

function displayDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

function outcome(row: AuditRow) {
  try {
    const details = JSON.parse(row.details || '{}')
    if (details.outcome) return String(details.outcome)
    if (details.reason) return 'FAILED'
  } catch { /* legacy text detail */ }
  return row.action.includes('FAILED') || row.action.includes('DENIED') ? 'FAILED' : 'SUCCESS'
}

async function load() {
  loading.value = true
  try {
    const response = await dispatch<{ results: AuditRow[] }>('GET_AUDIT_LOGS', {
      auditActorId: props.actorId,
      actorKind: props.actorKind,
      action: actionFilter.value ?? undefined,
      auditChannel: channelFilter.value ?? undefined,
    })
    rows.value = response.results ?? []
  } catch (error) {
    toast.error(error instanceof Error ? error.message : 'Unable to load audit history')
  } finally {
    loading.value = false
  }
}

watch(() => [props.actorId, props.actorKind], load)
watch([actionFilter, channelFilter], load)
onMounted(load)
</script>

<template>
  <div class="audit-panel">
    <div class="audit-tools">
      <v-text-field v-model="search" prepend-inner-icon="mdi-magnify" label="Search activity" clearable hide-details density="compact" variant="outlined" />
      <v-select v-model="actionFilter" :items="actions" :item-title="actionLabel" label="Activity" clearable hide-details density="compact" variant="outlined" />
      <v-select v-model="channelFilter" :items="['PORTAL', 'API']" label="Channel" clearable hide-details density="compact" variant="outlined" />
      <v-btn icon="mdi-refresh" variant="text" aria-label="Refresh audit history" :loading="loading" @click="load" />
    </div>
    <v-data-table :headers="headers" :items="rows" :search="search" :loading="loading" :density="compact ? 'compact' : 'comfortable'">
      <template #item.actorName="{ item }">
        <div class="person-cell"><strong>{{ item.actorName || 'Unknown user' }}</strong><span>{{ item.actorEmail || item.actorType }}</span></div>
      </template>
      <template #item.action="{ item }">
        <div class="activity-cell"><strong>{{ actionLabel(item.action) }}</strong><span v-if="item.entityId">{{ item.entityType || 'Record' }}: {{ item.entityId }}</span></div>
      </template>
      <template #item.actorRole="{ item }">{{ item.actorRole || '—' }}</template>
      <template #item.scope="{ item }">{{ scopeLabel(item) }}</template>
      <template #item.channel="{ item }"><v-chip size="x-small" variant="tonal" color="primary">{{ item.channel || 'PORTAL' }}</v-chip></template>
      <template #item.createdAt="{ item }">
        <div class="date-cell"><span>{{ displayDate(item.createdAt) }}</span><v-chip size="x-small" variant="tonal" :color="outcome(item) === 'FAILED' ? 'error' : 'success'">{{ outcome(item) }}</v-chip></div>
      </template>
      <template #no-data><div class="text-center text-medium-emphasis py-8">No activity has been recorded for this scope yet.</div></template>
    </v-data-table>
  </div>
</template>

<style scoped>
.audit-panel { min-width: 0; }
.audit-tools { display: grid; grid-template-columns: minmax(220px, 1fr) 220px 140px auto; gap: 10px; align-items: center; padding: 14px 16px; border-bottom: 1px solid #e2e8f0; }
.person-cell, .activity-cell { display: grid; gap: 2px; padding-block: 6px; }
.person-cell strong, .activity-cell strong { color: #0f172a; font-size: .82rem; }
.person-cell span, .activity-cell span { color: #64748b; font-size: .7rem; }
.date-cell { display: flex; align-items: center; gap: 7px; white-space: nowrap; }
@media (max-width: 800px) { .audit-tools { grid-template-columns: 1fr 1fr; } }
@media (max-width: 520px) { .audit-tools { grid-template-columns: 1fr; } }
</style>
