export type ModelSelectorOption = {
  id: string
  name: string
  provider?: string
  avatarPath?: string
  supportsImageParsing?: boolean
  supportsImageParsingSource?: 'manual' | 'inferred' | 'unknown'
}

export type ModelCapabilityTag = {
  key: 'text' | 'image' | 'document'
  label: string
  tone: 'text' | 'image' | 'document'
}

export function getModelCapabilityTags(model: Pick<ModelSelectorOption, 'supportsImageParsing'> | null | undefined): ModelCapabilityTag[] {
  const imageSupported = model?.supportsImageParsing === true
  const tags: ModelCapabilityTag[] = [
    { key: 'text', label: '文本', tone: 'text' },
  ]

  if (imageSupported) {
    tags.push({ key: 'image', label: '图片', tone: 'image' })
  }

  tags.push({ key: 'document', label: '文档', tone: 'document' })
  return tags
}

export function resolveModelSelectorState(
  modelValue: string,
  models: ModelSelectorOption[],
) {
  const normalizedValue = modelValue.trim()
  const matchedModel = models.find((model) => model.id === normalizedValue) || null
  const fallbackModel = normalizedValue ? null : models[0] || null
  const currentModel = matchedModel || fallbackModel
  const labelSource = currentModel?.id || normalizedValue || 'AI'
  const triggerLabel = labelSource.length > 24 ? `${labelSource.slice(0, 22)}...` : labelSource

  return {
    currentModel,
    triggerLabel,
    hasStaleSelection: Boolean(normalizedValue) && matchedModel === null,
  }
}
