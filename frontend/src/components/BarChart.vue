<script setup lang="ts">
import { computed, ref, useId } from 'vue'

interface Bar { label: string; value: number }
const props = defineProps<{ data: Bar[]; color?: string; valuePrefix?: string }>()

const width = 640
const height = 240
const padding = { top: 14, right: 14, bottom: 30, left: 44 }
const hoverIndex = ref<number | null>(null)
const gradientId = `bar-fill-${useId()}`

const fill = computed(() => props.color ?? '#0D9488')

function niceStep(max: number) {
  if (max <= 0) return 1
  const rough = max / 4
  const mag = 10 ** Math.floor(Math.log10(rough))
  const norm = rough / mag
  const step = norm < 1.5 ? 1 : norm < 3 ? 2 : norm < 7 ? 5 : 10
  return step * mag
}

const step = computed(() => niceStep(Math.max(...props.data.map((d) => d.value), 1)))
const niceMax = computed(() => Math.max(step.value * 4, step.value))
const ticks = computed(() => [0, 1, 2, 3, 4].map((i) => i * step.value).filter((v) => v <= niceMax.value + 0.001))

function formatTick(v: number) {
  const abbreviated = Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 }).format(v)
  return `${props.valuePrefix ?? ''}${abbreviated}`
}

const innerW = width - padding.left - padding.right
const innerH = height - padding.top - padding.bottom

const bars = computed(() => {
  const n = props.data.length
  if (n === 0) return []
  const gap = Math.min(18, innerW / (n * 4))
  const barWidth = Math.max((innerW - gap * (n - 1)) / n, 6)
  return props.data.map((d, i) => {
    const barHeight = (d.value / niceMax.value) * innerH
    const x = padding.left + i * (barWidth + gap)
    const y = padding.top + innerH - barHeight
    const r = Math.min(6, barWidth / 2, Math.max(barHeight, 2) / 2)
    return { x, y, width: barWidth, height: Math.max(barHeight, 2), r, ...d }
  })
})

function barPath(b: { x: number; y: number; width: number; height: number; r: number }) {
  const { x, y, width: w, height: h, r } = b
  const bottom = y + h
  return `M ${x} ${bottom} V ${y + r} Q ${x} ${y} ${x + r} ${y} H ${x + w - r} Q ${x + w} ${y} ${x + w} ${y + r} V ${bottom} Z`
}
</script>

<template>
  <div class="chart-wrap">
    <svg :viewBox="`0 0 ${width} ${height}`" class="w-100" role="img" aria-label="Bar chart">
      <defs>
        <linearGradient :id="gradientId" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" :stop-color="fill" stop-opacity="1" />
          <stop offset="100%" :stop-color="fill" stop-opacity=".72" />
        </linearGradient>
      </defs>

      <g v-for="(t, i) in ticks" :key="i">
        <line
          :x1="padding.left" :x2="width - padding.right"
          :y1="padding.top + innerH - (t / niceMax) * innerH" :y2="padding.top + innerH - (t / niceMax) * innerH"
          stroke="#EEF2F6" stroke-width="1"
        />
        <text :x="padding.left - 10" :y="padding.top + innerH - (t / niceMax) * innerH + 3" text-anchor="end" class="tick-label">{{ formatTick(t) }}</text>
      </g>

      <g v-for="(b, i) in bars" :key="i" @mouseenter="hoverIndex = i" @mouseleave="hoverIndex = null">
        <rect :x="b.x - 3" :y="padding.top" :width="b.width + 6" :height="innerH" fill="transparent" />
        <path :d="barPath(b)" :fill="`url(#${gradientId})`" :fill-opacity="hoverIndex === i ? 1 : 0.9" />
        <text :x="b.x + b.width / 2" :y="height - 10" text-anchor="middle" class="axis-label">{{ b.label }}</text>
      </g>
    </svg>
    <div
      v-if="hoverIndex !== null"
      class="tooltip"
      :style="{ left: bars[hoverIndex].x + bars[hoverIndex].width / 2 + 'px', top: bars[hoverIndex].y + 'px' }"
    >
      <strong>{{ valuePrefix }}{{ bars[hoverIndex].value.toLocaleString() }}</strong>
      <div class="tooltip-label">{{ bars[hoverIndex].label }}</div>
    </div>
  </div>
</template>

<style scoped>
.chart-wrap { position: relative; }
.tick-label { font-size: 10px; fill: #94a3b8; font-variant-numeric: tabular-nums; }
.axis-label { font-size: 10px; fill: #64748b; }
.tooltip {
  position: absolute;
  transform: translate(-50%, -122%);
  background: #0f172a;
  color: #fff;
  padding: 6px 10px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.3;
  pointer-events: none;
  white-space: nowrap;
  box-shadow: 0 6px 16px rgba(15, 23, 42, .22);
}
.tooltip-label { color: rgba(255, 255, 255, .72); font-size: 11px; }
</style>
