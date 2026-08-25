<script setup lang="ts">
import { computed, ref } from 'vue'

interface Slice { label: string; value: number }
const props = defineProps<{ data: Slice[]; colors?: string[] }>()

const size = 200
const radius = 84
const thickness = 26
const center = size / 2
const hoverIndex = ref<number | null>(null)

const palette = computed(() => props.colors ?? ['#0D9488', '#F59E0B', '#16A34A', '#0F766E', '#EA580C', '#94A3B8'])
const total = computed(() => props.data.reduce((sum, d) => sum + d.value, 0) || 0)

function polar(angle: number, r: number) {
  const rad = (angle - 90) * (Math.PI / 180)
  return { x: center + r * Math.cos(rad), y: center + r * Math.sin(rad) }
}

const slices = computed(() => {
  const denom = total.value || 1
  let startAngle = 0
  return props.data.map((d, i) => {
    const fraction = d.value / denom
    const endAngle = startAngle + fraction * 360
    const outerStart = polar(startAngle, radius)
    const outerEnd = polar(endAngle, radius)
    const innerStart = polar(endAngle, radius - thickness)
    const innerEnd = polar(startAngle, radius - thickness)
    const largeArc = endAngle - startAngle > 180 ? 1 : 0
    const path = fraction >= 0.9999
      ? `M ${center - radius} ${center} A ${radius} ${radius} 0 1 1 ${center + radius} ${center} A ${radius} ${radius} 0 1 1 ${center - radius} ${center}`
        + ` M ${center - (radius - thickness)} ${center} A ${radius - thickness} ${radius - thickness} 0 1 0 ${center + (radius - thickness)} ${center} A ${radius - thickness} ${radius - thickness} 0 1 0 ${center - (radius - thickness)} ${center} Z`
      : `M ${outerStart.x} ${outerStart.y} A ${radius} ${radius} 0 ${largeArc} 1 ${outerEnd.x} ${outerEnd.y} L ${innerStart.x} ${innerStart.y} A ${radius - thickness} ${radius - thickness} 0 ${largeArc} 0 ${innerEnd.x} ${innerEnd.y} Z`
    const slice = { path, color: palette.value[i % palette.value.length], percent: Math.round(fraction * 100), ...d }
    startAngle = endAngle
    return slice
  })
})
</script>

<template>
  <div class="pie-wrap">
    <div class="pie-svg-wrap">
      <svg :viewBox="`0 0 ${size} ${size}`" class="pie-svg" role="img" aria-label="Donut chart">
        <circle v-if="total === 0" :cx="center" :cy="center" :r="radius - thickness / 2" fill="none" stroke="#EEF2F6" :stroke-width="thickness" />
        <path
          v-for="(s, i) in slices" :key="i" :d="s.path" :fill="s.color"
          fill-rule="evenodd"
          :fill-opacity="hoverIndex === null || hoverIndex === i ? 1 : 0.35"
          @mouseenter="hoverIndex = i" @mouseleave="hoverIndex = null"
        />
      </svg>
      <div class="pie-center">
        <span class="pie-total">{{ total.toLocaleString() }}</span>
        <span class="pie-total-label">Total</span>
      </div>
    </div>
    <div class="legend">
      <div v-for="(s, i) in slices" :key="i" class="legend-row" :class="{ dim: hoverIndex !== null && hoverIndex !== i }" @mouseenter="hoverIndex = i" @mouseleave="hoverIndex = null">
        <span class="swatch" :style="{ background: s.color }" />
        <span class="legend-label">{{ s.label }}</span>
        <span class="legend-value">{{ s.value.toLocaleString() }} · {{ s.percent }}%</span>
      </div>
      <div v-if="!data.length" class="text-caption text-medium-emphasis">No data</div>
    </div>
  </div>
</template>

<style scoped>
.pie-wrap { display: flex; align-items: center; gap: 20px; flex-wrap: wrap; }
.pie-svg-wrap { position: relative; width: 148px; height: 148px; flex-shrink: 0; }
.pie-svg { width: 100%; height: 100%; }
.pie-svg path { transition: fill-opacity 150ms ease; cursor: default; }
.pie-center { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; pointer-events: none; }
.pie-total { font-size: 1.15rem; font-weight: 750; color: #0f172a; line-height: 1.1; }
.pie-total-label { font-size: .65rem; color: #94a3b8; font-weight: 600; text-transform: uppercase; letter-spacing: .04em; }
.legend { display: flex; flex-direction: column; gap: 6px; font-size: .78rem; min-width: 140px; }
.legend-row { display: flex; align-items: center; gap: 8px; transition: opacity 150ms ease; cursor: default; }
.legend-row.dim { opacity: 0.4; }
.swatch { width: 9px; height: 9px; border-radius: 3px; flex-shrink: 0; }
.legend-label { flex: 1; color: #334155; }
.legend-value { color: #64748b; font-variant-numeric: tabular-nums; font-size: .74rem; }
</style>
