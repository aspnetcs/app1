import { onUnmounted, ref } from 'vue'
import { synthesizeSpeech } from '@/api/audio'
import { useAudioEngine } from '@/utils/useAudioEngine'

type PlayMessagePayload = {
  id: string
  text: string
  model?: string
}

type RuntimeEnvLike = {
  USER_DATA_PATH?: string
}

type MiniProgramWriteFileManager = {
  writeFile(options: {
    filePath: string
    data: string
    encoding: 'base64'
    success: () => void
    fail: (err: unknown) => void
  }): void
}

type MiniProgramAudioContextLike = {
  src: string
  play(): void
}

type WriteMiniProgramTtsTempFileOptions = {
  base64: string
  contentType: string
  fileSystemManager?: MiniProgramWriteFileManager
  uniEnv?: RuntimeEnvLike
  wxEnv?: RuntimeEnvLike
  now?: () => number
}

type PlayMiniProgramTtsAudioOptions = WriteMiniProgramTtsTempFileOptions & {
  audioContext?: MiniProgramAudioContextLike
  onReady?: (filePath: string) => void
}

export function resolveMiniProgramUserDataPath(
  uniEnv?: RuntimeEnvLike,
  wxEnv?: RuntimeEnvLike,
) {
  const uniPath = uniEnv?.USER_DATA_PATH
  if (typeof uniPath === 'string' && uniPath) {
    return uniPath
  }

  const wxPath = wxEnv?.USER_DATA_PATH
  return typeof wxPath === 'string' && wxPath ? wxPath : ''
}

function resolveAudioExtension(contentType: string) {
  const normalized = String(contentType || '').toLowerCase()
  if (normalized.includes('wav')) return 'wav'
  if (normalized.includes('ogg')) return 'ogg'
  if (normalized.includes('aac')) return 'aac'
  return 'mp3'
}

export async function writeMiniProgramTtsTempFile({
  base64,
  contentType,
  fileSystemManager,
  uniEnv,
  wxEnv,
  now = () => Date.now(),
}: WriteMiniProgramTtsTempFileOptions) {
  const userDataPath = resolveMiniProgramUserDataPath(uniEnv, wxEnv)
  if (!userDataPath) {
    throw new Error('missing user data path')
  }
  if (!fileSystemManager) {
    throw new Error('missing file system manager')
  }

  const extension = resolveAudioExtension(contentType)
  const filePath = `${userDataPath}/tts_${now()}.${extension}`

  await new Promise<void>((resolve, reject) => {
    fileSystemManager.writeFile({
      filePath,
      data: base64,
      encoding: 'base64',
      success: () => resolve(),
      fail: (error) => reject(error),
    })
  })

  return filePath
}

export async function playMiniProgramTtsAudio({
  audioContext,
  onReady,
  ...writeOptions
}: PlayMiniProgramTtsAudioOptions) {
  if (!audioContext) {
    throw new Error('missing audio context')
  }

  const filePath = await writeMiniProgramTtsTempFile(writeOptions)
  onReady?.(filePath)
  audioContext.src = filePath
  audioContext.play()
  return filePath
}

export function useTts() {
  const playingId = ref<string | null>(null)
  const loadingId = ref<string | null>(null)
  const audioEngine = useAudioEngine()
  let browserUtterance: SpeechSynthesisUtterance | null = null

  function clearPlaybackState() {
    playingId.value = null
    loadingId.value = null
  }

  function stopSpeechSynthesis() {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      window.speechSynthesis.cancel()
    }
    browserUtterance = null
  }

  function stop() {
    audioEngine.stop()
    stopSpeechSynthesis()
    clearPlaybackState()
  }

  function playWithBrowser(id: string, text: string) {
    if (
      typeof window === 'undefined' ||
      !('speechSynthesis' in window) ||
      typeof SpeechSynthesisUtterance === 'undefined'
    ) {
      return false
    }

    const utterance = new SpeechSynthesisUtterance(text)
    utterance.lang = 'zh-CN'
    utterance.rate = 1
    utterance.onstart = () => {
      playingId.value = id
      loadingId.value = null
    }
    utterance.onend = () => clearPlaybackState()
    utterance.onerror = () => clearPlaybackState()
    browserUtterance = utterance
    window.speechSynthesis.speak(utterance)
    return true
  }

  async function playMessage(payload: PlayMessagePayload) {
    if (playingId.value === payload.id) {
      stop()
      return
    }

    stop()
    loadingId.value = payload.id

    try {
      const response = await synthesizeSpeech({
        input: payload.text,
        ...(payload.model ? { model: payload.model } : {}),
      })
      const base64 = response.data?.audio_base64 || ''
      const contentType = response.data?.content_type || 'audio/mpeg'
      if (!base64) {
        throw new Error('tts empty audio')
      }

      await audioEngine.playBase64(
        base64,
        contentType,
        {
          onEnded: () => {
            if (playingId.value === payload.id) {
              clearPlaybackState()
            }
          },
          onError: () => {
            if (playingId.value === payload.id || loadingId.value === payload.id) {
              clearPlaybackState()
              uni.showToast({ title: '语音播放失败', icon: 'none' })
            }
          },
        },
        'tts',
      )

      playingId.value = payload.id
      loadingId.value = null
      return
    } catch {
      if (playWithBrowser(payload.id, payload.text)) {
        return
      }

      clearPlaybackState()
      uni.showToast({ title: '当前环境不支持语音播放', icon: 'none' })
      throw new Error('tts unavailable')
    }
  }

  onUnmounted(() => {
    stop()
  })

  return {
    playingId,
    loadingId,
    playMessage,
    stop,
  }
}
