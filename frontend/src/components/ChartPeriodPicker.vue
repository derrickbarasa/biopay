<script setup lang="ts">
import { dateKey, type ChartPeriod } from '@/utils/dashboard'
defineProps<{ period: ChartPeriod; date: string; controlLabel: string }>()
const emit = defineEmits<{ 'update:period': [value: ChartPeriod]; 'update:date': [value: string] }>()
function changeDate(event: Event) {
  const value = (event.target as HTMLInputElement).value
  if (value) emit('update:date', value)
}
</script>
<template>
  <div class="calendar-control">
    <select :value="period" :aria-label="controlLabel + ' period'" @change="emit('update:period', ($event.target as HTMLSelectElement).value as ChartPeriod)">
      <option value="day">Day</option><option value="week">Week</option><option value="month">Month</option><option value="year">Year</option>
    </select>
    <input type="date" :value="date" :max="dateKey(new Date())" :aria-label="controlLabel" @change="changeDate" />
  </div>
</template>
<style scoped>
.calendar-control { display: inline-flex; flex-wrap: nowrap; align-items: center; gap: 4px; width: max-content; }
input, select { min-height: 28px; padding: 3px 5px; border: 1px solid #cbd5e1; border-radius: 6px; color: #334155; background: #fff; font: inherit; font-size: .72rem; font-variant-numeric: tabular-nums; cursor: pointer; }
select { width: 66px; flex: 0 0 66px; }
input { width: 122px; flex: 0 0 122px; }
input:focus-visible, select:focus-visible { outline: 2px solid #0d9488; outline-offset: 2px; }
</style>
