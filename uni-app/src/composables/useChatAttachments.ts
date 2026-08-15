import { ref, computed } from 'vue'
import type { ChatAttachment, ChatAttachmentKind } from '@/api/types/chat'
import type { AttachmentChipItem } from '@/components/attachments/AttachmentChip.vue'
import { uploadFileToLibrary, getFileUrl, getFileRecognition } from '@/api/files'
import { inferFileKind } from '@/utils/attachments/fileKind'

export interface PendingAttachment extends ChatAttachment {
  url?: string
}

type PickedUploadSource = {
  filePath?: string
  webFile?: File
}

const DOCUMENT_ACCEPT = [
  '.pdf',
  '.doc',
  '.docx',
  '.xls',
  '.xlsx',
  '.ppt',
  '.pptx',
  '.txt',
  '.md',
  '.markdown',
  '.csv',
  '.json',
  '.html',
  '.htm',
  '.xml',
  '.rtf',
].join(',')

function showAttachmentError(error: unknown, fallback = '上传失败，请稍后重试') {
  const message = error instanceof Error
    ? String(error.message || '').trim()
    : ''
  uni.showToast({ title: message || fallback, icon: 'none', duration: 2200 })
}

export function useChatAttachments() {
  const pending = ref<PendingAttachment[]>([])
  const uploading = ref(false)

  const chipItems = computed<AttachmentChipItem[]>(() =>
    pending.value.map(a => ({
      fileId: a.fileId,
      originalName: a.originalName || 'file',
      kind: a.kind as string as AttachmentChipItem['kind'],
      mimeType: a.mimeType,
      url: a.url,
      recognitionStatus: a.recognitionStatus,
      recognitionType: a.recognitionType,
      recognitionText: a.recognitionText,
      recognitionMessage: a.recognitionMessage,
    })),
  )

  const hasAttachments = computed(() => pending.value.length > 0)
  const hasImageAttachments = computed(() => pending.value.some(a => a.kind === 'image'))

  function removeAttachment(fileId: string) {
    pending.value = pending.value.filter(a => a.fileId !== fileId)
  }

  function clearAttachments() {
    pending.value = []
  }

  function toRequestAttachments(): ChatAttachment[] {
    return pending.value.map(({
      fileId,
      kind,
      originalName,
      mimeType,
      url,
      recognitionStatus,
      recognitionType,
      recognitionText,
      recognitionMessage,
    }) => ({
      fileId,
      kind,
      originalName,
      mimeType,
      url,
      recognitionStatus,
      recognitionType,
      recognitionText,
      recognitionMessage,
    }))
  }

  async function pickAndUploadImage() {
    try {
      const sources = await chooseImages()
      if (!sources.length) return
      await uploadPickedSources(sources)
    } catch (error) {
      showAttachmentError(error)
    }
  }

  async function pickAndUploadDocument() {
    try {
      const picked = await chooseFiles(DOCUMENT_ACCEPT)
      if (!picked.length) return
      await uploadPickedSources(picked)
    } catch (error) {
      showAttachmentError(error)
    }
  }

  async function pickAndUploadAttachment() {
    try {
      const picked = await chooseAttachment()
      if (!picked.length) return
      await uploadPickedSources(picked)
    } catch (error) {
      showAttachmentError(error)
    }
  }

  async function uploadWebFiles(files: File[]) {
    try {
      const picked = files
        .filter((file) => file instanceof File)
        .map((webFile) => ({ webFile }))
      if (picked.length === 0) return
      await uploadPickedSources(picked)
    } catch (error) {
      showAttachmentError(error)
    }
  }

  async function uploadPickedSources(sources: PickedUploadSource[]) {
    if (sources.length === 0) return
    uploading.value = true
    try {
      const uploaded: PendingAttachment[] = []
      for (const source of sources) {
        uploaded.push(await buildPendingAttachmentFromSource(source))
      }
      if (uploaded.length > 0) {
        pending.value = [...pending.value, ...uploaded]
      }
    } finally {
      uploading.value = false
    }
  }

  return {
    pending,
    uploading,
    chipItems,
    hasAttachments,
    hasImageAttachments,
    removeAttachment,
    clearAttachments,
    toRequestAttachments,
    pickAndUploadAttachment,
    uploadWebFiles,
    pickAndUploadImage,
    pickAndUploadDocument,
  }
}

function chooseImages(): Promise<PickedUploadSource[]> {
  return new Promise((resolve) => {
    uni.chooseImage({
      count: 4,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const paths = Array.isArray(res.tempFilePaths)
          ? res.tempFilePaths
          : res.tempFilePaths
            ? [res.tempFilePaths]
            : []
        resolve(paths.filter(Boolean).map((filePath: string) => ({ filePath })))
      },
      fail: () => resolve([]),
    })
  })
}

function chooseFiles(accept?: string): Promise<PickedUploadSource[]> {
  // #ifndef MP-WEIXIN
  return openWebFilePicker(accept)
  // #endif
  // #ifdef MP-WEIXIN
  return new Promise((resolve) => {
    uni.chooseMessageFile({
      count: 6,
      type: 'file',
      success: (res) => {
        const files = (res.tempFiles || [])
          .map((file) => file?.path || '')
          .filter(Boolean)
          .map((filePath) => ({ filePath }))
        resolve(files)
      },
      fail: () => resolve([]),
    })
  })
  // #endif
}

function chooseAttachment(): Promise<PickedUploadSource[]> {
  // #ifndef MP-WEIXIN
  return openWebFilePicker()
  // #endif
  // #ifdef MP-WEIXIN
  return new Promise((resolve) => {
    uni.chooseMessageFile({
      count: 6,
      type: 'all',
      success: (res) => {
        const files = (res.tempFiles || [])
          .map((file) => file?.path || '')
          .filter(Boolean)
          .map((filePath) => ({ filePath }))
        resolve(files)
      },
      fail: () => resolve([]),
    })
  })
  // #endif
}

function resolveAttachmentKind(
  kind: ChatAttachmentKind | 'audio' | 'video' | 'other' | undefined,
  mimeType: string | null | undefined,
  filename: string | null | undefined,
): ChatAttachmentKind {
  if (kind === 'image' || kind === 'document') {
    return kind
  }
  const inferred = inferFileKind({ mimeType, filename })
  return inferred === 'image' ? 'image' : 'document'
}

function fileNameFromPath(filePath: string | null | undefined): string {
  const text = String(filePath || '').trim()
  if (!text) return ''
  return text.split(/[\\/]/).filter(Boolean).pop() || ''
}

function truncateRecognitionText(text: string | null | undefined): string | undefined {
  const value = String(text || '').trim()
  if (!value) return undefined
  return value.length > 1200 ? `${value.slice(0, 1200)}...` : value
}

async function resolvePreviewUrl(fileId: string, fallbackUrl = '') {
  if (fallbackUrl) return fallbackUrl
  try {
    const urlRes = await getFileUrl(fileId, 'preview')
    return urlRes.data?.url || ''
  } catch {
    return ''
  }
}

async function buildPendingAttachmentFromSource(picked: PickedUploadSource): Promise<PendingAttachment> {
  const result = await uploadFileToLibrary({
    filePath: picked.filePath,
    webFile: picked.webFile,
    filename: picked.webFile?.name,
  })
  if (!result.data?.fileId) throw new Error('upload failed')

  const fileId = result.data.fileId
  const name = result.data.originalName || picked.webFile?.name || fileNameFromPath(picked.filePath) || 'file'
  const mime = result.data.mimeType || picked.webFile?.type || 'application/octet-stream'
  const resolvedKind = resolveAttachmentKind(result.data.kind, mime, name)
  let recognitionStatus = ''
  let recognitionType = ''
  let recognitionText: string | undefined
  let recognitionMessage = ''
  let url = result.data.url || ''

  try {
    const recognition = await getFileRecognition(fileId, 1600)
    const data = recognition.data
    if (data) {
      recognitionStatus = data.status || ''
      recognitionType = data.recognitionType || ''
      recognitionText = truncateRecognitionText(data.text)
      recognitionMessage = data.message || ''
      url = data.previewUrl || url
    }
  } catch {
    recognitionStatus = ''
  }

  url = await resolvePreviewUrl(fileId, url)

  return {
    fileId,
    kind: resolvedKind,
    originalName: name,
    mimeType: mime,
    url,
    recognitionStatus: recognitionStatus || undefined,
    recognitionType: recognitionType || undefined,
    recognitionText,
    recognitionMessage: recognitionMessage || undefined,
  }
}

function openWebFilePicker(accept?: string): Promise<PickedUploadSource[]> {
  return new Promise((resolve) => {
    const input = document.createElement('input')
    input.type = 'file'
    input.multiple = true
    if (accept) {
      input.accept = accept
    }

    input.style.position = 'fixed'
    input.style.left = '-9999px'
    input.style.top = '-9999px'
    input.style.opacity = '0'
    input.style.pointerEvents = 'none'

    const cleanup = () => {
      input.onchange = null
      if (input.parentNode) {
        input.parentNode.removeChild(input)
      }
    }

    input.onchange = () => {
      const files = input.files ? Array.from(input.files) : []
      cleanup()
      if (!files.length) {
        resolve([])
        return
      }
      resolve(files.map((webFile) => ({ webFile })))
    }

    document.body.appendChild(input)
    input.click()
  })
}
