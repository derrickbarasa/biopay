<script setup lang="ts">
import { computed, ref, useId } from 'vue'

interface Point { label: string; value: number }
const props = defineProps<{
  data: Point[]
  secondaryData?: Point[]
  color?: string
  secondaryColor?: string
  seriesLabel?: string
  secondaryLabel?: string
  valuePrefix?: string
  ariaLabel?: string
  showValues?: boolean
}>()

const width = 640
const height = 240
const padding = { top: 26, right: 16, bottom: 34, left: 44 }
const hoverIndex = ref<number | null>(null)
const gradientId = `line-fill-${useId()}`

const stroke = computed(() => props.color ?? '#0D9488')
const secondaryStroke = computed(() => props.secondaryColor ?? '#F59E0B')
const innerW = width - padding.left - padding.right
const innerH = height - padding.top - padding.bottom

function niceStep(max: number) {
  if (max <= 0) return 1
  const rough = max / 4
  const mag = 10 ** Math.floor(Math.log10(rough))
  const norm = rough / mag
  const step = norm < 1.5 ? 1 : norm < 3 ? 2 : norm < 7 ? 5 : 10
  return step * mag
}

const step = computed(() => niceStep(Math.max(
  ...props.data.map((d) => d.value),
  ...(props.secondaryData ?? []).map((d) => d.value),
  1,
)))
const niceMax = computed(() => Math.max(step.value * 4, step.value))
const ticks = computed(() => [0, 1, 2, 3, 4].map((i) => i * step.value).filter((v) => v <= niceMax.value + 0.001))

function formatTick(v: number) {
  const abbreviated = Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 }).format(v)
  return `${props.valuePrefix ?? ''}${abbreviated}`
}

const points = computed(() => {
  const n = props.data.length
  if (n === 0) return []
  return props.data.map((d, i) => {
    const x = padding.left + (n === 1 ? innerW / 2 : (i / (n - 1)) * innerW)
    const y = padding.top + innerH - (d.value / niceMax.value) * innerH
    return { x, y, ...d }
  })
})

const secondaryPoints = computed(() => {
  const data = props.secondaryData ?? []
  const n = data.length
  if (n === 0) return []
  return data.map((d, i) => {
    const x = padding.left + (n === 1 ? innerW / 2 : (i / (n - 1)) * innerW)
    const y = padding.top + innerH - (d.value / niceMax.value) * innerH
    return { x, y, ...d }
  })
})

// Smooth the polyline into a Catmull-Rom-derived cubic Bezier path so the
// trend reads as a curve rather than sharp mechanical joints.
function smoothPath(pts: { x: number; y: number }[]) {
  if (pts.length === 0) return ''
  if (pts.length === 1) return `M ${pts[0].x} ${pts[0].y}`
  let d = `M ${pts[0].x} ${pts[0].y}`
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i === 0 ? 0 : i - 1]
    const p1 = pts[i]
    const p2 = pts[i + 1]
    const p3 = pts[i + 2 < pts.length ? i + 2 : i + 1]
    const c1x = p1.x + (p2.x - p0.x) / 6
    const c1y = p1.y + (p2.y - p0.y) / 6
    const c2x = p2.x - (p3.x - p1.x) / 6
    const c2y = p2.y - (p3.y - p1.y) / 6
    d += ` C ${c1x} ${c1y} ${c2x} ${c2y} ${p2.x} ${p2.y}`
  }
  return d
}

const linePath = computed(() => smoothPath(points.value))
const secondaryLinePath = computed(() => smoothPath(secondaryPoints.value))

const areaPath = computed(() => {
  if (points.value.length === 0) return ''
  const last = points.value[points.value.length - 1]
  const first = points.value[0]
  const bottom = padding.top + innerH
  return `${linePath.value} L ${last.x} ${bottom} L ${first.x} ${bottom} Z`
})

function showAxisLabel(index: number) {
  const length = points.value.length
  if (length <= 8) return true
  const interval = Math.ceil((length - 1) / 7)
  return index === 0 || index === length - 1 || index % interval === 0
}
</script>

<template>
  <div class="chart-wrap">
    <svg
      :viewBox="`0 0 ${width} ${height}`"
      class="chart-svg"
      role="img"
      :aria-label="ariaLabel ?? 'Line chart'"
      preserveAspectRatio="xMidYMid meet"
    >
      <defs>
        <linearGradient :id="gradientId" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" :stop-color="stroke" stop-opacity=".22" />
          <stop offset="100%" :stop-color="stroke" stop-opacity="0" />
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

      <path v-if="!secondaryData?.length" :d="areaPath" :fill="`url(#${gradientId})`" stroke="none" />
      <path :d="linePath" fill="none" :stroke="stroke" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />
      <path v-if="secondaryPoints.length" :d="secondaryLinePath" fill="none" :stroke="secondaryStroke" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />

      <g
        v-for="(p, i) in points"
        :key="i"
        tabindex="0"
        class="chart-point"
        @mouseenter="hoverIndex = i"
        @mouseleave="hoverIndex = null"
        @focus="hoverIndex = i"
        @blur="hoverIndex = null"
      >
        <title>{{ p.label }}: {{ seriesLabel ?? 'Value' }} {{ valuePrefix }}{{ p.value.toLocaleString() }}<template v-if="secondaryPoints[i]">; {{ secondaryLabel ?? 'Secondary' }} {{ valuePrefix }}{{ secondaryPoints[i].value.toLocaleString() }}</template></title>
        <rect :x="p.x - (innerW / Math.max(points.length - 1, 1)) / 2" :y="padding.top" :width="innerW / Math.max(points.length, 1)" :height="innerH" fill="transparent" />
        <circle :cx="p.x" :cy="p.y" :r="hoverIndex === i ? 5 : 3.5" :fill="stroke" stroke="#fff" stroke-width="1.5" style="pointer-events: none;" />
        <circle v-if="secondaryPoints[i]" :cx="secondaryPoints[i].x" :cy="secondaryPoints[i].y" :r="hoverIndex === i ? 5 : 3.5" :fill="secondaryStroke" stroke="#fff" stroke-width="1.5" style="pointer-events: none;" />
        <text v-if="showValues" :x="p.x" :y="Math.max(p.y - 10, 14)" text-anchor="middle" class="value-label">{{ p.value.toLocaleString() }}</text>
        <text v-if="showAxisLabel(i)" :x="p.x" :y="height - 10" text-anchor="middle" class="axis-label">{{ p.label }}</text>
      </g>

      <line
        v-if="hoverIndex !== null"
        :x1="points[hoverIndex].x" :x2="points[hoverIndex].x"
        :y1="padding.top" :y2="padding.top + innerH"
        stroke="#CBD5E1" stroke-width="1" stroke-dasharray="3,3"
      />
    </svg>
    <div
      v-if="hoverIndex !== null"
      class="tooltip"
      :style="{
        left: `${(points[hoverIndex].x / width) * 100}%`,
        top: `${(points[hoverIndex].y / height) * 100}%`,
      }"
    >
      <div class="tooltip-label">{{ points[hoverIndex].label }}</div>
      <strong><i :style="{ background: stroke }" />{{ seriesLabel ?? 'Value' }}: {{ valuePrefix }}{{ points[hoverIndex].value.toLocaleString() }}</strong>
      <strong v-if="secondaryPoints[hoverIndex]"><i :style="{ background: secondaryStroke }" />{{ secondaryLabel ?? 'Secondary' }}: {{ valuePrefix }}{{ secondaryPoints[hoverIndex].value.toLocaleString() }}</strong>
    </div>
  </div>
</template>

<style scoped>
.chart-wrap { position: relative; width: 100%; }
.chart-svg { display: block; width: 100%; height: auto; overflow: visible; }
.tick-label { font-size: 10px; fill: #94a3b8; font-variant-numeric: tabular-nums; }
.axis-label { font-size: 10px; fill: #64748b; }
.value-label { font-size: 11px; font-weight: 700; fill: #334155; font-variant-numeric: tabular-nums; }
.chart-point { outline: none; }
.chart-point:focus circle { stroke: #0f172a; stroke-width: 2.5; }
.tooltip {
  position: absolute;
  transform: translate(-50%, -128%);
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
.tooltip strong { display: flex; align-items: center; gap: 6px; font-weight: 650; }
.tooltip i { display: inline-block; width: 7px; height: 7px; border-radius: 50%; }
</style>
