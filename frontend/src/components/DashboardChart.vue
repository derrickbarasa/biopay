<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { chartScale, dashboardCurrency, type ChartPoint } from '@/utils/dashboard'
const props = defineProps<{
  data: ChartPoint[]; secondaryData: ChartPoint[]
  seriesLabel: string; secondaryLabel: string
  color: string; secondaryColor: string
  money?: boolean; ariaLabel: string
}>()
const selected = ref<number | null>(null)
watch(() => [props.data, props.secondaryData], () => { selected.value = null })
const width = 640, height = 240
const left = 72, right = 12, top = 18, bottom = 32
const plotWidth = width - left - right, plotHeight = height - top - bottom
const scale = computed(() => chartScale(Math.max(0, ...props.data.map(p => p.value), ...props.secondaryData.map(p => p.value)), !props.money))
const ticks = computed(() => Array.from({ length: 5 }, (_, i) => i * scale.value.step))
const slot = computed(() => plotWidth / Math.max(props.data.length, 1))
const barWidth = computed(() => Math.min(22, slot.value * .34))
const rows = computed(() => props.data.map((point, i) => ({ ...point, secondary: props.secondaryData[i]?.value ?? 0 })))
const active = computed(() => selected.value === null ? null : rows.value[selected.value])
const empty = computed(() => rows.value.every(row => row.value === 0 && row.secondary === 0))
function y(value: number) { return top + plotHeight * (1 - value / scale.value.max) }
function exact(value: number) { return props.money ? dashboardCurrency(value) : value.toLocaleString() }
function tick(value: number) { return `${props.money ? 'USD ' : ''}${value.toLocaleString(undefined, { notation: 'compact', maximumFractionDigits: 2 })}` }
function showLabel(index: number) { return rows.value.length <= 12 || index === rows.value.length - 1 || index % Math.ceil(rows.value.length / 8) === 0 }
function description(row: typeof rows.value[number]) { return `${row.fullLabel ?? row.label}: ${props.seriesLabel} ${exact(row.value)}; ${props.secondaryLabel} ${exact(row.secondary)}` }
</script>

<template>
  <div class="dashboard-chart">
    <div class="plot-scroll" tabindex="0" :aria-label="ariaLabel">
      <svg :viewBox="`0 0 ${width} ${height}`" role="group" :aria-label="ariaLabel" class="chart-svg">
        <g v-for="value in ticks" :key="value">
          <line :x1="left" :x2="width - right" :y1="y(value)" :y2="y(value)" stroke="#e2e8f0" />
          <text :x="left - 8" :y="y(value) + 4" text-anchor="end" class="axis-label">{{ tick(value) }}</text>
        </g>
        <g v-for="(row, index) in rows" :key="row.fullLabel ?? row.label" tabindex="0" role="group"
          class="column-group" :aria-label="description(row)"
          @mouseenter="selected = index" @mouseleave="selected = null" @focus="selected = index" @blur="selected = null" @click="selected = index">
          <title>{{ description(row) }}</title>
          <rect class="hit-area" :x="left + slot * index" :y="top" :width="slot" :height="plotHeight" :fill="selected === index ? '#f1f5f9' : 'transparent'" />
          <rect :x="left + slot * (index + .5) - barWidth - 1" :y="y(row.value)" :width="barWidth" :height="plotHeight * row.value / scale.max" :fill="color" />
          <rect :x="left + slot * (index + .5) + 1" :y="y(row.secondary)" :width="barWidth" :height="plotHeight * row.secondary / scale.max" :fill="secondaryColor" />
          <text v-if="showLabel(index)" :x="left + slot * (index + .5)" :y="height - 10" text-anchor="middle" class="axis-label">{{ row.label }}</text>
        </g>
      </svg>
    </div>
    <p class="point-detail" aria-live="polite">{{ active ? description(active) : empty ? 'No activity in this period.' : 'Hover, tap or focus a date to see exact values.' }}</p>
    <details class="data-details">
      <summary>View all chart data</summary>
      <div class="data-scroll" tabindex="0" :aria-label="`${ariaLabel} data table`">
        <table>
          <caption>{{ ariaLabel }}</caption>
          <thead><tr><th scope="col">Date / hour</th><th scope="col">{{ seriesLabel }}</th><th scope="col">{{ secondaryLabel }}</th></tr></thead>
          <tbody><tr v-for="row in rows" :key="row.fullLabel ?? row.label"><th scope="row">{{ row.fullLabel ?? row.label }}</th><td>{{ exact(row.value) }}</td><td>{{ exact(row.secondary) }}</td></tr></tbody>
        </table>
      </div>
    </details>
  </div>
</template>

<style scoped>
.plot-scroll, .data-scroll { overflow: auto; scrollbar-width: thin; scrollbar-color: #94a3b8 transparent; }
.plot-scroll::-webkit-scrollbar, .data-scroll::-webkit-scrollbar { display: block; height: 8px; width: 8px; }
.plot-scroll::-webkit-scrollbar-thumb, .data-scroll::-webkit-scrollbar-thumb { background: #94a3b8; border-radius: 4px; }
.chart-svg { display: block; width: 100%; min-width: 460px; }
.axis-label { font-size: 11px; fill: #526178; font-variant-numeric: tabular-nums; }
.column-group { outline: none; cursor: pointer; }
.column-group:focus-visible .hit-area { stroke: #0f766e; stroke-width: 2; }
.point-detail { min-height: 2.8em; margin: 4px 0; color: #475569; font-size: .72rem; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
.data-details { border-top: 1px solid #e2e8f0; font-size: .72rem; }
summary { padding: 8px 0; color: #0f766e; cursor: pointer; font-weight: 600; }
summary:focus-visible, .plot-scroll:focus-visible, .data-scroll:focus-visible { outline: 2px solid #0f766e; outline-offset: -2px; }
.data-scroll { max-height: 260px; }
table { width: 100%; border-collapse: collapse; font-variant-numeric: tabular-nums; }
caption { text-align: left; padding: 6px; color: #475569; }
th, td { padding: 7px; text-align: right; border-bottom: 1px solid #e2e8f0; white-space: nowrap; }
th:first-child { text-align: left; }
</style>
