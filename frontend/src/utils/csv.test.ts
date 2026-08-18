import { describe, expect, it } from 'vitest'
import { parseCsv, toCsv } from './csv'

describe('CSV helpers', () => {
  it('round-trips quoted commas, quotes, and newlines', () => {
    const csv = toCsv(['name', 'note'], [['Nyandeng, Malual', 'Line 1\nLine "2"']])
    expect(parseCsv(csv)).toEqual([{ name: 'Nyandeng, Malual', note: 'Line 1\nLine "2"' }])
  })

  it('ignores blank rows and trims cell values', () => {
    expect(parseCsv('name,age\r\n  Peter  , 42 \r\n\r\n')).toEqual([{ name: 'Peter', age: '42' }])
  })
})
