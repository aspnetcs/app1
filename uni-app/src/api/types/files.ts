export type FileKind = 'image' | 'document' | 'audio' | 'video' | 'other'

export interface FileItem {
  fileId: string
  originalName: string
  sizeBytes: number
  mimeType: string
  sha256?: string
  kind: FileKind
  createdAt?: string
  deletedAt?: string | null
}

export interface FileUploadResult extends FileItem {
  /**
   * Optional preview URL returned by backend for immediate use.
   * The canonical preview/download URL should be fetched via `/url` endpoint when needed.
   */
  url?: string
}

export interface FileUrlResult {
  url: string
  expiresIn?: number
}

export type FileRecognitionStatus =
  | 'extracted'
  | 'vision_ready'
  | 'unsupported'
  | 'unsupported_mime'
  | 'extract_failed'
  | string

export interface FileRecognitionResult extends FileItem {
  supported: boolean
  recognitionType: 'document' | 'image' | string
  status: FileRecognitionStatus
  text?: string
  charCount?: number
  truncated?: boolean
  previewUrl?: string
  expiresIn?: number
  message?: string
}

