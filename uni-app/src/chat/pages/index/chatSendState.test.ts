import { describe, expect, it } from 'vitest'
import { normalizeMultiModelIds, replaceMultiModelId, resolveChatSendAttempt } from './chatSendState'

describe('normalizeMultiModelIds', () => {
  it('drops empty, duplicate, and unavailable model ids', () => {
    expect(
      normalizeMultiModelIds(
        ['gpt-4o', '', 'gpt-4o', 'claude-3-7-sonnet', 'missing'],
        ['gpt-4o', 'claude-3-7-sonnet'],
      ),
    ).toEqual(['gpt-4o', 'claude-3-7-sonnet'])
  })
})

describe('replaceMultiModelId', () => {
  it('keeps the previous state when the next model would duplicate another slot', () => {
    expect(
      replaceMultiModelId(
        ['gpt-4o', 'claude-3-7-sonnet', 'gemini-2.5-pro'],
        2,
        'claude-3-7-sonnet',
      ),
    ).toEqual(['gpt-4o', 'claude-3-7-sonnet', 'gemini-2.5-pro'])
  })

  it('replaces only the requested slot when the next model is unique', () => {
    expect(
      replaceMultiModelId(
        ['gpt-4o', 'claude-3-7-sonnet'],
        1,
        'gemini-2.5-pro',
      ),
    ).toEqual(['gpt-4o', 'gemini-2.5-pro'])
  })
})

describe('resolveChatSendAttempt', () => {
  it('keeps the draft intact when team mode is blocked by temporary scope', () => {
    const result = resolveChatSendAttempt({
      draftText: '继续保留这段输入',
      isTeamMode: true,
      isCompareMode: false,
      isGenerating: false,
      isModelLoading: false,
      isComparingGeneration: false,
      isDebateBusy: false,
      isTemporaryConversation: true,
      selectedModel: 'gpt-4o',
      availableModelIds: ['gpt-4o'],
      multiModelIds: ['gpt-4o', 'claude-3-7-sonnet'],
    })

    expect(result).toEqual({
      kind: 'blocked',
      text: '继续保留这段输入',
      error: '临时对话暂不支持团队模式',
    })
  })

  it('blocks compare mode with insufficient models before clearing the draft', () => {
    const result = resolveChatSendAttempt({
      draftText: '对比模式草稿',
      isTeamMode: false,
      isCompareMode: true,
      isGenerating: false,
      isModelLoading: false,
      isComparingGeneration: false,
      isDebateBusy: false,
      isTemporaryConversation: false,
      selectedModel: 'gpt-4o',
      availableModelIds: ['gpt-4o'],
      multiModelIds: ['gpt-4o'],
    })

    expect(result).toEqual({
      kind: 'blocked',
      text: '对比模式草稿',
      error: '请至少选择两个不同且可用的模型',
    })
  })

  it('blocks compare mode when the chosen models are duplicated or unavailable', () => {
    const result = resolveChatSendAttempt({
      draftText: '比较一下',
      isTeamMode: false,
      isCompareMode: true,
      isGenerating: false,
      isModelLoading: false,
      isComparingGeneration: false,
      isDebateBusy: false,
      isTemporaryConversation: false,
      selectedModel: 'gpt-4o',
      availableModelIds: ['gpt-4o', 'claude-3-7-sonnet'],
      multiModelIds: ['gpt-4o', 'gpt-4o', 'missing'],
    })

    expect(result).toEqual({
      kind: 'blocked',
      text: '比较一下',
      error: '请至少选择两个不同且可用的模型',
    })
  })

  it('blocks team mode when the chosen models are duplicated or unavailable', () => {
    const result = resolveChatSendAttempt({
      draftText: '团队协作回答',
      isTeamMode: true,
      isCompareMode: false,
      isGenerating: false,
      isModelLoading: false,
      isComparingGeneration: false,
      isDebateBusy: false,
      isTemporaryConversation: false,
      selectedModel: 'gpt-4o',
      availableModelIds: ['gpt-4o', 'claude-3-7-sonnet'],
      multiModelIds: ['gpt-4o', '', 'gpt-4o'],
    })

    expect(result).toEqual({
      kind: 'blocked',
      text: '团队协作回答',
      error: '团队模式至少需要 2 个不同且可用的模型',
    })
  })

  it('allows a normal single-model send once a valid model is selected', () => {
    const result = resolveChatSendAttempt({
      draftText: '正常发送',
      isTeamMode: false,
      isCompareMode: false,
      isGenerating: false,
      isModelLoading: false,
      isComparingGeneration: false,
      isDebateBusy: false,
      isTemporaryConversation: false,
      selectedModel: 'gpt-4o',
      availableModelIds: ['gpt-4o', 'claude-3-7-sonnet'],
      multiModelIds: [],
    })

    expect(result).toEqual({
      kind: 'single',
      text: '正常发送',
    })
  })

  it('allows attachment-only single-model sends with a default prompt', () => {
    const result = resolveChatSendAttempt({
      draftText: '   ',
      hasAttachments: true,
      attachmentOnlyText: '请识别并总结附件内容。',
      isTeamMode: false,
      isCompareMode: false,
      isGenerating: false,
      isModelLoading: false,
      isComparingGeneration: false,
      isDebateBusy: false,
      isTemporaryConversation: false,
      selectedModel: 'gpt-4o',
      availableModelIds: ['gpt-4o'],
      multiModelIds: [],
    })

    expect(result).toEqual({
      kind: 'single',
      text: '请识别并总结附件内容。',
    })
  })

  it('keeps model-loading feedback visible for single mode', () => {
    const result = resolveChatSendAttempt({
      draftText: '等待模型加载',
      isTeamMode: false,
      isCompareMode: false,
      isGenerating: false,
      isModelLoading: true,
      isComparingGeneration: false,
      isDebateBusy: false,
      isTemporaryConversation: false,
      selectedModel: 'gpt-4o',
      availableModelIds: ['gpt-4o'],
      multiModelIds: [],
    })

    expect(result).toEqual({
      kind: 'blocked',
      text: '等待模型加载',
      error: '模型加载中，请稍后再试',
    })
  })
})
