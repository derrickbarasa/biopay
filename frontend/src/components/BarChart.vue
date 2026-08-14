<script setup lang="ts">
import { computed, ref } from 'vue'

interface Bar { label: string; value: number }
const props = defineProps<{ data: Bar[]; color?: string }>()

const width = 640
const height = 220
const padding = { top: 16, right: 16, bottom: 28, left: 16 }
const hoverIndex = ref<number | null>(null)

const fill = computed(() => props.color ?? '#D98E2B')
const maxValue = computed(() => Math.max(...props.data.map((d) => d.value), 1))

const bars = computed(() => {
  const n = props.data.length
  if (n === 0) return []
  const innerW = width - padding.left - padding.right
  const innerH = height - padding.top - padding.bottom
  const gap = 12
  const barWidth = Math.max((innerW - gap * (n - 1)) / n, 4)
  return props.data.map((d, i) => {
    const barHeight = (d.value / maxValue.value) * innerH
    const x = padding.left + i * (barWidth + gap)
    const y = padding.top + innerH - barHeight
    return { x, y, width: barWidth, height: barHeight, ...d }
  })
})
</script>

<template>
  <div class="chart-wrap">
    <svg :viewBox="`0 0 ${width} ${height}`" class="w-100" role="img" aria-label="Bar chart">
      <line
        :x1="padding.left" :x2="width - padding.right"
        :y1="height - padding.bottom" :y2="height - padding.bottom"
        stroke="#E0E0E0" stroke-width="1"
      />
      <g v-for="(b, i) in bars" :key="i" @mouseenter="hoverIndex = i" @mouseleave="hoverIndex = null">
        <rect
          :x="b.x" :y="b.y" :width="b.width" :height="Math.max(b.height, 2)"
          :fill="fill" :fill-opacity="hoverIndex === i ? 1 : 0.85" rx="4" ry="4"
        />
        <text :x="b.x + b.width / 2" :y="height - 8" text-anchor="middle" class="axis-label">{{ b.label }}</text>
      </g>
    </svg>
    <div
      v-if="hoverIndex !== null"
      class="tooltip"
      :style="{ left: bars[hoverIndex].x + bars[hoverIndex].width / 2 + 'px', top: bars[hoverIndex].y + 'px' }"
    >
      <strong>{{ bars[hoverIndex].value.toLocaleString() }}</strong>
      <div class="text-caption">{{ bars[hoverIndex].label }}</div>
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
