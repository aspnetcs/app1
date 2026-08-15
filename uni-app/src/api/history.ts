import { http } from './http'
import { buildHistorySearchPath } from './platformHistoryRouteContract'

export interface HistorySearchTopicItem {
  id: string
  conversationId: string
  title: string
  snippet?: string
  model?: string
  updatedAt?: string
  messageCount?: number
}

export interface HistorySearchMessageItem {
  conversationId: string
  conversationTitle: string
  messageId: string
  anchorMessageId?: string
  role?: string
  snippet?: string
  createdAt?: string
}

export interface HistorySearchFileItem {
  conversationId: string
  conversationTitle: string
  messageId: string
  anchorMessageId?: string
  fileLabel?: string
  snippet?: string
  createdAt?: string
}

export interface HistorySearchResponse<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export const searchHistoryTopics = (keyword: string, page = 0, size = 20) =>
  http.get<HistorySearchResponse<HistorySearchTopicItem>>(buildHistorySearchPath('topics', { keyword, page, size }), undefined, {
    auth: true,
    silent: true,
  })

export const searchHistoryMessages = (keyword: string, topicId?: string, page = 0, size = 20) =>
  http.get<HistorySearchResponse<HistorySearchMessageItem>>(
    buildHistorySearchPath('messages', { keyword, topicId, page, size }),
    undefined,
    {
      auth: true,
      silent: true,
    },
  )

export const searchHistoryFiles = (keyword: string, topicId?: string, page = 0, size = 20) =>
  http.get<HistorySearchResponse<HistorySearchFileItem>>(buildHistorySearchPath('files', { keyword, topicId, page, size }), undefined, {
    auth: true,
    silent: true,
  })