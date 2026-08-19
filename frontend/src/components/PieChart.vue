<script setup lang="ts">
import { computed, ref } from 'vue'

interface Slice { label: string; value: number }
const props = defineProps<{ data: Slice[]; colors?: string[] }>()

const size = 220
const radius = 90
const center = size / 2
const hoverIndex = ref<number | null>(null)

const palette = computed(() => props.colors ?? ['#0D9488', '#F59E0B', '#2196F3', '#0F766E', '#EA580C', '#94A3B8'])
const total = computed(() => props.data.reduce((sum, d) => sum + d.value, 0) || 1)

function polar(angle: number) {
  const rad = (angle - 90) * (Math.PI / 180)
  return { x: center + radius * Math.cos(rad), y: center + radius * Math.sin(rad) }
}

const slices = computed(() => {
  let startAngle = 0
  return props.data.map((d, i) => {
    const fraction = d.value / total.value
    const endAngle = startAngle + fraction * 360
    const start = polar(startAngle)
    const end = polar(endAngle)
    const largeArc = endAngle - startAngle > 180 ? 1 : 0
    const path = fraction >= 0.9999
      ? `M ${center} ${center - radius} A ${radius} ${radius} 0 1 1 ${center - 0.01} ${center - radius} Z`
      : `M ${center} ${center} L ${start.x} ${start.y} A ${radius} ${radius} 0 ${largeArc} 1 ${end.x} ${end.y} Z`
    const slice = { path, color: palette.value[i % palette.value.length], percent: Math.round(fraction * 100), ...d }
    startAngle = endAngle
    return slice
  })
})
</script>

<template>
  <div class="pie-wrap">
    <svg :viewBox="`0 0 ${size} ${size}`" class="pie-svg" role="img" aria-label="Pie chart">
      <circle v-if="total === 0" :cx="center" :cy="center" :r="radius" fill="#F1F5F9" />
      <path
        v-for="(s, i) in slices" :key="i" :d="s.path" :fill="s.color"
        :fill-opacity="hoverIndex === null || hoverIndex === i ? 1 : 0.45"
        stroke="#fff" stroke-width="2"
        @mouseenter="hoverIndex = i" @mouseleave="hoverIndex = null"
      />
    </svg>
    <div class="legend">
      <div v-for="(s, i) in slices" :key="i" class="legend-row" :class="{ dim: hoverIndex !== null && hoverIndex !== i }">
        <span class="swatch" :style="{ background: s.color }" />
        <span class="legend-label">{{ s.label }}</span>
        <span class="legend-value">{{ s.value.toLocaleString() }} ({{ s.percent }}%)</span>
      </div>
      <div v-if="!data.length" class="text-caption text-medium-emphasis">No data</div>
    </div>
  </div>
</template>

<style scoped>
.pie-wrap { display: flex; align-items: center; gap: 20px; flex-wrap: wrap; }
.pie-svg { width: 160px; height: 160px; flex-shrink: 0; }
.legend { display: flex; flex-direction: column; gap: 6px; font-size: 0.8rem; min-width: 140px; }
.legend-row { display: flex; align-items: center; gap: 8px; transition: opacity 150ms ease; }
.legend-row.dim { opacity: 0.4; }
.swatch { width: 10px; height: 10px; border-radius: 3px; flex-shrink: 0; }
.legend-label { flex: 1; }
.legend-value { color: rgba(0, 0, 0, 0.6); font-variant-numeric: tabular-nums; }
</style>
