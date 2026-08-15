import { computed, nextTick, ref, watch, type Ref } from 'vue'
import type { Message } from '@/stores/chat'

type ScrollEvent = { detail?: { deltaY?: number; scrollTop?: number } }

function createThrottle(fn: () => void, waitMs: number) {
  let timer: ReturnType<typeof setTimeout> | null = null
  let trailing = false

  return () => {
    if (timer) {
      trailing = true
      return
    }

    fn()

    timer = setTimeout(() => {
      timer = null
      if (trailing) {
        trailing = false
        fn()
      }
    }, waitMs)
    // In Node (unit tests), avoid keeping the process alive due to pending timers.
    ;(timer as any)?.unref?.()
  }
}

export function useChatViewport(messages: Ref<Message[]>) {
  const scrollIntoView = ref('')
  const scrollTop = ref(0)
  const showScrollToLatest = ref(false)
  const newMessageCount = ref(0)
  const isPinnedToBottom = ref(true)
  const lastScrollTop = ref(0)

  const bumpScrollTopToBottom = () => {
    scrollTop.value += 1000000
    lastScrollTop.value = scrollTop.value
  }

  const scrollToBottom = () => {
    nextTick(() => {
      if (messages.value.length === 0) return
      const targetId = 'msg-' + messages.value[messages.value.length - 1].id
      bumpScrollTopToBottom()

      if (scrollIntoView.value === targetId) {
        scrollIntoView.value = ''
        nextTick(() => {
          bumpScrollTopToBottom()
          scrollIntoView.value = targetId
        })
        return
      }

      scrollIntoView.value = targetId
    })
  }

  const throttledScrollToBottom = createThrottle(() => {
    if (!isPinnedToBottom.value) return
    scrollToBottom()
  }, 80)

  const jumpToLatest = () => {
    isPinnedToBottom.value = true
    showScrollToLatest.value = false
    newMessageCount.value = 0
    scrollToBottom()
  }

  const beginUserScroll = () => {
    if (!isPinnedToBottom.value && showScrollToLatest.value) return
    isPinnedToBottom.value = false
    showScrollToLatest.value = true
    scrollIntoView.value = ''
  }

  const scrollToMessage = (messageId: string) => {
    const normalized = messageId.trim()
    if (!normalized) return
    const targetId = 'msg-' + normalized

    nextTick(() => {
      if (scrollIntoView.value === targetId) {
        scrollIntoView.value = ''
        nextTick(() => {
          scrollIntoView.value = targetId
        })
        return
      }

      scrollIntoView.value = targetId
    })
  }

  const onMessageScroll = (event: ScrollEvent) => {
    const detail = event.detail || {}
    const deltaY = Number(detail.deltaY || 0)
    const scrollTop = Number(detail.scrollTop || 0)

    const inferredDeltaY =
      deltaY !== 0
        ? deltaY
        : scrollTop - lastScrollTop.value

    lastScrollTop.value = scrollTop

    // Stable heuristic: user actively scrolls up (away from the latest).
    // Avoid fragile absolute scrollTop thresholds.
    if (inferredDeltaY < -2) {
      isPinnedToBottom.value = false
      showScrollToLatest.value = true
      scrollIntoView.value = ''
    }
  }

  const onScrollToLower = () => {
    isPinnedToBottom.value = true
    showScrollToLatest.value = false
    newMessageCount.value = 0
  }

  watch(
    () => messages.value.length,
    (current, previous) => {
      if (current <= previous) return
      if (!isPinnedToBottom.value || showScrollToLatest.value) {
        isPinnedToBottom.value = false
        showScrollToLatest.value = true
        newMessageCount.value += current - previous
        return
      }
      scrollToBottom()
    }
  )

  const lastMessageSignature = computed(() => {
    const list = messages.value
    if (list.length === 0) return ''
    const last = list[list.length - 1]

    const contentLen = typeof last.content === 'string' ? last.content.length : 0
    const reasoningLen = typeof last.reasoningContent === 'string' ? last.reasoningContent.length : 0
    const status = last.status ? String(last.status) : ''

    return `${last.id}:${contentLen}:${reasoningLen}:${status}`
  })

  watch(lastMessageSignature, (current, previous) => {
    if (!previous) return
    if (!current) return
    if (!isPinnedToBottom.value) return
    throttledScrollToBottom()
  })

  return {
    scrollIntoView,
    scrollTop,
    showScrollToLatest,
    newMessageCount,
    isPinnedToBottom,
    scrollToBottom,
    scrollToMessage,
    jumpToLatest,
    beginUserScroll,
    onMessageScroll,
    onScrollToLower,
  }
}
