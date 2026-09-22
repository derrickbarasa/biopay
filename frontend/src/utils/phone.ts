// E.164-shaped: a leading "+", then 7-15 digits total, first digit 1-9. Spaces and dashes
// are stripped before checking, so "+254 712 345 678" and "+254712345678" both pass -- only
// the presence of the country code itself is enforced, not a specific separator style.
const PHONE_PATTERN = /^\+[1-9]\d{6,14}$/

export function isValidPhone(value: string): boolean {
  return PHONE_PATTERN.test(value.replace(/[\s-]/g, ''))
}

/** Vuetify field rule: a blank value passes (fields stay optional unless paired with a
 *  required rule too) -- but any non-blank phone number must include its country code. */
export function phoneRule(value: string | null | undefined): true | string {
  const trimmed = (value ?? '').trim()
  if (!trimmed) return true
  return isValidPhone(trimmed) || 'Include the country code, e.g. +254712345678'
}
