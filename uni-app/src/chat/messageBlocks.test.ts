import { describe, expect, it } from 'vitest'
import {
  applyMessageTranslation,
  buildRenderableMessageBlocks,
  collectMessageCitations,
  markMessageTranslationLoading,
  normalizeMessageBlocks,
} from '../utils/messageBlocks'

describe('messageBlocks', () => {
  it('keeps plain text messages compatible', () => {
    expect(buildRenderableMessageBlocks({ content: 'hello world' })).toEqual([
      { id: 'content', type: 'markdown', content: 'hello world', citations: undefined },
    ])
  })

  it('normalizes structured blocks and citations', () => {
    const blocks = normalizeMessageBlocks([
      {
        id: 'b1',
        type: 'citation',
        citations: [
          { id: 'c1', title: 'Doc 1', snippet: 'Excerpt', url: 'https://example.com' },
        ],
      },
    ])

    expect(blocks[0]?.citations?.[0]).toMatchObject({
      id: 'c1',
      title: 'Doc 1',
      snippet: 'Excerpt',
      url: 'https://example.com',
      index: 1,
    })
  })

  it('collects citations from block payloads when top-level citations are missing', () => {
    expect(
      collectMessageCitations({
        blocks: [
          {
            id: 'b1',
            type: 'citation',
            citations: [{ id: 'c1', title: 'Doc 1' }],
          },
        ],
      }),
    ).toHaveLength(1)
  })

  it('renders translation blocks from message translation state', () => {
    const message = {
      content: 'hello world',
      translationText: 'bonjour le monde',
      translationLanguage: 'French',
    }

    const blocks = buildRenderableMessageBlocks(message)

    expect(blocks).toHaveLength(2)
    expect(blocks[1]).toMatchObject({
      id: 'translation',
      type: 'translation',
      title: '翻译 · 法文',
      content: 'bonjour le monde',
    })
  })

  it('updates translation blocks while loading and after completion', () => {
    const message = {
      id: 'm1',
      role: 'assistant' as const,
      content: 'hello',
      createdAt: Date.now(),
      blocks: undefined,
    }

    markMessageTranslationLoading(message, 'Japanese')

    expect(buildRenderableMessageBlocks(message)[1]).toMatchObject({
      type: 'translation',
      title: '翻译 · 日文',
      content: '正在翻译...',
    })

    applyMessageTranslation(message, {
      translatedText: 'こんにちは',
      targetLanguage: 'Japanese',
    })

    expect(buildRenderableMessageBlocks(message)[1]).toMatchObject({
      type: 'translation',
      title: '翻译 · 日文',
      content: 'こんにちは',
    })
  })
})

