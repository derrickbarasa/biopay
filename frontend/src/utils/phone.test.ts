import { describe, expect, it } from 'vitest'
import { isValidPhone, phoneRule } from './phone'

describe('isValidPhone', () => {
  it('accepts a plain E.164 number', () => {
    expect(isValidPhone('+254712345678')).toBe(true)
  })
  it('accepts spaces and dashes around the digits', () => {
    expect(isValidPhone('+254 712 345 678')).toBe(true)
    expect(isValidPhone('+1-212-555-0100')).toBe(true)
  })
  it('rejects a number with no country code', () => {
    expect(isValidPhone('0712345678')).toBe(false)
    expect(isValidPhone('712345678')).toBe(false)
  })
  it('rejects a bare plus or a leading zero after the plus', () => {
    expect(isValidPhone('+')).toBe(false)
    expect(isValidPhone('+0712345678')).toBe(false)
  })
})

describe('phoneRule', () => {
  it('passes a blank value through (optionality is a separate concern)', () => {
    expect(phoneRule('')).toBe(true)
    expect(phoneRule(undefined)).toBe(true)
    expect(phoneRule(null)).toBe(true)
  })
  it('passes a value that already has a country code', () => {
    expect(phoneRule('+254712345678')).toBe(true)
  })
  it('returns an error message for a number missing its country code', () => {
    expect(phoneRule('0712345678')).toBe('Include the country code, e.g. +254712345678')
  })
})
