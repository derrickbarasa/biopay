import { describe, expect, it } from 'vitest'
import { householdGenderBreakdown } from './gender'

describe('household gender breakdown', () => {
  it('counts mobile labels and dashboard codes together', () => {
    expect(householdGenderBreakdown(['Male', 'Female', 'M', 'F', 'Male'].map(gender => ({ gender })))).toEqual([
      { label: 'Male', value: 3 }, { label: 'Female', value: 2 },
    ])
  })
  it('handles casing and whitespace in existing and imported records', () => {
    expect(householdGenderBreakdown([' male ', 'female ', 'm', ' f '].map(gender => ({ gender })))).toEqual([
      { label: 'Male', value: 2 }, { label: 'Female', value: 2 },
    ])
  })
  it('keeps only male/female and never assigns missing values to either category', () => {
    expect(householdGenderBreakdown([{}, { gender: null }, { gender: '' }, { gender: 'unknown' }])).toEqual([
      { label: 'Male', value: 0 }, { label: 'Female', value: 0 },
    ])
  })
})
