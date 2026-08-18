import { describe, expect, it } from 'vitest'
import { PUBLIC_PROCESSING_CODES } from './processingCodes'

describe('processing code routing', () => {
  it('keeps session-establishing calls public', () => {
    expect(PUBLIC_PROCESSING_CODES.has('LOGIN_USER')).toBe(true)
    expect(PUBLIC_PROCESSING_CODES.has('VERIFY_LOGIN_OTP')).toBe(true)
  })

  it('does not expose administration calls without JWT authentication', () => {
    expect(PUBLIC_PROCESSING_CODES.has('GET_USERS' as never)).toBe(false)
    expect(PUBLIC_PROCESSING_CODES.has('SAVE_ROLE' as never)).toBe(false)
  })
})
