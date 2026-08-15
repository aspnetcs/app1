import { config } from '@/config'
import type { ApiResponse } from './types'
import { ErrorCodes } from './types'
import { buildRequestHeaders, clearCurrentAuth, recoverUnauthorizedRequest } from './httpAuth'

export { setAuthStoreGetter } from './httpAuth'

interface RequestOptions {
  auth?: boolean
  silent?: boolean
  headers?: Record<string, string>
  timeout?: number
  /**
   * Optional hook to observe the raw HTTP response (headers/status) without changing ApiResponse shapes.
   * Useful for correlation headers like X-Trace-Id.
   */
  onResponse?: (info: { statusCode: number; header: Record<string, unknown> }) => void
  __retried?: boolean
}

let lastToastAt = 0
let lastToastText = ''
const LOGIN_REQUIRED_MESSAGE = '请登录后使用此功能'

function showToast(message: string, silent: boolean) {
  if (silent || !message) return
  const now = Date.now()
  if (message === lastToastText && now - lastToastAt < 1200) return
  lastToastText = message
  lastToastAt = now
  uni.showToast({ title: message, icon: 'none' })
}

function statusMessage(statusCode: number) {
  if (statusCode === 403) return '无权限访问'
  if (statusCode === 429) return '请求频繁，请稍后重试'
  if (statusCode >= 500) return '服务繁忙，请稍后重试'
  return `请求失败(${statusCode})`
}

function toRequestData(data: unknown): UniApp.RequestOptions['data'] {
  if (data === undefined || data === null) return undefined
  if (typeof data === 'string' || data instanceof ArrayBuffer) return data
  if (typeof data === 'object') return data as Record<string, unknown>
  return String(data)
}

function normalizeResponse<T>(payload: unknown): ApiResponse<T> | null {
  if (!payload || typeof payload !== 'object') return null
  if (!('code' in payload)) return null
  const obj = payload as Partial<ApiResponse<T>>
  return {
    code: Number(obj.code),
    message: typeof obj.message === 'string' ? obj.message : '',
    data: (obj.data ?? null) as T,
  }
}

export function request<T = unknown>(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH',
  path: string,
  data?: unknown,
  options: RequestOptions = {}
): Promise<ApiResponse<T>> {
  const { auth = false, silent = false, headers = {}, timeout } = options
  const retried = options.__retried === true

  return new Promise((resolve, reject) => {
    const baseUrl = config.apiBaseUrl.replace(/\/$/, '')
    const url = path.startsWith('http') ? path : baseUrl + (path.startsWith('/') ? path : '/' + path)
    const header = buildRequestHeaders(auth, headers)

    // Ensure Content-Type is set correctly for POST/PUT/DELETE with body
    const methodUpper = String(method || '').toUpperCase()
    const requestData = toRequestData(data)
    // For mp-weixin: explicitly stringify object data to ensure JSON body is sent correctly
    let finalData: UniApp.RequestOptions['data'] = requestData
    if (requestData && typeof requestData === 'object' && !Array.isArray(requestData) && !(requestData instanceof ArrayBuffer)) {
      finalData = JSON.stringify(requestData)
      header['Content-Type'] = 'application/json'
    }

    // Debug: log request details for auth-related paths
    if (path.includes('auth') || path.includes('conversations')) {
      console.warn('[http] request:', method, path, 'auth=' + auth, 'hasAuthHeader=' + !!header.Authorization, 'hasRecoveryHeader=' + !!header['X-Guest-Recovery-Token'], 'dataType=' + typeof finalData, 'bodyPreview=' + (typeof finalData === 'string' ? finalData.substring(0, 100) : ''))
    }

    const handleUnauthorized = (rejectPayload: unknown) => {
      console.warn('[http] handleUnauthorized: auth=', auth, 'retried=', retried, 'path=', path)
      if (auth && !retried) {
        const recovery = recoverUnauthorizedRequest(config.features.guestAuth)
        console.warn('[http] handleUnauthorized: recovery=', recovery ? 'started' : 'null')

        if (!recovery) {
          clearCurrentAuth()
          showToast(LOGIN_REQUIRED_MESSAGE, silent)
          reject(rejectPayload)
          return
        }

        Promise.resolve(recovery)
          .then((newToken) => {
            console.warn('[http] handleUnauthorized: recovery succeeded, token length=', newToken?.length)
            request<T>(method, path, data, { ...options, __retried: true }).then(resolve).catch((retryErr) => {
              console.warn('[http] handleUnauthorized: retry failed:', retryErr)
              reject(retryErr)
            })
          })
          .catch((recoveryErr) => {
            console.warn('[http] handleUnauthorized: recovery failed:', recoveryErr)
            clearCurrentAuth()
            showToast(LOGIN_REQUIRED_MESSAGE, silent)
            reject(rejectPayload)
          })
        return
      }

      if (auth) {
        clearCurrentAuth()
      }
      console.warn('[http] handleUnauthorized: no recovery attempted, rejecting')
      showToast(LOGIN_REQUIRED_MESSAGE, silent)
      reject(rejectPayload)
    }

    uni.request({
      url,
      method: method as UniApp.RequestOptions['method'],
      data: finalData,
      header,
      enableCookie: true,
      withCredentials: true,
      timeout: timeout || 60000,
      success(res) {
        const statusCode = Number(res.statusCode || 0)
        // Debug: log response for auth-related paths
        if (path.includes('auth') || path.includes('conversations')) {
          console.warn('[http] response:', method, path, 'statusCode=' + statusCode, 'data=' + JSON.stringify(res.data).substring(0, 200))
        }
        try {
          options.onResponse?.({ statusCode, header: (res as any)?.header || {} })
        } catch {
          // ignore
        }
        const result = normalizeResponse<T>(res.data)

        if (statusCode === 401 || (auth && result?.code === ErrorCodes.UNAUTHORIZED)) {
          handleUnauthorized(result || res.data)
          return
        }

        if (statusCode < 200 || statusCode >= 300) {
          const message = result?.message || statusMessage(statusCode)
          showToast(message, silent)
          reject(res.data)
          return
        }

        if (!result) {
          resolve({
            code: ErrorCodes.SUCCESS,
            message: '',
            data: (res.data as T) ?? (null as unknown as T),
          })
          return
        }

        if (result.code === ErrorCodes.SUCCESS) {
          resolve(result)
          return
        }

        showToast(result.message || '请求失败', silent)
        reject(result)
      },
      fail(err) {
        const errObj = err as unknown as Record<string, unknown>
        const errText = String(errObj?.errMsg ?? '')
        const message = errText.includes('timeout') ? '请求超时，请稍后重试' : '网络连接失败，请检查网络'
        showToast(message, silent)
        reject(err)
      },
    })
  })
}

export const http = {
  get: <T = unknown>(path: string, data?: unknown, options?: RequestOptions) =>
    request<T>('GET', path, data, options),

  post: <T = unknown>(path: string, data?: unknown, options?: RequestOptions) =>
    request<T>('POST', path, data, options),

  put: <T = unknown>(path: string, data?: unknown, options?: RequestOptions) =>
    request<T>('PUT', path, data, options),

  delete: <T = unknown>(path: string, data?: unknown, options?: RequestOptions) =>
    request<T>('DELETE', path, data, options),

  patch: <T = unknown>(path: string, data?: unknown, options?: RequestOptions) =>
    request<T>('PATCH', path, data, options),
}
