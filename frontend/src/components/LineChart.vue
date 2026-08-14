<script setup lang="ts">
import { computed, ref } from 'vue'

interface Point { label: string; value: number }
const props = defineProps<{ data: Point[]; color?: string; valuePrefix?: string }>()

const width = 640
const height = 220
const padding = { top: 16, right: 16, bottom: 28, left: 16 }
const hoverIndex = ref<number | null>(null)

const stroke = computed(() => props.color ?? '#0F5C4D')

const maxValue = computed(() => Math.max(...props.data.map((d) => d.value), 1))

const points = computed(() => {
  const n = props.data.length
  if (n === 0) return []
  const innerW = width - padding.left - padding.right
  const innerH = height - padding.top - padding.bottom
  return props.data.map((d, i) => {
    const x = padding.left + (n === 1 ? innerW / 2 : (i / (n - 1)) * innerW)
    const y = padding.top + innerH - (d.value / maxValue.value) * innerH
    return { x, y, ...d }
  })
})

const linePath = computed(() =>
  points.value.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' '),
)

const areaPath = computed(() => {
  if (points.value.length === 0) return ''
  const last = points.value[points.value.length - 1]
  const first = points.value[0]
  const bottom = height - padding.bottom
  return `${linePath.value} L ${last.x} ${bottom} L ${first.x} ${bottom} Z`
})
</script>

<template>
  <div class="chart-wrap">
    <svg :viewBox="`0 0 ${width} ${height}`" class="w-100" role="img" aria-label="Line chart">
      <line
        :x1="padding.left" :x2="width - padding.right"
        :y1="height - padding.bottom" :y2="height - padding.bottom"
        stroke="#E0E0E0" stroke-width="1"
      />
      <path :d="areaPath" :fill="stroke" fill-opacity="0.08" stroke="none" />
      <path :d="linePath" fill="none" :stroke="stroke" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
      <g v-for="(p, i) in points" :key="i">
        <circle
          :cx="p.x" :cy="p.y" r="8" fill="transparent"
          @mouseenter="hoverIndex = i" @mouseleave="hoverIndex = null"
        />
        <circle :cx="p.x" :cy="p.y" :r="hoverIndex === i ? 5 : 3" :fill="stroke" stroke="#fff" stroke-width="1.5" />
        <text :x="p.x" :y="height - 8" text-anchor="middle" class="axis-label">{{ p.label }}</text>
      </g>
      <g v-if="hoverIndex !== null">
        <line
          :x1="points[hoverIndex].x" :x2="points[hoverIndex].x"
          :y1="padding.top" :y2="height - padding.bottom"
          stroke="#BDBDBD" stroke-width="1" stroke-dasharray="3,3"
        />
      </g>
    </svg>
    <div
      v-if="hoverIndex !== null"
      class="tooltip"
      :style="{ left: points[hoverIndex].x + 'px', top: points[hoverIndex].y + 'px' }"
    >
      <strong>{{ valuePrefix }}{{ points[hoverIndex].value.toLocaleString() }}</strong>
      <div class="text-caption">{{ points[hoverIndex].label }}</div>
    </div>
  </div>
</template>

<style scoped>
.chart-wrap { position: relative; }
.axis-label { font-size: 10px; fill: rgba(0, 0, 0, 0.6); }
.tooltip {
  position: absolute;
  transform: translate(-50%, -120%);
  background: rgba(33, 33, 33, 0.92);
  color: #fff;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 12px;
  pointer-events: none;
  white-space: nowrap;
}
</style>
