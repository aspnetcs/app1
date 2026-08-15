/**
 * 聊天 API
 */
import { http } from './http'
import { APP_CONFIG } from '@/config'
import { config } from '@/config'
import { getStorage } from '@/utils/storage'
import {
  PLATFORM_CHAT_ROUTE_CONTRACT,
  buildConversationForkPath,
  buildChatCompletionsSseUrl,
  buildChatCompatUrl,
} from './platformChatRouteContract'
import type {
  ChatCompletionsRequest,
  ChatCompletionsResponse,
  MultiChatCompletionsResponse,
  ChatForkRequest,
  ChatForkResponse,
} from './types'
import { readTraceIdFromHeaders } from '@/utils/traceId'

export interface ChatRequestMetaOptions {
  /**
   * Optional client-provided trace id. Backend may accept and echo it, or generate its own.
   */
  traceId?: string
  /**
   * Observes the trace id returned by backend response headers (X-Trace-Id), if present.
   */
  onTraceId?: (traceId: string) => void
}

/** OpenAI-compatible chat completion response shape */
interface OpenAiChatResponse {
  choices?: Array<{ message?: { content?: string } }>
  model?: string
  error?: string | { message?: string }
}

// 提交聊天 -> 返回 requestId (实际内容通过 WS 推送)
export const chatCompletions = (data: ChatCompletionsRequest, meta?: ChatRequestMetaOptions) =>
  http.post<ChatCompletionsResponse>(PLATFORM_CHAT_ROUTE_CONTRACT.completions, data, {
    auth: true,
    headers: meta?.traceId ? { 'X-Trace-Id': meta.traceId } : undefined,
    onResponse: meta?.onTraceId
      ? (info) => {
          const traceId = readTraceIdFromHeaders((info?.header || {}) as Record<string, unknown>)
          if (traceId) meta.onTraceId?.(traceId)
        }
      : undefined,
  })

export const chatCompletionsMulti = (data: ChatCompletionsRequest & { models: string[] }, meta?: ChatRequestMetaOptions) =>
  http.post<MultiChatCompletionsResponse>(PLATFORM_CHAT_ROUTE_CONTRACT.completionsMulti, data, {
    auth: true,
    headers: meta?.traceId ? { 'X-Trace-Id': meta.traceId } : undefined,
    onResponse: meta?.onTraceId
      ? (info) => {
          const traceId = readTraceIdFromHeaders((info?.header || {}) as Record<string, unknown>)
          if (traceId) meta.onTraceId?.(traceId)
        }
      : undefined,
  })

export const chatFork = (data: ChatForkRequest) =>
  http.post<ChatForkResponse>(buildConversationForkPath(data.conversationId), {
    messageId: data.messageId,
    ...(data.branchName ? { branchName: data.branchName } : {}),
  }, { auth: true })

export function getChatCompletionsSseUrl(): string {
  return buildChatCompletionsSseUrl(config.apiBaseUrl)
}

// WS 不可用时的 fallback: 走 OpenAI Gateway 非流式端点
export function chatCompat(data: ChatCompletionsRequest): Promise<{ message: { content: string }; model: string }> {
  // apiBaseUrl = 'http://…/api' -> 去掉末尾 /api -> 拼 /v1/chat/completions
  const url = buildChatCompatUrl(config.apiBaseUrl)
  const token = getStorage<string>('token') || ''

  return new Promise((resolve, reject) => {
    uni.request({
      url,
      method: 'POST' as UniApp.RequestOptions['method'],
      data: { ...data, stream: false },
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: 'Bearer ' + token } : {}),
      },
      timeout: APP_CONFIG.timeout,
      success(res) {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          // OpenAI 返回: { choices: [{ message: { content: "..." } }], model: "..." }
          // 转换为旧格式: { message: { content: "..." }, model: "..." }
          const d = res.data as OpenAiChatResponse
          const content = d?.choices?.[0]?.message?.content ?? ''
          const model = d?.model ?? data.model ?? ''
          resolve({ message: { content }, model })
        } else {
          // OpenAI Gateway error: { error: { message: "..." } } or { error: "..." }
          const errData = res.data as OpenAiChatResponse
          const err = errData?.error
          const msg = typeof err === 'object' ? err?.message : err
          reject(new Error(msg || `请求失败(${res.statusCode})`))
        }
      },
      fail(err) {
        reject(err)
      },
    })
  })
}

