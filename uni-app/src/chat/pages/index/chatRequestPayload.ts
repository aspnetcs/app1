import type { ChatCompletionsRequest } from '@/api/types/chat'

type ChatRequestMessage = ChatCompletionsRequest['messages'][number]

export interface ChatPayloadOptions {
  isTemporary?: boolean
  maskId?: string | null
  knowledgeBaseIds?: string[]
  memoryEnabled?: boolean
  outputFormatInstruction?: string
}

export function buildChatRequestPayload(
  model: string,
  messages: ChatRequestMessage[],
  isTemporaryOrOptions: boolean | ChatPayloadOptions,
  maskId?: string | null,
  legacyKnowledgeBaseIds?: string[],
  legacyMemoryEnabled?: boolean,
): ChatCompletionsRequest {
  const options: ChatPayloadOptions = typeof isTemporaryOrOptions === 'boolean'
    ? {
        isTemporary: isTemporaryOrOptions,
        maskId,
        knowledgeBaseIds: legacyKnowledgeBaseIds,
        memoryEnabled: legacyMemoryEnabled,
      }
    : isTemporaryOrOptions

  const outputFormatInstruction = options.outputFormatInstruction?.trim()
  const requestMessages = outputFormatInstruction
    ? [
        { role: 'system', content: outputFormatInstruction },
        ...messages,
      ]
    : messages

  return {
    model,
    messages: requestMessages,
    stream: true,
    ...(options.isTemporary ? { isTemporary: true } : {}),
    ...(options.maskId ? { maskId: options.maskId } : {}),
    ...(options.knowledgeBaseIds?.length ? { knowledgeBaseIds: options.knowledgeBaseIds } : {}),
    ...(typeof options.memoryEnabled === 'boolean' ? { memoryEnabled: options.memoryEnabled } : {}),
  }
}
