import type { PasswordLoginRequest } from '@/api/types'
import { isValidEmail, isValidPhone } from '@/utils/format'

export type LoginIdentifierType = 'phone' | 'email' | 'identifier'

export function isCodeLoginIdentifierValid(value: string): boolean {
  const normalized = value.trim()
  return isValidPhone(normalized) || isValidEmail(normalized)
}

export function isPasswordLoginIdentifierValid(value: string): boolean {
  return value.trim().length > 0
}

export function inferLoginIdentifierType(value: string): LoginIdentifierType {
  const normalized = value.trim()
  if (isValidPhone(normalized)) return 'phone'
  if (isValidEmail(normalized)) return 'email'
  return 'identifier'
}

export function buildPasswordLoginRequest(
  identifier: string,
  password: string,
  challengeToken: string,
): PasswordLoginRequest {
  const normalized = identifier.trim()
  const request: PasswordLoginRequest = {
    identifier: normalized,
    password,
    challengeToken,
  }
  const type = inferLoginIdentifierType(normalized)
  if (type === 'phone') {
    request.phone = normalized
  } else if (type === 'email') {
    request.email = normalized
  }
  return request
}
