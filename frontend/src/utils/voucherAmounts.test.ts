import { describe, expect, it } from 'vitest'
import { applyVoucherAmount, type VoucherAmountRow } from './voucherAmounts'

describe('voucher amount application', () => {
  it('fills every household row and returns a reactive replacement array', () => {
    const rows: VoucherAmountRow[] = [
      { householdNumber: 'HH-1', householdName: 'First household', amount: 0 },
      { householdNumber: 'HH-2', householdName: 'Second household', amount: 25 },
    ]

    const updated = applyVoucherAmount(rows, 400)

    expect(updated).not.toBe(rows)
    expect(updated.map((row) => row.amount)).toEqual([400, 400])
    expect(rows.map((row) => row.amount)).toEqual([0, 25])
  })
})
