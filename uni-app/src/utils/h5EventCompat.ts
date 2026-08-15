export type CompatEventLike = Event | { type?: string; timeStamp?: number } | null | undefined

type CompatEventMeta = {
  type: string
  timeStamp: number | null
  raw: object | null
}

function resolveEventType(event: CompatEventLike): string {
  const type = event && typeof event === 'object' ? (event as { type?: unknown }).type : undefined
  return typeof type === 'string' && type ? type : 'unknown'
}

function resolveEventTimeStamp(event: CompatEventLike): number | null {
  const timeStamp = event && typeof event === 'object'
    ? (event as { timeStamp?: unknown }).timeStamp
    : undefined
  return typeof timeStamp === 'number' && Number.isFinite(timeStamp) ? timeStamp : null
}

function resolveEventMeta(event: CompatEventLike): CompatEventMeta {
  return {
    type: resolveEventType(event),
    timeStamp: resolveEventTimeStamp(event),
    raw: event && typeof event === 'object' ? event : null,
  }
}

function isSameGestureDuplicate(previous: RecentAction, current: CompatEventMeta, now: number, windowMs: number) {
  if (previous.type !== current.type || now - previous.at >= windowMs) {
    return false
  }
  if (previous.raw && current.raw && previous.raw === current.raw) {
    return true
  }
  if (previous.timeStamp == null || current.timeStamp == null) {
    return false
  }
  return Math.abs(previous.timeStamp - current.timeStamp) < 1
}

type RecentAction = {
  type: string
  at: number
  timeStamp: number | null
  raw: object | null
}

export function createCompatActionRunner(crossTypeWindowMs = 320, sameGestureWindowMs = 48) {
  const recentActions = new Map<string, RecentAction>()

  return (key: string, event: CompatEventLike, action: () => void) => {
    const now = Date.now()
    const current = resolveEventMeta(event)
    const previous = recentActions.get(key)

    if (previous && isSameGestureDuplicate(previous, current, now, sameGestureWindowMs)) {
      recentActions.set(key, { ...previous, at: now })
      return
    }

    if (previous && previous.type !== current.type && now - previous.at < crossTypeWindowMs) {
      recentActions.set(key, { ...current, at: now })
      return
    }

    recentActions.set(key, { ...current, at: now })
    action()
  }
}
