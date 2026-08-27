/** "USD 1,234" rather than Intl's currency-symbol formatting, which renders USD as
 *  "US$" (or bare "$", ambiguous against other dollar currencies) depending on locale. */
export function formatCurrency(amount: number | null | undefined, code = 'USD'): string {
  return `${code} ${(amount ?? 0).toLocaleString(undefined, { maximumFractionDigits: 0 })}`
}

/** Compact variant for space-constrained summary tiles, e.g. "USD 12.3K". */
export function formatCurrencyCompact(amount: number | null | undefined, code = 'USD'): string {
  return `${code} ${(amount ?? 0).toLocaleString(undefined, { notation: 'compact', maximumFractionDigits: 1 })}`
}
