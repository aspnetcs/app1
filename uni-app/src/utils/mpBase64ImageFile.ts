export type MiniProgramEnv = { USER_DATA_PATH?: string }

export type MiniProgramFileSystemManager = {
  writeFile(opts: {
    filePath: string
    data: string
    encoding: 'base64'
    success: () => void
    fail: (err: unknown) => void
  }): void
  unlink?(opts: {
    filePath: string
    success?: () => void
    fail?: (err: unknown) => void
  }): void
}

export function resolveMiniProgramUserDataPath(
  uniEnv: MiniProgramEnv | undefined,
  wxEnv: MiniProgramEnv | undefined,
): string {
  // Project rule: prefer wx.env.USER_DATA_PATH, accept uni.env.USER_DATA_PATH fallback.
  return (wxEnv?.USER_DATA_PATH || uniEnv?.USER_DATA_PATH || '').trim()
}

function resolveImageExtension(mime: string): string {
  const normalized = (mime || '').trim().toLowerCase()
  if (normalized === 'image/jpeg') return 'jpg'
  if (normalized === 'image/png') return 'png'
  if (normalized === 'image/webp') return 'webp'
  if (normalized === 'image/svg+xml') return 'svg'
  if (normalized.startsWith('image/')) return normalized.slice('image/'.length) || 'png'
  return 'png'
}

export function parseBase64ImageDataUrl(
  dataUrl: string,
): { mime: string; base64: string } | null {
  if (!dataUrl || typeof dataUrl !== 'string') return null
  const commaIndex = dataUrl.indexOf(',')
  if (commaIndex <= 0) return null

  const header = dataUrl.slice(0, commaIndex)
  const payload = dataUrl.slice(commaIndex + 1)
  if (!header.startsWith('data:')) return null
  if (!header.includes(';base64')) return null

  const mimePart = header.slice('data:'.length, header.indexOf(';')).trim()
  if (!mimePart.startsWith('image/')) return null

  const base64 = payload.trim()
  if (!base64) return null
  return { mime: mimePart, base64 }
}

export async function writeMiniProgramBase64ImageFile(options: {
  base64: string
  mime: string
  fileSystemManager: MiniProgramFileSystemManager
  uniEnv?: MiniProgramEnv
  wxEnv?: MiniProgramEnv
  userDataPath?: string
  prefix?: string
  now?: () => number
  random?: () => string
  seq?: number
}): Promise<string> {
  const now = options.now || (() => Date.now())
  const random =
    options.random || (() => Math.random().toString(16).slice(2))
  const seq = typeof options.seq === 'number' ? options.seq : 0

  const userDataPath =
    options.userDataPath ||
    resolveMiniProgramUserDataPath(options.uniEnv, options.wxEnv)
  if (!userDataPath) throw new Error('missing user data path')

  const ext = resolveImageExtension(options.mime)
  const prefix = (options.prefix || 'img').trim() || 'img'
  const filePath = `${userDataPath}/${prefix}_${now()}_${seq}_${random()}.${ext}`

  await new Promise<void>((resolve, reject) => {
    options.fileSystemManager.writeFile({
      filePath,
      data: options.base64,
      encoding: 'base64',
      success: () => resolve(),
      fail: (err: unknown) => reject(err),
    })
  })

  return filePath
}

export type MpBase64ImageFileManager = {
  writeBase64ToTempFilePath: (base64: string, mime: string, prefix?: string) => Promise<string>
  resolveDataUrlToTempFilePath: (dataUrl: string, prefix?: string) => Promise<string>
  cleanupTempFiles: () => void
  listTempFiles: () => string[]
}

export function createMpBase64ImageFileManager(options: {
  fileSystemManager?: MiniProgramFileSystemManager | null
  getFileSystemManager?: () => MiniProgramFileSystemManager | null | undefined
  uniEnv?: MiniProgramEnv
  wxEnv?: MiniProgramEnv
  getUniEnv?: () => MiniProgramEnv | undefined
  getWxEnv?: () => MiniProgramEnv | undefined
  now?: () => number
  random?: () => string
} = {}): MpBase64ImageFileManager {
  const tempFiles: string[] = []
  let seq = 0

  function getFs(): MiniProgramFileSystemManager | null {
    return options.fileSystemManager || options.getFileSystemManager?.() || null
  }

  function getUniEnv(): MiniProgramEnv | undefined {
    return options.uniEnv || options.getUniEnv?.()
  }

  function getWxEnv(): MiniProgramEnv | undefined {
    return options.wxEnv || options.getWxEnv?.()
  }

  async function writeBase64ToTempFilePath(
    base64: string,
    mime: string,
    prefix = 'img',
  ): Promise<string> {
    const fs = getFs()
    if (!fs) return ''

    try {
      const filePath = await writeMiniProgramBase64ImageFile({
        base64,
        mime,
        fileSystemManager: fs,
        uniEnv: getUniEnv(),
        wxEnv: getWxEnv(),
        prefix,
        now: options.now,
        random: options.random,
        seq: seq++,
      })
      tempFiles.push(filePath)
      return filePath
    } catch {
      return ''
    }
  }

  async function resolveDataUrlToTempFilePath(
    dataUrl: string,
    prefix = 'img',
  ): Promise<string> {
    const parsed = parseBase64ImageDataUrl(dataUrl)
    if (!parsed) return dataUrl
    const resolved = await writeBase64ToTempFilePath(parsed.base64, parsed.mime, prefix)
    return resolved || dataUrl
  }

  function cleanupTempFiles(): void {
    const fs = getFs()
    const files = tempFiles.slice()
    tempFiles.length = 0
    if (!fs?.unlink) return

    files.forEach((filePath) => {
      try {
        fs.unlink?.({
          filePath,
          fail: () => {},
        })
      } catch {}
    })
  }

  function listTempFiles(): string[] {
    return tempFiles.slice()
  }

  return {
    writeBase64ToTempFilePath,
    resolveDataUrlToTempFilePath,
    cleanupTempFiles,
    listTempFiles,
  }
}

