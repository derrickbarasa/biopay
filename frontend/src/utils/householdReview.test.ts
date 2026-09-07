import { describe, expect, it } from 'vitest'
import { HOUSEHOLD_REVIEW_STATUSES, householdIsPendingReview, householdReviewStatus } from './householdReview'

describe('household review workflow', () => {
  it('exposes only the three supported statuses', () => {
    expect(HOUSEHOLD_REVIEW_STATUSES).toEqual(['PENDING', 'APPROVED', 'REJECTED'])
  })

  it('keeps legacy checked households pending and actionable', () => {
    expect(householdReviewStatus('CHECKED')).toBe('PENDING')
    expect(householdIsPendingReview('CHECKED')).toBe(true)
  })

  it('preserves final decisions and treats missing data as pending', () => {
    expect(householdReviewStatus(' approved ')).toBe('APPROVED')
    expect(householdReviewStatus('REJECTED')).toBe('REJECTED')
    expect(householdReviewStatus(null)).toBe('PENDING')
    expect(householdIsPendingReview('APPROVED')).toBe(false)
  })
})
