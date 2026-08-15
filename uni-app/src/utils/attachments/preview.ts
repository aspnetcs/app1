import type { FileKind } from '@/api/types/files'
import { inferFileExtension, inferFileKind } from './fileKind'

type PreviewInput = {
  url: string
  kind?: FileKind
  mimeType?: string | null
  filename?: string | null
}

function showToast(message: string) {
  try {
    uni.showToast({ title: message, icon: 'none' })
  } catch {
    // ignore
  }
}

function isLikelyImage(input: PreviewInput): boolean {
  if (input.kind === 'image') return true
  const inferred = inferFileKind({ mimeType: input.mimeType, filename: input.filename })
  return inferred === 'image'
}

function downloadToTempFile(url: string): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url,
      success: (res) => {
        const statusCode = Number(res.statusCode || 0)
        if (statusCode < 200 || statusCode >= 300) {
          reject(new Error(`HTTP ${statusCode}`))
          return
        }
        const tempFilePath = (res as unknown as { tempFilePath?: unknown }).tempFilePath
        const path = typeof tempFilePath === 'string' ? tempFilePath.trim() : ''
        if (!path) {
          reject(new Error('missing tempFilePath'))
          return
        }
        resolve(path)
      },
      fail: (error: unknown) => reject(error),
    })
  })
}

function openDocument(filePath: string, filename?: string | null): Promise<void> {
  return new Promise((resolve, reject) => {
    const ext = inferFileExtension(filename)
    const options: Record<string, unknown> = {
      filePath,
      showMenu: true,
    }
    if (ext) {
      options.fileType = ext
    }
    uni.openDocument({
      ...(options as any),
      success: () => resolve(),
      fail: (error: unknown) => reject(error),
    })
  })
}

export async function previewRemoteFile(input: PreviewInput): Promise<void> {
  const url = (input.url || '').trim()
  if (!url) {
    showToast('Missing file URL')
    return
  }

  if (isLikelyImage(input)) {
    uni.previewImage({ urls: [url], current: url })
    return
  }

  if (typeof uni.openDocument !== 'function') {
    showToast('Preview is not supported in this environment')
    return
  }

  try {
    const tempFilePath = await downloadToTempFile(url)
    await openDocument(tempFilePath, input.filename)
  } catch (err) {
    void err
    showToast('Failed to preview file')
  }
}
