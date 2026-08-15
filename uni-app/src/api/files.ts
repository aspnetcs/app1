import { config } from '@/config'
import { http } from './http'
import { buildRequestHeaders, clearCurrentAuth, recoverUnauthorizedRequest } from './httpAuth'
import { buildPlatformApiPath } from './platformUserRouteContract'
import type { ApiResponse } from './types'
import { ErrorCodes } from './types'
import type { FileItem, FileUploadResult, FileUrlResult, FileKind, FileRecognitionResult } from './types/files'

const PLATFORM_FILES_ROUTE = buildPlatformApiPath('files')
const LOGIN_REQUIRED_MESSAGE = '请登录后使用此功能'
let lastUploadToastAt = 0
let lastUploadToastText = ''

function toText(value: unknown): string {
  return String(value ?? '').trim()
}

function toOptionalText(value: unknown): string | undefined {
  const text = toText(value)
  return text ? text : undefined
}

function toNumber(value: unknown): number {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  const parsed = Number(String(value ?? '').trim())
  return Number.isFinite(parsed) ? parsed : 0
}

function isFileKind(value: unknown): value is FileKind {
  return value === 'image' || value === 'document' || value === 'audio' || value === 'video' || value === 'other'
}

function normalizeFileItem(raw: unknown): FileItem | null {
  if (!raw || typeof raw !== 'object') return null
  const record = raw as Record<string, unknown>

  const fileId = toText(record.fileId ?? record.id ?? record.file_id)
  if (!fileId) return null

  const originalName =
    toText(record.originalName ?? record.original_name ?? record.name ?? record.filename) || 'file'
  const sizeBytes = toNumber(record.sizeBytes ?? record.size_bytes ?? record.size ?? record.length)
  const mimeType = toText(record.mimeType ?? record.mime_type ?? record.contentType ?? record.content_type)
  const kindRaw = record.kind ?? record.fileKind ?? record.file_kind
  const kind: FileKind = isFileKind(kindRaw) ? kindRaw : 'other'

  return {
    fileId,
    originalName,
    sizeBytes,
    mimeType,
    kind,
    sha256: toOptionalText(record.sha256),
    createdAt: toOptionalText(record.createdAt ?? record.created_at),
    deletedAt: (record.deletedAt ?? record.deleted_at) as any,
  }
}

function normalizeUploadResult(raw: unknown): FileUploadResult | null {
  const base = normalizeFileItem(raw)
  if (!base) return null
  const record = raw as Record<string, unknown>
  return {
    ...base,
    url: toOptionalText(record.url ?? record.previewUrl ?? record.preview_url),
  }
}

function normalizeApiEnvelope<T>(payload: unknown): ApiResponse<T> | null {
  if (!payload || typeof payload !== 'object') return null
  if (!('code' in payload)) return null
  const record = payload as Record<string, unknown>
  return {
    code: Number(record.code ?? ErrorCodes.SERVER_ERROR),
    message: typeof record.message === 'string' ? record.message : '',
    data: (record.data ?? null) as T,
  }
}

function parseJsonMaybe(text: unknown): unknown {
  if (typeof text !== 'string') return text
  const trimmed = text.trim()
  if (!trimmed) return null
  try {
    return JSON.parse(trimmed)
  } catch {
    return trimmed
  }
}

function showUploadToast(message: string) {
  const text = toText(message)
  if (!text) return
  const now = Date.now()
  if (text === lastUploadToastText && now - lastUploadToastAt < 1200) return
  lastUploadToastText = text
  lastUploadToastAt = now
  uni.showToast({ title: text, icon: 'none', duration: 2200 })
}

function resolveUploadErrorMessage(error: unknown, fallback = '上传失败，请稍后重试') {
  if (error instanceof Error) {
    const text = toText(error.message)
    if (text) return text
  }
  if (error && typeof error === 'object') {
    const record = error as Record<string, unknown>
    const message = toText(record.message)
    if (message) return message
  }
  return fallback
}

function isSuccessCode(code: number) {
  return code === ErrorCodes.SUCCESS || code === 200
}

function isUnauthorizedEnvelope<T>(envelope: ApiResponse<T> | null) {
  return envelope?.code === ErrorCodes.UNAUTHORIZED
}

async function recoverUploadAuthorization(retried: boolean) {
  if (retried) {
    clearCurrentAuth()
    showUploadToast(LOGIN_REQUIRED_MESSAGE)
    throw new Error(LOGIN_REQUIRED_MESSAGE)
  }

  const recovery = recoverUnauthorizedRequest(config.features.guestAuth)
  if (!recovery) {
    clearCurrentAuth()
    showUploadToast(LOGIN_REQUIRED_MESSAGE)
    throw new Error(LOGIN_REQUIRED_MESSAGE)
  }

  try {
    await recovery
  } catch {
    clearCurrentAuth()
    showUploadToast(LOGIN_REQUIRED_MESSAGE)
    throw new Error(LOGIN_REQUIRED_MESSAGE)
  }
}

function buildUploadHeaders(): Record<string, string> {
  const header = buildRequestHeaders(true, {})
  // Let runtime decide multipart boundary.
  delete header['Content-Type']
  return header
}

function buildFilesUrl(apiBaseUrl: string): string {
  const baseUrl = apiBaseUrl.replace(/\/$/, '')
  return baseUrl + PLATFORM_FILES_ROUTE
}

export function uploadFileToLibrary(input: {
  filePath?: string
  webFile?: File
  /**
   * Optional filename hint (backend may read multipart metadata).
   * When missing, backend should infer from uploaded filePath.
   */
  filename?: string
  formData?: Record<string, string>
}): Promise<ApiResponse<FileUploadResult>> {
  if (input.webFile) {
    return uploadWebFileToLibrary({
      webFile: input.webFile,
      filename: input.filename,
      formData: input.formData,
    })
  }

  return uploadNativeFileToLibrary(input)
}

function uploadNativeFileToLibrary(input: {
  filePath?: string
  filename?: string
  formData?: Record<string, string>
}, retried = false): Promise<ApiResponse<FileUploadResult>> {
  const url = buildFilesUrl(config.apiBaseUrl)

  return new Promise((resolve, reject) => {
    if (!input.filePath) {
      reject(new Error('missing file path'))
      return
    }
    uni.uploadFile({
      url,
      filePath: input.filePath,
      name: 'file',
      formData: input.formData,
      header: buildUploadHeaders(),
      fail: (err) => reject(err),
      success: async (res) => {
        const statusCode = Number(res.statusCode || 0)
        const parsed = parseJsonMaybe(res.data)
        const envelope = normalizeApiEnvelope<unknown>(parsed)

        if (statusCode === 401 || isUnauthorizedEnvelope(envelope)) {
          try {
            await recoverUploadAuthorization(retried)
            const retriedResult = await uploadNativeFileToLibrary(input, true)
            resolve(retriedResult)
          } catch (error) {
            reject(error)
          }
          return
        }

        if (statusCode < 200 || statusCode >= 300) {
          if (envelope) {
            const error = new Error(envelope.message || `HTTP ${statusCode}`)
            showUploadToast(error.message)
            reject(error)
            return
          }
          const error = new Error(`HTTP ${statusCode}`)
          showUploadToast(error.message)
          reject(error)
          return
        }

        if (envelope) {
          if (!isSuccessCode(envelope.code)) {
            const error = new Error(envelope.message || 'upload failed')
            showUploadToast(error.message)
            reject(error)
            return
          }
          const normalized = normalizeUploadResult(envelope.data) ?? (envelope.data as any)
          resolve({ ...envelope, data: normalized })
          return
        }

        const normalized = normalizeUploadResult(parsed)
        if (!normalized) {
          const error = new Error('unexpected upload response')
          showUploadToast(error.message)
          reject(error)
          return
        }
        resolve({ code: ErrorCodes.SUCCESS, message: '', data: normalized })
      },
    })
  })
}

async function uploadWebFileToLibrary(input: {
  webFile: File
  filename?: string
  formData?: Record<string, string>
}, retried = false): Promise<ApiResponse<FileUploadResult>> {
  const form = new FormData()
  form.append('file', input.webFile, input.filename || input.webFile.name || 'file')

  if (input.formData) {
    for (const [key, value] of Object.entries(input.formData)) {
      form.append(key, value)
    }
  }

  const response = await fetch(buildFilesUrl(config.apiBaseUrl), {
    method: 'POST',
    headers: buildUploadHeaders(),
    body: form,
  })

  const parsed = parseJsonMaybe(await response.text())
  const envelope = normalizeApiEnvelope<unknown>(parsed)

  if (response.status === 401 || isUnauthorizedEnvelope(envelope)) {
    await recoverUploadAuthorization(retried)
    return uploadWebFileToLibrary(input, true)
  }

  if (!response.ok) {
    if (envelope) {
      const error = new Error(envelope.message || `HTTP ${response.status}`)
      showUploadToast(error.message)
      throw error
    }
    const error = new Error(`HTTP ${response.status}`)
    showUploadToast(error.message)
    throw error
  }

  if (envelope) {
    if (!isSuccessCode(envelope.code)) {
      const error = new Error(envelope.message || 'upload failed')
      showUploadToast(error.message)
      throw error
    }
    const normalized = normalizeUploadResult(envelope.data) ?? (envelope.data as FileUploadResult)
    return { ...envelope, data: normalized }
  }

  const normalized = normalizeUploadResult(parsed)
  if (!normalized) {
    const error = new Error('unexpected upload response')
    showUploadToast(error.message)
    throw error
  }
  return { code: ErrorCodes.SUCCESS, message: '', data: normalized }
}

export function getFileMeta(fileId: string) {
  const path = buildPlatformApiPath(`files/${encodeURIComponent(fileId)}`)
  return http.get<FileItem>(path, undefined, { auth: true, silent: true })
}

export function getFileUrl(fileId: string, mode: 'preview' | 'download' = 'preview') {
  const path = buildPlatformApiPath(`files/${encodeURIComponent(fileId)}/url`)
  return http.get<FileUrlResult>(path, { mode }, { auth: true, silent: true })
}

export function getFileRecognition(fileId: string, maxChars = 8000) {
  const path = buildPlatformApiPath(`files/${encodeURIComponent(fileId)}/recognition`)
  return http.get<FileRecognitionResult>(path, { maxChars }, { auth: true, silent: true })
}

export function deleteFile(fileId: string) {
  const path = buildPlatformApiPath(`files/${encodeURIComponent(fileId)}`)
  return http.delete<{ deleted: boolean }>(path, undefined, { auth: true, silent: true })
}
