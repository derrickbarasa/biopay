<script setup lang="ts">
import { computed, ref } from 'vue'

interface Slice { label: string; value: number }
const props = withDefaults(defineProps<{
  data: Slice[]
  colors?: string[]
  variant?: 'donut' | 'pie'
  showLabels?: boolean
  showLegendPercent?: boolean
  /** SVG viewBox size in px; the drawn circle scales with it. Default fits the
   *  compact side-by-side layout used everywhere except a "centered" chart. */
  size?: number
  /** Uses the featured dashboard layout with a centered chart and a legend
   *  beside it, as used by DashboardPage.vue's Registration Trend card. */
  centered?: boolean
}>(), { variant: 'donut', showLegendPercent: true, size: 200, centered: false })

const size = computed(() => props.size)
const radius = computed(() => size.value * 0.42)
const thickness = computed(() => size.value * 0.13)
const center = computed(() => size.value / 2)
const hoverIndex = ref<number | null>(null)

const palette = computed(() => props.colors ?? ['#0D9488', '#F59E0B', '#16A34A', '#0F766E', '#EA580C', '#94A3B8'])
const total = computed(() => props.data.reduce((sum, d) => sum + d.value, 0) || 0)

function polar(cx: number, angle: number, r: number) {
  const rad = (angle - 90) * (Math.PI / 180)
  return { x: cx + r * Math.cos(rad), y: cx + r * Math.sin(rad) }
}

const slices = computed(() => {
  const c = center.value
  const r = radius.value
  const t = thickness.value
  const denom = total.value || 1
  let startAngle = 0
  return props.data.map((d, i) => {
    const fraction = d.value / denom
    const endAngle = startAngle + fraction * 360
    const outerStart = polar(c, startAngle, r)
    const outerEnd = polar(c, endAngle, r)
    const innerStart = polar(c, endAngle, r - t)
    const innerEnd = polar(c, startAngle, r - t)
    const largeArc = endAngle - startAngle > 180 ? 1 : 0
    const fullCircle = `M ${c - r} ${c} A ${r} ${r} 0 1 1 ${c + r} ${c} A ${r} ${r} 0 1 1 ${c - r} ${c} Z`
    const path = props.variant === 'pie'
      ? (fraction >= 0.9999
          ? fullCircle
          : `M ${c} ${c} L ${outerStart.x} ${outerStart.y} A ${r} ${r} 0 ${largeArc} 1 ${outerEnd.x} ${outerEnd.y} Z`)
      : (fraction >= 0.9999
          ? `${fullCircle} M ${c - (r - t)} ${c} A ${r - t} ${r - t} 0 1 0 ${c + (r - t)} ${c} A ${r - t} ${r - t} 0 1 0 ${c - (r - t)} ${c} Z`
          : `M ${outerStart.x} ${outerStart.y} A ${r} ${r} 0 ${largeArc} 1 ${outerEnd.x} ${outerEnd.y} L ${innerStart.x} ${innerStart.y} A ${r - t} ${r - t} 0 ${largeArc} 0 ${innerEnd.x} ${innerEnd.y} Z`)
    const labelPoint = polar(c, startAngle + fraction * 180, props.variant === 'pie' ? r * .62 : r - t / 2)
    const slice = {
      path,
      color: palette.value[i % palette.value.length],
      percent: Math.round(fraction * 100),
      labelX: labelPoint.x,
      labelY: labelPoint.y,
      ...d,
    }
    startAngle = endAngle
    return slice
  })
})
</script>

<template>
  <div class="pie-wrap" :class="{ 'pie-wrap--centered': centered }">
    <div class="pie-svg-wrap" :style="{ width: `${size}px`, height: `${size}px` }">
      <svg :viewBox="`0 0 ${size} ${size}`" class="pie-svg" role="img" :aria-label="variant === 'pie' ? 'Pie chart' : 'Donut chart'">
        <circle
          v-if="total === 0"
          :cx="center"
          :cy="center"
          :r="variant === 'pie' ? radius : radius - thickness / 2"
          :fill="variant === 'pie' ? '#EEF2F6' : 'none'"
          :stroke="variant === 'pie' ? 'none' : '#EEF2F6'"
          :stroke-width="thickness"
        />
        <path
          v-for="(s, i) in slices" :key="i" :d="s.path" :fill="s.color"
          fill-rule="evenodd"
          :fill-opacity="hoverIndex === null || hoverIndex === i ? 1 : 0.35"
          @mouseenter="hoverIndex = i" @mouseleave="hoverIndex = null"
        />
        <text
          v-for="(s, i) in slices"
          v-show="showLabels && s.percent >= 8"
          :key="`label-${i}`"
          :x="s.labelX"
          :y="s.labelY + 4"
          text-anchor="middle"
          class="slice-label"
        >{{ s.percent }}%</text>
      </svg>
      <div v-if="variant === 'donut'" class="pie-center">
        <span class="pie-total">{{ total.toLocaleString() }}</span>
        <span class="pie-total-label">Total</span>
      </div>
    </div>
    <div class="legend">
      <div v-for="(s, i) in slices" :key="i" class="legend-row" :class="{ dim: hoverIndex !== null && hoverIndex !== i }" @mouseenter="hoverIndex = i" @mouseleave="hoverIndex = null">
        <span class="swatch" :style="{ background: s.color }" />
        <span class="legend-label">{{ s.label }}</span>
        <span class="legend-value">
          {{ s.value.toLocaleString() }}<template v-if="showLegendPercent"> &middot; {{ s.percent }}%</template>
        </span>
      </div>
      <div v-if="!data.length" class="text-caption text-medium-emphasis">No data</div>
    </div>
  </div>
</template>

<style scoped>
.pie-wrap { display: grid; grid-template-columns: 124px minmax(0, 1fr); align-items: center; gap: 16px; width: 100%; }
.pie-svg-wrap { position: relative; width: 124px; height: 124px; }
.pie-svg { width: 100%; height: 100%; }
.pie-svg path { transition: fill-opacity 150ms ease; cursor: default; }
.slice-label { fill: #fff; font-size: 14px; font-weight: 750; pointer-events: none; paint-order: stroke; stroke: rgb(15 23 42 / 18%); stroke-width: 1px; }
.pie-center { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; pointer-events: none; }
.pie-total { font-size: 1.15rem; font-weight: 750; color: #0f172a; line-height: 1.1; }
.pie-total-label { font-size: .65rem; color: #94a3b8; font-weight: 600; text-transform: uppercase; letter-spacing: .04em; }
.legend { min-width: 0; display: flex; flex-direction: column; gap: 8px; font-size: .78rem; }
.legend-row { display: flex; align-items: center; gap: 8px; transition: opacity 150ms ease; cursor: default; }
.legend-row.dim { opacity: 0.4; }
.swatch { width: 9px; height: 9px; border-radius: 3px; flex-shrink: 0; }
.legend-label { min-width: 0; flex: 1; overflow-wrap: anywhere; color: #334155; }
.legend-value { color: #64748b; font-variant-numeric: tabular-nums; font-size: .74rem; white-space: nowrap; }
@media (max-width: 380px) {
  .pie-wrap { grid-template-columns: 108px minmax(0, 1fr); gap: 12px; }
  .pie-svg-wrap { width: 108px; height: 108px; }
}

/* Featured dashboard variant: chart and legend share one balanced row. */
.pie-wrap--centered {
  grid-template-columns: minmax(200px, 1fr) minmax(180px, 220px);
  align-items: center;
  gap: 20px;
  padding-block: 8px 12px;
}
.pie-wrap--centered .pie-svg-wrap { justify-self: center; max-width: 100%; }
.pie-wrap--centered .pie-total { font-size: 1.6rem; }
.pie-wrap--centered .pie-total-label { font-size: .72rem; }
.pie-wrap--centered .legend {
  width: 100%;
  gap: 10px;
  font-size: .8rem;
}
.pie-wrap--centered .legend-row { justify-content: flex-start; }
.pie-wrap--centered .legend-label { flex: 1; }
.pie-wrap--centered .swatch { width: 11px; height: 11px; }
.pie-wrap--centered .legend-value { font-size: .76rem; }
@media (max-width: 600px) {
  .pie-wrap--centered {
    grid-template-columns: 1fr;
    justify-items: center;
    gap: 12px;
  }
  .pie-wrap--centered .legend { width: min(100%, 230px); }
}
</style>
