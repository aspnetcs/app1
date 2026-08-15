export interface PresignRequest {
  purpose: string
  filename: string
}

export interface PresignResponse {
  bucket: string
  objectKey: string
  uploadUrl: string
  method?: string
  expiresIn: number
}

export interface ConfirmRequest {
  purpose: string
  objectKey: string
  sha256: string
  mimeType?: string
  size?: number
}

export interface ConfirmResponse {
  bucket: string
  objectKey: string
  url: string
  expiresIn: number
  size: number
  mimeType: string
  sha256: string
}

export interface UserGroupProfileResponse {
  groups: string[]
  allowedModels: string[]
  featureFlags: string[]
  chatRateLimitPerMinute: number
}

export interface JobEnqueueRequest {
  type: string
  input: Record<string, unknown>
}

export interface JobEnqueueResponse {
  jobId: string
  type: string
  status: string
  createdAt: string
}

export interface JobStatusResponse {
  jobId: string
  type: string
  status: 'queued' | 'processing' | 'done' | 'failed'
  progress: number
  error: string
  output: Record<string, unknown>
  createdAt: string
  updatedAt: string
}

export interface WsMessage {
  type: string
  data: Record<string, unknown>
}
