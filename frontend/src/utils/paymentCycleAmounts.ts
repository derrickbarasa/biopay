export interface PaymentCycleAmountRow {
  householdNumber: string
  householdName: string
  amountFcy: number | null
  exchangeRate: number | null
  amountLcy: number | null
}

export function expectedLcy(amountFcy: number | null, exchangeRate: number | null): number | null {
  if (!amountFcy || !exchangeRate || amountFcy <= 0 || exchangeRate <= 0) return null
  return Math.round((amountFcy * exchangeRate + Number.EPSILON) * 100) / 100
}

export function isCompleteAmountRow(row: PaymentCycleAmountRow): boolean {
  const expected = expectedLcy(row.amountFcy, row.exchangeRate)
  return expected != null && !!row.amountLcy && row.amountLcy > 0 && Math.abs(row.amountLcy - expected) <= 0.01
}

export function paymentCycleTotals(rows: PaymentCycleAmountRow[]) {
  return rows.reduce((totals, row) => ({
    amountFcy: totals.amountFcy + (row.amountFcy ?? 0),
    amountLcy: totals.amountLcy + (row.amountLcy ?? 0),
  }), { amountFcy: 0, amountLcy: 0 })
}
