<script setup lang="ts">
type ChartPeriod = 'day' | 'month'

defineProps<{
  period: ChartPeriod
  date: string
  controlLabel: string
}>()

const emit = defineEmits<{
  'update:period': [value: ChartPeriod]
  'update:date': [value: string]
}>()

function showDay(event: Event) {
  const selectedDate = (event.target as HTMLInputElement).value
  if (!selectedDate) return
  emit('update:date', selectedDate)
  emit('update:period', 'day')
}

function showMonth() {
  emit('update:period', 'month')
}
</script>

<template>
  <div class="calendar-control">
    <input
      class="calendar-input"
      type="date"
      :value="date"
      :aria-label="controlLabel"
      @change="showDay"
    />
    <v-btn
      v-if="period === 'day'"
      icon="mdi-calendar-month-outline"
      variant="text"
      size="x-small"
      title="View full month"
      aria-label="View full month"
      @click="showMonth"
    />
  </div>
</template>

<style scoped>
.calendar-control { display: inline-flex; align-items: center; gap: 2px; }
.calendar-input {
  width: 128px;
  min-height: 32px;
  padding: 3px 5px;
  border: 0;
  border-bottom: 1px solid #cbd5e1;
  border-radius: 0;
  outline: 0;
  background: transparent;
  color: #334155;
  font: inherit;
  font-size: .72rem;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}
.calendar-input:hover { border-color: #94a3b8; }
.calendar-input:focus-visible { border-color: #0d9488; box-shadow: 0 2px 0 rgba(13, 148, 136, .2); }
@media (max-width: 420px) {
  .calendar-input { width: 118px; }
}
</style>
