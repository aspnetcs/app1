import { resolveMiniProgramUserDataPath } from '@/utils/mpBase64ImageFile'

export type AudioEngineHooks = {
  onEnded?: () => void
  onError?: (error: unknown) => void
}

type AudioEngineSource =
  | {
      kind: 'url'
      url: string
    }
  | {
      kind: 'base64'
      base64: string
      contentType?: string
      filePrefix?: string
    }

type RuntimeEnvLike = {
  USER_DATA_PATH?: string
}

let htmlAudio: HTMLAudioElement | null = null
let innerAudio: UniApp.InnerAudioContext | null = null
let activeObjectUrl: string | null = null
let activeTempFilePath: string | null = null
let activeHooks: AudioEngineHooks = {}
let playToken = 0

function resolveAudioExtension(contentType: string): string {
  const normalized = (contentType || '').trim().toLowerCase()
  if (normalized.includes('wav')) return 'wav'
  if (normalized.includes('ogg')) return 'ogg'
  if (normalized.includes('aac')) return 'aac'
  if (normalized.includes('mpeg')) return 'mp3'
  if (normalized.startsWith('audio/')) return normalized.slice('audio/'.length) || 'mp3'
  return 'mp3'
}

function cleanupTempFile(filePath: string | null) {
  if (!filePath) return
  try {
    uni.getFileSystemManager().unlink({
      filePath,
      complete: () => {},
    })
  } catch {
    // ignore cleanup failures
  }
}

function cleanupActiveSource() {
  if (activeObjectUrl && typeof URL !== 'undefined') {
    URL.revokeObjectURL(activeObjectUrl)
  }
  cleanupTempFile(activeTempFilePath)
  activeObjectUrl = null
  activeTempFilePath = null
}

function dispatchEnded() {
  cleanupActiveSource()
  const hooks = activeHooks
  activeHooks = {}
  hooks.onEnded?.()
}

function dispatchError(error: unknown) {
  cleanupActiveSource()
  const hooks = activeHooks
  activeHooks = {}
  hooks.onError?.(error)
}

function ensureHtmlAudio(): HTMLAudioElement {
  if (htmlAudio) {
    return htmlAudio
  }

  htmlAudio = new Audio()
  htmlAudio.onended = () => {
    dispatchEnded()
  }
  htmlAudio.onerror = () => {
    dispatchError(new Error('audio playback failed'))
  }
  return htmlAudio
}

function ensureInnerAudio(): UniApp.InnerAudioContext {
  if (innerAudio) {
    return innerAudio
  }

  innerAudio = uni.createInnerAudioContext()
  innerAudio.onEnded(() => {
    dispatchEnded()
  })
  innerAudio.onError((error) => {
    dispatchError(error)
  })
  return innerAudio
}

function createObjectUrlFromBase64(base64: string, contentType = 'audio/mpeg'): string {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index)
  }
  const blob = new Blob([bytes], { type: contentType })
  return URL.createObjectURL(blob)
}

async function writeBase64TempAudioFile(
  base64: string,
  contentType: string,
  filePrefix = 'audio',
): Promise<string> {
  const uniEnv = (uni as unknown as { env?: RuntimeEnvLike }).env
  const wxEnv =
    typeof wx !== 'undefined'
      ? ((wx as unknown as { env?: RuntimeEnvLike }).env ?? undefined)
      : undefined
  const userDataPath = resolveMiniProgramUserDataPath(uniEnv, wxEnv)
  if (!userDataPath) {
    throw new Error('missing user data path')
  }

  const extension = resolveAudioExtension(contentType)
  const filePath = `${userDataPath}/${filePrefix}_${Date.now()}_${Math.random().toString(16).slice(2)}.${extension}`

  await new Promise<void>((resolve, reject) => {
    uni.getFileSystemManager().writeFile({
      filePath,
      data: base64,
      encoding: 'base64',
      success: () => resolve(),
      fail: (error) => reject(error),
    })
  })

  activeTempFilePath = filePath
  return filePath
}

async function resolveSourceUrl(source: AudioEngineSource): Promise<string> {
  if (source.kind === 'url') {
    return source.url
  }

  const contentType = source.contentType || 'audio/mpeg'

  // #ifdef H5
  if (typeof URL !== 'undefined') {
    const objectUrl = createObjectUrlFromBase64(source.base64, contentType)
    activeObjectUrl = objectUrl
    return objectUrl
  }
  // #endif

  return writeBase64TempAudioFile(source.base64, contentType, source.filePrefix)
}

async function playSource(source: AudioEngineSource, hooks: AudioEngineHooks = {}) {
  const currentToken = ++playToken
  stopAudioEngine()
  activeHooks = hooks

  const src = await resolveSourceUrl(source)
  if (currentToken !== playToken) {
    cleanupActiveSource()
    return
  }

  // #ifdef H5
  if (typeof Audio !== 'undefined') {
    const audio = ensureHtmlAudio()
    audio.src = src
    try {
      await audio.play()
    } catch (error) {
      dispatchError(error)
      throw error
    }
    return
  }
  // #endif

  const audio = ensureInnerAudio()
  audio.src = src
  audio.play()
}

export function stopAudioEngine() {
  playToken += 1

  if (htmlAudio) {
    htmlAudio.pause()
    htmlAudio.currentTime = 0
  }

  if (innerAudio) {
    try {
      innerAudio.stop()
    } catch {
      // ignore stop failures
    }
  }

  cleanupActiveSource()
  activeHooks = {}
}

export function destroyAudioEngine() {
  stopAudioEngine()

  if (htmlAudio) {
    htmlAudio.src = ''
    htmlAudio.onended = null
    htmlAudio.onerror = null
    htmlAudio = null
  }

  if (innerAudio) {
    try {
      innerAudio.destroy()
    } catch {
      // ignore destroy failures
    }
    innerAudio = null
  }
}

export function useAudioEngine() {
  return {
    playUrl(url: string, hooks?: AudioEngineHooks) {
      return playSource({ kind: 'url', url }, hooks)
    },
    playBase64(base64: string, contentType?: string, hooks?: AudioEngineHooks, filePrefix?: string) {
      return playSource(
        {
          kind: 'base64',
          base64,
          contentType,
          filePrefix,
        },
        hooks,
      )
    },
    stop: stopAudioEngine,
    destroy: destroyAudioEngine,
  }
}
