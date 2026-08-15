import { http } from './http'
import { PLATFORM_TEXT_TRANSFORM_ROUTE_CONTRACT } from './platformTextTransformRouteContract'

export interface TranslationRequest {
  content: string
  targetLanguage?: string
  model?: string
}

export interface TranslationResponse {
  translatedText: string
  targetLanguage: string
}

export const translateMessageApi = (data: TranslationRequest) =>
  http.post<TranslationResponse>(PLATFORM_TEXT_TRANSFORM_ROUTE_CONTRACT.translationMessages, data, { auth: true, silent: true })
