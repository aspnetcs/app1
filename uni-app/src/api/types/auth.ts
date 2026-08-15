export interface UserInfo {
  userId: string
  phone: string
  email: string
  nickName?: string
  avatar: string
  role?: string
  wxBound: boolean
  createdAt: string
}

export interface LoginResponse {
  token: string
  accessToken: string
  refreshToken: string
  expiresIn: number
  refreshExpiresIn: number
  sessionId: string
  userInfo: UserInfo
  isNewUser: boolean
  sessionType?: string
  guestRecoveryToken?: string
}

export interface GuestLoginRequest {
  deviceId: string
  recoveryToken?: string
  deviceFingerprint?: string
}

export interface SmsSendCodeRequest {
  phone: string
  purpose: string
  challengeToken: string
}

export interface BindEmailSendCodeRequest {
  phone: string
  email: string
  purpose?: string
}

export interface BindEmailRequest {
  phone?: string
  email: string
  code: string
  purpose?: string
}

export interface EmailSendCodeRequest {
  email: string
  purpose: string
  challengeToken: string
}

export interface SmsLoginRequest {
  phone: string
  code: string
  purpose?: string
}

export interface EmailLoginRequest {
  email: string
  code: string
  purpose?: string
}

export interface PasswordLoginRequest {
  phone?: string
  email?: string
  identifier?: string
  password: string
  challengeToken: string
}

export interface RegisterRequest {
  phone?: string
  email?: string
  code: string
  password: string
  purpose?: string
}

export interface SetPasswordRequest {
  password: string
}

export interface ChangePasswordRequest {
  code: string
  newPassword: string
  purpose?: string
}

export interface UpdateEmailRequest {
  phone?: string
  email: string
  code: string
  purpose?: string
}

export interface TokenRefreshRequest {
  refreshToken: string
}

export interface WxLoginRequest {
  code: string
  brand?: string
}

export interface WxPhoneLoginRequest {
  wxLoginCode: string
  phoneCode: string
  brand?: string
}

export interface OAuthProviderItem {
  provider: string
  displayName: string
  icon?: string
  scene: 'user' | 'admin'
}

export interface OAuthProvidersResponse {
  enabled: boolean
  scene: 'user' | 'admin'
  providers: OAuthProviderItem[]
}

export interface OAuthStartRequest {
  redirectUri: string
  scene?: 'user' | 'admin'
  device?: {
    deviceType?: string
    deviceName?: string
    brand?: string
  }
}

export interface OAuthStartResponse {
  provider: string
  scene: 'user' | 'admin'
  authorizeUrl: string
}

export interface OAuthConsumeTicketRequest {
  ticket: string
  scene?: 'user' | 'admin'
}

export interface WsTicketResponse {
  ticket: string
  expiresIn: number
}

export interface CheckIdentifierRequest {
  identifier: string
}

export interface CheckIdentifierResponse {
  type: 'phone' | 'email' | 'unknown'
  hasPassword: boolean
}
