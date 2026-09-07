<script setup lang="ts">
import { computed } from 'vue'

interface BarItem { label: string; value: number; color?: string }

const props = withDefaults(defineProps<{
  data: BarItem[]
  color?: string
  ariaLabel?: string
}>(), {
  color: '#0D9488',
  ariaLabel: 'Vertical bar chart',
})

const maximum = computed(() => Math.max(1, ...props.data.map((item) => item.value)))
const rows = computed(() => props.data.map((item) => ({
  ...item,
  height: item.value ? Math.max(3, (item.value / maximum.value) * 82) : 0,
})))
</script>

<template>
  <div class="vertical-chart-scroll" tabindex="0" :aria-label="ariaLabel">
    <div class="vertical-chart" role="img" :aria-label="ariaLabel">
      <div v-for="row in rows" :key="row.label" class="vertical-chart__column">
        <div class="vertical-chart__plot">
          <strong>{{ row.value.toLocaleString() }}</strong>
          <span
            class="vertical-chart__bar"
            :style="{ height: `${row.height}%`, background: row.color ?? color }"
            aria-hidden="true"
          />
        </div>
        <span class="vertical-chart__label">{{ row.label }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.vertical-chart-scroll {
  width: 100%;
  overflow-x: auto;
  scrollbar-width: thin;
  scrollbar-color: #94a3b8 transparent;
}
.vertical-chart-scroll:focus-visible { outline: 2px solid #0f766e; outline-offset: 2px; }
.vertical-chart {
  min-width: 500px;
  display: grid;
  grid-template-columns: repeat(6, minmax(58px, 1fr));
  gap: 9px;
}
.vertical-chart__column { min-width: 0; display: grid; grid-template-rows: 132px auto; text-align: center; }
.vertical-chart__plot {
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
  border-bottom: 1px solid #cbd5e1;
  background: repeating-linear-gradient(to top, transparent 0, transparent 32px, #e7edf2 33px);
}
.vertical-chart__plot strong {
  margin-bottom: 5px;
  color: #172554;
  font-size: .72rem;
  font-weight: 750;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.vertical-chart__bar {
  width: min(48px, 68%);
  min-height: 0;
  border-radius: 5px 5px 0 0;
  background: linear-gradient(180deg, #20b99f 0%, #0d9488 100%);
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 28%);
}
.vertical-chart__label { margin-top: 7px; color: #334155; font-size: .68rem; line-height: 1.15; }
</style>
