import { describe, expect, it } from 'vitest'
import { daysUntilExpiry } from './subscriptionDates'

describe('daysUntilExpiry', () => {
  it('shows the full term on the day a subscription is issued', () => {
    expect(daysUntilExpiry('2026-11-13', new Date(2026, 8, 14, 23, 30))).toBe(60)
  })

  it('counts down using calendar days rather than elapsed hours', () => {
    expect(daysUntilExpiry('2026-11-13', new Date(2026, 8, 15, 0, 1))).toBe(59)
  })

  it('does not display negative days after expiry', () => {
    expect(daysUntilExpiry('2026-09-13', new Date(2026, 8, 14))).toBe(0)
  })

  it('returns no value when there is no valid expiry date', () => {
    expect(daysUntilExpiry(undefined, new Date(2026, 8, 14))).toBeNull()
    expect(daysUntilExpiry('not-a-date', new Date(2026, 8, 14))).toBeNull()
  })
})
