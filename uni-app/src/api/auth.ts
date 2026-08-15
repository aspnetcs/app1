import { http } from './http'
import { PLATFORM_AUTH_ROUTE_CONTRACT } from './platformAuthRouteContract'
import type {
  LoginResponse,
  GuestLoginRequest,
  SmsSendCodeRequest,
  EmailSendCodeRequest,
  BindEmailSendCodeRequest,
  BindEmailRequest,
  SmsLoginRequest,
  EmailLoginRequest,
  PasswordLoginRequest,
  RegisterRequest,
  SetPasswordRequest,
  ChangePasswordRequest,
  UpdateEmailRequest,
  TokenRefreshRequest,
  WxLoginRequest,
  WxPhoneLoginRequest,
  OAuthProvidersResponse,
  OAuthStartRequest,
  OAuthStartResponse,
  OAuthConsumeTicketRequest,
  WsTicketResponse,
  UserInfo,
  CheckIdentifierRequest,
  CheckIdentifierResponse,
} from './types'

export const sendSmsCode = (data: SmsSendCodeRequest) =>
  http.post(PLATFORM_AUTH_ROUTE_CONTRACT.smsSendCode, data)

export const guestLogin = (data: GuestLoginRequest) =>
  http.post<LoginResponse>(PLATFORM_AUTH_ROUTE_CONTRACT.guest, data)

export const sendEmailCode = (data: EmailSendCodeRequest) =>
  http.post(PLATFORM_AUTH_ROUTE_CONTRACT.emailSendCode, data)

export const bindEmailSendCode = (data: BindEmailSendCodeRequest) =>
  http.post(PLATFORM_AUTH_ROUTE_CONTRACT.bindEmailSendCode, data, { auth: true })

export const bindEmail = (data: BindEmailRequest) =>
  http.post(PLATFORM_AUTH_ROUTE_CONTRACT.bindEmail, data, { auth: true })

export const smsLogin = (data: SmsLoginRequest) =>
  http.post<LoginResponse>(PLATFORM_AUTH_ROUTE_CONTRACT.smsLogin, data)

export const emailLogin = (data: EmailLoginRequest) =>
  http.post<LoginResponse>(PLATFORM_AUTH_ROUTE_CONTRACT.emailLogin, data)

export const passwordLogin = (data: PasswordLoginRequest) =>
  http.post<LoginResponse>(PLATFORM_AUTH_ROUTE_CONTRACT.passwordLogin, data)

export const register = (data: RegisterRequest) =>
  http.post<LoginResponse>(PLATFORM_AUTH_ROUTE_CONTRACT.register, data)

export const wxLogin = (data: WxLoginRequest) =>
  http.post<LoginResponse>(PLATFORM_AUTH_ROUTE_CONTRACT.wxLogin, data)

export const wxPhoneLogin = (data: WxPhoneLoginRequest) =>
  http.post<LoginResponse>(PLATFORM_AUTH_ROUTE_CONTRACT.wxPhoneLogin, data)

export const getOAuthProviders = (scene: 'user' | 'admin' = 'user') =>
  http.get<OAuthProvidersResponse>(PLATFORM_AUTH_ROUTE_CONTRACT.buildOAuthProvidersPath(scene))

export const startOAuthLogin = (provider: string, data: OAuthStartRequest) =>
  http.post<OAuthStartResponse>(PLATFORM_AUTH_ROUTE_CONTRACT.buildOAuthStartPath(provider), data)

export const consumeOAuthTicket = (data: OAuthConsumeTicketRequest) =>
  http.post<LoginResponse>(PLATFORM_AUTH_ROUTE_CONTRACT.oauthConsumeTicket, data)

export const refreshToken = (data: TokenRefreshRequest) =>
  http.post<LoginResponse>(PLATFORM_AUTH_ROUTE_CONTRACT.tokenRefresh, data, { silent: true })

export const logout = (data: { refreshToken: string }) =>
  http.post(PLATFORM_AUTH_ROUTE_CONTRACT.logout, data)

export const getMe = () =>
  http.get<UserInfo>(PLATFORM_AUTH_ROUTE_CONTRACT.me, undefined, { auth: true })

export const setPassword = (data: SetPasswordRequest) =>
  http.post(PLATFORM_AUTH_ROUTE_CONTRACT.setPassword, data, { auth: true })

export const changePassword = (data: ChangePasswordRequest) =>
  http.post(PLATFORM_AUTH_ROUTE_CONTRACT.changePassword, data, { auth: true })

export const updateEmail = (data: UpdateEmailRequest) =>
  http.post(PLATFORM_AUTH_ROUTE_CONTRACT.updateEmail, data, { auth: true })

export const deleteAccount = () =>
  http.post(PLATFORM_AUTH_ROUTE_CONTRACT.deleteAccount, {}, { auth: true })

export const getWsTicket = () =>
  http.post<WsTicketResponse>(PLATFORM_AUTH_ROUTE_CONTRACT.wsTicket, {}, { auth: true })

export const checkIdentifier = (data: CheckIdentifierRequest) =>
  http.post<CheckIdentifierResponse>(PLATFORM_AUTH_ROUTE_CONTRACT.checkIdentifier, data)
