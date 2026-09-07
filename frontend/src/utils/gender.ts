/** Mobile stores display labels; dashboard forms and imports also use M/F codes. */
export function householdGenderBreakdown(rows: { gender?: string | null }[]) {
  let male = 0
  let female = 0
  for (const row of rows) {
    const gender = row.gender?.trim().toUpperCase()
    if (gender === 'M' || gender === 'MALE') male++
    else if (gender === 'F' || gender === 'FEMALE') female++
  }
  return [{ label: 'Male', value: male }, { label: 'Female', value: female }]
}
