import { describe, expect, it } from 'vitest'
import { expectedLcy, isCompleteAmountRow, paymentCycleTotals } from './paymentCycleAmounts'

describe('payment-cycle beneficiary amounts', () => {
  it('calculates LCY to two decimal places', () => {
    expect(expectedLcy(12.35, 3.2)).toBe(39.52)
  })

  it('requires the entered LCY amount to agree with FCY and the exchange rate', () => {
    expect(isCompleteAmountRow({ householdNumber: 'HH-1', householdName: 'One', amountFcy: 100, exchangeRate: 2.5, amountLcy: 250 })).toBe(true)
    expect(isCompleteAmountRow({ householdNumber: 'HH-1', householdName: 'One', amountFcy: 100, exchangeRate: 2.5, amountLcy: 251 })).toBe(false)
  })

  it('totals different beneficiary amounts independently', () => {
    expect(paymentCycleTotals([
      { householdNumber: 'HH-1', householdName: 'One', amountFcy: 100, exchangeRate: 2, amountLcy: 200 },
      { householdNumber: 'HH-2', householdName: 'Two', amountFcy: 75, exchangeRate: 3, amountLcy: 225 },
    ])).toEqual({ amountFcy: 175, amountLcy: 425 })
  })
})
