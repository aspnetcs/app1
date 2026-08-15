import {
  createConversationRemote,
  deleteConversationRemote,
  listArchivedConversations,
  listConversationMessages,
  listConversations,
  listMessageVersions,
  pinConversationRemote,
  restoreConversationRemote,
  saveConversationMessage,
  updateConversationRemote,
  type ConversationMessageResponse,
} from '@/api/conversations'
import {
  searchHistoryFiles,
  searchHistoryMessages,
  searchHistoryTopics,
  type HistorySearchFileItem,
  type HistorySearchMessageItem,
  type HistorySearchResponse,
  type HistorySearchTopicItem,
} from '@/api/history'
import type { Conversation, Message, MessageVersionItem } from './chatState'
import { normalizeConversation, normalizeMessage, normalizeMessageVersion } from './chatState'
import type { ChatAttachment } from '@/api/types/chat'

export async function fetchConversationHistoryData(page = 0, size = 200): Promise<{
  history: Conversation[]
}> {
  const response = await listConversations()
  const list = Array.isArray(response.data) ? response.data : []
  return {
    history: list.map(normalizeConversation),
  }
}

export async function fetchArchivedConversationHistoryData(): Promise<Conversation[]> {
  const response = await listArchivedConversations()
  const list = Array.isArray(response.data) ? response.data : []
  return list.map(normalizeConversation)
}

export async function searchConversationTopicHistoryData(
  keyword: string,
  page = 0,
  size = 20,
): Promise<HistorySearchResponse<HistorySearchTopicItem>> {
  const response = await searchHistoryTopics(keyword, page, size)
  return {
    items: Array.isArray(response.data?.items) ? response.data.items : [],
    total: Number(response.data?.total ?? 0),
    page: Number(response.data?.page ?? page),
    size: Number(response.data?.size ?? size),
  }
}

export async function searchConversationMessageHistoryData(
  keyword: string,
  topicId?: string,
  page = 0,
  size = 20,
): Promise<HistorySearchResponse<HistorySearchMessageItem>> {
  const response = await searchHistoryMessages(keyword, topicId, page, size)
  return {
    items: Array.isArray(response.data?.items) ? response.data.items : [],
    total: Number(response.data?.total ?? 0),
    page: Number(response.data?.page ?? page),
    size: Number(response.data?.size ?? size),
  }
}

export async function searchConversationFileHistoryData(
  keyword: string,
  topicId?: string,
  page = 0,
  size = 20,
): Promise<HistorySearchResponse<HistorySearchFileItem>> {
  const response = await searchHistoryFiles(keyword, topicId, page, size)
  return {
    items: Array.isArray(response.data?.items) ? response.data.items : [],
    total: Number(response.data?.total ?? 0),
    page: Number(response.data?.page ?? page),
    size: Number(response.data?.size ?? size),
  }
}

export async function fetchConversationMessagesData(conversationId: string): Promise<Message[]> {
  const response = await listConversationMessages(conversationId)
  const list = Array.isArray(response.data) ? response.data : []
  return list.map(normalizeMessage)
}

export async function createConversationData(data: {
  title?: string
  model?: string
  compareModels?: string[]
  isTemporary?: boolean
}): Promise<Conversation> {
  const response = await createConversationRemote({
    title: data.title,
    model: data.model,
    compare_models: data.compareModels,
    isTemporary: data.isTemporary,
  })
  return normalizeConversation(response.data)
}

export async function persistConversationMessageData(
  conversationId: string,
  message: Message,
  selectedModel: string,
): Promise<ConversationMessageResponse> {
  const blocks = buildAttachmentBlocks(message.attachments)
  const response = await saveConversationMessage(conversationId, {
    role: message.role,
    content: message.content,
    model: message.model || selectedModel,
    parent_message_id: message.parentMessageId || null,
    version: message.version ?? undefined,
    ...(blocks.length ? { blocks } : {}),
  })

  return response.data
}

function buildAttachmentBlocks(attachments?: ChatAttachment[]): Array<Record<string, unknown>> {
  if (!Array.isArray(attachments) || attachments.length === 0) return []
  return attachments
    .filter((item) => item?.fileId)
    .map((item, index) => ({
      type: item.kind === 'image' ? 'image' : 'file',
      key: `attachment-${item.fileId}`,
      sequence: index,
      status: 'final',
      payload: {
        fileId: item.fileId,
        kind: item.kind,
        originalName: item.originalName || 'file',
        mimeType: item.mimeType || '',
        url: item.url || '',
        recognitionStatus: item.recognitionStatus || '',
        recognitionType: item.recognitionType || '',
        recognitionText: item.recognitionText || '',
        recognitionMessage: item.recognitionMessage || '',
      },
    }))
}

export async function fetchMessageVersionData(messageId: string): Promise<MessageVersionItem[]> {
  const response = await listMessageVersions(messageId)
  return (Array.isArray(response.data) ? response.data : [])
    .map(normalizeMessageVersion)
    .sort((a, b) => a.version - b.version)
}

export async function updateConversationData(
  conversationId: string,
  data: {
    title?: string
    model?: string
    compare_models?: string[]
    system_prompt?: string
  },
): Promise<Conversation> {
  const response = await updateConversationRemote(conversationId, data)
  return normalizeConversation(response.data)
}

export async function pinConversationData(conversationId: string, pinned: boolean): Promise<Conversation> {
  const response = await pinConversationRemote(conversationId, pinned)
  return normalizeConversation(response.data)
}

export async function deleteConversationData(conversationId: string) {
  await deleteConversationRemote(conversationId)
}

export async function restoreConversationData(conversationId: string): Promise<Conversation> {
  const response = await restoreConversationRemote(conversationId)
  return normalizeConversation(response.data)
}
