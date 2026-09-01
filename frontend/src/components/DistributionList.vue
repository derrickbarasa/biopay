<script setup lang="ts">
import { computed } from 'vue'

interface DistributionItem { label: string; value: number }

const props = defineProps<{
  data: DistributionItem[]
  color?: string
  ariaLabel?: string
  preserveOrder?: boolean
  totalValue?: number
}>()

const total = computed(() => props.totalValue ?? props.data.reduce((sum, item) => sum + item.value, 0))
const rows = computed(() => {
  const mapped = props.data.map((item) => ({
    ...item,
    displayLabel: item.label === 'Unspecified' ? 'Not recorded' : item.label,
    percent: total.value ? Math.round((item.value / total.value) * 100) : 0,
  }))
  return props.preserveOrder ? mapped : mapped.sort((a, b) => b.value - a.value)
})
</script>

<template>
  <div class="distribution" role="img" :aria-label="ariaLabel ?? 'Distribution'">
    <div v-for="row in rows" :key="row.label" class="distribution-row">
      <div class="distribution-copy">
        <span>{{ row.displayLabel }}</span>
        <strong>{{ row.value.toLocaleString() }} &middot; {{ row.percent }}%</strong>
      </div>
      <div class="distribution-track" aria-hidden="true">
        <span :style="{ width: `${row.percent}%`, minWidth: row.value ? '2px' : '0', background: color ?? '#0D9488' }" />
      </div>
    </div>
    <div v-if="!rows.length" class="distribution-empty">No household data</div>
  </div>
</template>

<style scoped>
.distribution { width: 100%; display: grid; gap: 14px; }
.distribution-row { display: grid; gap: 6px; }
.distribution-copy { display: flex; align-items: baseline; justify-content: space-between; gap: 14px; color: #475569; font-size: .875rem; line-height: 1.3; }
.distribution-copy span { min-width: 0; overflow-wrap: anywhere; }
.distribution-copy strong { flex-shrink: 0; color: #334155; font-size: .8125rem; font-weight: 700; font-variant-numeric: tabular-nums; }
.distribution-track { height: 8px; overflow: hidden; border-radius: 4px; background: #e8eef2; }
.distribution-track span { display: block; height: 100%; border-radius: inherit; }
.distribution-empty { color: #64748b; font-size: .875rem; }
</style>
