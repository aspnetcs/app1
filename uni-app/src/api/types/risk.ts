export type CaptchaType = 'math' | 'slider' | 'text'

export interface CaptchaMathData {
  captchaId: string
  type: 'math'
  question?: string
  mathImage?: string
}

export interface CaptchaSliderData {
  captchaId: string
  type: 'slider'
  backgroundImage: string
  sliderImage: string
  pieceY: number
  bgWidth: number
  bgHeight: number
  sliderWidth: number
  sliderHeight: number
}

export interface CaptchaTextData {
  captchaId: string
  type: 'text'
  backgroundImage: string
  targetChars: string[]
  bgWidth: number
  bgHeight: number
}

export type CaptchaData = CaptchaMathData | CaptchaSliderData | CaptchaTextData

export type CaptchaVerifyData =
  | { answer: number }
  | { movePercent: number; tracks: Array<{ x: number; y: number }>; duration: number }
  | { points: Array<{ px: number; py: number }> }

export interface CaptchaVerifyRequest {
  captchaId: string
  data: CaptchaVerifyData
}

export interface CaptchaVerifyResponse {
  token: string
}
