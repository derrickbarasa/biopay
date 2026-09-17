<script setup lang="ts">
import { computed } from 'vue'

type MetricTone = 'teal' | 'green' | 'amber' | 'red' | 'slate'

const props = withDefaults(defineProps<{
  label: string
  value: string | number
  detail: string
  icon: string
  tone?: MetricTone
  progress?: number | null
  progressLabel?: string
}>(), {
  tone: 'teal',
  progress: null,
  progressLabel: '',
})

const RING_RADIUS = 26
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS
const progressValue = computed(() => Math.max(0, Math.min(1, Number(props.progress) || 0)))
const progressDash = computed(() => `${(progressValue.value * RING_CIRCUMFERENCE).toFixed(2)} ${RING_CIRCUMFERENCE.toFixed(2)}`)
const hasProgress = computed(() => props.progress !== null && props.progress !== undefined)
</script>

<template>
  <v-card class="summary-metric-card" :class="`summary-metric-card--${tone}`" variant="flat" border>
    <div class="summary-metric-card__copy">
      <div class="summary-metric-card__label">{{ label }}</div>
      <div class="summary-metric-card__value">{{ value }}</div>
      <div class="summary-metric-card__detail">{{ detail }}</div>
    </div>

    <div
      class="summary-metric-card__visual"
      :class="{ 'summary-metric-card__visual--ring': hasProgress }"
      role="img"
      :aria-label="progressLabel || `${label} summary`"
    >
      <svg v-if="hasProgress" viewBox="0 0 64 64" aria-hidden="true">
        <circle class="summary-metric-card__ring-track" cx="32" cy="32" :r="RING_RADIUS" fill="none" stroke-width="5" />
        <circle
          class="summary-metric-card__ring-value"
          cx="32"
          cy="32"
          :r="RING_RADIUS"
          fill="none"
          stroke-width="5"
          stroke-linecap="round"
          transform="rotate(-90 32 32)"
          :stroke-dasharray="progressDash"
        />
      </svg>
      <v-icon :icon="icon" size="24" aria-hidden="true" />
    </div>
  </v-card>
</template>

<style scoped>
.summary-metric-card {
  --metric-color: #0f766e;
  --metric-soft: #ccfbf1;
  --metric-highlight: rgba(15, 118, 110, .055);
  --metric-highlight-border: rgba(15, 118, 110, .4);
  min-height: 124px;
  padding: 18px 20px !important;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  transition: background-color 180ms cubic-bezier(.16, 1, .3, 1), border-color 180ms cubic-bezier(.16, 1, .3, 1);
}

.summary-metric-card--green { --metric-color: #047857; --metric-soft: #d1fae5; --metric-highlight: rgba(4, 120, 87, .055); --metric-highlight-border: rgba(4, 120, 87, .4); }
.summary-metric-card--amber { --metric-color: #b45309; --metric-soft: #fef3c7; --metric-highlight: rgba(180, 83, 9, .055); --metric-highlight-border: rgba(180, 83, 9, .4); }
.summary-metric-card--red { --metric-color: #b91c1c; --metric-soft: #fee2e2; --metric-highlight: rgba(185, 28, 28, .05); --metric-highlight-border: rgba(185, 28, 28, .38); }
.summary-metric-card--slate { --metric-color: #475569; --metric-soft: #f1f5f9; --metric-highlight: rgba(71, 85, 105, .05); --metric-highlight-border: rgba(71, 85, 105, .38); }

.summary-metric-card__copy {
  min-width: 0;
}

.summary-metric-card__label {
  color: #64748b;
  font-size: .72rem;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: .04em;
  text-transform: uppercase;
}

.summary-metric-card__value {
  margin-top: 5px;
  color: #0f172a;
  font-size: 1.6rem;
  font-weight: 750;
  line-height: 1.1;
  letter-spacing: -.025em;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.summary-metric-card__detail {
  margin-top: 7px;
  color: #64748b;
  font-size: .75rem;
  line-height: 1.35;
}

.summary-metric-card__visual {
  position: relative;
  width: 56px;
  height: 56px;
  flex: 0 0 56px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--metric-soft);
  color: var(--metric-color);
}

.summary-metric-card__visual--ring {
  width: 64px;
  height: 64px;
  flex-basis: 64px;
  background: transparent;
}

.summary-metric-card__visual svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.summary-metric-card__ring-track { stroke: #e8eef3; }
.summary-metric-card__ring-value { stroke: var(--metric-color); }

@media (hover: hover) and (pointer: fine) {
  .summary-metric-card:hover {
    border-color: var(--metric-highlight-border) !important;
    background-color: var(--metric-highlight);
  }
}

@media (prefers-reduced-motion: reduce) {
  .summary-metric-card { transition-duration: 0.01ms; }
}

@media (max-width: 420px) {
  .summary-metric-card { min-height: 112px; padding: 16px !important; }
}
</style>
