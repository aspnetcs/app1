/**
 * 风控/验证码 API
 */
import { http } from './http'
import { PLATFORM_RISK_ROUTE_CONTRACT } from './platformAuthRouteContract'
import type { CaptchaData, CaptchaVerifyRequest, CaptchaVerifyResponse } from './types'

// 生成验证题
export const generateCaptcha = () =>
  http.get<CaptchaData>(PLATFORM_RISK_ROUTE_CONTRACT.captchaGenerate)

// 验证答案
export const verifyCaptcha = (data: CaptchaVerifyRequest) =>
  http.post<CaptchaVerifyResponse>(PLATFORM_RISK_ROUTE_CONTRACT.captchaVerify, data)
