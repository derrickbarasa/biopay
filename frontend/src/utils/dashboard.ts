export type ChartPeriod = 'day' | 'week' | 'month' | 'year'
export interface ChartPoint { label: string; value: number; fullLabel?: string }

export function dateKey(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export function chartBuckets(period: ChartPeriod, referenceDate: string, now = new Date()) {
  const [year, month, day] = referenceDate.split('-').map(Number)
  const selected = new Date(year, month - 1, day)
  if (!Number.isFinite(selected.getTime()) || dateKey(selected) !== referenceDate) return []
  const start = new Date(selected)
  if (period === 'month') start.setDate(1)
  if (period === 'year') start.setMonth(0, 1)
  if (period === 'week') start.setDate(start.getDate() - (start.getDay() + 6) % 7)
  const length = period === 'day' ? 24 : period === 'week' ? 7 : period === 'year' ? 12 : new Date(year, month, 0).getDate()
  return Array.from({ length }, (_, i) => {
    const date = new Date(start)
    if (period === 'day') date.setHours(i)
    else if (period === 'year') date.setMonth(i)
    else date.setDate(start.getDate() + i)
    const hour = `${String(i).padStart(2, '0')}:00`
    return {
      date,
      key: period === 'day' ? String(i).padStart(2, '0') : period === 'year' ? dateKey(date).slice(0, 7) : dateKey(date),
      label: period === 'day' ? hour : period === 'year' ? date.toLocaleDateString(undefined, { month: 'short' }) : String(date.getDate()),
      fullLabel: period === 'day' ? `${referenceDate} ${hour}` : period === 'year' ? date.toLocaleDateString(undefined, { month: 'long', year: 'numeric' }) : dateKey(date),
    }
  }).filter(bucket => bucket.date <= now)
}

export function filledSeries<T extends { period: string }>(rows: T[], value: (row: T) => number, period: ChartPeriod, referenceDate: string, now = new Date()): ChartPoint[] {
  const byPeriod = new Map<string, number>()
  for (const row of rows) {
    const amount = value(row)
    if (!Number.isFinite(amount) || amount < 0) throw new Error('The chart returned an invalid value. Refresh to try again.')
    byPeriod.set(row.period, (byPeriod.get(row.period) ?? 0) + amount)
  }
  return chartBuckets(period, referenceDate, now).map(({ key, label, fullLabel }) => ({ label, fullLabel, value: byPeriod.get(key) ?? 0 }))
}

/** Dashboard amounts are source USD amounts; payout currency uses the cycle's exchange rate. */
export function dashboardCurrency(value: number | null | undefined): string {
  return `USD ${(value ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

export function chartScale(max: number, integer = false) {
  if (!(max > 0)) return { step: 1, max: 4 }
  const rough = max / 4
  const magnitude = 10 ** Math.floor(Math.log10(rough))
  const normalized = rough / magnitude
  const step = Math.max(integer ? 1 : 0, (normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10) * magnitude)
  return { step, max: Math.max(step * 4, max) }
}
