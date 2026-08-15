import { describe, expect, it } from 'vitest'
import {
  buildPasswordLoginRequest,
  inferLoginIdentifierType,
  isCodeLoginIdentifierValid,
  isPasswordLoginIdentifierValid,
} from './loginDialogRules'

describe('loginDialogRules', () => {
  it('accepts literal admin identifier for password login only', () => {
    expect(isCodeLoginIdentifierValid('admin')).toBe(false)
    expect(isPasswordLoginIdentifierValid('admin')).toBe(true)
    expect(inferLoginIdentifierType('admin')).toBe('identifier')
    expect(buildPasswordLoginRequest('admin', 'admin', 'token')).toEqual({
      identifier: 'admin',
      password: 'admin',
      challengeToken: 'token',
    })
  })

  it('preserves phone and email payload fields for standard identifiers', () => {
    expect(buildPasswordLoginRequest('13812345678', 'secret123', 'token')).toEqual({
      phone: '13812345678',
      identifier: '13812345678',
      password: 'secret123',
      challengeToken: 'token',
    })
    expect(buildPasswordLoginRequest('user@example.com', 'secret123', 'token')).toEqual({
      email: 'user@example.com',
      identifier: 'user@example.com',
      password: 'secret123',
      challengeToken: 'token',
    })
  })
})
