export type ChatSendAttempt =
  | { kind: 'noop' }
  | { kind: 'blocked'; text: string; error?: string }
  | { kind: 'single' | 'compare' | 'team'; text: string }

type ResolveChatSendAttemptOptions = {
  customText?: string
  draftText: string
  hasAttachments?: boolean
  attachmentOnlyText?: string
  isTeamMode: boolean
  isCompareMode: boolean
  isGenerating: boolean
  isModelLoading: boolean
  isComparingGeneration: boolean
  isDebateBusy: boolean
  isTemporaryConversation: boolean
  selectedModel: string
  availableModelIds: string[]
  multiModelIds: string[]
}

export function normalizeMultiModelIds(multiModelIds: string[], availableModelIds: string[]): string[] {
  const available = new Set(availableModelIds.filter(Boolean))
  const normalized: string[] = []
  for (const modelId of multiModelIds) {
    if (!modelId || !available.has(modelId) || normalized.includes(modelId)) {
      continue
    }
    normalized.push(modelId)
  }
  return normalized
}

export function replaceMultiModelId(
  multiModelIds: string[],
  index: number,
  nextModelId: string,
): string[] {
  if (index < 0 || index >= multiModelIds.length) {
    return [...multiModelIds]
  }

  const normalizedNextModelId = nextModelId.trim()
  if (!normalizedNextModelId) {
    return [...multiModelIds]
  }

  if (multiModelIds[index] === normalizedNextModelId) {
    return [...multiModelIds]
  }

  const duplicatedElsewhere = multiModelIds.some((modelId, currentIndex) => {
    return currentIndex !== index && modelId === normalizedNextModelId
  })
  if (duplicatedElsewhere) {
    return [...multiModelIds]
  }

  const updated = [...multiModelIds]
  updated[index] = normalizedNextModelId
  return updated
}

export function resolveChatSendAttempt(options: ResolveChatSendAttemptOptions): ChatSendAttempt {
  let text = (options.customText ?? options.draftText).trim()
  if (!text && options.hasAttachments) {
    text = (options.attachmentOnlyText || '请识别并总结附件内容。').trim()
  }
  const normalizedMultiModelIds = normalizeMultiModelIds(options.multiModelIds, options.availableModelIds)
  const hasInvalidMultiModelSelection = normalizedMultiModelIds.length !== options.multiModelIds.length
  if (!text) {
    return { kind: 'noop' }
  }

  if (options.isTeamMode) {
    if (options.isDebateBusy) {
      return { kind: 'blocked', text }
    }
    if (options.isTemporaryConversation) {
      return { kind: 'blocked', text, error: '临时对话暂不支持团队模式' }
    }
    if (hasInvalidMultiModelSelection || normalizedMultiModelIds.length < 2) {
      return { kind: 'blocked', text, error: '团队模式至少需要 2 个不同且可用的模型' }
    }
    return { kind: 'team', text }
  }

  if (options.isCompareMode) {
    if (options.isComparingGeneration) {
      return { kind: 'blocked', text }
    }
    if (hasInvalidMultiModelSelection || normalizedMultiModelIds.length < 2) {
      return { kind: 'blocked', text, error: '请至少选择两个不同且可用的模型' }
    }
    return { kind: 'compare', text }
  }

  if (options.isGenerating) {
    return { kind: 'blocked', text }
  }
  if (options.isModelLoading) {
    return { kind: 'blocked', text, error: '模型加载中，请稍后再试' }
  }
  if (options.availableModelIds.length === 0 || !options.selectedModel || !options.availableModelIds.includes(options.selectedModel)) {
    return { kind: 'blocked', text, error: '当前没有可用模型' }
  }

  return { kind: 'single', text }
}
