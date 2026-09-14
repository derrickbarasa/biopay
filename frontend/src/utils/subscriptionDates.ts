const MILLISECONDS_PER_DAY = 24 * 60 * 60 * 1000

function calendarDayUtc(value: string | Date): number | null {
  if (value instanceof Date) {
    if (Number.isNaN(value.getTime())) return null
    return Date.UTC(value.getFullYear(), value.getMonth(), value.getDate())
  }

  const dateOnly = value.match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (dateOnly) {
    const [, year, month, day] = dateOnly
    return Date.UTC(Number(year), Number(month) - 1, Number(day))
  }

  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return null
  return Date.UTC(parsed.getFullYear(), parsed.getMonth(), parsed.getDate())
}

/**
 * Returns whole calendar days remaining. Date-only subscription values are kept
 * timezone-neutral, so a newly issued 60-day period displays 60 on its issue day.
 */
export function daysUntilExpiry(expiresAt?: string | null, today: Date = new Date()): number | null {
  if (!expiresAt) return null
  const expiryDay = calendarDayUtc(expiresAt)
  const currentDay = calendarDayUtc(today)
  if (expiryDay == null || currentDay == null) return null
  return Math.max(0, Math.round((expiryDay - currentDay) / MILLISECONDS_PER_DAY))
}
