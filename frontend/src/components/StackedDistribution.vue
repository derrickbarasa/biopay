<script setup lang="ts">
import { computed } from 'vue'

interface Segment { label: string; value: number; color?: string }

const props = defineProps<{
  data: Segment[]
  colors?: string[]
  ariaLabel?: string
}>()

const palette = ['#0F8F7A', '#65D38A', '#38BDF8', '#F59E0B', '#94A3B8']
const total = computed(() => props.data.reduce((sum, item) => sum + item.value, 0))
const rows = computed(() => props.data.map((item, index) => ({
  ...item,
  color: item.color ?? props.colors?.[index] ?? palette[index % palette.length],
  percent: total.value ? Math.round((item.value / total.value) * 100) : 0,
  width: total.value ? (item.value / total.value) * 100 : 0,
})))
</script>

<template>
  <div class="stacked-distribution" role="img" :aria-label="ariaLabel ?? 'Stacked distribution'">
    <div class="stacked-distribution__bar" aria-hidden="true">
      <span
        v-for="row in rows"
        :key="row.label"
        :style="{ width: `${row.width}%`, background: row.color }"
      >{{ row.percent >= 12 ? `${row.percent}%` : '' }}</span>
      <span v-if="!total" class="stacked-distribution__empty" />
    </div>
    <div class="stacked-distribution__legend">
      <div v-for="row in rows" :key="row.label" class="stacked-distribution__row">
        <span class="stacked-distribution__swatch" :style="{ background: row.color }" />
        <span class="stacked-distribution__label">{{ row.label }}</span>
        <strong>{{ row.value.toLocaleString() }}</strong>
      </div>
      <div v-if="!rows.length" class="stacked-distribution__no-data">No household data</div>
    </div>
  </div>
</template>

<style scoped>
.stacked-distribution { width: 100%; display: grid; gap: 12px; }
.stacked-distribution__bar {
  height: 34px;
  display: flex;
  overflow: hidden;
  border-radius: 7px;
  background: #e8eef2;
}
.stacked-distribution__bar > span {
  min-width: 0;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: .72rem;
  font-weight: 750;
  font-variant-numeric: tabular-nums;
}
.stacked-distribution__empty { width: 100% !important; background: #e8eef2 !important; }
.stacked-distribution__legend { display: grid; gap: 7px; }
.stacked-distribution__row {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  color: #475569;
  font-size: .76rem;
}
.stacked-distribution__swatch { width: 9px; height: 9px; border-radius: 50%; }
.stacked-distribution__label { min-width: 0; overflow-wrap: anywhere; }
.stacked-distribution__row strong { color: #172554; font-size: .76rem; font-variant-numeric: tabular-nums; }
.stacked-distribution__no-data { color: #64748b; font-size: .8rem; }
</style>
