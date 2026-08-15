import { http } from './http'
import { PLATFORM_MEDIA_ROUTE_CONTRACT } from './platformMediaRouteContract'

export interface SpeechRequest {
  input: string
  model?: string
  voice?: string
  response_format?: string
  speed?: number
}

export interface SpeechResponse {
  audio_base64: string
  content_type: string
  model: string
  voice: string
}

export const synthesizeSpeech = (data: SpeechRequest) =>
  http.post<SpeechResponse>(PLATFORM_MEDIA_ROUTE_CONTRACT.audioSpeech, data, { auth: true, silent: true })
