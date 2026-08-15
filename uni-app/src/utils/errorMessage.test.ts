import { describe, expect, it } from 'vitest'
import { extractErrorMessage } from './errorMessage'

describe('extractErrorMessage', () => {
  it('returns nested api message when available', () => {
    expect(extractErrorMessage({ data: { message: '验证码错误' } }, 'fallback')).toBe('验证码错误')
  })

  it('returns error instance message when present', () => {
    expect(extractErrorMessage(new Error('网络失败'), 'fallback')).toBe('网络失败')
  })

  it('falls back for empty payloads', () => {
    expect(extractErrorMessage({}, 'fallback')).toBe('fallback')
  })
})
