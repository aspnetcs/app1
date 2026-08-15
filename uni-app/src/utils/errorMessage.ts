type ErrorLikeObject = {
  message?: unknown
  errMsg?: unknown
  data?: unknown
}

export function extractErrorMessage(error: unknown, fallback: string): string {
  if (typeof error === 'string') {
    const text = error.trim()
    return text || fallback
  }

  if (error instanceof Error) {
    const text = error.message.trim()
    return text || fallback
  }

  if (!error || typeof error !== 'object') {
    return fallback
  }

  const candidate = error as ErrorLikeObject
  if (typeof candidate.message === 'string' && candidate.message.trim()) {
    return candidate.message.trim()
  }

  if (typeof candidate.errMsg === 'string' && candidate.errMsg.trim()) {
    return candidate.errMsg.trim()
  }

  if (candidate.data && typeof candidate.data === 'object') {
    const payload = candidate.data as { message?: unknown }
    if (typeof payload.message === 'string' && payload.message.trim()) {
      return payload.message.trim()
    }
  }

  return fallback
}
