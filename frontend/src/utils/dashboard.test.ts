import { describe, expect, it } from 'vitest'
import { chartBuckets, chartScale, dashboardCurrency, filledSeries } from './dashboard'

const now = new Date(2026, 8, 7, 10, 30)
describe('dashboard date buckets', () => {
  it('includes month-to-date zeros but never invents future activity', () => {
    const result = filledSeries([{ period: '2026-09-02', value: 3000.25 }], r => r.value, 'month', '2026-09-07', now)
    expect(result).toHaveLength(7)
    expect(result[1]).toEqual({ label: '2', fullLabel: '2026-09-02', value: 3000.25 })
    expect(result[0].value).toBe(0)
    expect(result.reduce((s, p) => s + p.value, 0)).toBe(3000.25)
  })
  it('covers every day of historical months including leap days', () => {
    expect(chartBuckets('month', '2024-02-15', now)).toHaveLength(29)
    expect(chartBuckets('month', '2025-02-15', now)).toHaveLength(28)
    expect(chartBuckets('month', '2026-08-01', now)).toHaveLength(31)
  })
  it('keeps Monday-based weeks intact across year boundaries', () => {
    expect(chartBuckets('week', '2025-01-01', now).map(p => p.key)).toEqual([
      '2024-12-30', '2024-12-31', '2025-01-01', '2025-01-02', '2025-01-03', '2025-01-04', '2025-01-05',
    ])
  })
  it('includes each elapsed hour and month, with complete historical periods', () => {
    expect(chartBuckets('day', '2026-09-07', now)).toHaveLength(11)
    expect(chartBuckets('day', '2026-09-06', now)).toHaveLength(24)
    expect(chartBuckets('year', '2026-09-07', now)).toHaveLength(9)
    expect(chartBuckets('year', '2025-01-01', now)).toHaveLength(12)
    expect(chartBuckets('month', '2026-10-01', now)).toEqual([])
  })
  it('rejects invalid dates instead of silently shifting months', () => {
    expect(chartBuckets('month', '2026-02-30', now)).toEqual([])
    expect(chartBuckets('month', '', now)).toEqual([])
  })
  it('preserves duplicate bucket amounts instead of overwriting data', () => {
    const rows = [{ period: '2026-09-02', value: 1 }, { period: '2026-09-02', value: 2 }]
    expect(filledSeries(rows, r => r.value, 'month', '2026-09-07', now)[1].value).toBe(3)
  })
  it('rejects corrupt values instead of displaying a false zero', () => {
    for (const value of [NaN, Infinity, -1]) {
      expect(() => filledSeries([{ period: '2026-09-02', value }], r => r.value, 'month', '2026-09-07', now)).toThrow()
    }
  })
})
describe('dashboard numerical accuracy', () => {
  it('never clips tall columns or uses fractional count ticks', () => {
    for (const max of [0, 1, 3, 5, 9, 25, 2999, 10001, 1234567]) {
      const scale = chartScale(max, true)
      expect(scale.max).toBeGreaterThanOrEqual(max)
      expect(Number.isInteger(scale.step)).toBe(true)
    }
    expect(chartScale(.03).max).toBeGreaterThanOrEqual(.03)
  })
  it('keeps cents and avoids compact rounding of headline totals', () => {
    expect(dashboardCurrency(3025.75)).toContain('3,025.75')
    expect(dashboardCurrency(0)).toBe('USD 0.00')
  })
})
