import { resolveTranslationLanguageLabel } from '@/config/translation'
import type { Message } from '@/stores/chatState'

export const MESSAGE_TRANSLATION_BLOCK_TYPE = 'translation'
export const MESSAGE_TRANSLATION_LOADING_CONTENT = '正在翻译...'

export interface CitationSource {
  id: string
  index?: number
  title: string
  snippet?: string
  url?: string
  sourceType?: string
  metadata?: Record<string, unknown>
}

export interface MessageBlock {
  id: string
  type: string
  title?: string
  content?: string
  citations?: CitationSource[]
  metadata?: Record<string, unknown>
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' ? value as Record<string, unknown> : {}
}

function toString(value: unknown): string {
  if (typeof value === 'string') return value
  if (value == null) return ''
  return String(value)
}

function normalizeCitation(raw: unknown, index = 0): CitationSource | null {
  const record = asRecord(raw)
  const title = toString(record.title || record.name || record.source || record.label).trim()
  const snippet = toString(record.snippet || record.content || record.quote || record.text).trim()
  const url = toString(record.url || record.href || record.sourceUrl).trim()
  const sourceType = toString(record.sourceType || record.type || record.kind).trim()
  if (!title && !snippet && !url) return null
  const explicitId = toString(record.id).trim()
  return {
    id: explicitId || `citation-${index + 1}`,
    index: typeof record.index === 'number' ? record.index : index + 1,
    title: title || `Source ${index + 1}`,
    snippet: snippet || undefined,
    url: url || undefined,
    sourceType: sourceType || undefined,
    metadata: record,
  }
}

export function normalizeCitationList(raw: unknown): CitationSource[] {
  if (!Array.isArray(raw)) return []
  return raw
    .map((item, index) => normalizeCitation(item, index))
    .filter((item): item is CitationSource => Boolean(item))
}

function normalizeBlock(raw: unknown, index = 0): MessageBlock | null {
  const record = asRecord(raw)
  const type = toString(record.type || record.blockType || record.kind).trim() || 'markdown'
  const payload = asRecord(record.payload)
  const content = toString(record.content || record.text || payload.text || payload.content).trim()
  const title = toString(record.title || record.label || payload.title || payload.originalName).trim()
  const citations = normalizeCitationList(record.citations || record.references || record.items || payload.citations)
  const hasPayload = Object.keys(payload).length > 0
  if (!content && !title && citations.length === 0 && !hasPayload) return null
  const explicitId = toString(record.id).trim()
  return {
    id: explicitId || `block-${index + 1}`,
    type,
    title: title || undefined,
    content: content || undefined,
    citations: citations.length ? citations : undefined,
    metadata: hasPayload ? { ...record, payload } : record,
  }
}

export function normalizeMessageBlocks(raw: unknown): MessageBlock[] {
  if (!Array.isArray(raw)) return []
  return raw
    .map((item, index) => normalizeBlock(item, index))
    .filter((item): item is MessageBlock => Boolean(item))
}

function hasTranslationBlock(blocks: MessageBlock[]): boolean {
  return blocks.some((block) => block.type === MESSAGE_TRANSLATION_BLOCK_TYPE)
}

export function createMessageTranslationBlock(payload: {
  targetLanguage?: string
  content?: string
  status?: Message['translationStatus']
  error?: string
}): MessageBlock | null {
  const targetLanguage = typeof payload.targetLanguage === 'string' ? payload.targetLanguage.trim() : ''
  const content = typeof payload.content === 'string' ? payload.content.trim() : ''
  const status = payload.status === 'loading' || payload.status === 'error' ? payload.status : 'done'
  const fallbackContent =
    status === 'loading'
      ? MESSAGE_TRANSLATION_LOADING_CONTENT
      : content || (typeof payload.error === 'string' ? payload.error.trim() : '')
  if (!fallbackContent) return null
  return {
    id: 'translation',
    type: MESSAGE_TRANSLATION_BLOCK_TYPE,
    title: `${status === 'error' ? '翻译失败' : '翻译'} · ${resolveTranslationLanguageLabel(targetLanguage)}`,
    content: fallbackContent,
    metadata: {
      targetLanguage: targetLanguage || undefined,
      status,
      error: payload.error,
    },
  }
}

export function upsertMessageTranslationBlock(
  blocks: MessageBlock[] | undefined,
  translationBlock: MessageBlock | null,
): MessageBlock[] | undefined {
  const list = Array.isArray(blocks) ? blocks.filter(Boolean) : []
  const next = list.filter((block) => block.type !== MESSAGE_TRANSLATION_BLOCK_TYPE)
  if (!translationBlock) return next.length ? next : undefined
  return [...next, translationBlock]
}

export function markMessageTranslationLoading(message: Message, targetLanguage: string): void {
  message.translationLanguage = targetLanguage
  message.translationStatus = 'loading'
  message.translationError = undefined
  message.blocks = upsertMessageTranslationBlock(
    message.blocks,
    createMessageTranslationBlock({
      targetLanguage,
      status: 'loading',
    }),
  )
}

export function applyMessageTranslation(
  message: Message,
  payload: { translatedText: string; targetLanguage: string },
): void {
  const translatedText = typeof payload.translatedText === 'string' ? payload.translatedText.trim() : ''
  const targetLanguage = typeof payload.targetLanguage === 'string' ? payload.targetLanguage.trim() : ''
  message.translationText = translatedText || undefined
  message.translationLanguage = targetLanguage || undefined
  message.translationStatus = translatedText ? 'done' : undefined
  message.translationError = undefined
  message.blocks = upsertMessageTranslationBlock(
    message.blocks,
    createMessageTranslationBlock({
      targetLanguage,
      content: translatedText,
      status: 'done',
    }),
  )
}

export function markMessageTranslationFailed(
  message: Message,
  error: string,
  targetLanguage: string,
): void {
  message.translationLanguage = targetLanguage
  message.translationStatus = 'error'
  message.translationError = error
  message.blocks = upsertMessageTranslationBlock(
    message.blocks,
    createMessageTranslationBlock({
      targetLanguage,
      content: message.translationText,
      status: message.translationText ? 'done' : 'error',
      error,
    }),
  )
}

export function buildMessageTranslationBlock(
  message: Pick<Message, 'translationText' | 'translationLanguage' | 'translationStatus' | 'translationError'>,
): MessageBlock | null {
  return createMessageTranslationBlock({
    targetLanguage: message.translationLanguage,
    content: message.translationText,
    status: message.translationStatus,
    error: message.translationError,
  })
}

export function collectMessageCitations(message: Pick<Message, 'blocks' | 'citations'>): CitationSource[] {
  const direct = normalizeCitationList(message.citations)
  if (direct.length) return direct
  const fromBlocks: CitationSource[] = []
  for (const block of message.blocks || []) {
    if (!block.citations?.length) continue
    fromBlocks.push(...block.citations)
  }
  return fromBlocks
}

export function buildRenderableMessageBlocks(
  message: Pick<
    Message,
    'content' | 'blocks' | 'citations' | 'translationText' | 'translationLanguage' | 'translationStatus' | 'translationError'
  >,
): MessageBlock[] {
  const blocks = Array.isArray(message.blocks) ? message.blocks.filter(Boolean) : []
  const translationBlock = buildMessageTranslationBlock(message)
  const citations = normalizeCitationList(message.citations)
  const contentFallbackBlocks = !message.content && citations.length
    ? [{ id: 'citation-only', type: 'citation', citations }]
    : message.content
      ? [{ id: 'content', type: 'markdown', content: message.content, citations: citations.length ? citations : undefined }]
      : []
  const primaryBlocks = blocks.filter((block) => block.type !== MESSAGE_TRANSLATION_BLOCK_TYPE)
  const resolvedPrimaryBlocks = primaryBlocks.length ? primaryBlocks : contentFallbackBlocks
  const resolvedTranslationBlock = hasTranslationBlock(blocks)
    ? blocks.find((block) => block.type === MESSAGE_TRANSLATION_BLOCK_TYPE) ?? null
    : translationBlock

  if (!resolvedTranslationBlock) return resolvedPrimaryBlocks
  return [...resolvedPrimaryBlocks, resolvedTranslationBlock]
}
