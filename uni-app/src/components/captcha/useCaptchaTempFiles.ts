import { ref } from 'vue'

type UnlinkFs = { unlink(opts: { filePath: string; fail: () => void }): void }
type WriteFs = {
  writeFile(opts: {
    filePath: string
    data: string
    encoding: string
    success: () => void
    fail: (err: unknown) => void
  }): void
}

export function useCaptchaTempFiles() {
  const tempFiles = ref<string[]>([])

  function cleanupTempFiles() {
    const uniExt = uni as unknown as { getFileSystemManager?(): UnlinkFs }
    const fs = uniExt.getFileSystemManager?.()
    if (!fs) {
      tempFiles.value = []
      return
    }
    const files = tempFiles.value.slice()
    tempFiles.value = []
    files.forEach((path) => {
      try {
        fs.unlink({ filePath: path, fail: () => {} })
      } catch {}
    })
  }

  async function resolveDataUrlToTempFile(dataUrl: string, prefix: string): Promise<string> {
    if (!dataUrl || typeof dataUrl !== 'string') return ''
    if (!dataUrl.startsWith('data:image/') || !dataUrl.includes(';base64,')) return dataUrl

    const uniFs = uni as unknown as { getFileSystemManager?(): WriteFs }
    const fs = uniFs.getFileSystemManager?.()
    const wxGlobal = typeof wx !== 'undefined' ? wx : undefined
    const userPath: string | undefined = (wxGlobal as unknown as { env?: { USER_DATA_PATH?: string } } | undefined)?.env?.USER_DATA_PATH
    if (!fs || !userPath) return dataUrl

    try {
      const parts = dataUrl.split(';base64,')
      if (parts.length !== 2) return dataUrl
      const header = parts[0]
      const base64 = parts[1]
      const mime = header.replace(/^data:image\//, '')
      const ext = mime === 'jpeg' ? 'jpg' : mime === 'svg+xml' ? 'svg' : mime
      const filePath = `${userPath}/captcha_${prefix}_${Date.now()}_${Math.random().toString(16).slice(2)}.${ext}`

      await new Promise<void>((resolve, reject) => {
        fs.writeFile({
          filePath,
          data: base64,
          encoding: 'base64',
          success: () => resolve(),
          fail: (err: unknown) => reject(err),
        })
      })

      tempFiles.value.push(filePath)
      return filePath
    } catch {
      return dataUrl
    }
  }

  return {
    cleanupTempFiles,
    resolveDataUrlToTempFile,
  }
}
