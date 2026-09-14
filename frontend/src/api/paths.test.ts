import { describe, expect, it } from 'vitest'
import { apiRelativeFilePath } from './paths'

describe('apiRelativeFilePath', () => {
  it.each([
    ['/biopay/api/v1/files/head.jpg', 'api/v1/files/head.jpg'],
    ['biopay/api/v1/files/head.jpg', 'api/v1/files/head.jpg'],
    ['/api/v1/files/head.jpg', 'api/v1/files/head.jpg'],
    ['api/v1/files/head.jpg', 'api/v1/files/head.jpg'],
    ['https://example.test/biopay/api/v1/files/head.jpg', 'api/v1/files/head.jpg'],
  ])('keeps %s relative to the configured API base', (input, expected) => {
    expect(apiRelativeFilePath(input)).toBe(expected)
  })
})
