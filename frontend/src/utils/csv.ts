/**
 * Minimal RFC4180-ish CSV helpers for the household/voucher bulk-upload flows.
 * No parsing library dependency by design -- the household template only ever
 * has a handful of plain-text columns, so a small hand-rolled parser is enough
 * and keeps quoted fields with embedded commas/newlines working correctly.
 */

/** Splits one CSV document into rows of raw string cells (handles quoted fields). */
function parseRows(text: string): string[][] {
  const rows: string[][] = []
  let row: string[] = []
  let cell = ''
  let inQuotes = false

  for (let i = 0; i < text.length; i++) {
    const char = text[i]
    if (inQuotes) {
      if (char === '"') {
        if (text[i + 1] === '"') { cell += '"'; i++ } else { inQuotes = false }
      } else {
        cell += char
      }
    } else if (char === '"') {
      inQuotes = true
    } else if (char === ',') {
      row.push(cell); cell = ''
    } else if (char === '\n' || char === '\r') {
      if (char === '\r' && text[i + 1] === '\n') i++
      row.push(cell); cell = ''
      if (row.some((c) => c.length > 0)) rows.push(row)
      row = []
    } else {
      cell += char
    }
  }
  if (cell.length > 0 || row.length > 0) {
    row.push(cell)
    if (row.some((c) => c.length > 0)) rows.push(row)
  }
  return rows
}

/** Parses a CSV document into objects keyed by its header row. */
export function parseCsv(text: string): Record<string, string>[] {
  const rows = parseRows(text)
  if (rows.length < 2) return []
  const headers = rows[0].map((h) => h.trim())
  return rows.slice(1).map((row) => {
    const obj: Record<string, string> = {}
    headers.forEach((h, i) => { obj[h] = (row[i] ?? '').trim() })
    return obj
  })
}

function escapeCell(value: string | number): string {
  const str = String(value ?? '')
  return /[",\n]/.test(str) ? `"${str.replace(/"/g, '""')}"` : str
}

export function toCsv(headers: string[], rows: (string | number)[][] = []): string {
  const lines = [headers.map(escapeCell).join(',')]
  for (const row of rows) lines.push(row.map(escapeCell).join(','))
  return lines.join('\r\n')
}

/** Triggers a browser download of the given CSV text. */
export function downloadCsv(filename: string, content: string) {
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
