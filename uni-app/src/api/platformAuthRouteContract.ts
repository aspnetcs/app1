export type OAuthScene = 'user' | 'admin'

const PLATFORM_AUTH_BASE_PATH = '/v1/auth'
const PLATFORM_RISK_BASE_PATH = '/v1/risk'

function normalizePath(path: string) {
  return path.trim().replace(/^\/+|\/+$/g, '')
}

function joinPath(basePath: string, path: string) {
  const normalizedPath = normalizePath(path)
  if (!normalizedPath) return basePath
  return `${basePath}/${normalizedPath}`
}

export function buildPlatformAuthPath(path: string) {
  return joinPath(PLATFORM_AUTH_BASE_PATH, path)
}

export function buildPlatformRiskPath(path: string) {
  return joinPath(PLATFORM_RISK_BASE_PATH, path)
}

export const PLATFORM_AUTH_ROUTE_CONTRACT = {
  guest: buildPlatformAuthPath('guest'),
  smsSendCode: buildPlatformAuthPath('sms/send-code'),
  emailSendCode: buildPlatformAuthPath('email/send-code'),
  bindEmailSendCode: buildPlatformAuthPath('bind-email-send-code'),
  bindEmail: buildPlatformAuthPath('bind-email'),
  smsLogin: buildPlatformAuthPath('sms/login'),
  emailLogin: buildPlatformAuthPath('email/login'),
  passwordLogin: buildPlatformAuthPath('password/login'),
  register: buildPlatformAuthPath('register'),
  wxLogin: buildPlatformAuthPath('wx-login'),
  wxPhoneLogin: buildPlatformAuthPath('wx-phone-login'),
  oauthConsumeTicket: buildPlatformAuthPath('oauth/consume-ticket'),
  tokenRefresh: buildPlatformAuthPath('token/refresh'),
  logout: buildPlatformAuthPath('logout'),
  me: buildPlatformAuthPath('me'),
  setPassword: buildPlatformAuthPath('set-password'),
  changePassword: buildPlatformAuthPath('change-password'),
  updateEmail: buildPlatformAuthPath('update-email'),
  deleteAccount: buildPlatformAuthPath('delete-account'),
  checkIdentifier: buildPlatformAuthPath('check-identifier'),
  wsTicket: buildPlatformAuthPath('ws-ticket'),
  buildOAuthProvidersPath(scene: OAuthScene = 'user') {
    return `${buildPlatformAuthPath('oauth/providers')}?scene=${encodeURIComponent(scene)}`
  },
  buildOAuthStartPath(provider: string) {
    return buildPlatformAuthPath(`oauth/${encodeURIComponent(provider)}/start`)
  },
} as const

export const PLATFORM_RISK_ROUTE_CONTRACT = {
  captchaGenerate: buildPlatformRiskPath('captcha/generate'),
  captchaVerify: buildPlatformRiskPath('captcha/verify'),
} as const
