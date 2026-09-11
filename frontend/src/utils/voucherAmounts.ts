export interface VoucherAmountRow {
  householdNumber: string
  householdName: string
  amount: number
  stateCode?: string
  countyCode?: string
  bomaCode?: string
}

export function applyVoucherAmount(rows: VoucherAmountRow[], amount: number): VoucherAmountRow[] {
  return rows.map((row) => ({ ...row, amount }))
}
