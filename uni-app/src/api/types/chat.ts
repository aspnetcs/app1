export interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  images?: string[]
  timestamp: number
  status?: 'sending' | 'streaming' | 'done' | 'error'
  requestId?: string
  serverId?: string | null
  parentMessageId?: string | null
  version?: number | null
  translationText?: string
  translationLanguage?: string
  followUpSuggestions?: string[]
  versionHistory?: MessageVersion[]
  currentVersionIndex?: number
  toolTrace?: ToolTraceItem[]
  modelId?: string
  modelName?: string
  multiRoundId?: string | null
  branchIndex?: number
}

export type ConversationScope = 'persistent' | 'temporary'

export interface ToolTraceItem {
  toolName: string
  status: string
  input?: string
  output?: string
}

export type ChatAttachmentKind = 'image' | 'document'

export interface ChatAttachment {
  fileId: string
  kind: ChatAttachmentKind
  originalName?: string
  mimeType?: string
  url?: string
  recognitionStatus?: string
  recognitionType?: string
  recognitionText?: string
  recognitionMessage?: string
}

export interface ChatCompletionsRequest {
  model: string
  messages: Array<{
    role: string
    content: string
    images?: string[]
    attachments?: ChatAttachment[]
  }>
  temperature?: number
  top_p?: number
  max_tokens?: number
  isTemporary?: boolean
  is_temporary?: boolean
  maskId?: string | null
  toolNames?: string[]
  stream?: boolean
  knowledgeBaseIds?: string[]
  memoryEnabled?: boolean
}

export interface ChatCompletionsResponse {
  requestId: string
}

export interface MultiChatCompletionsResponse {
  roundId: string
  items: Array<{
    requestId: string
    model: string
    roundId: string
  }>
}

export interface ChatForkRequest {
  conversationId: string
  messageId: string
  branchName?: string
}

export interface ChatForkResponse {
  conversation: ForkConversation
}

export interface ForkConversation {
  id: string
  title?: string
  model: string
  messages: ChatMessage[]
  pinned?: boolean
  starred?: boolean
  presetId?: string | null
  presetName?: string | null
  systemPrompt?: string | null
  temperature?: number | null
  topP?: number | null
  maxTokens?: number | null
  maskId?: string | null
  maskName?: string | null
  maskAvatar?: string | null
  maskContextMessages?: MaskContextMessage[]
  createdAt: number
  updatedAt: number
  sourceConversationId?: string | null
  source_conversation_id?: string | null
  sourceMessageId?: string | null
  source_message_id?: string | null
}

export interface MessageVersion {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  content_type?: string
  media_url?: string
  parent_message_id?: string | null
  version: number
  token_count?: number | null
  model?: string | null
  created_at?: string
}

export type MessageVersionsResponse = MessageVersion[]

export interface MaskContextMessage {
  role: 'system' | 'user' | 'assistant'
  content: string
}

export interface Mask {
  id: string
  name?: string
  avatar?: string
  modelId?: string
  model_id?: string
  temperature?: number
  topP?: number
  top_p?: number
  systemPrompt?: string
  system_prompt?: string
  contextMessages?: MaskContextMessage[]
  context_messages?: MaskContextMessage[]
  enabled?: boolean
  sortOrder?: number
  sort_order?: number
  createdAt?: string
  created_at?: string
  updatedAt?: string
  updated_at?: string
}
