import { describe, expect, it } from 'vitest'
import { getModelCapabilityTags, resolveModelSelectorState } from './modelSelectorState'

describe('resolveModelSelectorState', () => {
  it('does not pretend the first model is selected when the stored selection is stale', () => {
    const state = resolveModelSelectorState('stale-model', [
      { id: 'model-a', name: 'Model A' },
      { id: 'model-b', name: 'Model B' },
    ])

    expect(state.currentModel).toBeNull()
    expect(state.hasStaleSelection).toBe(true)
    expect(state.triggerLabel).toBe('stale-model')
  })

  it('falls back to the first model only when no explicit model is selected', () => {
    const state = resolveModelSelectorState('', [
      { id: 'model-a', name: 'Model A' },
      { id: 'model-b', name: 'Model B' },
    ])

    expect(state.currentModel?.id).toBe('model-a')
    expect(state.hasStaleSelection).toBe(false)
    expect(state.triggerLabel).toBe('model-a')
  })
})

describe('getModelCapabilityTags', () => {
  it('shows text, image, and document for image-capable models', () => {
    const tags = getModelCapabilityTags({ supportsImageParsing: true })

    expect(tags.map(tag => tag.label)).toEqual(['文本', '图片', '文档'])
  })

  it('shows text plus document for image-disabled models', () => {
    const tags = getModelCapabilityTags({ supportsImageParsing: false })

    expect(tags.map(tag => tag.label)).toEqual(['文本', '文档'])
  })
})
