export interface VoucherAmountRow {
  householdNumber: string
  householdName: string
  amount: number
}

export function applyVoucherAmount(rows: VoucherAmountRow[], amount: number): VoucherAmountRow[] {
  return rows.map((row) => ({ ...row, amount }))
}
