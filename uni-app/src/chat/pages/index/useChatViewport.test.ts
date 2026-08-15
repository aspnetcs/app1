import { describe, expect, it } from 'vitest'
import { nextTick, ref } from 'vue'
import { useChatViewport } from './useChatViewport'
import type { Message } from '@/stores/chat'

async function flushViewportTicks(times = 3) {
  for (let i = 0; i < times; i += 1) {
    await nextTick()
  }
}

function msg(id: string): Message {
  return {
    id,
    role: 'assistant',
    content: `m-${id}`,
    createdAt: Date.now(),
  }
}

describe('useChatViewport', () => {
  it('auto scrolls to bottom when new messages arrive and user is at latest', async () => {
    const messages = ref<Message[]>([])
    const viewport = useChatViewport(messages)

    messages.value.push(msg('1'))
    await flushViewportTicks()
    expect(viewport.scrollIntoView.value).toBe('msg-1')
    expect(viewport.scrollTop.value).toBeGreaterThan(0)

    messages.value.push(msg('2'))
    await flushViewportTicks()
    expect(viewport.scrollIntoView.value).toBe('msg-2')
    expect(viewport.scrollTop.value).toBeGreaterThan(0)
  })

  it('shows scroll-to-latest when user scrolls up', async () => {
    const messages = ref<Message[]>([msg('1')])
    const viewport = useChatViewport(messages)

    viewport.onMessageScroll({ detail: { deltaY: -10, scrollTop: 0 } })
    expect(viewport.showScrollToLatest.value).toBe(true)
  })

  it('counts new messages when user is not at latest', async () => {
    const messages = ref<Message[]>([msg('1')])
    const viewport = useChatViewport(messages)

    // User scrolls up
    viewport.onMessageScroll({ detail: { deltaY: -10, scrollTop: 0 } })
    expect(viewport.showScrollToLatest.value).toBe(true)

    // New messages should not auto-scroll; they should increase the badge count.
    messages.value.push(msg('2'), msg('3'))
    await flushViewportTicks()

    expect(viewport.newMessageCount.value).toBe(2)
    expect(viewport.scrollIntoView.value).toBe('')
  })

  it('jumpToLatest resets badge and scrolls to bottom', async () => {
    const messages = ref<Message[]>([msg('1')])
    const viewport = useChatViewport(messages)

    viewport.onMessageScroll({ detail: { deltaY: -10, scrollTop: 0 } })
    messages.value.push(msg('2'))
    await flushViewportTicks()
    expect(viewport.newMessageCount.value).toBe(1)
    expect(viewport.showScrollToLatest.value).toBe(true)

    viewport.jumpToLatest()
    await flushViewportTicks(4)
    expect(viewport.showScrollToLatest.value).toBe(false)
    expect(viewport.newMessageCount.value).toBe(0)
    expect(viewport.scrollIntoView.value).toBe('msg-2')
    expect(viewport.scrollTop.value).toBeGreaterThan(0)
  })

  it('shows scroll-to-latest again after jumping to latest and scrolling up later', async () => {
    const messages = ref<Message[]>([msg('1'), msg('2')])
    const viewport = useChatViewport(messages)

    viewport.onMessageScroll({ detail: { deltaY: -10, scrollTop: 120 } })
    expect(viewport.showScrollToLatest.value).toBe(true)

    viewport.jumpToLatest()
    await flushViewportTicks(4)
    expect(viewport.showScrollToLatest.value).toBe(false)

    viewport.onMessageScroll({ detail: { deltaY: 0, scrollTop: viewport.scrollTop.value - 40 } })
    expect(viewport.showScrollToLatest.value).toBe(true)
  })

  it('onScrollToLower clears badge and hides scroll-to-latest', () => {
    const messages = ref<Message[]>([msg('1')])
    const viewport = useChatViewport(messages)

    viewport.showScrollToLatest.value = true
    viewport.newMessageCount.value = 3
    viewport.onScrollToLower()

    expect(viewport.showScrollToLatest.value).toBe(false)
    expect(viewport.newMessageCount.value).toBe(0)
  })

  it('scrollToMessage targets the requested anchor id', async () => {
    const messages = ref<Message[]>([msg('1'), msg('2')])
    const viewport = useChatViewport(messages)

    viewport.scrollToMessage('2')
    await flushViewportTicks(4)

    expect(viewport.scrollIntoView.value).toBe('msg-2')
  })

  it('keeps pinned-to-bottom on streaming growth when message array length is unchanged', async () => {
    const messages = ref<Message[]>([])
    const viewport = useChatViewport(messages)

    messages.value.push(msg('1'))
    await flushViewportTicks()
    expect(viewport.scrollIntoView.value).toBe('msg-1')

    // In-place content growth (streaming) should trigger scroll-to-bottom when pinned.
    messages.value[0].content += ' +delta'

    await nextTick()
    await nextTick()
    expect(viewport.scrollIntoView.value).toBe('')

    await nextTick()
    expect(viewport.scrollIntoView.value).toBe('msg-1')
  })
})
