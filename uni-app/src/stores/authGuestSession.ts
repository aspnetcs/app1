import { getStorage, setStorage } from '@/utils/storage'

export const GUEST_STORAGE_KEY = 'guestSession'
export const GUEST_RECOVERY_TOKEN_STORAGE_KEY = 'guestRecoveryToken'

const GUEST_DEVICE_ID_STORAGE_KEY = 'guestDeviceId'

type GuestFingerprintSystemInfo = {
  platform?: unknown
  brand?: unknown
  model?: unknown
  deviceModel?: unknown
  system?: unknown
  deviceType?: unknown
  windowWidth?: unknown
  windowHeight?: unknown
  screenWidth?: unknown
  screenHeight?: unknown
  pixelRatio?: unknown
  language?: unknown
}

let guestDeviceFingerprint: string | null = null

function createGuestDeviceId(): string {
  const randomUuid = globalThis.crypto?.randomUUID?.()
  if (randomUuid) {
    return randomUuid
  }
  return `guest-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`
}

export function getGuestDeviceId(): string {
  const existing = getStorage<string>(GUEST_DEVICE_ID_STORAGE_KEY)
  if (existing) {
    return existing
  }
  const nextId = createGuestDeviceId()
  setStorage(GUEST_DEVICE_ID_STORAGE_KEY, nextId)
  return nextId
}

function fnv1aHex(input: string): string {
  let hash = 0x811c9dc5
  for (let i = 0; i < input.length; i += 1) {
    hash ^= input.charCodeAt(i)
    hash = Math.imul(hash, 0x01000193)
  }
  return (hash >>> 0).toString(16).padStart(8, '0')
}

function normalizeFingerprintPart(value: unknown): string {
  if (value == null) return ''
  if (Array.isArray(value)) return value.map(item => normalizeFingerprintPart(item)).join(',')
  return String(value).trim().toLowerCase()
}

function getCanvasFingerprintPart(): string {
  const doc = typeof document !== 'undefined' ? document : undefined
  if (!doc?.createElement) return ''
  try {
    const canvas = doc.createElement('canvas') as HTMLCanvasElement
    canvas.width = 240
    canvas.height = 48
    const ctx = canvas.getContext('2d')
    if (!ctx) return ''
    ctx.textBaseline = 'top'
    ctx.font = '16px sans-serif'
    ctx.fillStyle = '#123456'
    ctx.fillRect(0, 0, 240, 48)
    ctx.fillStyle = '#f5f5f5'
    ctx.fillText('guest-fingerprint-v1', 8, 8)
    return canvas.toDataURL()
  } catch {
    return ''
  }
}

function getWebGlFingerprintPart(): string {
  const doc = typeof document !== 'undefined' ? document : undefined
  if (!doc?.createElement) return ''
  try {
    const canvas = doc.createElement('canvas') as HTMLCanvasElement
    const gl = (canvas.getContext('webgl') || canvas.getContext('experimental-webgl')) as WebGLRenderingContext | null
    if (!gl) return ''
    const debugInfo = gl.getExtension('WEBGL_debug_renderer_info') as { UNMASKED_VENDOR_WEBGL: number; UNMASKED_RENDERER_WEBGL: number } | null
    const vendor = debugInfo ? gl.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL) : gl.getParameter(gl.VENDOR)
    const renderer = debugInfo ? gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER)
    return `${normalizeFingerprintPart(vendor)}|${normalizeFingerprintPart(renderer)}`
  } catch {
    return ''
  }
}

export function getGuestDeviceFingerprint(): string {
  if (guestDeviceFingerprint) {
    return guestDeviceFingerprint
  }

  let systemInfo: GuestFingerprintSystemInfo = {}
  try {
    systemInfo = (uni.getSystemInfoSync() || {}) as unknown as GuestFingerprintSystemInfo
  } catch {
    systemInfo = {}
  }

  const nav = typeof navigator !== 'undefined' ? navigator : undefined
  const screenInfo = typeof screen !== 'undefined' ? screen : undefined
  const timeZone = (() => {
    try {
      return Intl.DateTimeFormat().resolvedOptions().timeZone || ''
    } catch {
      return ''
    }
  })()

  const raw = [
    'guest-fingerprint-v1',
    systemInfo.platform,
    systemInfo.brand,
    systemInfo.model,
    systemInfo.deviceModel,
    systemInfo.system,
    systemInfo.deviceType,
    systemInfo.windowWidth,
    systemInfo.windowHeight,
    systemInfo.screenWidth,
    systemInfo.screenHeight,
    systemInfo.pixelRatio,
    systemInfo.language,
    nav?.userAgent,
    nav?.language,
    nav?.languages,
    nav?.platform,
    nav?.vendor,
    nav?.hardwareConcurrency,
    nav?.maxTouchPoints,
    screenInfo?.width,
    screenInfo?.height,
    screenInfo?.colorDepth,
    timeZone,
    getCanvasFingerprintPart(),
    getWebGlFingerprintPart(),
  ].map(part => normalizeFingerprintPart(part)).join('|')

  guestDeviceFingerprint = `guest-fp-${fnv1aHex(raw)}${fnv1aHex([...raw].reverse().join(''))}`
  return guestDeviceFingerprint
}
