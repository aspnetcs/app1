import { describe, expect, it } from 'vitest'
import {
  normalizeConversation,
  normalizeMessage,
  sortConversationHistory,
  type Conversation,
} from './chatState'

describe('chatState conversation helpers', () => {
  it('normalizes pinned_at into pinnedAt', () => {
    const conversation = normalizeConversation({
      id: 'conv-1',
      title: 'Pinned',
      pinned: true,
      updated_at: '2026-03-27T00:00:00Z',
      pinned_at: '2026-03-26T00:00:00Z',
    })

    expect(conversation.pinnedAt).toBe(Date.parse('2026-03-26T00:00:00Z'))
  })

  it('preserves compare metadata and infers compare mode from compareModels', () => {
    const conversation = normalizeConversation({
      id: 'compare-1',
      title: 'Compare',
      updated_at: '2026-03-27T00:00:00Z',
      compare_models: ['gpt-4o', 'claude-3-7-sonnet'],
    })

    expect(conversation.mode).toBe('compare')
    expect(conversation.compareModelIds).toEqual(['gpt-4o', 'claude-3-7-sonnet'])
  })

  it('keeps pinned conversations ordered by pinnedAt before recency', () => {
    const history: Conversation[] = [
      {
        id: 'b',
        title: 'Pinned B',
        pinned: true,
        pinnedAt: Date.parse('2026-03-26T00:00:00Z'),
        updatedAt: Date.parse('2026-03-27T08:00:00Z'),
      },
      {
        id: 'a',
        title: 'Pinned A',
        pinned: true,
        pinnedAt: Date.parse('2026-03-25T00:00:00Z'),
        updatedAt: Date.parse('2026-03-27T09:00:00Z'),
      },
      {
        id: 'c',
        title: 'Recent',
        pinned: false,
        updatedAt: Date.parse('2026-03-27T10:00:00Z'),
      },
    ]

    const sorted = sortConversationHistory(history)

    expect(sorted.map((item) => item.id)).toEqual(['a', 'b', 'c'])
  })

  it('restores saved attachment blocks into message attachments', () => {
    const message = normalizeMessage({
      id: 'msg-1',
      role: 'user',
      content: '请识别附件',
      created_at: '2026-06-15T00:00:00Z',
      blocks: [
        {
          type: 'file',
          payload: {
            fileId: 'file-1',
            kind: 'document',
            originalName: 'report.pdf',
            mimeType: 'application/pdf',
            recognitionStatus: 'extracted',
            recognitionText: 'hello',
          },
        },
        {
          type: 'image',
          payload: {
            fileId: 'file-2',
            originalName: 'photo.png',
            mimeType: 'image/png',
            url: 'https://example.test/photo.png',
            recognitionStatus: 'vision_ready',
          },
        },
      ],
    })

    expect(message.attachments).toEqual([
      {
        fileId: 'file-1',
        kind: 'document',
        originalName: 'report.pdf',
        mimeType: 'application/pdf',
        recognitionStatus: 'extracted',
        recognitionText: 'hello',
      },
      {
        fileId: 'file-2',
        kind: 'image',
        originalName: 'photo.png',
        mimeType: 'image/png',
        url: 'https://example.test/photo.png',
        recognitionStatus: 'vision_ready',
      },
    ])
  })

})
