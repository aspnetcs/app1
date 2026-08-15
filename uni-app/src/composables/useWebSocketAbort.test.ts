import { describe, expect, it } from 'vitest'
import { buildChatAbortFrame, createAbortDeduper } from './useWebSocket'

describe('useWebSocket abort helpers', () => {
  it('buildChatAbortFrame matches contract shape', () => {
    expect(buildChatAbortFrame('req_1', 'user_cancel')).toEqual({
      type: 'chat.abort',
      data: { requestId: 'req_1', reason: 'user_cancel' },
    })
  })

  it('createAbortDeduper prevents duplicates within ttl', () => {
    const deduper = createAbortDeduper(2000)
    expect(deduper.shouldSend('req_1', 1000)).toBe(true)
    expect(deduper.shouldSend('req_1', 2500)).toBe(false)
    expect(deduper.shouldSend('req_1', 3001)).toBe(true)
  })

  it('createAbortDeduper ignores empty requestId', () => {
    const deduper = createAbortDeduper(2000)
    expect(deduper.shouldSend('', 1000)).toBe(false)
    expect(deduper.shouldSend('   ', 1000)).toBe(false)
  })
})

