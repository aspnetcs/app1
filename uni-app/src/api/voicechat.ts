import { config } from '@/config'
import { http } from './http'
import { buildVoiceChatPipelineUrl, PLATFORM_VOICE_CHAT_ROUTE_CONTRACT } from './platformVoiceChatRouteContract'
import { getStorage } from '@/utils/storage'

export interface VoiceChatConfig {
  enabled: boolean
  maxDurationSeconds: number
}

export interface VoiceChatResult {
  transcript: string
  aiResponse: string
  audioBase64?: string
  contentType?: string
}

export function getVoiceChatConfig() {
  return http.get<VoiceChatConfig>(PLATFORM_VOICE_CHAT_ROUTE_CONTRACT.config, undefined, { auth: true, silent: true })
}

export function voiceChatPipeline(
  audioFilePath: string,
  model?: string,
  history?: Array<{ role: string; content: string }>
): Promise<VoiceChatResult> {
  return new Promise((resolve, reject) => {
    // Token is persisted via `setStorage('token', accessToken)` which JSON-encodes strings.
    // Some older code paths may still persist a raw string token, so keep a defensive fallback.
    const STORAGE_MISS = Symbol('storage_miss')
    const storedToken = getStorage<unknown | typeof STORAGE_MISS>('token', STORAGE_MISS)
    const token =
      storedToken !== STORAGE_MISS
        ? typeof storedToken === 'string'
          ? storedToken.trim()
          : ''
        : (() => {
            try {
              const raw = uni.getStorageSync('token')
              if (typeof raw !== 'string') return ''
              const trimmed = raw.trim()
              if (!trimmed) return ''
              return trimmed
            } catch {
              return ''
            }
          })()
    uni.uploadFile({
      url: buildVoiceChatPipelineUrl(config.apiBaseUrl),
      filePath: audioFilePath,
      name: 'audio',
      formData: {
        ...(model ? { model } : {}),
        ...(history ? { history: JSON.stringify(history) } : {}),
      },
      header: token ? { Authorization: 'Bearer ' + token } : {},
      fail: reject,
      success: (res) => {
        try {
          const payload = JSON.parse(res.data)
          if (payload?.code === 0 || payload?.code === 200) {
            resolve(payload.data as VoiceChatResult)
            return
          }
          reject(new Error(payload?.message || 'voice chat failed'))
        } catch (e) {
          reject(e)
        }
      },
    })
  })
}
