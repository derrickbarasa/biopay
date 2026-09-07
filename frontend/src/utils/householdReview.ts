export const HOUSEHOLD_REVIEW_STATUSES = ['PENDING', 'APPROVED', 'REJECTED'] as const

export type HouseholdReviewStatus = typeof HOUSEHOLD_REVIEW_STATUSES[number]

export function householdReviewStatus(value?: string | null): HouseholdReviewStatus {
  const status = value?.trim().toUpperCase()
  if (status === 'APPROVED' || status === 'REJECTED') return status
  return 'PENDING'
}

export function householdIsPendingReview(value?: string | null): boolean {
  return householdReviewStatus(value) === 'PENDING'
}
