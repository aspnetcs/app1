import { describe, expect, it } from 'vitest'
import type { Message } from './chatState'
import { applyStreamingAssistantDelta, applyStreamingAssistantThinking, upsertStreamingAssistantMessage } from './chatState'

function fixedIdFactory(now: number) {
  return `fixed_${now}`
}

describe('chatState streaming helpers', () => {
  it('creates a placeholder assistant message when delta arrives for unknown requestId', () => {
    const messages: Message[] = []
    const msg = applyStreamingAssistantDelta(
      messages,
      { requestId: 'req_1', delta: 'hi', traceId: 't_1' },
      { now: 1700000000000, idFactory: fixedIdFactory },
    )
    expect(msg).toBeTruthy()
    expect(messages).toHaveLength(1)
    expect(messages[0].role).toBe('assistant')
    expect(messages[0].requestId).toBe('req_1')
    expect(messages[0].content).toBe('hi')
    expect(messages[0].traceId).toBe('t_1')
    expect(messages[0].id).toBe('fixed_1700000000000')
  })

  it('does not create duplicates for the same requestId', () => {
    const messages: Message[] = []
    applyStreamingAssistantDelta(messages, { requestId: 'req_1', delta: 'a' }, { now: 1, idFactory: fixedIdFactory })
    applyStreamingAssistantDelta(messages, { requestId: 'req_1', delta: 'b' }, { now: 2, idFactory: fixedIdFactory })
    expect(messages).toHaveLength(1)
    expect(messages[0].content).toBe('ab')
  })

  it('dedupes multiple assistant messages with the same requestId, keeping the most informative', () => {
    const messages: Message[] = [
      {
        id: 'm1',
        role: 'assistant',
        content: 'short',
        createdAt: 1,
        requestId: 'req_1',
        status: 'sending',
      },
      {
        id: 'm2',
        role: 'assistant',
        content: 'this is longer',
        createdAt: 2,
        requestId: 'req_1',
        status: 'sending',
        traceId: 't_2',
      },
    ]
    const kept = upsertStreamingAssistantMessage(messages, 'req_1')
    expect(kept).toBeTruthy()
    expect(messages).toHaveLength(1)
    expect(messages[0].id).toBe('m2')
    expect(messages[0].traceId).toBe('t_2')
  })

  it('ignores updates for closed requestId', () => {
    const messages: Message[] = []
    const msg = applyStreamingAssistantDelta(
      messages,
      { requestId: 'req_1', delta: 'x' },
      { isClosedRequestId: (id) => id === 'req_1' },
    )
    expect(msg).toBeNull()
    expect(messages).toHaveLength(0)
  })

  it('applies thinking chunks into reasoningContent with thinking status', () => {
    const messages: Message[] = []
    const msg = applyStreamingAssistantThinking(
      messages,
      { requestId: 'req_1', delta: 'r1', traceId: 't_1' },
      { now: 10, idFactory: fixedIdFactory },
    )
    expect(msg).toBeTruthy()
    expect(messages[0].reasoningContent).toBe('r1')
    expect(messages[0].reasoningStatus).toBe('thinking')
    expect(messages[0].reasoningStartTime).toBe(10)
  })
})

